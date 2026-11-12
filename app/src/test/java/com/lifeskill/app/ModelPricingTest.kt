package com.lifeskill.app

import com.lifeskill.app.ai.ModelPricing
import com.lifeskill.app.ai.ModelUsage
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.data.UsageTotal
import com.lifeskill.app.ui.displayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ModelPricingTest {
    @Test fun cumulativeUsageShowsTheEstimateWithoutPartialCostCopy() {
        val mixed = UsageTotal(2_400, 3_404_000, unpriced = 1)
        assertEquals("预估费用 ¥0.0034", mixed.displayText(cumulative = true).substringAfter(" · "))
        assertEquals("已估 ¥0.0034（部分费用未知）", mixed.displayText().substringAfter(" · "))
        assertEquals("暂无费用估算", mixed.copy(modelCostNanos = null).displayText(cumulative = true).substringAfter(" · "))
    }

    @Test fun appliesBeijingTimeAndTheHolidayCalendarToTheReceipt() {
        fun estimate(timestamp: String) = ModelPricing.estimateNanos(
            ModelUsage(Provider.DEEPSEEK, "deepseek-flash", Instant.parse(timestamp), 1_000, 200, 100),
        )
        assertEquals(3_404_000L, estimate("2026-11-11T01:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-11T04:00:00Z"))
        assertEquals(3_404_000L, estimate("2026-11-11T06:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-11T10:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-14T02:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-10-01T02:00:00Z"))
    }

    @Test fun doesNotEstimateWhenModelOrCacheBreakdownIsUnknown() {
        val usage = ModelUsage(Provider.DEEPSEEK, "deepseek-flash", Instant.EPOCH, 100, 20, null)
        assertNull(ModelPricing.estimateNanos(usage))
        assertNull(ModelPricing.estimateNanos(usage.copy(model = "unknown", cachedInputTokens = 0)))
        assertEquals(80_000L, ModelPricing.estimateNanos(usage.copy(inputTokens = 0)))
    }

    @Test fun estimatesMiniMaxPayAsYouGoWithoutAssigningSubscriptionSpend() {
        val usage = ModelUsage(Provider.MINIMAX, "MiniMax-M3.1-Flash", Instant.EPOCH, 1_000, 200, 100)
        assertEquals(1_710_000L, ModelPricing.estimateNanos(usage))
        assertNull(ModelPricing.estimateNanos(usage.copy(subscription = true)))
        assertNull(ModelPricing.estimateNanos(usage.copy(cachedInputTokens = null)))
    }
}
