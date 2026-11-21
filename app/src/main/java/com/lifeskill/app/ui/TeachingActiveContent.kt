package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.*
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import kotlinx.coroutines.delay

@Composable
internal fun TeachingFlipCardContent(
    block: TeachingFlipCard, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    val back = state.selection == "back"
    val density = LocalDensity.current
    val rotation by animateFloatAsState(
        targetValue = if (back) 180f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "知识翻卡",
    )
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp).testTag("flip-card")
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            }
            .semantics { stateDescription = if (back) "答案面" else "问题面" },
        shape = ControlShape,
        color = if (back) aiColors.chip else Ground,
        border = BorderStroke(1.dp, if (back) aiColors.accent.copy(alpha = .45f) else Line),
    ) {
        val showBack = rotation > 90f
        Column(Modifier.fillMaxSize().graphicsLayer {
            if (showBack) rotationY = 180f
        }.padding(20.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (showBack) "答案" else "先回忆", color = aiColors.accent,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(if (showBack) block.answer else block.prompt,
                style = if (showBack) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = Ink)
            Spacer(Modifier.height(14.dp))
            Text(if (showBack) block.hint else "想好后翻开核对", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onAction(TeachingAction.Flip) },
                enabled = enabled,
                modifier = Modifier.testTag("flip-card-turn"),
            ) {
                Mark(Glyph.RESTART, MaterialTheme.colorScheme.onPrimary, Modifier.size(17.dp))
                Text(if (showBack) "翻回" else "翻面", Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
internal fun TeachingSortingContent(
    block: TeachingSorting, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    val order = state.sortOrder(block)
    val complete = state.sortComplete(block)
    val locked = !enabled || complete || state.revealed
    val haptics = LocalHapticFeedback.current
    Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
    ReorderableColumn(
        modifier = Modifier.fillMaxWidth().testTag("sorting-list"),
        list = order,
        onSettle = { fromIndex, toIndex ->
            if (!locked) onAction(TeachingAction.Move(order[fromIndex], toIndex))
        },
        onMove = { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) { index, itemId, isDragging ->
        val item = block.items.first { it.id == itemId }
        key(item.id) {
            ReorderableItem {
                val elevation by animateDpAsState(if (isDragging) 10.dp else 0.dp, label = "排序拖动高度")
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("sort-item-${item.id}")
                        .semantics { contentDescription = "第${index + 1}项，${item.text}；按住拖动点可排序" },
                    shape = ControlShape,
                    color = Ground,
                    border = BorderStroke(if (isDragging) 2.dp else 1.dp,
                        if (isDragging) aiColors.accent else Line),
                    shadowElevation = elevation,
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).testTag("sort-handle-${item.id}").longPressDraggableHandle(
                                enabled = !locked,
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                                onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Mark(Glyph.MOVE, if (locked) Muted else aiColors.accent, Modifier.size(22.dp))
                        }
                        Text("${index + 1}", Modifier.width(24.dp), color = aiColors.accent,
                            style = MaterialTheme.typography.titleSmall)
                        Text(item.text, Modifier.weight(1f).padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium, color = Ink)
                        GlyphButton(Glyph.UP, "上移${item.text}", {
                            onAction(TeachingAction.Move(item.id, index - 1))
                        }, enabled = !locked && index > 0, background = Panel)
                        GlyphButton(Glyph.DOWN, "下移${item.text}", {
                            onAction(TeachingAction.Move(item.id, index + 1))
                        }, enabled = !locked && index < order.lastIndex, background = Panel)
                    }
                }
            }
        }
    }
    AssessmentFeedback(
        tag = "feedback-sorting",
        submitted = state.submitted,
        correct = complete,
        revealed = state.revealed,
        hint = block.hint,
        explanation = block.explanation,
        onReveal = { onAction(TeachingAction.Reveal) },
        enabled = enabled,
    )
    Button(
        onClick = { onAction(TeachingAction.Submit) },
        enabled = enabled && !state.submitted && !complete && !state.revealed,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.submitted) "调整顺序后可重试" else "提交顺序")
    }
}

