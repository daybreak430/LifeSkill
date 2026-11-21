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
            assertEquals(4, lessons.size)
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

    @Test fun explicitRestoreRecreatesTheShowcaseWithoutTouchingOtherLearning() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        try {
            val app = fixture(database, ScriptedGateway())
            app.library.initialize()
            val direction = app.library.snapshot().childrenOf(null).first { it.name == "本职工作" }
            val personalCourse = app.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "保留的用户课程"))
            app.library.save(EntryEdit(null, personalCourse.id, EntryKind.SKILL, "保留的用户课时", "验证学习记录不受影响"))

            app.library.delete(BuiltInTeaching.COURSE_ID)
            app.library.initialize()
            assertNull(app.library.entry(BuiltInTeaching.COURSE_ID))

            val restored = app.library.restoreTeachingShowcase()

            assertEquals(BuiltInTeaching.COURSE_ID, restored.id)
            assertEquals(4, app.library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID).size)
            assertNotNull(app.library.entry(personalCourse.id))
            assertEquals("保留的用户课时", app.library.snapshot().childrenOf(personalCourse.id).single().name)
            assertEquals(restored.id, app.library.restoreTeachingShowcase().id)
            assertEquals(4, app.library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID).size)
        } finally { database.close() }
    }

    @Test fun laterComponentsAppendToTheExistingCategorySession() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        val secret = SecretBox("lifeskill-tests")
        try {
            val app = fixture(database, ScriptedGateway())
            app.library.initialize()
            val practice = checkNotNull(BuiltInTeaching.lessonByKey("practice"))
            val scope = ChatScope(ChatMode.TUTOR, practice.id)
            val original = checkNotNull(database.skills().turn(scope.id, practice.turnId))
            val blocks = TeachingBlockProtocol.decode(JSONArray(secret.open(original.teachingBlocks.orEmpty(), "${practice.turnId}:blocks")))
            val newest = practice.items.last()
            val shortened = blocks.filterNot { it.id == newest.block.id }
            database.skills().updateTurn(original.copy(
                teachingBlocks = secret.seal(
                    TeachingBlockProtocol.encode(shortened).toString(), "${practice.turnId}:blocks",
                ),
            ))
            database.openHelper.writableDatabase.execSQL("DELETE FROM bootstrap WHERE id = ${newest.installMarker}")

            app.library.initialize()

            val lessons = app.library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID)
            assertEquals(4, lessons.size)
            val restored = checkNotNull(database.skills().turn(scope.id, practice.turnId))
            val restoredBlocks = TeachingBlockProtocol.decode(
                JSONArray(secret.open(restored.teachingBlocks.orEmpty(), "${practice.turnId}:blocks")),
            )
            assertTrue(restoredBlocks.any { it.id == newest.block.id })
            assertEquals(1, database.skills().turnsForChat(scope.id).size)
        } finally { database.close() }
    }

    @Test fun uneditedLegacyShowcaseNodesMergeIntoFourCategorySessions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        val secret = SecretBox("lifeskill-tests")
        try {
            val app = fixture(database, ScriptedGateway())
            app.library.initialize()
            val course = checkNotNull(database.skills().entry(BuiltInTeaching.COURSE_ID))
            database.skills().siblings(course.id).forEach { database.skills().deleteEntry(it.id) }
            BuiltInTeaching.lessons.forEach {
                database.openHelper.writableDatabase.execSQL("DELETE FROM bootstrap WHERE id = ${it.installMarker}")
            }

            val legacy = listOf("timeline", "flow").map { key ->
                checkNotNull(BuiltInTeaching.legacyDemos.find { it.key == key })
            }
            val blocks = legacy.map { demo ->
                when (demo.key) {
                    "timeline" -> BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingTimeline>().first()
                    else -> BuiltInTeaching.lessons.flatMap { it.blocks }.filterIsInstance<TeachingFlow>().first()
                }.also { block ->
                    database.skills().insertEntry(EntryRow(
                        demo.id, course.id, EntryKind.SKILL.name,
                        secret.seal(demo.name, "${demo.id}:name"), secret.seal(demo.goal, "${demo.id}:goal"),
                        Mastery.UNLIT.name, 0, 0, database.skills().nextPosition(),
                        EntryIcon.BOOK.name, "旧示例", null,
                    ))
                    val scope = ChatScope(ChatMode.TUTOR, demo.id)
                    database.skills().insertChat(ChatRow(scope.id, demo.id))
                    database.skills().insertTurn(TurnRow(
                        id = demo.turnId, chatId = scope.id, speaker = Speaker.ASSISTANT.name,
                        kind = TurnKind.MESSAGE.name, text = secret.seal("旧版内置示例", "${demo.turnId}:turn"),
                        contentVersion = 0, exerciseId = null, resultId = null, choices = null,
                        teachingBlocks = secret.seal(
                            TeachingBlockProtocol.encode(listOf(block)).toString(), "${demo.turnId}:blocks",
                        ),
                    ))
                }
            }

            app.library.initialize()

            val categories = app.library.snapshot().childrenOf(BuiltInTeaching.COURSE_ID)
            assertEquals(4, categories.size)
            legacy.forEach { assertNull(app.library.entry(it.id)) }
            val structure = checkNotNull(BuiltInTeaching.lessonByKey("structure"))
            val turn = checkNotNull(database.skills().turn(ChatScope(ChatMode.TUTOR, structure.id).id, structure.turnId))
            val merged = TeachingBlockProtocol.decode(
                JSONArray(secret.open(turn.teachingBlocks.orEmpty(), "${structure.turnId}:blocks")),
            )
            assertTrue(merged.any { it.id == "timeline" })
            assertTrue(merged.any { it.id == "flow" })
            assertEquals(1, database.skills().turnsForChat(ChatScope(ChatMode.TUTOR, structure.id).id).size)
            assertTrue(blocks.size == 2)
        } finally { database.close() }
    }

    @Test fun statesAreEncryptedSurviveReopenAndNeverLightTheLesson() = runBlocking {
        val name = "teaching-state-${UUID.randomUUID()}"
        val sample = BuiltInTeaching.lessons.first { it.key == "practice" }
        val scope = ChatScope(ChatMode.TUTOR, sample.id)
        try {
            Room.databaseBuilder(context, LifeSkillDatabase::class.java, name).build().let { database ->
                try {
                    val app = fixture(database, ScriptedGateway())
                    app.library.initialize()
                    val block = app.conversations.watch(scope).first().single().teachingBlocks
                        .filterIsInstance<TeachingQuiz>().first { it.id == "quiz" }
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
        val richState = TeachingState(
            selection = "2", submitted = true, attempts = 2, revealed = true,
            order = listOf("observe", "goal", "validate", "decide"),
            placements = mapOf("april" to "observation", "festival" to "explanation"), step = 1,
        )
        assertEquals(richState, TeachingBlockProtocol.decodeStates(
            TeachingBlockProtocol.encodeStates(mapOf("rich" to richState)),
        ).getValue("rich"))
        val valid = JSONArray().put(
            TeachingBlockProtocol.encode(listOf(BuiltInTeaching.lessons.first().blocks.first())).getJSONObject(0),
        )
        valid.put(JSONObject().put("id", "future").put("type", "future").put("title", "未来组件"))
        val turn = AiProtocol.tutor(JSONObject().put("kind", "teach").put("reply", "仍能独立阅读的讲解").put("blocks", valid).toString())
        assertEquals(1, turn.blocks.size)
        val malformed = JSONArray("""[{"id":"f","title":"无效流程","type":"flow","diagram":"flowchart TD\nA[A] --> B[B]","details":{"A":"缺少B"}}]""")
        assertTrue(TeachingBlockProtocol.decode(malformed).isEmpty())
        val invalidOrder = JSONArray("""[{"id":"s","title":"排序","type":"sort","prompt":"排序","items":[
            {"id":"a","text":"甲"},{"id":"b","text":"乙"},{"id":"c","text":"丙"}],
            "correctOrder":["a","a","c"],"hint":"提示","explanation":"解释"}]""")
        assertTrue(TeachingBlockProtocol.decode(invalidOrder).isEmpty())
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

    @Test fun largeComponentHistoryKeepsTheRecentConversationWithinBudget() {
        val blocks = List(4) { index -> TeachingText("text$index", "第${index + 1}段", "教学说明".repeat(450)) }
        val history = AiProtocol.tutorHistory(Turn("large", Speaker.ASSISTANT, TurnKind.MESSAGE, "讲解".repeat(3_000), teachingBlocks = blocks))
        assertTrue(history.length < 11_000)
        assertTrue(history.contains("blockSummaries"))
        assertFalse(history.contains("\"blocks\""))
    }

    @Test fun demandUnitsFollowTheExampleAndAxesCannotBeReversed() {
        val raw = JSONObject().put("id", "tickets").put("title", "咖啡券").put("type", "chart")
            .put("model", "linear_demand").put("xLabel", "价格").put("yLabel", "数量")
            .put("quantityUnit", "张")
        val chart = TeachingBlockProtocol.decode(JSONArray().put(raw)).single() as TeachingChart
        assertEquals("张", chart.quantityUnit)
        assertEquals("需求量（张）", chart.xLabel)
        assertEquals("价格（元）", chart.yLabel)
        assertTrue(chart.interactionSummary(TeachingState(value = 4.0, revealed = true)).contains("80 张"))
        raw.remove("quantityUnit")
        val legacy = TeachingBlockProtocol.decode(JSONArray().put(raw)).single() as TeachingChart
        assertEquals("单位", legacy.quantityUnit)
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
