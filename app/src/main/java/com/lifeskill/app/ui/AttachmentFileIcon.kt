package com.lifeskill.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val resource = when (icon) {
        FileIcon.WORD, FileIcon.PDF, FileIcon.TEXT -> com.lifeskill.app.R.drawable.lucide_file_text
        FileIcon.MARKDOWN -> com.lifeskill.app.R.drawable.lucide_file_code
        FileIcon.GENERIC -> com.lifeskill.app.R.drawable.lucide_file
    }
    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(resource), contentDescription = null,
        tint = icon.tint, modifier = Modifier.size(36.dp).testTag("attachment-icon-${icon.name.lowercase()}"))
}
