package com.lifeskill.app.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/** 参数实验台：学习者拖动参数，观察状态随之变化。杠杆与复利只是两个模型，不是两种组件。 */
enum class ExperimentModel(val id: String) {
    LEVER("lever"),
    COMPOUND("compound");

    companion object {
        fun from(id: String): ExperimentModel? = entries.firstOrNull { it.id == id }
    }
}

data class ExperimentParameter(
    val id: String,
    val label: String,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
    val default: Double,
    val decimals: Int = 0,
) {
    fun isValid(value: Double): Boolean = value.isFinite() && value in minimum..maximum &&
        abs((value - minimum) / step - round((value - minimum) / step)) < 1e-6

    // 先对齐到最小值的整数步长，再夹到边界；避免滑块小数误差累积成非法值。
    fun snap(value: Double): Double {
        require(value.isFinite())
        val steps = round((value - minimum) / step)
        return (minimum + steps * step).coerceIn(minimum, maximum)
    }

    fun format(value: Double): String = when {
        decimals > 0 -> String.format(Locale.CHINA, "%.${decimals}f", value)
        // 金额按千分位分组，避免 200000 读成两万或二十万要数零。
        unit == "元" -> String.format(Locale.CHINA, "%,d", value.roundToInt())
        else -> value.roundToInt().toString()
    }

    fun text(value: Double): String = if (unit == "%") "${format(value)}%" else "${format(value)} $unit"
}

/** 模型参数表。新增模型只需在这里登记参数与计算方式，协议与渲染不需要新组件类型。 */
internal object ExperimentCatalog {
    private val lever = listOf(
        ExperimentParameter("leftForce", "左侧力", "N", 1.0, 10.0, 1.0, 4.0),
        ExperimentParameter("rightForce", "右侧力", "N", 1.0, 10.0, 1.0, 2.0),
        ExperimentParameter("leftArm", "左侧力臂", "m", 1.0, 4.0, 0.5, 2.0),
        ExperimentParameter("rightArm", "右侧力臂", "m", 1.0, 4.0, 0.5, 3.0),
    )
    private val compound = listOf(
        ExperimentParameter("principal", "本金", "元", 10_000.0, 200_000.0, 5_000.0, 50_000.0),
        ExperimentParameter("monthly", "每月投入", "元", 0.0, 5_000.0, 500.0, 2_000.0),
        ExperimentParameter("rate", "年化收益", "%", 1.0, 15.0, 0.5, 8.0, decimals = 1),
        ExperimentParameter("years", "年限", "年", 1.0, 30.0, 1.0, 10.0),
    )

    fun parameters(model: ExperimentModel): List<ExperimentParameter> = when (model) {
        ExperimentModel.LEVER -> lever
        ExperimentModel.COMPOUND -> compound
    }

    fun parameter(model: ExperimentModel, id: String): ExperimentParameter? =
        parameters(model).firstOrNull { it.id == id }

    fun defaults(model: ExperimentModel): Map<String, Double> = parameters(model).associate { it.id to it.default }
}

data class ExperimentValues(val model: ExperimentModel, val values: Map<String, Double>) {
    fun value(id: String): Double = values[id] ?: 0.0

    companion object {
        fun defaults(model: ExperimentModel) = ExperimentValues(model, ExperimentCatalog.defaults(model))
    }
}

/** 参数实验组件。杠杆是默认模型，不是组件类型本身。 */
data class TeachingExperiment(
    override val id: String, override val title: String, val prompt: String,
    val model: ExperimentModel = ExperimentModel.LEVER,
    val initial: ExperimentValues = ExperimentValues.defaults(model),
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

data class ExperimentMetric(val label: String, val value: String)

data class ExperimentObservation(
    val headline: String,
    val metrics: List<ExperimentMetric>,
    val lever: LeverObservation? = null,
    // 复利模型的投入占比，用于一条比例带；杠杆模型不使用。
    val investedShare: Float? = null,
)

internal fun ExperimentValues.leverParameters(): LeverParameters = LeverParameters(
    leftForce = value("leftForce"), rightForce = value("rightForce"),
    leftArm = value("leftArm"), rightArm = value("rightArm"),
)

internal object ExperimentMath {
    fun isValid(values: ExperimentValues): Boolean =
        ExperimentCatalog.parameters(values.model).all { it.isValid(values.value(it.id)) }

    fun update(values: ExperimentValues, parameterId: String, raw: Double): ExperimentValues {
        val parameter = ExperimentCatalog.parameter(values.model, parameterId)
            ?: throw RuleViolation("未知的实验参数。")
        if (!raw.isFinite()) throw RuleViolation("实验参数超出范围或步长。")
        val next = parameter.snap(raw)
        if (!parameter.isValid(next)) throw RuleViolation("实验参数超出范围或步长。")
        return values.copy(values = values.values + (parameterId to next))
    }

    fun observe(values: ExperimentValues): ExperimentObservation {
        require(isValid(values))
        return when (values.model) {
            ExperimentModel.LEVER -> {
                val observation = LeverPhysics.observe(values.leverParameters())
                val status = when (observation.balance) {
                    LeverBalance.LEFT_DOWN -> "左侧下沉"
                    LeverBalance.RIGHT_DOWN -> "右侧下沉"
                    LeverBalance.BALANCED -> "已平衡"
                }
                ExperimentObservation(
                    headline = status,
                    metrics = listOf(
                        ExperimentMetric("左侧力矩", "${plain(observation.leftMoment)} N·m"),
                        ExperimentMetric("右侧力矩", "${plain(observation.rightMoment)} N·m"),
                    ),
                    lever = observation,
                )
            }
            ExperimentModel.COMPOUND -> {
                val principal = values.value("principal")
                val monthly = values.value("monthly")
                val rate = values.value("rate") / 100.0
                val months = values.value("years").toInt() * 12
                val monthlyRate = rate / 12.0
                // 本金按年复利，定投按月的复利终值公式累加。
                val principalFuture = principal * (1 + rate).pow(values.value("years").toInt())
                val monthlyFuture = if (monthlyRate == 0.0) monthly * months
                else monthly * (((1 + monthlyRate).pow(months) - 1) / monthlyRate)
                val total = principalFuture + monthlyFuture
                val invested = principal + monthly * months
                val growth = (total - invested).coerceAtLeast(0.0)
                ExperimentObservation(
                    headline = money(total),
                    metrics = listOf(
                        ExperimentMetric("累计投入", money(invested)),
                        ExperimentMetric("收益", money(growth)),
                        ExperimentMetric("收益占比", "${(growth / total * 100).roundToInt()}%"),
                    ),
                    investedShare = (invested / total).toFloat().coerceIn(0f, 1f),
                )
            }
        }
    }

    fun summary(values: ExperimentValues): String = when (values.model) {
        ExperimentModel.LEVER -> values.leverParameters().summary()
        ExperimentModel.COMPOUND -> {
            val observation = observe(values)
            val rate = ExperimentCatalog.parameter(values.model, "rate")!!.format(values.value("rate"))
            "复利实验：本金 ${money(values.value("principal"))}，每月投入 ${money(values.value("monthly"))}，" +
                "年化 $rate%，年限 ${values.value("years").roundToInt()} 年；期末 ${observation.headline}，" +
                observation.metrics.joinToString("，") { "${it.label} ${it.value}" }
        }
    }
}

private fun plain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.CHINA, "%.1f", value)

private fun money(value: Double): String = if (abs(value) >= 10_000) {
    String.format(Locale.CHINA, "%.1f 万", value / 10_000)
} else {
    String.format(Locale.CHINA, "%.0f 元", value)
}