@Composable
internal fun TeachingClassificationContent(
    block: TeachingClassification, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    val complete = state.classificationComplete(block)
    val locked = !enabled
    Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
    block.items.forEachIndexed { itemIndex, item ->
        val assigned = state.placements[item.id]
        Surface(
            Modifier.fillMaxWidth().testTag("classification-item-${item.id}"),
            shape = ControlShape,
            color = Ground,
            border = BorderStroke(1.dp, Line),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("${itemIndex + 1}", Modifier.width(24.dp), color = aiColors.accent,
                        style = MaterialTheme.typography.titleSmall)
                    Text(item.text, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.categories.forEachIndexed { categoryIndex, category ->
                        val active = assigned == category.id
                        val categoryColor = classificationColor(categoryIndex)
                        Box {
                            Surface(
                                onClick = { onAction(TeachingAction.Classify(item.id, category.id)) },
                                enabled = !locked,
                                modifier = Modifier.heightIn(min = 44.dp)
                                    .testTag("classification-${item.id}-${category.id}")
                                    .semantics { selected = active },
                                shape = CircleShape,
                                color = if (active) lerp(Panel, categoryColor, .18f) else Panel,
                                border = BorderStroke(1.dp, if (active) categoryColor else Line),
                            ) {
                                Box(
                                    Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(category.title, color = if (active) categoryColor else Ink,
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (active) {
                                Surface(
                                    Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(18.dp),
                                    shape = CircleShape,
                                    color = categoryColor,
                                    shadowElevation = 1.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Mark(Glyph.CHECK, Color.White, Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (complete || state.revealed) {
                    Text(item.explanation, color = Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
    AssessmentFeedback(
        tag = "feedback-classification",
        submitted = state.submitted,
        correct = complete,
        revealed = state.revealed,
        hint = block.hint,
        explanation = block.explanation,
        onReveal = { onAction(TeachingAction.Reveal) },
        enabled = enabled,
    )
    val assignedCount = block.items.count { state.placements[it.id] != null }
    Button(
        onClick = { onAction(TeachingAction.Submit) },
        enabled = enabled && assignedCount == block.items.size && !state.submitted && !complete && !state.revealed,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (assignedCount == block.items.size) "提交分类" else "还剩 ${block.items.size - assignedCount} 项")
    }
}

@Composable
internal fun TeachingHotspotContent(
    block: TeachingHotspot, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
    PlantCellHotspotImage(block, state, enabled) { onAction(TeachingAction.Select(it)) }
    if (state.revealed) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.hotspots.forEach { hotspot ->
                Text("${hotspot.label} · ${hotspot.detail}", color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = { onAction(TeachingAction.Reset) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth()) {
            Text("重新探索")
        }
    } else {
        block.hotspots.find { it.id == state.selection }?.let { hotspot ->
            Surface(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                shape = ControlShape,
                color = aiColors.chip,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(hotspot.label, color = aiColors.accent, style = MaterialTheme.typography.titleSmall)
                    Text(hotspot.detail, color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        TextButton(onClick = { onAction(TeachingAction.Reveal) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth()) { Text("查看全部热点") }
    }
}

@Composable
private fun PlantCellHotspotImage(
    block: TeachingHotspot, state: TeachingState, enabled: Boolean, onSelect: (String) -> Unit,
) {
    val cellFill = lerp(Panel, aiColors.accent, if (Panel.luminance() < .3f) .12f else .06f)
    val wall = lerp(aiColors.accent, Color(0xFF8BAF6A), .35f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(1.55f).clip(ControlShape).background(cellFill)
            .testTag("hotspot-image")
            .semantics {
                role = Role.Image
                contentDescription = "植物细胞示意图，${block.hotspots.size}个可点击热点"
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(wall, topLeft = Offset(size.width * .03f, size.height * .04f),
                size = Size(size.width * .94f, size.height * .92f),
                cornerRadius = CornerRadius(28.dp.toPx()), style = Stroke(5.dp.toPx()))
            drawRoundRect(wall.copy(alpha = .16f), topLeft = Offset(size.width * .06f, size.height * .08f),
                size = Size(size.width * .88f, size.height * .84f),
                cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
            val nucleus = Offset(size.width * .50f, size.height * .46f)
            drawCircle(lerp(cellFill, Color(0xFF9B78C5), .42f), size.minDimension * .145f, nucleus)
            drawCircle(Color(0xFF6B4C96).copy(alpha = .55f), size.minDimension * .055f, nucleus)
            drawOval(lerp(cellFill, Color(0xFF5EADD1), .30f),
                topLeft = Offset(size.width * .62f, size.height * .18f),
                size = Size(size.width * .28f, size.height * .34f))
            drawOval(lerp(cellFill, Color(0xFF68A852), .42f),
                topLeft = Offset(size.width * .17f, size.height * .66f),
                size = Size(size.width * .22f, size.height * .16f))
            drawOval(lerp(cellFill, Color(0xFF68A852), .42f),
                topLeft = Offset(size.width * .30f, size.height * .78f),
                size = Size(size.width * .20f, size.height * .14f))
            drawOval(lerp(cellFill, Color(0xFFD07A4D), .35f),
                topLeft = Offset(size.width * .70f, size.height * .70f),
                size = Size(size.width * .20f, size.height * .12f))
            drawOval(lerp(cellFill, Color(0xFFD07A4D), .35f),
                topLeft = Offset(size.width * .82f, size.height * .64f),
                size = Size(size.width * .14f, size.height * .10f))
        }
        block.hotspots.forEachIndexed { index, hotspot ->
            val x = maxWidth * (hotspot.horizontalPercent.toFloat() / 100f) - 21.dp
            val y = maxHeight * (hotspot.verticalPercent.toFloat() / 100f) - 21.dp
            val active = state.selection == hotspot.id
            Surface(
                onClick = { onSelect(hotspot.id) },
                enabled = enabled,
                modifier = Modifier.offset(x, y).size(42.dp).testTag("hotspot-${hotspot.id}")
                    .semantics {
                        selected = active
                        contentDescription = "热点${index + 1}：${hotspot.label}"
                    },
                shape = CircleShape,
                color = if (active) Gold else aiColors.accent,
                border = BorderStroke(2.dp, Panel),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun TeachingStepTraceContent(
    block: TeachingStepTrace, state: TeachingState, enabled: Boolean, onAction: (TeachingAction) -> Unit,
) {
    val currentStepRequester = remember { BringIntoViewRequester() }
    var observedStep by remember { mutableIntStateOf(state.step) }
    LaunchedEffect(state.step, state.revealed) {
        if (!state.revealed && state.step > observedStep) {
            delay(80)
            currentStepRequester.bringIntoView()
        }
        observedStep = state.step
    }
    Text(block.introduction, color = Ink, style = MaterialTheme.typography.bodyMedium)
    val visibleLast = if (state.revealed) block.steps.lastIndex else state.step.coerceIn(0, block.steps.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (index in 0..visibleLast) {
            val step = block.steps[index]
            val current = !state.revealed && index == state.step
            Surface(
                Modifier.fillMaxWidth().testTag("trace-step-${step.id}")
                    .then(if (current) Modifier.bringIntoViewRequester(currentStepRequester) else Modifier),
                shape = ControlShape,
                color = if (current) aiColors.chip else Ground,
                border = BorderStroke(1.dp, if (current) aiColors.accent.copy(alpha = .4f) else Line),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${step.title}", color = aiColors.accent,
                        style = MaterialTheme.typography.titleSmall)
                    if (current) {
                        Text(step.checkpoint.question, color = Ink, style = MaterialTheme.typography.bodyMedium)
                        step.checkpoint.options.forEachIndexed { optionIndex, option ->
                            TeachingOption(choiceAnswer(optionIndex, option),
                                state.selection?.toIntOrNull() == optionIndex,
                                enabled && !state.revealed,
                                Modifier.fillMaxWidth().testTag("trace-choice-${step.id}-$optionIndex"),
                                { onAction(TeachingAction.Select(optionIndex.toString())) })
                        }
                    } else {
                        Text(step.result, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (current && state.submitted) {
                        val correct = state.currentStepCorrect(block)
                        Text(
                            if (correct) step.checkpoint.explanation else step.checkpoint.hint,
                            color = if (correct) aiColors.accent else Ink,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (correct) {
                            Text(step.result, color = Ink, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (state.revealed) {
        Surface(Modifier.fillMaxWidth(), shape = ControlShape, color = aiColors.chip) {
            Text(block.summary, Modifier.padding(12.dp), color = Ink,
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val current = block.steps[state.step.coerceIn(0, block.steps.lastIndex)]
    val correct = state.currentStepCorrect(block)
    if (state.submitted && !correct) {
        OutlinedButton(onClick = { onAction(TeachingAction.Reveal) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth()) { Text("查看完整推演") }
    }
    when {
        correct && state.step < block.steps.lastIndex -> Button(
            onClick = { onAction(TeachingAction.Advance) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("进入下一步") }
        correct -> Surface(Modifier.fillMaxWidth(), shape = ControlShape, color = aiColors.chip) {
            Text(block.summary, Modifier.padding(12.dp), color = Ink,
                style = MaterialTheme.typography.bodyMedium)
        }
        else -> Button(
            onClick = { onAction(TeachingAction.Submit) },
            enabled = enabled && state.selection != null && !state.submitted,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.submitted) "更改预测后可重试" else "提交预测") }
    }
}

@Composable
private fun AssessmentFeedback(
    tag: String, submitted: Boolean, correct: Boolean, revealed: Boolean, hint: String, explanation: String,
    onReveal: () -> Unit, enabled: Boolean,
) {
    if (!submitted) return
    Surface(Modifier.fillMaxWidth().testTag(tag), shape = ControlShape, color = aiColors.chip) {
        Column(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (correct) "理解正确" else "再想一步", color = aiColors.accent,
                style = MaterialTheme.typography.titleSmall)
            Text(if (correct || revealed) explanation else hint, color = Ink,
                style = MaterialTheme.typography.bodyMedium)
            if (!correct && !revealed) {
                TextButton(onClick = onReveal, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("$tag-reveal")) {
                    Text("查看完整答案")
                }
            }
        }
    }
}

private fun classificationColor(index: Int): Color = listOf(
    Color(0xFF2D7B83),
    Color(0xFFAD6A2F),
    Color(0xFF7A62AE),
)[index % 3]
