package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.TreeAssessmentSummary
import com.lifeskill.app.model.TreeChallenge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class MedalPalette(val center: Color, val edge: Color, val trim: Color, val ornaments: Int)

private val Perfect = MedalPalette(Color(0xFF8662BC), Color(0xFF302264), Color(0xFFFFE1A0), 12)
private val GoldMedal = MedalPalette(Color(0xFF99652A), Color(0xFF56371E), Color(0xFFFFD889), 8)
private val SilverMedal = MedalPalette(Color(0xFF496C9C), Color(0xFF233957), Color(0xFFD2E9FF), 4)
private val GreenMedal = MedalPalette(Color(0xFF388472), Color(0xFF214A45), Color(0xFFB7EADB), 2)
private val BronzeMedal = MedalPalette(Color(0xFF8F5E50), Color(0xFF52343D), Color(0xFFF3C4AA), 1)
private val SlateMedal = MedalPalette(Color(0xFF5B6270), Color(0xFF373A43), Color(0xFFC5CAD5), 0)

@Composable
internal fun AssessmentMedallion(
    icon: EntryIcon, diameter: Dp, state: SkillState = SkillState.AVAILABLE, score: Int? = null,
) {
    val palette = when {
        score == 100 -> Perfect
        score == null -> null
        score >= 90 -> GoldMedal
        score >= 80 -> SilverMedal
        score >= 60 -> GreenMedal
        score > 0 -> BronzeMedal
        else -> SlateMedal
    }
    if (palette == null) {
        SkillMedallion(icon, state, diameter, mapPalette = studyColors.mapNodePalette)
        return
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.minDimension
            drawCircle(palette.trim.copy(alpha = .24f), unit * .49f)
            drawCircle(Brush.linearGradient(listOf(palette.center, palette.edge)), unit * .45f)
            drawCircle(palette.trim, unit * .43f, style = Stroke(unit * .025f))
            if (palette.ornaments > 0) {
                drawCircle(palette.trim.copy(alpha = .42f), unit * .36f, style = Stroke(unit * .009f))
            }
            repeat(palette.ornaments) { index ->
                val angle = (index.toDouble() / palette.ornaments - .25) * 2 * PI
                val anchor = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (unit * .405f)
                val radius = unit * if (index % 2 == 0) .033f else .021f
                drawPath(Path().apply {
                    moveTo(anchor.x, anchor.y - radius)
                    lineTo(anchor.x + radius * .7f, anchor.y)
                    lineTo(anchor.x, anchor.y + radius)
                    lineTo(anchor.x - radius * .7f, anchor.y)
                    close()
                }, palette.trim)
            }
        }
        Mark(if (score == 100) Glyph.DIAMOND else icon.glyph, palette.trim, Modifier.size(diameter * .41f))
    }
}

