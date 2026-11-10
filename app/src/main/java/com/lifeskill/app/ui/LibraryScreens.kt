package com.lifeskill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.HomeStudyState
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.homeStudy
import kotlinx.coroutines.flow.first

@Composable
internal fun HomeScreen(library: Library, latestSkill: Entry?, app: AppViewModel, addedEntryId: String?) {
    val directions = library.childrenOf(null)
    val study = remember(library, latestSkill) { library.homeStudy(latestSkill) }
    val columnWidth = 148.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val sidePadding = 16.dp
    val gap = 12.dp
    val cardHeight = directionCardHeight()
    val state = rememberLazyGridState()
    val addition = directions.indexOfFirst { it.id == addedEntryId }.takeIf { it >= 0 }
        ?.plus(if (study == null) 3 else 4)
    RevealAddition(
        addedEntryId, addition, { state.layoutInfo.totalItemsCount },
        visible = { index ->
            val info = state.layoutInfo
            info.visibleItemsInfo.any {
                it.index == index && it.offset.y >= info.viewportStartOffset &&
                    it.offset.y + it.size.height <= info.viewportEndOffset
            }
        },
        scroll = { state.animateScrollToItem(it) }, onShown = app::finishShowingAddition,
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(columnWidth),
        state = state,
        modifier = Modifier.fillMaxSize().testTag("learning-directions"),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 6.dp, bottom = 20.dp),
    ) {
        item(key = "home-header", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LifeSkill", Modifier.weight(1f), style = LibraryTitle)
                GlyphButton(Glyph.SETTINGS, "设置", { app.open(Route(Page.SETTINGS)) })
            }
        }
        item(key = "home-study", span = { GridItemSpan(maxLineSpan) }) {
            HomeStudyCard(study, Modifier.animateItem()) {
                when (study?.state) {
                    null -> app.open(Route(Page.GENERATE))
                    HomeStudyState.COMPLETED -> app.open(Route.entry(study.tree))
                    else -> app.continueStudy(study.skill)
                }
            }
        }
        if (study != null) {
            item(key = "home-ai", span = { GridItemSpan(maxLineSpan) }) {
                AiAction(
                    label = "AI 生成技能树",
                    onClick = { app.open(Route(Page.GENERATE)) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    wide = true,
                )
            }
        }
        item(key = "directions-heading", span = { GridItemSpan(maxLineSpan) }) {
            LibrarySection("学习方向", Modifier.animateItem().padding(top = 6.dp)) {
                TextButton(onClick = { app.add(EntryKind.DIRECTION) }) {
                    Mark(Glyph.ADD, Gold, Modifier.size(16.dp))
                    Text("新增方向", Modifier.padding(start = 4.dp))
                }
            }
        }
        items(directions, key = Entry::id) { direction ->
            val progress = library.progress(direction.id)
            val card = Modifier.animateItem().height(cardHeight).testTag("direction-card-${direction.id}")
            val open = { app.open(Route.entry(direction)) }
            DirectionCard(
                direction, progress, card, open,
                createTree = if (library.childrenOf(direction.id).isEmpty()) {
                    { app.add(EntryKind.TREE, direction.id) }
                } else null,
            )
        }
        if (directions.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryEmpty("还没有学习方向", Glyph.EXPLORE)
            }
        }
    }
}

@Composable
internal fun DirectionScreen(direction: Entry, library: Library, latestTreeId: String?, app: AppViewModel, addedEntryId: String?) {
    val trees = library.childrenOf(direction.id)
    val state = rememberLazyListState()
    val addition = trees.indexOfFirst { it.id == addedEntryId }.takeIf { it >= 0 }?.plus(3)
    RevealListAddition(addedEntryId, addition, state, app::finishShowingAddition)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlyphButton(Glyph.BACK, "返回", app::back)
            EntryMenu(direction, app)
        }
        LazyColumn(
            state = state,
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
                        Text("学习方向", style = MaterialTheme.typography.labelLarge, color = Muted)
                        Text(direction.name, style = LibraryTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    val tint = entryTint(direction)
                    Emblem(direction.displayIcon.glyph, tint, tint.copy(alpha = .14f), diameter = 54.dp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryAction("新增技能树", Glyph.ADD, { app.add(EntryKind.TREE, direction.id) }, Modifier.weight(1f))
                    AiAction(
                        "AI 生成", { app.open(Route(Page.GENERATE, direction.id)) }, Modifier.weight(1f),
                    )
                }
            }
            item { LibrarySection("技能树", Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            if (trees.isEmpty()) item { LibraryEmpty("还没有技能树", Glyph.TREE) }
            items(trees, key = Entry::id) { tree ->
                SkillTreeCard(tree, library.progress(tree.id), tree.id == latestTreeId, Modifier.animateItem()) {
                    app.open(Route.entry(tree))
                }
            }
        }
    }
}

@Composable
internal fun RevealListAddition(
    id: String?, index: Int?, state: LazyListState, onShown: (String) -> Unit,
) {
    RevealAddition(
        id, index, { state.layoutInfo.totalItemsCount },
        visible = { target ->
            val info = state.layoutInfo
            info.visibleItemsInfo.any {
                it.index == target && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
            }
        },
        scroll = { state.animateScrollToItem(it) }, onShown = onShown,
    )
}

@Composable
private fun RevealAddition(
    id: String?, index: Int?, itemCount: () -> Int, visible: (Int) -> Boolean,
    scroll: suspend (Int) -> Unit, onShown: (String) -> Unit,
) {
    LaunchedEffect(id, index) {
        if (id == null || index == null) return@LaunchedEffect
        // 等新条目进入列表布局，只在它超出视口时滚动。
        snapshotFlow { itemCount() }.first { it > index }
        try {
            if (!visible(index)) scroll(index)
        } finally {
            onShown(id)
        }
    }
}

@Composable
internal fun EntryMenu(entry: Entry, app: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlyphButton(Glyph.MORE, "更多操作", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = ControlShape, containerColor = Panel) {
            DropdownMenuItem(text = { Text("编辑${entry.kind.label}") }, onClick = { open = false; app.edit(entry) })
            DropdownMenuItem(
                text = { Text("删除${entry.kind.label}", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; app.confirmDelete(entry) },
            )
        }
    }
}

@Composable
internal fun EntryTile(entry: Entry, state: SkillState, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Raised,
        shape = ControlShape, modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            SkillMedallion(entry.displayIcon, state, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.label, style = MaterialTheme.typography.labelMedium, color = state.tint())
            }
            Mark(Glyph.NEXT, Muted, Modifier.size(15.dp))
        }
    }
}
