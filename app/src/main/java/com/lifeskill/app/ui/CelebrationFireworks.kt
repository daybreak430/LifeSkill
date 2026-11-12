package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val FIREWORK_LAUNCH_FRACTION = .22f

private data class Firework(
    val center: Offset, val start: Float, val end: Float, val radius: Float, val color: Color, val rays: List<Offset>,
)

private val fireworks = listOf(
    firework(.19f, .24f, 0f, .43f, .22f, Color(0xFFFFCF7B)),
    firework(.77f, .30f, .10f, .53f, .24f, Color(0xFF85E8CE)),
    firework(.18f, .68f, .24f, .65f, .20f, Color(0xFFAAB8FF)),
    firework(.81f, .72f, .36f, .81f, .22f, Color(0xFFFFCB89)),
    firework(.55f, .16f, .53f, 1f, .27f, Color(0xFFA3F0D8)),
)

internal val fireworkBurstProgress = fireworks.map { burst ->
    burst.start + (burst.end - burst.start) * FIREWORK_LAUNCH_FRACTION
}

private fun firework(x: Float, y: Float, start: Float, end: Float, radius: Float, color: Color): Firework {
    val random = Random((x * 1000).toInt())
    val rays = List(26) { index ->
        val angle = (index + random.nextFloat() * .65f) * (2f * PI.toFloat() / 26f)
        val speed = .45f + .55f * random.nextFloat()
        Offset(cos(angle), sin(angle)) * speed
    }
    return Firework(Offset(x, y), start, end, radius, color, rays)
}

@Composable
internal fun CelebrationFireworks(modifier: Modifier, progress: () -> Float, opacity: () -> Float) {
    Canvas(modifier) {
        val time = progress()
        if (time >= 1f) return@Canvas
        val visible = opacity()
        fireworks.forEach { burst ->
            if (time in burst.start..<burst.end) {
                drawFirework(burst, motionPhase(time, burst.start, burst.end), visible)
            }
        }
    }
}

private fun DrawScope.drawFirework(burst: Firework, phase: Float, opacity: Float) {
    val origin = Offset(size.width * burst.center.x, size.height * burst.center.y)
    val reach = size.minDimension * burst.radius
    val launch = FIREWORK_LAUNCH_FRACTION
    if (phase < launch) {
        val rise = phase / launch
        val tip = origin + Offset(0f, reach * (1f - rise))
        drawLine(burst.color.copy(alpha = rise * opacity), tip + Offset(0f, reach * .23f), tip, 2.dp.toPx(), StrokeCap.Round)
        return
    }

    // 爆开后粒子减速散开，尾迹跟随较早的位置，末段受重力下落并淡出。
    val spread = motionPhase(phase, launch, 1f)
    val fade = (1f - spread) * opacity
    val flash = 1f - motionPhase(spread, 0f, .27f)
    if (flash > 0f) {
        val flashRadius = reach * .31f
        drawCircle(Brush.radialGradient(listOf(burst.color.copy(alpha = .48f * flash * opacity), Color.Transparent),
            origin, flashRadius), flashRadius, origin)
    }
    val tailTime = (spread - .12f).coerceAtLeast(0f)
    val front = reach * spread * (2f - spread)
    val tail = reach * tailTime * (2f - tailTime)
    val drop = Offset(0f, reach * .31f * spread * spread)
    val tailDrop = Offset(0f, reach * .31f * tailTime * tailTime)
    burst.rays.forEachIndexed { index, ray ->
        val tip = origin + ray * front + drop
        val start = origin + ray * tail + tailDrop
        val dot = if (index % 4 == 0) 2.dp else 1.35.dp
        drawLine(burst.color.copy(alpha = .60f * fade), start, tip, dot.toPx(), StrokeCap.Round)
        drawCircle(burst.color.copy(alpha = fade), dot.toPx(), tip)
    }
}
