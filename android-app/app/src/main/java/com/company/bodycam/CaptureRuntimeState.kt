package com.company.bodycam

data class CaptureRuntimeState(
    val isStreaming: Boolean = false,
    val streamStatus: String = "Idle",
    val syncStatus: String = "Waiting for session start",
    val lastError: String? = null,
    val usingFrontCamera: Boolean = false,
    val canFlipCamera: Boolean = false,
    val cameraSwitchInFlight: Boolean = false,
    val thermalThrottling: Boolean = false
)

data class ActiveSessionConfig(
    val backendUrl: String,
    val liveKitUrl: String,
    val token: String,
    val authToken: String,
    val sessionId: String
)
