package com.lifeskill.app.model

enum class ChatMode { GENERATE, TUTOR }

data class ChatScope(val mode: ChatMode, val ownerId: String? = null) {
    init {
        require(mode != ChatMode.TUTOR || ownerId != null)
    }

    val id get() = when (mode) {
        ChatMode.GENERATE -> "generate:${ownerId ?: "home"}"
        ChatMode.TUTOR -> "tutor:$ownerId"
    }
}

enum class Speaker { USER, ASSISTANT }
enum class TurnKind {
    MESSAGE, CHECK, CHECK_OFFER, EXERCISE, PASSED, RETRY, OFF_TOPIC, DRAFT, CREATED, RESTART,
    TREE_QUESTION, TREE_OFFER, GENERATE_TREE,
}

enum class GenerationLevel(val label: String) {
    BEGINNER("入门"),
    INTERMEDIATE("进阶"),
    EXPERT("熟练"),
}

data class GenerationOptions(val level: GenerationLevel, val courseCount: IntRange) {
    init {
        require(courseCount == OVERVIEW || courseCount == DETAILED ||
            (courseCount.first == courseCount.last && courseCount.first in 1..COURSE_LIMIT))
    }

    val courseCountLabel get() = if (courseCount.first == courseCount.last) "${courseCount.first} 节"
        else "${courseCount.first}–${courseCount.last} 节"

    internal val confirmation get() = "确认生成 · ${level.label} · $courseCountLabel"

    companion object {
        internal const val COURSE_LIMIT = 33
        internal val OVERVIEW = 3..5
        internal val DETAILED = 8..12
    }
}

data class GenerationPreview(
    val treeNames: List<String>,
    val suggestedDirectionId: String?,
    val suggestedDirectionName: String?,
)

internal sealed interface GenerationDestination {
    data class Existing(val id: String) : GenerationDestination
    data class New(val name: String) : GenerationDestination
}

data class Turn(
    val id: String,
    val speaker: Speaker,
    val kind: TurnKind,
    val text: String,
    val contentVersion: Long = 0,
    val exerciseId: String? = null,
    val resultId: String? = null,
    val choices: List<String> = emptyList(),
    val preview: GenerationPreview? = null,
    val generationOptions: GenerationOptions? = null,
) {
    val modelText get() = if (choices.isEmpty()) text else "$text\n" +
        choices.mapIndexed { index, choice -> choiceAnswer(index, choice) }.joinToString("\n")
}

data class Exercise(val id: String, val question: String, val choices: List<String> = emptyList())

fun choiceAnswer(index: Int, choice: String) = "${'A' + index}. $choice"

object TutorRules {
    const val CONTINUE_REPLY = "好的，继续"
    const val EXAM_REPLY = "做课后练习"
    // 保留旧版快捷回复的识别，历史记录仍按原文显示。
    private const val LEGACY_EXAM_REPLY = "感觉已经会了，来考试吧"
    internal const val EXAM_TEACHING_ROUNDS = 5
    internal val teachingReplyKinds = setOf(TurnKind.MESSAGE, TurnKind.CHECK_OFFER)

    fun currentLesson(history: List<Turn>): List<Turn> =
        history.drop(history.indexOfLast { it.kind == TurnKind.RESTART }.coerceAtLeast(0))

    internal fun canStartExam(lesson: List<Turn>): Boolean =
        canStartExam(
            lesson.lastOrNull()?.takeIf { it.speaker == Speaker.ASSISTANT }?.kind,
            lesson.count { it.speaker == Speaker.ASSISTANT && it.kind in teachingReplyKinds },
        )

    internal fun canStartExam(lastReply: TurnKind?, teachingReplies: Int): Boolean =
        lastReply in teachingReplyKinds && teachingReplies >= EXAM_TEACHING_ROUNDS

    fun pendingExercise(history: List<Turn>): Exercise? {
        val last = history.lastOrNull { it.speaker == Speaker.ASSISTANT && it.kind != TurnKind.OFF_TOPIC }
        if (last?.kind != TurnKind.EXERCISE && last?.kind != TurnKind.RETRY) return null
        val id = last.exerciseId ?: return null
        val question = history.firstOrNull { it.kind == TurnKind.EXERCISE && it.exerciseId == id } ?: return null
        return Exercise(id, question.modelText, question.choices)
    }

    fun validateVerdict(reply: TurnKind, exerciseId: String?, pending: Exercise?, input: Turn) {
        if (reply != TurnKind.PASSED && reply != TurnKind.RETRY) return
        if (pending == null || exerciseId != pending.id || input.kind != TurnKind.MESSAGE ||
            input.text.isBlank() || input.text == CONTINUE_REPLY || input.text == EXAM_REPLY || input.text == LEGACY_EXAM_REPLY
        ) {
            throw RuleViolation("AI 的评估没有对应当前练习，请重试。")
        }
    }
}
