package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Ground: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
internal val Panel: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
internal val Raised: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
internal val Line: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
internal val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
internal val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Steel: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
internal val Gold: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
internal val GoldGround: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFF6C477),
    onPrimary = Color(0xFF2C1C0A),
    primaryContainer = Color(0xFF3A2C1D),
    onPrimaryContainer = Color(0xFFF6C477),
    secondary = Color(0xFF96B1D0),
    onSecondary = Color(0xFF10141B),
    secondaryContainer = Color(0xFF222B39),
    onSecondaryContainer = Color(0xFFF1F3F5),
    background = Color(0xFF10141B),
    onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF191F29),
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF222B39),
    onSurfaceVariant = Color(0xFFAEBAC9),
    outline = Color(0xFF8292A6),
    outlineVariant = Color(0xFF344154),
    surfaceContainer = Color(0xFF191F29),
    surfaceContainerHigh = Color(0xFF222B39),
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF795620),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E5CF),
    onPrimaryContainer = Color(0xFF604418),
    secondary = Color(0xFF675F52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7DA),
    onSecondaryContainer = Color(0xFF433B2E),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF252522),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF252522),
    surfaceVariant = Color(0xFFF0ECE3),
    onSurfaceVariant = Color(0xFF5D5A52),
    outline = Color(0xFF71675D),
    outlineVariant = Color(0xFFD9D1C2),
    surfaceContainer = Color(0xFFF7F1E8),
    surfaceContainerHigh = Color(0xFFF0EAE1),
    surfaceContainerHighest = Color(0xFFE9E1D6),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
internal fun LifeSkillTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkPalette else LightPalette, typography = Type, content = content)
}

internal enum class Glyph {
    TREE, SPARK, ADD, BACK, NEXT, SETTINGS, EDIT, MORE, CHECK, DIAMOND, TARGET, SEND, LOCK,
    WORK, SIDE, HOBBY, PRACTICAL, EXPLORE, CLOSE, BOOK, CODE, CHART, SPEECH, MUSIC, CAMERA, LEAF, TOOLS, RESTART,
}

