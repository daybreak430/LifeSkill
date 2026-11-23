package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.LessonFlow
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as PlainText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.net.URI

private val lessonParser = Parser.builder().extensions(listOf(TablesExtension.create()))
    .includeSourceSpans(IncludeSourceSpans.BLOCKS).build()
private val decisionShape = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.height / 2)
    lineTo(size.width / 2, size.height)
    lineTo(0f, size.height / 2)
    close()
}

@Composable
internal fun LessonMarkdown(source: String) {
    // 教学回复经常只用单个换行分段，CommonMark 把它当段内软换行，渲染出来仍是整段。
    // 先把围栏、表格和字符图之外的单换行升级为段落分隔，再解析，
    // 让段落间距稳定出现，不依赖模型遵循提示词。
    val normalized = remember(source) { normalizeLessonBreaks(source) }
    val document = remember(normalized) { lessonParser.parse(normalized) }
    Column(verticalArrangement = Arrangement.spacedBy(ParagraphGap)) {
        document.children().forEach { LessonBlock(it, normalized) }
    }
}

// 正文排版集中在几个常量里：行高与段距决定段落边界是否可辨，
// 行内数字启用等宽（tnum），金额和百分比才能按列对齐。
private val ParagraphGap = 14.dp
private val ListItemGap = 8.dp
private val BulletSize = 5.dp

@Composable
private fun lessonBody() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp, fontFeatureSettings = "tnum")

// 等式行比正文略小、加粗并单独成块，避免和普通段落抢同一层阅读权重。
private val FormulaShape = RoundedCornerShape(9.dp)

private val fencePattern = Regex("^(```+|~~~+)")
private val asciiBorderPattern = Regex("""\+[=-]{2,}\+""")
private val asciiBranchPattern = Regex("""^[ |]*[+|`\\]--""")

// 等式识别：左端是短名词短语、含单个等号、不以句末标点收尾。
// 阈值刻意保守，宁可把公式留在正文里，也不要把带等号的普通句子变成色块。
private val formulaPattern = Regex("""^[^。！？；，、：:]{1,14}\s*[=＝]\s*[^。！？]{1,60}$""")
private val datePattern = Regex("""\d{4}-\d{1,2}-\d{1,2}""")
private val spacedEquals = Regex("""(?<![=<>!])\s*[=＝]\s*(?!=)""")
private val digitMinus = Regex("""(?<=\d)\s*-\s*(?=\d)""")
// 中文算式里的减号两侧通常是名词短语，靠空白识别，避免把“3-5 天”这类区间误改。
private val spacedHyphen = Regex("""\s-\s""")
// 数字之间的运算符用不换行空格粘住，避免算式被行尾拆成“200 - 100 -”＋“20”。
private val breakableDigitOperator = Regex("""(?<=\d) ([=＝−+×÷]) (?=\d)""")

/**
 * 把行内算式里的 ASCII 减号换成两侧带空白的减号，等号也统一留白。
 * 只在确定是算式的文本里调用，并且跳过日期（2024-01-05）这类会被误伤的写法。
 */
internal fun normalizeEquationOperators(text: String): String {
    if (!text.contains('=') && !text.contains('＝')) return text
    if (datePattern.containsMatchIn(text)) return text
    return text.replace(spacedEquals, " = ")
        .replace(digitMinus, " − ")
        .replace(spacedHyphen, " − ")
        .replace(breakableDigitOperator) { "\u00A0${it.groupValues[1]}\u00A0" }
}

/** 整段就是一个等式时返回可用于独立排版的内容，否则返回 null。 */
internal fun formulaCandidate(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.length > 80 || !formulaPattern.matches(trimmed)) return null
    return normalizeEquationOperators(trimmed)
}

/**
 * 兜底小标题：模型常把引导句写成独立短段并以冒号结尾（“先讲一个底层公式：”）。
 * 渲染层据此提升层级，不依赖模型输出 Markdown 标题。
 */
internal fun subheadCandidate(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.length !in 2..24) return null
    if (!trimmed.endsWith("：") && !trimmed.endsWith(":")) return null
    if (trimmed.any { it in "。！？；=" } || trimmed.contains('＝')) return null
    return trimmed
}

