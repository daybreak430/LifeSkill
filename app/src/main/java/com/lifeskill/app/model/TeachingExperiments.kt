package com.lifeskill.app.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 参数实验台：学习者拖动参数，观察结果随之变化。
 *
 * 组件不内置任何具体模型。AI 判断当前主题适合“调一个量、看结果怎么变”时，
 * 当场给出参数表与结果表达式，应用用受限求值器计算；杠杆、复利只是两份示例数据。
 */
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
        abs(value) >= 10_000 -> String.format(Locale.CHINA, "%,d", value.roundToInt())
        else -> value.roundToInt().toString()
    }

    fun text(value: Double): String = if (unit == "%") "${format(value)}%" else "${format(value)} $unit"
}

/** 一个结果指标。expression 只能引用已声明的参数和前序结果，由受限求值器计算。 */
data class ExperimentOutput(
    val id: String,
    val label: String,
    val unit: String,
    val expression: String,
    val decimals: Int = 0,
) {
    fun format(value: Double): String = when {
        decimals > 0 -> String.format(Locale.CHINA, "%.${decimals}f", value)
        abs(value) >= 10_000 -> String.format(Locale.CHINA, "%,d", value.roundToInt())
        else -> value.roundToInt().toString()
    }

    fun text(value: Double): String = if (unit == "%") "${format(value)}%" else "${format(value)} $unit"
}

data class ExperimentValues(val values: Map<String, Double>) {
    fun value(id: String): Double = values[id] ?: 0.0
}

data class TeachingExperiment(
    override val id: String, override val title: String, val prompt: String,
    val parameters: List<ExperimentParameter>,
    val outputs: List<ExperimentOutput>,
    val initial: ExperimentValues = ExperimentValues(parameters.associate { it.id to it.default }),
) : TeachingBlock

data class ExperimentMetric(val label: String, val value: String)

data class ExperimentObservation(val headline: String, val metrics: List<ExperimentMetric>)

internal object ExperimentMath {
    fun isValid(values: ExperimentValues, parameters: List<ExperimentParameter>): Boolean =
        parameters.all { it.isValid(values.value(it.id)) }

    fun update(
        values: ExperimentValues, parameters: List<ExperimentParameter>, parameterId: String, raw: Double,
    ): ExperimentValues {
        val parameter = parameters.firstOrNull { it.id == parameterId }
            ?: throw RuleViolation("未知的实验参数。")
        if (!raw.isFinite()) throw RuleViolation("实验参数超出范围或步长。")
        val next = parameter.snap(raw)
        if (!parameter.isValid(next)) throw RuleViolation("实验参数超出范围或步长。")
        return values.copy(values = values.values + (parameterId to next))
    }

    fun observe(block: TeachingExperiment, values: ExperimentValues): ExperimentObservation {
        require(isValid(values, block.parameters))
        val computed = evaluate(block, values)
        val headline = block.outputs.first().text(computed.first())
        return ExperimentObservation(
            headline = headline,
            metrics = block.outputs.drop(1).mapIndexed { index, output ->
                ExperimentMetric(output.label, output.text(computed[index + 1]))
            },
        )
    }

    /** 依次求值，后面的表达式可以引用前面的结果 id。 */
    fun evaluate(block: TeachingExperiment, values: ExperimentValues): List<Double> {
        val scope = values.values.toMutableMap()
        return block.outputs.map { output ->
            val result = ExperimentExpression.evaluate(output.expression, scope)
            scope[output.id] = result
            result
        }
    }

    fun summary(block: TeachingExperiment, values: ExperimentValues): String {
        val result = runCatching { observe(block, values) }.getOrNull() ?: return block.prompt
        val params = block.parameters.joinToString("，") { "${it.label} ${it.text(values.value(it.id))}" }
        val metrics = listOf(block.outputs.first().label to result.headline) +
            result.metrics.map { it.label to it.value }
        return "${block.title}：$params；" + metrics.joinToString("，") { "${it.first} ${it.second}" }
    }
}

/**
 * 受限表达式求值器：只支持数字、变量、四则运算、乘方、括号和少量函数。
 * 不做属性访问、字符串、赋值或循环，长度与递归深度都有上限，解析失败整块丢弃。
 */
