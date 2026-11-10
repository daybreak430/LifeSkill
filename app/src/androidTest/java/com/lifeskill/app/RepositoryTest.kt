package com.lifeskill.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.data.ChatRow
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.LocalSettings
import com.lifeskill.app.data.SecretBox
import com.lifeskill.app.data.TurnRow
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.Speaker
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
import java.util.UUID

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
            ai.reply("""{"kind":"exercise","reply":"请说出操作步骤。"}""")
            conversations.send(scope, "", check = true)
            val exercise = conversations.watch(scope).first().last()
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
            assertTrue(conversations.watch(scope).first().last().text.contains("点亮前置技能后"))

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

    @Test fun confirmingInvalidDraftRollsBackWholeBatch() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val invalid = """{"kind":"create","reply":"方案","directionName":"设计","trees":[{"name":"树 A","skills":[]},{"name":"树 B","skills":[{"ref":"x","name":"节点","goal":"能解释用途","requires":["x"]}]}]}"""
        seedLegacyDraft(scope, invalid)
        val preview = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.DRAFT, preview.kind)
        expectRule { container.conversations.confirmDraft(scope, preview.id, GenerationDestination.New("设计")) }
        assertFalse(container.library.snapshot().entries.any { it.name in setOf("设计", "树 A", "树 B") })
        assertEquals(preview, container.conversations.watch(scope).first().last())

        ai.reply(invalid)
        ai.enqueue { messages ->
            assertTrue(messages.first().text.contains("上一版技能树没有通过校验"))
            CREATED
        }
        container.conversations.send(scope, "重新生成数据技能树")
        val secondDraft = container.conversations.watch(scope).first().last()
        container.conversations.confirmDraft(scope, secondDraft.id, GenerationDestination.New("数据能力"))
        val history = container.conversations.watch(scope).first()
        assertEquals(secondDraft.id, history.last().id)
        assertEquals(TurnKind.CREATED, history.last().kind)
        val treeId = history.last().resultId
        assertNotNull(treeId)
        assertEquals(2, container.library.snapshot().childrenOf(treeId).size)
        assertEquals(1, container.library.snapshot().edges.size)
    }

    @Test fun confirmingLegacyDraftKeepsLocalPrerequisitesAndIgnoresForeignReferences() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        val direction = container.library.snapshot().childrenOf(null).first()
        seedLegacyDraft(scope, """
            {"kind":"create","reply":"已规划","trees":[
              {"name":"基础","skills":[{"ref":"foreign","name":"外树节点","goal":"能说明用途","requires":[]}]},
              {"name":"实践","skills":[
                {"ref":"base","name":"本树前置","goal":"掌握概念","requires":[]},
                {"ref":"follow","name":"本树后续","goal":"实际运用","requires":[" existing:base ","本树前置","foreign","existing:missing"]}
              ]}
            ]}
        """.trimIndent())
        val draft = container.conversations.watch(scope).first().single()
        assertTrue(draft.text.contains("略过 2 条"))
        assertTrue(container.library.snapshot().childrenOf(direction.id).isEmpty())

        container.conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(direction.id))
        val library = container.library.snapshot()
        val trees = library.childrenOf(direction.id)
        assertEquals(listOf("基础", "实践"), trees.map(Entry::name))
        assertEquals(listOf(EntryIcon.BOOK, EntryIcon.TOOLS), trees.map(Entry::icon))
        val (base, follow) = library.childrenOf(trees.last().id)
        assertEquals(EntryIcon.BOOK, base.icon)
        assertEquals(EntryIcon.EXPLORE, follow.icon)
        assertEquals(setOf(base.id), library.prerequisitesOf(follow.id))
        val created = container.conversations.watch(scope).first().single()
        assertEquals(TurnKind.CREATED, created.kind)
        assertTrue(created.text.contains("略过 2 条"))
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
            val firstWaiting = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            ai.enqueue {
                firstWaiting.complete(Unit)
                releaseFirst.await()
                CREATED
            }
            ai.reply(CREATED)
            val first = async { container.conversations.send(scope, "生成数据技能树") }
            firstWaiting.await()
            container.conversations.retry(scope)
            releaseFirst.complete(Unit)
            first.await()
            val history = container.conversations.watch(scope).first()
            assertEquals(2, history.size)
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
        ai.reply("""{"kind":"create","reply":"已规划","trees":[{"name":"信息化基础","icon":"TREE","skills":[{"ref":"intro","name":"认识信息系统","goal":"能用 SQL 查询数据","requires":[],"icon":"CODE"}]}]}""")
        container.conversations.send(scope, "生成信息化技能树")
        val draft = container.conversations.watch(scope).first().last()
        assertEquals(source.id, draft.preview?.suggestedDirectionId)
        assertTrue(container.library.snapshot().childrenOf(source.id).isEmpty())
        container.conversations.confirmDraft(scope, draft.id, GenerationDestination.Existing(destination.id))
        val library = container.library.snapshot()
        assertTrue(library.childrenOf(source.id).isEmpty())
        val tree = library.childrenOf(destination.id).single()
        assertEquals("信息化基础", tree.name)
        assertEquals(EntryIcon.CHART, tree.icon)
        assertEquals(EntryIcon.CODE, library.childrenOf(tree.id).single().icon)
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
            """{"kind":"exercise","reply":"如何处理缺少主键的记录？"}"""
        }
        container.conversations.send(scope, "", check = true)
        val exercise = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.EXERCISE, exercise.kind)
        ai.reply(verdict("retry", "想想可追溯性。", exercise.id))
        container.conversations.send(scope, "直接删掉")
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
        ai.reply(verdict("passed", "你提到了隔离和追查。", exercise.id))
        container.conversations.send(scope, "隔离并追查源记录，保留审计线索。")
        assertEquals(Mastery.AI, container.library.entry(skill.id)?.mastery)
        assertEquals(Mastery.UNLIT, container.library.entry(other.id)?.mastery)
        assertEquals(TurnKind.PASSED, container.conversations.watch(scope).first().last().kind)
        assertNotEquals(skill.name, database.skills().entry(skill.id)?.name)
        assertNotEquals("隔离并追查源记录，保留审计线索。", database.skills().recentTurns(scope.id, 2).last().text)
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
        ai.reply("""{"kind":"exercise","reply":"应该核对什么？","choices":["核对金额","忽略金额"]}""")
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

    @Test fun assessmentCannotLightEditedSkillWhileWaiting() = runBlocking {
        withTimeout(10_000) {
            val direction = container.library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "进度优先")
            val skill = add(tree, EntryKind.SKILL, "点亮状态")
            val scope = ChatScope(ChatMode.TUTOR, skill.id)
            offerCheck(scope)
            ai.reply("""{"kind":"exercise","reply":"你如何核对结果？"}""")
            container.conversations.send(scope, "", check = true)
            val exercise = container.conversations.watch(scope).first().last()
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
        ai.reply("""{"kind":"exercise","reply":"旧版练习"}""")
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

    companion object {
        const val CREATED = """{"kind":"create","reply":"已规划","directionName":"数据能力","trees":[{"name":"数据质量","icon":"TARGET","skills":[{"ref":"base","name":"基础","goal":"能解释数据质量","requires":[],"icon":"CHART"},{"ref":"practice","name":"实践","goal":"能找出异常记录","requires":["base"],"icon":"UNKNOWN"}]}]}"""

        fun verdict(kind: String, text: String, id: String): String =
            JSONObject().put("kind", kind).put("reply", text).put("exerciseId", id).toString()
    }
}
