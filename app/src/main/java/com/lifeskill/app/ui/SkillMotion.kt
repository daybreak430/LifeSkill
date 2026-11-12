package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon

internal const val MAP_UNLOCK_DELAY_MS = 200L
internal const val MAP_UNLOCK_DURATION_MS = 1050
internal const val MAP_LOCK_RELEASE_AT = .88f
internal const val CELEBRATION_DURATION_MS = 3_000
private const val BADGE_REVEAL_START = .025f

private val celebrationHaptics = listOf(BADGE_REVEAL_START to HapticFeedbackType.Confirm) +
    fireworkBurstProgress.mapIndexed { index, progress ->
        progress to if (index == fireworkBurstProgress.lastIndex) HapticFeedbackType.SegmentTick
        else HapticFeedbackType.SegmentFrequentTick
    }

private val CelebrationGold = Color(0xFFFFDEA0)
private val CelebrationMint = Color(0xFFAFEAD1)
private val CelebrationNight = Color(0xFF0B1E26)
private val CelebrationInk = Color(0xFFFFF3DA)

internal fun motionPhase(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

@Composable
internal fun SkillCelebration(key: String, skill: Entry, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setWindowAnimations(0)
                it.setDimAmount(0f)
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        CelebrationContent(key, skill, onClose)
    }
}

@Composable
internal fun CelebrationContent(key: String, skill: Entry, onClose: () -> Unit) {
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val clock = remember(key) { Animatable(if (played) 1f else 0f) }
    val showFireworks by remember(clock) { derivedStateOf { clock.value < 1f } }
    val exit = remember(key) { Animatable(0f) }
    var closing by remember(key) { mutableStateOf(false) }
    val close = rememberUpdatedState(onClose)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key) {
        // 同一次点亮在旋转屏幕后直接恢复徽章，避免重放整段烟花。
        if (!played) {
            played = true
            var nextCue = 0
            clock.animateTo(1f, tween(CELEBRATION_DURATION_MS, easing = LinearEasing)) {
                val before = nextCue
                while (nextCue < celebrationHaptics.size && value >= celebrationHaptics[nextCue].first) nextCue++
                // 动画跳帧只反馈最近一拍，直接跳到终帧或退出时不补发触感。
                if (nextCue > before && value < 1f && !closing &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    haptics.performHapticFeedback(celebrationHaptics[nextCue - 1].second)
                }
            }
        }
    }
    LaunchedEffect(key, closing) {
        if (closing) {
            exit.animateTo(1f, tween(180))
            close.value()
        }
    }
    Box(
        Modifier.fillMaxSize().testTag("skill-celebration")
            .drawBehind { drawRect(CelebrationNight.copy(alpha = .86f * (1f - exit.value))) },
        contentAlignment = Alignment.Center,
    ) {
        if (showFireworks) {
            CelebrationFireworks(
                Modifier.matchParentSize().testTag("celebration-fireworks"), { clock.value }, { 1f - exit.value },
            )
        }
        Column(
            Modifier.safeDrawingPadding().graphicsLayer {
                val appear = FastOutSlowInEasing.transform(motionPhase(clock.value, 0f, .13f))
                val leave = exit.value
                alpha = (.38f + .62f * appear) * (1f - leave)
                scaleX = .94f + .06f * appear
                scaleY = scaleX
                translationY = (1f - appear - leave) * 12.dp.toPx()
            }.verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                Modifier.widthIn(min = 222.dp, max = 320.dp).semantics { liveRegion = LiveRegionMode.Polite },
                shape = DialogShape, color = CelebrationNight, shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Brush.linearGradient(listOf(
                    CelebrationGold.copy(alpha = .72f), CelebrationMint.copy(alpha = .17f),
                    CelebrationGold.copy(alpha = .50f),
                ))),
            ) {
                Column(
                    Modifier.background(Brush.verticalGradient(listOf(
                        Color(0xFF225749), Color(0xFF14372F), CelebrationNight,
                    ))).padding(horizontal = 25.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CelebrationBadge(skill.displayIcon) { clock.value }
                    Text("本节已完成", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, color = CelebrationInk, textAlign = TextAlign.Center)
                    Text(skill.name, style = MaterialTheme.typography.bodyMedium,
                        color = CelebrationMint, textAlign = TextAlign.Center)
                }
            }
            GlyphButton(Glyph.CLOSE, "关闭庆祝", { closing = true }, enabled = !closing,
                tint = CelebrationInk, background = Color(0xFF24453D))
        }
    }
}

@Composable
private fun CelebrationBadge(icon: EntryIcon, progress: () -> Float) {
    Box(
        Modifier.size(116.dp).graphicsLayer {
            val reveal = FastOutSlowInEasing.transform(motionPhase(progress(), BADGE_REVEAL_START, .20f))
            val settle = FastOutSlowInEasing.transform(motionPhase(progress(), .20f, .34f))
            scaleX = .74f + .34f * reveal - .08f * settle
            scaleY = scaleX
            alpha = .45f + .55f * reveal
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            drawCircle(Brush.radialGradient(
                listOf(CelebrationGold.copy(alpha = .30f), Color.Transparent), center, radius,
            ), radius)
            drawCircle(Brush.sweepGradient(listOf(
                Color(0xFFAB8249), CelebrationGold, CelebrationMint, Color(0xFF7A5D35),
                CelebrationGold, Color(0xFFAB8249),
            ), center), radius * .84f)
            drawCircle(CelebrationNight, radius * .78f)
            drawCircle(Brush.radialGradient(
                listOf(Color(0xFF67B79A), Color(0xFF22634F), Color(0xFF103D35)),
                center - Offset(radius * .24f, radius * .34f), radius,
            ), radius * .70f)
            drawCircle(CelebrationMint.copy(alpha = .63f), radius * .70f, style = Stroke(1.dp.toPx()))
            drawCircle(CelebrationGold.copy(alpha = .42f), radius * .57f, style = Stroke(1.dp.toPx()))
            val edge = radius * .91f
            drawArc(CelebrationMint.copy(alpha = .54f), 196f, 72f, false,
                topLeft = center - Offset(edge, edge), size = Size(edge * 2, edge * 2),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            drawArc(CelebrationGold.copy(alpha = .62f), 14f, 62f, false,
                topLeft = center - Offset(edge, edge), size = Size(edge * 2, edge * 2),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        }
        Mark(icon.glyph, CelebrationInk, Modifier.size(42.dp))
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(28.dp),
            shape = CircleShape, color = CelebrationGold, border = BorderStroke(1.5.dp, CelebrationNight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Mark(Glyph.CHECK, CelebrationNight, Modifier.size(16.dp))
            }
        }
    }
}
