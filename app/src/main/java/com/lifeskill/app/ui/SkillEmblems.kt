package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
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
internal fun SkillMedallion(icon: EntryIcon, state: SkillState, diameter: Dp) {
    val lit = state == SkillState.LIT
    val locked = state == SkillState.LOCKED
    val tint = state.tint()
    val centerColor = if (lit) GoldGround else Raised
    val edgeColor = if (lit) lerp(GoldGround, Ground, .44f) else Panel
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(centerColor, edgeColor),
                    center = center, radius = radius,
                ),
                radius = radius,
            )
            val ringAlpha = if (locked) .30f else .85f
            drawCircle(tint.copy(alpha = ringAlpha), radius * .94f, style = Stroke(radius * .055f))
            drawCircle(tint.copy(alpha = .25f), radius * .77f, style = Stroke(radius * .025f))
        }
        Mark(icon.glyph, tint.copy(alpha = if (locked) .62f else 1f), Modifier.size(diameter * .45f))
        if (locked) {
            Surface(Modifier.align(Alignment.BottomEnd).size(diameter * .34f), shape = CircleShape, color = Panel) {
                Box(contentAlignment = Alignment.Center) {
                    Mark(Glyph.LOCK, tint, Modifier.size(diameter * .22f))
                }
            }
        }
    }
}
