package com.lifeskill.app.ui

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun Modifier.homeCardRefraction(
    tilt: State<Offset>, cornerRadius: Dp, colors: StudyColors,
): Modifier = drawWithCache {
    val rim = Stroke(1.5.dp.toPx())
    val inset = rim.width / 2
    val rimSize = Size(size.width - rim.width, size.height - rim.width)
    val rimCorner = CornerRadius((cornerRadius.toPx() - inset).coerceAtLeast(0f))
    val faceRadius = size.maxDimension * .56f
    val rimRadius = size.minDimension * .76f
    val faceLight = colors.heroReflection
    val tint = colors.heroRefraction
    val rimColors = listOf(faceLight.copy(alpha = .82f), tint.copy(alpha = 0f))

    onDrawBehind {
        val angles = tilt.value
        val strength = (angles.getDistance() / MAX_HOME_TILT_DEGREES).coerceIn(0f, 1f)
        if (strength > 0f && rimSize.minDimension > 0f) {
            // 用同一组倾角定位卡面的高光和边缘亮斑，回正时一同消退。
            val light = Offset(
                center.x + size.width * .54f * angles.y / MAX_HOME_TILT_DEGREES,
                center.y - size.height * .61f * angles.x / MAX_HOME_TILT_DEGREES,
            )
            drawRect(
                brush = Brush.radialGradient(
                    0f to faceLight,
                    .35f to faceLight.copy(alpha = faceLight.alpha * .6f),
                    .67f to tint,
                    1f to tint.copy(alpha = 0f),
                    center = light, radius = faceRadius,
                ),
                alpha = strength,
            )
            drawRoundRect(
                brush = Brush.radialGradient(rimColors, center = light, radius = rimRadius),
                topLeft = Offset(inset, inset), size = rimSize, cornerRadius = rimCorner,
                alpha = strength, style = rim,
            )
        }
    }
}
