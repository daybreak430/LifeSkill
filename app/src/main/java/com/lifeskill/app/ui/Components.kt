package com.lifeskill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PanelShape = RoundedCornerShape(22.dp)
internal val ControlShape = RoundedCornerShape(16.dp)
internal val DialogShape = RoundedCornerShape(26.dp)

@Composable
internal fun Modifier.panelShadow(): Modifier {
    val shade = studyColors.cardShadow.copy(alpha = .62f)
    return shadow(4.dp, PanelShape, clip = false, ambientColor = shade, spotColor = shade)
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Ground, unfocusedContainerColor = Ground,
    unfocusedBorderColor = Line, disabledBorderColor = Line,
)

@Composable
internal fun DialogRadioChoice(
    label: String, selected: Boolean, enabled: Boolean = true, badge: String? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(ControlShape)
            .background(if (selected) GoldGround else Ground)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        badge?.let { Text(it, color = Gold, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
internal fun SelectionChip(
    label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected, onClick, label = { Text(label) }, enabled = enabled, leadingIcon = leadingIcon,
        shape = CircleShape, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Raised, selectedContainerColor = GoldGround,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
internal fun GlyphButton(
    glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = Ink, enabled: Boolean = true, background: Color = Panel,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp).semantics { contentDescription = label }, enabled = enabled) {
        Surface(Modifier.size(38.dp), shape = CircleShape, color = background) {
            Box(contentAlignment = Alignment.Center) {
                Mark(glyph, if (enabled) tint else Muted, Modifier.size(19.dp))
            }
        }
    }
}

@Composable
internal fun PageHeader(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(Glyph.BACK, "返回", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
internal fun Action(
    label: String, glyph: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        val color = androidx.compose.material3.LocalContentColor.current
        Mark(glyph, color, Modifier.size(17.dp))
        Text(label, Modifier.padding(start = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Button(
        onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = if (primary) ButtonDefaults.buttonColors()
        else ButtonDefaults.buttonColors(containerColor = Raised, contentColor = Ink),
        content = content,
    )
}

@Composable
internal fun Emblem(glyph: Glyph, tint: Color = Gold, background: Color = GoldGround, diameter: Dp = 42.dp) {
    Surface(
        modifier = Modifier.size(diameter), shape = CircleShape, color = background,
    ) {
        Box(contentAlignment = Alignment.Center) { Mark(glyph, tint, Modifier.size(diameter * .55f)) }
    }
}

@Composable
internal fun EmptyState(
    title: String, message: String = "", icon: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.width(26.dp), strokeWidth = 2.dp)
    }
}
