package com.lifeskill.app.model

// 放宽到 16000 是为了让模型把状态机、判定和绘制写成分层函数，
// 不必为省字符把逻辑压成一行而写出隐蔽 bug；仍远小于通用引擎的体量，
// 运行器另有命令数、消息频率和 120 秒局长限制。
const val MAX_GAME_CODE_LENGTH = 16_000

/** 小游戏仅提供学习体验，生成代码在无宿主接口的独立网页 Worker 内运行。 */
data class TeachingGame(
    override val id: String, override val title: String,
    val goal: String, val instructions: String, val code: String,
) : TeachingBlock

internal fun validGameCode(code: String): Boolean = code.isNotBlank() && code.length <= MAX_GAME_CODE_LENGTH && '\u0000' !in code
