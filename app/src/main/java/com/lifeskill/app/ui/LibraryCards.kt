package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.Progress

private val CardShape = RoundedCornerShape(14.dp)
private val ActionShape = RoundedCornerShape(12.dp)
internal val LibraryTitle = TextStyle(
    fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 35.sp,
)

@Composable
internal fun ContinueStudyCard(skill: Entry, tree: Entry, progress: Progress, locked: Boolean, onContinue: () -> Unit) {
    val colors = studyColors
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val heroShape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().padding(horizontal = 12.dp).padding(bottom = 7.dp)
            .background(colors.heroLayer, heroShape))
        Box(Modifier.fillMaxWidth().padding(top = 7.dp).clip(heroShape).background(Brush.linearGradient(colors.hero))) {
            if (fontScale < 1.3f) StudyMotif(Modifier.align(Alignment.TopEnd).size(116.dp, 82.dp))
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tree.name, style = MaterialTheme.typography.bodyMedium, color = Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(skill.name, style = LibraryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SegmentedProgress(progress, Modifier.weight(1f), active = colors.route)
                    Text(progress.label, style = MaterialTheme.typography.labelLarge, color = Ink)
                }
                LibraryAction(
                    if (locked) "查看技能" else "继续学习", null, onContinue,
                    Modifier.padding(top = 2.dp).widthIn(max = 164.dp * fontScale),
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
        shape = ActionShape,
        color = when {
            primary -> primaryColor
            trailing -> Raised
            else -> Panel
        },
        contentColor = content,
        border = if (primary) null else BorderStroke(1.dp, Line),
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
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Ink,
    divider: Boolean = true,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = style, color = color)
        if (divider) HorizontalDivider(Modifier.weight(1f), color = Line)
        else Spacer(Modifier.weight(1f))
        action()
    }
}

@Composable
internal fun DirectionCard(entry: Entry, progress: Progress, treeCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        border = BorderStroke(1.dp, Line), shape = CardShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Mark(entry.displayIcon.glyph, directionTint(entry), Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Mark(Glyph.NEXT, Muted, Modifier.size(14.dp))
            }
            Text(
                if (progress.total > 0) progress.label else "$treeCount 棵技能树",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }
    }
}

@Composable
private fun directionTint(entry: Entry): Color {
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
        border = BorderStroke(1.dp, if (latest) Gold.copy(alpha = .55f) else Line),
        shape = CardShape, modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            if (latest) {
                val ribbon = Gold
                Canvas(Modifier.matchParentSize()) {
                    drawRect(ribbon, size = Size(3.dp.toPx(), size.height))
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Emblem(tree.displayIcon.glyph, background = Raised, diameter = 48.dp)
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
}

@Composable
internal fun LibraryEmpty(title: String, glyph: Glyph) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Emblem(glyph, background = Raised, diameter = 40.dp)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

private val Progress.label get() = "$lit / $total 已点亮"

@Composable
private fun StudyBookmark() {
    Surface(shape = RoundedCornerShape(4.dp), color = GoldGround) {
        Text(
            "上次学习", modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
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
private fun StudyMotif(modifier: Modifier) {
    val branch = studyColors.route.copy(alpha = .17f)
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val motif = Path().apply {
            moveTo(width * .16f, height * .63f)
            cubicTo(width * .44f, height * .16f, width * .77f, height * .69f, width * .80f, 0f)
            moveTo(width * .37f, height * .45f)
            cubicTo(width * .54f, height * .34f, width * .60f, height * .27f, width * .59f, 0f)
            moveTo(width * .46f, height * .41f)
            cubicTo(width * .70f, height * .46f, width * .74f, height * .81f, width, height * .77f)
        }
        drawPath(motif, branch, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
