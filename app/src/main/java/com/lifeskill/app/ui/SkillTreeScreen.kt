package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.SkillMapLayout
import kotlin.math.roundToInt

@Composable
internal fun SkillTreeScreen(tree: Entry, library: Library, app: AppViewModel) {
    val skills = library.childrenOf(tree.id)
    val progress = library.progress(tree.id)
    val edges = remember(skills, library.edges) {
        val ids = skills.map(Entry::id).toSet()
        library.edges.filter { it.skillId in ids && it.prerequisiteId in ids }
    }
    val layout = remember(skills.map(Entry::id), edges) { SkillMapLayout.arrange(skills.map(Entry::id), edges) }
    var showReferences by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(tree.name, app::back, subtitle = library.byId[tree.parentId]?.name) { EntryMenu(tree, app) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Action("新增技能点", Glyph.ADD, { app.add(EntryKind.SKILL, tree.id) }, Modifier.weight(1f))
            Action("AI 追加", Glyph.SPARK, { app.open(Route(Page.GENERATE, tree.id)) }, Modifier.weight(1f), primary = true)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("技能地图", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (layout.references.isNotEmpty()) {
                TextButton(onClick = { showReferences = true }) { Text("关联 ${layout.references.size}") }
            }
            Mark(Glyph.DIAMOND, Gold, Modifier.size(14.dp))
            Text(
                "${progress.lit} / ${progress.total} 已点亮", Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = Gold,
            )
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            color = Ground, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Line),
        ) {
            if (skills.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    EmptyState("技能地图还是空的", "新增第一个技能点，或让 AI 拆解学习路径。", Glyph.TREE)
                }
            } else {
                SkillMap(skills, layout) { app.open(Route.entry(it)) }
            }
        }
    }
    if (showReferences && layout.references.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showReferences = false },
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

