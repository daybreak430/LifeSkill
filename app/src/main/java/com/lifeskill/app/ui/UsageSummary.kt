package com.lifeskill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifeskill.app.data.UsageTotal
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryUsageMenu(
    entry: Entry, app: AppViewModel, usage: UsageTotal?, generation: UsageTotal? = null,
    onExportConversation: (() -> Unit)? = null,
) {
    var showingUsage by rememberSaveable(entry.id) { mutableStateOf(false) }
    EntryMenu(entry, app, onShowUsage = { showingUsage = true }, onExportConversation = onExportConversation)
    if (showingUsage) {
        ModalBottomSheet(
            onDismissRequest = { showingUsage = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Panel,
            contentColor = Ink,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI 用量", Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    GlyphButton(Glyph.CLOSE, "关闭 AI 用量", { showingUsage = false })
                }
                AiUsageSummary(
                    usage, generation = generation,
                    label = if (entry.kind == EntryKind.TREE) "整门课程" else "本节课",
                )
                if (usage != null && usage.searchCalls > 0) {
                    val calls = NumberFormat.getIntegerInstance().format(usage.searchCalls)
                    Text("其中联网搜索 $calls 次 · ${formatCost(usage.searchCostNanos)}",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun AiUsageSummary(
    usage: UsageTotal?,
    modifier: Modifier = Modifier,
    color: Color = Muted,
    generation: UsageTotal? = null,
    cumulative: Boolean = false,
    label: String = if (cumulative) "AI 累计用量" else "AI 用量",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        Text(usage?.displayText(cumulative) ?: "暂无用量记录", color = color, style = MaterialTheme.typography.bodyMedium)
        if (generation != null) {
            Text("其中生成和补充课时：${generation.displayText()}",
                color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun UsageTotal.displayText(cumulative: Boolean = false): String {
    val count = NumberFormat.getIntegerInstance().format(tokens)
    val cost = costNanos?.let(::formatCost)
    return when {
        cost == null -> "$count Token · 暂无费用估算"
        cumulative -> "$count Token · 预估费用 $cost"
        unpriced > 0 -> "$count Token · 已估 $cost（部分费用未知）"
        else -> "$count Token · 预估 $cost"
    }
}

private fun formatCost(nanos: Long): String {
    val yuan = BigDecimal.valueOf(nanos, 9).setScale(4, RoundingMode.HALF_UP)
    return if (nanos > 0 && yuan.signum() == 0) "< ¥0.0001" else "¥${yuan.toPlainString()}"
}
