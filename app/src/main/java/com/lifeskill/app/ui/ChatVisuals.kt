package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AiChatHeader(
    title: String, subtitle: String?, onBack: () -> Unit,
    onRestart: (() -> Unit)?, restartEnabled: Boolean, onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (onRestart != null) 96.dp else 48.dp)) {
            GlyphButton(Glyph.BACK, "返回", onBack, background = aiColors.panel)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (onRestart != null) {
            GlyphButton(Glyph.RESTART, "重新学习", onRestart, enabled = restartEnabled, background = aiColors.panel)
        }
        GlyphButton(Glyph.SETTINGS, "设置", onSettings, background = aiColors.panel)
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
    sendEnabled: Boolean, onSend: () -> Unit,
) {
    val colors = aiColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = colors.panel,
            border = BorderStroke(1.dp, if (errorText != null) MaterialTheme.colorScheme.error else colors.outline),
            shadowElevation = 1.dp,
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(start = 15.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                IconButton(onSend, Modifier.size(48.dp).semantics { contentDescription = "发送" }, enabled = sendEnabled) {
                    val fill = if (sendEnabled) Brush.linearGradient(colors.outgoing)
                    else SolidColor(colors.chip)
                    Box(Modifier.size(41.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                        Mark(Glyph.SEND, if (sendEnabled) Color.White else colors.accent, Modifier.size(20.dp))
                    }
                }
            }
        }
        errorText?.let { Text(it, Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
    }
}
