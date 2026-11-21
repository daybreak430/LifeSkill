package com.lifeskill.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.*
import kotlin.math.roundToInt

@Composable
internal fun TeachingChartContent(block: TeachingChart, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val prediction = block.prediction
    if (prediction != null && !state.revealed) {
        Text(prediction.question, color = Ink, style = MaterialTheme.typography.bodyLarge)
        prediction.options.forEachIndexed { index, text ->
            TeachingOption(text, state.prediction == index, enabled,
                Modifier.fillMaxWidth().testTag("prediction-${block.id}-choice-$index"),
                { onAction(TeachingAction.Predict(index)) })
        }
        Button(onClick = { onAction(TeachingAction.Reveal) }, enabled = enabled && state.prediction != null,
            modifier = Modifier.fillMaxWidth().testTag("prediction-${block.id}-reveal")) { Text("验证预测") }
        return
    }
    val demand = block.mode == TeachingChartMode.LINEAR_DEMAND
    // 滑动过程只改本地展示，松手才提交经过领域规则校验的值，避免逐帧写数据库。
    var price by remember(block.id, state.value) { mutableFloatStateOf((state.value ?: 6.0).toFloat()) }
    val selected = if (demand) price.roundToInt() - 2 else state.selection?.toIntOrNull() ?: 0
    val points = remember(block) {
        if (demand) (2..10).map { value -> PlotPoint(block.demandAt(value.toDouble()), value.toDouble(), "${value}元") }
        else block.points.mapIndexed { index, point ->
            // 散点用真实的 x；其他样式按顺序排列，避免模型乱填 x 把线画歪。
            PlotPoint(if (block.style == TeachingChartStyle.SCATTER) point.x ?: index.toDouble() else index.toDouble(), point.value, point.label)
        }
    }
    if (demand) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("价格 ${price.roundToInt()} 元", style = MaterialTheme.typography.titleMedium, color = aiColors.accent)
            Text("需求量 ${block.demandAt(price.toDouble()).roundToInt()} ${block.quantityUnit}", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
    } else {
        val point = block.points.getOrNull(selected) ?: block.points.first()
        Text("${point.label} · ${number(point.value)}", style = MaterialTheme.typography.titleMedium, color = aiColors.accent)
    }
    if (block.style == TeachingChartStyle.WATERFALL) {
        TeachingWaterfall(block, selected, enabled) { index -> onAction(TeachingAction.Select(index.toString())) }
    } else TeachingPlot(points, demand, selected,
        if (demand) "需求量（${block.quantityUnit}）" else block.xLabel,
        if (demand) "价格（元）" else block.yLabel, enabled, if (demand) TeachingChartStyle.LINE else block.style) { index ->
        onAction(if (demand) TeachingAction.SetValue(index + 2.0) else TeachingAction.Select(index.toString()))
    }
    if (demand) {
        Slider(value = price, onValueChange = { price = it.roundToInt().toFloat() },
            onValueChangeFinished = { onAction(TeachingAction.SetValue(price.toDouble())) },
            valueRange = 2f..10f, steps = 7, enabled = enabled,
            modifier = Modifier.testTag("teaching-price-${block.id}").semantics { contentDescription = "调整价格，单位元" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("2元", color = Muted, style = MaterialTheme.typography.labelSmall)
            Text("10元", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Text("教学模型：Q = 120 − 10P，其他条件不变。", style = MaterialTheme.typography.bodySmall, color = Muted)
    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // FlowRow 里的芯片在真实消息列表中收不到点击，改用固定每行三个的分行布局。
        block.points.withIndex().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (index, point) ->
                    TeachingOption("${point.label} ${number(point.value)}", index == selected, enabled,
                        modifier = Modifier.testTag("chart-chip-${block.id}-$index"),
                        onClick = { onAction(TeachingAction.Select(index.toString())) })
                }
            }
        }
    }
    if (prediction != null) Text(
        (if (state.prediction == prediction.answer) "预测方向正确。" else "结果与你的预测不同。") + prediction.explanation,
        color = Ink, style = MaterialTheme.typography.bodyMedium,
    )
}

private data class PlotPoint(val horizontal: Double, val vertical: Double, val label: String)
private data class PlotRange(val horizontalMin: Double, val horizontalMax: Double, val verticalMin: Double, val verticalMax: Double)

