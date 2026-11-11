package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.lifeskill.app.model.TreeChallenge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DiagnosticAccent = Color(0xFF9FE8D8)
private val FeynmanAccent = Color(0xFFC9B8FF)
private val ObsidianCenter = Color(0xFF243040)
private val ObsidianEdge = Color(0xFF080D15)
private val CompletionGold = Color(0xFFFFD58A)

@Composable
internal fun ChallengeSigilMedallion(
    kind: TreeChallenge, diameter: Dp, completed: Boolean = false, progress: () -> Float = { 1f },
) {
    val accent = when (kind) {
        TreeChallenge.DIAGNOSTIC -> DiagnosticAccent
        TreeChallenge.FEYNMAN -> FeynmanAccent
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val clock = progress().coerceIn(0f, 1f)
            val charge = motionPhase(clock, 0f, .30f)
            val release = motionPhase(clock, .24f, 1f)
            val radius = size.minDimension * .45f
            val auraRadius = size.minDimension * (.52f + .04f * charge)
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        accent.copy(alpha = (.16f + .18f * charge) * (.32f + .68f * release)),
                        accent.copy(alpha = 0f),
                    ),
                    center,
                    auraRadius,
                ),
                auraRadius,
                center,
            )
            if (clock < .36f) {
                repeat(3) { index ->
                    val ring = motionPhase(clock, index * .055f, .28f + index * .04f)
                    if (ring in 0f..1f) {
                        drawCircle(
                            accent.copy(alpha = (1f - ring) * .34f),
                            radius * (.70f + ring * .52f),
                            center,
                            style = Stroke(size.minDimension * (.006f + .004f * ring)),
                        )
                    }
                }
            }
            rotate(-24f + release * 28f + charge * 14f) {
                drawCircle(
                    Brush.linearGradient(
                        listOf(ObsidianCenter, ObsidianEdge),
                        Offset(size.width * .18f, size.height * .08f),
                        Offset(size.width * .84f, size.height * .95f),
                    ),
                    radius,
                    center,
                )
                drawCircle(accent.copy(alpha = .58f), radius, center, style = Stroke(size.minDimension * .018f))
                drawCircle(
                    if (completed) CompletionGold.copy(alpha = .92f) else accent.copy(alpha = .46f),
                    radius * .82f,
                    center,
                    style = Stroke(size.minDimension * .009f),
                )
                repeat(4) { index ->
                    val start = index * 90f + 20f + clock * 34f
                    drawArc(
                        accent.copy(alpha = (.30f + .48f * release)),
                        start,
                        36f,
                        false,
                        topLeft = Offset(center.x - radius * .93f, center.y - radius * .93f),
                        size = Size(radius * 1.86f, radius * 1.86f),
                        style = Stroke(size.minDimension * .012f, cap = StrokeCap.Round),
                    )
                }
            }
            val particleCount = if (completed) 10 else 6
            repeat(particleCount) { index ->
                val angle = (index.toFloat() / particleCount - .25f) * 2f * PI.toFloat() + clock * .7f
                val orbit = radius * (1.02f + .12f * sin(index * 1.7f))
                val point = center + Offset(cos(angle), sin(angle)) * orbit
                val particle = size.minDimension * if (index % 3 == 0) .014f else .008f
                drawCircle(
                    (if (completed && index % 2 == 0) CompletionGold else accent).copy(alpha = .32f + .68f * release),
                    particle,
                    point,
                )
            }
            when (kind) {
                TreeChallenge.DIAGNOSTIC -> drawSelfAwarenessSigil(accent, release)
                TreeChallenge.FEYNMAN -> drawFeynmanSigil(accent, release)
            }
            if (completed) {
                drawCompletionCrown()
            }
        }
    }
}

