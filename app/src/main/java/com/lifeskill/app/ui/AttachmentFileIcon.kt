package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private enum class FileIcon(val tint: Color) {
    WORD(Color(0xFF315FB1)),
    PDF(Color(0xFFB93645)),
    TEXT(Color(0xFF526477)),
    MARKDOWN(Color(0xFF247966)),
    GENERIC(Color(0xFF526477)),
}

@Composable
internal fun AttachmentFileIcon(mimeType: String) {
    val icon = when (mimeType) {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileIcon.WORD
        "application/pdf" -> FileIcon.PDF
        "text/plain" -> FileIcon.TEXT
        "text/markdown" -> FileIcon.MARKDOWN
        else -> FileIcon.GENERIC
    }
    Canvas(Modifier.size(26.dp).testTag("attachment-icon-${icon.name.lowercase()}")) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val paper = Path().apply {
                moveTo(5f, 1f)
                lineTo(14f, 1f)
                lineTo(21f, 8f)
                lineTo(21f, 21f)
                quadraticTo(21f, 23f, 19f, 23f)
                lineTo(5f, 23f)
                quadraticTo(3f, 23f, 3f, 21f)
                lineTo(3f, 3f)
                quadraticTo(3f, 1f, 5f, 1f)
                close()
            }
            drawPath(paper, icon.tint)
            drawPath(Path().apply {
                moveTo(14f, 1f)
                lineTo(21f, 8f)
                lineTo(14f, 8f)
                close()
            }, Color.White.copy(alpha = .45f))

            fun symbol(vararg coords: Float) {
                val mark = Path().apply {
                    moveTo(coords[0], coords[1])
                    for (index in 2 until coords.size step 2) lineTo(coords[index], coords[index + 1])
                }
                drawPath(mark, Color.White, style = Stroke(1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            when (icon) {
                FileIcon.WORD -> symbol(7f, 11f, 9.3f, 18f, 12f, 13f, 14.7f, 18f, 17f, 11f)
                FileIcon.PDF -> {
                    val mark = Path().apply {
                        moveTo(9f, 18f)
                        cubicTo(11.5f, 15f, 13f, 10f, 11.4f, 10f)
                        cubicTo(9.4f, 10f, 12.3f, 16.1f, 16f, 16.6f)
                        cubicTo(18.2f, 17f, 17.8f, 14.5f, 13.8f, 15.5f)
                        cubicTo(10.8f, 16.2f, 7.1f, 17.3f, 7.1f, 18.6f)
                        cubicTo(7.1f, 19.2f, 8.2f, 19f, 9f, 18f)
                    }
                    drawPath(mark, Color.White, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                FileIcon.TEXT -> {
                    symbol(7.5f, 11.5f, 16.5f, 11.5f)
                    symbol(7.5f, 15f, 16.5f, 15f)
                    symbol(7.5f, 18.5f, 13.5f, 18.5f)
                }
                FileIcon.MARKDOWN -> {
                    symbol(6f, 17.5f, 6f, 11.5f, 9f, 14.6f, 12f, 11.5f, 12f, 17.5f)
                    symbol(16f, 11.5f, 16f, 17.5f)
                    symbol(14f, 15.5f, 16f, 17.5f, 18f, 15.5f)
                }
                FileIcon.GENERIC -> symbol(8f, 15f, 16f, 15f)
            }
        }
    }
}
