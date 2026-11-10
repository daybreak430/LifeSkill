package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.lifeskill.app.model.EntryIcon

internal val EntryIcon.glyph get() = Glyph.valueOf(name)

@Composable
internal fun SkillMedallion(icon: EntryIcon, lit: Boolean, diameter: Dp) {
    val tint = if (lit) Gold else Steel
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
            drawCircle(tint.copy(alpha = if (lit) .9f else .58f), radius * .94f, style = Stroke(radius * .055f))
            drawCircle(tint.copy(alpha = .25f), radius * .77f, style = Stroke(radius * .025f))
        }
        Mark(icon.glyph, tint, Modifier.size(diameter * .45f))
    }
}
