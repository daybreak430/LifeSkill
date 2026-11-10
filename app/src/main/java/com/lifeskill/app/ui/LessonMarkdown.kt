package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
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
import org.commonmark.parser.Parser
import java.net.URI

private val lessonParser = Parser.builder().extensions(listOf(TablesExtension.create())).build()
private val decisionShape = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.height / 2)
    lineTo(size.width / 2, size.height)
    lineTo(0f, size.height / 2)
    close()
}

@Composable
internal fun LessonMarkdown(source: String) {
    val document = remember(source) { lessonParser.parse(source) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        document.children().forEach { LessonBlock(it) }
    }
}

@Composable
private fun LessonBlock(node: Node) {
    when (node) {
        is Heading -> Text(
            node.richText(), color = Ink,
            style = if (node.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
        is Paragraph -> Text(node.richText(), color = Ink, style = MaterialTheme.typography.bodyLarge)
        is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val start = (node as? OrderedList)?.markerStartNumber ?: 1
            node.children().forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (node is OrderedList) "${start + index}." else "•", color = Gold, style = MaterialTheme.typography.bodyLarge)
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item.children().forEach { LessonBlock(it) }
                    }
                }
            }
        }
        is BlockQuote -> Surface(shape = RoundedCornerShape(9.dp), color = Raised) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                node.children().forEach { LessonBlock(it) }
            }
        }
        is FencedCodeBlock -> {
            val flow = remember(node) {
                val language = node.info.trim()
                if (language.isEmpty() || language.equals("mermaid", ignoreCase = true)) LessonFlow.parse(node.literal) else null
            }
            if (flow == null) CodeBlock(node.literal) else FlowBlock(flow)
        }
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is HtmlBlock -> CodeBlock(node.literal)
        is ThematicBreak -> HorizontalDivider(color = Line)
        is TableBlock -> LessonTable(node)
        else -> node.children().forEach { LessonBlock(it) }
    }
}

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

@Composable
private fun Node.richText(): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString { appendChildren(this@richText, colors) }
}

private fun AnnotatedString.Builder.appendChildren(parent: Node, colors: ColorScheme) {
    for (node in parent.children()) {
        when (node) {
            is PlainText -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant, color = colors.primary)) {
                append(node.literal)
            }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendChildren(node, colors) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendChildren(node, colors) }
            is Link -> {
                val url = runCatching { URI(node.destination) }.getOrNull()
                if (url != null && url.scheme in listOf("https", "http") && !url.host.isNullOrBlank()) {
                    withLink(LinkAnnotation.Url(node.destination, TextLinkStyles(
                        SpanStyle(color = colors.secondary, textDecoration = TextDecoration.Underline),
                    ))) { appendChildren(node, colors) }
                } else appendChildren(node, colors)
            }
            is HtmlInline -> Unit
            else -> appendChildren(node, colors)
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
private fun FlowBlock(flow: LessonFlow) {
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
                                .size(stepWidth, stepHeight),
                            shape = if (decision) decisionShape else RoundedCornerShape(8.dp),
                            color = if (decision) GoldGround else Raised, border = BorderStroke(1.dp, if (decision) Gold else Line),
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
