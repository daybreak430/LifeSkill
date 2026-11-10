package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.MapPoint
import com.lifeskill.app.model.SkillMapLayout
import com.lifeskill.app.model.SkillState
import kotlin.math.abs

@Composable
internal fun SkillTreeScreen(tree: Entry, library: Library, latestStudySkillId: String?, app: AppViewModel) {
    val skills = library.childrenOf(tree.id)
    val progress = library.progress(tree.id)
    val edges = remember(skills, library.edges) {
        val ids = skills.map(Entry::id).toSet()
        library.edges.filter { it.skillId in ids && it.prerequisiteId in ids }
    }
    val layout = remember(skills.map(Entry::id), edges) { SkillMapLayout.arrange(skills.map(Entry::id), edges) }
    var showReferences by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(Glyph.BACK, "返回", app::back)
            Text(
                library.byId[tree.parentId]?.name.orEmpty(), Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            EntryMenu(tree, app)
        }
        Text(
            tree.name, Modifier.padding(horizontal = 20.dp), style = LibraryTitle,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("技能地图", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (layout.references.isNotEmpty()) {
                TextButton(onClick = { showReferences = true }) { Text("关联 ${layout.references.size}") }
            }
            Surface(shape = CircleShape, color = GoldGround) {
                Text(
                    "${progress.lit} / ${progress.total} 已点亮", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryAction("新增技能点", Glyph.ADD, { app.add(EntryKind.SKILL, tree.id) }, Modifier.weight(1f))
            AiAction("AI 追加", { app.open(Route(Page.GENERATE, tree.id)) }, Modifier.weight(1f))
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = studyColors.map.first(), shape = PanelShape,
        ) {
            if (skills.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    LibraryEmpty("技能地图还是空的", Glyph.TREE)
                }
            } else {
                SkillMap(skills, layout, library, latestStudySkillId) { app.open(Route.entry(it)) }
            }
        }
    }
    if (showReferences && layout.references.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showReferences = false },
            shape = DialogShape, containerColor = Panel,
            title = { Text("前置关联") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    layout.references.forEachIndexed { index, edge ->
                        Text("${index + 1}　${library.byId[edge.prerequisiteId]?.name} → ${library.byId[edge.skillId]?.name}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReferences = false }) { Text("关闭") } },
        )
    }
}

private data class MapSegment(val edge: Edge, val start: MapPoint, val end: MapPoint, val onTrack: Boolean)

@Composable
private fun SkillMap(
    skills: List<Entry>, layout: SkillMapLayout, library: Library, latestStudySkillId: String?, onOpen: (Entry) -> Unit,
) {
    val nodesByRow = remember(skills, layout) { skills.groupBy { layout.nodes.getValue(it.id).row } }
    val segmentsByRow = remember(layout) {
        layout.connections.flatMap { connection ->
            connection.points.zipWithNext().mapIndexed { index, (start, end) ->
                MapSegment(connection.edge, start, end, onTrack = index > 0)
            }
        }.groupBy { it.start.row }
    }
    val referencesByNode = remember(layout.references) {
        layout.references.flatMapIndexed { index, edge ->
            listOf(edge.prerequisiteId to index + 1, edge.skillId to index + 1)
        }.groupBy({ it.first }, { it.second })
    }
    val ready = skills.filter { library.skillState(it.id) == SkillState.AVAILABLE }
    val focusId = ready.firstOrNull { it.id == latestStudySkillId }?.id ?: ready.minWithOrNull(
        compareBy({ layout.nodes.getValue(it.id).row }, { layout.nodes.getValue(it.id).column }),
    )?.id
    val colors = studyColors
    val density = LocalDensity.current
    val scaleText = density.fontScale.coerceIn(1f, 2f)
    val labelHeight = with(density) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    val cardHeight = 57.dp + labelHeight
    val rowGap = 22.dp
    val lockedLine = Line

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(colors.map)).ignoreHorizontalAndMultiTouch()) {
        val cardWidth = minOf(112.dp * scaleText, (maxWidth - 24.dp).coerceAtLeast(1.dp) / layout.width)
        val diameter = minOf(54.dp, cardWidth * .82f)
        val left = (maxWidth - cardWidth * layout.width) / 2
        LazyColumn(
            Modifier.fillMaxSize().testTag("skill-map"),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            items(layout.rowCount) { row ->
                val rowHeight = if (row < layout.rowCount - 1) cardHeight + rowGap else cardHeight
                Box(Modifier.fillMaxWidth().height(rowHeight)) {
                    // 连线拆成逐行的线段，滚动时只绘制视口内的节点和路径。
                    Canvas(Modifier.matchParentSize()) {
                        val originX = left.toPx()
                        val unitWidth = cardWidth.toPx()
                        val cardBottom = cardHeight.toPx()
                        segmentsByRow[row].orEmpty().forEach { segment ->
                            val fromX = originX + segment.start.column * unitWidth
                            val toX = originX + segment.end.column * unitWidth
                            val middleY = (cardBottom + size.height) / 2
                            val path = Path().apply {
                                moveTo(fromX, if (segment.onTrack) 0f else cardBottom)
                                if (segment.onTrack) lineTo(fromX, cardBottom)
                                cubicTo(fromX, middleY, toX, middleY, toX, size.height)
                            }
                            val from = library.skillState(segment.edge.prerequisiteId)
                            val to = library.skillState(segment.edge.skillId)
                            val onRoute = from == SkillState.LIT && (to == SkillState.LIT || segment.edge.skillId == focusId)
                            drawPath(
                                path, if (onRoute) colors.route else lockedLine,
                                style = Stroke((if (onRoute) 2.dp else 1.5.dp).toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                    nodesByRow[row].orEmpty().forEach { skill ->
                        val point = layout.nodes.getValue(skill.id)
                        MapNode(
                            skill, library.skillState(skill.id), diameter, skill.id == focusId,
                            referencesByNode[skill.id].orEmpty(),
                            { onOpen(skill) },
                            Modifier.absoluteOffset(x = left + cardWidth * (point.column - .5f)).size(cardWidth, cardHeight),
                        )
                    }
                }
            }
        }
    }
}

// 横拖和双指手势落在节点内时也应被取消，避免被节点当作一次点击。纵向拖动交给列表。
private fun Modifier.ignoreHorizontalAndMultiTouch() = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var blocked = false
        var decided = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) blocked = true
            if (!decided && !blocked) {
                val motion = event.changes.first().position - down.position
                if (motion.getDistance() > viewConfiguration.touchSlop) {
                    decided = true
                    blocked = abs(motion.x) > abs(motion.y)
                }
            }
            if (blocked) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun MapNode(
    skill: Entry,
    state: SkillState,
    diameter: Dp,
    focused: Boolean,
    references: List<Int>,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val mapName = remember(skill.name, skill.shortName) { skill.mapName }
    Surface(
        onClick = onOpen, shape = ControlShape,
        modifier = modifier.semantics {
            contentDescription = skill.name
            stateDescription = buildString {
                append(state.label)
                if (focused) append("，下一步")
                if (references.isNotEmpty()) append("，前置关联 ${references.joinToString()}")
            }
        },
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box {
                SkillMedallion(skill.displayIcon, state, diameter, focused)
                if (references.isNotEmpty()) {
                    Surface(Modifier.align(Alignment.BottomEnd), shape = CircleShape, color = Raised) {
                        val label = references.take(2).joinToString("·") + if (references.size > 2) "+" else ""
                        Text(label, Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelMedium, color = Ink)
                    }
                }
            }
            Text(mapName, color = if (state == SkillState.LOCKED) Muted else Ink,
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
