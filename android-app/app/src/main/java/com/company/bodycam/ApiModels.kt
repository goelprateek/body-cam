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
    val id: String
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
    val id: String
)
