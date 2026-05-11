package com.kriyanshtech.bodycam

data class MainUiState(
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val username: String = "worker1",
    val password: String = "worker123",
    val user: StoredUser? = null,
    val referenceNumber: String = "",
    val referenceError: String? = null,
    val loginInFlight: Boolean = false,
    val actionInFlight: Boolean = false,
    val isStreaming: Boolean = false,
    val sessionSummary: String = "",
    val streamStatus: String = "Idle",
    val syncStatus: String = "Waiting for login",
    val message: String? = null,
    val activeSessionId: String? = null,
    val highQualityMode: Boolean = false,
    val usingFrontCamera: Boolean = false,
    val canFlipCamera: Boolean = false,
    val cameraSwitchInFlight: Boolean = false,
    val thermalThrottling: Boolean = false
)
