package com.company.bodycam

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class UploadRecordingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = BackendRepository(AuthStore(context))

    override suspend fun doWork(): Result {
        val backendUrl = inputData.getString(KEY_BACKEND_URL) ?: return Result.failure()
        val authToken = inputData.getString(KEY_AUTH_TOKEN) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val durationSeconds = inputData.getInt(KEY_DURATION_SECONDS, 0).takeIf { it > 0 }
        val file = File(filePath)

        if (!file.exists()) {
            return Result.success()
        }

        android.util.Log.d("UploadRecordingWorker", "Uploading file: ${file.absolutePath}, size: ${file.length()} bytes")

        return try {
            repository.uploadRecording(
                backendUrl = backendUrl,
                token = authToken,
                sessionId = sessionId,
                durationSeconds = durationSeconds,
                file = file
            )
            file.delete()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_DURATION_SECONDS = "duration_seconds"
    }
}
