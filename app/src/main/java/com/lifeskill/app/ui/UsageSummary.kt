package com.lifeskill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifeskill.app.data.UsageTotal
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

@Composable
internal fun AiUsageSummary(
    usage: UsageTotal?,
    modifier: Modifier = Modifier,
    color: Color = Muted,
    generation: UsageTotal? = null,
    label: String = "AI 用量",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        Text(usage?.displayText() ?: "暂无用量记录", color = color, style = MaterialTheme.typography.bodyMedium)
        if (generation != null) {
            Text("其中生成和补充课时：${generation.displayText()}",
                color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun UsageTotal.displayText(): String {
    val count = NumberFormat.getIntegerInstance().format(tokens)
    val cost = costNanos?.let { nanos ->
        val yuan = BigDecimal.valueOf(nanos, 9).setScale(4, RoundingMode.HALF_UP)
        if (nanos > 0 && yuan.signum() == 0) "< ¥0.0001" else "¥${yuan.toPlainString()}"
    }
    return when {
        cost == null -> "$count Token · 暂无费用估算"
        unpriced > 0 -> "$count Token · 已估 $cost（部分费用未知）"
        else -> "$count Token · 预估 $cost"
    }
}
