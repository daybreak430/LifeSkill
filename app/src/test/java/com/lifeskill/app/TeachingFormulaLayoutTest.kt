package com.lifeskill.app

import com.lifeskill.app.ui.fitFormulaScale
import com.lifeskill.app.ui.formulaChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingFormulaLayoutTest {
    @Test fun splitsWideFormulaAtTheEqualsSign() {
        assertEquals(
            listOf("支付金额 =", "访客数 × 转化率 × 客单价"),
            formulaChunks("支付金额 = 访客数 × 转化率 × 客单价"),
        )
        assertEquals(listOf("x + y"), formulaChunks("x + y"))
        assertEquals(listOf("frac{a}{b}"), formulaChunks("frac{a}{b}"))
    }

    @Test fun scaleNeverGrowsAndStopsAtTheReadableFloor() {
        assertEquals(1f, fitFormulaScale(100f, 300f), 0.001f)
        assertTrue(fitFormulaScale(400f, 300f) < 1f)
        // 宽到连最小字号都放不下时，停在可读下限，交给折行或横滑兜底。
        assertEquals(0.76f, fitFormulaScale(2000f, 300f), 0.001f)
    }
}
