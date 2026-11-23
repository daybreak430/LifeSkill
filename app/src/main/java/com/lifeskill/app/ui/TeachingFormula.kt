package com.lifeskill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.TeachingFormula

/**
 * 受限公式记号：只做排版，不求值。
 * 支持 frac{a}{b}、sqrt{x}、x^{2}、a_{i}，以及 \times、\pm、\le 等常用符号。
 * 不支持函数图、矩阵和多层嵌套，解析失败时整行按原文显示。
 */
private sealed interface MathAtom {
    data class Run(val text: String) : MathAtom
    data class Sup(val text: String) : MathAtom
    data class Sub(val text: String) : MathAtom
    data class Frac(val numerator: List<MathAtom>, val denominator: List<MathAtom>) : MathAtom
    data class Sqrt(val body: List<MathAtom>) : MathAtom
}

private val mathSymbols = mapOf(
    "times" to "×", "cdot" to "·", "pm" to "±", "mp" to "∓", "le" to "≤", "leq" to "≤",
    "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠", "approx" to "≈", "infty" to "∞",
    "pi" to "π", "alpha" to "α", "beta" to "β", "gamma" to "γ", "theta" to "θ", "lambda" to "λ",
    "mu" to "μ", "sigma" to "σ", "omega" to "ω", "Delta" to "Δ", "sum" to "∑", "int" to "∫",
)

private class MathParser(private val source: String) {
    private var index = 0

    fun parse(depth: Int = 0): List<MathAtom> = parseUntil(null, depth)

    private fun parseUntil(stop: Char?, depth: Int): List<MathAtom> {
        val atoms = mutableListOf<MathAtom>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                atoms += MathAtom.Run(buffer.toString())
                buffer.clear()
            }
        }
        while (index < source.length) {
            val current = source[index]
            if (stop != null && current == stop) {
                index++
                break
            }
            when {
                source.startsWith("\\frac", index) || source.startsWith("frac", index) -> {
                    flush()
                    index += if (source.startsWith("\\frac", index)) 5 else 4
                    val numerator = group(depth + 1) ?: return atoms
                    val denominator = group(depth + 1) ?: return atoms
                    atoms += MathAtom.Frac(numerator, denominator)
                }
                source.startsWith("\\sqrt", index) || source.startsWith("sqrt", index) -> {
                    flush()
                    index += if (source.startsWith("\\sqrt", index)) 5 else 4
                    val body = group(depth + 1) ?: return atoms
                    atoms += MathAtom.Sqrt(body)
                }
                current == '^' || current == '_' -> {
                    flush()
                    index++
                    val content = argument(depth + 1).orEmpty()
                    if (current == '^') atoms += MathAtom.Sup(content) else atoms += MathAtom.Sub(content)
                }
                current == '{' -> {
                    // 普通分组：括号本身不显示，内容按顺序铺开。
                    flush()
                    index++
                    atoms += parseUntil('}', depth)
                }
                current == '\\' -> {
                    val start = index + 1
                    var end = start
                    while (end < source.length && source[end].isLetter()) end++
                    val name = source.substring(start, end)
                    val symbol = mathSymbols[name]
                    if (symbol != null) {
                        buffer.append(symbol)
                        index = end
                    } else {
                        buffer.append(current)
                        index++
                    }
                }
                else -> {
                    buffer.append(current)
                    index++
                }
            }
        }
        flush()
        return atoms
    }

    // 读取 { ... }，没有花括号时取单个字符，方便写 x^2 这类短写法。
    private fun group(depth: Int): List<MathAtom>? {
        if (depth > 3) return null
        if (index >= source.length) return null
        if (source[index] != '{') {
            val single = source[index].toString()
            index++
            return listOf(MathAtom.Run(single))
        }
        index++
        return parseUntil('}', depth)
    }

    private fun argument(depth: Int): String? {
        if (depth > 3 || index >= source.length) return null
        if (source[index] != '{') {
            val single = source[index].toString()
            index++
            return single
        }
        index++
        val start = index
        var level = 1
        while (index < source.length) {
            when (source[index]) {
                '{' -> level++
                '}' -> {
                    level--
                    if (level == 0) {
                        val text = source.substring(start, index)
                        index++
                        return text
                    }
                }
            }
            index++
        }
        return source.substring(start)
    }
}

