package com.lifeskill.app

import com.lifeskill.app.ui.normalizeLessonBreaks
import org.junit.Assert.assertEquals
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
}
