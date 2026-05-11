package com.kriyanshtech.bodycam

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.time.Instant

class UploadRecordingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = BackendRepository(AuthStore(context))
    private val locationMetadataProvider = LocationMetadataProvider(context)

    override suspend fun doWork(): Result {
        val backendUrl = inputData.getString(KEY_BACKEND_URL) ?: return Result.failure()
        val authToken = inputData.getString(KEY_AUTH_TOKEN) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val durationSeconds = inputData.getInt(KEY_DURATION_SECONDS, 0).takeIf { it > 0 }
        val cameraFacing = inputData.getString(KEY_CAMERA_FACING)
        val capturedAt = inputData.getString(KEY_CAPTURED_AT)
        val segmentSequence = inputData.getInt(KEY_SEGMENT_SEQUENCE, -1).takeIf { it >= 0 }
        val segmentStartedAt = inputData.getString(KEY_SEGMENT_STARTED_AT)
        val segmentEndedAt = inputData.getString(KEY_SEGMENT_ENDED_AT)
        val sessionElapsedStartMs = inputData.getLong(KEY_SESSION_ELAPSED_START_MS, -1L).takeIf { it >= 0L }
        val sessionElapsedEndMs = inputData.getLong(KEY_SESSION_ELAPSED_END_MS, -1L).takeIf { it >= 0L }
        val highQuality = inputData.getBoolean(KEY_HIGH_QUALITY, false)
        val file = File(filePath)

        if (!file.exists()) {
            return Result.success()
        }

        val startTime = System.currentTimeMillis()
        android.util.Log.i(
            "UploadRecordingWorker",
            "Starting upload sessionId=$sessionId file=${file.name} sizeBytes=${file.length()} durationSeconds=$durationSeconds segmentSequence=$segmentSequence sessionElapsedStartMs=$sessionElapsedStartMs sessionElapsedEndMs=$sessionElapsedEndMs"
        )

        return try {
            val location = locationMetadataProvider.getLatestLocationMetadata()
            val metadata = RecordingMetadataRequest(
                capturedAt = capturedAt,
                segmentSequence = segmentSequence,
                segmentStartedAt = segmentStartedAt,
                segmentEndedAt = segmentEndedAt,
                sessionElapsedStartMs = sessionElapsedStartMs,
                sessionElapsedEndMs = sessionElapsedEndMs,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitudeMeters = location?.altitudeMeters,
                locationAccuracyMeters = location?.locationAccuracyMeters,
                cameraFacing = cameraFacing,
                sensorPayload = mapOf(
                    "source" to "android-app",
                    "highQuality" to highQuality,
                    "uploadedAt" to Instant.now().toString()
                )
            )
            repository.uploadRecording(
                backendUrl = backendUrl,
                token = authToken,
                sessionId = sessionId,
                durationSeconds = durationSeconds,
                metadata = metadata,
                file = file
            )
            val duration = System.currentTimeMillis() - startTime
            val sizeKb = file.length() / 1024.0
            val speedKbps = if (duration > 0) (sizeKb / (duration / 1000.0)) else 0.0
            android.util.Log.i(
                "UploadRecordingWorker",
                "Upload successful sessionId=$sessionId file=${file.name} segmentSequence=$segmentSequence tookMs=$duration speedKbps=${"%.2f".format(speedKbps)}"
            )

            if (!file.delete()) {
                android.util.Log.w("UploadRecordingWorker", "Uploaded file could not be deleted: ${file.absolutePath}")
            }
            Result.success()
        } catch (e: HttpException) {
            val errorMessage = parseBackendErrorMessage(e.response()?.errorBody())
            val retryable = e.code() >= 500 || e.code() == 429
            android.util.Log.e(
                "UploadRecordingWorker",
                "Upload failed sessionId=$sessionId file=${file.name} segmentSequence=$segmentSequence: HTTP ${e.code()}${errorMessage?.let { " - $it" } ?: ""}. ${if (retryable) "Will retry if possible." else "Not retrying because the request is invalid or unauthorized."}",
                e
            )
            if (retryable) Result.retry() else Result.failure()
        } catch (e: Exception) {
            android.util.Log.e(
                "UploadRecordingWorker",
                "Upload failed sessionId=$sessionId file=${file.name} segmentSequence=$segmentSequence: ${e.message}. Will retry if possible.",
                e
            )
            Result.retry()
        }
    }

    private fun parseBackendErrorMessage(errorBody: ResponseBody?): String? {
        return errorBody?.use { body ->
            runCatching {
                val source = body.source()
                source.request(16384) // Buffer up to 16KB
                val buffer = source.buffer.clone()
                val rawBody = buffer.readUtf8(16384L.coerceAtMost(buffer.size))
                if (rawBody.isBlank()) return null
                
                // Try to parse as JSON to get the 'message' field
                runCatching {
                    JSONObject(rawBody).optString("message").takeIf { it.isNotBlank() }
                }.getOrNull() ?: rawBody.take(256) // Fallback to raw snippet
            }.getOrNull()
        }
    }

    companion object {
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_DURATION_SECONDS = "duration_seconds"
        const val KEY_CAMERA_FACING = "camera_facing"
        const val KEY_CAPTURED_AT = "captured_at"
        const val KEY_SEGMENT_SEQUENCE = "segment_sequence"
        const val KEY_SEGMENT_STARTED_AT = "segment_started_at"
        const val KEY_SEGMENT_ENDED_AT = "segment_ended_at"
        const val KEY_SESSION_ELAPSED_START_MS = "session_elapsed_start_ms"
        const val KEY_SESSION_ELAPSED_END_MS = "session_elapsed_end_ms"
        const val KEY_HIGH_QUALITY = "high_quality"
    }
}