internal object ExperimentExpression {
    private const val MaxLength = 300
    private const val MaxDepth = 24

    fun evaluate(expression: String, variables: Map<String, Double>): Double {
        require(expression.length in 1..MaxLength)
        val value = try {
            val parser = Parser(expression, variables)
            parser.expression(0).also { parser.requireEnd() }
        } catch (error: IllegalStateException) {
            // error(...) 抛的是 IllegalStateException，统一成可预期的参数错误。
            throw IllegalArgumentException(error.message ?: "表达式不合法")
        }
        require(value.isFinite() && abs(value) <= 1e12) { "实验结果超出可显示范围" }
        return value
    }

    private class Parser(private val source: String, private val variables: Map<String, Double>) {
        private var index = 0

        fun expression(depth: Int): Double {
            require(depth <= MaxDepth) { "表达式嵌套过深" }
            var value = term(depth)
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> { index++; value + term(depth) }
                    '-' -> { index++; value - term(depth) }
                    else -> return value
                }
            }
        }

        private fun term(depth: Int): Double {
            var value = unary(depth)
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> { index++; value * unary(depth) }
                    '/' -> {
                        index++
                        val divisor = unary(depth)
                        require(divisor != 0.0) { "除数不能为 0" }
                        value / divisor
                    }
                    '%' -> {
                        index++
                        val divisor = unary(depth)
                        require(divisor != 0.0) { "除数不能为 0" }
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun unary(depth: Int): Double {
            skipSpaces()
            return when (peek()) {
                '-' -> { index++; -unary(depth) }
                '+' -> { index++; unary(depth) }
                else -> power(depth)
            }
        }

        private fun power(depth: Int): Double {
            val base = primary(depth)
            skipSpaces()
            if (peek() != '^') return base
            index++
            // 乘方右结合：2^3^2 = 2^(3^2)。
            return base.pow(power(depth))
        }

        private fun primary(depth: Int): Double {
            skipSpaces()
            val current = peek() ?: error("表达式不完整")
            if (current == '(') {
                index++
                val value = expression(depth + 1)
                skipSpaces()
                require(peek() == ')') { "缺少右括号" }
                index++
                return value
            }
            if (current.isDigit() || current == '.') return number()
            if (current.isLetter() || current == '_') return identifier(depth)
            error("无法识别的符号：$current")
        }

        private fun number(): Double {
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            val text = source.substring(start, index)
            require(text.count { it == '.' } <= 1) { "数字格式错误" }
            return text.toDoubleOrNull() ?: error("数字格式错误")
        }

        private fun identifier(depth: Int): Double {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
            val name = source.substring(start, index)
            skipSpaces()
            if (peek() == '(') {
                index++
                val args = mutableListOf<Double>()
                skipSpaces()
                if (peek() == ')') {
                    index++
                } else {
                    while (true) {
                        args += expression(depth + 1)
                        skipSpaces()
                        when (peek()) {
                            ',' -> index++
                            ')' -> { index++; break }
                            else -> error("函数参数缺少分隔或右括号")
                        }
                    }
                }
                return function(name, args)
            }
            return constant(name) ?: variables[name] ?: error("未知变量：$name")
        }

        private fun function(name: String, args: List<Double>): Double = when (name) {
            "pow" -> { require(args.size == 2); args[0].pow(args[1]) }
            "sqrt" -> { require(args.size == 1); require(args[0] >= 0); sqrt(args[0]) }
            "abs" -> { require(args.size == 1); abs(args[0]) }
            "round" -> { require(args.size == 1); round(args[0]) }
            "floor" -> { require(args.size == 1); floor(args[0]) }
            "ceil" -> { require(args.size == 1); ceil(args[0]) }
            "min" -> { require(args.size == 2); minOf(args[0], args[1]) }
            "max" -> { require(args.size == 2); maxOf(args[0], args[1]) }
            else -> error("不支持的函数：$name")
        }

        private fun constant(name: String): Double? = when (name) {
            "pi" -> Math.PI
            "e" -> Math.E
            else -> null
        }

        private fun peek(): Char? = source.getOrNull(index)

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        fun requireEnd() {
            skipSpaces()
            require(index == source.length) { "表达式末尾有多余内容" }
        }
    }
}
