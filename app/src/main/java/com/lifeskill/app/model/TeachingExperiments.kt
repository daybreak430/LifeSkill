package com.lifeskill.app.model

import kotlin.math.abs
import kotlin.math.round

/** 理想轻杆模板：两侧受竖直向下的力，力臂为水平距离；支点固定，不计杆重、摩擦与动态振荡。 */
data class TeachingExperiment(
    override val id: String, override val title: String, val prompt: String,
    val initial: LeverParameters = LeverParameters(),
) : TeachingBlock

data class LeverParameters(
    val leftForce: Double = 4.0, val rightForce: Double = 2.0,
    val leftArm: Double = 2.0, val rightArm: Double = 3.0,
)

enum class LeverParameter(val minimum: Double, val maximum: Double, val increment: Double) {
    LEFT_FORCE(1.0, 10.0, 1.0), RIGHT_FORCE(1.0, 10.0, 1.0),
    LEFT_ARM(1.0, 4.0, .5), RIGHT_ARM(1.0, 4.0, .5),
}

enum class LeverBalance { LEFT_DOWN, BALANCED, RIGHT_DOWN }

data class LeverObservation(val leftMoment: Double, val rightMoment: Double, val balance: LeverBalance) {
    // 倾角仅用于方向示意，不将力矩差伪装成真实静止角度或运动学仿真。
    val illustrationAngle: Float get() = ((rightMoment - leftMoment) / (leftMoment + rightMoment) * 16.0).toFloat()
}

internal object LeverPhysics {
    fun value(parameters: LeverParameters, parameter: LeverParameter): Double = when (parameter) {
        LeverParameter.LEFT_FORCE -> parameters.leftForce
        LeverParameter.RIGHT_FORCE -> parameters.rightForce
        LeverParameter.LEFT_ARM -> parameters.leftArm
        LeverParameter.RIGHT_ARM -> parameters.rightArm
    }

    fun isValid(parameters: LeverParameters): Boolean = LeverParameter.entries.all { parameter ->
        val value = value(parameters, parameter)
        value.isFinite() && value in parameter.minimum..parameter.maximum &&
            abs(value / parameter.increment - round(value / parameter.increment)) < .000001
    }

    fun snap(parameter: LeverParameter, value: Double): Double {
        require(value.isFinite())
        return (round(value / parameter.increment) * parameter.increment).coerceIn(parameter.minimum, parameter.maximum)
    }

    fun update(parameters: LeverParameters, parameter: LeverParameter, value: Double): LeverParameters {
        val updated = when (parameter) {
            LeverParameter.LEFT_FORCE -> parameters.copy(leftForce = value)
            LeverParameter.RIGHT_FORCE -> parameters.copy(rightForce = value)
            LeverParameter.LEFT_ARM -> parameters.copy(leftArm = value)
            LeverParameter.RIGHT_ARM -> parameters.copy(rightArm = value)
        }
        if (!isValid(updated)) throw RuleViolation("实验参数超出范围或步长。")
        return updated
    }

    fun observe(parameters: LeverParameters): LeverObservation {
        require(isValid(parameters))
        val left = parameters.leftForce * parameters.leftArm
        val right = parameters.rightForce * parameters.rightArm
        val balance = when {
            abs(left - right) < .000001 -> LeverBalance.BALANCED
            left > right -> LeverBalance.LEFT_DOWN
            else -> LeverBalance.RIGHT_DOWN
        }
        return LeverObservation(left, right, balance)
    }
}

internal fun LeverParameters.summary(): String {
    val observation = LeverPhysics.observe(this)
    val result = when (observation.balance) {
        LeverBalance.LEFT_DOWN -> "左侧下沉"
        LeverBalance.RIGHT_DOWN -> "右侧下沉"
        LeverBalance.BALANCED -> "已平衡"
    }
    return "左侧 ${leftForce} N × ${leftArm} m = ${observation.leftMoment} N·m；" +
        "右侧 ${rightForce} N × ${rightArm} m = ${observation.rightMoment} N·m；$result"
}