// 表格行和字符图行必须保持单换行：表格靠连续行识别，
// 未围栏的字符图靠整段 sourceSpans 识别，拆开就都不成立。
private fun isTableOrDiagramLine(line: String): Boolean {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("|")) return true
    return trimmed.any { it in '\u2500'..'\u257f' } ||
        asciiBorderPattern.containsMatchIn(trimmed) || asciiBranchPattern.containsMatchIn(trimmed)
}

internal fun normalizeLessonBreaks(source: String): String {
    val lines = source.split('\n')
    val out = StringBuilder(source.length + lines.size)
    var inFence = false
    var fenceMarker = "```"
    lines.forEachIndexed { index, line ->
        if (index > 0) {
            val previous = lines[index - 1]
            val keepSingle = inFence || line.isBlank() || previous.isBlank() ||
                isTableOrDiagramLine(line) || isTableOrDiagramLine(previous)
            out.append(if (keepSingle) '\n' else "\n\n")
        }
        out.append(line)
        val trimmed = line.trimStart()
        if (inFence) {
            if (trimmed.startsWith(fenceMarker)) inFence = false
        } else {
            fencePattern.find(trimmed)?.let {
                inFence = true
                fenceMarker = it.groupValues[1]
            }
        }
    }
    return out.toString()
}

@Composable
private fun LessonBlock(node: Node, source: String) {
    when (node) {
        is Heading -> Text(
            node.richText(), color = Ink,
            style = if (node.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
        is Paragraph -> {
            val diagram = remember(node, source) {
                // CommonMark 会整理段落缩进，识别未围栏的示意图时读取每行原文。
                node.sourceSpans.joinToString("\n") { source.substring(it.inputIndex, it.inputIndex + it.length) }
                    .takeIf(::looksLikeTextDiagram)
            }
            if (diagram != null) LessonDiagram(diagram)
            else {
                val raw = node.plainText()
                val equation = raw.contains('=') || raw.contains('＝')
                val text = node.richText(equation)
                val flow = remember(text.text) { LessonFlow.parse(text.text) }
                val formula = remember(raw) { formulaCandidate(raw) }
                val subhead = remember(raw) { subheadCandidate(raw) }
                // 顺序：等式块最具体，其次是流程图，再是兜底小标题，最后才是正文。
                when {
                    formula != null -> LessonFormula(formula)
                    flow != null -> FlowBlock(flow)
                    subhead != null -> Text(
                        text, Modifier.padding(top = 4.dp), color = Gold,
                        style = lessonBody().copy(fontWeight = FontWeight.SemiBold),
                    )
                    else -> Text(text, color = Ink, style = lessonBody())
                }
            }
        }
        is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(ListItemGap)) {
            val start = (node as? OrderedList)?.markerStartNumber ?: 1
            node.children().forEachIndexed { index, item ->
                // 无序列表项若整个就是等式，去掉圆点改排成独立等式行；有序列表保留编号。
                val onlyChild = item.children().toList().singleOrNull()
                val formula = (onlyChild as? Paragraph)?.plainText()?.let(::formulaCandidate)
                    .takeIf { node is BulletList }
                if (formula != null) LessonFormula(formula)
                else Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (node is OrderedList) Text("${start + index}.", color = Gold, style = lessonBody())
                    else Box(
                        Modifier.padding(top = bulletTopPadding()).size(BulletSize).background(Gold, CircleShape),
                    )
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(ListItemGap)) {
                        item.children().forEach { LessonBlock(it, source) }
                    }
                }
            }
        }
        is BlockQuote -> Surface(shape = RoundedCornerShape(9.dp), color = Raised) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(ListItemGap)) {
                node.children().forEach { LessonBlock(it, source) }
            }
        }
        is FencedCodeBlock -> {
            val language = node.info.trim().takeWhile { !it.isWhitespace() }.lowercase()
            LessonCode(node.literal, language)
        }
        is IndentedCodeBlock -> LessonCode(node.literal)
        is HtmlBlock -> CodeBlock(node.literal)
        is ThematicBreak -> HorizontalDivider(color = Line)
        is TableBlock -> LessonTable(node)
        else -> node.children().forEach { LessonBlock(it, source) }
    }
}

// 等式行用淡底色单独成块，和正文拉开层次；运算符已由 normalizeEquationOperators 统一。
@Composable
private fun LessonFormula(text: String) {
    Surface(shape = FormulaShape, color = Raised) {
        Text(
            text, Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp),
            color = Ink,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
            ),
        )
    }
}

