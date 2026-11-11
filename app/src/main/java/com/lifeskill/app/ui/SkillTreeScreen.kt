package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.lifeskill.app.model.TreeAssessmentSummary
import com.lifeskill.app.model.TreeChallenge
import com.lifeskill.app.model.examAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun SkillTreeScreen(
    tree: Entry, library: Library, latestStudySkillId: String?, app: AppViewModel,
    unlock: SkillUnlock?, active: Boolean, addedEntryId: String?, assessment: TreeAssessmentSummary,
) {
    val skills = library.childrenOf(tree.id)
    val progress = library.progress(tree.id)
    val edges = remember(skills, library.edges) {
        val ids = skills.map(Entry::id).toSet()
        library.edges.filter { it.skillId in ids && it.prerequisiteId in ids }
    }
    val layout = remember(skills.map(Entry::id), edges) { SkillMapLayout.arrange(skills.map(Entry::id), edges) }
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
            Text("课程大纲", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Surface(shape = CircleShape, color = GoldGround) {
                Text(
                    "已完成 ${progress.lit} / ${progress.total} 节", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryAction("添加一节课", Glyph.ADD, { app.add(EntryKind.SKILL, tree.id) }, Modifier.weight(1f))
            AiAction("AI 补充课时", { app.open(Route(Page.GENERATE, tree.id)) }, Modifier.weight(1f))
        }
        Surface(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = studyColors.map.first(), shape = PanelShape,
        ) {
            Box(Modifier.fillMaxWidth().skillMapBackdrop(), contentAlignment = Alignment.Center) {
                if (skills.isEmpty()) {
                    LibraryEmpty("还没有课时", Glyph.TREE)
                } else {
                    SkillMap(
                        skills, layout, library, latestStudySkillId, unlock, active, app::finishUnlock,
                        addedEntryId, app::finishShowingAddition,
                        assessmentPassed = assessment.passed,
                        onOpen = { app.open(Route.entry(it)) },
                        assessmentNodes = { diameter ->
                            ExamMapNodes(library.examAvailable(tree.id), assessment, diameter, active, tree.id,
                                { app.open(Route(Page.EXAM, tree.id)) },
                                { kind ->
                                    val page = when (kind) {
                                        TreeChallenge.DIAGNOSTIC -> Page.DIAGNOSTIC
                                        TreeChallenge.FEYNMAN -> Page.FEYNMAN
                                    }
                                    app.open(Route(page, tree.id))
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.skillMapBackdrop(): Modifier {
    val colors = studyColors
    val grid = Steel
    return drawWithCache {
        val base = Brush.verticalGradient(colors.map)
        val mint = Brush.radialGradient(
            listOf(colors.focusBand.copy(alpha = .24f), colors.focusBand.copy(alpha = 0f)),
            center = Offset(size.width * .10f, size.height * .08f), radius = size.width * .67f,
        )
        val blue = Brush.radialGradient(
            listOf(colors.iconBlue.copy(alpha = .18f), colors.iconBlue.copy(alpha = 0f)),
            center = Offset(size.width, size.height * .90f), radius = size.width * .72f,
        )
        val spacing = 21.dp.toPx()
        val rows = (size.height / spacing + .5f).toInt()
        val columns = (size.width / spacing + .5f).toInt()
        val dots = buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    add(Offset((column + .5f) * spacing, (row + .5f) * spacing))
                }
            }
        }
        val contourCenter = Offset(size.width * 1.02f, size.height * .87f)
        val contour = Brush.radialGradient(
            listOf(grid.copy(alpha = .26f), grid.copy(alpha = 0f)), contourCenter, size.width * .67f,
        )
        val contourStep = 28.dp.toPx()
        val contourStroke = Stroke(.75.dp.toPx())
        onDrawBehind {
            // 装饰固定在画布上，节点滚动和连线动效共用这层静态背景。
            drawRect(base)
            drawRect(mint)
            drawRect(blue)
            drawPoints(dots, PointMode.Points, grid.copy(alpha = .21f), strokeWidth = 1.45.dp.toPx(), cap = StrokeCap.Round)
            repeat(4) { index ->
                drawCircle(contour, radius = contourStep * (index + 2), center = contourCenter, style = contourStroke)
            }
        }
    }
}

private data class MapSegment(val edge: Edge, val start: MapPoint, val end: MapPoint, val index: Int, val count: Int)

@Composable
private fun SkillMap(
    skills: List<Entry>, layout: SkillMapLayout, library: Library, latestStudySkillId: String?,
    unlock: SkillUnlock?, active: Boolean, onUnlockFinished: (String) -> Unit,
    addedEntryId: String?, onAdditionShown: (String) -> Unit, onOpen: (Entry) -> Unit,
    assessmentPassed: Boolean,
    assessmentNodes: @Composable (Dp) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var assessmentWasPassed by rememberSaveable { mutableStateOf(assessmentPassed) }
    RevealListAddition(addedEntryId, layout.nodes[addedEntryId]?.row?.takeIf { unlock == null }, listState, onAdditionShown)
    LaunchedEffect(active, assessmentPassed, layout.rowCount) {
        // 首次通过验收并回到地图时，主动滚到地图末端，让蓄力与双节点显现完整进入视野。
        if (active && assessmentPassed && !assessmentWasPassed) {
            listState.animateScrollToItem(layout.rowCount)
            assessmentWasPassed = true
        } else if (!assessmentPassed) {
            assessmentWasPassed = false
        }
    }
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
        var feltLight = false
        var feltUnlock = false
        clock.animateTo(1f, tween(MAP_UNLOCK_DURATION_MS, easing = LinearEasing)) {
            if (unlock.availableChildren.isNotEmpty() && !feltUnlock && value >= MAP_LOCK_RELEASE_AT) {
                // 多条分支同时开锁时只反馈一次，系统跳过动效时也不叠加两次反馈。
                feltUnlock = true
                feltLight = true
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            } else if (!feltLight && value >= .24f) {
                feltLight = true
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
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
    val ready = skills.filter { library.skillState(it.id) == SkillState.AVAILABLE }
    val focusId = ready.firstOrNull { it.id == latestStudySkillId }?.id ?: ready.minWithOrNull(
        compareBy({ layout.nodes.getValue(it.id).row }, { layout.nodes.getValue(it.id).column }),
    )?.id
    val colors = studyColors
    val scaleText = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val mutedLine = lerp(Line, Steel, .48f)
    val focusLine = lerp(colors.route, Gold, .42f)

    BoxWithConstraints(Modifier.fillMaxWidth().ignoreHorizontalAndMultiTouch()) {
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
            item(key = "tree-assessment") { assessmentNodes(diameter) }
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
    onOpen: () -> Unit,
    modifier: Modifier,
    unlockSource: Boolean,
    unlockTarget: Boolean,
    clock: () -> Float,
) {
    val mapName = remember(skill.name, skill.shortName) { skill.mapName }
    val palette = studyColors.mapNodePalette
    val locked = state == SkillState.LOCKED
    val lockedPalette = palette.takeIf { locked }
    Surface(
        onClick = onOpen, shape = ControlShape,
        modifier = modifier.semantics {
            contentDescription = skill.name
            stateDescription = if (focused) "${state.label}，建议下一课" else state.label
        },
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            when {
                unlockSource && state == SkillState.LIT ->
                    UnlockingMedallion(skill.displayIcon, diameter, focused, source = true, progress = clock)
                unlockTarget && state == SkillState.AVAILABLE ->
                    UnlockingMedallion(skill.displayIcon, diameter, focused, source = false, progress = clock)
                else -> SkillMedallion(
                    skill.displayIcon, state, diameter, focused, glyphSize = diameter * .49f,
                    mapPalette = palette,
                )
            }
            Text(
                mapName,
                color = when {
                    focused -> Gold
                    locked -> lockedPalette?.lockedInk ?: Muted
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
