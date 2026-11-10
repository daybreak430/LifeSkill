package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
                if (node.info.trim().equals("mermaid", ignoreCase = true)) LessonFlow.parse(node.literal) else null
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

private fun Node.richText(): AnnotatedString = buildAnnotatedString { appendChildren(this@richText) }

private fun AnnotatedString.Builder.appendChildren(parent: Node) {
    for (node in parent.children()) {
        when (node) {
            is PlainText -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Raised, color = Gold)) {
                append(node.literal)
            }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendChildren(node) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendChildren(node) }
            is Link -> {
                val url = runCatching { URI(node.destination) }.getOrNull()
                if (url != null && url.scheme in listOf("https", "http") && !url.host.isNullOrBlank()) {
                    withLink(LinkAnnotation.Url(node.destination, TextLinkStyles(
                        SpanStyle(color = Steel, textDecoration = TextDecoration.Underline),
                    ))) { appendChildren(node) }
                } else appendChildren(node)
            }
            is HtmlInline -> Unit
            else -> appendChildren(node)
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(shape = RoundedCornerShape(9.dp), color = Night, border = BorderStroke(1.dp, Line)) {
        Text(
            code.trimEnd(), Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = Steel,
        )
    }
}

@Composable
private fun LessonTable(table: TableBlock) {
    Surface(shape = RoundedCornerShape(9.dp), color = Night, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            table.children().forEach { section ->
                val header = section is TableHead
                section.children().filterIsInstance<TableRow>().forEach { row ->
                    Row(Modifier.background(if (header) Raised else Night)) {
                        row.children().filterIsInstance<TableCell>().forEach { cell ->
                            Text(
                                cell.richText(), Modifier.width(118.dp).padding(horizontal = 9.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (header) Gold else Ink,
                            )
                        }
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun FlowBlock(flow: LessonFlow) {
    val stepWidth = 104.dp
    val stepHeight = 56.dp
    val column = 120.dp
    val row = 97.dp
    val columns = flow.tiers.maxOf { it.size }
    val positions = remember(flow) {
        buildMap {
            flow.tiers.forEachIndexed { level, ids ->
                ids.forEachIndexed { index, id -> put(id, ((columns - ids.size) / 2f + index) to level) }
            }
        }
    }
    Surface(shape = RoundedCornerShape(9.dp), color = Night, border = BorderStroke(1.dp, Line)) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
            Box(Modifier.size(column * columns, row * (flow.tiers.size - 1) + stepHeight)) {
                Canvas(Modifier.matchParentSize()) {
                    fun center(id: String): Offset {
                        val (slot, level) = positions.getValue(id)
                        return Offset(column.toPx() * (slot + .5f), row.toPx() * level + stepHeight.toPx() / 2)
                    }
                    val halfStep = stepHeight.toPx() / 2
                    for (edge in flow.arrows) {
                        val from = center(edge.from) + Offset(0f, halfStep)
                        val to = center(edge.to) - Offset(0f, halfStep)
                        val middle = (from.y + to.y) / 2
                        drawPath(Path().apply {
                            moveTo(from.x, from.y)
                            cubicTo(from.x, middle, to.x, middle, to.x, to.y)
                        }, Steel, style = Stroke(1.2.dp.toPx()))
                        val tip = 4.dp.toPx()
                        drawLine(Steel, to - Offset(tip, tip), to, 1.2.dp.toPx())
                        drawLine(Steel, to + Offset(tip, -tip), to, 1.2.dp.toPx())
                    }
                }
                flow.arrows.forEach { edge ->
                    edge.label?.let { label ->
                        val (fromX, fromY) = positions.getValue(edge.from)
                        val (toX, toY) = positions.getValue(edge.to)
                        Text(
                            label, Modifier.offset(
                                x = column * ((fromX + toX + 1) / 2) + 4.dp,
                                y = row * ((fromY + toY) / 2f) + stepHeight / 2 - 9.dp,
                            ).background(Night).padding(horizontal = 2.dp),
                            style = MaterialTheme.typography.labelMedium, color = Gold,
                        )
                    }
                }
                positions.forEach { (id, location) ->
                    val (slot, level) = location
                    val decision = id in flow.decisions
                    Surface(
                        modifier = Modifier.offset(column * (slot + .5f) - stepWidth / 2, row * level)
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