@Composable
private fun TeachingPlot(
    points: List<PlotPoint>, demand: Boolean, selected: Int, xLabel: String, yLabel: String,
    enabled: Boolean, style: TeachingChartStyle, onSelect: (Int) -> Unit,
) {
    val ranges = remember(points, demand, style) {
        if (demand) PlotRange(0.0, 120.0, 0.0, 12.0)
        else {
            val minimum = minOf(0.0, points.minOf { it.vertical })
            val maximum = maxOf(0.0, points.maxOf { it.vertical }).coerceAtLeast(minimum + 1.0)
            when (style) {
                TeachingChartStyle.BAR -> PlotRange(-.5, points.size - .5, minimum, maximum)
                TeachingChartStyle.SCATTER -> {
                    val lowest = points.minOf { it.horizontal }
                    val highest = points.maxOf { it.horizontal }
                    val padding = ((highest - lowest).takeIf { it > 0 } ?: 1.0) * .08
                    PlotRange(lowest - padding, highest + padding, minimum, maximum)
                }
                else -> PlotRange(0.0, (points.size - 1).toDouble(), minimum, maximum)
            }
        }
    }
    val density = LocalDensity.current
    val left = with(density) { 44.dp.toPx() }
    val right = with(density) { 18.dp.toPx() }
    val top = with(density) { 18.dp.toPx() }
    val bottom = with(density) { 32.dp.toPx() }
    val hitRadius = with(density) { 28.dp.toPx() }
    val paint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = with(density) { 11.sp.toPx() } } }
    val lineColor = Line
    val accent = aiColors.accent
    val marker = Gold
    val textColor = Muted
    fun location(point: PlotPoint, width: Float, height: Float) = Offset(
        left + ((point.horizontal - ranges.horizontalMin) / (ranges.horizontalMax - ranges.horizontalMin)).toFloat() * (width - left - right),
        height - bottom - ((point.vertical - ranges.verticalMin) / (ranges.verticalMax - ranges.verticalMin)).toFloat() * (height - top - bottom),
    )
    Text(yLabel, color = Muted, style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(210.dp).testTag("teaching-chart-plot")
        .semantics {
            role = Role.Image
            contentDescription = "$yLabel，横轴$xLabel。" + if (demand) "线性需求曲线，价格降低时需求量增加。"
                else points.joinToString("；") { "${it.label} ${number(it.vertical)}" }
        }.pointerInput(points, enabled) {
            detectTapGestures { position ->
                if (enabled) {
                    val closest = points.indices.minByOrNull { if (style == TeachingChartStyle.BAR) kotlin.math.abs(location(points[it], size.width.toFloat(), size.height.toFloat()).x - position.x) else (location(points[it], size.width.toFloat(), size.height.toFloat()) - position).getDistanceSquared() }
                    if (closest != null && (style == TeachingChartStyle.BAR && position.x in left..(size.width - right) && position.y in top..(size.height - bottom) || (location(points[closest], size.width.toFloat(), size.height.toFloat()) - position).getDistance() <= hitRadius)) {
                        onSelect(closest)
                    }
                }
            }
        }) {
        paint.color = textColor.toArgb()
        paint.textAlign = Paint.Align.RIGHT
        repeat(5) { index ->
            val fraction = index / 4f
            val vertical = size.height - bottom - fraction * (size.height - top - bottom)
            drawLine(lineColor, Offset(left, vertical), Offset(size.width - right, vertical), 1.dp.toPx())
            val label = number(ranges.verticalMin + fraction * (ranges.verticalMax - ranges.verticalMin))
            drawContext.canvas.nativeCanvas.drawText(label, left - 8.dp.toPx(), vertical + paint.textSize / 3, paint)
        }
        drawLine(lineColor, Offset(left, top), Offset(left, size.height - bottom), 1.dp.toPx())
        val sorted = points.sortedBy(PlotPoint::horizontal)
        val trace = Path().apply {
            sorted.forEachIndexed { index, point ->
                val offset = location(point, size.width, size.height)
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        if (style == TeachingChartStyle.AREA) {
            val baseline = location(PlotPoint(0.0, 0.0, ""), size.width, size.height).y
            drawPath(Path().apply {
                addPath(trace)
                lineTo(location(sorted.last(), size.width, size.height).x, baseline)
                lineTo(location(sorted.first(), size.width, size.height).x, baseline)
                close()
            }, accent.copy(alpha = .18f))
        }
        // 散点只画点，不连线：连线会暗示不存在的趋势。
        if (style != TeachingChartStyle.BAR && style != TeachingChartStyle.SCATTER) drawPath(trace, accent, style = Stroke(2.dp.toPx()))
        points.forEachIndexed { index, point ->
            val offset = location(point, size.width, size.height)
            if (style == TeachingChartStyle.BAR) {
                val halfWidth = ((size.width - left - right) / (points.size + 1) * .28f).coerceAtMost(16.dp.toPx())
                val baseline = location(PlotPoint(0.0, 0.0, ""), size.width, size.height).y
                drawRect(if (index == selected) marker else accent.copy(alpha = .55f),
                    Offset(offset.x - halfWidth, minOf(offset.y, baseline)),
                    androidx.compose.ui.geometry.Size(halfWidth * 2, kotlin.math.abs(baseline - offset.y).coerceAtLeast(2.dp.toPx())))
            } else drawCircle(if (index == selected) marker else accent, if (index == selected) 6.dp.toPx() else 3.dp.toPx(), offset)
        }
        paint.textAlign = Paint.Align.CENTER
        if (demand) listOf(0, 60, 120).forEach { value ->
            val offset = location(PlotPoint(value.toDouble(), 0.0, ""), size.width, size.height)
            drawContext.canvas.nativeCanvas.drawText(value.toString(), offset.x, size.height - 8.dp.toPx(), paint)
        } else if (style == TeachingChartStyle.SCATTER) {
            listOf(ranges.horizontalMin, (ranges.horizontalMin + ranges.horizontalMax) / 2, ranges.horizontalMax)
                .forEach { value ->
                    val offset = location(PlotPoint(value, 0.0, ""), size.width, size.height)
                    drawContext.canvas.nativeCanvas.drawText(number(value), offset.x, size.height - 8.dp.toPx(), paint)
                }
        } else points.forEachIndexed { index, point ->
            if (points.size <= 6 || index == 0 || index == points.lastIndex) {
                val offset = location(point, size.width, size.height)
                drawContext.canvas.nativeCanvas.drawText(point.label.take(5), offset.x, size.height - 8.dp.toPx(), paint)
            }
        }
    }
    Text(xLabel, Modifier.fillMaxWidth(), color = Muted, style = MaterialTheme.typography.labelSmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.End)
}

internal fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", value)

private data class WaterfallBar(val from: Double, val to: Double, val point: TeachingPoint)

/**
 * 瀑布图：首尾是合计柱，中间按增减量累加。
 * 累计基线由应用计算，模型只给增减量，避免它算错还画出来。
 */
@Composable
private fun TeachingWaterfall(block: TeachingChart, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val bars = remember(block) {
        var running = 0.0
        block.points.map { point ->
            val from = if (point.total) 0.0 else running
            val to = if (point.total) point.value else running + point.value
            running = to
            WaterfallBar(from, to, point)
        }
    }
    val lowest = minOf(0.0, bars.minOf { minOf(it.from, it.to) })
    val highest = maxOf(0.0, bars.maxOf { maxOf(it.from, it.to) }).coerceAtLeast(lowest + 1.0)
    val span = highest - lowest
    val green = Color(0xFF1D9E75)
    val red = Color(0xFFE24B4A)
    val accentColor = aiColors.accent
    val lineColor = Line
    val mutedColor = Muted
    val valueTexts = bars.map { bar ->
        when {
            bar.point.total -> number(bar.point.value)
            bar.point.value >= 0 -> "+" + number(bar.point.value)
            else -> "−" + number(-bar.point.value)
        }
    }
    val labelTexts = block.points.map { it.label.take(4) }
    val description = "瀑布图：" + block.points.joinToString("；") { "${it.label} ${number(it.value)}" }
    val painter = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER } }
    val labelPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER } }
    Canvas(
        Modifier.fillMaxWidth().height(200.dp).testTag("waterfall-${block.id}")
            .semantics { contentDescription = description }
            .pointerInput(block.id, enabled, bars) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val left = 8.dp.toPx()
                    val slot = (size.width - left * 2) / bars.size
                    val index = ((offset.x - left) / slot).toInt().coerceIn(0, bars.lastIndex)
                    onSelect(index)
                }
            },
    ) {
        val left = 8.dp.toPx()
        val bottom = 24.dp.toPx()
        val top = 20.dp.toPx()
        val plotHeight = size.height - top - bottom
        val slot = (size.width - left * 2) / bars.size
        val barWidth = slot * .56f
        fun y(value: Double): Float = top + ((highest - value) / span).toFloat() * plotHeight

        drawLine(lineColor, Offset(left, y(0.0)), Offset(size.width - left, y(0.0)), 1.dp.toPx())
        painter.textSize = 11.sp.toPx()
        labelPaint.textSize = 11.sp.toPx()
        labelPaint.color = mutedColor.toArgb()

        bars.forEachIndexed { index, bar ->
            val centerX = left + slot * (index + .5f)
            val barTop = y(maxOf(bar.from, bar.to))
            val barBottom = y(minOf(bar.from, bar.to))
            val color = when {
                bar.point.total -> accentColor
                bar.to >= bar.from -> green
                else -> red
            }
            drawRect(color.copy(alpha = if (index == selected) .45f else .28f),
                topLeft = Offset(centerX - barWidth / 2, barTop),
                size = Size(barWidth, (barBottom - barTop).coerceAtLeast(1.dp.toPx())))
            drawRect(color, topLeft = Offset(centerX - barWidth / 2, barTop),
                size = Size(barWidth, (barBottom - barTop).coerceAtLeast(1.dp.toPx())), style = Stroke(1.dp.toPx()))

            if (index < bars.lastIndex) {
                val nextX = left + slot * (index + 1.5f)
                drawLine(mutedColor.copy(alpha = .6f), Offset(centerX + barWidth / 2, y(bar.to)),
                    Offset(nextX - barWidth / 2, y(bar.to)), 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            painter.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(valueTexts[index], centerX, barTop - 6.dp.toPx(), painter)
            drawContext.canvas.nativeCanvas.drawText(labelTexts[index], centerX, size.height - 6.dp.toPx(), labelPaint)
        }
    }
}
