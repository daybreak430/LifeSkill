package com.lifeskill.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val AVATAR_EDGE = 384
private const val DECODE_EDGE = 1024

internal suspend fun readAvatarPhoto(resolver: ContentResolver, uri: Uri): ProfileAvatar.Photo = withContext(Dispatchers.IO) {
    fun open() = resolver.openInputStream(uri) ?: throw RuleViolation("无法打开这张照片，请重新选择。")

    // 先读尺寸再抽样，避免把相册原图载入内存。只保存裁剪后重新编码的像素。
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw RuleViolation("无法读取这张照片，请重新选择。")
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > DECODE_EDGE) sample *= 2
    val orientation = try {
        open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val source = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw RuleViolation("无法读取这张照片，请重新选择。")
    try {
        val edge = minOf(source.width, source.height)
        val scale = minOf(1f, AVATAR_EDGE.toFloat() / edge)
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
            postScale(scale, scale)
        }
        val avatar = Bitmap.createBitmap(source, (source.width - edge) / 2, (source.height - edge) / 2,
            edge, edge, transform, true)
        try {
            val output = ByteArrayOutputStream()
            if (!avatar.compress(Bitmap.CompressFormat.JPEG, 84, output)) throw RuleViolation("无法生成头像，请重试。")
            ProfileAvatar.Photo(Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
        } finally {
            if (avatar !== source) avatar.recycle()
        }
    } finally {
        source.recycle()
    }
}
