package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.SkillState
import kotlin.math.cos
import kotlin.math.sin

internal const val MAP_UNLOCK_DELAY_MS = 200L
internal const val MAP_UNLOCK_DURATION_MS = 1050
internal const val MAP_LOCK_RELEASE_AT = .88f

internal fun motionPhase(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

@Composable
internal fun SkillCelebration(key: String, skill: Entry, onFinished: () -> Unit) {
    Dialog(
        onDismissRequest = onFinished,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
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
        CelebrationContent(key, skill, onFinished)
    }
}

@Composable
private fun CelebrationContent(key: String, skill: Entry, onFinished: () -> Unit) {
    val clock = remember(key) { Animatable(0f) }
    val finish = rememberUpdatedState(onFinished)
    val colors = studyColors
    val spark = Gold
    LaunchedEffect(key) {
        clock.animateTo(1f, tween(1450, easing = LinearEasing))
        finish.value()
    }
    Box(
        Modifier.fillMaxSize().testTag("skill-celebration")
            .drawBehind { drawRect(Color.Black.copy(alpha = .62f * (1f - motionPhase(clock.value, .76f, 1f)))) }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val time = clock.value
            val pulse = motionPhase(time, 0f, .66f)
            val glow = .24f * (1f - motionPhase(time, .20f, .78f))
            val reach = minOf(size.width * .46f, 230.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(colors.focusBand.copy(alpha = glow), Color.Transparent), center, reach),
                reach,
            )
            repeat(2) { ring ->
                val wave = motionPhase(time, ring * .10f, .68f + ring * .10f)
                if (wave > 0f) drawCircle(
                    spark.copy(alpha = .32f * (1f - wave)), reach * (.28f + .68f * wave),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            repeat(14) { index ->
                val angle = index * .4488f
                val reachRatio = .63f + (index % 3) * .14f
                val distance = reach * reachRatio * (.32f + .68f * pulse)
                val point = center + Offset(cos(angle) * distance, sin(angle) * distance)
                drawCircle(
                    (if (index % 3 == 0) colors.focusBand else spark).copy(alpha = .78f * (1f - pulse)),
                    (2 + index % 3).dp.toPx(), point,
                )
            }
        }
        Surface(
            Modifier.padding(horizontal = 32.dp).widthIn(min = 194.dp, max = 300.dp)
                .graphicsLayer {
                    val appear = FastOutSlowInEasing.transform(motionPhase(clock.value, 0f, .08f))
                    val leave = motionPhase(clock.value, .76f, 1f)
                    alpha = (.56f + .44f * appear) * (1f - leave)
                    scaleX = .92f + .08f * appear
                    scaleY = scaleX
                    translationY = (1f - appear - leave) * 8.dp.toPx()
                }.semantics { liveRegion = LiveRegionMode.Polite },
            shape = DialogShape, color = Panel, shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkillMedallion(skill.displayIcon, SkillState.LIT, 76.dp)
                Text("技能已点亮", style = MaterialTheme.typography.titleLarge, color = Gold)
                Text(skill.name, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
            }
        }
    }
}