@Composable
private fun SkillMap(skills: List<Entry>, layout: SkillMapLayout, onOpen: (Entry) -> Unit) {
    val byId = remember(skills) { skills.associateBy(Entry::id) }
    val referencesByNode = remember(layout.references) {
        layout.references.flatMapIndexed { index, edge ->
            listOf(edge.prerequisiteId to index + 1, edge.skillId to index + 1)
        }.groupBy({ it.first }, { it.second })
    }
    val density = LocalDensity.current
    val scaleText = density.fontScale.coerceIn(1f, 1.5f)
    val cardWidth = 108.dp * scaleText
    val cardHeight = 94.dp * scaleText
    val widthPx = with(density) { cardWidth.toPx() }
    val heightPx = with(density) { cardHeight.toPx() }
    val gapPx = with(density) { 38.dp.toPx() }
    val stepPx = heightPx + gapPx
    val worldWidth = layout.width * widthPx
    val worldHeight = layout.rowCount * stepPx - gapPx
    val dots = Line
    val litLine = Gold
    val unlitLine = Steel.copy(alpha = .64f)
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var shiftX by rememberSaveable { mutableFloatStateOf(0f) }
    var shiftY by rememberSaveable { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("skill-map")) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val margin = with(density) { 24.dp.toPx() }
        val visibleStrip = with(density) { 48.dp.toPx() }.coerceAtMost(minOf(screenWidth, screenHeight) / 2)
        val origin = Offset((screenWidth - worldWidth) / 2f, margin)
        val originInView = origin + Offset(shiftX, shiftY)

        // 以触点为缩放中心，保留一段可拖回的画布，避免节点全部移出视口。
        fun transform(pivot: Offset, pan: Offset, factor: Float) {
            val next = (zoom * factor).coerceIn(.45f, 2.25f)
            val previous = origin + Offset(shiftX, shiftY)
            val offset = pivot + pan + (previous - pivot) * (next / zoom)
            shiftX = offset.x.coerceIn(visibleStrip - worldWidth * next, screenWidth - visibleStrip) - origin.x
            shiftY = offset.y.coerceIn(visibleStrip - worldHeight * next, screenHeight - visibleStrip) - origin.y
            zoom = next
        }

        Box(
            Modifier.fillMaxSize().pointerInput(layout, worldWidth, worldHeight, screenWidth, screenHeight) {
                detectTransformGestures(panZoomLock = true) { pivot, pan, factor, _ ->
                    transform(pivot, pan, factor)
                }
            },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val dotStep = 18.dp.toPx()
                var x = dotStep / 2
                while (x < size.width) {
                    var y = dotStep / 2
                    while (y < size.height) {
                        drawCircle(dots.copy(alpha = .55f), 0.85.dp.toPx(), Offset(x, y))
                        y += dotStep
                    }
                    x += dotStep
                }
                layout.connections.forEach { connection ->
                    val edge = connection.edge
                    val points = connection.points
                    val start = points.first()
                    var from = Offset(start.column * widthPx, start.row * stepPx + heightPx) * zoom + originInView
                    val path = Path().apply { moveTo(from.x, from.y) }
                    var end = from
                    for (index in 1..points.lastIndex) {
                        val point = points[index]
                        val to = Offset(point.column * widthPx, point.row * stepPx) * zoom + originInView
                        val middleY = (from.y + to.y) / 2
                        path.cubicTo(from.x, middleY, to.x, middleY, to.x, to.y)
                        end = to
                        if (index < points.lastIndex) {
                            from = to + Offset(0f, heightPx * zoom)
                            path.lineTo(from.x, from.y)
                        }
                    }
                    val lineColor = if (byId[edge.prerequisiteId]?.lit == true && byId[edge.skillId]?.lit == true) litLine else unlitLine
                    drawPath(path, lineColor, style = Stroke(1.5.dp.toPx()))
                    drawCircle(lineColor, 2.8.dp.toPx(), end)
                }
            }

            skills.forEach { skill ->
                val center = layout.nodes.getValue(skill.id)
                val topLeft = Offset((center.column - .5f) * widthPx, center.row * stepPx) * zoom + originInView
                MapNode(
                    skill, cardHeight * .66f, referencesByNode[skill.id].orEmpty(), { onOpen(skill) },
                    Modifier.offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                        .size(cardWidth, cardHeight)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            shape = RoundedCornerShape(13.dp), color = Panel,
            border = BorderStroke(1.dp, Line),
        ) {
            val center = Offset(screenWidth / 2, screenHeight / 2)
            Row {
                GlyphButton(Glyph.MINUS, "缩小地图", { transform(center, Offset.Zero, .8f) }, enabled = zoom > .45f)
                GlyphButton(Glyph.PLUS, "放大地图", { transform(center, Offset.Zero, 1.25f) }, enabled = zoom < 2.25f)
                GlyphButton(Glyph.FIT, "重置地图视图", {
                    zoom = 1f
                    shiftX = 0f
                    shiftY = 0f
                })
            }
        }
    }
}

@Composable
private fun MapNode(
    skill: Entry,
    diameter: androidx.compose.ui.unit.Dp,
    references: List<Int>,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val state = if (skill.lit) "已点亮" else "待学习"
    val tint = if (skill.lit) Gold else Steel
    val mapName = remember(skill.name, skill.shortName) { skill.mapName }
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(13.dp),
        modifier = modifier.semantics {
            contentDescription = skill.name
            stateDescription = if (references.isEmpty()) state else "$state，前置关联 ${references.joinToString()}"
        },
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box {
                SkillMedallion(skill.displayIcon, skill.lit, diameter)
                if (references.isNotEmpty()) {
                    Surface(Modifier.align(Alignment.TopEnd), shape = RoundedCornerShape(5.dp), color = Raised) {
                        val label = references.take(2).joinToString("·") + if (references.size > 2) "+" else ""
                        Text(label, Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelMedium, color = Ink)
                    }
                }
            }
            Text(mapName, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
