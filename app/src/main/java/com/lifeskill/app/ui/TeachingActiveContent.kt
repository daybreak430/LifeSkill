package com.lifeskill.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.*
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

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
    val hintLevels = block.hintLevels
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp).testTag("flip-card")
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            }
            .semantics { stateDescription = if (back) "答案面" else "问题面" },
        shape = ControlShape,
        color = if (rotation > 90f) aiColors.chip else Ground,
        border = BorderStroke(1.dp, if (rotation > 90f) aiColors.accent.copy(alpha = .45f) else Line),
    ) {
        val showBack = rotation > 90f
        // 两面同时参与测量，以较高的一面确定卡片高度；切面时不改变布局，也不暴露隐藏面的读屏节点。
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            listOf(false, true).forEach { answerFace ->
                val visible = showBack == answerFace
                Column(
                    Modifier.fillMaxWidth().zIndex(if (visible) 1f else 0f).alpha(if (visible) 1f else 0f)
                        .then(if (!visible) Modifier.clearAndSetSemantics { } else Modifier)
                        .graphicsLayer { if (answerFace) rotationY = 180f }.padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (answerFace) "答案" else "先回忆", color = aiColors.accent,
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (answerFace) block.answer else block.prompt,
                        style = if (answerFace) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                        color = Ink)
                    Spacer(Modifier.height(14.dp))
                    Text(if (answerFace) "核对后翻回继续" else "想好后翻开核对",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onAction(TeachingAction.Flip) },
                        enabled = enabled && visible,
                        modifier = Modifier.then(if (visible) Modifier.testTag("flip-card-turn") else Modifier),
                    ) {
                        Mark(Glyph.RESTART, MaterialTheme.colorScheme.onPrimary, Modifier.size(17.dp))
                        Text(if (answerFace) "翻回" else "翻面", Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
    }
    // 提示放在卡片外侧，翻面前逐级展开，避免把答案面的提示当成解题线索。
    if (!back && hintLevels.isNotEmpty()) {
        val shown = hintLevels.take((state.hintLevel + 1).coerceAtMost(hintLevels.size))
        if (state.hintLevel > 0) Text(
            shown.joinToString("\n"), color = Ink, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.hintLevel < hintLevels.lastIndex) TextButton(
            onClick = { onAction(TeachingAction.Hint) }, enabled = enabled,
            modifier = Modifier.testTag("flip-hint"),
        ) { Text(if (state.hintLevel == 0) "看提示" else "再给一点提示") }
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
            // 可见数字只有 18dp，触控框保留 48dp；边缘热点向内约束，避免被图像裁剪吞掉点击。
            val x = (maxWidth * (hotspot.horizontalPercent.toFloat() / 100f) - 24.dp)
                .coerceIn(0.dp, (maxWidth - 48.dp).coerceAtLeast(0.dp))
            val y = (maxHeight * (hotspot.verticalPercent.toFloat() / 100f) - 24.dp)
                .coerceIn(0.dp, (maxHeight - 48.dp).coerceAtLeast(0.dp))
            val active = state.selection == hotspot.id
            val markerColor by animateColorAsState(if (active) Gold else aiColors.accent,
                tween(180), label = "热点选中颜色")
            Surface(
                onClick = { onSelect(hotspot.id) },
                enabled = enabled,
                modifier = Modifier.offset(x, y).size(48.dp).testTag("hotspot-${hotspot.id}")
                    .semantics {
                        selected = active
                        contentDescription = "热点${index + 1}：${hotspot.label}"
                    },
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        Modifier.size(18.dp).testTag("hotspot-marker-${hotspot.id}"),
                        shape = CircleShape,
                        color = markerColor,
                        border = BorderStroke(1.dp, Panel),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color.White, fontSize = 10.sp,
                                lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
