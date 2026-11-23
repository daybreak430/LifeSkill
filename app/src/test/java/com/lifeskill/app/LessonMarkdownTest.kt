package com.lifeskill.app

import com.lifeskill.app.ui.formulaCandidate
import com.lifeskill.app.ui.normalizeEquationOperators
import com.lifeskill.app.ui.normalizeLessonBreaks
import com.lifeskill.app.ui.subheadCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonMarkdownTest {
    @Test fun upgradesSingleBreaksToParagraphs() {
        assertEquals("第一段。\n\n第二段。", normalizeLessonBreaks("第一段。\n第二段。"))
    }

    @Test fun keepsExistingBlankLinesWithoutDoubling() {
        assertEquals("A\n\nB", normalizeLessonBreaks("A\n\nB"))
    }

    @Test fun leavesFencedCodeUntouched() {
        val source = "说明：\n```mermaid\nflowchart TD\nA --> B\n```\n结论。"
        assertEquals("说明：\n\n```mermaid\nflowchart TD\nA --> B\n```\n\n结论。", normalizeLessonBreaks(source))
    }

    @Test fun keepsTableRowsTight() {
        val source = "| 部件 | 位置 |\n| --- | --- |\n| CPU | 主板 |"
        assertEquals(source, normalizeLessonBreaks(source))
    }

    @Test fun keepsUnfencedDiagramLinesTight() {
        val source = "┌────┐\n│ CPU │\n└────┘"
        assertEquals(source, normalizeLessonBreaks(source))
    }

    @Test fun recognizesWholeLineEquationsOnly() {
        assertEquals(
            "订单利润 = 实收金额 − 商品成本 − 各项费用",
            formulaCandidate("订单利润 = 实收金额 - 商品成本 - 各项费用"),
        )
        // 以句号收尾或过长的是讲解句，不能变成色块。
        assertNull(formulaCandidate("没有优惠时利润 = 200-100-20 = 80 元。"))
        assertNull(formulaCandidate("结论先看成本"))
    }

    @Test fun normalizesEquationOperatorsWithoutTouchingDates() {
        assertEquals("利润 = 80", normalizeEquationOperators("利润=80"))
        // 数字之间的运算符用不换行空格粘住，行尾不会只剩一个减号。
        assertEquals(
            "170\u00A0−\u00A0100\u00A0−\u00A020\u00A0=\u00A050",
            normalizeEquationOperators("170-100-20 = 50"),
        )
        // 日期区间不能被当成减法。
        assertEquals("2024-01-05 下单 = 当天发货", normalizeEquationOperators("2024-01-05 下单 = 当天发货"))
    }

    @Test fun promotesShortColonParagraphToSubhead() {
        assertEquals("先讲一个最常被忽略的底层公式：", subheadCandidate("先讲一个最常被忽略的底层公式："))
        // 带句号、过长或含等号的都是正文。
        assertNull(subheadCandidate("先讲一个最常被忽略的底层公式：然后再看价格变化。"))
        assertNull(subheadCandidate("利润 = 实收金额："))
    }
}
