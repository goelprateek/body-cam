package com.kriyanshtech.bodycam

import com.squareup.moshi.JsonClass

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
    val workerName: String,
    val referenceNumber: String
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

@JsonClass(generateAdapter = true)
data class RecordingMetadataRequest(
    val capturedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val locationAccuracyMeters: Double? = null,
    val cameraFacing: String? = null,
    val thermalEnabled: Boolean? = null,
    val thermalMinC: Double? = null,
    val thermalMaxC: Double? = null,
    val thermalAvgC: Double? = null,
    val sensorPayload: Map<String, Any?>? = null
)
