package com.lifeskill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.CertificateTheme
import com.lifeskill.app.model.CollectedCertificate

private val Parchment = Color(0xFFFCF7ED)
private val CertificateType = TextStyle(fontFamily = FontFamily.Serif, localeList = LocaleList(Locale("zh-CN")))

private data class CertificatePalette(val ink: Color, val border: Color, val paperEdge: Color)

private fun CertificateTheme.palette() = when (this) {
    CertificateTheme.TECH -> CertificatePalette(Color(0xFF203553), Color(0xFF647EA1), Color(0xFFECEFF4))
    CertificateTheme.BUSINESS -> CertificatePalette(Color(0xFF194733), Color(0xFFA38448), Color(0xFFF3EBDD))
    CertificateTheme.COMMUNICATION -> CertificatePalette(Color(0xFF632B3C), Color(0xFFA56C77), Color(0xFFF7EBEA))
    CertificateTheme.ARTS -> CertificatePalette(Color(0xFF4B375D), Color(0xFF937AA6), Color(0xFFF1EBF5))
}

@Composable
internal fun CertificateCard(treeName: String, style: CertificateStyle, recipient: String?) {
    val palette = style.theme.palette()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("certificate-paper-${style.theme.name.lowercase()}"),
        color = Parchment, shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().drawBehind {
                drawRect(Brush.linearGradient(listOf(palette.paperEdge, Parchment, palette.paperEdge)))
                drawCertificateFrame(style, palette.border)
            }.padding(horizontal = 34.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Emblem(Glyph.TREE, tint = Parchment, background = palette.ink, diameter = 25.dp)
                Text("LifeSkill", color = palette.ink,
                    style = CertificateType.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(29.dp))
            Text("结业证书", Modifier.semantics { heading() }, color = palette.ink, textAlign = TextAlign.Center,
                style = CertificateType.copy(fontSize = 39.sp, lineHeight = 50.sp, letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold))
            Text("CERTIFICATE OF COMPLETION", color = palette.ink, textAlign = TextAlign.Center,
                style = CertificateType.copy(fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.25.sp))
            Spacer(Modifier.height(if (recipient == null) 38.dp else 24.dp))
            recipient?.let { name ->
                Text("颁发给", color = palette.ink, style = CertificateType.copy(fontSize = 13.sp))
                Text(name, color = palette.ink, textAlign = TextAlign.Center,
                    style = CertificateType.copy(fontSize = 29.sp, lineHeight = 38.sp,
                        fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading))
                Spacer(Modifier.height(15.dp))
            }
            Text(treeName, color = palette.ink, textAlign = TextAlign.Center,
                style = CertificateType.copy(fontSize = 27.sp, lineHeight = 38.sp,
                    fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading))
            Spacer(Modifier.height(17.dp))
            Canvas(Modifier.width(64.dp).height(2.dp)) {
                drawLine(palette.border, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
            }
            Spacer(Modifier.height(25.dp))
            CertificateSeal(style, palette.ink)
        }
    }
}

