package com.lifeskill.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.*
import kotlin.math.tan

@Composable
internal fun TeachingExperimentContent(
    block: TeachingExperiment, state: TeachingState, enabled: Boolean,
    onAction: (TeachingAction) -> Unit, expanded: Boolean,
) {
    val stored = state.experiment?.takeIf { it.model == block.model } ?: block.initial
    var values by remember(block.id) { mutableStateOf(stored) }
    var editing by remember { mutableStateOf(false) }
    // 仓库回执更新另一视图；正在拖动时保留本地预览，松手只提交被调整的字段。
    LaunchedEffect(state.experiment, block.initial) {
        if (!editing) values = stored
    }
    fun change(parameterId: String, raw: Double) {
        values = runCatching { ExperimentMath.update(values, parameterId, raw) }.getOrDefault(values)
    }
    fun finish(parameterId: String) {
        editing = false
        onAction(TeachingAction.SetExperimentValue(parameterId, values.value(parameterId)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
        when (block.model) {
            ExperimentModel.LEVER -> LeverExperiment(
                values = values, enabled = enabled, expanded = expanded,
                onStartEdit = { editing = true }, onChange = ::change, onFinish = ::finish,
            )
            ExperimentModel.COMPOUND -> CompoundExperiment(
                values = values, enabled = enabled, expanded = expanded,
                onStartEdit = { editing = true }, onChange = ::change, onFinish = ::finish,
            )
        }
    }
}

@Composable
private fun LeverExperiment(
    values: ExperimentValues, enabled: Boolean, expanded: Boolean,
    onStartEdit: () -> Unit, onChange: (String, Double) -> Unit, onFinish: (String) -> Unit,
) {
    val parameters = values.leverParameters()
    val observation = LeverPhysics.observe(parameters)
    val angle by animateFloatAsState(observation.illustrationAngle, tween(220), label = "杠杆倾斜")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = expanded && maxWidth >= 600.dp
        val scene: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LeverScene(
                    parameters, angle, enabled, if (wide) 200 else if (expanded) 280 else 210,
                    onStart = onStartEdit,
                    onChange = { parameter, value -> onChange(parameter.specId(), value) },
                    onFinish = { parameter -> onFinish(parameter.specId()) },
                )
                Text("理想轻杆 · 力臂为水平距离 · 倾斜示意", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeverReadout(parameters, wide)
                ExperimentSliders(
                    values, ExperimentCatalog.parameters(ExperimentModel.LEVER), enabled,
                    tagPrefix = "lever", ticks = true,
                    onChange = { id, value -> onStartEdit(); onChange(id, value) }, onFinish = onFinish,
                )
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f)) { scene() }
            Box(Modifier.weight(1f)) { controls() }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            scene()
            controls()
        }
    }
}

@Composable
private fun CompoundExperiment(
    values: ExperimentValues, enabled: Boolean, expanded: Boolean,
    onStartEdit: () -> Unit, onChange: (String, Double) -> Unit, onFinish: (String) -> Unit,
) {
    val observation = ExperimentMath.observe(values)
    Column(
        verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompoundReadout(observation, compact = !expanded)
        ExperimentSliders(
            values, ExperimentCatalog.parameters(ExperimentModel.COMPOUND), enabled,
            tagPrefix = "experiment", ticks = false,
            onChange = { id, value -> onStartEdit(); onChange(id, value) }, onFinish = onFinish,
        )
    }
}