@Composable
internal fun ExamMapNodes(
    ready: Boolean, summary: TreeAssessmentSummary, diameter: Dp, active: Boolean, treeId: String,
    onExam: () -> Unit, onChallenge: (TreeChallenge) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var revealed by rememberSaveable(treeId) { mutableStateOf(false) }
    // 离开挑战页后恢复已完成的能量帧，避免已显现的挑战节点再次变成透明。
    val reveal = remember(treeId) { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(active, summary.passed, treeId) {
        if (!active || !summary.passed || revealed) return@LaunchedEffect
        reveal.snapTo(0f)
        var feltCharge = false
        var feltRelease = false
        reveal.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing)) {
            if (!feltCharge && value >= .20f) {
                feltCharge = true
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            } else if (!feltRelease && value >= .46f) {
                feltRelease = true
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
        revealed = true
    }
    val state = when {
        summary.passed -> SkillState.LIT
        ready -> SkillState.AVAILABLE
        else -> SkillState.LOCKED
    }
    val route = if (ready || summary.bestScore != null) studyColors.route else Line
    val revealModifier = if (summary.passed) {
        Modifier.testTag("challenge-reveal").semantics {
            stateDescription = if (revealed) "挑战节点已显现" else "挑战节点正在蓄力"
        }
    } else Modifier
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).then(revealModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawLine(route, Offset(center.x, 0f), Offset(center.x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
        AssessmentMapNode("验收考试", EntryIcon.BOOK, diameter, state, onExam,
            score = summary.bestScore, detail = summary.bestScore?.let { "最高 $it 分" })
        if (summary.passed) {
            ChallengeRevealConnector(route, progress = { reveal.value })
            Row(
                Modifier.fillMaxWidth().graphicsLayer {
                    // 两个挑战节点先被中心吸引，再向两侧弹开，形成接受能量后释放的张力。
                    val release = motionPhase(reveal.value, .30f, .84f)
                    val settle = motionPhase(reveal.value, .72f, 1f)
                    alpha = motionPhase(reveal.value, .30f, .53f)
                    scaleX = .80f + .20f * release + .035f * settle * (1f - settle) * 4f
                    scaleY = scaleX
                },
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TreeChallenge.entries.forEachIndexed { index, kind ->
                    val passed = kind in summary.completedChallenges
                    ChallengeMapNode(
                        kind, diameter, passed, { onChallenge(kind) },
                        Modifier.weight(1f).graphicsLayer {
                            val release = motionPhase(reveal.value, .30f, .84f)
                            val charge = motionPhase(reveal.value, 0f, .34f)
                            val direction = if (index == 0) -1f else 1f
                            translationX = direction * 34.dp.toPx() * (1f - release)
                            rotationZ = direction * -5f * (1f - release) + charge * direction * 2f
                        },
                        progress = { reveal.value },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeRevealConnector(route: Color, progress: () -> Float) {
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val clock = progress()
        val charge = motionPhase(clock, 0f, .30f)
        val split = motionPhase(clock, .25f, .73f)
        val settle = motionPhase(clock, .68f, 1f)
        val centerTop = Offset(center.x, 0f)
        val centerSplit = Offset(center.x, size.height * .30f)
        val leftEnd = Offset(size.width * .25f, size.height)
        val rightEnd = Offset(size.width * .75f, size.height)
        val glowRadius = size.minDimension * (.08f + .16f * charge + .12f * (1f - settle))
        if (clock > 0f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(route.copy(alpha = .42f * (1f - settle)), Color.Transparent),
                    centerSplit,
                    glowRadius,
                ),
                glowRadius,
                centerSplit,
            )
            repeat(3) { index ->
                val ring = motionPhase(clock, .18f + index * .06f, .48f + index * .06f)
                if (ring in 0f..1f) {
                    drawCircle(
                        route.copy(alpha = .30f * (1f - ring)),
                        size.minDimension * (.10f + .28f * ring),
                        centerSplit,
                        style = Stroke(1.2.dp.toPx()),
                    )
                }
            }
        }
        drawLine(route.copy(alpha = .42f + .58f * split), centerTop, centerSplit, 2.dp.toPx(), StrokeCap.Round)
        val left = Path().apply {
            moveTo(centerSplit.x, centerSplit.y)
            cubicTo(centerSplit.x, size.height * .62f, leftEnd.x, size.height * .56f, leftEnd.x, leftEnd.y)
        }
        val right = Path().apply {
            moveTo(centerSplit.x, centerSplit.y)
            cubicTo(centerSplit.x, size.height * .62f, rightEnd.x, size.height * .56f, rightEnd.x, rightEnd.y)
        }
        listOf(left, right).forEach { path ->
            val measure = PathMeasure().apply { setPath(path, false) }
            val traced = Path()
            measure.getSegment(0f, measure.length * split, traced, true)
            drawPath(traced, route.copy(alpha = split), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (settle > 0f) {
            val spark = 2.dp.toPx() * settle
            drawCircle(Color.White.copy(alpha = .78f * (1f - settle)), spark, centerSplit)
        }
    }
}

@Composable
private fun ChallengeMapNode(
    kind: TreeChallenge, diameter: Dp, completed: Boolean, open: () -> Unit,
    modifier: Modifier = Modifier, progress: () -> Float,
) {
    Surface(
        onClick = open,
        modifier = modifier.semantics { stateDescription = if (completed) "挑战成功" else "隐藏挑战" },
        shape = ControlShape, color = Color.Transparent,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChallengeSigilMedallion(kind, diameter, completed, progress)
            Text(kind.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (completed) Text("挑战成功", style = MaterialTheme.typography.labelMedium, color = Gold)
        }
    }
}

@Composable
private fun AssessmentMapNode(
    title: String, icon: EntryIcon, diameter: Dp, state: SkillState, open: () -> Unit,
    modifier: Modifier = Modifier, score: Int? = null, detail: String? = null,
) {
    Surface(
        onClick = open,
        modifier = modifier.semantics { stateDescription = state.label },
        shape = ControlShape, color = Color.Transparent,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AssessmentMedallion(icon, diameter, state, score)
            Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Gold) }
        }
    }
}
