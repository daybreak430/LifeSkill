package com.lifeskill.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lifeskill.app.R
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import kotlin.random.Random

@Composable
internal fun Modifier.directionCardShadows(): Modifier {
    val color = studyColors.cardShadow
    return dropShadow(PanelShape, Shadow(9.dp, color.copy(alpha = .17f), offset = DpOffset(0.dp, 4.dp)))
        .dropShadow(PanelShape, Shadow(2.dp, color.copy(alpha = .19f), offset = DpOffset(0.dp, 1.dp)))
}

@Composable
internal fun Modifier.directionCardPaper(): Modifier {
    val dark = Panel.luminance() < .3f
    val dotColor = if (dark) Color(0xFF8DB2CB) else Color(0xFF537F98)
    val highlight = Color.White.copy(alpha = if (dark) .14f else .92f)
    return drawWithCache {
        val topLight = Brush.verticalGradient(listOf(highlight, Color.Transparent), endY = 8.dp.toPx())
        val insetSize = Size((size.width - 1f).coerceAtLeast(0f), (size.height - 1f).coerceAtLeast(0f))
        val corners = CornerRadius((PanelShape.topStart.toPx(size, this) - .5f).coerceAtLeast(0f))
        val grain = if (dark) lightGrain else darkGrain
        val step = 7.dp.toPx()
        val dotRadius = .52.dp.toPx()
        val dotStart = size.width * .63f
        val dotWidth = (size.width - dotStart).coerceAtLeast(1f)
        val dotHeight = (size.height * .73f).coerceAtLeast(1f)
        onDrawBehind {
            drawRect(grain)
            // 点阵只在右上角显现，逐渐淡出，标题和进度所在区域保持清晰。
            var top = step
            while (top < dotHeight) {
                var left = size.width - step
                while (left > dotStart) {
                    val opacity = (left - dotStart) / dotWidth * (1f - top / dotHeight)
                    drawCircle(dotColor.copy(alpha = .42f * opacity), dotRadius, Offset(left, top))
                    left -= step
                }
                top += step
            }
            // 仅顶部有一像素内高光，圆角处随透明度逐渐收掉。
            drawRoundRect(
                brush = topLight, topLeft = Offset(.5f, .5f), size = insetSize,
                cornerRadius = corners, style = Stroke(1f),
            )
        }
    }
}

private val darkGrain by lazy { paperGrain(0x0016222A) }
private val lightGrain by lazy { paperGrain(0x00ECF2F7) }

private fun paperGrain(rgb: Int): ShaderBrush {
    // 固定种子的像素纹理只生成一次，跨卡片复用，透明度最高约 5%。
    val side = 128
    val random = Random(27)
    val pixels = IntArray(side * side) { (random.nextInt(14) shl 24) or rgb }
    val tile = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

@Composable
internal fun DirectionCardIcon(entry: Entry, tint: Color) {
    val tile = when {
        entry.directionColor != null -> tint.copy(alpha = .15f)
        (entry.id.hashCode() and 1) == 0 -> GoldGround
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Box(Modifier.size(30.dp).background(tile, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(entry.displayIcon.directionIconResource), null, Modifier.size(24.dp),
            tint = if (entry.directionColor == null) Gold else tint,
        )
    }
}

private val EntryIcon.directionIconResource: Int
    get() = when (this) {
        EntryIcon.DIAMOND -> R.drawable.lucide_diamond
        EntryIcon.TREE -> R.drawable.lucide_git_fork
        EntryIcon.WORK -> R.drawable.lucide_briefcase_business
        EntryIcon.SIDE -> R.drawable.lucide_trending_up
        EntryIcon.HOBBY -> R.drawable.lucide_star
        EntryIcon.PRACTICAL -> R.drawable.lucide_square_check
        EntryIcon.EXPLORE -> R.drawable.lucide_compass
        EntryIcon.TARGET -> R.drawable.lucide_target
        EntryIcon.BOOK -> R.drawable.lucide_book_open
        EntryIcon.CODE -> R.drawable.lucide_code
        EntryIcon.CHART -> R.drawable.lucide_chart_no_axes_column
        EntryIcon.SPEECH -> R.drawable.lucide_message_circle
        EntryIcon.MUSIC -> R.drawable.lucide_headphones
        EntryIcon.CAMERA -> R.drawable.lucide_camera
        EntryIcon.LEAF -> R.drawable.lucide_leaf
        EntryIcon.TOOLS -> R.drawable.lucide_wrench
    }