private fun DrawScope.drawSelfAwarenessSigil(accent: Color, reveal: Float) {
    val radius = size.minDimension * .22f
    val stroke = size.minDimension * .025f
    val prism = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * .86f, center.y)
        lineTo(center.x - radius * .78f, center.y + radius * .72f)
        close()
    }
    drawPath(prism, accent.copy(alpha = reveal), style = Stroke(stroke, cap = StrokeCap.Round))

    val eyeLeft = center.x - radius * .43f
    val eyeRight = center.x + radius * .43f
    val eye = Path().apply {
        moveTo(eyeLeft, center.y)
        quadraticTo(center.x, center.y - radius * .42f, eyeRight, center.y)
        quadraticTo(center.x, center.y + radius * .42f, eyeLeft, center.y)
    }
    drawPath(eye, accent.copy(alpha = reveal), style = Stroke(stroke * .72f, cap = StrokeCap.Round))
    drawCircle(accent.copy(alpha = reveal), radius * .08f, center)

    // 裂纹穿过观察窗口，表示看似完整、实际存在盲点的自我认知。
    val fracture = Path().apply {
        moveTo(center.x + radius * .50f, center.y - radius * .13f)
        lineTo(center.x + radius * .08f, center.y)
        lineTo(center.x + radius * .20f, center.y + radius * .17f)
        lineTo(center.x - radius * .18f, center.y + radius * .55f)
    }
    drawPath(fracture, accent.copy(alpha = reveal), style = Stroke(stroke * .66f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFeynmanSigil(accent: Color, reveal: Float) {
    val unit = size.minDimension
    val stroke = unit * .024f
    val large = Size(unit * .28f, unit * .19f)
    val small = Size(unit * .21f, unit * .15f)
    val largeTop = Offset(center.x - unit * .20f, center.y - unit * .16f)
    val smallTop = Offset(center.x - unit * .01f, center.y + unit * .01f)
    drawRoundRect(
        accent.copy(alpha = reveal),
        largeTop,
        large,
        CornerRadius(unit * .065f),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawRoundRect(
        accent.copy(alpha = reveal),
        smallTop,
        small,
        CornerRadius(unit * .055f),
        style = Stroke(stroke * .82f, cap = StrokeCap.Round),
    )
    val largeTail = Path().apply {
        moveTo(largeTop.x + large.width * .18f, largeTop.y + large.height)
        lineTo(largeTop.x + large.width * .31f, largeTop.y + large.height + unit * .055f)
        lineTo(largeTop.x + large.width * .35f, largeTop.y + large.height)
    }
    val smallTail = Path().apply {
        moveTo(smallTop.x + small.width * .62f, smallTop.y + small.height)
        lineTo(smallTop.x + small.width * .75f, smallTop.y + small.height + unit * .042f)
        lineTo(smallTop.x + small.width * .79f, smallTop.y + small.height)
    }
    drawPath(largeTail, accent.copy(alpha = reveal), style = Stroke(stroke * .72f, cap = StrokeCap.Round))
    drawPath(smallTail, accent.copy(alpha = reveal), style = Stroke(stroke * .66f, cap = StrokeCap.Round))

    // 回环箭头代表讲一遍、听反馈、再改述的费曼循环。
    val loop = Path().apply {
        moveTo(center.x - unit * .11f, center.y + unit * .21f)
        cubicTo(
            center.x - unit * .25f,
            center.y + unit * .29f,
            center.x + unit * .22f,
            center.y + unit * .31f,
            center.x + unit * .18f,
            center.y + unit * .16f,
        )
    }
    drawPath(loop, accent.copy(alpha = reveal), style = Stroke(stroke * .72f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(center.x + unit * .18f, center.y + unit * .16f)
        lineTo(center.x + unit * .235f, center.y + unit * .19f)
        lineTo(center.x + unit * .145f, center.y + unit * .22f)
    }, accent.copy(alpha = reveal), style = Stroke(stroke * .72f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCompletionCrown() {
    val unit = size.minDimension
    val crown = Path().apply {
        moveTo(center.x - unit * .18f, center.y - unit * .36f)
        lineTo(center.x - unit * .10f, center.y - unit * .43f)
        lineTo(center.x, center.y - unit * .36f)
        lineTo(center.x + unit * .10f, center.y - unit * .43f)
        lineTo(center.x + unit * .18f, center.y - unit * .36f)
    }
    drawPath(crown, CompletionGold, style = Stroke(unit * .018f, cap = StrokeCap.Round))
    drawCircle(CompletionGold, unit * .018f, Offset(center.x, center.y - unit * .43f))
}
