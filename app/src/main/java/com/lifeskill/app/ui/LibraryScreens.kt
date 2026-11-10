package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library

@Composable
internal fun HomeScreen(library: Library, latestSkill: Entry?, app: AppViewModel) {
    val directions = library.childrenOf(null)
    val latestTree = library.byId[latestSkill?.parentId]
    val canContinue = latestSkill != null && latestTree != null
    val columnWidth = 148.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(columnWidth),
        modifier = Modifier.fillMaxSize().testTag("learning-directions"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mark(Glyph.TREE, Gold, Modifier.size(30.dp))
                Text("LifeSkill", Modifier.weight(1f), style = LibraryTitle)
                GlyphButton(Glyph.SETTINGS, "设置", { app.open(Route(Page.SETTINGS)) })
            }
        }
        if (latestSkill != null && latestTree != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueStudyCard(latestSkill, latestTree, library.progress(latestTree.id)) {
                    app.continueStudy(latestSkill)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryAction(
                label = "AI 生成技能树",
                glyph = Glyph.SPARK,
                onClick = { app.open(Route(Page.GENERATE)) },
                modifier = Modifier.fillMaxWidth(),
                primary = !canContinue,
                trailing = true,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibrarySection("学习方向", Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { app.add(EntryKind.DIRECTION) }) {
                    Mark(Glyph.ADD, Gold, Modifier.size(16.dp))
                    Text("新增方向", Modifier.padding(start = 4.dp))
                }
            }
        }
        items(directions, key = Entry::id) { direction ->
            DirectionCard(direction, library.progress(direction.id), library.childrenOf(direction.id).size) {
                app.open(Route.entry(direction))
            }
        }
        if (directions.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryEmpty("还没有学习方向", Glyph.EXPLORE)
            }
        }
    }
}

@Composable
internal fun DirectionScreen(direction: Entry, library: Library, latestTreeId: String?, app: AppViewModel) {
    val trees = library.childrenOf(direction.id)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlyphButton(Glyph.BACK, "返回", app::back)
            EntryMenu(direction, app)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("skill-tree-list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        LibrarySection("学习方向", style = MaterialTheme.typography.labelLarge, color = Muted)
                        Text(direction.name, style = LibraryTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Emblem(direction.displayIcon.glyph, background = Raised, diameter = 58.dp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryAction("新增技能树", Glyph.ADD, { app.add(EntryKind.TREE, direction.id) }, Modifier.weight(1f))
                    LibraryAction(
                        "AI 生成", Glyph.SPARK, { app.open(Route(Page.GENERATE, direction.id)) },
                        Modifier.weight(1f), primary = true,
                    )
                }
            }
            item { LibrarySection("技能树", Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            if (trees.isEmpty()) item { LibraryEmpty("还没有技能树", Glyph.TREE) }
            items(trees, key = Entry::id) { tree ->
                SkillTreeCard(tree, library.progress(tree.id), latest = tree.id == latestTreeId) {
                    app.open(Route.entry(tree))
                }
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
internal fun EntryTile(entry: Entry, subtitle: String, onClick: () -> Unit) {
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
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Mark(Glyph.NEXT, Muted, Modifier.size(15.dp))
        }
    }
}
