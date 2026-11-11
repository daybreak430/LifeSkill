package com.lifeskill.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal val TreeChallenge.icon get() = when (this) {
    TreeChallenge.DIAGNOSTIC -> EntryIcon.TARGET
    TreeChallenge.FEYNMAN -> EntryIcon.SPEECH
}

private data class MedalPalette(val center: Color, val edge: Color, val trim: Color, val ornaments: Int)

private val Perfect = MedalPalette(Color(0xFF8662BC), Color(0xFF302264), Color(0xFFFFE1A0), 12)
private val GoldMedal = MedalPalette(Color(0xFF99652A), Color(0xFF56371E), Color(0xFFFFD889), 8)
private val SilverMedal = MedalPalette(Color(0xFF496C9C), Color(0xFF233957), Color(0xFFD2E9FF), 4)
private val GreenMedal = MedalPalette(Color(0xFF388472), Color(0xFF214A45), Color(0xFFB7EADB), 2)
private val BronzeMedal = MedalPalette(Color(0xFF8F5E50), Color(0xFF52343D), Color(0xFFF3C4AA), 1)
private val SlateMedal = MedalPalette(Color(0xFF5B6270), Color(0xFF373A43), Color(0xFFC5CAD5), 0)

@Composable
internal fun AssessmentMedallion(
    icon: EntryIcon, diameter: Dp, state: SkillState = SkillState.AVAILABLE,
    score: Int? = null, challengeCompleted: Boolean = false,
) {
    val palette = when {
        challengeCompleted || score == 100 -> Perfect
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
    ready: Boolean, summary: TreeAssessmentSummary, diameter: Dp,
    onExam: () -> Unit, onChallenge: (TreeChallenge) -> Unit,
) {
    val state = when {
        summary.passed -> SkillState.LIT
        ready -> SkillState.AVAILABLE
        else -> SkillState.LOCKED
    }
    val route = if (ready || summary.bestScore != null) studyColors.route else Line
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawLine(route, Offset(center.x, 0f), Offset(center.x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
        AssessmentMapNode("验收考试", EntryIcon.BOOK, diameter, state, onExam,
            score = summary.bestScore, detail = summary.bestScore?.let { "最高 $it 分" })
        if (summary.passed) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                drawPath(Path().apply {
                    moveTo(size.width * .25f, size.height)
                    cubicTo(size.width * .25f, size.height * .55f, center.x, size.height * .7f, center.x, 0f)
                    cubicTo(center.x, size.height * .7f, size.width * .75f, size.height * .55f, size.width * .75f, size.height)
                }, route, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TreeChallenge.entries.forEach { kind ->
                    val passed = kind in summary.completedChallenges
                    AssessmentMapNode(kind.title, kind.icon, diameter,
                        if (passed) SkillState.LIT else SkillState.AVAILABLE, { onChallenge(kind) },
                        modifier = Modifier.weight(1f), challengeCompleted = passed,
                        detail = "挑战成功".takeIf { passed },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentMapNode(
    title: String, icon: EntryIcon, diameter: Dp, state: SkillState, open: () -> Unit,
    modifier: Modifier = Modifier, score: Int? = null, challengeCompleted: Boolean = false, detail: String? = null,
) {
    Surface(
        onClick = open,
        modifier = modifier.semantics { stateDescription = if (challengeCompleted) "挑战成功" else state.label },
        shape = ControlShape, color = Color.Transparent,
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AssessmentMedallion(icon, diameter, state, score, challengeCompleted)
            Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Gold) }
        }
    }
}
