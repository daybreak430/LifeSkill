package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.MapPoint
import com.lifeskill.app.model.SkillMapLayout
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.SkillUnlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun SkillTreeScreen(
    tree: Entry, library: Library, latestStudySkillId: String?, app: AppViewModel,
    unlock: SkillUnlock?, active: Boolean,
) {
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
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = studyColors.map.first(), shape = PanelShape,
        ) {
            if (skills.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    LibraryEmpty("技能地图还是空的", Glyph.TREE)
                }
            } else {
                SkillMap(skills, layout, library, latestStudySkillId, unlock, active, app::finishUnlock) {
                    app.open(Route.entry(it))
                }
            }
        }
    }
    if (active && showReferences && layout.references.isNotEmpty()) {
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

private data class MapSegment(val edge: Edge, val start: MapPoint, val end: MapPoint, val index: Int, val count: Int)

@Composable
private fun SkillMap(
    skills: List<Entry>, layout: SkillMapLayout, library: Library, latestStudySkillId: String?,
    unlock: SkillUnlock?, active: Boolean, onUnlockFinished: (String) -> Unit, onOpen: (Entry) -> Unit,
) {
    val listState = rememberLazyListState()
    val clock = remember(unlock?.key) { Animatable(if (unlock == null) 1f else 0f) }
    LaunchedEffect(unlock?.key, active) {
        if (!active || unlock == null) return@LaunchedEffect
        clock.snapTo(0f)
        launch {
            val source = layout.nodes[unlock.skillId]?.row ?: return@launch
            val visible = snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.first { it.isNotEmpty() }
            val next = unlock.availableChildren.mapNotNull { layout.nodes[it]?.row }.minOrNull()
            if (source !in visible || next != null && next !in visible) listState.animateScrollToItem(source)
        }
        delay(MAP_UNLOCK_DELAY_MS)
        clock.animateTo(1f, tween(MAP_UNLOCK_DURATION_MS, easing = LinearEasing))
        onUnlockFinished(unlock.key)
    }
    val nodesByRow = remember(skills, layout) { skills.groupBy { layout.nodes.getValue(it.id).row } }
    val segmentsByRow = remember(layout) {
        layout.connections.flatMap { connection ->
            connection.points.zipWithNext().mapIndexed { index, (start, end) ->
                MapSegment(connection.edge, start, end, index, connection.points.size - 1)
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
    val scaleText = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val mutedLine = lerp(Line, Steel, .48f)
    val focusLine = lerp(colors.route, Gold, .42f)

    BoxWithConstraints(Modifier.fillMaxWidth().background(Brush.verticalGradient(colors.map)).ignoreHorizontalAndMultiTouch()) {
        val cardWidth = minOf(140.dp * scaleText, (maxWidth - 28.dp).coerceAtLeast(1.dp) / layout.width)
        val diameter = minOf(66.dp, cardWidth * .82f)
        val left = (maxWidth - cardWidth * layout.width) / 2
        LazyColumn(
            Modifier.fillMaxWidth().testTag("skill-map"),
            state = listState,
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            items(layout.rowCount) { row ->
                val gap = if (row < layout.rowCount - 1) 22.dp else 0.dp
                Box(Modifier.fillMaxWidth()) {
                    // 每行由实际节点和文字撑开，连线从这一行的文字下方开始。
                    Canvas(Modifier.matchParentSize()) {
                        val originX = left.toPx()
                        val unitWidth = cardWidth.toPx()
                        val cardBottom = size.height - gap.toPx()
                        segmentsByRow[row].orEmpty().forEach { segment ->
                            val fromX = originX + segment.start.column * unitWidth
                            val toX = originX + segment.end.column * unitWidth
                            val middleY = (cardBottom + size.height) / 2
                            val path = Path().apply {
                                moveTo(fromX, if (segment.index > 0) 0f else cardBottom)
                                if (segment.index > 0) lineTo(fromX, cardBottom)
                                cubicTo(fromX, middleY, toX, middleY, toX, size.height)
                            }
                            val from = library.skillState(segment.edge.prerequisiteId)
                            val to = library.skillState(segment.edge.skillId)
                            val (tint, stroke) = when {
                                from == SkillState.LIT && segment.edge.skillId == focusId -> focusLine to 2.8.dp
                                from == SkillState.LIT && to != SkillState.LOCKED -> colors.route to 2.4.dp
                                else -> mutedLine to 1.8.dp
                            }
                            val revealing = unlock != null && segment.edge.prerequisiteId == unlock.skillId &&
                                segment.edge.skillId in unlock.availableChildren && to == SkillState.AVAILABLE
                            if (revealing) {
                                drawPath(path, mutedLine, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                                // 跨行路径按顺序填充，到达末段后再打开下游的锁。
                                val portion = (motionPhase(clock.value, .25f, .76f) * segment.count - segment.index).coerceIn(0f, 1f)
                                if (portion > 0f) {
                                    val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
                                    val filled = Path()
                                    measure.getSegment(0f, measure.length * portion, filled, startWithMoveTo = true)
                                    drawPath(filled, tint, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
                                }
                            } else {
                                drawPath(path, tint, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                    nodesByRow[row].orEmpty().forEach { skill ->
                        val point = layout.nodes.getValue(skill.id)
                        MapNode(
                            skill, library.skillState(skill.id), diameter, skill.id == focusId,
                            referencesByNode[skill.id].orEmpty(),
                            { onOpen(skill) },
                            Modifier.absoluteOffset(x = left + cardWidth * (point.column - .5f))
                                .width(cardWidth).padding(bottom = gap),
                            unlockSource = skill.id == unlock?.skillId,
                            unlockTarget = unlock != null && skill.id in unlock.availableChildren,
                            clock = { clock.value },
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
    unlockSource: Boolean,
    unlockTarget: Boolean,
    clock: () -> Float,
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
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box {
                when {
                    unlockSource && state == SkillState.LIT ->
                        UnlockingMedallion(skill.displayIcon, diameter, focused, source = true, progress = clock)
                    unlockTarget && state == SkillState.AVAILABLE ->
                        UnlockingMedallion(skill.displayIcon, diameter, focused, source = false, progress = clock)
                    else -> SkillMedallion(skill.displayIcon, state, diameter, focused, glyphSize = diameter * .49f)
                }
                if (references.isNotEmpty()) {
                    Surface(Modifier.align(Alignment.BottomEnd), shape = CircleShape, color = Raised) {
                        val label = references.take(2).joinToString("·") + if (references.size > 2) "+" else ""
                        Text(label, Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelMedium, color = Ink)
                    }
                }
            }
            Text(
                mapName,
                color = when {
                    focused -> Gold
                    state == SkillState.LOCKED -> Muted
                    else -> Ink
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp, lineHeight = 22.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                ),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
