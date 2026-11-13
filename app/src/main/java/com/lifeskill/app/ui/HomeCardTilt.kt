package com.lifeskill.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.awaitCancellation
import kotlin.math.asin
import kotlin.math.exp

internal const val MAX_HOME_TILT_DEGREES = 5f
private const val TILT_DEAD_ZONE_DEGREES = 1.2f
private const val TILT_GAIN = .42f
private const val TILT_SMOOTHING_SECONDS = .10f
private const val CAMERA_POINTS_PER_INCH = 72f

@Composable
internal fun rememberHomeCardTilt(enabled: Boolean): State<Offset> {
    val view = LocalView.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val manager = remember(view) { view.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        ?: return tilt
    val sensor = remember(manager) {
        manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    } ?: return tilt
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val motionEnabled = durationScale > 0f
    val listening = enabled && lifecycle == Lifecycle.State.RESUMED && LocalWindowInfo.current.isWindowFocused

    LaunchedEffect(listening, motionEnabled, view, manager, sensor) {
        if (!motionEnabled) {
            tilt.value = Offset.Zero
        } else if (listening) {
            val listener = HomeTiltListener(view, { tilt.value }) { tilt.value = it }
            if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                try {
                    awaitCancellation()
                } finally {
                    manager.unregisterListener(listener)
                }
            } else {
                tilt.value = Offset.Zero
            }
        } else if (tilt.value != Offset.Zero) {
            Animatable(tilt.value, Offset.VectorConverter).animateTo(Offset.Zero, tween(240)) { tilt.value = value }
        }
    }

    return tilt
}

internal fun Modifier.homeCardTilt(tilt: State<Offset>): Modifier = graphicsLayer {
    val angles = tilt.value
    rotationX = angles.x
    rotationY = angles.y
    // Android 底层相机会将距离乘以 72，换算后让透视距离随卡片尺寸等比变化。
    cameraDistance = (maxOf(size.width, size.height) * 1.35f / CAMERA_POINTS_PER_INCH).coerceAtLeast(1f)
}

private class HomeTiltListener(
    private val view: View,
    private val currentTilt: () -> Offset,
    private val updateTilt: (Offset) -> Unit,
) : SensorEventListener {
    private val rotation = FloatArray(9)
    private val neutral = FloatArray(9)
    private var displayRotation = -1
    private var lastTimestamp = 0L

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val screenRotation = view.display?.rotation ?: Surface.ROTATION_0
        if (screenRotation != displayRotation) {
            // 每次恢复监听以当前握姿为基准，横屏时按显示方向重新映射屏幕轴。
            val (horizontal, vertical) = when (screenRotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rotation, horizontal, vertical, neutral)
            displayRotation = screenRotation
            lastTimestamp = event.timestamp
            return
        }
        val target = homeTiltAngles(neutral, rotation)
        val elapsed = ((event.timestamp - lastTimestamp) / 1_000_000_000f).coerceIn(0f, .05f)
        lastTimestamp = event.timestamp
        val previous = currentTilt()
        // 按采样时间滤掉抖动，接近目标时收敛为固定值，避免持续刷新卡面。
        val smoothed = lerp(previous, target, 1f - exp(-elapsed / TILT_SMOOTHING_SECONDS))
        updateTilt(if ((smoothed - target).getDistance() < .015f) target else smoothed)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

internal fun homeTiltAngles(neutral: FloatArray, rotation: FloatArray): Offset {
    // 把当前屏幕法线投影到基准屏幕的两个轴。绕法线转动手机时，卡片不会平面旋转。
    val normalOnRight = neutral[0] * rotation[2] + neutral[3] * rotation[5] + neutral[6] * rotation[8]
    val normalOnUp = neutral[1] * rotation[2] + neutral[4] * rotation[5] + neutral[7] * rotation[8]
    val angles = Offset(
        -asin(normalOnUp.coerceIn(-1f, 1f)),
        asin(normalOnRight.coerceIn(-1f, 1f)),
    ) * (180f / Math.PI.toFloat())
    val magnitude = angles.getDistance()
    if (magnitude <= TILT_DEAD_ZONE_DEGREES) return Offset.Zero
    val tilt = ((magnitude - TILT_DEAD_ZONE_DEGREES) * TILT_GAIN).coerceAtMost(MAX_HOME_TILT_DEGREES)
    return angles * (tilt / magnitude)
}
