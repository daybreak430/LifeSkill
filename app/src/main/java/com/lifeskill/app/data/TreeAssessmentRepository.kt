package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.ai.TreeAssessmentProtocol
import com.lifeskill.app.model.ChallengeExchange
import com.lifeskill.app.model.ChallengeSession
import com.lifeskill.app.model.DIAGNOSTIC_QUESTIONS
import com.lifeskill.app.model.EXAM_ANSWER_LIMIT
import com.lifeskill.app.model.EXAM_MAX_SCORE
import com.lifeskill.app.model.EXAM_PASS_SCORE
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.ExamAnswer
import com.lifeskill.app.model.ExamAttempt
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.TreeAssessmentSummary
import com.lifeskill.app.model.TreeChallenge
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.examAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class TreeAssessmentRepository(
    private val database: LifeSkillDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val gateway: AiGateway,
    private val secret: SecretBox,
) {
    private val dao = database.assessments()

    val summaries = combine(dao.watchBestScores(), dao.watchCompletedChallenges()) { scores, challenges ->
        val completed = challenges.groupBy({ it.treeId }, { TreeChallenge.valueOf(it.kind) })
        scores.associate { it.treeId to TreeAssessmentSummary(it.score, completed[it.treeId].orEmpty().toSet()) }
    }

    fun watchAttempts(treeId: String) = dao.watchAttempts(treeId)
        .map { rows -> rows.map { it.decode() } }
        .flowOn(Dispatchers.IO)

    fun watchChallenge(treeId: String, kind: TreeChallenge) = dao.watchChallenge(treeId, kind.name)
        .map { it?.decode() }
        .flowOn(Dispatchers.IO)

    suspend fun startExam(treeId: String) {
        val snapshot = database.withTransaction {
            val current = requireReadyTree(treeId)
            val latest = dao.latestAttempt(treeId)
            if (latest != null && latest.score == null) return@withTransaction null
            if (dao.bestScore(treeId) == EXAM_MAX_SCORE) throw RuleViolation("已取得满分。")
            val previous = latest?.decode()?.questions.orEmpty().map { it.prompt }
            ExamContext(latest?.id, curriculum(current, treeId), learningContext(current, treeId), previous)
        } ?: return
        val questions = request(TreeAssessmentProtocol.examRequest(snapshot.learning, snapshot.previousQuestions),
            TreeAssessmentProtocol::questions)
        val attempt = ExamAttempt(newId(), questions)
        database.withTransaction {
            val current = requireReadyTree(treeId)
            if (curriculum(current, treeId) != snapshot.curriculum) throw RuleViolation("课程已更新，请重新出题。")
            // 网络请求不占用事务。若另一请求先建好试卷，保留先提交的试卷。
            if (dao.latestAttempt(treeId)?.id == snapshot.previousId && dao.bestScore(treeId) != EXAM_MAX_SCORE) {
                dao.insert(ExamAttemptRow(id = attempt.id, treeId = treeId, score = null, content = attempt.encode()))
            }
        }
    }

    suspend fun submitExam(treeId: String, attemptId: String, responses: List<ExamAnswer>) {
        val row = dao.attempt(treeId, attemptId) ?: throw RuleViolation("本次考试已不存在。")
        if (row.score != null) return
        val attempt = withContext(Dispatchers.IO) { row.decode() }
        if (responses.size != attempt.questions.size) throw RuleViolation("试卷已更新，请重新打开。")
        val answers = attempt.questions.zip(responses).map { (question, answer) ->
            if (question.choices.isEmpty()) {
                ExamAnswer(text = answer.text.trim().take(EXAM_ANSWER_LIMIT))
            } else {
                val selected = answer.selected.filter { it in 1..question.choices.size }.toSet()
                if (question.correctChoices.size == 1 && selected.size > 1) throw RuleViolation("单选题只能选择一项。")
                ExamAnswer(selected = selected)
            }
        }
        val marks = request(TreeAssessmentProtocol.gradingRequest(attempt.questions, answers)) {
            TreeAssessmentProtocol.marks(it, attempt.questions, answers)
        }
        val graded = attempt.copy(answers = answers, marks = marks)
        database.withTransaction {
            val latest = dao.attempt(treeId, attemptId)
            if (latest != null && latest.score == null) {
                dao.update(row.copy(score = graded.score, content = graded.encode()))
            }
        }
    }

    suspend fun collectCertificate(treeId: String, attemptId: String) = database.withTransaction {
        val row = dao.attempt(treeId, attemptId) ?: throw RuleViolation("本次考试已不存在。")
        if ((row.score ?: 0) < EXAM_PASS_SCORE) throw RuleViolation("通过验收考试后可领取结业证书。")
        val attempt = row.decode()
        if (!attempt.certificateCollected) {
            dao.update(row.copy(content = attempt.copy(certificateCollected = true).encode()))
        }
    }

    suspend fun startChallenge(treeId: String, kind: TreeChallenge) {
        val (previous, learning) = database.withTransaction {
            val current = requirePassedTree(treeId)
            val row = dao.challenge(treeId, kind.name)
            if (row != null) {
                val session = row.decode()
                if (!session.finished || session.passed) return@withTransaction null
            }
            row to if (kind == TreeChallenge.DIAGNOSTIC) learningContext(current, treeId) else null
        } ?: return
        val prompt = if (learning == null) TreeAssessmentProtocol.FEYNMAN_PROMPT else {
            request(TreeAssessmentProtocol.challengeRequest(kind, learning, null, null)) {
                TreeAssessmentProtocol.challenge(it, needsFeedback = false, needsQuestion = true).next
            }
        }
        saveChallenge(treeId, kind, previous, ChallengeSession(prompt))
    }

    suspend fun answerChallenge(treeId: String, kind: TreeChallenge, response: String) {
        val answer = response.trim()
        if (answer.length !in 1..EXAM_ANSWER_LIMIT) throw RuleViolation("请输入 1–${EXAM_ANSWER_LIMIT} 个字符。")
        val (row, session, learning) = database.withTransaction {
            val current = requirePassedTree(treeId)
            val row = dao.challenge(treeId, kind.name) ?: throw RuleViolation("请先开始挑战。")
            val session = row.decode()
            if (session.finished) return@withTransaction null
            Triple(row, session, learningContext(current, treeId))
        } ?: return
        val needsQuestion = if (kind == TreeChallenge.DIAGNOSTIC) session.exchanges.size + 1 < DIAGNOSTIC_QUESTIONS else null
        val reply = request(TreeAssessmentProtocol.challengeRequest(kind, learning, session, answer)) {
            TreeAssessmentProtocol.challenge(it, needsFeedback = true, needsQuestion = needsQuestion).also { reply ->
                if (needsQuestion == true && (reply.next == session.prompt || session.exchanges.any { it.prompt == reply.next })) {
                    throw RecoverableReply("AI 返回了重复的挑战题，请重试。", "保留对本题的反馈，next 改为未问过的新题。")
                }
            }
        }
        val exchanges = session.exchanges + ChallengeExchange(session.prompt, answer, reply.feedback, reply.accepted)
        val passed = when (kind) {
            TreeChallenge.DIAGNOSTIC -> exchanges.size == DIAGNOSTIC_QUESTIONS && exchanges.all { it.accepted }
            TreeChallenge.FEYNMAN -> reply.accepted
        }
        saveChallenge(treeId, kind, row, ChallengeSession(reply.next, exchanges, passed))
    }

    private suspend fun saveChallenge(treeId: String, kind: TreeChallenge, previous: TreeChallengeRow?, session: ChallengeSession) {
        database.withTransaction {
            if (dao.challenge(treeId, kind.name) != previous) return@withTransaction
            // 课程树被删时外键会清理成绩与挑战，迟到的回复不能恢复它。
            if (library.entry(treeId)?.kind != EntryKind.TREE) return@withTransaction
            dao.save(TreeChallengeRow(treeId, kind.name, session.passed,
                secret.seal(TreeAssessmentProtocol.encodeSession(session), challengeKey(treeId, kind.name))))
        }
    }

    private suspend fun requireReadyTree(treeId: String) = library.snapshot().also {
        if (it.byId[treeId]?.kind != EntryKind.TREE) throw RuleViolation("技能树已不存在。")
        if (!it.examAvailable(treeId)) throw RuleViolation("点亮全部课程后解锁验收考试。")
    }

    private suspend fun requirePassedTree(treeId: String) = library.snapshot().also {
        if (it.byId[treeId]?.kind != EntryKind.TREE) throw RuleViolation("技能树已不存在。")
        if ((dao.bestScore(treeId) ?: 0) < EXAM_PASS_SCORE) throw RuleViolation("通过验收考试后解锁挑战。")
    }

    private fun curriculum(current: Library, treeId: String) =
        current.childrenOf(treeId).map { it.id to it.contentVersion }

    private suspend fun learningContext(current: Library, treeId: String): JSONObject {
        val skills = current.childrenOf(treeId)
        val lessons = skills.map { skill ->
            val messages = database.skills().recentTeaching("tutor:${skill.id}", skill.contentVersion,
                TutorRules.teachingReplyKinds.map { it.name }, 2)
            // 每门课都提供目标。仅摘取当前版本的讲解，避免发送完整聊天或其他技能树的记录。
            val excerpts = messages.asReversed().map { secret.open(it.text, "${it.id}:turn").take(320) }
            JSONObject().put("skill", skill.name).put("goal", skill.goal).put("excerpts", JSONArray(excerpts))
        }
        return JSONObject().put("tree", current.byId.getValue(treeId).name).put("lessons", JSONArray(lessons))
    }

    private suspend fun <T> request(messages: List<ModelMessage>, parse: (String) -> T): T {
        val config = withContext(Dispatchers.IO) { settings.active() }.validated()
        return try {
            val raw = gateway.complete(config, messages, structured = true)
            withContext(Dispatchers.Default) { parse(raw) }
        } catch (error: RecoverableReply) {
            val system = messages.first()
            val retry = listOf(system.copy(text = "${system.text}\n\n${error.retryInstruction}")) + messages.drop(1)
            val raw = gateway.complete(config, retry, structured = true, useJsonMode = !error.retryWithoutJsonMode)
            withContext(Dispatchers.Default) { parse(raw) }
        }
    }

    private fun ExamAttempt.encode() = secret.seal(TreeAssessmentProtocol.encodeAttempt(this), "$id:exam")
    private fun ExamAttemptRow.decode() = TreeAssessmentProtocol.decodeAttempt(id, secret.open(content, "$id:exam"))
    private fun TreeChallengeRow.decode() = TreeAssessmentProtocol.decodeSession(secret.open(content, challengeKey(treeId, kind)))
    private fun challengeKey(treeId: String, kind: String) = "$treeId:challenge:$kind"

    private data class ExamContext(
        val previousId: String?, val curriculum: List<Pair<String, Long>>,
        val learning: JSONObject, val previousQuestions: List<String>,
    )
}
