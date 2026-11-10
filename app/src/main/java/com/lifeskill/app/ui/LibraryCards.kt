package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.Progress

internal val LibraryTitle = TextStyle(
    fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 35.sp,
)

@Composable
internal fun ContinueStudyCard(skill: Entry, tree: Entry, progress: Progress, locked: Boolean, onContinue: () -> Unit) {
    val colors = studyColors
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val heroShape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(Modifier.matchParentSize().padding(horizontal = 14.dp).padding(top = 11.dp, bottom = 16.dp)
            .rotate(-3f)
            .background(colors.heroLayer, heroShape))
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 15.dp).clip(heroShape).background(Brush.linearGradient(colors.hero)),
        ) {
            // 窄屏和大字号优先保留标题、进度与操作空间。
            val artWidth = if (maxWidth >= 328.dp && fontScale < 1.3f) minOf(maxWidth * .41f, 200.dp) else 0.dp
            if (artWidth > 0.dp) {
                StudyMotif(skill.displayIcon.glyph, Modifier.align(Alignment.CenterEnd).width(artWidth).height(156.dp))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = maxOf(artWidth + 8.dp, 20.dp), top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    tree.name, style = MaterialTheme.typography.bodyMedium, color = Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(skill.name, style = LibraryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SegmentedProgress(progress, Modifier.weight(1f), active = colors.route)
                    Text(progress.label, style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                LibraryAction(
                    if (locked) "查看技能" else "继续学习", null, onContinue,
                    Modifier.padding(top = 4.dp).widthIn(max = 158.dp * fontScale),
                    primary = true, trailing = true, primaryColor = Gold,
                )
            }
        }
    }
}

@Composable
internal fun LibraryAction(
    label: String,
    glyph: Glyph?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    trailing: Boolean = false,
    primaryColor: Color = Ink,
) {
    val content = if (primary) Panel else Ink
    val icon = if (primary) content else Gold
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button },
        shape = ControlShape,
        color = when {
            primary -> primaryColor
            trailing -> Raised
            else -> Panel
        },
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) Mark(glyph, icon, Modifier.padding(end = 8.dp).size(19.dp))
            Text(
                label, modifier = if (trailing) Modifier.weight(1f) else Modifier,
                style = MaterialTheme.typography.titleMedium,
            )
            if (trailing) Mark(Glyph.NEXT, icon, Modifier.padding(start = 6.dp).size(18.dp))
        }
    }
}

@Composable
internal fun LibrarySection(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        action()
    }
}

@Composable
internal fun DirectionCard(entry: Entry, progress: Progress, onClick: () -> Unit) {
    val tint = entryTint(entry)
    val colors = studyColors
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        shape = PanelShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp).panelShadow()
            .semantics {
                role = Role.Button
                stateDescription = "已学会 ${progress.lit} 个技能点，共 ${progress.total} 个"
            },
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(.82f), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.widthIn(max = 146.dp).fillMaxWidth().aspectRatio(2f)) {
                    val stroke = 4.5.dp.toPx()
                    val diameter = minOf(size.width - stroke, (size.height - stroke) * 2).coerceAtLeast(0f)
                    val origin = Offset((size.width - diameter) / 2, stroke / 2)
                    val bounds = Size(diameter, diameter)
                    val arc = Stroke(stroke, cap = StrokeCap.Round)
                    drawArc(colors.progressTrack, 180f, 180f, false, origin, bounds, style = arc)
                    if (progress.lit > 0) {
                        drawArc(tint, 180f, progress.fraction * 180f, false, origin, bounds, style = arc)
                    }
                }
                Mark(entry.displayIcon.glyph, tint, Modifier.padding(bottom = 4.dp).size(21.dp))
            }
            Text(
                entry.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${progress.lit}/${progress.total}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"), color = Muted,
            )
        }
    }
}

@Composable
internal fun entryTint(entry: Entry): Color {
    val colors = studyColors
    return when (entry.displayIcon) {
        EntryIcon.WORK, EntryIcon.CODE, EntryIcon.CHART, EntryIcon.BOOK -> colors.iconViolet
        EntryIcon.DIAMOND, EntryIcon.TREE, EntryIcon.TARGET, EntryIcon.SPEECH -> colors.iconBlue
        EntryIcon.SIDE, EntryIcon.PRACTICAL, EntryIcon.LEAF, EntryIcon.TOOLS -> colors.iconGreen
        EntryIcon.HOBBY, EntryIcon.MUSIC, EntryIcon.CAMERA -> colors.iconOrange
        EntryIcon.EXPLORE -> listOf(colors.iconBlue, colors.iconViolet, colors.iconGreen, colors.iconOrange)
            .get((entry.id.hashCode() and Int.MAX_VALUE) % 4)
    }
}

@Composable
internal fun SkillTreeCard(tree: Entry, progress: Progress, latest: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        shape = PanelShape, modifier = Modifier.fillMaxWidth().panelShadow(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = entryTint(tree)
            Emblem(tree.displayIcon.glyph, tint = tint, background = tint.copy(alpha = .12f), diameter = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (latest) StudyBookmark()
                Text(tree.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress.total > 0) SegmentedProgress(progress)
                Text(
                    if (progress.total > 0) progress.label else "暂无技能点",
                    style = MaterialTheme.typography.labelMedium, color = Muted,
                )
            }
            Mark(Glyph.NEXT, Gold, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun LibraryEmpty(title: String, glyph: Glyph) {
    EmptyState(title) {
        Emblem(glyph, background = Raised, diameter = 40.dp)
    }
}

private val Progress.label get() = "$lit / $total 已点亮"

@Composable
private fun StudyBookmark() {
    Surface(shape = CircleShape, color = GoldGround) {
        Text(
            "上次学习", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SegmentedProgress(progress: Progress, modifier: Modifier = Modifier, active: Color = Gold) {
    val track = Line
    val segments = progress.total.coerceAtMost(12)
    if (segments == 0) return
    // 大树用固定上限的分段，确切进度由下方文字呈现。
    val filled = (progress.fraction * segments).toInt()
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val gap = 3.dp.toPx().coerceAtMost(size.width / (2 * segments))
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { index ->
            drawRoundRect(
                color = if (index < filled) active else track,
                topLeft = Offset(index * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
    }
}

@Composable
private fun StudyMotif(glyph: Glyph, modifier: Modifier) {
    val colors = studyColors
    val panel = Panel
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = 35.dp.toPx()
            val satellites = listOf(Offset(size.width * .22f, size.height * .27f),
                Offset(size.width * .79f, size.height * .18f), Offset(size.width * .77f, size.height * .81f))
            // 装饰只突出当前技能图标，外围圆点用于表现技能树意象。
            drawCircle(Brush.radialGradient(listOf(colors.heroGlow, Color.Transparent), center, radius * 2.18f),
                radius = radius * 2.18f)
            drawCircle(panel.copy(alpha = .44f), radius * 1.30f, style = Stroke(1.2.dp.toPx()))
            satellites.forEach { node ->
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    cubicTo(center.x, node.y, (center.x + node.x) / 2, node.y, node.x, node.y)
                }
                drawPath(path, colors.route.copy(alpha = .34f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(colors.route.copy(alpha = .13f), 10.dp.toPx(), node)
                drawCircle(panel.copy(alpha = .89f), 6.dp.toPx(), node)
                drawCircle(colors.route.copy(alpha = .72f), 2.3.dp.toPx(), node)
            }
        }
        Box(
            Modifier.size(66.dp).background(Brush.linearGradient(colors.heroEmblem), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Mark(glyph, Color.White, Modifier.size(31.dp))
        }
    }
}
