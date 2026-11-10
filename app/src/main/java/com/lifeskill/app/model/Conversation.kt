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
enum class TurnKind { MESSAGE, CHECK, CHECK_OFFER, EXERCISE, PASSED, RETRY, OFF_TOPIC, CREATED, RESTART }

data class Turn(
    val id: String,
    val speaker: Speaker,
    val kind: TurnKind,
    val text: String,
    val contentVersion: Long = 0,
    val exerciseId: String? = null,
    val resultId: String? = null,
    val choices: List<String> = emptyList(),
) {
    val modelText get() = if (choices.isEmpty()) text else "$text\n" +
        choices.mapIndexed { index, choice -> choiceAnswer(index, choice) }.joinToString("\n")
}

data class Exercise(val id: String, val question: String, val choices: List<String> = emptyList())

fun choiceAnswer(index: Int, choice: String) = "${'A' + index}. $choice"

object TutorRules {
    const val CONTINUE_REPLY = "好的，继续"

    fun currentLesson(history: List<Turn>): List<Turn> =
        history.drop(history.indexOfLast { it.kind == TurnKind.RESTART }.coerceAtLeast(0))

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
            input.text.isBlank() || input.text == CONTINUE_REPLY
        ) {
            throw RuleViolation("AI 的评估没有对应当前练习，请重试。")
        }
    }
}
