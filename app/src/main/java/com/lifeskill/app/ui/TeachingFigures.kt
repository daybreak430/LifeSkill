package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.FigureItem
import com.lifeskill.app.model.FigureRow
import com.lifeskill.app.model.FigureTone
import com.lifeskill.app.model.TeachingFigure

/**
 * 结构图按行渲染：每行要么是 1–3 个并列节点，要么是一句关系说明。
 * 节点宽度由 weight 均分，列数越多内边距越小，窄屏不额外降级成单列，保住并列语义。
 */
@Composable
internal fun TeachingFigureContent(block: TeachingFigure) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("figure-${block.id}")
            .semantics { contentDescription = block.figureDescription() },
    ) {
        block.rows.forEach { row ->
            when (row) {
                is FigureRow.Nodes -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.items.forEach { item ->
                        FigureNode(item, columns = row.items.size, modifier = Modifier.weight(1f))
                    }
                }
                is FigureRow.Relation -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(Line))
                    Text(row.text, style = MaterialTheme.typography.labelMedium, color = Muted, textAlign = TextAlign.Center)
                    Box(Modifier.weight(1f).height(1.dp).background(Line))
                }
            }
        }
        block.caption?.let { caption ->
            Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun FigureNode(item: FigureItem, columns: Int, modifier: Modifier = Modifier) {
    val horizontal = if (columns >= 3) 6.dp else 10.dp
    Surface(modifier = modifier, shape = ControlShape, color = Ground, border = BorderStroke(1.dp, Line)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            item.tone?.let { tone ->
                Box(Modifier.fillMaxWidth().height(3.dp).background(tone.color()))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink, textAlign = TextAlign.Center)
                item.value?.let { value ->
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = Ink)
                }
                item.note?.let { note ->
                    Text(note, style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center)
                }
                item.items.forEach { line ->
                    Text("· $line", style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun FigureTone.color(): Color = when (this) {
    FigureTone.BLUE -> Color(0xFF378ADD)
    FigureTone.AMBER -> Color(0xFFEF9F27)
    FigureTone.ROSE -> Color(0xFFD4537E)
    FigureTone.TEAL -> Color(0xFF1D9E75)
}

// 读屏只念一次结构摘要，避免逐节点跳读打断理解。
private fun TeachingFigure.figureDescription(): String = buildString {
    append("结构图：").append(title).append("。")
    rows.forEach { row ->
        when (row) {
            is FigureRow.Nodes -> row.items.forEach { item ->
                append(item.title)
                item.value?.let { append("，").append(it) }
                item.note?.let { append("，").append(it) }
                if (item.items.isNotEmpty()) append("，").append(item.items.joinToString("、"))
                append("。")
            }
            is FigureRow.Relation -> append("关系：").append(row.text).append("。")
        }
    }
    caption?.let { append(it) }
}
