package com.company.bodycam

import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class BackendRepository(
    private val authStore: AuthStore
) {

    private fun api(baseUrl: String, token: String?): BackendApi {
        return BackendApiFactory.create(baseUrl) { token }
    }

    suspend fun login(
        backendUrl: String,
        liveKitUrl: String,
        username: String,
        password: String
    ): StoredUser {
        val response = api(backendUrl, null).login(LoginRequest(username = username, password = password))
        val user = StoredUser(
            userId = response.userId,
            username = response.username,
            displayName = response.displayName,
            role = response.role
        )
        authStore.saveAuthenticatedSession(
            backendUrl = backendUrl,
            liveKitUrl = liveKitUrl,
            token = response.accessToken,
            user = user
        )
        return user
    }

    suspend fun createWorkerSession(
        backendUrl: String,
        token: String,
        user: StoredUser
    ): SessionResponse {
        return api(backendUrl, token).createSession(
            CreateSessionRequest(
                workerId = user.userId,
                workerName = user.displayName
            )
        )
    }

    suspend fun joinWorkerSession(
        backendUrl: String,
        token: String,
        session: SessionResponse,
        user: StoredUser
    ): LiveKitTokenResponse {
        return api(backendUrl, token).joinSession(
            sessionId = session.id,
            request = JoinSessionTokenRequest(
                participantName = user.displayName,
                participantRole = "WORKER"
            )
        )
    }

    suspend fun endSession(backendUrl: String, token: String, sessionId: String) {
        api(backendUrl, token).endSession(sessionId)
    }

    suspend fun uploadRecording(
        backendUrl: String,
        token: String,
        sessionId: String,
        durationSeconds: Int?,
        file: File
    ): RecordingResponse {
        val durationPart = durationSeconds?.toString()?.toRequestBody(textPlainMediaType)
        val sessionPart = sessionId.toRequestBody(textPlainMediaType)
        val fileBody = file.asRequestBody("video/mp4".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", file.name, fileBody)
        return api(backendUrl, token).uploadRecording(sessionPart, durationPart, filePart)
    }
}
