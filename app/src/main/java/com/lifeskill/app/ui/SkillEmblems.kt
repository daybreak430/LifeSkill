package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
internal fun SkillMedallion(icon: EntryIcon, state: SkillState, diameter: Dp, focused: Boolean = false) {
    val lit = state == SkillState.LIT
    val locked = state == SkillState.LOCKED
    val tint = state.tint()
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
                    drawCircle(colors.focusBand, radius = radius)
                    drawCircle(surface, radius = radius * .89f)
                    drawCircle(fill, radius = radius * .82f)
                }
                else -> drawCircle(fill, radius = radius)
            }
        }
        Mark(icon.glyph, if (lit) colors.earnedInk else tint, Modifier.size(diameter * .43f))
        if (lit || locked) {
            Surface(Modifier.align(Alignment.TopEnd).size(diameter * .30f), shape = CircleShape, color = surface) {
                Box(contentAlignment = Alignment.Center) {
                    Mark(if (lit) Glyph.CHECK else Glyph.LOCK, tint, Modifier.size(diameter * .19f))
                }
            }
        }
    }
}
