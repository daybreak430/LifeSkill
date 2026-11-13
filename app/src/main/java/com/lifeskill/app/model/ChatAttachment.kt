package com.lifeskill.app.model

data class ChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val partial: Boolean = false,
    val imageCredit: ImageCredit? = null,
) {
    val isImage get() = mimeType.startsWith("image/")
}

data class ImageCredit(val title: String, val sourceUrl: String, val author: String, val license: String, val licenseUrl: String?)

internal const val CHAT_ATTACHMENT_LIMIT = 3
internal const val CHAT_ATTACHMENT_BYTES = 8 * 1024 * 1024
internal const val CHAT_ATTACHMENT_TEXT_LIMIT = 24_000
internal const val COURSE_ATTACHMENT_TEXT_LIMIT = 48_000
