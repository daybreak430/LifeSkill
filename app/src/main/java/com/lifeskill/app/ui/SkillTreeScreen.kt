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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.lifeskill.app.model.Edge
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.SkillGraph
import kotlin.math.roundToInt

@Composable
internal fun SkillTreeScreen(tree: Entry, library: Library, app: AppViewModel) {
    val skills = library.childrenOf(tree.id)
    val progress = library.progress(tree.id)
    val edges = remember(skills, library.edges) {
        val ids = skills.map(Entry::id).toSet()
        library.edges.filter { it.skillId in ids && it.prerequisiteId in ids }
    }
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
            Mark(Glyph.DIAMOND, Gold, Modifier.size(14.dp))
            Text(
                "${progress.lit} / ${progress.total} 已点亮", Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = Gold,
            )
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            color = Night, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Line),
        ) {
            if (skills.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    EmptyState("技能地图还是空的", "新增第一个技能点，或让 AI 拆解学习路径。", Glyph.TREE)
                }
            } else {
                SkillMap(skills, edges) { app.open(Route.entry(it)) }
            }
        }
    }
}

private data class MapLayout(val centers: Map<String, Offset>, val width: Float, val height: Float)

private fun mapLayout(tiers: List<List<String>>, width: Float, height: Float, gap: Float, step: Float): MapLayout {
    val worldWidth = tiers.maxOf { it.size } * (width + gap) - gap
    val centers = buildMap {
        tiers.forEachIndexed { level, skills ->
            val left = (worldWidth - (skills.size * (width + gap) - gap)) / 2
            skills.forEachIndexed { index, id ->
                put(id, Offset(left + index * (width + gap) + width / 2, level * (height + step) + height / 2))
            }
        }
    }
    return MapLayout(centers, worldWidth, tiers.size * (height + step) - step)
}

@Composable
private fun SkillMap(skills: List<Entry>, edges: List<Edge>, onOpen: (Entry) -> Unit) {
    val tiers = remember(skills, edges) { SkillGraph.tiers(skills.map(Entry::id), edges) }
    val byId = remember(skills) { skills.associateBy(Entry::id) }
    val density = LocalDensity.current
    val scaleText = density.fontScale.coerceIn(1f, 1.5f)
    val cardWidth = 148.dp * scaleText
    val cardHeight = 82.dp * scaleText
    val widthPx = with(density) { cardWidth.toPx() }
    val heightPx = with(density) { cardHeight.toPx() }
    val layout = remember(tiers, widthPx, heightPx, density) {
        with(density) { mapLayout(tiers, widthPx, heightPx, 18.dp.toPx(), 46.dp.toPx()) }
    }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var shiftX by rememberSaveable { mutableFloatStateOf(0f) }
    var shiftY by rememberSaveable { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("skill-map")) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val margin = with(density) { 24.dp.toPx() }
        val visibleStrip = with(density) { 48.dp.toPx() }.coerceAtMost(minOf(screenWidth, screenHeight) / 2)
        val origin = Offset((screenWidth - layout.width) / 2f, margin)
        val originInView = origin + Offset(shiftX, shiftY)

        // 以触点为缩放中心，保留一段可拖回的画布，避免节点全部移出视口。
        fun transform(pivot: Offset, pan: Offset, factor: Float) {
            val next = (zoom * factor).coerceIn(.45f, 2.25f)
            val previous = origin + Offset(shiftX, shiftY)
            val offset = pivot + pan + (previous - pivot) * (next / zoom)
            shiftX = offset.x.coerceIn(visibleStrip - layout.width * next, screenWidth - visibleStrip) - origin.x
            shiftY = offset.y.coerceIn(visibleStrip - layout.height * next, screenHeight - visibleStrip) - origin.y
            zoom = next
        }

        Box(
            Modifier.fillMaxSize().pointerInput(layout, screenWidth, screenHeight) {
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
                        drawCircle(Line.copy(alpha = .55f), 0.85.dp.toPx(), Offset(x, y))
                        y += dotStep
                    }
                    x += dotStep
                }
                val halfCardHeight = heightPx * zoom / 2
                edges.forEach { edge ->
                    val from = layout.centers.getValue(edge.prerequisiteId) * zoom + originInView + Offset(0f, halfCardHeight)
                    val to = layout.centers.getValue(edge.skillId) * zoom + originInView - Offset(0f, halfCardHeight)
                    val middleY = (from.y + to.y) / 2
                    val lineColor = if (byId[edge.prerequisiteId]?.lit == true && byId[edge.skillId]?.lit == true) Gold else Steel.copy(alpha = .64f)
                    drawPath(
                        Path().apply {
                            moveTo(from.x, from.y)
                            cubicTo(from.x, middleY, to.x, middleY, to.x, to.y)
                        },
                        lineColor, style = Stroke(1.5.dp.toPx()),
                    )
                    drawCircle(lineColor, 2.8.dp.toPx(), to)
                }
            }

            skills.forEach { skill ->
                val center = layout.centers.getValue(skill.id)
                val topLeft = (center - Offset(widthPx / 2, heightPx / 2)) * zoom + originInView
                MapNode(
                    skill, { onOpen(skill) },
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
private fun MapNode(skill: Entry, onOpen: () -> Unit, modifier: Modifier) {
    val state = if (skill.lit) "已点亮" else "待学习"
    val tint = if (skill.lit) Gold else Steel
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(13.dp),
        modifier = modifier.semantics { contentDescription = skill.name; stateDescription = state },
        color = if (skill.lit) GoldGround else Panel,
        border = BorderStroke(1.dp, if (skill.lit) Gold.copy(alpha = .78f) else Line),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(skill.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mark(if (skill.lit) Glyph.CHECK else Glyph.DIAMOND, tint, Modifier.size(12.dp))
                Text(state, Modifier.padding(start = 4.dp), color = tint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
