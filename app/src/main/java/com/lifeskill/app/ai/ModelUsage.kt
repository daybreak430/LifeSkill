package com.lifeskill.app.ai

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

internal data class ModelUsage(
    val provider: Provider,
    val model: String,
    val timestamp: Instant,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cachedInputTokens: Long?,
    val subscription: Boolean = false,
    val reportedTotalTokens: Long? = null,
) {
    companion object {
        fun from(response: JSONObject, settings: ModelSettings, startedAt: Instant): ModelUsage? {
            val usage = response.optJSONObject("usage") ?: return null
            val reportedTotal = usage.tokenCount("total_tokens")
            val input = usage.tokenCount("prompt_tokens")
            val output = usage.tokenCount("completion_tokens")
            if (reportedTotal == null && (input == null || output == null)) return null
            // 回执只有总数，或分项与总数矛盾时，仍保留总数用于统计。
            val usableBreakdown = input != null && output != null && (reportedTotal == null || input + output == reportedTotal)
            val cached = (usage.tokenCount("prompt_cache_hit_tokens")
                ?: usage.optJSONObject("prompt_tokens_details")?.tokenCount("cached_tokens")
                ?: usage.tokenCount("prompt_cache_miss_tokens")?.let { missed -> input?.minus(missed) })
                ?.takeIf { usableBreakdown && it in 0..checkNotNull(input) }
            val model = (response.opt("model") as? String)?.takeIf { it.isNotBlank() && it.length <= 160 }
                ?: settings.model
            val timestamp = response.optLong("created").takeIf { it in 1..253_402_300_799L }
                ?.let(Instant::ofEpochSecond) ?: startedAt
            // 订阅额度和按量扣费独立，密钥前缀只在内存中用于判断计费方式。
            val subscription = settings.provider == Provider.MINIMAX && settings.apiKey.startsWith("sk-cp-")
            return ModelUsage(
                settings.provider, model, timestamp, input.takeIf { usableBreakdown }, output.takeIf { usableBreakdown },
                cached, subscription, reportedTotal,
            )
        }

        private fun JSONObject.tokenCount(name: String): Long? =
            (opt(name) as? Number)?.toString()?.toLongOrNull()?.takeIf { it in 0..1_000_000_000L }
    }
}

internal object ModelPricing {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    // 单位为十亿分之一元。按请求记录当时的估算值，日后调整费率不会重算旧记录。
    fun estimateNanos(usage: ModelUsage): Long? {
        if (usage.subscription) return null
        val input = usage.inputTokens ?: return null
        val output = usage.outputTokens ?: return null
        val cached = usage.cachedInputTokens ?: if (input == 0L) 0L else return null
        val uncached = input - cached
        return when (usage.provider) {
            Provider.DEEPSEEK -> deepSeekCost(usage.model, usage.timestamp, cached, uncached, output)
            Provider.MINIMAX -> when (usage.model.lowercase()) {
                "minimax-m3.1-flash" -> cached * 100 + uncached * 1_000 + output * 4_000
                else -> null
            }
            Provider.CUSTOM -> null
        }
    }

    private fun deepSeekCost(model: String, timestamp: Instant, cached: Long, uncached: Long, output: Long): Long? {
        val peakCost = when (model.lowercase()) {
            "deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp", "deepseek-v4.1-flash-1017" ->
                cached * 40 + uncached * 2_000 + output * 8_000
            "deepseek-v4-pro", "deepseek-v4-pro-0813" ->
                cached * 300 + uncached * 9_000 + output * 27_000
            else -> return null
        }
        val time = timestamp.atZone(shanghai)
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