// 圆点按首行行高居中，字号缩放时跟着一起移动，避免小圆点浮在文字上方。
@Composable
private fun bulletTopPadding(): Dp {
    val lineHeight = with(LocalDensity.current) { lessonBody().lineHeight.toDp() }
    return ((lineHeight - BulletSize) / 2).coerceAtLeast(0.dp)
}

@Composable
private fun LessonCode(source: String, language: String = "") {
    val diagram = remember(source, language) {
        when (language) {
            "diagram", "ascii", "box-drawing" -> true
            "", "text", "plaintext", "unicode" -> looksLikeTextDiagram(source)
            else -> false
        }
    }
    if (diagram) LessonDiagram(source)
    else {
        val flow = remember(source, language) { LessonFlow.parse(source, mermaidFence = language == "mermaid") }
        if (flow == null) CodeBlock(source) else FlowBlock(flow)
    }
}

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

// 取节点里的纯文字：跳过 Markdown 标记，用于识别等式与小标题，不参与渲染。
private fun Node.plainText(): String = buildString { appendPlainText(this@plainText) }

private fun StringBuilder.appendPlainText(parent: Node) {
    for (node in parent.children()) {
        when (node) {
            is PlainText -> append(node.literal)
            is Code -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            else -> appendPlainText(node)
        }
    }
}

@Composable
private fun Node.richText(equation: Boolean = false): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString { appendChildren(this@richText, colors, equation) }
}

private fun AnnotatedString.Builder.appendChildren(parent: Node, colors: ColorScheme, equation: Boolean = false) {
    for (node in parent.children()) {
        when (node) {
            // 只有算式段落才改写运算符：普通中文里的连字符（如年份区间）保持原样。
            is PlainText -> append(if (equation) normalizeEquationOperators(node.literal) else node.literal)
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant, color = colors.primary)) {
                append(node.literal)
            }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendChildren(node, colors, equation) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendChildren(node, colors, equation) }
            is Link -> {
                val url = runCatching { URI(node.destination) }.getOrNull()
                if (url != null && url.scheme in listOf("https", "http") && !url.host.isNullOrBlank()) {
                    withLink(LinkAnnotation.Url(node.destination, TextLinkStyles(
                        SpanStyle(color = colors.secondary, textDecoration = TextDecoration.Underline),
                    ))) { appendChildren(node, colors, equation) }
                } else appendChildren(node, colors, equation)
            }
            is HtmlInline -> Unit
            else -> appendChildren(node, colors, equation)
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(shape = ControlShape, color = Ground) {
        Text(
            code.trimEnd(), Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = Steel,
        )
    }
}

@Composable
private fun LessonTable(table: TableBlock) {
    Surface(shape = ControlShape, color = Ground) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            table.children().forEach { section ->
                val header = section is TableHead
                section.children().filterIsInstance<TableRow>().forEach { row ->
                    Row(Modifier.background(if (header) Raised else Ground)) {
                        row.children().filterIsInstance<TableCell>().forEach { cell ->
                            Text(
                                cell.richText(), Modifier.width(118.dp).padding(horizontal = 9.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (header) Gold else Ink,
                            )
                        }
                    }
                    HorizontalDivider(color = Line.copy(alpha = .62f))
                }
            }
        }
    }
}

