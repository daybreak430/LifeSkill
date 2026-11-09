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
enum class TurnKind { MESSAGE, CHECK, EXERCISE, PASSED, RETRY, OFF_TOPIC, CREATED }

data class Turn(
    val id: String,
    val speaker: Speaker,
    val kind: TurnKind,
    val text: String,
    val contentVersion: Long = 0,
    val exerciseId: String? = null,
    val resultId: String? = null,
)

data class Exercise(val id: String, val question: String)

object TutorRules {
    fun pendingExercise(history: List<Turn>): Exercise? {
        val last = history.lastOrNull { it.speaker == Speaker.ASSISTANT && it.kind != TurnKind.OFF_TOPIC }
        if (last?.kind != TurnKind.EXERCISE && last?.kind != TurnKind.RETRY) return null
        val id = last.exerciseId ?: return null
        val question = history.firstOrNull { it.kind == TurnKind.EXERCISE && it.exerciseId == id } ?: return null
        return Exercise(id, question.text)
    }

    fun validateVerdict(reply: TurnKind, exerciseId: String?, pending: Exercise?, input: Turn) {
        if (reply != TurnKind.PASSED && reply != TurnKind.RETRY) return
        if (pending == null || exerciseId != pending.id || input.kind != TurnKind.MESSAGE || input.text.isBlank()) {
            throw RuleViolation("AI 的评估没有对应当前练习，请重试。")
        }
    }
}
