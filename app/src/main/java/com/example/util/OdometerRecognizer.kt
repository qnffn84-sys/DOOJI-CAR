package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToLong

object OdometerRecognizer {

    private const val TAG = "OdometerRecognizer"
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val TIMEOUT_MS = 45_000L

    private val odometerSchema = Schema.obj(
        properties = mapOf(
            "found" to Schema.boolean(
                description = "True if the vehicle's total accumulated odometer reading (ODO) is clearly readable in the image."
            ),
            "mileageKm" to Schema.integer(
                description = "Total accumulated odometer reading in whole kilometers (0 to 2000000). Ignore speedometer numbers, RPM, trip A/B meters, fuel range/distance-to-empty, clock, and temperature. If displayed in miles, convert to km.",
                nullable = true
            ),
            "reason" to Schema.string(
                description = "Short Korean explanation if found is false.",
                nullable = true
            )
        ),
        optionalProperties = listOf("mileageKm", "reason")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = odometerSchema
            }
        )
    }

    suspend fun recognizeFromFile(file: File): Result<Long> {
        val bitmap = decodeScaledBitmapFromFile(file)
            ?: return Result.failure(IllegalArgumentException("사진을 불러올 수 없습니다."))
        return try {
            recognizeFromBitmap(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun recognizeFromUri(context: Context, uri: Uri): Result<Long> {
        val bitmap = decodeScaledBitmapFromUri(context, uri)
            ?: return Result.failure(IllegalArgumentException("사진을 불러올 수 없습니다."))
        return try {
            recognizeFromBitmap(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): Result<Long> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "이 자동차 계기판 사진에서 누적 총 주행거리(ODO, 총 주행거리) 숫자만 km 단위 정수로 읽어 주세요. " +
                                "속도계 눈금, RPM, 트립(Trip A/B), 주행 가능 거리(잔여 거리), 시간, 온도는 제외하고 누적 총 주행거리만 추출하세요. " +
                                "만약 단위가 마일(mi)이면 km로 환산해 정수로 반환하고, 누적 주행거리가 보이지 않으면 found를 false로 반환하세요."
                        )
                    }
                )
            }
            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("인식 결과를 받지 못했습니다."))
            val json = JSONObject(rawJson)
            val found = json.optBoolean("found", false)
            if (!found || json.isNull("mileageKm")) {
                val reason = json.optString("reason").ifBlank { "계기판에서 총 주행거리를 찾지 못했습니다." }
                return Result.failure(IllegalStateException(reason))
            }
            val km = json.optDouble("mileageKm", -1.0).roundToLong()
            if (km < 0L || km > 2_000_000L) {
                return Result.failure(IllegalStateException("주행거리 범위가 올바르지 않습니다."))
            }
            Result.success(km)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Odometer recognition timed out", e)
            Result.failure(IllegalStateException("인식 시간이 초과되었습니다. 다시 시도해 주세요."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Odometer recognition failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "계기판 인식에 실패했습니다."))
        }
    }

    private suspend fun decodeScaledBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
            val raw = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return@withContext null
            resizeIfNeeded(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode file bitmap", e)
            null
        }
    }

    private suspend fun decodeScaledBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            } ?: return@withContext null
            resizeIfNeeded(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode URI bitmap", e)
            null
        }
    }

    private fun computeSampleSize(width: Int, height: Int): Int {
        val maxSide = maxOf(width, height).coerceAtLeast(1)
        var sample = 1
        while (maxSide / sample > MAX_IMAGE_DIMENSION * 2) {
            sample *= 2
        }
        return sample
    }

    private fun resizeIfNeeded(raw: Bitmap): Bitmap {
        val maxSide = maxOf(raw.width, raw.height).coerceAtLeast(1)
        if (maxSide <= MAX_IMAGE_DIMENSION) return raw
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide.toFloat()
        return Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        ).also {
            if (it !== raw) raw.recycle()
        }
    }
}
