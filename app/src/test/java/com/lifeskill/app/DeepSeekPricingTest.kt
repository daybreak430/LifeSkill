package com.lifeskill.app

import com.lifeskill.app.ai.DeepSeekPricing
import com.lifeskill.app.ai.DeepSeekUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DeepSeekPricingTest {
    @Test fun appliesBeijingTimeAndTheHolidayCalendarToTheReceipt() {
        fun estimate(timestamp: String) = DeepSeekPricing.estimateNanos(
            DeepSeekUsage("deepseek-flash", Instant.parse(timestamp), 1_000, 200, 100),
        )
        assertEquals(3_404_000L, estimate("2026-11-11T01:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-11T04:00:00Z"))
        assertEquals(3_404_000L, estimate("2026-11-11T06:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-11T10:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-11-14T02:00:00Z"))
        assertEquals(1_702_000L, estimate("2026-10-01T02:00:00Z"))
    }

    @Test fun doesNotEstimateWhenModelOrCacheBreakdownIsUnknown() {
        val usage = DeepSeekUsage("deepseek-flash", Instant.EPOCH, 100, 20, null)
        assertNull(DeepSeekPricing.estimateNanos(usage))
        assertNull(DeepSeekPricing.estimateNanos(usage.copy(model = "unknown", cachedInputTokens = 0)))
        assertEquals(80_000L, DeepSeekPricing.estimateNanos(usage.copy(inputTokens = 0)))
    }
}