@Composable
internal fun Mark(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val unit = size.minDimension / 24f
        fun at(x: Number, y: Number) = Offset(x.toFloat() * unit, y.toFloat() * unit)
        fun line(x: Number, y: Number, x2: Number, y2: Number) =
            drawLine(color, at(x, y), at(x2, y2), strokeWidth = 1.65f * unit, cap = StrokeCap.Round)
        fun path(vararg coords: Number, close: Boolean = false, fill: Boolean = false) {
            val trace = Path().apply {
                moveTo(coords[0].toFloat() * unit, coords[1].toFloat() * unit)
                for (index in 2 until coords.size step 2) {
                    lineTo(coords[index].toFloat() * unit, coords[index + 1].toFloat() * unit)
                }
                if (close) close()
            }
            if (fill) drawPath(trace, color) else drawPath(trace, color, style = Stroke(1.65f * unit, cap = StrokeCap.Round))
        }
        fun circle(x: Number, y: Number, radius: Float, fill: Boolean = false) {
            if (fill) drawCircle(color, radius * unit, at(x, y))
            else drawCircle(color, radius * unit, at(x, y), style = Stroke(1.65f * unit))
        }
        when (glyph) {
            Glyph.TREE -> {
                path(6, 6, 12, 11, 18, 6)
                line(12, 11, 12, 17)
                path(6, 2, 9, 5, 6, 8, 3, 5, close = true, fill = true)
                path(18, 2, 21, 5, 18, 8, 15, 5, close = true, fill = true)
                path(12, 15, 16, 19, 12, 23, 8, 19, close = true, fill = true)
            }
            Glyph.SPARK -> {
                path(10, 3, 12.4, 9.6, 19, 12, 12.4, 14.4, 10, 21, 7.6, 14.4, 1, 12, 7.6, 9.6, close = true)
                line(19, 3, 19, 8)
                line(16.5, 5.5, 21.5, 5.5)
            }
            Glyph.ADD -> { line(5, 12, 19, 12); line(12, 5, 12, 19) }
            Glyph.BACK -> { path(11, 5, 4, 12, 11, 19); line(4, 12, 20, 12) }
            Glyph.NEXT -> path(9, 5, 16, 12, 9, 19)
            Glyph.SETTINGS -> {
                circle(12, 12, 7f)
                circle(12, 12, 2.4f)
                line(12, 2, 12, 5); line(12, 19, 12, 22); line(2, 12, 5, 12); line(19, 12, 22, 12)
                line(5, 5, 7, 7); line(17, 17, 19, 19); line(5, 19, 7, 17); line(17, 7, 19, 5)
            }
            Glyph.EDIT -> path(4, 20, 5, 15, 16, 4, 20, 8, 9, 19, close = true)
            Glyph.MORE -> { circle(12, 5, 1.6f, true); circle(12, 12, 1.6f, true); circle(12, 19, 1.6f, true) }
            Glyph.CHECK -> path(4, 12, 9, 17, 20, 6)
            Glyph.DIAMOND -> path(12, 3, 21, 12, 12, 21, 3, 12, close = true)
            Glyph.TARGET -> { circle(12, 12, 9f); circle(12, 12, 5f); circle(12, 12, 1.5f, true) }
            Glyph.SEND -> { path(3, 4, 21, 12, 3, 20, 7, 12, close = true); line(7, 12, 21, 12) }
            Glyph.LOCK -> {
                path(5, 11, 19, 11, 19, 21, 5, 21, close = true)
                path(8, 11, 8, 7, 10, 4, 14, 4, 16, 7, 16, 11)
                line(12, 15, 12, 17)
            }
            Glyph.WORK -> {
                drawRect(color, at(3, 8), Size(18 * unit, 12 * unit), style = Stroke(1.65f * unit))
                path(8, 8, 8, 4, 16, 4, 16, 8)
                line(3, 13, 21, 13); line(12, 12, 12, 15)
            }
            Glyph.SIDE -> path(3, 18, 9, 12, 13, 15, 21, 6, 15, 6, 21, 6, 21, 12)
            Glyph.HOBBY -> path(12, 3, 14.7, 9, 21, 9.5, 16.2, 14, 17.6, 21, 12, 17.4, 6.4, 21, 7.8, 14, 3, 9.5, 9.3, 9, close = true)
            Glyph.PRACTICAL -> { path(4, 5, 20, 5, 20, 19, 4, 19, close = true); path(8, 12, 11, 15, 16, 9) }
            Glyph.EXPLORE -> { circle(12, 12, 9f); path(16.5, 7.5, 14.5, 14.5, 7.5, 16.5, 9.5, 9.5, close = true) }
            Glyph.CLOSE -> {
                line(6, 6, 18, 18)
                line(18, 6, 6, 18)
            }
            Glyph.RESTART -> {
                drawArc(
                    color, -138f, 306f, false, at(4, 4), Size(16 * unit, 16 * unit),
                    style = Stroke(1.65f * unit, cap = StrokeCap.Round),
                )
                path(3, 5, 3, 11, 9, 11)
            }
            Glyph.BOOK -> {
                path(12, 7, 8, 4, 3, 4, 3, 18, 8, 18, 12, 21, 16, 18, 21, 18, 21, 4, 16, 4, close = true)
                line(12, 7, 12, 21)
            }
            Glyph.CODE -> {
                path(7, 7, 2, 12, 7, 17)
                path(17, 7, 22, 12, 17, 17)
                line(14, 4, 10, 20)
            }
            Glyph.CHART -> {
                path(3, 4, 3, 21, 21, 21)
                line(8, 17, 8, 12)
                line(13, 17, 13, 8)
                line(18, 17, 18, 5)
            }
            Glyph.SPEECH -> path(4, 4, 20, 4, 20, 16, 11, 16, 6, 21, 6, 16, 4, 16, close = true)
            Glyph.MUSIC -> {
                path(9, 17, 9, 6, 20, 3, 20, 14)
                circle(6, 18, 3f)
                circle(17, 15, 3f)
            }
            Glyph.CAMERA -> {
                path(3, 7, 8, 7, 10, 4, 15, 4, 17, 7, 21, 7, 21, 20, 3, 20, close = true)
                circle(12, 13, 4f)
            }
            Glyph.LEAF -> {
                path(4, 17, 5, 10, 11, 5, 21, 3, 19, 13, 13, 19, 7, 19)
                line(3, 22, 15, 10)
            }
            Glyph.TOOLS -> {
                path(21, 3, 15, 4, 13, 8, 15, 11, 20, 10, 22, 6)
                path(13, 8, 3, 18, 3, 21, 6, 21, 15, 11)
            }
        }
    }
}
