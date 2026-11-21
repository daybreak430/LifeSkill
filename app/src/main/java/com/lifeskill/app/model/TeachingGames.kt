package com.lifeskill.app.model

// 放宽到 8000 是为了让模型能写出完整的小游戏（状态、绘制、反馈、动效），
// 仍远小于可执行引擎的体量；运行器另有命令数、消息频率和 90 秒局长限制。
const val MAX_GAME_CODE_LENGTH = 8_000

/** 小游戏仅提供学习体验，生成代码在无宿主接口的独立网页 Worker 内运行。 */
data class TeachingGame(
    override val id: String, override val title: String,
    val goal: String, val instructions: String, val code: String,
) : TeachingBlock

internal fun validGameCode(code: String): Boolean = code.isNotBlank() && code.length <= MAX_GAME_CODE_LENGTH && '\u0000' !in code
