package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.FunnelMode
import com.lifeskill.app.model.TeachingFunnel
import com.lifeskill.app.model.TeachingMatrix
import com.lifeskill.app.model.TeachingRange

private val matrixGreen = Color(0xFF1D9E75)
private val matrixAmber = Color(0xFFEF9F27)
private val matrixRed = Color(0xFFE24B4A)
private val matrixGray = Color(0xFF888780)

/**
 * 热力矩阵：行标签 + 分数网格。颜色只表达分数高低，不表达对错，
 * 因此不加图标、不加语义色说明，由脚注解释分数含义。
 */
@Composable
internal fun TeachingMatrixContent(block: TeachingMatrix) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().testTag("matrix-${block.id}")
            .semantics { contentDescription = block.matrixDescription() },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1.4f))
            block.columns.forEach { label ->
                Text(
                    label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Muted, textAlign = TextAlign.Center, maxLines = 2,
                )
            }
        }
        block.rows.forEachIndexed { rowIndex, label ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2)
                block.values.getOrNull(rowIndex).orEmpty().forEach { score ->
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(ControlShape)
                            .background(matrixColor(score).copy(alpha = .18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (score == score.toLong().toDouble()) score.toLong().toString()
                            else String.format(java.util.Locale.ROOT, "%.1f", score),
                            style = MaterialTheme.typography.bodyMedium, color = Ink,
                        )
                    }
                }
            }
        }
        block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

private fun matrixColor(score: Double): Color = when {
    score >= 4 -> matrixGreen
    score >= 3 -> matrixAmber
    score >= 1 -> matrixRed
    else -> matrixGray
}

private fun TeachingMatrix.matrixDescription(): String = buildString {
    append("评分矩阵，").append(rows.size).append(" 行 ").append(columns.size).append(" 列。")
    rows.forEachIndexed { index, label ->
        append(label).append("：")
        columns.forEachIndexed { columnIndex, column ->
            append(column).append(" ")
            val score = values.getOrNull(index)?.getOrNull(columnIndex) ?: return@forEachIndexed
            append(if (score == score.toLong().toDouble()) score.toLong().toString() else score.toString())
            append("，")
        }
        append("。")
    }
    caption?.let { append(it) }
}

/** 区间条：底色轨 + 范围带 + 当前值标记，读屏一次给出范围与当前值。 */
@Composable
internal fun TeachingRangeContent(block: TeachingRange) {
    val span = (block.maximum - block.minimum).takeIf { it > 0 } ?: 1.0
    val start = ((block.low - block.minimum) / span).toFloat().coerceIn(0f, 1f)
    val width = ((block.high - block.low) / span).toFloat().coerceIn(0f, 1f - start)
    val marker = block.value?.let { (((it - block.minimum) / span).toFloat()).coerceIn(0f, 1f) }
    Column(
        Modifier.fillMaxWidth().testTag("range-${block.id}")
            .semantics { contentDescription = block.rangeDescription() },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(block.label, style = MaterialTheme.typography.titleSmall, color = Ink)
        BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
            val trackWidth = maxWidth
            Box(
                Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp)).background(Line.copy(alpha = .35f)),
            )
            Box(
                Modifier.width(trackWidth * width).height(8.dp).align(Alignment.CenterStart)
                    .offset(x = trackWidth * start)
                    .clip(RoundedCornerShape(4.dp)).background(matrixGreen.copy(alpha = .38f)),
            )
            marker?.let { position ->
                Box(
                    Modifier.offset(x = trackWidth * position - 1.dp).width(2.dp).height(22.dp)
                        .align(Alignment.CenterStart).background(matrixGreen),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatBound(block.minimum, block.unit), style = MaterialTheme.typography.labelSmall, color = Muted)
            block.value?.let {
                Text("当前 ${formatBound(it, block.unit)}", style = MaterialTheme.typography.labelSmall, color = Ink)
            }
            Text(formatBound(block.maximum, block.unit), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

private fun formatBound(value: Double, unit: String): String {
    val number = if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", value)
    return number + unit
}

private fun TeachingRange.rangeDescription(): String = buildString {
    append("区间图：").append(label).append("，范围 ")
    append(formatBound(low, unit)).append(" 到 ").append(formatBound(high, unit))
    value?.let { append("，当前 ").append(formatBound(it, unit)) }
    append("。")
    caption?.let { append(it) }
}

/** 漏斗与金字塔共用一套渲染，mode 只决定色阶方向与语义说明。 */
@Composable
internal fun TeachingFunnelContent(block: TeachingFunnel) {
    Column(
        Modifier.fillMaxWidth().testTag("funnel-${block.id}")
            .semantics { contentDescription = block.funnelDescription() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        block.stages.forEachIndexed { index, stage ->
            val alpha = if (block.mode == FunnelMode.FUNNEL) .2f + index * .12f else .36f - index * .08f
            Box(
                Modifier.fillMaxWidth(stage.ratio / 100f).height(44.dp)
                    .clip(ControlShape).background(matrixGreen.copy(alpha = alpha.coerceIn(.12f, .6f))),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stage.label, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 1)
                    stage.value?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1)
                    }
                }
            }
        }
        block.caption?.let {
            Text(it, Modifier.fillMaxWidth().padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

private fun TeachingFunnel.funnelDescription(): String = buildString {
    append(if (mode == FunnelMode.FUNNEL) "漏斗图：" else "金字塔：")
    append(title).append("。")
    stages.forEach { stage ->
        append(stage.label)
        stage.value?.let { append(" ").append(it) }
        append("，占比 ").append(stage.ratio).append("%。")
    }
    caption?.let { append(it) }
}
