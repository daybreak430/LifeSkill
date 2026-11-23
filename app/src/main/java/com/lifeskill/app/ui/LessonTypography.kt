package com.lifeskill.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 教学正文的排版 token。
 *
 * 字号、行高与块间距集中在这里，渲染层只取用，避免每加一种块就各自写一套数值。
 * 节奏来自对比：块间距大、块内间距小、层级字号有明显落差；行内数字统一等宽，
 * 金额与百分比才能按列对齐。
 */
internal object LessonType {
    // 块间距：标题与自己的正文更近，与上一块更远。
    val headingTop = 18.dp
    val headingBottom = 6.dp
    val paragraphGap = 16.dp
    val listItemGap = 12.dp
    val nestedItemGap = 4.dp
    val quoteGap = 6.dp

    // 列表符号：顶层实心圆点，嵌套更小更淡。
    val bulletSize = 5.dp
    val nestedBulletSize = 4.dp
    val nestedIndent = 16.dp

    val body: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp, fontFeatureSettings = "tnum")

    val heading: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp, lineHeight = 29.sp, fontFeatureSettings = "tnum",
        )

    val subhead: TextStyle
        @Composable get() = body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    // 引用块的文字比正文小一档，让“提醒/区别”在视觉上真的降一级。
    val quote: TextStyle
        @Composable get() = body.copy(fontSize = 14.5.sp, lineHeight = 24.sp)

    val nested: TextStyle
        @Composable get() = body.copy(fontSize = 15.sp, lineHeight = 24.sp)

    val formula: TextStyle
        @Composable get() = body.copy(fontSize = 15.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
}
