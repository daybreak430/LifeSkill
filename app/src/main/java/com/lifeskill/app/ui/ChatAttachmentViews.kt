package com.lifeskill.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import com.lifeskill.app.data.AttachmentDraft
import com.lifeskill.app.data.attachmentBitmap
import com.lifeskill.app.model.CHAT_ATTACHMENT_LIMIT
import com.lifeskill.app.model.ChatAttachment
import com.lifeskill.app.model.ImageCredit
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
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Column(Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
    sent: Boolean = false,
) {
    if (attachments.isEmpty()) return
    val bounds = if (sent) Modifier.padding(start = 44.dp).testTag("sent-attachments") else
        Modifier.testTag("draft-attachments")
    LazyRow(
        Modifier.fillMaxWidth().then(bounds).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (sent) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(attachments, key = ChatAttachment::id) { item ->
            Box {
                Surface(
                    onClick = { onPreview(item) }, shape = ControlShape, color = aiColors.chip,
                    modifier = Modifier.testTag("attachment-card-${item.id}"),
                ) {
                    if (item.isImage) {
                        AttachmentThumbnail(item, load, Modifier.size(if (sent) 112.dp else 84.dp))
                    } else {
                        Row(
                            Modifier.widthIn(min = 124.dp, max = if (sent) 236.dp else 208.dp)
                                .heightIn(min = if (sent) 62.dp else 84.dp)
                                .padding(start = 10.dp, end = if (onRemove == null) 12.dp else 46.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AttachmentFileIcon(item.mimeType)
                            Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (onRemove != null) {
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                            .semantics { contentDescription = "移除 ${item.name}" },
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xB3232D34)),
                            contentAlignment = Alignment.Center) {
                            Mark(Glyph.CLOSE, Color.White, Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    item: ChatAttachment, load: suspend (ChatAttachment) -> AttachmentDraft, modifier: Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            try {
                attachmentBitmap(load(item).bytes, 384)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        }
    }
    Box(modifier.semantics { contentDescription = item.name }, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } ?: Mark(Glyph.PHOTO, aiColors.accent, Modifier.size(24.dp))
    }
}

@Composable
internal fun LessonIllustration(item: ChatAttachment, load: suspend (ChatAttachment) -> AttachmentDraft, open: () -> Unit) {
    val credit = checkNotNull(item.imageCredit)
    val preview by produceState<AttachmentPreview?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            try {
                AttachmentPreview(bitmap = attachmentBitmap(load(item).bytes, 960))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AttachmentPreview()
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val bitmap = preview?.bitmap
        if (bitmap != null) {
            Surface(open, Modifier.fillMaxWidth().testTag("lesson-illustration"), shape = ControlShape, color = Raised) {
                Image(bitmap.asImageBitmap(), credit.title, Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    contentScale = ContentScale.Fit)
            }
        } else if (preview != null) {
            Text("图片暂无法显示", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        IllustrationCredit(credit)
    }
}

@Composable
private fun IllustrationCredit(credit: ImageCredit) {
    Text(buildAnnotatedString {
        withLink(LinkAnnotation.Url(credit.sourceUrl)) { append("维基共享资源") }
        if (credit.author.isNotBlank()) append(" · ${credit.author}")
        append(" · ")
        val license = credit.licenseUrl
        if (license != null) withLink(LinkAnnotation.Url(license)) { append(credit.license) } else append(credit.license)
    }, color = aiColors.accent, style = MaterialTheme.typography.labelMedium)
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
        onDismissRequest = onDismiss,
        title = if (attachment.isImage) null else ({ Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }),
        text = {
            Column(Modifier.heightIn(max = if (attachment.isImage) 520.dp else 400.dp).verticalScroll(rememberScrollState())) {
                val bitmap = preview?.bitmap
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), attachment.imageCredit?.title ?: attachment.name,
                        Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                } else Text(if (preview == null) "正在读取…" else preview?.text ?: "无法显示图片。")
                attachment.imageCredit?.let { IllustrationCredit(it) }
                saveMessage?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("关闭") } },
        dismissButton = if (attachment.imageCredit == null) ({
            TextButton({ saveOriginal.launch(attachment.name) }, enabled = preview != null) { Text("保存原件") }
        }) else null,
    )
}

private data class AttachmentPreview(val bitmap: Bitmap? = null, val text: String? = null)
