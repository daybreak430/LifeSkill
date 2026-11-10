package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.Progress

@Composable
internal fun HomeScreen(library: Library, app: AppViewModel) {
    val directions = library.childrenOf(null)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Emblem(Glyph.TREE)
                Text("LifeSkill", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                GlyphButton(Glyph.SETTINGS, "设置", { app.open(Route(Page.SETTINGS)) })
            }
        }
        item { ProgressCard(library.progress(), library.entries.count { it.kind == EntryKind.TREE }) }
        item {
            Action(
                "AI 生成技能树", Glyph.SPARK, { app.open(Route(Page.GENERATE)) },
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 5.dp), primary = true,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("学习方向", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { app.add(EntryKind.DIRECTION) }) {
                    Mark(Glyph.ADD, Gold, Modifier.size(16.dp))
                    Text("新增方向", Modifier.padding(start = 4.dp))
                }
            }
        }
        items(directions, key = Entry::id) { direction ->
            val trees = library.childrenOf(direction.id)
            val progress = library.progress(direction.id)
            EntryTile(
                direction,
                if (trees.isEmpty()) "0 棵技能树" else "${trees.size} 棵树 · ${progress.lit}/${progress.total} 已点亮",
                onClick = { app.open(Route.entry(direction)) },
            )
        }
        if (directions.isEmpty()) item { EmptyState("还没有学习方向", "新增方向，或交给 AI 规划。", Glyph.EXPLORE) }
    }
}

@Composable
internal fun DirectionScreen(direction: Entry, library: Library, app: AppViewModel) {
    val trees = library.childrenOf(direction.id)
    Column(Modifier.fillMaxSize()) {
        PageHeader(direction.name, app::back, subtitle = "学习方向") { EntryMenu(direction, app) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Action("新增技能树", Glyph.ADD, { app.add(EntryKind.TREE, direction.id) }, Modifier.weight(1f))
                    Action("AI 生成", Glyph.SPARK, { app.open(Route(Page.GENERATE, direction.id)) }, Modifier.weight(1f), primary = true)
                }
            }
            if (trees.isEmpty()) item { EmptyState("还没有技能树", "从一个你想掌握的主题开始。", Glyph.TREE) }
            items(trees, key = Entry::id) { tree ->
                val progress = library.progress(tree.id)
                EntryTile(
                    tree, "${progress.lit}/${progress.total} 个技能点已点亮",
                    progress = progress, onClick = { app.open(Route.entry(tree)) },
                )
            }
        }
    }
}

@Composable
internal fun EntryMenu(entry: Entry, app: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlyphButton(Glyph.MORE, "更多操作", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("编辑${entry.kind.label}") }, onClick = { open = false; app.edit(entry) })
            DropdownMenuItem(
                text = { Text("删除${entry.kind.label}", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; app.confirmDelete(entry) },
            )
        }
    }
}

@Composable
private fun ProgressCard(progress: Progress, treeCount: Int) {
    val line = Line
    val steel = Steel
    val gold = Gold
    Surface(shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Line)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width * .87f, size.height * .40f)
                val gap = 24.dp.toPx()
                val upper = center - Offset(gap * .9f, gap)
                val lower = center + Offset(gap * .75f, gap)
                val edge = center + Offset(gap * 1.75f, -gap * .85f)
                drawLine(line, upper, center, strokeWidth = 1.dp.toPx())
                drawLine(line, center, lower, strokeWidth = 1.dp.toPx())
                drawLine(line, center, edge, strokeWidth = 1.dp.toPx())
                listOf(upper, lower, edge).forEach { drawCircle(steel.copy(alpha = .32f), 3.dp.toPx(), it) }
                drawCircle(gold.copy(alpha = .26f), 13.dp.toPx(), center)
                drawCircle(gold, 4.dp.toPx(), center)
            }
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学习进度", color = Muted, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${progress.lit}", style = MaterialTheme.typography.headlineLarge, color = Gold)
                    Text("/ ${progress.total} 已点亮", modifier = Modifier.padding(bottom = 5.dp), color = Muted)
                }
                LinearProgressIndicator(
                    progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(),
                    trackColor = Raised, color = Gold, drawStopIndicator = {},
                )
                Text("$treeCount 棵技能树", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun EntryTile(
    entry: Entry, subtitle: String, onClick: () -> Unit, progress: Progress? = null,
) {
    Surface(
        onClick = onClick, color = Panel, border = BorderStroke(1.dp, Line.copy(alpha = .8f)),
        shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (entry.lit) Gold else Steel
            Emblem(entry.displayIcon.glyph, tint, if (entry.lit) GoldGround else Raised)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted)
                if (progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        color = Gold, trackColor = Raised, drawStopIndicator = {},
                    )
                }
            }
            Mark(Glyph.NEXT, Muted, Modifier.size(15.dp))
        }
    }
}
