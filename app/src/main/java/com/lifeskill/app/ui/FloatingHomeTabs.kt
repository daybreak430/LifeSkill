package com.lifeskill.app.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

internal val HomeTabClearance = 94.dp
internal val liveTabGlass get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal fun FloatingHomeTabs(page: Page, backdrop: Backdrop, dark: Boolean, select: (Page) -> Unit, modifier: Modifier) {
    val shape = CircleShape
    val tint = Panel.copy(alpha = if (liveTabGlass) { if (dark) .64f else .57f } else .96f)
    val edge = if (dark) Color.White.copy(alpha = .19f) else Color.White.copy(alpha = .85f)
    val glass = if (liveTabGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = {
                vibrancy()
                blur(9.dp.toPx())
                lens(16.dp.toPx(), 19.dp.toPx(), depthEffect = true, chromaticAberration = true)
            },
            shadow = { Shadow(radius = 16.dp, color = Color.Black.copy(alpha = .16f)) },
            onDrawSurface = { drawRect(tint) },
        )
    } else Modifier.shadow(6.dp, shape).background(tint, shape)
    val indicator = if (dark) listOf(Color.White.copy(alpha = .19f), GoldGround.copy(alpha = .56f))
        else listOf(Color.White.copy(alpha = .95f), GoldGround.copy(alpha = .79f))
    val slide by animateFloatAsState(
        if (page == Page.ME) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "标签选中胶囊",
    )
    BoxWithConstraints(
        modifier.padding(horizontal = 24.dp, vertical = 10.dp).widthIn(max = 284.dp).fillMaxWidth()
            .then(glass).border(1.dp, edge, shape),
    ) {
        val tabWidth = (maxWidth - 10.dp) / 2
        Box(Modifier.matchParentSize().padding(5.dp)) {
            Box(Modifier.offset { IntOffset((tabWidth.toPx() * slide).toInt(), 0) }.width(tabWidth).fillMaxHeight()
                .background(Brush.verticalGradient(indicator), shape)
                .border(1.dp, edge.copy(alpha = edge.alpha * .65f), shape))
        }
        Row(Modifier.fillMaxWidth().padding(5.dp).selectableGroup()) {
            HomeTab("学习", Glyph.BOOK, "tab-learning", page == Page.HOME) { select(Page.HOME) }
            HomeTab("我的", Glyph.PERSON, "tab-personal", page == Page.ME) { select(Page.ME) }
        }
    }
}

@Composable
private fun RowScope.HomeTab(label: String, icon: Glyph, tag: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Gold else Ink
    Column(
        Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape)
            .selectable(selected, role = Role.Tab, onClick = onClick).testTag(tag).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Mark(icon, color, Modifier.size(21.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
