package com.lifeskill.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/** 一团柔光斑：位置用画布比例表示，半径按短边换算，保证不同屏幕比例下构图一致。 */
@Immutable
internal data class BackdropGlow(val x: Float, val y: Float, val radius: Float, val color: Color)

/**
 * 全屏背景：一层斜向中性底色 + 四团超柔光斑 + 一层细颗粒。
 *
 * 结构固定（3 底 + 4 雾），换主题只换色值不改几何 —— 这是"一套语言"的前提：
 * 页面之间切换时只有色温在变，光斑位置不跳。
 */
@Immutable
internal data class BackdropSpec(val base: List<Color>, val glows: List<BackdropGlow>)

/**
 * 四团雾的固定构图，取自参考图采样：左上最亮、右上偏冷、右下偏暖、左下压暗。
 * 半径都接近或超过短边，边缘必须落在画布外，否则会看到圆的边界。
 */
private val MistyGlowGeometry = listOf(
    Triple(.02f, -.10f, 1.45f),
    Triple(1.02f, .06f, 1.30f),
    Triple(.96f, 1.06f, 1.35f),
    Triple(.06f, 1.02f, 1.25f),
)

/** 用四团雾固定的几何拼出背景，调用处只写 3 个底色和 4 个雾色。 */
internal fun mistyScene(base: List<Color>, glows: List<Color>): BackdropSpec {
    require(base.size == 3 && glows.size == MistyGlowGeometry.size) { "背景必须由 3 个底色和 4 团雾组成" }
    return BackdropSpec(
        base = base,
        glows = MistyGlowGeometry.mapIndexed { index, (x, y, radius) ->
            BackdropGlow(x, y, radius, glows[index])
        },
    )
}

/**
 * 绘制背景。
 *
 * 光斑用同色透明收边而不是 [Color.Transparent]：后者等价于黑色透明，
 * 径向渐变收尾时会带出一圈脏灰。
 */
/**
 * 背景挂载点。
 *
 * 底色和四团雾先渲染进一张几十像素的小图，绘制时放大成整屏：
 * 每帧只剩一次纹理填充，而不是五次全屏渐变（实测滚动时 list 明显掉帧）。
 * 降采样看不出差异 —— 背景本来就是一整片柔光，没有需要保真的细节。
 */
@Composable
internal fun Modifier.mistyBackdrop(scene: BackdropSpec, grain: Shader?, grainAlpha: Float): Modifier {
    val layer = remember(scene) { renderBackdropLayer(scene) }
    val grainBrush = remember(grain) { grain?.let(::ShaderBrush) }
    return drawBehind {
        drawImage(
            layer,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low,
        )
        // 颗粒保持 1:1，放大就糊成噪斑了；一层正常混合，成本远低于 Overlay。
        if (grainBrush != null && grainAlpha > 0f) drawRect(grainBrush, alpha = grainAlpha)
    }
}

/** 72×156 的小图足够表达柔和渐变；scene 变化时重建（含过场动画的每一帧）也只有几万次插值。 */
private fun renderBackdropLayer(scene: BackdropSpec): ImageBitmap {
    val image = ImageBitmap(72, 156)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(72f, 156f),
    ) {
        drawMistyBackdrop(scene)
    }
    return image
}

private fun DrawScope.drawMistyBackdrop(scene: BackdropSpec) {
    // 底色走一点点斜向，比纯竖渐变更像自然光；色差只有十几个色阶，不会抢内容。
    drawRect(
        Brush.linearGradient(
            scene.base,
            start = Offset.Zero,
            end = Offset(size.width * .38f, size.height),
        ),
    )
    scene.glows.forEach { glow ->
        val radius = (size.minDimension * glow.radius).coerceAtLeast(1f)
        drawRect(
            Brush.radialGradient(
                listOf(glow.color, glow.color.copy(alpha = 0f)),
                center = Offset(size.width * glow.x, size.height * glow.y),
                radius = radius,
            ),
        )
    }
}

/**
 * 颜色数量固定（3 底 + 4 雾），逐项做颜色过渡；页面之间换色温时整片背景一起走，
 * 不会出现"底色先变、雾后到"的错位。
 */
@Composable
internal fun animatedBackdrop(target: BackdropSpec): BackdropSpec {
    val base = target.base.map { color ->
        animateColorAsState(color, motion(Motion.Base), label = "背景底色").value
    }
    val glows = target.glows.map { glow ->
        val color by animateColorAsState(glow.color, motion(Motion.Base), label = "背景光斑")
        glow.copy(color = color)
    }
    return BackdropSpec(base, glows)
}

/**
 * 128px 的重复噪点贴图。
 *
 * 只生成一次：逐像素随机在 96–159 的中灰附近抖动，透明度 14–23/255，
 * 铺满全屏后是"看得见的细颗粒"，放大也不会出现明显的图案重复。
 */
@Composable
internal fun rememberBackdropGrain(): Shader = remember {
    val edge = 128
    val random = Random(20261126)
    val pixels = IntArray(edge * edge) {
        val value = 96 + random.nextInt(64)
        val alpha = 18 + random.nextInt(12)
        (alpha shl 24) or (value shl 16) or (value shl 8) or value
    }
    ImageShader(
        Bitmap.createBitmap(pixels, edge, edge, Bitmap.Config.ARGB_8888).asImageBitmap(),
        TileMode.Repeated,
        TileMode.Repeated,
    )
}
