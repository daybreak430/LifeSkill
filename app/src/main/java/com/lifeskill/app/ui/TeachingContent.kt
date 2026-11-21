package com.lifeskill.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifeskill.app.model.*

@Composable
internal fun TeachingContent(turn: Turn, enabled: Boolean, onAction: (String, TeachingAction) -> Unit) {
    var gameEpoch by rememberSaveable(turn.id) { mutableIntStateOf(0) }
    var expandedId by rememberSaveable(turn.id) { mutableStateOf<String?>(null) }
    val availableBlocks = turn.teachingBlocks.filter { it.isAvailable }
    if (availableBlocks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        availableBlocks.forEach { block ->
            key(block.id) {
                TeachingCard(block, turn.teachingStates[block.id] ?: TeachingState(), enabled,
                    { action -> onAction(block.id, action) }, onExpand = {
                        if (block is TeachingGame) onAction(block.id, TeachingAction.OpenGame)
                        expandedId = block.id
                    })
            }
        }
    }
    val expanded = availableBlocks.find { it.id == expandedId }
    if (expanded != null) Dialog(
        onDismissRequest = { expandedId = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)).safeDrawingPadding().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                Modifier.fillMaxSize().testTag("teaching-expanded"),
                shape = RoundedCornerShape(28.dp),
                color = Ground,
                tonalElevation = 6.dp,
                shadowElevation = 18.dp,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(expanded.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Ink)
                        if (expanded !is TeachingText) {
                            GlyphButton(Glyph.RESTART, if (expanded is TeachingGame) "重新开始小游戏" else "重置${expanded.title}",
                                { if (expanded is TeachingGame) gameEpoch++ else onAction(expanded.id, TeachingAction.Reset) }, enabled = enabled)
                        }
                        GlyphButton(Glyph.CLOSE, "关闭互动图示", { expandedId = null })
                    }
                    if (expanded is TeachingGame) TeachingGamePlayer(expanded, gameEpoch, Modifier.weight(1f).fillMaxWidth())
                    else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        TeachingBlockBody(
                            expanded, turn.teachingStates[expanded.id] ?: TeachingState(), enabled,
                            { action -> onAction(expanded.id, action) }, expanded = true,
                        )
                    }
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
        // 时间轴由每行协调展开，其余组件由卡片协调高度，避免嵌套尺寸动画互相追赶。
        Column(Modifier.then(if (block is TeachingTimeline) Modifier else Modifier.animateContentSize(tween(220)))
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleSmall)
                if (block !is TeachingText && block !is TeachingGame) {
                    if ((block is TeachingChart || block is TeachingExperiment) && onExpand != null) {
                        GlyphButton(Glyph.EXPAND, "放大${block.title}", onExpand, tint = aiColors.accent)
                    }
                    GlyphButton(Glyph.RESTART, "重置${block.title}", { onAction(TeachingAction.Reset) },
                        enabled = enabled, background = Panel)
                }
            }
            if (block is TeachingGame) TeachingGamePreview(block, enabled) { onExpand?.invoke() }
            else TeachingBlockBody(block, state, enabled, onAction)
        }
    }
}

@Composable
private fun TeachingBlockBody(
    block: TeachingBlock, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
    expanded: Boolean = false,
) {
    when (block) {
        is TeachingGame -> Unit
        is TeachingText -> LessonMarkdown(block.text)
        is TeachingTimeline -> TimelineContent(block, state, enabled, onAction)
        is TeachingFlow -> InteractiveFlowContent(block, state, enabled, onAction)
        is TeachingComparison -> ComparisonContent(block, state, enabled, onAction)
        is TeachingExperiment -> TeachingExperimentContent(block, state, enabled, onAction, expanded)
        is TeachingChart -> TeachingChartContent(block, state, enabled, onAction)
        is TeachingQuiz -> PracticeContent(block, state, enabled, onAction)
        is TeachingFlipCard -> TeachingFlipCardContent(block, state, enabled, onAction)
        is TeachingSorting -> TeachingSortingContent(block, state, enabled, onAction)
        is TeachingClassification -> Unit
        is TeachingHotspot -> TeachingHotspotContent(block, state, enabled, onAction)
        is TeachingStepTrace -> Unit
    }
}

