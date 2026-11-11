package com.lifeskill.app.ai

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

internal data class DeepSeekUsage(
    val model: String,
    val timestamp: Instant,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long?,
) {
    val totalTokens get() = inputTokens + outputTokens

    companion object {
        fun from(response: JSONObject, requestedModel: String, startedAt: Instant): DeepSeekUsage? {
            val usage = response.optJSONObject("usage") ?: return null
            val input = usage.tokenCount("prompt_tokens") ?: return null
            val output = usage.tokenCount("completion_tokens") ?: return null
            val cached = (usage.tokenCount("prompt_cache_hit_tokens")
                ?: usage.optJSONObject("prompt_tokens_details")?.tokenCount("cached_tokens")
                ?: usage.tokenCount("prompt_cache_miss_tokens")?.let { input - it })
                ?.takeIf { it in 0..input }
            val model = (response.opt("model") as? String)?.takeIf { it.isNotBlank() && it.length <= 160 }
                ?: requestedModel
            val timestamp = response.optLong("created").takeIf { it in 1..253_402_300_799L }
                ?.let(Instant::ofEpochSecond) ?: startedAt
            return DeepSeekUsage(model, timestamp, input, output, cached)
        }

        private fun JSONObject.tokenCount(name: String): Long? =
            (opt(name) as? Number)?.toString()?.toLongOrNull()?.takeIf { it in 0..1_000_000_000L }
    }
}

internal object DeepSeekPricing {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    // 单位为十亿分之一元。按请求记录当时的估算值，日后调整费率不会重算旧记录。
    fun estimateNanos(usage: DeepSeekUsage): Long? {
        val cached = usage.cachedInputTokens ?: if (usage.inputTokens == 0L) 0L else return null
        val peakCost = when (usage.model.lowercase()) {
            "deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp", "deepseek-v4.1-flash-1017" ->
                cached * 40 + (usage.inputTokens - cached) * 2_000 + usage.outputTokens * 8_000
            "deepseek-v4-pro", "deepseek-v4-pro-0813" ->
                cached * 300 + (usage.inputTokens - cached) * 9_000 + usage.outputTokens * 27_000
            else -> return null
        }
        val time = usage.timestamp.atZone(shanghai)
        val peak = time.dayOfWeek.value <= 5 && (time.hour in 9..11 || time.hour in 14..17) &&
            !(time.year == 2026 && isHoliday2026(time.monthValue, time.dayOfMonth))
        return if (peak) peakCost else peakCost / 2
    }

    // 依据国办发明电〔2025〕7号。尚未配置的年份使用常规工作日时段估算。
    private fun isHoliday2026(month: Int, day: Int): Boolean = when (month) {
        1 -> day in 1..3
        2 -> day in 15..23
        4 -> day in 4..6
        5 -> day in 1..5
        6 -> day in 19..21
        9 -> day in 25..27
        10 -> day in 1..7
        else -> false
    }
}
