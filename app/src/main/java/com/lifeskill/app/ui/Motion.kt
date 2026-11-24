package com.lifeskill.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

/**
 * 全应用唯一的动效语言。
 *
 * 约定（新增动效必须复用，不要在各处自定时长与曲线）：
 * - 同一类变化只允许一种节奏：出现用减速曲线，消失比出现更快，位置变化用标准曲线。
 * - 时长按“信息量”分档，用户高频操作（按下、选中、开关）只用 [Instant] 与 [Quick]。
 * - 所有 tween/spring 都会自动遵循系统“移除动画 / 动画时长缩放”设置，
 *   系统把时长缩放设为 0 时这些动画会直接落到终态，不需要额外分支。
 */
internal object Motion {
    /** 按下、悬停、颜色微调：用户一碰就要有回应，不能有等待感。 */
    const val Instant = 90

    /** 小元件出现消失、图标与标签切换、错误提示。 */
    const val Quick = 150

    /** 页面转场、卡片换内容、区块展开：默认档。 */
    const val Base = 220

    /** 大块内容替换、抽屉与表单展开，需要交代因果的地方。 */
    const val Gentle = 320

    /** 一次性主场动画（庆祝、翻卡、证书开信封），只用于用户主动触发的大场面。 */
    const val Hero = 520

    /**
     * 标准曲线：起步就给出速度，收尾稳稳停住。用于绝大多数位移与换内容。
     * 相当于苹果常用的“临界阻尼弹簧”手感，只是用时长曲线表达，开销更低。
     */
    val Standard = CubicBezierEasing(.2f, 0f, 0f, 1f)

    /** 减速曲线：元素进入画面时用，到达快、停得软。 */
    val Decelerate = CubicBezierEasing(0f, 0f, .18f, 1f)

    /** 加速曲线：元素离开画面时用，比进入更短，不拖住下一步操作。 */
    val Accelerate = CubicBezierEasing(.32f, 0f, .88f, .2f)
}

/** 统一入口：除一次性主场动画外，不在调用处直接写 tween。 */
internal fun <T> motion(
    durationMillis: Int = Motion.Base,
    easing: Easing = Motion.Standard,
    delayMillis: Int = 0,
): TweenSpec<T> = tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)

internal fun <T> motionIn(durationMillis: Int = Motion.Base, delayMillis: Int = 0): TweenSpec<T> =
    motion(durationMillis, Motion.Decelerate, delayMillis)

internal fun <T> motionOut(durationMillis: Int = Motion.Quick): TweenSpec<T> =
    motion(durationMillis, Motion.Accelerate)

/** 轻弹簧：只用于需要一点“回弹生命感”的小元件（标签胶囊、按压回弹）。 */
internal fun <T> motionSpring(
    dampingRatio: Float = .82f,
    stiffness: Float = Spring.StiffnessMediumLow,
): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * 同一位置换内容（标题、数值、状态文案）。
 *
 * 旧内容快速淡出，新内容稍后淡入，避免两段文字同时可见的重影；
 * 尺寸变化用同一个时长过渡，换内容时卡片不会跳高矮。
 *
 * 只能放在 `AnimatedContent` 的 `transitionSpec` 里用（`using` 由该作用域提供）。
 */
internal fun <S> AnimatedContentTransitionScope<S>.contentSwap(
    enterMillis: Int = Motion.Base,
    exitMillis: Int = Motion.Quick,
    sizeAnimation: Boolean = true,
): ContentTransform {
    val swap = fadeIn(motionIn<Float>(enterMillis, delayMillis = 60)) togetherWith fadeOut(motionOut(exitMillis))
    return if (sizeAnimation) {
        swap.using(SizeTransform(clip = false) { _, _ -> motion(Motion.Base) })
    } else {
        swap
    }
}

/** 区块在布局中展开：高度与透明度一起变化，让观众看清“从无到有”。 */
internal fun expandFade(durationMillis: Int = Motion.Base, from: Alignment.Vertical = Alignment.Top): EnterTransition =
    expandVertically(motionIn(durationMillis), expandFrom = from) + fadeIn(motionIn(durationMillis))

/** 区块收起：比展开更快，避免遮挡后续操作。 */
internal fun shrinkFade(durationMillis: Int = Motion.Quick, towards: Alignment.Vertical = Alignment.Top): ExitTransition =
    shrinkVertically(motionOut(durationMillis), shrinkTowards = towards) + fadeOut(motionOut(durationMillis))

/**
 * 只做淡入淡出，不碰高度。
 *
 * 用在父容器已经用 `animateContentSize` 统一管高度的区块里：
 * 尺寸只有一个主人，父子两层同时缩放会互相追赶，看起来“慢半拍”。
 */
internal fun appearFade(durationMillis: Int = Motion.Base): EnterTransition = fadeIn(motionIn(durationMillis))

internal fun disappearFade(durationMillis: Int = Motion.Quick): ExitTransition = fadeOut(motionOut(durationMillis))

/**
 * 按下回弹。用在“提交 / 打开”这类需要明确手感的主操作上，列表行仍交给系统水波。
 *
 * 按下用 [Motion.Instant] 直接给反馈，松手回到原尺寸时带一点弹簧，
 * 动画进行中再次按下会从当前尺寸接续，不会跳帧。
 */
@Composable
internal fun rememberPressScale(source: InteractionSource, pressedScale: Float = .97f): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) motion(Motion.Instant) else motionSpring(dampingRatio = .55f),
        label = "按下回弹",
    )
}
