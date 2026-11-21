package com.lifeskill.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.QuadrantPoint
import com.lifeskill.app.model.TeachingAction
import com.lifeskill.app.model.TeachingQuadrant
import com.lifeskill.app.model.TeachingState
import kotlin.math.hypot

/**
 * 四象限：拖动点位改判，松手才提交坐标；点一下看依据。
 * 坐标统一按 0–100 存储，横纵各自映射，避免不同屏宽下位置漂移。
 */
@Composable
internal fun TeachingQuadrantContent(
    block: TeachingQuadrant, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    val density = LocalDensity.current
    val accentColor = aiColors.accent
    val lineColor = Line
    val mutedColor = Muted
    val inkColor = Ink
    val positions = remember(block.id) {
        mutableStateMapOf<String, Pair<Float, Float>>().apply {
            block.points.forEach { point ->
                val saved = state.positions[point.id]
                put(
                    point.id,
                    (saved?.getOrNull(0)?.toFloat() ?: point.x.toFloat()) to
                        (saved?.getOrNull(1)?.toFloat() ?: point.y.toFloat()),
                )
            }
        }
    }
    // 重置或换课时把外部状态同步回来；没有保存过就回到模型给的位置。
    LaunchedEffect(state.positions, block.id) {
        block.points.forEach { point ->
            val saved = state.positions[point.id]
            positions[point.id] = if (saved != null) {
                (saved.getOrNull(0)?.toFloat() ?: point.x.toFloat()) to (saved.getOrNull(1)?.toFloat() ?: point.y.toFloat())
            } else {
                point.x.toFloat() to point.y.toFloat()
            }
        }
    }
    var dragging by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    val padLeft = with(density) { 34.dp.toPx() }
    val padRight = with(density) { 12.dp.toPx() }
    val padTop = with(density) { 14.dp.toPx() }
    val padBottom = with(density) { 26.dp.toPx() }
    val hitRadius = with(density) { 30.dp.toPx() }

    fun centerOf(point: QuadrantPoint): Offset {
        val (x, y) = positions[point.id] ?: (point.x.toFloat() to point.y.toFloat())
        return Offset(
            padLeft + (canvasSize.x - padLeft - padRight) * (x / 100f),
            canvasSize.y - padBottom - (canvasSize.y - padTop - padBottom) * (y / 100f),
        )
    }
    fun nearest(position: Offset): String? = block.points
        .minByOrNull { hypot(centerOf(it).x - position.x, centerOf(it).y - position.y) }
        ?.takeIf { hypot(centerOf(it).x - position.x, centerOf(it).y - position.y) < hitRadius }
        ?.id

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).testTag("quadrant-${block.id}")
                .onSizeChanged { canvasSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                .semantics { contentDescription = block.quadrantDescription(positions) }
                .pointerInput(block.id, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = nearest(offset)
                            selected = dragging
                        },
                        onDragEnd = {
                            dragging?.let { id ->
                                val (x, y) = positions.getValue(id)
                                dragging = null
                                onAction(TeachingAction.Place(id, x.toDouble(), y.toDouble()))
                            }
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        val id = dragging
                        val width = canvasSize.x - padLeft - padRight
                        val height = canvasSize.y - padTop - padBottom
                        if (id != null && width > 0 && height > 0) {
                            val x = ((change.position.x - padLeft) / width * 100f).coerceIn(0f, 100f)
                            val y = ((canvasSize.y - padBottom - change.position.y) / height * 100f).coerceIn(0f, 100f)
                            positions[id] = x to y
                        }
                    }
                }
                // 拖动放在外层先接触事件；单独点击落在内层，避免点击先消费 down。
                .pointerInput(block.id, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> selected = nearest(offset) }
                },
        ) {
            val plotLeft = padLeft
            val plotTop = padTop
            val plotWidth = size.width - padLeft - padRight
            val plotHeight = size.height - padTop - padBottom
            val midX = plotLeft + plotWidth / 2
            val midY = plotTop + plotHeight / 2

            drawLine(lineColor, Offset(plotLeft, midY), Offset(plotLeft + plotWidth, midY), 1.dp.toPx())
            drawLine(lineColor, Offset(midX, plotTop), Offset(midX, plotTop + plotHeight), 1.dp.toPx())

            val weak = Paint().apply { color = mutedColor.toArgb(); textSize = 11.sp.toPx(); isAntiAlias = true }
            val strong = Paint().apply { color = inkColor.toArgb(); textSize = 11.sp.toPx(); isAntiAlias = true }
            fun text(value: String, x: Float, y: Float, paint: Paint = weak) =
                drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)

            block.quadrants.getOrNull(0)?.let { text(it, plotLeft + 6.dp.toPx(), plotTop + 14.sp.toPx()) }
            block.quadrants.getOrNull(1)?.let {
                text(it, plotLeft + plotWidth - weak.measureText(it) - 6.dp.toPx(), plotTop + 14.sp.toPx())
            }
            block.quadrants.getOrNull(2)?.let {
                text(it, plotLeft + 6.dp.toPx(), plotTop + plotHeight - 8.dp.toPx())
            }
            block.quadrants.getOrNull(3)?.let {
                text(it, plotLeft + plotWidth - weak.measureText(it) - 6.dp.toPx(), plotTop + plotHeight - 8.dp.toPx())
            }
            text(block.xLabel, plotLeft + plotWidth - weak.measureText(block.xLabel), size.height - 5.dp.toPx())
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, 11.dp.toPx(), plotTop + plotHeight / 2)
            text(block.yLabel, 11.dp.toPx() - weak.measureText(block.yLabel) / 2, plotTop + plotHeight / 2)
            drawContext.canvas.nativeCanvas.restore()

            block.points.forEach { point ->
                val center = centerOf(point)
                val active = dragging == point.id || selected == point.id
                if (active) drawCircle(accentColor.copy(alpha = .18f), 14.dp.toPx(), center)
                drawCircle(accentColor, 5.dp.toPx(), center)
                if (active) drawCircle(accentColor, 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                // 靠近右边界时把标签画到点的左侧，避免被卡片裁掉。
                val labelWidth = strong.measureText(point.label)
                val textX = if (center.x + 9.dp.toPx() + labelWidth > plotLeft + plotWidth) {
                    center.x - 9.dp.toPx() - labelWidth
                } else {
                    center.x + 9.dp.toPx()
                }
                text(point.label, textX, center.y + 4.dp.toPx(), strong)
            }
        }

        val current = selected?.let { id -> block.points.firstOrNull { it.id == id } }
        if (current != null) {
            val (x, y) = positions[current.id] ?: (current.x.toFloat() to current.y.toFloat())
            Text(
                "${current.label} · ${block.quadrants.getOrNull(block.quadrantIndex(x.toDouble(), y.toDouble())).orEmpty()}",
                style = MaterialTheme.typography.titleSmall, color = aiColors.accent,
            )
            current.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        } else if (block.caption == null) {
            // 有脚注时不再重复默认提示，避免两句话意思一样。
            Text("拖动点位改判，点一下看依据。", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

internal fun TeachingQuadrant.quadrantIndex(x: Double, y: Double): Int = when {
    y >= 50 && x < 50 -> 0
    y >= 50 -> 1
    x < 50 -> 2
    else -> 3
}

private fun TeachingQuadrant.quadrantDescription(positions: Map<String, Pair<Float, Float>>): String = buildString {
    append("四象限图：横轴 ").append(xLabel).append("，纵轴 ").append(yLabel).append("。")
    points.forEach { point ->
        val (x, y) = positions[point.id] ?: (point.x.toFloat() to point.y.toFloat())
        append(point.label).append("在").append(quadrants.getOrNull(quadrantIndex(x.toDouble(), y.toDouble())).orEmpty()).append("。")
    }
    caption?.let { append(it) }
}
