package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.Progress

private val CardShape = RoundedCornerShape(14.dp)
private val ActionShape = RoundedCornerShape(12.dp)
internal val LibraryTitle = TextStyle(
    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp,
)

@Composable
internal fun ContinueStudyCard(skill: Entry, tree: Entry, progress: Progress, locked: Boolean, onContinue: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = Panel, contentColor = Ink,
        shape = CardShape, border = BorderStroke(1.dp, Line),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                LibrarySection("上次学到", style = MaterialTheme.typography.labelLarge, color = Muted)
                Text(skill.name, style = LibraryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    tree.name, style = MaterialTheme.typography.bodyMedium, color = Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(progress.label, style = MaterialTheme.typography.labelLarge)
                LibraryAction(
                    if (locked) "查看技能" else "继续学习", null, onContinue, Modifier.padding(top = 4.dp),
                    primary = true, trailing = true,
                )
            }
            if (LocalDensity.current.fontScale < 1.3f) {
                StudyMotif(Modifier.weight(.48f, fill = false).widthIn(max = 126.dp).fillMaxWidth().aspectRatio(.95f))
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
) {
    val content = if (primary) Panel else Ink
    val icon = if (primary) content else Gold
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button },
        shape = ActionShape,
        color = when {
            primary -> Ink
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
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = style, color = color)
        HorizontalDivider(Modifier.weight(1f), color = Line)
        action()
    }
}

@Composable
internal fun DirectionCard(entry: Entry, progress: Progress, treeCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        border = BorderStroke(1.dp, Line), shape = CardShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Emblem(entry.displayIcon.glyph, background = Raised, diameter = 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (progress.total > 0) progress.label else "$treeCount 棵技能树",
                    style = MaterialTheme.typography.labelMedium, color = Muted,
                )
            }
        }
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
private fun SegmentedProgress(progress: Progress) {
    val active = Gold
    val track = Line
    val segments = progress.total.coerceAtMost(12)
    // 大树用固定上限的分段，确切进度由下方文字呈现。
    val filled = (progress.fraction * segments).toInt()
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        val gap = 3.dp.toPx().coerceAtMost(size.width / (2 * segments))
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { index ->
            drawRoundRect(
                color = if (index < filled) active else track,
                topLeft = Offset(index * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun StudyMotif(modifier: Modifier) {
    val accent = Gold
    val branch = Gold.copy(alpha = .43f)
    val surface = Panel
    Canvas(modifier) {
        val nodes = listOf(
            Offset(.5f, .12f), Offset(.5f, .36f),
            Offset(.28f, .60f), Offset(.72f, .60f),
            Offset(.11f, .87f), Offset(.37f, .87f), Offset(.63f, .87f), Offset(.89f, .87f),
        ).map { Offset(it.x * size.width, it.y * size.height) }
        nodes.forEachIndexed { index, node ->
            if (index > 0) drawLine(branch, nodes[index / 2], node, 1.5.dp.toPx())
        }
        val radius = size.minDimension * .056f
        nodes.forEachIndexed { index, node ->
            drawCircle(if (index == 1) accent else surface, radius, node)
            drawCircle(if (index == 1) accent else branch, radius, node, style = Stroke(1.5.dp.toPx()))
        }
    }
}
