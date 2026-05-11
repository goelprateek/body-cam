package com.kriyanshtech.bodycam

data class CaptureRuntimeState(
    val isStreaming: Boolean = false,
    val streamStatus: String = "Idle",
    val syncStatus: String = "Waiting for session start",
    val uploadQueueSummary: String = "Queue clear",
    val pendingUploadCount: Int = 0,
    val queuedUploadCount: Int = 0,
    val uploadingUploadCount: Int = 0,
    val lastError: String? = null,
    val usingFrontCamera: Boolean = false,
    val canFlipCamera: Boolean = false,
    val cameraSwitchInFlight: Boolean = false,
    val thermalThrottling: Boolean = false,
    val sessionId: String? = null
)

data class ActiveSessionConfig(
    val backendUrl: String,
    val liveKitUrl: String,
    val token: String,
    val authToken: String,
    val sessionId: String
)
