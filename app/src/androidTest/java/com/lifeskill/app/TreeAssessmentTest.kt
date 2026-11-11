package com.lifeskill.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.LifeSkillDatabase
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.ExamAnswer
import com.lifeskill.app.model.ExamQuestionType
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TreeChallenge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreeAssessmentTest {
    private lateinit var database: LifeSkillDatabase
    private lateinit var container: AppContainer
    private lateinit var gateway: ScriptedGateway
    private lateinit var tree: Entry
    private lateinit var skill: Entry

    @Before fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LifeSkillDatabase::class.java).build()
        gateway = ScriptedGateway()
        container = fixture(database, gateway)
        container.library.initialize()
        val direction = container.library.snapshot().childrenOf(null).first()
        tree = container.library.save(EntryEdit(null, direction.id, EntryKind.TREE, "账单验收"))
        skill = container.library.save(EntryEdit(null, tree.id, EntryKind.SKILL, "对账", "会核对金额与凭证"))
    }

    @After fun tearDown() = database.close()

    @Test fun examUsesTeachingThenValidatesGradesAndKeepsEveryRetake() = runBlocking {
        val exams = container.assessments
        expectRule { exams.startExam(tree.id) }
        expectRule { exams.startChallenge(tree.id, TreeChallenge.FEYNMAN) }

        gateway.reply("""{"kind":"teach","reply":"先把实收金额与凭证上的总额对齐。"}""")
        container.conversations.send(ChatScope(ChatMode.TUTOR, skill.id), "学习对账")
        container.library.lightFromAssessment(skill)

        gateway.enqueue { messages ->
            val context = JSONObject(messages.last().text).getJSONObject("learning")
            assertEquals("账单验收", context.getString("tree"))
            val lesson = context.getJSONArray("lessons").getJSONObject(0)
            assertEquals(skill.goal, lesson.getString("goal"))
            assertTrue(lesson.getJSONArray("excerpts").getString(0).contains("实收金额"))
            """{"questions":[]}"""
        }
        gateway.enqueue { messages ->
            assertTrue(messages.first().text.contains("未通过校验"))
            examFixture("第一轮")
        }
        exams.startExam(tree.id)
        exams.startExam(tree.id)
        val first = exams.watchAttempts(tree.id).first().single()
        assertNull(first.score)
        assertEquals(ExamQuestionType.entries.toSet(), first.questions.map { it.type }.toSet())

        gateway.reply(markFixture())
        exams.submitExam(tree.id, first.id, first.answers)
        assertEquals(0, exams.watchAttempts(tree.id).first().single().score)
        assertFalse(exams.summaries.first().getValue(tree.id).passed)
        expectRule { exams.startChallenge(tree.id, TreeChallenge.DIAGNOSTIC) }

        gateway.enqueue { messages ->
            assertEquals(8, JSONObject(messages.last().text).getJSONArray("previousQuestions").length())
            examFixture("第二轮")
        }
        exams.startExam(tree.id)
        val second = exams.watchAttempts(tree.id).first().first()
        val passing = second.questions.map { question ->
            when (question.type) {
                ExamQuestionType.SHORT -> ExamAnswer()
                ExamQuestionType.FILL -> ExamAnswer(text = "核对金额")
                else -> ExamAnswer(selected = question.correctChoices)
            }
        }
        gateway.reply(markFixture())
        exams.submitExam(tree.id, second.id, passing)
        assertEquals(60, exams.watchAttempts(tree.id).first().first().score)
        assertTrue(exams.summaries.first().getValue(tree.id).passed)

        gateway.reply(examFixture("第三轮"))
        exams.startExam(tree.id)
        val third = exams.watchAttempts(tree.id).first().first()
        gateway.reply(markFixture(pointsAdjustment = 1))
        gateway.enqueue { messages ->
            assertTrue(messages.first().text.contains("未通过校验"))
            markFixture()
        }
        exams.submitExam(tree.id, third.id, third.questions.map { question ->
            ExamAnswer(question.correctChoices, "以凭证核对金额")
        })
        val attempts = exams.watchAttempts(tree.id).first()
        assertEquals(listOf(100, 60, 0), attempts.map { it.score })
        assertEquals(0, attempts[1].marks.last().points)
        assertEquals("未作答。", attempts.last().marks.first().feedback)
        assertEquals(100, exams.summaries.first().getValue(tree.id).bestScore)
        expectRule { exams.startExam(tree.id) }
        val saved = database.assessments().attempt(tree.id, third.id)
        assertFalse(checkNotNull(saved).content.contains("以凭证核对金额"))
    }

    @Test fun challengesRequireThreeDiagnosticAnswersAndAcceptRevisedFeynmanExplanation() = runBlocking {
        val exams = container.assessments
        container.library.lightFromAssessment(skill)
        gateway.reply(examFixture())
        exams.startExam(tree.id)
        val attempt = exams.watchAttempts(tree.id).first().single()
        gateway.reply(markFixture())
        exams.submitExam(tree.id, attempt.id, attempt.questions.map { question ->
            ExamAnswer(question.correctChoices, "用凭证核对金额")
        })

        val diagnostic = TreeChallenge.DIAGNOSTIC
        gateway.reply(challengeFixture("", false, "第一题"))
        exams.startChallenge(tree.id, diagnostic)
        repeat(3) { index ->
            gateway.reply(challengeFixture("反馈 ${index + 1}", true, if (index < 2) "下一题 ${index + 2}" else ""))
            exams.answerChallenge(tree.id, diagnostic, "诊断回答 ${index + 1}")
            val session = checkNotNull(exams.watchChallenge(tree.id, diagnostic).first())
            assertEquals(index + 1, session.exchanges.size)
            assertEquals(index == 2, session.passed)
        }

        val feynman = TreeChallenge.FEYNMAN
        exams.startChallenge(tree.id, feynman)
        assertTrue(checkNotNull(exams.watchChallenge(tree.id, feynman).first()).prompt.contains("10 岁"))
        gateway.reply(challengeFixture("请先解释凭证是什么意思。", false, "用一个买东西的例子说明。"))
        exams.answerChallenge(tree.id, feynman, "对照凭证。")
        assertFalse(checkNotNull(exams.watchChallenge(tree.id, feynman).first()).finished)
        gateway.reply(challengeFixture("讲清楚了金额和收据的关系。", true, ""))
        exams.answerChallenge(tree.id, feynman, "买东西付完钱，把付的钱和收据上的数字比较。")
        assertTrue(checkNotNull(exams.watchChallenge(tree.id, feynman).first()).passed)
        assertEquals(TreeChallenge.entries.toSet(), exams.summaries.first().getValue(tree.id).completedChallenges)

        container.library.delete(tree.id)
        assertTrue(exams.watchAttempts(tree.id).first().isEmpty())
        assertNull(exams.watchChallenge(tree.id, diagnostic).first())
        assertNull(exams.watchChallenge(tree.id, feynman).first())
    }

    private suspend fun expectRule(action: suspend () -> Unit) {
        try {
            action()
            fail("预期本次操作被拒绝。")
        } catch (_: RuleViolation) {
            // 由调用方验证数据状态。
        }
    }
}

