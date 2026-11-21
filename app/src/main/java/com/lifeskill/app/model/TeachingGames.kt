package com.lifeskill.app.model

const val MAX_GAME_CODE_LENGTH = 4_000

/** 小游戏仅提供学习体验，生成代码在无宿主接口的独立网页 Worker 内运行。 */
data class TeachingGame(
    override val id: String, override val title: String,
    val goal: String, val instructions: String, val code: String,
) : TeachingBlock

internal fun validGameCode(code: String): Boolean = code.isNotBlank() && code.length <= MAX_GAME_CODE_LENGTH && '\u0000' !in code
