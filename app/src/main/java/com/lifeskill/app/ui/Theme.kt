package com.lifeskill.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    primary = Color(0xFFA7DBC5),
    onPrimary = Color(0xFF102B22),
    primaryContainer = Color(0xFF23483D),
    onPrimaryContainer = Color(0xFFBCE8D6),
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
    primary = Color(0xFF0D503A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F0E5),
    onPrimaryContainer = Color(0xFF1B503E),
    secondary = Color(0xFF386763),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F1F1),
    onSecondaryContainer = Color(0xFF284D4A),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF162824),
    surface = Color(0xFFFCFDFD),
    onSurface = Color(0xFF162824),
    surfaceVariant = Color(0xFFEEF2F2),
    onSurfaceVariant = Color(0xFF4B605B),
    outline = Color(0xFF697D78),
    outlineVariant = Color(0xFFD4E0E1),
    surfaceContainer = Color(0xFFF0F5F5),
    surfaceContainerHigh = Color(0xFFE9F0F0),
    surfaceContainerHighest = Color(0xFFE0EAEA),
)

internal data class MapNodePalette(
    val lockedFill: Color,
    val lockedRim: Color,
    val lockedInk: Color,
    val lockedBadgeFill: Color,
    val availableFill: Color,
    val availableRim: Color,
)

internal data class StudyColors(
    val scene: BackdropSpec,
    val cardShadow: Color,
    val progressTrack: Color,
    val hero: List<Color>,
    val heroLayer: Color,
    val heroReflection: Color,
    val heroRefraction: Color,
    val heroGlow: Color,
    val heroEmblem: List<Color>,
    val map: List<Color>,
    val earned: List<Color>,
    val earnedInk: Color,
    val route: Color,
    val focusBand: Color,
    val iconViolet: Color,
    val iconBlue: Color,
    val iconGreen: Color,
    val iconOrange: Color,
    val mapNodePalette: MapNodePalette? = null,
    val heroRim: Color = Color.Transparent,
    val heroLayerTopInset: Dp = 11.dp,
)

private val LightStudyColors = StudyColors(
    // 方案 A「原味」：底色是中性灰，层次全靠四团雾；色值取自参考图采样，饱和度不到 2%
    scene = mistyScene(
        base = listOf(Color(0xFFDFDEDD), Color(0xFFDAD9D8), Color(0xFFD5D4D3)),
        glows = listOf(
            Color(0xE6FCFBFA),
            Color(0xB3DAD9DD),
            Color(0xCCEDE8E0),
            Color(0x8CE3DFE0),
        ),
    ),
    cardShadow = Color(0xFF869BAF),
    progressTrack = Color(0xFFE4E8F1),
    hero = listOf(Color(0xFFDDF3E6), Color(0xFFDCEFF1), Color(0xFFD2E1F7)),
    heroLayer = Color(0xFFDDD2F2),
    heroReflection = Color(0xA6FFFFFF),
    heroRefraction = Color(0x29618FE0),
    heroRim = Color(0x6689B9AF),
    heroLayerTopInset = 8.dp,
    heroGlow = Color(0xBBA1C9EB),
    heroEmblem = listOf(Color(0xFF639AAA), Color(0xFF36758A)),
    map = listOf(Color(0xFFEAF4F5), Color(0xFFF8FBFD)),
    earned = listOf(Color(0xFF599E7C), Color(0xFF327756)),
    earnedInk = Color.White,
    route = Color(0xFF4C9675),
    focusBand = Color(0xFFB9E2D0),
    iconViolet = Color(0xFF8C7DB2), iconBlue = Color(0xFF6C91A8),
    iconGreen = Color(0xFF6E9583), iconOrange = Color(0xFFBB886A),
    mapNodePalette = MapNodePalette(
        lockedFill = Color(0xFFECECEC),
        lockedRim = Color(0xFFBDBDBD),
        lockedInk = Color(0xFF696969),
        lockedBadgeFill = Color(0xFFF6F6F6),
        availableFill = Color(0xFFCFE7E1),
        availableRim = Color(0xFF7EAA9E),
    ),
)

private val DarkStudyColors = StudyColors(
    // 夜间沿用蓝黑基调，雾换成极低对比的冷灰，结构不变
    scene = mistyScene(
        base = listOf(Color(0xFF10141B), Color(0xFF161E2C), Color(0xFF0C1016)),
        glows = listOf(
            Color(0x4D6E6A7A),
            Color(0x45505A68),
            Color(0x3D3A4048),
            Color(0x29070A10),
        ),
    ),
    cardShadow = Color.Black,
    progressTrack = DarkPalette.outlineVariant,
    hero = listOf(Color(0xFF203A35), Color(0xFF22354D)),
    heroLayer = Color(0xFF383B57),
    heroReflection = Color(0x4FBDF2FA),
    heroRefraction = Color(0x308F97FF),
    heroGlow = Color(0xAA4C749A),
    heroEmblem = listOf(Color(0xFF5E9BAE), Color(0xFF32677E)),
    map = listOf(DarkPalette.surface, DarkPalette.background),
    earned = listOf(Color(0xFF548F78), Color(0xFF2D6956)),
    earnedInk = Color.White,
    route = DarkPalette.primary,
    focusBand = Color(0xFF579783),
    iconViolet = Color(0xFFC3B1F6), iconBlue = Color(0xFF98C2F0),
    iconGreen = Color(0xFF8ED8B5), iconOrange = Color(0xFFF5B483),
    mapNodePalette = MapNodePalette(
        lockedFill = Color(0xFF202936),
        lockedRim = Color(0xFF3B4758),
        lockedInk = Color(0xFF8490A0),
        lockedBadgeFill = Color(0xFF293443),
        availableFill = Color(0xFF284A40),
        availableRim = Color(0xFF77BDA2),
    ),
)

