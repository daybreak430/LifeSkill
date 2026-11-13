package com.lifeskill.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import com.lifeskill.app.data.AttachmentDraft
import com.lifeskill.app.data.attachmentBitmap
import com.lifeskill.app.model.CHAT_ATTACHMENT_LIMIT
import com.lifeskill.app.model.ChatAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ChatAttachmentSources(chat: ChatViewModel, expanded: Boolean, onClose: () -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile?.let(::File)
        cameraFile = null
        if (success && file != null) chat.addAttachments(listOf(cameraUri(context, file)), camera = true) { file.delete() }
        else file?.delete()
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(CHAT_ATTACHMENT_LIMIT)) {
        chat.addAttachments(it)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { chat.addAttachments(it) }

    if (!expanded) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttachmentSource("拍照", Glyph.CAMERA, Modifier.weight(1f), enabled) {
            try {
                val directory = File(context.cacheDir, "chat-camera").apply { mkdirs() }
                val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                directory.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.delete() }
                val file = File.createTempFile("photo-", ".jpg", directory)
                cameraFile = file.absolutePath
                camera.launch(cameraUri(context, file))
                onClose()
            } catch (_: Exception) {
                cameraFile?.let { File(it).delete() }
                cameraFile = null
                chat.showAttachmentMessage("无法打开相机，请试试从相册选择。")
                onClose()
            }
        }
        AttachmentSource("相册", Glyph.PHOTO, Modifier.weight(1f), enabled) {
            photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            onClose()
        }
        AttachmentSource("文件", Glyph.ATTACHMENT, Modifier.weight(1f), enabled) {
            documents.launch(arrayOf("image/*", "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/*"))
            onClose()
        }
    }
}

private fun cameraUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.conversation-share", file)

@Composable
private fun AttachmentSource(label: String, glyph: Glyph, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick, modifier, enabled = enabled, shape = ControlShape, color = aiColors.panel) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Mark(glyph, aiColors.accent, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun ChatAttachmentStrip(
    attachments: List<ChatAttachment>,
    load: suspend (ChatAttachment) -> AttachmentDraft,
    onPreview: (ChatAttachment) -> Unit,
    onRemove: ((ChatAttachment) -> Unit)? = null,
) {
    if (attachments.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(attachments, key = ChatAttachment::id) { item ->
            Surface(shape = ControlShape, color = aiColors.panel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { onPreview(item) }, shape = ControlShape, color = aiColors.chip) {
                        Row(Modifier.widthIn(min = 92.dp, max = 174.dp).heightIn(min = 48.dp).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.isImage) {
                                val bitmap by produceState<Bitmap?>(null, item.id) {
                                    value = withContext(Dispatchers.IO) {
                                        try {
                                            attachmentBitmap(load(item).bytes, 100)
                                        } catch (error: Exception) {
                                            if (error is CancellationException) throw error
                                            null
                                        }
                                    }
                                }
                                Box(Modifier.size(36.dp).clip(ControlShape), contentAlignment = Alignment.Center) {
                                    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop) }
                                        ?: Mark(Glyph.PHOTO, aiColors.accent, Modifier.size(18.dp))
                                }
                            } else {
                                Mark(Glyph.ATTACHMENT, aiColors.accent, Modifier.size(18.dp))
                            }
                            Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (onRemove != null) {
                        GlyphButton(Glyph.CLOSE, "移除 ${item.name}", { onRemove(item) }, background = aiColors.panel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatAttachmentPreview(attachment: ChatAttachment, chat: ChatViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveMessage by rememberSaveable(attachment.id) { mutableStateOf<String?>(null) }
    val saveOriginal = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(attachment.mimeType)) { uri ->
        if (uri != null) scope.launch {
            saveMessage = try {
                val data = chat.loadAttachment(attachment).bytes
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("附件副本无法写入")
                    stream.use { it.write(data) }
                }
                "已保存原件"
            } catch (error: Exception) {
                readable(error, "无法保存原件。")
            }
        }
    }
    val preview by produceState<AttachmentPreview?>(null, attachment.id) {
        value = withContext(Dispatchers.IO) {
            try {
                val draft = chat.loadAttachment(attachment)
                AttachmentPreview(if (attachment.isImage) attachmentBitmap(draft.bytes, 1400) else null, draft.text)
            } catch (error: Exception) {
                AttachmentPreview(text = readable(error, "附件无法读取。"))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                val bitmap = preview?.bitmap
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), attachment.name, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                } else Text(if (preview == null) "正在读取…" else preview?.text ?: "无法显示图片。")
                saveMessage?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("关闭") } },
        dismissButton = { TextButton({ saveOriginal.launch(attachment.name) }, enabled = preview != null) { Text("保存原件") } },
    )
}

private data class AttachmentPreview(val bitmap: Bitmap? = null, val text: String? = null)
