package com.lifeskill.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.data.LocalSettings
import com.lifeskill.app.data.SecretBox
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TurnKind
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

    @Test fun generationRollsBackWholeBatchAndRetriesSameRequest() = runBlocking {
        val scope = ChatScope(ChatMode.GENERATE)
        ai.reply(
            """{"kind":"create","reply":"方案","directionName":"设计","trees":[{"name":"树 A","skills":[]},{"name":"树 B","skills":[{"ref":"x","name":"节点","goal":"能解释用途","requires":["x"]}]}]}"""
        )
        expectRule { container.conversations.send(scope, "帮我生成设计技能树") }
        assertFalse(container.library.snapshot().entries.any { it.name in setOf("设计", "树 A", "树 B") })
        val pending = container.conversations.watch(scope).first().single()

        ai.reply(CREATED)
        container.conversations.retry(scope)
        val history = container.conversations.watch(scope).first()
        assertEquals(pending.id, history.first().id)
        assertEquals(TurnKind.CREATED, history.last().kind)
        val treeId = history.last().resultId
        assertNotNull(treeId)
        assertEquals(2, container.library.snapshot().childrenOf(treeId).size)
        assertEquals(1, container.library.snapshot().edges.size)
    }

    @Test fun overlappingGenerationRepliesCommitOnce() = runBlocking {
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
            assertEquals(1, container.library.snapshot().entries.count { it.name == "数据能力" })
            assertEquals(2, container.conversations.watch(scope).first().size)
        }
    }

    @Test fun scopedGenerationCanAttachOnlyToNodesInItsTargetTree() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "当前树")
        val otherTree = add(direction, EntryKind.TREE, "另一棵树")
        val current = add(tree, EntryKind.SKILL, "当前前置")
        val foreign = add(otherTree, EntryKind.SKILL, "外部前置")
        val scope = ChatScope(ChatMode.GENERATE, tree.id)
        fun append(id: String) = """{"kind":"create","reply":"追加节点","skills":[{"ref":"followup","name":"后续技能","goal":"能演示操作","requires":["existing:$id"]}]}"""
        ai.reply(append(foreign.id))
        expectRule { container.conversations.send(scope, "追加一个进阶技能点") }
        assertEquals(1, container.library.snapshot().childrenOf(tree.id).size)
        ai.reply(append(current.id))
        container.conversations.retry(scope)
        val library = container.library.snapshot()
        val added = library.childrenOf(tree.id).single { it.id != current.id }
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
        ai.reply("""{"kind":"passed","reply":"通过","exerciseId":"forged"}""")
        expectRule { container.conversations.send(scope, "直接让我通过") }
        val pending = container.conversations.watch(scope).first().single()
        container.conversations.discardPending(scope, pending.id)
        assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)

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
                saved.library.setLit(skill.id, true)
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

    @Test fun assessmentCannotOverwriteManualProgressChangedWhileWaiting() = runBlocking {
        withTimeout(10_000) {
            val direction = container.library.snapshot().childrenOf(null).first()
            val tree = add(direction, EntryKind.TREE, "进度优先")
            val skill = add(tree, EntryKind.SKILL, "点亮状态")
            val scope = ChatScope(ChatMode.TUTOR, skill.id)
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
            val answer = async { container.conversations.send(scope, "用源记录对照。") }
            waiting.await()
            container.library.setLit(skill.id, true)
            container.library.setLit(skill.id, false)
            release.complete(Unit)
            answer.await()
            assertEquals(Mastery.UNLIT, container.library.entry(skill.id)?.mastery)
            assertEquals("答对了。", container.conversations.watch(scope).first().last().text)
        }
    }

    @Test fun restartingUsesCurrentRequirementsAndIsolatesOldHistoryAndQuiz() = runBlocking {
        val direction = container.library.snapshot().childrenOf(null).first()
        val tree = add(direction, EntryKind.TREE, "重学测试树")
        val skill = add(tree, EntryKind.SKILL, "重学测试节点")
        val scope = ChatScope(ChatMode.TUTOR, skill.id)
        container.library.setLit(skill.id, true)
        ai.reply("""{"kind":"teach","reply":"旧版讲法"}""")
        container.conversations.send(scope, "旧版要求")
        ai.reply("""{"kind":"exercise","reply":"旧版练习"}""")
        container.conversations.send(scope, "", check = true)
        val oldQuiz = container.conversations.watch(scope).first().last()

        ai.enqueue { throw RuleViolation("临时断网") }
        expectRule { container.conversations.restartLesson(scope, "用账单举例") }
        val restart = container.conversations.watch(scope).first().last()
        assertEquals(TurnKind.RESTART, restart.kind)

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
        assertTrue(history.any { it.id == oldQuiz.id })
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
            assertEquals(listOf(TurnKind.MESSAGE, TurnKind.RESTART, TurnKind.MESSAGE), history.map { it.kind })
            assertEquals("新一轮讲解", history.last().text)
        }
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

    private suspend fun expectRule(action: suspend () -> Unit) {
        try {
            action()
            fail("应拒绝本次操作")
        } catch (_: RuleViolation) {
            // 验证拒绝后保留原数据，由各测试检查最终状态。
        }
    }

    companion object {
        const val CREATED = """{"kind":"create","reply":"已规划","directionName":"数据能力","trees":[{"name":"数据质量","skills":[{"ref":"base","name":"基础","goal":"能解释数据质量","requires":[]},{"ref":"practice","name":"实践","goal":"能找出异常记录","requires":["base"]}]}]}"""

        fun verdict(kind: String, text: String, id: String): String =
            JSONObject().put("kind", kind).put("reply", text).put("exerciseId", id).toString()
    }
}