internal fun examFixture(prefix: String = "验收"): String = JSONObject().put("questions", JSONArray(
    ExamQuestionType.entries.flatMap { type ->
        (1..2).map { index ->
            val choices = when (type) {
                ExamQuestionType.SINGLE, ExamQuestionType.MULTIPLE -> listOf("核对金额", "查找凭证", "修改字体", "忽略差额")
                else -> emptyList()
            }
            val correct = when (type) {
                ExamQuestionType.SINGLE -> listOf(1)
                ExamQuestionType.MULTIPLE -> listOf(1, 2)
                else -> emptyList()
            }
            JSONObject().put("type", type.name).put("prompt", "$prefix${type.label}$index")
                .put("choices", JSONArray(choices)).put("correctChoices", JSONArray(correct)).put("reference", "先用凭证核对金额。")
        }
    },
)).toString()

internal fun markFixture(pointsAdjustment: Int = 0): String = JSONObject().put("marks", JSONArray(
    ExamQuestionType.entries.flatMap { type -> listOf(type.points, type.points) }.mapIndexed { index, points ->
        JSONObject().put("number", index + 1).put("points", points + pointsAdjustment).put("feedback", "说明了核对的方法。")
    },
)).toString()

internal fun challengeFixture(feedback: String, accepted: Boolean, next: String): String =
    JSONObject().put("feedback", feedback).put("accepted", accepted).put("next", next).toString()
