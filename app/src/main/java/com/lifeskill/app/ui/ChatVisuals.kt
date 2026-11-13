package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal fun String.searchSourceLinks(): String = substringAfter("\n\n", missingDelimiterValue = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSourcesSheet(links: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel,
        contentColor = Ink,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("联网搜索来源", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                GlyphButton(Glyph.CLOSE, "关闭来源", onDismiss)
            }
            LessonMarkdown(links)
        }
    }
}

@Composable
internal fun AiChatHeader(
    title: String, subtitle: String?, onBack: () -> Unit,
    onRestart: (() -> Unit)?, restartEnabled: Boolean, onExportConversation: (() -> Unit)?,
    onClearConversation: (() -> Unit)?, onShowUsage: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp)) {
            GlyphButton(Glyph.BACK, "返回", onBack, background = aiColors.panel)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (onRestart != null || onExportConversation != null || onClearConversation != null || onShowUsage != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                GlyphButton(Glyph.MORE, "更多操作", { expanded = true }, background = aiColors.panel)
                DropdownMenu(
                    expanded = expanded, onDismissRequest = { expanded = false },
                    shape = ControlShape, containerColor = Panel,
                ) {
                    if (onShowUsage != null) {
                        DropdownMenuItem(text = { Text("AI 用量") }, onClick = {
                            expanded = false
                            onShowUsage()
                        })
                    }
                    if (onExportConversation != null) {
                        DropdownMenuItem(text = { Text("导出对话") }, onClick = {
                            expanded = false
                            onExportConversation()
                        })
                    }
                    if (onRestart != null) {
                        DropdownMenuItem(text = { Text("重新学习") }, enabled = restartEnabled, onClick = {
                            expanded = false
                            onRestart()
                        })
                    }
                    if (onClearConversation != null) {
                        DropdownMenuItem(text = { Text("清空当前会话") }, onClick = {
                            expanded = false
                            onClearConversation()
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatPill(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, glyph: Glyph? = null,
    trailingGlyph: Glyph? = null,
) {
    val colors = aiColors
    TextButton(
        onClick, modifier, enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = colors.chip, contentColor = colors.accent,
            disabledContainerColor = colors.panel, disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (glyph != null) Mark(glyph, if (enabled) colors.accent else Muted, Modifier.padding(end = 6.dp).size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (trailingGlyph != null) {
            Mark(trailingGlyph, if (enabled) colors.accent else Muted, Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

@Composable
internal fun AiComposer(
    value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, errorText: String?,
    sendEnabled: Boolean, webSearch: Boolean? = null, onToggleSearch: () -> Unit = {}, onSend: () -> Unit,
    deepThinking: Boolean? = null, onToggleDeepThinking: () -> Unit = {},
    attachmentsExpanded: Boolean = false, attachEnabled: Boolean = false, onToggleAttachments: (() -> Unit)? = null,
    attachmentStrip: @Composable () -> Unit = {},
) {
    val colors = aiColors
    val supportsAttachments = onToggleAttachments != null
    val hasTools = deepThinking != null || webSearch != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(if (supportsAttachments) 24.dp else 20.dp),
            color = colors.panel,
            border = BorderStroke(
                1.dp, when {
                    errorText != null -> MaterialTheme.colorScheme.error
                    supportsAttachments -> colors.outline.copy(alpha = .38f)
                    else -> colors.outline
                },
            ),
            shadowElevation = if (supportsAttachments) 0.dp else 1.dp,
        ) {
            Column(Modifier.padding(start = 15.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                attachmentStrip()
                Row(
                    Modifier.heightIn(min = if (hasTools) 36.dp else 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value, onChange,
                        modifier = Modifier.weight(1f).testTag("chat-input").semantics {
                            contentDescription = label
                            if (errorText != null) error(errorText)
                        },
                        enabled = enabled, minLines = 1, maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) Ink else Muted),
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyLarge, color = Muted)
                                innerTextField()
                            }
                        },
                    )
                    if (!hasTools) {
                        if (onToggleAttachments != null) ChatAttachmentButton(attachmentsExpanded, attachEnabled, onToggleAttachments)
                        ChatSendButton(sendEnabled, onSend)
                    }
                }
                if (hasTools) {
                    val toolScroll = rememberScrollState()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).drawWithCache {
                                val fadeWidth = minOf(24.dp.toPx(), size.width / 3)
                                val left = Brush.horizontalGradient(listOf(colors.panel, colors.panel.copy(alpha = 0f)),
                                    endX = fadeWidth)
                                val right = Brush.horizontalGradient(listOf(colors.panel.copy(alpha = 0f), colors.panel),
                                    startX = size.width - fadeWidth, endX = size.width)
                                val leftToRight = layoutDirection == LayoutDirection.Ltr
                                onDrawWithContent {
                                    drawContent()
                                    // 渐隐只覆盖可滑动的开关区域，附件和发送按钮始终保留独立触控区域。
                                    if (fadeWidth > 0f) {
                                        val fadeSize = Size(fadeWidth, size.height)
                                        if (if (leftToRight) toolScroll.canScrollBackward else toolScroll.canScrollForward) {
                                            drawRect(left, size = fadeSize)
                                        }
                                        if (if (leftToRight) toolScroll.canScrollForward else toolScroll.canScrollBackward) {
                                            drawRect(right, topLeft = Offset(size.width - fadeWidth, 0f), size = fadeSize)
                                        }
                                    }
                                }
                            }.horizontalScroll(toolScroll).padding(end = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (webSearch != null) ComposerTool("联网搜索", Glyph.GLOBE, webSearch, enabled, onToggleSearch)
                            if (deepThinking != null) ComposerTool("深度思考", Glyph.THINK, deepThinking, enabled, onToggleDeepThinking)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onToggleAttachments != null) ChatAttachmentButton(attachmentsExpanded, attachEnabled, onToggleAttachments)
                            ChatSendButton(sendEnabled, onSend)
                        }
                    }
                }
            }
        }
        errorText?.let { Text(it, Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ComposerTool(label: String, glyph: Glyph, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = aiColors
    val tint = if (selected) colors.accent else Muted
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled, shape = CircleShape,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Mark(glyph, if (enabled) tint else Muted.copy(alpha = .5f), Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.panel, labelColor = Muted, selectedContainerColor = colors.chip, selectedLabelColor = colors.accent,
        ),
        border = BorderStroke(1.dp, (if (selected) colors.accent else colors.outline).copy(alpha = if (enabled) .45f else .20f)),
    )
}

@Composable
private fun ChatAttachmentButton(expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = if (expanded) "收起附件选项" else "添加附件"
    val colors = aiColors
    val fill = colors.chip.copy(alpha = if (enabled && expanded) 1f else .6f)
    ChatComposerButton(if (expanded) Glyph.CLOSE else Glyph.ADD, label, enabled, onClick,
        SolidColor(fill), if (enabled) colors.accent else Muted)
}

@Composable
private fun ChatSendButton(enabled: Boolean, onSend: () -> Unit) {
    val colors = aiColors
    val fill = if (enabled) Brush.linearGradient(colors.outgoing) else SolidColor(colors.chip)
    ChatComposerButton(Glyph.SEND, "发送", enabled, onSend, fill, if (enabled) Color.White else colors.accent)
}

@Composable
private fun ChatComposerButton(glyph: Glyph, label: String, enabled: Boolean, onClick: () -> Unit, fill: Brush, tint: Color) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = label }, enabled = enabled) {
        Box(Modifier.size(41.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Mark(glyph, tint, Modifier.size(20.dp))
        }
    }
}
