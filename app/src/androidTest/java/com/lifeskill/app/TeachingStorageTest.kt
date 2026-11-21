package com.lifeskill.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.TeachingBlockProtocol
import com.lifeskill.app.data.*
import com.lifeskill.app.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeachingStorageTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun catalogAppendsWithoutOverwritingEditsOrResurrectingDeletions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val app = fixture(database, ScriptedGateway())
            app.library.initialize()
            app.library.initialize()
            val lessons = app.library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID)
            assertEquals(BuiltInTeaching.lessons.size, lessons.size)
            assertTrue(lessons.all { app.library.snapshot().prerequisitesOf(it.id).isEmpty() })
            val first = lessons.first()
            app.library.save(EntryEdit(first.id, first.parentId, first.kind, "我的时间轴", first.goal))
            val newest = BuiltInTeaching.lessons.last()
            // 模拟旧版本尚未包含最后一种交互：新版本只追加这一节。
            database.skills().deleteEntry(newest.id)
            database.openHelper.writableDatabase.execSQL("DELETE FROM bootstrap WHERE id = ${newest.installMarker}")
            app.library.initialize()
            assertEquals("我的时间轴", app.library.entry(first.id)?.name)
            assertNotNull(app.library.entry(newest.id))
            app.library.delete(first.id)
            app.library.initialize()
            assertNull(app.library.entry(first.id))
            app.library.delete(BuiltInTeaching.COURSE_ID)
            app.library.initialize()
            assertNull(app.library.entry(BuiltInTeaching.COURSE_ID))
        } finally { database.close() }
    }

    @Test fun statesAreEncryptedSurviveReopenAndNeverLightTheLesson() = runBlocking {
        val name = "teaching-state-${UUID.randomUUID()}"
        val sample = BuiltInTeaching.lessons.first { it.key == "quiz" }
        val scope = ChatScope(ChatMode.TUTOR, sample.id)
        try {
            Room.databaseBuilder(context, LifeSkillDatabase::class.java, name).build().let { database ->
                try {
                    val app = fixture(database, ScriptedGateway())
                    app.library.initialize()
                    val block = app.conversations.watch(scope).first().single().teachingBlocks.single() as TeachingQuiz
                    app.conversations.interact(scope, sample.turnId, block.id, TeachingAction.Select(block.question.answer.toString()))
                    app.conversations.interact(scope, sample.turnId, block.id, TeachingAction.Submit)
                    val row = checkNotNull(database.skills().turn(scope.id, sample.turnId))
                    assertFalse(row.teachingBlocks.orEmpty().contains(block.question.question))
                    assertFalse(row.teachingStates.orEmpty().contains("submitted"))
                    assertEquals(Mastery.UNLIT, app.library.entry(sample.id)?.mastery)
                    assertFalse(app.conversations.exportConversation(sample.id).markdown.contains("不支持"))
                } finally { database.close() }
            }
            Room.databaseBuilder(context, LifeSkillDatabase::class.java, name).build().let { database ->
                try {
                    val app = fixture(database, ScriptedGateway())
                    app.library.initialize()
                    val turn = app.conversations.watch(scope).first().single()
                    assertEquals(1, turn.teachingStates.getValue("quiz").attempts)
                    assertTrue(turn.teachingStates.getValue("quiz").submitted)
                    assertTrue(app.conversations.exportConversation(sample.id).markdown.contains("答对"))
                    app.library.delete(sample.id)
                    try {
                        app.conversations.interact(scope, sample.turnId, "quiz", TeachingAction.Reset)
                        fail("删除课时后不能复活组件状态")
                    } catch (_: RuleViolation) { }
                    assertTrue(app.conversations.watch(scope).first().isEmpty())
                } finally { database.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun modelBlocksRoundTripDegradeSafelyAndFeedBackInteractions() {
        BuiltInTeaching.lessons.forEach { lesson ->
            assertEquals(lesson.blocks, TeachingBlockProtocol.decode(TeachingBlockProtocol.encode(lesson.blocks)))
        }
        val valid = TeachingBlockProtocol.encode(BuiltInTeaching.lessons.first().blocks)
        valid.put(JSONObject().put("id", "future").put("type", "future").put("title", "未来组件"))
        val turn = AiProtocol.tutor(JSONObject().put("kind", "teach").put("reply", "仍能独立阅读的讲解").put("blocks", valid).toString())
        assertEquals(1, turn.blocks.size)
        val malformed = JSONArray("""[{"id":"f","title":"无效流程","type":"flow","diagram":"flowchart TD\nA[A] --> B[B]","details":{"A":"缺少B"}}]""")
        assertTrue(TeachingBlockProtocol.decode(malformed).isEmpty())
        val formal = AiProtocol.tutor(JSONObject().put("kind", "exercise").put("question", "正式题目").put("blocks", valid).toString())
        assertTrue(formal.blocks.isEmpty())
        val history = AiProtocol.tutorHistory(Turn("reply", Speaker.ASSISTANT, TurnKind.MESSAGE, "讲解",
            teachingBlocks = turn.blocks, teachingStates = mapOf("timeline" to TeachingState(selection = "uprising"))))
        assertTrue(history.contains("陈胜吴广起义"))
        assertTrue(history.contains("interactions"))
    }

    @Test fun upgradesVersionTwelveWithoutChangingExistingMessages() = runBlocking {
        val name = "teaching-upgrade-${UUID.randomUUID()}"
        val secret = SecretBox("lifeskill-tests")
        try {
            createVersionTwelve(name).use { old ->
                old.execSQL("INSERT INTO chats (id, ownerId) VALUES ('generate:home', NULL)")
                old.execSQL("""INSERT INTO turns (id, chatId, speaker, kind, text, contentVersion, deepThinking)
                    VALUES ('old', 'generate:home', 'ASSISTANT', 'MESSAGE', ?, 0, 1)""", arrayOf(secret.seal("原有教学内容", "old:turn")))
            }
            val database = Room.databaseBuilder(context, LifeSkillDatabase::class.java, name).build()
            try {
                val row = checkNotNull(database.skills().turn("generate:home", "old"))
                assertNull(row.teachingBlocks)
                assertNull(row.teachingStates)
                assertTrue(row.deepThinking)
                assertEquals("原有教学内容", secret.open(row.text, "old:turn"))
            } finally { database.close() }
        } finally { context.deleteDatabase(name) }
    }

    private fun createVersionTwelve(name: String): SQLiteDatabase {
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = instrumentation.context.assets.open("${LifeSkillDatabase::class.java.name}/12.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        return SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) repeat(indices.length()) { position ->
                    execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { execSQL(setup.getString(it)) }
            version = 12
        }
    }
}
