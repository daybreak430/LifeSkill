package com.lifeskill.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.*

/**
 * 参数实验台：参数与结果全部来自 AI 的定义，组件只负责生成滑块、计算与排版。
 * 没有内置模型分支；杠杆、复利等主题都是数据。
 */
@Composable
internal fun TeachingExperimentContent(
    block: TeachingExperiment, state: TeachingState, enabled: Boolean,
    onAction: (TeachingAction) -> Unit, expanded: Boolean,
) {
    val stored = state.experiment ?: block.initial
    var values by remember(block.id) { mutableStateOf(stored) }
    var editing by remember { mutableStateOf(false) }
    // 仓库回执会更新另一视图；拖动期间保留本地预览，松手只提交被调整的字段。
    LaunchedEffect(state.experiment, block.initial) {
        if (!editing) values = stored
    }
    fun change() {
        editing = true
    }
    val observation = remember(block, values) {
        runCatching { ExperimentMath.observe(block, values) }.getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
        observation?.let { ExperimentReadout(block, it, compact = !expanded) }
        ExperimentSliders(
            values = values, parameters = block.parameters, enabled = enabled,
            onChange = { id, raw ->
                change()
                values = runCatching { ExperimentMath.update(values, block.parameters, id, raw) }.getOrDefault(values)
            },
            onFinish = { id ->
                editing = false
                onAction(TeachingAction.SetExperimentValue(id, values.value(id)))
            },
        )
    }
}

@Composable
private fun ExperimentReadout(block: TeachingExperiment, observation: ExperimentObservation, compact: Boolean) {
    Surface(color = aiColors.chip, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(block.outputs.first().label, color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(
                observation.headline, color = Ink,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("experiment-headline"),
            )
            if (observation.metrics.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    observation.metrics.forEach { metric ->
                        Column(Modifier.weight(1f)) {
                            Text(metric.label, color = Muted, style = MaterialTheme.typography.labelSmall)
                            Text(metric.value, color = Ink, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentSliders(
    values: ExperimentValues, parameters: List<ExperimentParameter>, enabled: Boolean,
    onChange: (String, Double) -> Unit, onFinish: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        parameters.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { parameter ->
                    val value = values.value(parameter.id)
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(parameter.label, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text(parameter.text(value), color = Ink, style = MaterialTheme.typography.labelLarge)
                        }
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { onChange(parameter.id, it.toDouble()) },
                            onValueChangeFinished = { onFinish(parameter.id) },
                            valueRange = parameter.minimum.toFloat()..parameter.maximum.toFloat(),
                            // 连续轨道 + 取值吸附：参数多时不会把轨道画成一片刻度点。
                            steps = 0,
                            enabled = enabled,
                            modifier = Modifier.testTag("experiment-${parameter.id}")
                                .semantics { contentDescription = "调整${parameter.label}，单位${parameter.unit}" },
                        )
                    }
                }
            }
        }
    }
}
