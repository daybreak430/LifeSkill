package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.HomeStudy
import com.lifeskill.app.model.HomeStudyState
import com.lifeskill.app.model.Progress

internal val LibraryTitle = TextStyle(
    fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 35.sp,
)

@Composable
internal fun HomeStudyCard(
    study: HomeStudy?, modifier: Modifier = Modifier, onAction: () -> Unit,
) {
    val colors = studyColors
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val heroShape = RoundedCornerShape(26.dp)
    val title = when (study?.state) {
        null -> "想学什么？"
        HomeStudyState.COMPLETED -> study.tree.name
        else -> study.skill.name
    }
    val context = when (study?.state) {
        null -> null
        HomeStudyState.NEXT -> "下一步 · ${study.tree.name}"
        HomeStudyState.COMPLETED -> "已完成"
        else -> study.tree.name
    }
    val action = when (study?.state) {
        null -> "AI 生成技能树"
        HomeStudyState.NEXT -> "开始学习"
        HomeStudyState.LOCKED -> "查看技能"
        HomeStudyState.COMPLETED -> "查看技能地图"
        HomeStudyState.RESUME -> "继续学习"
    }
    Box(modifier.fillMaxWidth().clipToBounds()) {
        Box(Modifier.matchParentSize().padding(horizontal = 14.dp).padding(top = 11.dp, bottom = 16.dp)
            .rotate(-3f)
            .background(colors.heroLayer, heroShape))
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 15.dp).clip(heroShape).background(Brush.linearGradient(colors.hero)),
        ) {
            val wideArt = maxWidth >= 328.dp && fontScale < 1.3f
            val compactArt = !wideArt && maxWidth >= 256.dp
            val artWidth = minOf(maxWidth * .41f, 200.dp)
            val compactWidth = minOf(maxWidth * (if (fontScale >= 1.6f) .19f else .25f), 92.dp)
            val glyph = when (study?.state) {
                null -> Glyph.TREE
                HomeStudyState.COMPLETED -> Glyph.CHECK
                else -> study.skill.displayIcon.glyph
            }
            if (wideArt) {
                StudyMotif(glyph, Modifier.align(Alignment.CenterEnd).width(artWidth).height(156.dp))
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 166.dp)
                    .padding(start = 20.dp, end = if (wideArt) artWidth + 8.dp else 20.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
            ) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (context != null && study != null) {
                            val highlighted = study.state == HomeStudyState.NEXT || study.state == HomeStudyState.COMPLETED
                            Text(
                                context, style = MaterialTheme.typography.bodyMedium, color = if (highlighted) Gold else Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(title, style = LibraryTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (compactArt) {
                        // 小插图只与标题同排，进度和按钮仍能使用整张卡片的宽度。
                        StudyMotif(glyph, Modifier.width(compactWidth).height(70.dp), emblemSize = 38.dp)
                    }
                }
                if (study != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SegmentedProgress(study.progress, Modifier.weight(1f), active = colors.route)
                        Text(study.progress.label, style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                }
                LibraryAction(
                    action, null, onAction,
                    Modifier.padding(top = 4.dp).widthIn(max = 178.dp * fontScale),
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
internal fun DirectionCard(entry: Entry, progress: Progress, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = entryTint(entry)
    val colors = studyColors
    // 返回首页时从这张卡上次呈现的进度起播，首次出现直接显示现有进度。
    var seenFraction by rememberSaveable { mutableFloatStateOf(progress.fraction) }
    val sweep = remember { Animatable(seenFraction) }
    LaunchedEffect(progress.fraction) {
        if (sweep.value != progress.fraction) sweep.animateTo(progress.fraction, tween(260))
        seenFraction = progress.fraction
    }
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        shape = PanelShape,
        modifier = modifier.fillMaxWidth().panelShadow()
            .semantics {
                role = Role.Button
                stateDescription = "已学会 ${progress.lit} 个技能点，共 ${progress.total} 个"
            },
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 4.dp.toPx()
                    val diameter = minOf(size.width - stroke, (size.height - stroke) * 2).coerceAtLeast(0f)
                    val origin = Offset((size.width - diameter) / 2, stroke / 2)
                    val bounds = Size(diameter, diameter)
                    val arc = Stroke(stroke, cap = StrokeCap.Round)
                    drawArc(colors.progressTrack, 180f, 180f, false, origin, bounds, style = arc)
                    if (sweep.value > 0f) {
                        drawArc(tint, 180f, sweep.value * 180f, false, origin, bounds, style = arc)
                    }
                }
                Mark(entry.displayIcon.glyph, tint, Modifier.padding(bottom = 2.dp).size(19.dp))
            }
            Text(
                entry.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${progress.lit}/${progress.total}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"), color = Muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun CompactDirectionCard(
    entry: Entry, modifier: Modifier = Modifier, onClick: () -> Unit, createTree: (() -> Unit)?,
) {
    val tint = entryTint(entry)
    Surface(modifier.fillMaxWidth().panelShadow(), color = Panel, contentColor = Ink, shape = PanelShape) {
        val open = Modifier.clickable(role = Role.Button, onClick = onClick)
        Column(
            (if (createTree == null) open else Modifier).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                (if (createTree == null) Modifier else open).fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Emblem(entry.displayIcon.glyph, tint, tint.copy(alpha = .12f), diameter = 30.dp)
                Text(
                    entry.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (createTree == null) {
                Text("暂无技能点", Modifier.padding(start = 41.dp, bottom = 9.dp),
                    color = Muted, style = MaterialTheme.typography.labelMedium)
            } else {
                TextButton(onClick = createTree, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Mark(Glyph.ADD, Gold, Modifier.padding(end = 5.dp).size(15.dp))
                    Text(
                        "创建技能树",
                        Modifier.clearAndSetSemantics { contentDescription = "为${entry.name}创建技能树" },
                        style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
internal fun SkillTreeCard(tree: Entry, progress: Progress, latest: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Panel, contentColor = Ink,
        shape = PanelShape, modifier = modifier.fillMaxWidth().panelShadow(),
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
private fun StudyMotif(glyph: Glyph, modifier: Modifier, emblemSize: Dp = 66.dp) {
    val colors = studyColors
    val panel = Panel
    val grid = Steel.copy(alpha = .18f)
    BoxWithConstraints(modifier.testTag("home-study-motif"), contentAlignment = Alignment.Center) {
        val diameter = minOf(emblemSize, minOf(maxWidth, maxHeight) * .57f)
        val scale = diameter / 66.dp
        Canvas(Modifier.matchParentSize()) {
            // 按插图的短边缩放外圈、光晕和卫星点，让所有图形都落在同一个圆形范围内。
            val outerRadius = size.minDimension * .46f
            val nodeRadius = (10.dp * scale).toPx()
            val orbit = outerRadius - nodeRadius
            val satellites = listOf(Offset(-.87f, -.50f), Offset(.68f, -.73f), Offset(.61f, .79f))
                .map { center + it * orbit }
            val glow = Brush.radialGradient(listOf(colors.heroGlow, colors.heroGlow.copy(alpha = 0f)), center, outerRadius)
            drawCircle(glow, radius = outerRadius)
            val step = 15.dp.toPx()
            val gridSteps = (outerRadius / step).toInt()
            for (row in -gridSteps..gridSteps) {
                for (column in -gridSteps..gridSteps) {
                    val offset = Offset(column * step, row * step)
                    if (offset.getDistance() < orbit) drawCircle(grid, .55.dp.toPx(), center + offset)
                }
            }
            drawCircle(colors.route.copy(alpha = .16f), outerRadius, style = Stroke(.75.dp.toPx()))
            drawCircle(panel.copy(alpha = .44f), orbit, style = Stroke(1.2.dp.toPx()))
            satellites.forEach { node ->
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    cubicTo(center.x, node.y, (center.x + node.x) / 2, node.y, node.x, node.y)
                }
                drawPath(path, colors.route.copy(alpha = .34f), style = Stroke((1.4.dp * scale).toPx(), cap = StrokeCap.Round))
                drawCircle(colors.route.copy(alpha = .13f), nodeRadius, node)
                drawCircle(panel.copy(alpha = .89f), (6.dp * scale).toPx(), node)
                drawCircle(colors.route.copy(alpha = .72f), (2.3.dp * scale).toPx(), node)
            }
        }
        Box(
            Modifier.size(diameter).background(Brush.linearGradient(colors.heroEmblem), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Mark(glyph, Color.White, Modifier.size(31.dp * scale))
        }
    }
}
