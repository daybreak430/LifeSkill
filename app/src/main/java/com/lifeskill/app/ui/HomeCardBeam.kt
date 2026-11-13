package com.lifeskill.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val HOME_BEAM_LAP_MS = 4_000

@Composable
internal fun Modifier.homeCardBeam(cornerRadius: Dp): Modifier {
    val colors = studyColors
    val progress = rememberInfiniteTransition(label = "首页流光").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HOME_BEAM_LAP_MS, easing = LinearEasing)),
        label = "边框位置",
    )

    return drawWithCache {
        val rim = Stroke(1.6.dp.toPx())
        val nearGlow = Stroke(5.dp.toPx())
        val farGlow = Stroke(10.dp.toPx())
        val inset = rim.width / 2
        val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
        val outline = Path().apply {
            addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset), CornerRadius(radius)))
        }
        val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
        val length = measure.length
        val tailOffset = 23.dp.toPx()
        val tailRadius = 55.dp.toPx()
        val headRadius = 20.dp.toPx()
        val glowColors = listOf(colors.heroBeam.copy(alpha = .19f), colors.heroBeam.copy(alpha = 0f))
        val tailColors = listOf(colors.heroBeam.copy(alpha = .9f), colors.heroBeam.copy(alpha = 0f))
        val headColors = listOf(colors.heroBeamHighlight, colors.heroBeamHighlight.copy(alpha = 0f))

        onDrawWithContent {
            drawContent()
            if (length > 0f) {
                // 按轮廓长度推进，直边与圆角等速。逐帧只读取绘制状态，布局和触控区域保持原样。
                val distance = progress.value * length
                val head = measure.getPosition(distance)
                val tail = measure.getPosition((distance - tailOffset).mod(length))
                val glow = Brush.radialGradient(glowColors, tail, tailRadius)
                drawPath(outline, glow, style = farGlow)
                drawPath(outline, glow, style = nearGlow)
                drawPath(outline, Brush.radialGradient(tailColors, tail, tailRadius), style = rim)
                drawPath(outline, Brush.radialGradient(headColors, head, headRadius), style = rim)
            }
        }
    }
}
