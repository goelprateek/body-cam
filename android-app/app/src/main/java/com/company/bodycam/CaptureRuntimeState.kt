package com.company.bodycam

data class CaptureRuntimeState(
    val isStreaming: Boolean = false,
    val streamStatus: String = "Idle",
    val syncStatus: String = "Waiting for session start",
    val lastError: String? = null,
    val highQualityMode: Boolean = false
)

data class ActiveSessionConfig(
    val backendUrl: String,
    val liveKitUrl: String,
    val token: String,
    val authToken: String,
    val sessionId: String,
    val roomName: String
)