@Composable
internal fun TeachingFormulaContent(block: TeachingFormula) {
    Column(
        Modifier.fillMaxWidth().testTag("formula-${block.id}")
            .background(Ground, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics { contentDescription = "公式：" + block.lines.joinToString("；") + (block.caption?.let { "。$it" } ?: "") },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        block.lines.forEach { line -> FormulaLine(line) }
        block.caption?.let {
            Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

/**
 * 一行公式：优先按原字号显示；放不下时等比缩小到最小可读字号；
 * 仍然放不下才在等号处折成两行，避免业务公式被挤成需要横滑的长条。
 */
@Composable
private fun FormulaLine(source: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val available = constraints.maxWidth.toFloat()
        val plan = remember(source, available, density.fontScale) {
            planFormula(source, available, measurer, density)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            plan.forEach { line ->
                Row(
                    if (line.scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MathAtoms(line.atoms, line.scale)
                }
            }
        }
    }
}

private data class PlannedFormulaLine(val atoms: List<MathAtom>, val scale: Float, val scrollable: Boolean)

// 低于这个比例就不如折行：约 14.4sp，再小会失去公式的可读性。
private const val MinFormulaScale = 0.76f

/**
 * 在第一个等号处拆成左右两段，等号留在左段。
 * 只处理有等号的算式；没有等号的长表达式保持原样，交给横滑兜底。
 */
internal fun formulaChunks(source: String): List<String> {
    val trimmed = source.trim()
    val equals = trimmed.indexOf('=')
    if (equals <= 0 || equals >= trimmed.lastIndex) return listOf(trimmed)
    val left = trimmed.substring(0, equals + 1).trim()
    val right = trimmed.substring(equals + 1).trim()
    return if (right.isEmpty()) listOf(trimmed) else listOf(left, right)
}

/** 按可用宽度算缩放比例；下限由调用方决定，避免公式缩得比正文还小。 */
internal fun fitFormulaScale(measured: Float, available: Float, min: Float = MinFormulaScale): Float {
    if (measured <= 0f) return 1f
    return (available / measured * .98f).coerceIn(min, 1f)
}

private fun planFormula(
    source: String,
    available: Float,
    measurer: TextMeasurer,
    density: Density,
): List<PlannedFormulaLine> {
    val atoms = MathParser(source).parse()
    val full = measureAtoms(atoms, 1f, measurer, density)
    if (full <= available) return listOf(PlannedFormulaLine(atoms, 1f, false))
    val fitted = fitFormulaScale(full, available)
    if (full * fitted <= available) return listOf(PlannedFormulaLine(atoms, fitted, false))
    val chunks = formulaChunks(source)
    if (chunks.size > 1) {
        return chunks.map { chunk ->
            val chunkAtoms = MathParser(chunk).parse()
            val width = measureAtoms(chunkAtoms, 1f, measurer, density)
            if (width <= available) {
                PlannedFormulaLine(chunkAtoms, 1f, false)
            } else {
                val scale = fitFormulaScale(width, available)
                PlannedFormulaLine(chunkAtoms, scale, width * scale > available)
            }
        }
    }
    return listOf(PlannedFormulaLine(atoms, fitted, true))
}

// 逐个原子累加宽度：分式取分子分母较宽的一侧，根号补上符号与线宽，近似排版结果。
private fun measureAtoms(atoms: List<MathAtom>, scale: Float, measurer: TextMeasurer, density: Density): Float {
    var total = 0f
    atoms.forEach { atom ->
        total += when (atom) {
            is MathAtom.Run -> measureText(atom.text, 19 * scale, measurer, density)
            is MathAtom.Sup -> measureText(atom.text, 12 * scale, measurer, density)
            is MathAtom.Sub -> measureText(atom.text, 12 * scale, measurer, density)
            is MathAtom.Frac -> maxOf(
                measureAtoms(atom.numerator, scale * .82f, measurer, density),
                measureAtoms(atom.denominator, scale * .82f, measurer, density),
            ) + with(density) { 2.dp.toPx() }
            is MathAtom.Sqrt -> measureText("√", 21 * scale, measurer, density) +
                measureAtoms(atom.body, scale * .9f, measurer, density) + with(density) { 4.dp.toPx() }
        } + with(density) { 2.dp.toPx() }
    }
    return total
}

private fun measureText(text: String, fontSize: Float, measurer: TextMeasurer, density: Density): Float {
    if (text.isEmpty()) return 0f
    val style = TextStyle(fontFamily = FontFamily.Serif, fontSize = fontSize.sp, lineHeight = fontSize.sp * 1.3f)
    return with(density) { measurer.measure(text, style).size.width.toFloat() }
}

@Composable
private fun MathAtoms(atoms: List<MathAtom>, scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        atoms.forEach { atom ->
            when (atom) {
                is MathAtom.Run -> Text(
                    atom.text, color = Ink, fontFamily = FontFamily.Serif,
                    fontSize = (19 * scale).sp, lineHeight = (24 * scale).sp, maxLines = 1,
                )
                is MathAtom.Sup -> Text(
                    atom.text, color = Ink, fontFamily = FontFamily.Serif,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.offset(y = (-7 * scale).dp),
                )
                is MathAtom.Sub -> Text(
                    atom.text, color = Ink, fontFamily = FontFamily.Serif,
                    fontSize = (12 * scale).sp,
                    modifier = Modifier.offset(y = (7 * scale).dp),
                )
                is MathAtom.Frac -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MathAtoms(atom.numerator, scale * .82f)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
                    MathAtoms(atom.denominator, scale * .82f)
                }
                is MathAtom.Sqrt -> Row(verticalAlignment = Alignment.Top) {
                    Text("√", color = Ink, fontFamily = FontFamily.Serif, fontSize = (21 * scale).sp)
                    Column {
                        MathAtoms(atom.body, scale * .9f)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
                    }
                }
            }
        }
    }
}