@Composable
private fun TimelineContent(block: TeachingTimeline, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit) {
    val selected = state.selection ?: block.events.first().id
    val accent = aiColors.accent
    val rail = accent.copy(alpha = .25f)
    Column {
        block.events.forEachIndexed { index, event ->
            val active = event.id == selected
            // 轴线在整行高度上绘制，详情展开后仍连续；首尾只连接到各自节点。
            Row(Modifier.fillMaxWidth().drawBehind {
                val horizontal = 10.dp.toPx()
                val center = 25.dp.toPx()
                drawLine(rail, Offset(horizontal, if (index == 0) center else 0f),
                    Offset(horizontal, if (index == block.events.lastIndex) center else size.height), 2.dp.toPx())
                if (active) drawCircle(accent.copy(alpha = .15f), 9.dp.toPx(), Offset(horizontal, center))
                drawCircle(accent, if (active) 5.dp.toPx() else 3.dp.toPx(), Offset(horizontal, center))
            }.padding(start = 28.dp, bottom = 8.dp)) {
                Surface(onClick = { onAction(TeachingAction.Select(event.id)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("timeline-${event.id}").semantics { this.selected = active },
                    shape = ControlShape, color = if (active) aiColors.chip else Ground,
                    border = if (active) BorderStroke(1.dp, accent.copy(alpha = .4f)) else null) {
                    Column(Modifier.animateContentSize(tween(220)).heightIn(min = 52.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.date, color = accent, style = MaterialTheme.typography.labelMedium)
                        Text(event.title, style = MaterialTheme.typography.titleSmall, color = Ink)
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
    val accents = when (block.palette) {
        ComparisonPalette.BLUE_AMBER -> listOf(Color(0xFF276B91), Color(0xFFAD6422), Color(0xFF6674A6))
        ComparisonPalette.VIOLET_TEAL -> listOf(Color(0xFF8060BD), Color(0xFF168479), Color(0xFFB17D3D))
        ComparisonPalette.ROSE_SAGE -> listOf(Color(0xFFB35777), Color(0xFF588167), Color(0xFF577EAC))
    }
    val dark = Panel.luminance() < .3f
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        block.columns.forEachIndexed { index, label ->
            val accent = if (dark) lerp(accents[index], Color.White, .35f) else accents[index]
            Surface(Modifier.weight(1f).fillMaxHeight().testTag("comparison-${block.id}-column-$index"), shape = ControlShape,
                color = lerp(Panel, accent, if (dark) .17f else .09f), border = BorderStroke(1.dp, accent.copy(alpha = .3f))) {
                Column {
                    Box(Modifier.fillMaxWidth().height(3.dp).drawBehind { drawRect(accent) })
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(dimension.values[index], color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
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
        TeachingOption(choiceAnswer(index, text), state.selection == index.toString(),
            enabled && !correct && !state.revealed,
            Modifier.fillMaxWidth().testTag("practice-choice-$index"), { onAction(TeachingAction.Select(index.toString())) })
    }
    if (state.submitted) {
        Surface(shape = ControlShape, color = aiColors.chip) {
            Column(Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (correct) "理解正确" else if (state.revealed) "完整答案" else "再想一步",
                    color = aiColors.accent, style = MaterialTheme.typography.titleSmall)
                Text(if (correct || state.revealed) question.explanation else question.hint,
                    color = Ink, style = MaterialTheme.typography.bodyMedium)
                if (!correct && state.revealed) {
                    Text("正确选项：${choiceAnswer(question.answer, question.options[question.answer])}",
                        color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
                if (!correct && !state.revealed) {
                    TextButton(onClick = { onAction(TeachingAction.Reveal) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth()) { Text("查看完整答案") }
                }
            }
        }
    }
    if (!correct) Button(onClick = { onAction(TeachingAction.Submit) },
        enabled = enabled && state.selection != null && !state.submitted && !state.revealed,
        modifier = Modifier.fillMaxWidth()) {
        Text(if (state.submitted) "更改答案后可重试" else "提交随堂答案")
    }
}

@Composable
internal fun TeachingOption(text: String, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background by animateColorAsState(if (active) aiColors.chip else Ground, tween(180), label = "教学选项颜色")
    Surface(onClick = onClick, enabled = enabled, shape = ControlShape,
        color = background,
        border = BorderStroke(1.dp, if (active) aiColors.accent else Line),
        modifier = modifier.semantics { selected = active }) {
        // 最小高度属于容器，文字只按自身行高测量，再在容器内居中；不能把最小高度直接加在 Text 上。
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (active) aiColors.accent else Ink)
        }
    }
}