@Composable
private fun CompoundReadout(observation: ExperimentObservation, compact: Boolean) {
    Surface(color = aiColors.chip, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("期末总额", color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(
                observation.headline, color = Ink,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("experiment-headline"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                observation.metrics.forEach { metric ->
                    Column(Modifier.weight(1f)) {
                        Text(metric.label, color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(metric.value, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            observation.investedShare?.let { share ->
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Gold.copy(alpha = .22f)),
                ) {
                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(Gold))
                }
                Text("实色为累计投入，浅色为收益", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 参数滑块按模型登记的参数表生成：两列布局、标签与单位来自参数定义，
 * 松手只提交被调整的字段，避免整组草稿覆盖其他滑块的改动。
 */
@Composable
private fun ExperimentSliders(
    values: ExperimentValues, parameters: List<ExperimentParameter>, enabled: Boolean,
    tagPrefix: String, ticks: Boolean, onChange: (String, Double) -> Unit, onFinish: (String) -> Unit,
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
                            // 杠杆的离散档位有意义，保留刻度；复利参数连续调节，避免轨道变成点阵。
                            steps = if (ticks) {
                                ((parameter.maximum - parameter.minimum) / parameter.step).toInt() - 1
                            } else 0,
                            enabled = enabled,
                            modifier = Modifier.testTag("$tagPrefix-${parameter.tagId}")
                                .semantics { contentDescription = "调整${parameter.label}，单位${parameter.unit}" },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// 杠杆沿用旧的测试标记与外部契约（left_force 等），新模型直接使用参数 id。
private val ExperimentParameter.tagId: String
    get() = if (id.any { it.isUpperCase() }) id.replace(Regex("([A-Z])"), "_$1").lowercase() else id

private fun LeverParameter.specId(): String = when (this) {
    LeverParameter.LEFT_FORCE -> "leftForce"
    LeverParameter.RIGHT_FORCE -> "rightForce"
    LeverParameter.LEFT_ARM -> "leftArm"
    LeverParameter.RIGHT_ARM -> "rightArm"
}

@Composable
private fun LeverReadout(parameters: LeverParameters, compact: Boolean) {
    val observation = LeverPhysics.observe(parameters)
    val leftColor = aiColors.accent
    val rightColor = Gold
    val status = when (observation.balance) {
        LeverBalance.LEFT_DOWN -> "左侧下沉"
        LeverBalance.RIGHT_DOWN -> "右侧下沉"
        LeverBalance.BALANCED -> "已平衡"
    }
    val statusColor by animateColorAsState(
        if (observation.balance == LeverBalance.BALANCED) leftColor else Ink, tween(180), label = "杠杆状态颜色",
    )
    Surface(color = aiColors.chip, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, color = statusColor, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("lever-status"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("左 ${number(parameters.leftForce)} × ${number(parameters.leftArm)} = ${number(observation.leftMoment)} N·m",
                    color = leftColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), minLines = if (compact) 1 else 2)
                Text("右 ${number(parameters.rightForce)} × ${number(parameters.rightArm)} = ${number(observation.rightMoment)} N·m",
                    color = rightColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), minLines = if (compact) 1 else 2)
            }
        }
    }
}

@Composable
private fun LeverScene(
    parameters: LeverParameters, angle: Float, enabled: Boolean, height: Int,
    onStart: () -> Unit, onChange: (LeverParameter, Double) -> Unit, onFinish: (LeverParameter) -> Unit,
) {
    val leftArm by animateFloatAsState(parameters.leftArm.toFloat(), tween(120), label = "左侧重物位置")
    val rightArm by animateFloatAsState(parameters.rightArm.toFloat(), tween(120), label = "右侧重物位置")
    val leftColor = aiColors.accent
    val rightColor = Gold
    val line = Line
    val muted = Muted
    val currentParameters by rememberUpdatedState(parameters)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinish by rememberUpdatedState(onFinish)
    val currentOnStart by rememberUpdatedState(onStart)
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp)
        .testTag("lever-scene")) {
        val centerX = maxWidth / 2
        val centerY = maxHeight * .43f
        val unit = (maxWidth - 64.dp) / 8
        val unitPx = with(LocalDensity.current) { unit.toPx() }
        val slope = tan(Math.toRadians(angle.toDouble())).toFloat()
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "杠杆，可水平拖动左侧和右侧重物调整力臂"
        }) {
            val pivot = Offset(centerX.toPx(), centerY.toPx())
            fun point(distance: Float) = pivot + Offset(unit.toPx() * distance, unit.toPx() * distance * slope)
            // 力始终竖直向下，横向坐标直接表示力臂；示意倾角不改变 F×水平距离 的计算含义。
            drawLine(leftColor, point(-4f), pivot, 6.dp.toPx())
            drawLine(rightColor, pivot, point(4f), 6.dp.toPx())
            for (mark in -4..4) {
                val position = point(mark.toFloat())
                drawLine(muted, position - Offset(0f, 5.dp.toPx()), position + Offset(0f, 5.dp.toPx()), 1.dp.toPx())
            }
            val support = Path().apply {
                moveTo(pivot.x, pivot.y + 5.dp.toPx())
                lineTo(pivot.x - 18.dp.toPx(), pivot.y + 37.dp.toPx())
                lineTo(pivot.x + 18.dp.toPx(), pivot.y + 37.dp.toPx())
                close()
            }
            drawPath(support, muted, style = Stroke(2.dp.toPx()))
            drawLine(line, Offset(pivot.x - 30.dp.toPx(), pivot.y + 42.dp.toPx()),
                Offset(pivot.x + 30.dp.toPx(), pivot.y + 42.dp.toPx()), 2.dp.toPx())
            listOf(-leftArm to leftColor, rightArm to rightColor).forEach { (arm, color) ->
                val anchor = point(arm)
                drawLine(color, anchor, anchor + Offset(0f, 14.dp.toPx()), 2.dp.toPx())
            }
        }
        listOf(LeverParameter.LEFT_ARM, LeverParameter.RIGHT_ARM).forEach { parameter ->
            val left = parameter == LeverParameter.LEFT_ARM
            val sign = if (left) -1f else 1f
            val arm = if (left) leftArm else rightArm
            val x = centerX + unit * arm * sign - 24.dp
            val y = centerY + unit * arm * sign * slope + 14.dp
            var dragArm by remember(parameter) { mutableDoubleStateOf(arm.toDouble()) }
            val dragState = rememberDraggableState { delta ->
                dragArm = (dragArm + delta / unitPx * sign).coerceIn(parameter.minimum, parameter.maximum)
                currentOnChange(parameter, dragArm)
            }
            Surface(
                modifier = Modifier.offset(x, y).size(48.dp).testTag(if (left) "lever-weight-left" else "lever-weight-right")
                    .draggable(dragState, Orientation.Horizontal, enabled = enabled,
                        onDragStarted = {
                            dragArm = LeverPhysics.value(currentParameters, parameter)
                            currentOnStart()
                        },
                        onDragStopped = { currentOnFinish(parameter) })
                    .semantics { contentDescription = if (left) "左侧重物，水平拖动调整力臂" else "右侧重物，水平拖动调整力臂" },
                color = if (left) leftColor else rightColor, shape = ControlShape,
                border = BorderStroke(1.dp, if (left) leftColor else rightColor),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("${number(if (left) parameters.leftForce else parameters.rightForce)} N",
                        color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium)
                    Mark(Glyph.MOVE, androidx.compose.ui.graphics.Color.White, Modifier.size(14.dp))
                }
            }
        }
    }
}
