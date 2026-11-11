package com.lifeskill.app.ui

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

private val asciiBorder = Regex("""\+[=-]{2,}\+""")
private val asciiBranch = Regex("""^[ |]*(?:[+|`\\])--""")

internal fun looksLikeTextDiagram(source: String): Boolean {
    val lines = source.lineSequence().filter(String::isNotBlank).toList()
    if (lines.size < 2) return false
    return lines.count { line -> line.any { it in '\u2500'..'\u257f' } } >= 2 ||
        (lines.any(asciiBorder::containsMatchIn) && lines.any { '|' in it }) ||
        lines.count(asciiBranch::containsMatchIn) >= 2
}

@Composable
internal fun LessonDiagram(source: String) {
    val body = MaterialTheme.typography.bodyMedium
    val style = body.copy(
        // 收紧字符图的行距，让上下行的竖线相接。
        fontFamily = FontFamily.Monospace,
        lineHeight = body.fontSize * 1.14f, letterSpacing = 0.sp, textDirection = TextDirection.Ltr,
    )
    val measurer = rememberTextMeasurer()
    val text = remember(source, measurer, style) { diagramText(source.trimEnd('\r', '\n'), style, measurer) }
    Surface(shape = ControlShape, color = Ground, border = BorderStroke(1.dp, Line)) {
        Text(
            text, Modifier.testTag("lesson-diagram").horizontalScroll(rememberScrollState()).padding(10.dp),
            color = Ink, style = style, softWrap = false,
        )
    }
}

private fun diagramText(source: String, style: TextStyle, measurer: TextMeasurer): AnnotatedString {
    fun advance(text: String) = measurer.measure(text, style = style, softWrap = false).multiParagraph.getLineRight(0)
    val cell = advance("0")
    val scales = mutableMapOf<String, Float>()
    val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(source) }
    var start = graphemes.first()
    var end = graphemes.next()
    var column = 0
    return buildAnnotatedString {
        while (end != BreakIterator.DONE) {
            val glyph = source.substring(start, end)
            when (glyph) {
                "\n", "\r\n", "\r" -> {
                    append('\n')
                    column = 0
                }
                "\t" -> {
                    val spaces = 4 - column % 4
                    append(" ".repeat(spaces))
                    column += spaces
                }
                else -> {
                    val widthClass = UCharacter.getIntPropertyValue(source.codePointAt(start), UProperty.EAST_ASIAN_WIDTH)
                    val cells = if (widthClass == UCharacter.EastAsianWidth.WIDE ||
                        widthClass == UCharacter.EastAsianWidth.FULLWIDTH
                    ) 2 else 1
                    val from = length
                    append(glyph)
                    if (glyph.any { it > '~' }) {
                        // 中文等系统回退字形与等宽字体的字宽不同，按一列或两列校正，保留原文以便选择和复制。
                        val scale = scales.getOrPut(glyph) {
                            val width = advance("0${glyph}0") - 2 * cell
                            if (width > 0f) cells * cell / width else 1f
                        }
                        if (abs(scale - 1f) > .005f) {
                            addStyle(SpanStyle(textGeometricTransform = TextGeometricTransform(scaleX = scale)), from, length)
                        }
                    }
                    column += cells
                }
            }
            start = end
            end = graphemes.next()
        }
    }
}