private val LocalStudyColors = staticCompositionLocalOf { LightStudyColors }
internal val studyColors: StudyColors @Composable @ReadOnlyComposable get() = LocalStudyColors.current

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
    CompositionLocalProvider(
        LocalStudyColors provides if (dark) DarkStudyColors else LightStudyColors,
        LocalAiColors provides if (dark) darkAiColors else lightAiColors,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkPalette else LightPalette, typography = Type, content = content)
    }
}

internal enum class Glyph {
    TREE, SPARK, ADD, BACK, NEXT, SETTINGS, EDIT, MORE, CHECK, DIAMOND, TARGET, SEND, LOCK,
    WORK, SIDE, HOBBY, PRACTICAL, EXPLORE, GLOBE, CLOSE, BOOK, CODE, CHART, SPEECH, MUSIC, CAMERA, LEAF, TOOLS, RESTART, PERSON,
    PHOTO, ATTACHMENT, THINK, EXPAND, MOVE, UP, DOWN,
}

// 功能图标统一使用随应用打包的 Lucide 矢量资源，避免手绘近似图形产生风格差异。
@Composable
internal fun Mark(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    val resource = when (glyph) {
        Glyph.TREE -> com.lifeskill.app.R.drawable.lucide_git_fork
        Glyph.SPARK -> com.lifeskill.app.R.drawable.lucide_sparkles
        Glyph.ADD -> com.lifeskill.app.R.drawable.lucide_plus
        Glyph.BACK -> com.lifeskill.app.R.drawable.lucide_arrow_left
        Glyph.NEXT -> com.lifeskill.app.R.drawable.lucide_chevron_right
        Glyph.SETTINGS -> com.lifeskill.app.R.drawable.lucide_settings
        Glyph.EDIT -> com.lifeskill.app.R.drawable.lucide_pencil
        Glyph.MORE -> com.lifeskill.app.R.drawable.lucide_ellipsis_vertical
        Glyph.CHECK -> com.lifeskill.app.R.drawable.lucide_check
        Glyph.DIAMOND -> com.lifeskill.app.R.drawable.lucide_diamond
        Glyph.TARGET -> com.lifeskill.app.R.drawable.lucide_target
        Glyph.SEND -> com.lifeskill.app.R.drawable.lucide_send
        Glyph.LOCK -> com.lifeskill.app.R.drawable.lucide_lock
        Glyph.WORK -> com.lifeskill.app.R.drawable.lucide_briefcase_business
        Glyph.SIDE -> com.lifeskill.app.R.drawable.lucide_trending_up
        Glyph.HOBBY -> com.lifeskill.app.R.drawable.lucide_star
        Glyph.PRACTICAL -> com.lifeskill.app.R.drawable.lucide_square_check
        Glyph.EXPLORE -> com.lifeskill.app.R.drawable.lucide_compass
        Glyph.GLOBE -> com.lifeskill.app.R.drawable.lucide_globe
        Glyph.CLOSE -> com.lifeskill.app.R.drawable.lucide_x
        Glyph.BOOK -> com.lifeskill.app.R.drawable.lucide_book_open
        Glyph.CODE -> com.lifeskill.app.R.drawable.lucide_code
        Glyph.CHART -> com.lifeskill.app.R.drawable.lucide_chart_no_axes_column
        Glyph.SPEECH -> com.lifeskill.app.R.drawable.lucide_message_circle
        Glyph.MUSIC -> com.lifeskill.app.R.drawable.lucide_headphones
        Glyph.CAMERA -> com.lifeskill.app.R.drawable.lucide_camera
        Glyph.LEAF -> com.lifeskill.app.R.drawable.lucide_leaf
        Glyph.TOOLS -> com.lifeskill.app.R.drawable.lucide_wrench
        Glyph.RESTART -> com.lifeskill.app.R.drawable.lucide_rotate_ccw
        Glyph.PERSON -> com.lifeskill.app.R.drawable.lucide_user
        Glyph.PHOTO -> com.lifeskill.app.R.drawable.lucide_image
        Glyph.ATTACHMENT -> com.lifeskill.app.R.drawable.lucide_paperclip
        Glyph.THINK -> com.lifeskill.app.R.drawable.lucide_atom
        Glyph.EXPAND -> com.lifeskill.app.R.drawable.lucide_maximize_2
        Glyph.MOVE -> com.lifeskill.app.R.drawable.lucide_grip_vertical
        Glyph.UP -> com.lifeskill.app.R.drawable.lucide_arrow_up
        Glyph.DOWN -> com.lifeskill.app.R.drawable.lucide_arrow_down
    }
    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(resource), contentDescription = null,
        tint = color, modifier = modifier.size(22.dp))
}