@Composable
internal fun CertificateThumbnail(certificate: CollectedCertificate, modifier: Modifier = Modifier) {
    val style = certificate.style
    val palette = style.theme.palette()
    val density = LocalDensity.current.density
    // 展柜缩略图是纸面预览，课程名和操作由外层卡片提供给读屏。
    CompositionLocalProvider(LocalDensity provides Density(density)) {
        Column(
            modifier.clearAndSetSemantics {}.drawBehind {
                drawRect(Brush.linearGradient(listOf(palette.paperEdge, Parchment, palette.paperEdge)))
                drawCertificateFrame(style, palette.border, frameScale = .35f)
            }.padding(horizontal = 9.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("LifeSkill", color = palette.ink, style = CertificateType.copy(fontSize = 8.sp), maxLines = 1)
            Text("结业证书", color = palette.ink, style = CertificateType.copy(fontSize = 13.sp,
                fontWeight = FontWeight.Bold), maxLines = 1)
            certificate.recipient?.let {
                Text(it, color = palette.ink, style = CertificateType.copy(fontSize = 8.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(certificate.treeName, color = palette.ink, textAlign = TextAlign.Center,
                style = CertificateType.copy(fontSize = 9.sp, lineHeight = 11.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Emblem(style.emblem.glyph, tint = palette.ink, background = palette.paperEdge, diameter = 23.dp)
        }
    }
}

@Composable
private fun CertificateSeal(style: CertificateStyle, ink: Color) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            drawCircle(ink.copy(alpha = .055f), radius - 3.dp.toPx())
            drawCircle(ink, radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawCircle(ink.copy(alpha = .66f), radius - 5.dp.toPx(), style = Stroke(.75.dp.toPx()))
            for (side in listOf(-1f, 1f)) {
                drawCircle(ink, 1.35.dp.toPx(), center + Offset(side * 25.dp.toPx(), 0f))
            }
        }
        Mark(style.emblem.glyph, ink, Modifier.size(32.dp))
    }
}

private fun DrawScope.drawCertificateFrame(style: CertificateStyle, color: Color, frameScale: Float = 1f) {
    val outer = 9.dp.toPx() * frameScale
    val inner = 14.dp.toPx() * frameScale
    for (inset in listOf(outer, inner)) {
        val frameSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        if (frameSize.minDimension <= 0f) return
        drawRoundRect(
            color = color.copy(alpha = if (inset == outer) .9f else .62f),
            topLeft = Offset(inset, inset), size = frameSize,
            cornerRadius = CornerRadius(if (style.theme == CertificateTheme.COMMUNICATION) 7.dp.toPx() * frameScale else 0f),
            style = Stroke((if (inset == outer) 1.dp else .6.dp).toPx() * frameScale),
        )
    }

    // 将同一角纹镜像到四角。固定编号分别决定线段长度和小印记，两者组合成 16 个变体。
    val length = (16 + style.cornerVariant % 4 * 2).dp.toPx() * frameScale
    val variant = style.cornerVariant / 4
    for (horizontal in listOf(1f, -1f)) {
        for (vertical in listOf(1f, -1f)) {
            val left = if (horizontal > 0f) inner else size.width - inner
            val top = if (vertical > 0f) inner else size.height - inner
            translate(left, top) {
                scale(horizontal, vertical, Offset.Zero) {
                    drawCertificateCorner(style.theme, color, length, variant, frameScale)
                }
            }
        }
    }
}

private fun DrawScope.drawCertificateCorner(theme: CertificateTheme, color: Color, length: Float, variant: Int, scale: Float) {
    val step = 4.dp.toPx() * scale
    val corner = Path().apply {
        when (theme) {
            CertificateTheme.TECH -> {
                moveTo(step, length)
                lineTo(step, step)
                lineTo(length, step)
            }
            CertificateTheme.BUSINESS -> {
                moveTo(-step, length)
                lineTo(-step, 0f)
                lineTo(0f, 0f)
                lineTo(0f, -step)
                lineTo(length, -step)
            }
            CertificateTheme.COMMUNICATION -> {
                moveTo(step, length)
                quadraticTo(step, step, length, step)
            }
            CertificateTheme.ARTS -> {
                moveTo(step, length)
                lineTo(step, length / 2)
                lineTo(length / 2, step)
                lineTo(length, step)
            }
        }
    }
    drawPath(corner, color, style = Stroke(1.dp.toPx() * scale))
    val markCenter = Offset(length * .60f, length * .60f)
    val radius = 1.75.dp.toPx() * scale
    when (variant) {
        0 -> drawCircle(color, radius * .7f, markCenter)
        1 -> drawLine(color, markCenter - Offset(radius, radius), markCenter + Offset(radius, radius), 1.dp.toPx() * scale)
        2 -> drawRect(color, markCenter - Offset(radius, radius), Size(2 * radius, 2 * radius),
            style = Stroke(.8.dp.toPx() * scale))
        3 -> drawPath(Path().apply {
            moveTo(markCenter.x, markCenter.y - radius)
            lineTo(markCenter.x + radius, markCenter.y)
            lineTo(markCenter.x, markCenter.y + radius)
            lineTo(markCenter.x - radius, markCenter.y)
            close()
        }, color)
    }
}
