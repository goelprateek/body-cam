package com.company.bodycam

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String
)

data class CreateSessionRequest(
    val workerId: String,
    val workerName: String
)

data class SessionResponse(
    val id: String,
    val workerId: String,
    val workerName: String,
    val roomName: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
    val createdAt: String
)

data class JoinSessionTokenRequest(
    val participantName: String,
    val participantRole: String
)

data class LiveKitTokenResponse(
    val token: String,
    val roomName: String,
    val liveKitUrl: String
)

data class RecordingResponse(
    val id: String,
    val sessionId: String,
    val roomName: String,
    val objectKey: String,
    val playbackUrl: String,
    val durationSeconds: Int?,
    val createdAt: String
)
