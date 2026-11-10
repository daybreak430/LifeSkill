package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class AiColors(
    val backdrop: List<Color>,
    val outgoing: List<Color>,
    val panel: Color,
    val chip: Color,
    val accent: Color,
    val outline: Color,
    val action: List<Color>,
    val actionAccent: Color,
    val actionRim: Color,
)

internal val lightAiColors = AiColors(
    backdrop = listOf(Color(0xFFE5F1F6), Color(0xFFF0F7FA), Color(0xFFEAF5F9)),
    outgoing = listOf(Color(0xFF407E8F), Color(0xFF19617A)),
    panel = Color(0xFFFCFDFE), chip = Color(0xFFDDEFF5), accent = Color(0xFF225A70),
    outline = Color(0xFFCBDDE7),
    action = listOf(Color(0xFFF0E6FC), Color(0xFFE9EEFC), Color(0xFFCDE3FA)),
    actionAccent = Color(0xFF6252A8),
    actionRim = Color(0xDFFFFFFF),
)

internal val darkAiColors = AiColors(
    backdrop = listOf(Color(0xFF142633), Color(0xFF141D28), Color(0xFF172732)),
    outgoing = listOf(Color(0xFF327488), Color(0xFF21536D)),
    panel = Color(0xFF213442), chip = Color(0xFF244756), accent = Color(0xFFB5E5F2),
    outline = Color(0xFF3B5667),
    action = listOf(Color(0xFF333048), Color(0xFF2A3E57), Color(0xFF29465D)),
    actionAccent = Color(0xFFD0C1F2),
    actionRim = Color(0xFF4B4F66),
)

internal val LocalAiColors = staticCompositionLocalOf { lightAiColors }
internal val aiColors: AiColors @Composable @ReadOnlyComposable get() = LocalAiColors.current

@Composable
internal fun AiAction(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    featured: Boolean = false, enabled: Boolean = true,
) {
    val colors = aiColors
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(if (featured) 18.dp else 12.dp),
        color = colors.panel, shadowElevation = if (featured) 2.dp else 1.dp,
        border = if (featured) BorderStroke(1.dp, colors.actionRim) else null,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Row(
            Modifier.background(Brush.linearGradient(colors.action))
                .heightIn(min = if (featured) 64.dp else 48.dp)
                .padding(horizontal = if (featured) 20.dp else 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(if (featured) 10.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiCrystal(if (featured) 42.dp else 25.dp, forAction = true)
            Text(
                label, Modifier.weight(1f, fill = featured),
                color = if (enabled) Ink else Muted,
                style = if (featured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            )
            if (featured) Mark(Glyph.NEXT, colors.actionAccent, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun AiCrystal(diameter: Dp, forAction: Boolean = false) {
    val rim = if (forAction) Color(0xFF4A3B71) else Color(0xFF276577)
    val reflection = if (forAction) Color(0xFFC0AFE8) else Color(0xFFA5DFE8)
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension * .43f
            // 用明暗渐变和两段高光画出水晶的内外折射，同一图形可用于小尺寸头像和入口。
            drawCircle(Brush.radialGradient(listOf(rim.copy(alpha = .23f), Color.Transparent), radius = radius * 1.15f),
                radius = radius * 1.15f)
            drawCircle(Brush.sweepGradient(listOf(rim, reflection, Color(0xFFEAFDFF), rim, reflection, rim)), radius = radius)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFE5FCFF), Color(0xFF77C8DB), reflection, rim),
                    center = center + Offset(radius * .28f, radius * .35f), radius = radius * 1.45f,
                ), radius = radius * .82f,
            )
            val inset = radius * .83f
            val topLeft = center - Offset(inset, inset)
            val arc = Size(inset * 2, inset * 2)
            val highlight = Stroke(width = radius * .12f, cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = .88f), 206f, 50f, false, topLeft, arc, style = highlight)
            drawArc(Color(0xFFDBFAFF).copy(alpha = .78f), 28f, 62f, false, topLeft, arc, style = highlight)
        }
        if (forAction) Mark(Glyph.SPARK, Color.White, Modifier.size(diameter * .46f))
    }
}
