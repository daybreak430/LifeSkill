package com.lifeskill.app.data

import android.text.Html
import com.lifeskill.app.BuildConfig
import com.lifeskill.app.ai.AiJson
import com.lifeskill.app.model.ImageCredit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal data class LessonImage(val credit: ImageCredit, val jpeg: ByteArray)

internal fun interface LessonImageSearch {
    suspend fun find(query: String): List<LessonImage>
}

internal class CommonsImages : LessonImageSearch {
    override suspend fun find(query: String): List<LessonImage> = withContext(Dispatchers.IO) {
        val keywords = URLEncoder.encode(query, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=$keywords" +
            "&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=640" +
            "&iiextmetadatafilter=Artist%7CAttribution%7CAttributionRequired%7CLicenseShortName%7CLicenseUrl"
        val body = read(url, maxBytes = 256 * 1024) ?: return@withContext emptyList()
        val pages = AiJson.objectOrNull(body.decodeToString())?.optJSONObject("query")?.optJSONArray("pages")
            ?: return@withContext emptyList()
        val candidates = (0 until pages.length()).mapNotNull(pages::optJSONObject)
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }.mapNotNull(::candidate)
            .distinctBy { it.thumbnail }.take(3)
        coroutineScope {
            candidates.map { item -> async {
                val thumbnail = read(item.thumbnail, maxBytes = 2 * 1024 * 1024) ?: return@async null
                // 同一张压缩图用于模型看图和聊天展示，源站失效时也能从对话历史读取。
                val jpeg = attachmentBitmap(thumbnail, 960)?.toJpeg() ?: return@async null
                LessonImage(item.credit, jpeg)
            } }.awaitAll().filterNotNull()
        }
    }

    private fun candidate(page: JSONObject): Candidate? {
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        if (info.optString("mime") !in setOf("image/jpeg", "image/png", "image/webp", "image/svg+xml")) return null
        val thumbnail = info.optString("thumburl").verifiedUrl("upload.wikimedia.org", "thumb.wikimedia.org") ?: return null
        val source = info.optString("descriptionurl").verifiedUrl("commons.wikimedia.org") ?: return null
        val metadata = info.optJSONObject("extmetadata") ?: return null
        val license = metadata.value("LicenseShortName")
        val licenseUrl = metadata.value("LicenseUrl").verifiedUrl("creativecommons.org", "www.creativecommons.org")
        val author = metadata.value("Attribution").ifBlank { metadata.value("Artist") }
            .replace(Regex("^(.{3,120})\\1$"), "$1")
        val reusable = license == "Public domain" || licenseUrl != null &&
            (license == "CC0" || license.startsWith("CC BY ") || license.startsWith("CC BY-SA "))
        if (!reusable || (license.startsWith("CC BY") || metadata.value("AttributionRequired") == "true") && author.isBlank()) {
            return null
        }
        val title = page.optString("title").removePrefix("File:").substringBeforeLast('.').cleanText().ifBlank { "图片案例" }
        return Candidate(thumbnail, ImageCredit(title, source, author, license, licenseUrl))
    }

    private fun read(url: String, maxBytes: Int): ByteArray? {
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("User-Agent", "LifeSkill/${BuildConfig.VERSION_NAME} (https://github.com/daybreak430/LifeSkill)")
            if (connection.responseCode != 200 || connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (output.size() <= maxBytes) {
                    val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
                    if (count == -1) return output.toByteArray()
                    output.write(buffer, 0, count)
                }
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private data class Candidate(val thumbnail: String, val credit: ImageCredit)
}

private fun JSONObject.value(key: String): String = optJSONObject(key)?.optString("value").orEmpty().cleanText()

private fun String.cleanText() = Html.fromHtml(take(2_000), Html.FROM_HTML_MODE_COMPACT).toString()
    .replace(Regex("\\s+"), " ").trim().take(240)

private fun String.verifiedUrl(vararg hosts: String): String? {
    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    return takeIf { length <= 2048 && uri.scheme == "https" && uri.host in hosts && uri.userInfo == null && uri.port == -1 }
}
