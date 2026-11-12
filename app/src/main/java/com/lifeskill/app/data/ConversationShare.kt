package com.lifeskill.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.lifeskill.app.model.ConversationExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

internal class ConversationShareProvider : FileProvider()

internal suspend fun cacheConversationShare(context: Context, document: ConversationExport): Uri =
    withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "conversation-shares").apply { mkdirs() }
        // 接收应用可能稍后才读附件。清理过期副本，并为每次分享保留独立路径。
        val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        cache.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.deleteRecursively() }
        val directory = Files.createTempDirectory(cache.toPath(), "share-").toFile()
        try {
            val attachment = File(directory, document.fileName)
            attachment.writeText(document.markdown, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.conversation-share", attachment)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
