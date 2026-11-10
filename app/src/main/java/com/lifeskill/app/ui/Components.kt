package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun GlyphButton(
    glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = Muted, enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }, enabled = enabled) {
        Mark(glyph, if (enabled) tint else Line)
    }
}

@Composable
internal fun PageHeader(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(Glyph.BACK, "返回", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
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
        Text(label, Modifier.padding(start = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(
            onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(), content = content,
        )
    } else {
        OutlinedButton(
            onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink), content = content,
        )
    }
}

@Composable
internal fun Emblem(glyph: Glyph, tint: Color = Gold, background: Color = GoldGround, diameter: Dp = 42.dp) {
    Surface(
        modifier = Modifier.size(diameter), shape = CircleShape, color = background, border = BorderStroke(1.dp, Line),
    ) {
        Box(contentAlignment = Alignment.Center) { Mark(glyph, tint, Modifier.size(diameter * .55f)) }
    }
}

@Composable
internal fun EmptyState(
    title: String, message: String, glyph: Glyph, modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(glyph, Steel, Raised)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.width(26.dp), strokeWidth = 2.dp)
    }
}
