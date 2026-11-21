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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
        block.lines.forEach { line ->
            val atoms = remember(line) { MathParser(line).parse() }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MathAtoms(atoms)
            }
        }
        block.caption?.let {
            Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
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
