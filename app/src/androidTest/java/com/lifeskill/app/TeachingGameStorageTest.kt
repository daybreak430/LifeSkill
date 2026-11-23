package com.lifeskill.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.*
import com.lifeskill.app.data.*
import com.lifeskill.app.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeachingGameStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun gameCodeRoundTripBudgetEncryptedStorageAndHistoryRemainBounded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val app = fixture(db, ScriptedGateway())
            app.library.initialize()
            val lesson = BuiltInTeaching.lessonByKey("practice")!!
            val game = lesson.blocks.filterIsInstance<TeachingGame>().single()
            assertEquals(listOf(game), TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(listOf(game))))
            val tooLong = TeachingBlockProtocol.encode(listOf(game.copy(code = "x".repeat(MAX_GAME_CODE_LENGTH + 1))))
            assertTrue(TeachingBlockProtocol.decode(tooLong).isEmpty())
            val duplicateGames = TeachingBlockProtocol.encode(listOf(game, game.copy(id = "second")))
            val reply = AiProtocol.tutor(JSONObject().put("kind", "teach").put("reply", "游戏说明").put("blocks", duplicateGames).toString())
            assertEquals(1, reply.blocks.filterIsInstance<TeachingGame>().size)
            val scope = ChatScope(ChatMode.TUTOR, lesson.id)
            app.conversations.interact(scope, lesson.turnId, game.id, TeachingAction.OpenGame)
            val turn = app.conversations.watch(scope).first().single()
            assertTrue(turn.teachingStates.getValue(game.id).gameOpened)
            assertFalse(db.skills().turn(scope.id, lesson.turnId)!!.teachingBlocks!!.contains(game.code))
            val history = AiProtocol.tutorHistory(turn)
            assertFalse(history.contains(game.code))
            assertTrue(history.contains(game.goal))
            assertFalse(TeachingBlockProtocol.decodeStates("{\"old\":{}}").getValue("old").gameOpened)
            assertEquals(Mastery.UNLIT, app.library.entry(lesson.id)!!.mastery)
        } finally { db.close() }
    }

    @Test fun upgradeRemovesOnlyUnmodifiedExtraComparisonDemos() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        val secret = SecretBox("lifeskill-tests")
        try {
            val app = fixture(db, ScriptedGateway()); app.library.initialize()
            val lesson = BuiltInTeaching.lessonByKey("structure")!!
            val scope = ChatScope(ChatMode.TUTOR, lesson.id)
            val row = db.skills().turn(scope.id, lesson.turnId)!!
            val original = lesson.blocks + BuiltInTeaching.retiredComparisonDemos
            db.skills().updateTurn(row.copy(teachingBlocks = secret.seal(TeachingBlockProtocol.encode(original).toString(), "${row.id}:blocks")))
            app.library.initialize()
            // 主对比示例已下线，清理后不应残留任何未经修改的对比卡。
            assertEquals(0, app.conversations.watch(scope).first().single().teachingBlocks.filterIsInstance<TeachingComparison>().size)
            val edited = BuiltInTeaching.retiredComparisonDemos.first().copy(title = "用户改写的对比")
            val current = db.skills().turn(scope.id, lesson.turnId)!!
            db.skills().updateTurn(current.copy(teachingBlocks = secret.seal(TeachingBlockProtocol.encode(lesson.blocks + edited).toString(), "${row.id}:blocks")))
            app.library.initialize()
            assertTrue(app.conversations.watch(scope).first().single().teachingBlocks.contains(edited))
        } finally { db.close() }
    }

    @Test fun actualModelOldCourseSamplesDecodeIntoNativeComponents() {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open("teaching-legacy-model-samples.json").bufferedReader().use { it.readText() }
        val samples = JSONObject(raw)
        // 旧快照仍可解码为对比卡，但历史消息渲染与 AI 输出都按停用状态过滤。
        val legacyCompare = TeachingBlockProtocol.decode(samples.getJSONObject("legacy_compare").getJSONArray("blocks"))
        assertTrue(legacyCompare.single() is TeachingComparison)
        assertTrue(AiProtocol.tutor(samples.getJSONObject("legacy_compare").toString()).blocks.isEmpty())
        assertTrue(AiProtocol.tutor(samples.getJSONObject("legacy_flow").toString()).blocks.single() is TeachingFlow)
    }

    @Test fun legacyPlainCourseRestartUsesCurrentProtocolAndAcceptsNativeComponents() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val ai = ScriptedGateway(); val app = fixture(db, ai); app.library.initialize()
            val direction = app.library.snapshot().childrenOf(null).first()
            val course = app.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "旧课程"))
            val lesson = app.library.save(EntryEdit(null, course.id, EntryKind.SKILL, "动力煤与炼焦煤", "理解和掌握不同煤种的用途与定价"))
            val scope = ChatScope(ChatMode.TUTOR, lesson.id)
            ai.reply("""{"kind":"teach","reply":"旧版纯文字讲解"}""")
            app.conversations.send(scope, "开始学习")
            ai.enqueue { messages ->
                assertTrue(messages.first().text.contains("新旧课程一律使用当前教学组件协议"))
                assertTrue(messages.first().text.contains("小游戏"))
                assertTrue(messages.none { it.text.contains("旧版纯文字讲解") })
                assertTrue(messages.last().text.contains("旧课程的创建时间"))
                context.cacheDir.resolve("legacy-tutor-prompt.txt").writeText(messages.first().text)
                """{"kind":"teach","reply":"比较两类煤种的用途。","blocks":[{"type":"compare","id":"coal","title":"煤种对比","columns":["动力煤","炼焦煤"],"dimensions":[{"id":"use","title":"用途","values":["燃烧供能","炼制焦炭"]}]}]}"""
            }
            app.conversations.restartLesson(scope, "")
            // 对比卡停用后，模型即便生成 compare 也会被过滤，只保留可读的 reply 正文。
            assertTrue(app.conversations.watch(scope).first().last().teachingBlocks.isEmpty())
            assertEquals(lesson.goal, app.library.entry(lesson.id)!!.goal)
        } finally { db.close() }
    }
}
