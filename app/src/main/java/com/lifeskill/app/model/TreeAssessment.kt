package com.lifeskill.app.model

internal const val EXAM_PASS_SCORE = 60
internal const val EXAM_MAX_SCORE = 100
internal const val EXAM_ANSWER_LIMIT = 2_000
internal const val DIAGNOSTIC_QUESTIONS = 3
internal const val EXAM_QUESTION_COUNT = 10

internal enum class ExamQuestionType(val label: String, val points: Int) {
    SINGLE("单选", 10),
    MULTIPLE("多选", 10),
    FILL("填空", 10),
    // 仅用于读取 0.12.0 已保存的历史试卷，新试卷不会再包含此题型。
    SHORT("简答", 20);

    val examCount: Int
        get() = when (this) {
            SINGLE -> 4
            MULTIPLE -> 3
            FILL -> 3
            SHORT -> 0
        }
}

internal data class ExamQuestion(
    val type: ExamQuestionType,
    val prompt: String,
    val choices: List<String>,
    val correctChoices: Set<Int>,
    val reference: String,
)

internal data class ExamAnswer(val selected: Set<Int> = emptySet(), val text: String = "")

internal data class ExamMark(val points: Int, val feedback: String)

internal data class ExamAttempt(
    val id: String,
    val questions: List<ExamQuestion>,
    val answers: List<ExamAnswer> = questions.map { ExamAnswer() },
    val marks: List<ExamMark> = emptyList(),
    val certificateCollected: Boolean = false,
) {
    val score get() = marks.takeIf { it.isNotEmpty() }?.sumOf(ExamMark::points)
}

internal enum class TreeChallenge(val title: String) {
    DIAGNOSTIC("自以为是检测"),
    FEYNMAN("费曼复习"),
}

internal data class ChallengeExchange(val prompt: String, val answer: String, val feedback: String, val accepted: Boolean)

internal data class ChallengeSession(
    val prompt: String,
    val exchanges: List<ChallengeExchange> = emptyList(),
    val passed: Boolean = false,
) {
    val finished get() = passed || prompt.isEmpty()
}

internal data class TreeAssessmentSummary(
    val bestScore: Int? = null,
    val completedChallenges: Set<TreeChallenge> = emptySet(),
) {
    val passed get() = (bestScore ?: 0) >= EXAM_PASS_SCORE
}

internal fun Library.examAvailable(treeId: String): Boolean =
    childrenOf(treeId).let { skills -> skills.isNotEmpty() && skills.all { skillState(it.id) == SkillState.LIT } }
