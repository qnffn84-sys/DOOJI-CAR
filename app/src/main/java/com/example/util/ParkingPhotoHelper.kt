package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class SavedParkingPhoto(
    val localPath: String,
    val thumbnailBase64: String?
)

object ParkingPhotoHelper {

    private const val DIR_NAME = "parking_photos"
    private const val MAX_THUMB_DIMENSION = 480
    private const val MAX_BASE64_LENGTH = 300_000

    fun createCaptureUri(context: Context, vehicleId: String): Pair<File, Uri> {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val safeId = vehicleId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(dir, "parking_${safeId}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    suspend fun processCapturedFile(file: File): SavedParkingPhoto = withContext(Dispatchers.IO) {
        val thumb = encodeThumbnailBase64(file)
        SavedParkingPhoto(localPath = file.absolutePath, thumbnailBase64 = thumb)
    }

    suspend fun copyFromUri(
        context: Context,
        vehicleId: String,
        sourceUri: Uri
    ): SavedParkingPhoto? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val safeId = vehicleId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val dest = File(dir, "parking_${safeId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            val thumb = encodeThumbnailBase64(dest)
            SavedParkingPhoto(localPath = dest.absolutePath, thumbnailBase64 = thumb)
        } catch (_: Exception) {
            null
        }
    }

    fun loadBitmap(localPath: String?, base64: String?): Bitmap? {
        val localFile = localPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        return when {
            localFile != null && localFile.exists() -> BitmapFactory.decodeFile(localFile.absolutePath)
            !base64.isNullOrBlank() -> decodeBase64Bitmap(base64)
            else -> null
        }
    }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeThumbnailBase64(file: File): String? {
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxSide / sampleSize > MAX_THUMB_DIMENSION * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val scale = minOf(
                1f,
                MAX_THUMB_DIMENSION.toFloat() / maxOf(raw.width, raw.height).coerceAtLeast(1)
            )
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * scale).toInt().coerceAtLeast(1),
                    (raw.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { raw.recycle() }
            } else {
                raw
            }
            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 68, stream)
            resized.recycle()
            val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= MAX_BASE64_LENGTH) encoded else null
        } catch (_: Exception) {
            null
        }
    }
}
