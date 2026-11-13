package com.lifeskill.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.data.ChatRow
import com.lifeskill.app.data.AttachmentDraft
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.LocalSettings
import com.lifeskill.app.data.SecretBox
import com.lifeskill.app.data.TurnRow
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatAttachment
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationLevel
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillGraph
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LifeSkillDatabase
    private lateinit var ai: ScriptedGateway
    private lateinit var container: AppContainer

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        ai = ScriptedGateway()
        container = fixture(database, ai)
        container.library.initialize()
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun directionsSeedOnceAndManualDeleteCascadesThroughEdgesAndChats() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "测试树")
        val root = add(tree, EntryKind.SKILL, "前置")
        val child = add(tree, EntryKind.SKILL, "后续", setOf(root.id))
        markLegacyLit(root.id)
        val scope = ChatScope(ChatMode.TUTOR, child.id)
        ai.reply("""{"kind":"teach","reply":"先复习已有知识。"}""")
        container.conversations.send(scope, "开始学习")
        val bad = EntryEdit(root.id, tree.id, EntryKind.SKILL, root.name, root.goal, setOf(child.id))
        expectRule { container.library.save(bad) }
        assertEquals(setOf(root.id), container.library.snapshot().prerequisitesOf(child.id))

        container.library.delete(root.id)
        assertTrue(container.library.snapshot().prerequisitesOf(child.id).isEmpty())
        assertEquals(2, container.conversations.watch(scope).first().size)
        container.library.delete(direction.id)
        assertTrue(container.conversations.watch(scope).first().isEmpty())
        assertTrue(container.library.snapshot().childrenOf(tree.id).isEmpty())
        container.library.initialize()
        assertEquals(4, container.library.snapshot().childrenOf(null).size)
    }

    @Test fun latestStudyFollowsUserRequestsAndFallsBackAfterDeletion() = runBlocking {
        withTimeout(10_000) {
            val library = container.library
            val conversations = container.conversations
            val direction = library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "续学测试")
            val earlier = add(tree, EntryKind.SKILL, "较早课程")
            val later = add(tree, EntryKind.SKILL, "最近课程")
            val latest = conversations.watchLatestStudySkillId()
            assertEquals(null, latest.first())

            val waiting = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            ai.enqueue {
                waiting.complete(Unit)
                release.await()
                """{"kind":"teach","reply":"较早课程的答复。"}"""
            }
            val earlierRequest = async {
                conversations.send(ChatScope(ChatMode.TUTOR, earlier.id), "学习第一课")
            }
            waiting.await()
            assertEquals(earlier.id, latest.first())

            ai.reply("""{"kind":"teach","reply":"最近课程的答复。"}""")
            conversations.send(ChatScope(ChatMode.TUTOR, later.id), "学习第二课")
            release.complete(Unit)
            earlierRequest.await()
            ai.reply("""{"kind":"message","reply":"你想生成什么？"}""")
            conversations.send(ChatScope(ChatMode.GENERATE), "生成技能树")
            assertEquals(later.id, latest.first())

            library.delete(later.id)
            assertEquals(earlier.id, latest.first())
            library.delete(tree.id)
            assertEquals(null, latest.first())
        }
    }

    @Test fun learningAndAssessmentRequireEveryPrerequisite() = runBlocking {
        withTimeout(10_000) {
            val library = container.library
            val conversations = container.conversations
            val direction = library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "顺序验证")
            val first = add(tree, EntryKind.SKILL, "第一前置")
            val second = add(tree, EntryKind.SKILL, "第二前置")
            val third = add(tree, EntryKind.SKILL, "新增前置")
            val child = add(tree, EntryKind.SKILL, "合流技能", setOf(first.id, second.id))
            val scope = ChatScope(ChatMode.TUTOR, child.id)
            markLegacyLit(first.id)
            expectRule { conversations.send(scope, "开始学习") }
            expectRule { conversations.send(scope, "", check = true) }
            expectRule { conversations.restartLesson(scope, "") }
            assertTrue(conversations.watch(scope).first().isEmpty())

            markLegacyLit(second.id)
            offerCheck(scope)
            ai.reply("""{"kind":"exercise","question":"请说出操作步骤。"}""")
            conversations.send(scope, "", check = true)
            val exercise = advanceToSecondQuestion(scope, "先核对输入")
            val waiting = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            ai.enqueue {
                waiting.complete(Unit)
                release.await()
                verdict("passed", "答对了。", exercise.id)
            }
            val check = async { conversations.send(scope, "核对后执行") }
            waiting.await()
            library.save(EntryEdit(child.id, tree.id, EntryKind.SKILL, child.name, child.goal, setOf(first.id, second.id, third.id)))
            release.complete(Unit)
            check.await()
            assertEquals(SkillState.LOCKED, library.snapshot().skillState(child.id))
            assertEquals(Mastery.UNLIT, library.entry(child.id)?.mastery)
            assertTrue(conversations.watch(scope).first().last().text.contains("完成先修课后"))

            val history = conversations.watch(scope).first()
            expectRule { conversations.restartLesson(scope, "") }
            assertEquals(history, conversations.watch(scope).first())
        }
    }

    @Test fun editingAndGenerationBothRejectAThirdBranch() = runBlocking {
        val library = container.library
        val direction = library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "分叉验证")
        val parent = add(tree, EntryKind.SKILL, "起点")
        add(tree, EntryKind.SKILL, "左支线", setOf(parent.id))
        add(tree, EntryKind.SKILL, "右支线", setOf(parent.id))
        expectRule { add(tree, EntryKind.SKILL, "第三支线", setOf(parent.id)) }
        val scope = ChatScope(ChatMode.GENERATE, tree.id)
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("\"remainingBranches\":0"))
            """{"kind":"create","reply":"新增","skills":[{"ref":"more","name":"第三支线","goal":"可验证目标","requires":["existing:${parent.id}"]}]}"""
        }
        expectRule { container.conversations.send(scope, "再加一条支线") }
        assertEquals(3, library.snapshot().childrenOf(tree.id).size)
        assertEquals(1, container.conversations.watch(scope).first().size)
    }

    @Test fun manualSaveAndAiAppendEnforceAdjacentPrerequisites() = runBlocking {
        val library = container.library
        val direction = library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "逻辑层级验证")
        val root = add(tree, EntryKind.SKILL, "基础")
        val middle = add(tree, EntryKind.SKILL, "进阶", setOf(root.id))
        val original = library.snapshot()
        expectRule { add(tree, EntryKind.SKILL, "应用", setOf(root.id, middle.id)) }
        assertEquals(original, library.snapshot())

        val scope = ChatScope(ChatMode.GENERATE, tree.id)
        fun response(requires: List<Entry>) = """
            {"kind":"create","reply":"新增","skills":[
              {"ref":"apply","name":"应用","goal":"演示操作","requires":${JSONArray(requires.map { "existing:${it.id}" })}}
            ]}
        """.trimIndent()
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("\"level\":2"))
            response(listOf(root, middle))
        }
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("跨阶段的先修关系"))
            assertEquals(original, library.snapshot())
            response(listOf(middle))
        }
        container.conversations.send(scope, "追加应用技能")
        val updated = library.snapshot()
        val added = updated.childrenOf(tree.id).single { it.name == "应用" }
        assertEquals(setOf(middle.id), updated.prerequisitesOf(added.id))
        assertEquals(TurnKind.CREATED, container.conversations.watch(scope).first().last().kind)
    }

    @Test fun invalidNamesRollBackButCrossTierEdgesAreFixedWithoutAnotherModelCall() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val invalid = """
            {"kind":"create","reply":"方案","directionName":"设计","trees":[
              {"name":"树 A","skills":[{"ref":"base","name":"首节","goal":"能说明基础概念","requires":[]}]},
              {"name":"树 B","skills":[
                {"ref":"base","name":"基础","goal":"能解释用途","requires":[]},
                {"ref":"next","name":"进阶","goal":"能使用基础概念","requires":["base"]},
                {"ref":"last","name":"进阶","goal":"能完成练习","requires":["base","next"]}
              ]}
            ]}
        """.trimIndent()
        seedLegacyDraft(scope, invalid)
        val preview = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.DRAFT, preview.kind)
        expectRule { container.conversations.confirmDraft(scope, preview.id, GenerationDestination.New("设计")) }
        assertFalse(container.library.snapshot().entries.any { it.name in setOf("设计", "树 A", "树 B") })
        assertEquals(preview, container.conversations.watch(scope).first().last())

        val offerId = offerTree(scope)
        val newTree = JSONObject(invalid).getJSONArray("trees").getJSONObject(1)
        newTree.getJSONArray("skills").getJSONObject(2).put("name", "应用")
        val singleTree = JSONObject(invalid).put("trees", JSONArray().put(newTree))
        ai.reply(singleTree.toString())
        container.conversations.generateTree(scope, offerId, GenerationOptions(GenerationLevel.BEGINNER, 3..3))
        val secondDraft = container.conversations.watch(scope).first().last()
        assertTrue(secondDraft.text.contains("略过 1 条不符合规则的先修关系"))
        container.conversations.confirmDraft(scope, secondDraft.id, GenerationDestination.New("数据能力"))
        val history = container.conversations.watch(scope).first()
        assertEquals(secondDraft.id, history.last().id)
        assertEquals(TurnKind.CREATED, history.last().kind)
        val treeId = history.last().resultId
        assertNotNull(treeId)
        val library = container.library.snapshot()
        val (base, next, last) = library.childrenOf(treeId)
        assertEquals(setOf(base.id), library.prerequisitesOf(next.id))
        assertEquals(setOf(next.id), library.prerequisitesOf(last.id))
        assertTrue(history.last().text.contains("略过 1 条不符合规则的先修关系"))
    }

    @Test fun confirmingLegacyDraftKeepsLocalPrerequisitesAndIgnoresForeignReferences() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val direction = container.library.snapshot().childrenOf(null).first()
        seedLegacyDraft(scope, """
            {"kind":"create","reply":"已规划","trees":[
              {"name":"基础","skills":[{"ref":"foreign","name":"外树节点","goal":"能说明用途","requires":[]}]},
              {"name":"实践","skills":[
                {"ref":"base","name":"本树前置","goal":"掌握概念","requires":[]},
                {"ref":"follow","name":"本树后续","goal":"实际运用","requires":[" existing:base ","本树前置","foreign","existing:missing"]},
                {"ref":"application","name":"综合练习","goal":"能完成综合练习","requires":["base","follow"]}
              ]}
            ]}
        """.trimIndent())
        val draft = container.conversations.watch(scope).first().single()
        assertTrue(draft.text.contains("略过 2 条"))
        assertTrue(draft.text.contains("略过 1 条不符合规则的先修关系"))
        assertTrue(container.library.snapshot().childrenOf(direction.id).isEmpty())

        container.conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        val library = container.library.snapshot()
        val trees = library.childrenOf(direction.id)
        assertEquals(listOf("基础", "实践"), trees.map(Entry::name))
        assertEquals(listOf(EntryIcon.BOOK, EntryIcon.TOOLS), trees.map(Entry::icon))
        val (base, follow, application) = library.childrenOf(trees.last().id)
        assertEquals(EntryIcon.BOOK, base.icon)
        assertEquals(EntryIcon.EXPLORE, follow.icon)
        assertEquals(setOf(base.id), library.prerequisitesOf(follow.id))
        assertEquals(setOf(follow.id), library.prerequisitesOf(application.id))
        val created = container.conversations.watch(scope).first().single()
        assertEquals(TurnKind.CREATED, created.kind)
        assertTrue(created.text.contains("略过 2 条"))
    }

    @Test fun confirmingLegacyDraftRepairsMultipleRoots() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val before = container.library.snapshot()
        seedLegacyDraft(scope, CREATED.replace("\"requires\":[\"base\"]", "\"requires\":[]"))
        val draft = container.conversations.watch(scope).first().single()
        assertEquals(before, container.library.snapshot())

        container.conversations.confirmDraft(scope, draft.id, GenerationDestination.New("数据能力"))
        val created = container.conversations.watch(scope).first().single()
        assertEquals(TurnKind.CREATED, created.kind)
        val library = container.library.snapshot()
        val lessons = library.childrenOf(checkNotNull(created.resultId))
        assertEquals(2, lessons.size)
        assertEquals(setOf(lessons.first().id), library.prerequisitesOf(lessons.last().id))
    }

    private suspend fun seedLegacyDraft(scope: ChatScope, raw: String) {
        val dao = database.skills()
        val id = "legacy-draft"
        dao.insertChat(ChatRow(scope.id, scope.ownerId))
        dao.insertTurn(TurnRow(
            id = id, chatId = scope.id, speaker = Speaker.ASSISTANT.name, kind = TurnKind.DRAFT.name,
            text = SecretBox("lifeskill-tests").seal(raw, "$id:turn"), contentVersion = 0,
            exerciseId = null, resultId = null, choices = null,
        ))
    }

    @Test fun overlappingGenerationAndConfirmationUseOnlyTheSelectedDirection() = runBlocking {
        withTimeout(10_000) {
            val scope = ChatScope(ChatMode.GENERATE)
            val offerId = offerTree(scope)
            val firstWaiting = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            ai.enqueue {
                firstWaiting.complete(Unit)
                releaseFirst.await()
                CREATED
            }
            ai.reply(CREATED)
            val first = async {
                container.conversations.generateTree(scope, offerId, GenerationOptions(GenerationLevel.BEGINNER, 2..2))
            }
            firstWaiting.await()
            container.conversations.retry(scope)
            releaseFirst.complete(Unit)
            first.await()
            val history = container.conversations.watch(scope).first()
            assertEquals(4, history.size)
            assertFalse(container.library.snapshot().entries.any { it.name in setOf("数据能力", "数据质量") })
            val chosen = container.library.snapshot().childrenOf(null).first()
            val draft = history.last()
            val destination = GenerationDestination.Existing(chosen.id)
            val firstConfirm = async { container.conversations.confirmDraft(scope, draft.id, destination) }
            container.conversations.confirmDraft(scope, draft.id, destination)
            firstConfirm.await()
            assertEquals(listOf("数据质量"), container.library.snapshot().childrenOf(chosen.id).map(Entry::name))
            assertFalse(container.library.snapshot().entries.any { it.name == "数据能力" })
            assertEquals(TurnKind.CREATED, container.conversations.watch(scope).first().last().kind)
        }
    }

    @Test fun directionScopedDraftCanBeConfirmedInADifferentDirection() = runBlocking {
        val (source, destination) = container.library.snapshot().childrenOf(null)
        val scope = ChatScope(ChatMode.GENERATE, source.id)
        val offerId = offerTree(scope, "生成信息化技能树")
        ai.reply("""
            {"kind":"create","reply":"已规划","trees":[{"name":"信息化基础","icon":"TREE","skills":[
              {"ref":"intro","name":"认识信息系统","goal":"能说明数据表的用途","requires":[],"icon":"CODE"},
              {"ref":"query","name":"查询数据","goal":"能按条件筛选数据","requires":["intro"]},
              {"ref":"quality","name":"检查数据质量","goal":"能识别空值和重复项","requires":["intro"]}
            ]}]}
        """.trimIndent())
        container.conversations.generateTree(scope, offerId, GenerationOptions(GenerationLevel.BEGINNER, 3..3))
        val draft = container.conversations.watch(scope).first().last()
        assertEquals(source.id, draft.preview?.suggestedDirectionId)
        assertTrue(container.library.snapshot().childrenOf(source.id).isEmpty())
        container.conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(destination.id))
        val library = container.library.snapshot()
        assertTrue(library.childrenOf(source.id).isEmpty())
        val tree = library.childrenOf(destination.id).single()
        assertEquals("信息化基础", tree.name)
        assertEquals(EntryIcon.CHART, tree.icon)
        val skills = library.childrenOf(tree.id).associateBy(Entry::name)
        val intro = skills.getValue("认识信息系统")
        assertEquals(3, skills.size)
        assertEquals(EntryIcon.CODE, intro.icon)
        assertTrue(library.prerequisitesOf(intro.id).isEmpty())
        assertEquals(setOf(intro.id), library.prerequisitesOf(skills.getValue("查询数据").id))
        assertEquals(setOf(intro.id), library.prerequisitesOf(skills.getValue("检查数据质量").id))
    }

    @Test fun newTreeRequiresConfirmationAndKeepsTheConfirmedCountAcrossRetries() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val conversations = container.conversations
        val original = container.library.snapshot()
        val options = GenerationOptions(GenerationLevel.EXPERT, 33..33)
        expectRule { conversations.generateTree(scope, "missing", options) }
        assertTrue(conversations.watch(scope).first().isEmpty())

        val request = "生成用于数据审计的技能树，重点是异常诊断"
        ai.reply(CREATED)
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("用户还未确认学习基础和课时数量"))
            TREE_OFFER
        }
        conversations.send(scope, request)
        val offer = conversations.watch(scope).first().last()
        assertEquals(TurnKind.TREE_OFFER, offer.kind)
        assertEquals(original, container.library.snapshot())

        val duplicate = JSONObject(CREATED)
        duplicate.getJSONArray("trees").put(JSONObject().put("name", "另一棵树"))
        ai.reply(duplicate.toString())
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("数组数量超过上限"))
            assertTrue(messages.first().text.contains("精确生成 33 节"))
            assertTrue(messages.any { it.text == request })
            newTreeBatch(32)
        }
        expectRule { conversations.generateTree(scope, offer.id, options) }
        val pending = conversations.watch(scope).first().last()
        assertEquals(TurnKind.GENERATE_TREE, pending.kind)
        assertEquals(options, pending.generationOptions)
        assertEquals(original, container.library.snapshot())

        ai.reply(newTreeBatch(34))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("失效模式和方案权衡"))
            assertTrue(messages.first().text.contains("精确生成 33 节"))
            assertEquals("确认生成 · 熟练 · 33 节", messages.last().text)
            newTreeBatch(33)
        }
        conversations.retry(scope, pending.id)
        val draft = conversations.watch(scope).first().last()
        assertEquals(TurnKind.DRAFT, draft.kind)
        assertEquals(original, container.library.snapshot())

        val direction = original.childrenOf(null).first()
        conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        val updated = container.library.snapshot()
        assertEquals(33, updated.childrenOf(updated.childrenOf(direction.id).single().id).size)
    }

    @Test fun disconnectedGenerationIsRepairedWithoutAnotherModelCall() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val conversations = container.conversations
        val original = container.library.snapshot()
        val offerId = offerTree(scope)
        val invalid = JSONObject(newTreeBatch(33))
        val skills = invalid.getJSONArray("trees").getJSONObject(0).getJSONArray("skills")
        skills.getJSONObject(3).put("requires", JSONArray().put("skill_0"))
        skills.getJSONObject(4).put("requires", JSONArray())
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("第 1 层必须恰好有 1 个共同起点"))
            invalid.toString()
        }
        conversations.generateTree(scope, offerId, GenerationOptions(GenerationLevel.INTERMEDIATE, 33..33))
        val draft = conversations.watch(scope).first().last()
        assertEquals(TurnKind.DRAFT, draft.kind)
        assertEquals(original, container.library.snapshot())
        val direction = original.childrenOf(null).first()
        conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        val library = container.library.snapshot()
        val lessons = library.childrenOf(library.childrenOf(direction.id).single().id)
        assertEquals(33, lessons.size)
        val (root, first, second) = lessons
        assertEquals(listOf(root), lessons.filter { library.prerequisitesOf(it.id).isEmpty() })
        assertEquals(setOf(root.id), library.prerequisitesOf(first.id))
        assertEquals(setOf(root.id), library.prerequisitesOf(second.id))
        SkillGraph.validate(lessons.map { it.id }.toSet(), library.edges)
    }

    private suspend fun offerTree(scope: ChatScope, request: String = "生成数据技能树"): String {
        ai.reply(TREE_OFFER)
        container.conversations.send(scope, request)
        val offer = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.TREE_OFFER, offer.kind)
        return offer.id
    }

    @Test fun clearingGenerationPreservesOtherChatsAndSavedTreesAndIgnoresLateReplies() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val scope = ChatScope(ChatMode.GENERATE)
        val other = ChatScope(ChatMode.GENERATE, direction.id)
        val conversations = container.conversations
        val offer = offerTree(scope)
        ai.reply(CREATED)
        conversations.generateTree(scope, offer, GenerationOptions(GenerationLevel.BEGINNER, 2..2))
        val draft = conversations.watch(scope).first().last()
        conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        offerTree(other)
        val otherHistory = conversations.watch(other).first()
        val savedLibrary = container.library.snapshot()
        val waiting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        withTimeout(10_000) {
            ai.enqueue {
                waiting.complete(Unit)
                release.await()
                TREE_OFFER
            }
            val oldRequest = async { conversations.send(scope, "旧会话请求") }
            waiting.await()
            conversations.clearGeneration(scope)
            assertTrue(conversations.watch(scope).first().isEmpty())
            ai.reply("""{"kind":"message","reply":"新会话回复"}""")
            conversations.send(scope, "新会话请求")
            release.complete(Unit)
            oldRequest.await()
        }

        assertEquals(listOf("新会话请求", "新会话回复"), conversations.watch(scope).first().map { it.text })
        assertEquals(otherHistory, conversations.watch(other).first())
        assertEquals(savedLibrary, container.library.snapshot())
    }

    @Test fun newTreePlanningDoesNotSendDirectionsOrPriorTopicsAsChoices() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val conversations = container.conversations
        offerTree(scope, "从零学习文明6游戏")
        ai.reply("""{"kind":"message","reply":"可以继续规划摄影。"}""")
        conversations.send(scope, "摄影")
        val request = "从0到1到100学习文明7游戏"
        ai.enqueue { messages ->
            val prompt = messages.first().text
            assertTrue(prompt.contains("对话中不得询问要放在哪个学习方向"))
            assertTrue(prompt.contains("只要能确定一个可学习的主题"))
            assertTrue(prompt.contains("只有无法确定学习主题"))
            assertTrue(prompt.contains("\"items\":[]"))
            assertEquals(listOf(request), messages.filter { it.role == "user" }.map { it.text })
            TREE_OFFER
        }
        conversations.send(scope, request)
        assertEquals(TurnKind.TREE_OFFER, conversations.watch(scope).first().last().kind)

        ai.enqueue { messages ->
            assertEquals(listOf(request, "重点讲外交"), messages.filter { it.role == "user" }.map { it.text })
            TREE_OFFER
        }
        conversations.send(scope, "重点讲外交")
        assertEquals(TurnKind.TREE_OFFER, conversations.watch(scope).first().last().kind)
    }

    @Test fun repeatedTopicChecksDuplicatesOnlyInTheSelectedDirectionAtAddTime() = runBlocking {
        container.settings.save(ModelSettings(Provider.MINIMAX, apiKey = "test-key"))
        val scope = ChatScope(ChatMode.GENERATE)
        val conversations = container.conversations
        val topic = "从零学习文明 6"
        val offerId = offerTree(scope, topic)
        val response = newTreeBatch(33, "文明6")
        ai.reply(response)
        conversations.generateTree(scope, offerId, GenerationOptions(GenerationLevel.BEGINNER, 33..33))
        val draft = conversations.watch(scope).first().last()
        val direction = container.library.snapshot().childrenOf(null).first()
        conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        val library = container.library.snapshot()
        val original = library.childrenOf(direction.id).single()

        ai.reply("可以继续规划这棵技能树。")
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("本轮尚未收到 App 的课程生成确认"))
            assertTrue(messages.first().text.contains("上一次回复未通过格式校验"))
            assertEquals(topic, messages.last().text)
            assertTrue(messages.none { it.role == "assistant" })
            TREE_OFFER
        }
        conversations.send(scope, topic)
        val turns = conversations.watch(scope).first()
        assertEquals(TurnKind.TREE_OFFER, turns.last().kind)
        assertEquals(1, turns.count { it.kind == TurnKind.CREATED })
        assertEquals(library, container.library.snapshot())

        ai.reply(response)
        conversations.generateTree(scope, turns.last().id, GenerationOptions(GenerationLevel.BEGINNER, 33..33))
        val duplicate = conversations.watch(scope).first().last()
        expectRule { conversations.confirmDraft(scope, duplicate.id, GenerationDestination.Existing(direction.id)) }
        assertEquals(library, container.library.snapshot())

        container.library.delete(original.id)
        ai.reply("""{"kind":"message","reply":"你已有《文明6》技能树（33 个技能点）。"}""")
        conversations.send(scope, topic)
        ai.enqueue { messages ->
            assertTrue(messages.none { it.role == "assistant" })
            assertTrue(messages.none { "确认生成 ·" in it.text })
            assertEquals(topic, messages.last().text)
            TREE_OFFER
        }
        conversations.send(scope, topic)
        val newOffer = conversations.watch(scope).first().last()
        ai.reply(response)
        conversations.generateTree(scope, newOffer.id, GenerationOptions(GenerationLevel.BEGINNER, 33..33))
        val replacement = conversations.watch(scope).first().last()
        conversations.confirmDraft(scope, replacement.id, GenerationDestination.Existing(direction.id))
        val updated = container.library.snapshot()
        val newTree = updated.childrenOf(direction.id).single()
        assertEquals("文明6", newTree.name)
        assertNotEquals(original.id, newTree.id)
        assertEquals(33, updated.childrenOf(newTree.id).size)
    }

    @Test fun unconfirmedDraftAndOldProposalDoNotBlockASecondPlan() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val scope = ChatScope(ChatMode.GENERATE, direction.id)
        val conversations = container.conversations
        val topic = "从零学习文明 7"
        val options = GenerationOptions(GenerationLevel.BEGINNER, 3..3)
        val response = newTreeBatch(3, "文明7")

        ai.reply("""{"kind":"offer_tree","reply":"上一次的文明7提议"}""")
        conversations.send(scope, topic)
        val oldOffer = conversations.watch(scope).first().last()
        ai.reply(response)
        conversations.generateTree(scope, oldOffer.id, options)
        val oldDraft = conversations.watch(scope).first().last()
        assertEquals(TurnKind.DRAFT, oldDraft.kind)
        assertTrue(container.library.snapshot().childrenOf(direction.id).isEmpty())

        ai.enqueue { messages ->
            assertTrue(messages.none { it.role == "assistant" })
            assertTrue(messages.none { "确认生成 ·" in it.text })
            assertEquals(topic, messages.last().text)
            """{"kind":"clarify_tree","reply":"想重点学经济还是外交？"}"""
        }
        conversations.send(scope, topic)
        ai.enqueue { messages ->
            val question = JSONObject(messages.single { it.role == "assistant" }.text)
            assertEquals("clarify_tree", question.getString("kind"))
            assertEquals("想重点学经济还是外交？", question.getString("reply"))
            TREE_OFFER
        }
        conversations.send(scope, "第二个")
        val newOffer = conversations.watch(scope).first().last()
        ai.enqueue { messages ->
            val planningKinds = messages.filter { it.role == "assistant" }.map { JSONObject(it.text).getString("kind") }
            assertEquals(listOf("clarify_tree", "offer_tree"), planningKinds)
            response
        }
        conversations.generateTree(scope, newOffer.id, options)
        val newDraft = conversations.watch(scope).first().last()
        conversations.confirmDraft(scope, newDraft.id, GenerationDestination.Existing(direction.id))
        val history = conversations.watch(scope).first()
        assertEquals(oldDraft, history.single { it.id == oldDraft.id })
        assertEquals("文明7", container.library.snapshot().childrenOf(direction.id).single().name)
    }

    @Test fun scopedGenerationCanAttachOnlyToNodesInItsTargetTree() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "当前树")
        val otherTree = add(direction, EntryKind.TREE, "另一棵树")
        val current = add(tree, EntryKind.SKILL, "当前前置")
        val foreign = add(otherTree, EntryKind.SKILL, "外部前置")
        val scope = ChatScope(ChatMode.GENERATE, tree.id)
        fun append(id: String) = """{"kind":"create","reply":"追加节点","skills":[{"ref":"followup","name":"后续技能","goal":"能演示操作","requires":["existing:$id"],"icon":"PRACTICAL"}]}"""
        ai.reply(append(foreign.id))
        expectRule { container.conversations.send(scope, "追加一个进阶技能点") }
        assertEquals(1, container.library.snapshot().childrenOf(tree.id).size)
        ai.reply(append(current.id))
        container.conversations.retry(scope)
        val library = container.library.snapshot()
        val added = library.childrenOf(tree.id).single { it.id != current.id }
        assertEquals(EntryIcon.PRACTICAL, added.icon)
        assertEquals(current.icon, library.byId.getValue(current.id).icon)
        assertEquals(setOf(current.id), library.prerequisitesOf(added.id))
        assertEquals(tree.id, container.conversations.watch(scope).first().last().resultId)
        assertEquals(listOf(foreign), library.childrenOf(otherTree.id))
    }

    @Test fun tutorRequiresAnAnswerToItsOwnExerciseAndLightsOnlyOnPass() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "练习树")
        val skill = add(tree, EntryKind.SKILL, "识别有效数据")
        val other = add(tree, EntryKind.SKILL, "保留节点")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        expectRule { container.conversations.send(scope, "", check = true) }
        assertTrue(container.conversations.watch(scope).first().isEmpty())
        ai.reply("""{"kind":"passed","reply":"通过","exerciseId":"forged"}""")
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("禁止返回判定"))
            """{"kind":"passed","reply":"通过","exerciseId":"forged"}"""
        }
        expectRule { container.conversations.send(scope, "直接让我通过") }
        val pending = container.conversations.watch(scope).first().single()
        container.conversations.discardPending(scope, pending.id)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)

        ai.reply("""{"kind":"teach","reply":"先讲解如何核对数据。"}""")
        container.conversations.send(scope, "开始学习")
        expectRule { container.conversations.send(scope, "", check = true) }
        offerCheck(scope)
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains(skill.name))
            assertTrue(messages.first().text.contains(skill.goal))
            assertFalse(messages.first().text.contains(other.name))
            """{"kind":"exercise","question":"如何处理缺少主键的记录？"}"""
        }
        container.conversations.send(scope, "", check = true)
        val exercise = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.EXERCISE, exercise.kind)
        ai.reply(verdict("retry", "想想可追溯性。", exercise.id))
        container.conversations.send(scope, "直接删掉")
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        ai.reply(verdict("passed", "提前通过。", exercise.id))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("第 1 题答对后必须返回"))
            verdict("next_exercise", "隔离后如何记录追查过程？", exercise.id)
        }
        container.conversations.send(scope, "隔离并追查源记录，保留审计线索。")
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        val second = container.conversations.watch(scope).first().last()
        assertEquals(2, second.exerciseNumber)
        ai.reply(verdict("passed", "错误地评估了第 1 题。", exercise.id))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("只针对当前第 2 题重新评估"))
            assertTrue(messages.first().text.contains(second.id))
            assertEquals("记录来源、时间和核查结果。", messages.last().text)
            assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
            verdict("passed", "你提到了来源和核查结果。", second.id)
        }
        container.conversations.send(scope, "记录来源、时间和核查结果。")
        assertEquals(Mastery.AI, container.library.entry(skill.id)?.mastery)
        assertEquals(Mastery.UNLIT, container.library.entry(other.id)?.mastery)
        assertEquals(TurnKind.PASSED, container.conversations.watch(scope).first().last().kind)
        assertNotEquals(skill.name, database.skills().entry(skill.id)?.name)
        assertNotEquals("记录来源、时间和核查结果。", database.skills().recentTurns(scope.id, 2).last().text)
    }

    @Test fun feedbackOnlyDoesNotBecomeAQuestionAndLegacyContinueCanRecoverTheCurrentNumber() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "补题测试")
        val skill = add(tree, EntryKind.SKILL, "追踪证据")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        val conversations = container.conversations
        offerCheck(scope)
        ai.reply("""{"kind":"exercise","question":"如何判断风险降低？"}""")
        conversations.send(scope, "", check = true)
        val first = conversations.watch(scope).first().last()
        val feedback = "答得对！要有可归因的证据链。接下来看第二题，检验推广路径的把握。"
        ai.reply(JSONObject().put("kind", "next_exercise").put("reply", feedback)
            .put("exerciseId", first.id).toString())
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("缺少 question"))
            verdict("next_exercise", "请说明推广前应验证的指标。", first.id)
        }
        conversations.send(scope, "核对过程、结果和价值指标")
        val second = conversations.watch(scope).first().last()
        assertEquals(2, second.exerciseNumber)
        assertFalse(conversations.watch(scope).first().any { it.text == feedback })

        // 模拟旧版把点评误存为第 2 题，并留下截图中的待重试快捷回复。
        val dao = database.skills()
        val row = checkNotNull(dao.turn(scope.id, second.id))
        dao.updateTurn(row.copy(text = SecretBox("lifeskill-tests").seal(feedback, "${row.id}:turn")))
        ai.enqueue { throw RuleViolation("AI 的评估没有对应当前练习，请重试。") }
        expectRule { conversations.send(scope, TutorRules.CONTINUE_REPLY) }
        ai.reply(verdict("passed", "错误地把继续当作作答。", second.id))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("重新出当前第 2 题"))
            assertTrue(messages.first().text.contains("重新出本题时只返回 exercise"))
            """{"kind":"exercise","question":"推广前怎样建立对照？"}"""
        }
        conversations.retry(scope)
        val repaired = conversations.watch(scope).first()
        val replacement = repaired.last()
        assertEquals("推广前怎样建立对照？", replacement.text)
        assertEquals(listOf(first.id, replacement.id), repaired.filter { it.kind == TurnKind.EXERCISE }.map { it.id })
        assertEquals(TurnKind.MESSAGE, repaired.single { it.id == second.id }.kind)
        assertEquals(2, replacement.exerciseNumber)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        ai.reply(verdict("passed", "已说明对照方法。", replacement.id))
        conversations.send(scope, "选取相似工况，按相同口径对照试点前后记录")
        assertEquals(Mastery.AI, container.library.entry(skill.id)?.mastery)
    }

    @Test fun tutorCanChooseAThirdQuestionButRejectsRepeatedQuestionsAndAFourth() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "三题练习")
        val skill = add(tree, EntryKind.SKILL, "核对有效数据")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        val conversations = container.conversations
        offerCheck(scope)
        ai.reply("""{"kind":"exercise","question":"怎样核对记录？"}""")
        conversations.send(scope, "", check = true)
        val first = conversations.watch(scope).first().last()
        val dao = database.skills()
        val firstRow = checkNotNull(dao.turn(scope.id, first.id))
        dao.updateTurn(firstRow.copy(exerciseNumber = null))
        val second = advanceToSecondQuestion(scope, "对照源记录。")
        ai.reply(verdict("next_exercise", "怎样核对记录？", second.id))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("下一题的题干重复了"))
            JSONObject(verdict("next_exercise", "如何确认复核结果？", second.id))
                .put("choices", JSONArray(listOf("看复核记录", "删除原记录"))).toString()
        }
        conversations.send(scope, "保留处理依据。")
        val third = conversations.watch(scope).first().last()
        assertEquals(3, third.exerciseNumber)
        assertEquals(listOf("看复核记录", "删除原记录"), third.choices)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        ai.reply(verdict("retry", "先找复核证据。", third.id))
        conversations.send(scope, "B. 删除原记录")
        assertEquals(third.id, conversations.watch(scope).first().last().exerciseId)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        ai.reply(verdict("next_exercise", "第四题", third.id))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("第 3 题后不得再出题"))
            verdict("passed", "三个情境都判断正确。", third.id)
        }
        conversations.send(scope, "A. 看复核记录")
        assertEquals(Mastery.AI, container.library.entry(skill.id)?.mastery)
        assertEquals(listOf(null, 2, 3), conversations.watch(scope).first()
            .filter { it.kind == TurnKind.EXERCISE }.map { it.exerciseNumber })
    }

    @Test fun closingAndReopeningDatabaseRestoresProgressAndConversation() = runBlocking {
        val file = "verification-${UUID.randomUUID()}.db"
        try {
            val savedId = withDiskDatabase(file) { disk ->
                val saved = fixture(disk, ai)
                saved.library.initialize()
                val direction = saved.library.snapshot().childrenOf(null).first()
                val tree = saved.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "持久化"))
                val skill = saved.library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "记录", "能复述"))
                ai.reply("""{"kind":"teach","reply":"先说说你知道什么。"}""")
                saved.conversations.send(ChatScope(ChatMode.TUTOR, skill.id), "继续学习")
                markLegacyLit(skill.id, disk)
                skill.id
            }
            withDiskDatabase(file) { disk ->
                val restored = fixture(disk, ai)
                restored.library.initialize()
                assertEquals(Mastery.MANUAL, restored.library.entry(savedId)?.mastery)
                val history = restored.conversations.watch(ChatScope(ChatMode.TUTOR, savedId)).first()
                assertEquals(listOf("继续学习", "先说说你知道什么。"), history.map { it.text })
            }
        } finally {
            context.deleteDatabase(file)
        }
        Unit
    }

    @Test fun attachmentsSurviveDatabaseReopenAndRetryAndAreRemovedByRestart() = runBlocking {
        val file = "attachments-${UUID.randomUUID()}.db"
        val attachment = ChatAttachment(UUID.randomUUID().toString(), "学习资料.txt", "text/plain")
        val material = "保留原始记录，核对时间戳。"
        val bytes = ByteArray(512 * 1024) { (it % 233).toByte() }
        try {
            val scope = withDiskDatabase(file) { disk ->
                val saved = fixture(disk, ai)
                saved.library.initialize()
                val direction = saved.library.snapshot().childrenOf(null).first()
                val course = saved.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "附件测试"))
                val lesson = saved.library.save(EntryEdit(null, course.id, EntryKind.SKILL, "资料学习", "核对资料"))
                val scope = ChatScope(ChatMode.TUTOR, lesson.id)
                ai.enqueue { messages ->
                    assertTrue(messages.last().text.contains(material))
                    throw RuleViolation("模拟服务暂时不可用")
                }
                expectRule { saved.conversations.send(scope, "", files = listOf(AttachmentDraft(attachment, bytes, material))) }
                assertEquals(3, disk.skills().attachmentPartCount(scope.id, attachment.id))
                assertFalse(checkNotNull(disk.skills().attachmentPart(scope.id, attachment.id, 0))
                    .contentEquals(bytes.copyOfRange(0, 192 * 1024)))
                scope
            }
            withDiskDatabase(file) { disk ->
                val reopened = fixture(disk, ai)
                val pending = reopened.conversations.watch(scope).first().single()
                assertEquals(listOf(attachment), pending.attachments)
                assertTrue(bytes.contentEquals(reopened.attachments.load(scope.id, attachment).bytes))
                ai.enqueue { messages ->
                    assertTrue(messages.last().text.contains(material))
                    """{"kind":"teach","reply":"先核对资料的时间戳。"}"""
                }
                reopened.conversations.retry(scope, pending.id)
                assertTrue(reopened.conversations.exportConversation(checkNotNull(scope.ownerId)).markdown
                    .contains("附件：学习资料.txt"))
                ai.reply("""{"kind":"teach","reply":"我们从第一节开始。"}""")
                reopened.conversations.restartLesson(scope, "")
                assertEquals(0, disk.skills().attachmentPartCount(scope.id, attachment.id))
            }
        } finally {
            context.deleteDatabase(file)
        }
        Unit
    }

    @Test fun importsDocumentTextAndRoutesImageToVisionOrLocalOcr() = runBlocking {
        val directory = File(context.cacheDir, "conversation-shares/${UUID.randomUUID()}").apply { mkdirs() }
        fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.conversation-share", file)
        try {
            val pdf = File(directory, "资料.pdf")
            PDFBoxResourceLoader.init(context)
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 20f)
                    stream.newLineAtOffset(30f, 720f)
                    stream.showText("Read the invoice")
                    stream.endText()
                }
                document.save(pdf)
            }
            assertEquals("Read the invoice", container.attachments.import(uri(pdf), camera = false).text)

            val docx = File(directory, "资料.docx")
            ZipOutputStream(docx.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                    <w:p><w:r><w:t>逐条核对原件。</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
                zip.closeEntry()
            }
            assertEquals("逐条核对原件。", container.attachments.import(uri(docx), camera = false).text)

            val image = File(directory, "资料.png")
            val bitmap = Bitmap.createBitmap(800, 180, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(Color.WHITE)
                    drawText("READ THE INVOICE", 24f, 105f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 58f })
                }
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            val draft = container.attachments.import(uri(image), camera = false)
            val scope = ChatScope(ChatMode.GENERATE)
            ai.enqueue { messages ->
                assertTrue(messages.last().images.single().startsWith("data:image/jpeg;base64,"))
                TREE_OFFER
            }
            container.conversations.send(scope, "", files = listOf(draft))
            val ocr = container.attachments.addToMessage(scope.id, ModelMessage("user", ""), listOf(draft.attachment),
                ModelSettings(Provider.MINIMAX), required = true)
            assertTrue(ocr.images.isEmpty())
            assertTrue(ocr.text.contains("READ THE INVOICE"))
        } finally {
            directory.deleteRecursively()
        }
        Unit
    }

    @Test fun examShortcutCountsCurrentTeachingRepliesAndRequiresANewAnswer() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "快捷考试")
        val skill = add(tree, EntryKind.SKILL, "核对账单")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        val conversations = container.conversations
        suspend fun teach(text: String) {
            ai.reply("""{"kind":"teach","reply":"核对账单中的记录。"}""")
            conversations.send(scope, text)
        }

        teach("开始学习")
        ai.reply("""{"kind":"off_topic","reply":"请回到学习目标。"}""")
        conversations.send(scope, "换个话题")
        repeat(3) { teach(TutorRules.CONTINUE_REPLY) }
        expectRule { conversations.send(scope, TutorRules.EXAM_REPLY, check = true) }
        assertEquals(10, conversations.watch(scope).first().size)
        teach(TutorRules.CONTINUE_REPLY)
        ai.reply("""{"kind":"exercise","question":"应该核对什么？","choices":["核对金额","忽略金额"]}""")
        conversations.send(scope, TutorRules.EXAM_REPLY, check = true)
        val history = conversations.watch(scope).first()
        assertEquals(TurnKind.CHECK, history[history.lastIndex - 1].kind)
        assertEquals(TutorRules.EXAM_REPLY, history[history.lastIndex - 1].text)
        assertEquals(TurnKind.EXERCISE, history.last().kind)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        expectRule { conversations.send(scope, TutorRules.EXAM_REPLY, check = true) }
        assertEquals(history, conversations.watch(scope).first())

        ai.reply("""{"kind":"teach","reply":"从新例子开始。"}""")
        conversations.restartLesson(scope, "用另一个例子")
        expectRule { conversations.send(scope, TutorRules.EXAM_REPLY, check = true) }
        assertEquals(2, conversations.watch(scope).first().size)
    }

    @Test fun invalidInputAndMalformedReplyDoNotWriteLibraryOrLoseTheDraft() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val original = container.library.snapshot()
        val atLimit = "测".repeat(ConversationRepository.INPUT_LIMIT)
        for (input in listOf("", "　\n ", atLimit + "试")) {
            expectRule { container.conversations.send(scope, input) }
        }
        assertTrue(container.conversations.watch(scope).first().isEmpty())

        ai.enqueue { messages ->
            assertEquals(atLimit, messages.last().text)
            """{"kind":"create","reply":"返回截断"""
        }
        ai.reply("""{"kind":"create","reply":"再次截断"""")
        expectRule { container.conversations.send(scope, " $atLimit ") }
        assertEquals(original, container.library.snapshot())
        val pending = container.conversations.watch(scope).first().single()
        assertEquals(atLimit, pending.text)
        container.conversations.discardPending(scope, pending.id)
        ai.reply("""{"kind":"message","reply":"可以从小练习开始。"}""")
        container.conversations.send(scope, "请先给建议")
        assertEquals(listOf("请先给建议", "可以从小练习开始。"), container.conversations.watch(scope).first().map { it.text })
        assertEquals(original, container.library.snapshot())
    }

    @Test fun blankTutorReplyRetriesOnceAndKeepsTheOriginalMessageIfRepairFails() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "回复恢复")
        val skill = add(tree, EntryKind.SKILL, "分辨目标用户")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        val conversations = container.conversations
        val request = "好的，继续"

        ai.enqueue { throw RecoverableReply("模型没有返回文字，请重试。", "请返回一个完整的 JSON 对象。") }
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("请返回一个完整的 JSON 对象。"))
            assertEquals(request, messages.last().text)
            """{"kind":"teach","reply":""}"""
        }
        ai.reply("""{"kind":"teach","reply":"下一步是核对角色。"}""")

        expectRule { conversations.send(scope, request) }
        val pending = conversations.watch(scope).first().single()
        assertEquals(request, pending.text)
        conversations.retry(scope)
        val history = conversations.watch(scope).first()
        assertEquals(2, history.size)
        assertEquals(pending.id, history.first().id)
        assertEquals("下一步是核对角色。", history.last().text)
    }

    @Test fun requestIdReachesUiBeforeTutorModelStarts() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "请求状态")
        val skill = add(tree, EntryKind.SKILL, "识别学习目标")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        var queuedId: String? = null
        var callbackSeen = false
        ai.enqueue {
            assertTrue(callbackSeen)
            """{"kind":"teach","reply":"先明确要解决的问题。"}"""
        }

        container.conversations.send(scope, "开始学习") { id ->
            queuedId = id
            callbackSeen = true
        }

        assertEquals(queuedId, container.conversations.watch(scope).first().first().id)
    }

    @Test fun overLimitGenerationRepairsOnceBeforeWriting() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "边界数量")
        val scope = ChatScope(ChatMode.GENERATE, tree.id)
        val request = "新增 30 个独立技能点"
        val before = container.library.snapshot()
        ai.reply(skillBatch(31))
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("数组数量超过上限"))
            assertEquals(request, messages.last().text)
            assertEquals(before, container.library.snapshot())
            skillBatch(30)
        }
        container.conversations.send(scope, request)
        assertEquals(30, container.library.snapshot().childrenOf(tree.id).size)
        assertEquals(TurnKind.CREATED, container.conversations.watch(scope).first().last().kind)

        val after = container.library.snapshot()
        ai.reply(skillBatch(31))
        ai.reply(skillBatch(31))
        expectRule { container.conversations.send(scope, "再新增一批") }
        assertEquals(after, container.library.snapshot())
        assertEquals("再新增一批", container.conversations.watch(scope).first().last().text)
    }

    @Test fun savedModelSettingsKeepKeysSeparateAndEncrypted() = runBlocking {
        val file = "verification-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(file, mode)
        }
        val preset = ModelSettings(Provider.MINIMAX, apiKey = "test-preset-key")
        val custom = ModelSettings(Provider.CUSTOM, "https://example.com/v1", "test-model", "test-custom-key")
        try {
            val first = LocalSettings(isolated, SecretBox("lifeskill-tests"))
            first.save(preset)
            first.save(custom)
            val reopened = LocalSettings(isolated, SecretBox("lifeskill-tests"))
            assertEquals(custom, reopened.active())
            assertEquals(preset, reopened.load(Provider.MINIMAX))
            expectRule { reopened.save(custom.copy(baseUrl = "http://example.com")) }
            assertEquals(custom, reopened.active())
            val stored = context.getSharedPreferences(file, Context.MODE_PRIVATE).all.values
            assertFalse(stored.any { it == preset.apiKey || it == custom.apiKey })
            reopened.save(custom.copy(apiKey = ""))
            assertEquals("", reopened.active().apiKey)
            assertEquals(preset, reopened.load(Provider.MINIMAX))
        } finally {
            context.deleteSharedPreferences(file)
        }
        Unit
    }

    private fun skillBatch(count: Int): String {
        val skills = JSONArray().apply {
            repeat(count) { index ->
                put(JSONObject().put("ref", "skill_$index").put("name", "技能$index")
                    .put("goal", "能说明概念").put("requires", JSONArray()))
            }
        }
        return JSONObject().put("kind", "create").put("reply", "已规划").put("skills", skills).toString()
    }

    private fun newTreeBatch(count: Int, name: String = "数据审计"): String {
        val response = JSONObject(skillBatch(count))
        val skills = response.remove("skills") as JSONArray
        for (index in 1 until count) {
            skills.getJSONObject(index).put("requires", JSONArray().put("skill_${(index - 1) / 2}"))
        }
        val tree = JSONObject().put("name", name).put("skills", skills)
        return response.put("trees", JSONArray().put(tree)).toString()
    }

    @Test fun assessmentCannotLightEditedSkillWhileWaiting() = runBlocking {
        withTimeout(10_000) {
            val direction = container.library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "进度优先")
            val skill = add(tree, EntryKind.SKILL, "点亮状态")
            val scope = ChatScope(ChatMode.TUTOR, skill.id)
            offerCheck(scope)
            ai.reply("""{"kind":"exercise","question":"你如何核对结果？"}""")
            container.conversations.send(scope, "", check = true)
            val exercise = advanceToSecondQuestion(scope, "先核对来源。")
            val waiting = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            ai.enqueue {
                waiting.complete(Unit)
                release.await()
                verdict("passed", "答对了。", exercise.id)
            }
            val answer = async { expectRule { container.conversations.send(scope, "用源记录对照。") } }
            waiting.await()
            container.library.save(EntryEdit(skill.id, tree.id, EntryKind.SKILL, skill.name, "学习新的核对方法"))
            release.complete(Unit)
            answer.await()
            assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
            assertEquals("用源记录对照。", container.conversations.watch(scope).first().last().text)
        }
    }

    @Test fun restartingClearsOnlyThisChatAndUsesCurrentRequirements() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "重学测试树")
        val skill = add(tree, EntryKind.SKILL, "重学测试节点")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        val other = add(tree, EntryKind.SKILL, "其他节点")
        val otherScope = ChatScope(ChatMode.TUTOR, other.id)
        ai.reply("""{"kind":"teach","reply":"其他节点的记录"}""")
        container.conversations.send(otherScope, "开始学习")
        markLegacyLit(skill.id)
        offerCheck(scope)
        ai.reply("""{"kind":"exercise","question":"旧版练习"}""")
        container.conversations.send(scope, "", check = true)
        val oldQuiz = container.conversations.watch(scope).first().last()

        ai.enqueue { throw RuleViolation("临时断网") }
        expectRule { container.conversations.restartLesson(scope, "用账单举例") }
        val restart = container.conversations.watch(scope).first().single()
        assertEquals(TurnKind.RESTART, restart.kind)
        assertEquals(2, container.conversations.watch(otherScope).first().size)

        ai.enqueue { messages ->
            assertEquals(2, messages.size)
            assertTrue(messages.first().text.contains(skill.goal))
            assertTrue(messages.first().text.contains("当前待答练习：null"))
            assertTrue(messages.last().text.contains("用账单举例"))
            """{"kind":"off_topic","reply":"先回到这个技能。"}"""
        }
        container.conversations.retry(scope)

        ai.enqueue { messages ->
            assertTrue(messages.none { "旧版" in it.text || oldQuiz.id in it.text })
            assertTrue(messages.first().text.contains("当前待答练习：null"))
            """{"kind":"teach","reply":"用账单重新讲解"}"""
        }
        container.conversations.send(scope, "请继续用账单解释")
        val history = container.conversations.watch(scope).first()
        assertEquals(listOf(restart.id), history.filter { it.kind == TurnKind.RESTART }.map { it.id })
        assertEquals(4, history.size)
        assertTrue(history.none { it.id == oldQuiz.id })
        assertEquals("用账单重新讲解", history.last().text)
        assertEquals(Mastery.MANUAL, container.library.entry(skill.id)?.mastery)
    }

    @Test fun lateReplyFromBeforeRestartCannotEnterTheNewLesson() = runBlocking {
        withTimeout(10_000) {
            val direction = container.library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "并发重学")
            val skill = add(tree, EntryKind.SKILL, "开始新一轮")
            val scope = ChatScope(ChatMode.TUTOR, skill.id)
            val waiting = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            ai.enqueue {
                waiting.complete(Unit)
                release.await()
                """{"kind":"teach","reply":"迟到的旧讲解"}"""
            }
            val old = async { container.conversations.send(scope, "旧请求") }
            waiting.await()
            ai.reply("""{"kind":"teach","reply":"新一轮讲解"}""")
            container.conversations.restartLesson(scope, "")
            release.complete(Unit)
            old.await()
            val history = container.conversations.watch(scope).first()
            assertEquals(listOf(TurnKind.RESTART, TurnKind.MESSAGE), history.map { it.kind })
            assertEquals("新一轮讲解", history.last().text)
        }
    }

    private suspend fun offerCheck(scope: ChatScope) {
        ai.reply("""{"kind":"offer_check","reply":"完成本节讲解。"}""")
        container.conversations.send(scope, "继续讲解")
    }

    private suspend fun <T> withDiskDatabase(file: String, action: suspend (LifeSkillDatabase) -> T): T {
        val disk = Room.databaseBuilder(context, LifeSkillDatabase::class.java, file).build()
        return try {
            action(disk)
        } finally {
            disk.close()
        }
    }

    private suspend fun add(parent: Entry, kind: EntryKind, name: String, prerequisites: Set<String> = emptySet()) =
        container.library.save(EntryEdit(null, parent.id, kind, name, "能识别异常并说明原因", prerequisites))

    private suspend fun markLegacyLit(id: String, db: LifeSkillDatabase = database) {
        val dao = db.skills()
        val row = checkNotNull(dao.entry(id))
        dao.updateEntry(row.copy(mastery = Mastery.MANUAL.name, masteryVersion = 1))
    }

    private suspend fun expectRule(action: suspend () -> Unit) {
        try {
            action()
            fail("应拒绝本次操作")
        } catch (_: RuleViolation) {
            // 验证拒绝后保留原数据，由各测试检查最终状态。
        }
    }

    private suspend fun advanceToSecondQuestion(scope: ChatScope, answer: String): Turn {
        val conversations = container.conversations
        val first = conversations.watch(scope).first().last()
        ai.reply(verdict("next_exercise", "如何保留处理依据？", first.id))
        conversations.send(scope, answer)
        return conversations.watch(scope).first().last()
    }

    companion object {
        const val TREE_OFFER = """{"kind":"offer_tree","reply":"可以为你规划这棵技能树。"}"""
        const val CREATED = """{"kind":"create","reply":"已规划","directionName":"数据能力","trees":[{"name":"数据质量","icon":"TARGET","skills":[{"ref":"base","name":"基础","goal":"能解释数据质量","requires":[],"icon":"CHART"},{"ref":"practice","name":"实践","goal":"能找出异常记录","requires":["base"],"icon":"UNKNOWN"}]}]}"""

        fun verdict(kind: String, text: String, id: String): String =
            JSONObject().put("kind", kind).put(if (kind == "next_exercise") "question" else "reply", text)
                .put("exerciseId", id).toString()
    }
}