@Composable
internal fun FlowBlock(flow: LessonFlow, selectedNode: String? = null, onSelect: ((String) -> Unit)? = null) {
    val columns = flow.tiers.maxOf { it.size }
    val gutter = if (flow.returnArrows.isEmpty()) 0.dp else 22.dp + 10.dp * (flow.returnArrows.size - 1)
    val positions = remember(flow) {
        buildMap {
            flow.tiers.forEachIndexed { level, ids ->
                ids.forEachIndexed { index, id -> put(id, ((columns - ids.size) / 2f + index) to level) }
            }
        }
    }
    Surface(shape = ControlShape, color = Ground) {
        BoxWithConstraints(Modifier.padding(8.dp)) {
            val column = ((maxWidth - gutter) / columns).coerceIn(96.dp, 120.dp)
            val stepWidth = column - 16.dp
            val stepHeight = 56.dp
            val row = 97.dp
            val top = if (flow.returnArrows.isEmpty()) 0.dp else 14.dp
            val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
            val bottom = if (flow.returnArrows.any { it.label != null }) 14.dp + labelHeight else top
            val chartWidth = column * columns + gutter
            val chartHeight = top + row * (flow.tiers.size - 1) + stepHeight + bottom
            val arrowColor = Steel
            val returnColor = Gold
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Box(Modifier.size(chartWidth, chartHeight)) {
                    Canvas(Modifier.matchParentSize()) {
                        fun center(id: String): Offset {
                            val (slot, level) = positions.getValue(id)
                            return Offset(column.toPx() * (slot + .5f), (top + row * level + stepHeight / 2).toPx())
                        }
                        val halfStep = stepHeight.toPx() / 2
                        val tip = 4.dp.toPx()
                        for (edge in flow.arrows) {
                            val from = center(edge.from) + Offset(0f, halfStep)
                            val to = center(edge.to) - Offset(0f, halfStep)
                            val middle = (from.y + to.y) / 2
                            drawPath(Path().apply {
                                moveTo(from.x, from.y)
                                cubicTo(from.x, middle, to.x, middle, to.x, to.y)
                            }, arrowColor, style = Stroke(1.2.dp.toPx()))
                            drawLine(arrowColor, to - Offset(tip, tip), to, 1.2.dp.toPx())
                            drawLine(arrowColor, to + Offset(tip, -tip), to, 1.2.dp.toPx())
                        }
                        val returnStroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(4.dp.toPx()))
                        flow.returnArrows.forEachIndexed { index, edge ->
                            val from = center(edge.from) + Offset(0f, halfStep)
                            val to = center(edge.to) - Offset(0f, halfStep)
                            val side = (column * columns + 8.dp + 10.dp * index).toPx()
                            val bend = 10.dp.toPx()
                            // 回环从节点底部绕到侧边，从目标上方接回，避开节点和主线。
                            drawPath(Path().apply {
                                moveTo(from.x, from.y)
                                lineTo(from.x, from.y + bend)
                                lineTo(side, from.y + bend)
                                lineTo(side, to.y - bend)
                                lineTo(to.x, to.y - bend)
                                lineTo(to.x, to.y)
                            }, returnColor, style = returnStroke)
                            drawLine(returnColor, to - Offset(tip, tip), to, returnStroke.width)
                            drawLine(returnColor, to + Offset(tip, -tip), to, returnStroke.width)
                        }
                    }
                    flow.arrows.forEach { edge ->
                        edge.label?.let { label ->
                            val (fromX, fromY) = positions.getValue(edge.from)
                            val (toX, toY) = positions.getValue(edge.to)
                            Text(
                                label, Modifier.offset(
                                    x = column * ((fromX + toX + 1) / 2) + 4.dp,
                                    y = top + row * ((fromY + toY) / 2f) + stepHeight / 2 - 9.dp,
                                ).background(Ground).padding(horizontal = 2.dp),
                                style = MaterialTheme.typography.labelMedium, color = Gold,
                            )
                        }
                    }
                    flow.returnArrows.forEachIndexed { index, edge ->
                        edge.label?.let { label ->
                            val (slot, level) = positions.getValue(edge.from)
                            val side = column * columns + 8.dp + 10.dp * index
                            val labelWidth = 76.dp
                            val labelStart = ((column * (slot + .5f) + side - labelWidth) / 2).coerceIn(0.dp, chartWidth - labelWidth)
                            Text(
                                label, Modifier.offset(x = labelStart, y = top + row * level + stepHeight + 12.dp)
                                    .width(labelWidth), style = MaterialTheme.typography.labelMedium,
                                color = returnColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    positions.forEach { (id, location) ->
                        val (slot, level) = location
                        val decision = id in flow.decisions
                        Surface(
                            modifier = Modifier.offset(column * (slot + .5f) - stepWidth / 2, top + row * level)
                                .size(stepWidth, stepHeight).then(if (onSelect != null) Modifier
                                    .testTag("flow-node-$id")
                                    .clickable(role = Role.Button, onClickLabel = "查看${flow.labels.getValue(id)}") { onSelect(id) }
                                else Modifier),
                            shape = if (decision) decisionShape else RoundedCornerShape(8.dp),
                            color = if (id == selectedNode) aiColors.chip else if (decision) GoldGround else Raised,
                            border = BorderStroke(1.dp, if (id == selectedNode) aiColors.accent else if (decision) Gold else Line),
                        ) {
                            Box(Modifier.padding(horizontal = if (decision) 18.dp else 5.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    flow.labels.getValue(id), style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
