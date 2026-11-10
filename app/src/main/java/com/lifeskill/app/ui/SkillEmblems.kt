package com.lifeskill.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.SkillState

internal val EntryIcon.glyph get() = Glyph.valueOf(name)

@Composable
internal fun SkillState.tint() = when (this) {
    SkillState.LOCKED -> Muted
    SkillState.AVAILABLE -> Steel
    SkillState.LIT -> Gold
}

@Composable
internal fun SkillMedallion(
    icon: EntryIcon, state: SkillState, diameter: Dp, focused: Boolean = false, glyphSize: Dp = diameter * .43f,
    showBadge: Boolean = true,
) {
    val lit = state == SkillState.LIT
    val locked = state == SkillState.LOCKED
    val tint = if (focused) Gold else state.tint()
    val colors = studyColors
    val surface = Panel
    val fill = when {
        focused -> GoldGround
        locked -> Raised
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            when {
                lit -> drawCircle(Brush.linearGradient(colors.earned), radius = radius)
                focused -> {
                    drawCircle(lerp(colors.focusBand, colors.route, .46f), radius = radius)
                    drawCircle(surface, radius = radius * .89f)
                    drawCircle(fill, radius = radius * .82f)
                }
                else -> drawCircle(fill, radius = radius)
            }
        }
        Mark(icon.glyph, if (lit) colors.earnedInk else tint, Modifier.size(glyphSize))
        if (showBadge && (lit || locked)) {
            Surface(Modifier.align(Alignment.TopEnd).size(diameter * .30f), shape = CircleShape, color = surface) {
                Box(contentAlignment = Alignment.Center) {
                    Mark(if (lit) Glyph.CHECK else Glyph.LOCK, tint, Modifier.size(diameter * .19f))
                }
            }
        }
    }
}

@Composable
internal fun UnlockingMedallion(
    icon: EntryIcon, diameter: Dp, focused: Boolean, source: Boolean, progress: () -> Float,
) {
    val glow = Gold
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val from = if (source) .04f else .76f
            val pulse = motionPhase(progress(), from, if (source) .65f else 1f)
            if (pulse > 0f && pulse < 1f) {
                drawCircle(glow.copy(alpha = .38f * (1f - pulse)),
                    size.minDimension * (.45f + .28f * pulse), style = Stroke(1.5.dp.toPx()))
            }
        }
        SkillMedallion(icon, if (source) SkillState.AVAILABLE else SkillState.LOCKED,
            diameter, focused = source, glyphSize = diameter * .49f, showBadge = false)
        Box(Modifier.graphicsLayer {
            val reveal = if (source) motionPhase(progress(), 0f, .43f) else motionPhase(progress(), .76f, 1f)
            alpha = FastOutSlowInEasing.transform(reveal)
            val pop = motionPhase(reveal, 0f, .62f) - motionPhase(reveal, .62f, 1f)
            scaleX = 1f + .10f * pop
            scaleY = scaleX
        }) {
            SkillMedallion(icon, if (source) SkillState.LIT else SkillState.AVAILABLE,
                diameter, focused && !source, glyphSize = diameter * .49f)
        }
        if (!source) {
            UnlockingLock(
                Modifier.align(Alignment.TopEnd).size(diameter * .30f),
                diameter * .19f, progress,
            )
        }
    }
}

@Composable
private fun UnlockingLock(modifier: Modifier, size: Dp, progress: () -> Float) {
    val tint = Muted
    Surface(
        modifier.graphicsLayer {
            val leave = motionPhase(progress(), MAP_LOCK_RELEASE_AT, 1f)
            alpha = 1f - leave
            translationY = -8.dp.toPx() * leave
        },
        shape = CircleShape, color = Panel,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val unit = this.size.minDimension / 24f
                val opening = motionPhase(progress(), .76f, .91f)
                val outline = Stroke(1.65f * unit, cap = StrokeCap.Round)
                rotate(40f * opening, Offset(16f * unit, 11f * unit)) {
                    drawPath(Path().apply {
                        moveTo(8f * unit, 11f * unit)
                        lineTo(8f * unit, 7f * unit)
                        quadraticTo(12f * unit, 1f * unit, 16f * unit, 7f * unit)
                        lineTo(16f * unit, 11f * unit)
                    }, tint, style = outline)
                }
                drawRoundRect(tint, Offset(5f * unit, 11f * unit), Size(14f * unit, 10f * unit),
                    CornerRadius(1.4f * unit), style = outline)
                drawLine(tint, Offset(12f * unit, 15f * unit), Offset(12f * unit, 17f * unit),
                    outline.width, StrokeCap.Round)
            }
        }
    }
}
