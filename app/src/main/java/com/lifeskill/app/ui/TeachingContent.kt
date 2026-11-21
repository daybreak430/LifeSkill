package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifeskill.app.model.*

@Composable
internal fun TeachingContent(turn: Turn, enabled: Boolean, onAction: (String, TeachingAction) -> Unit) {
    var expandedId by rememberSaveable(turn.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        turn.teachingBlocks.forEach { block ->
            key(block.id) {
                TeachingCard(block, turn.teachingStates[block.id] ?: TeachingState(), enabled,
                    { action -> onAction(block.id, action) }, onExpand = { expandedId = block.id })
            }
        }
    }
    val expanded = turn.teachingBlocks.find { it.id == expandedId }
    if (expanded != null) Dialog(
        onDismissRequest = { expandedId = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag("teaching-expanded"), color = Ground) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(expanded.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Ink)
                    GlyphButton(Glyph.CLOSE, "关闭互动图示", { expandedId = null })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TeachingCard(expanded, turn.teachingStates[expanded.id] ?: TeachingState(), enabled,
                        { action -> onAction(expanded.id, action) })
                }
            }
        }
    }
}

@Composable
private fun TeachingCard(
    block: TeachingBlock, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
    onExpand: (() -> Unit)? = null,
) {
    Surface(Modifier.fillMaxWidth().testTag("teaching-block-${block.id}"), shape = PanelShape,
        color = Panel, border = BorderStroke(1.dp, aiColors.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleSmall)
                if (block !is TeachingText) {
                    if (onExpand != null) TextButton(onClick = onExpand) { Text("放大", color = aiColors.accent) }
                    GlyphButton(Glyph.RESTART, "重置${block.title}", { onAction(TeachingAction.Reset) },
                        enabled = enabled, background = Panel)
                }
            }
            when (block) {
                is TeachingText -> LessonMarkdown(block.text)
                is TeachingTimeline -> TimelineContent(block, state, enabled, onAction)
                is TeachingFlow -> InteractiveFlowContent(block, state, enabled, onAction)
                is TeachingComparison -> ComparisonContent(block, state, enabled, onAction)
                is TeachingChart -> TeachingChartContent(block, state, enabled, onAction)
                is TeachingQuiz -> PracticeContent(block, state, enabled, onAction)
            }
        }
    }
}

@Composable
private fun TimelineContent(block: TeachingTimeline, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val selected = state.selection ?: block.events.first().id
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.events.forEach { event ->
            val active = event.id == selected
            Surface(onClick = { onAction(TeachingAction.Select(event.id)) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("timeline-${event.id}").semantics { this.selected = active },
                shape = ControlShape, color = if (active) aiColors.chip else Ground) {
                Row(Modifier.heightIn(min = 52.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(event.date, Modifier.widthIn(min = 62.dp, max = 82.dp), color = aiColors.accent,
                        style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                        if (active) Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveFlowContent(block: TeachingFlow, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val flow = remember(block) { block.flow } ?: return
    val selection = state.selection ?: flow.labels.keys.first()
    FlowBlock(flow, selection, if (enabled) ({ id -> onAction(TeachingAction.Select(id)) }) else null)
    Text(flow.labels.getValue(selection), style = MaterialTheme.typography.titleSmall, color = Ink)
    Text(block.details.getValue(selection), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = Muted)
    val next = (flow.arrows + flow.returnArrows).filter { it.from == selection }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        next.forEach { edge ->
            OutlinedButton(onClick = { onAction(TeachingAction.Select(edge.to)) }, enabled = enabled) {
                Text(listOfNotNull(edge.label, flow.labels[edge.to]).joinToString(" → "))
            }
        }
    }
}

@Composable
private fun ComparisonContent(block: TeachingComparison, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val dimension = block.dimensions.find { it.id == state.selection } ?: block.dimensions.first()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.dimensions.forEach {
            TeachingOption(it.title, it.id == dimension.id, enabled, Modifier, { onAction(TeachingAction.Select(it.id)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        block.columns.forEachIndexed { index, label ->
            Surface(Modifier.weight(1f), shape = ControlShape, color = Ground) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = aiColors.accent, style = MaterialTheme.typography.labelLarge)
                    Text(dimension.values[index], color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PracticeContent(block: TeachingQuiz, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val question = block.question
    val correct = state.submitted && state.selection?.toIntOrNull() == question.answer
    Text(question.question, style = MaterialTheme.typography.bodyLarge, color = Ink)
    question.options.forEachIndexed { index, text ->
        TeachingOption(choiceAnswer(index, text), state.selection == index.toString(), enabled && !correct,
            Modifier.fillMaxWidth().testTag("practice-choice-$index"), { onAction(TeachingAction.Select(index.toString())) })
    }
    if (state.submitted) {
        Surface(shape = ControlShape, color = aiColors.chip) {
            Column(Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (correct) "理解正确" else "再想一步", color = aiColors.accent, style = MaterialTheme.typography.titleSmall)
                Text(if (correct) question.explanation else question.hint, color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (!correct) Button(onClick = { onAction(TeachingAction.Submit) },
        enabled = enabled && state.selection != null && !state.submitted, modifier = Modifier.fillMaxWidth()) {
        Text("提交随堂答案")
    }
}

@Composable
internal fun TeachingOption(text: String, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = ControlShape,
        color = if (active) aiColors.chip else Ground,
        border = BorderStroke(1.dp, if (active) aiColors.accent else Line),
        modifier = modifier.semantics { selected = active }) {
        Text(text, Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium, color = if (active) aiColors.accent else Ink)
    }
}
