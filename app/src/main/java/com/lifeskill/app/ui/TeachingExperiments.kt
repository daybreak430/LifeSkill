package com.lifeskill.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var parameters by remember(block.id) { mutableStateOf(state.lever ?: block.initial) }
    var editing by remember { mutableStateOf(false) }
    // 仓库回执更新另一视图；正在拖动时保留本地预览，松手只提交被调整的字段。
    LaunchedEffect(state.lever, block.initial) {
        if (!editing) parameters = state.lever ?: block.initial
    }
    fun change(parameter: LeverParameter, value: Double) {
        parameters = LeverPhysics.update(parameters, parameter, LeverPhysics.snap(parameter, value))
    }
    fun finish(parameter: LeverParameter) {
        editing = false
        onAction(TeachingAction.SetLeverParameter(parameter, LeverPhysics.value(parameters, parameter)))
    }
    val observation = LeverPhysics.observe(parameters)
    val angle by animateFloatAsState(observation.illustrationAngle, tween(220), label = "杠杆倾斜")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(block.prompt, color = Ink, style = MaterialTheme.typography.bodyMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = expanded && maxWidth >= 600.dp
            // 横屏放大时把控件放到场景侧面，避免实验场景占满视口而看不到结果与调节入口。
            val scene: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LeverScene(parameters, angle, enabled, if (wide) 200 else if (expanded) 280 else 210,
                        onStart = { editing = true }, onChange = ::change, onFinish = ::finish)
                    Text("理想轻杆 · 力臂为水平距离 · 倾斜示意", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            val controls: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeverReadout(parameters, wide)
                    LeverControls(parameters, enabled, wide,
                        onChange = { parameter, value -> editing = true; change(parameter, value) }, onFinish = ::finish)
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
private fun LeverControls(
    parameters: LeverParameters, enabled: Boolean, inlineLabels: Boolean,
    onChange: (LeverParameter, Double) -> Unit, onFinish: (LeverParameter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            LeverParameter.LEFT_FORCE to LeverParameter.RIGHT_FORCE,
            LeverParameter.LEFT_ARM to LeverParameter.RIGHT_ARM,
        ).forEach { (left, right) ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(left, right).forEach { parameter ->
                    val label = when (parameter) {
                        LeverParameter.LEFT_FORCE -> "左侧力"
                        LeverParameter.RIGHT_FORCE -> "右侧力"
                        LeverParameter.LEFT_ARM -> "左侧力臂"
                        LeverParameter.RIGHT_ARM -> "右侧力臂"
                    }
                    val unit = if (parameter == LeverParameter.LEFT_FORCE || parameter == LeverParameter.RIGHT_FORCE) "N" else "m"
                    val value = LeverPhysics.value(parameters, parameter)
                    Column(Modifier.weight(1f)) {
                        if (inlineLabels) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text("${number(value)} $unit", color = Ink, style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text("${number(value)} $unit", color = Ink, style = MaterialTheme.typography.labelLarge)
                        }
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { onChange(parameter, it.toDouble()) },
                            onValueChangeFinished = { onFinish(parameter) },
                            valueRange = parameter.minimum.toFloat()..parameter.maximum.toFloat(),
                            steps = ((parameter.maximum - parameter.minimum) / parameter.increment).toInt() - 1,
                            enabled = enabled,
                            modifier = Modifier.testTag("lever-${parameter.name.lowercase()}")
                                .semantics { contentDescription = "调整$label，单位$unit" },
                        )
                    }
                }
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
