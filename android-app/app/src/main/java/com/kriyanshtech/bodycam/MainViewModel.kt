package com.kriyanshtech.bodycam

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authStore = AuthStore(application)
    private val repository = BackendRepository(authStore)
    private var captureService: CaptureService? = null
    private var previewView: PreviewView? = null
    private var previewLifecycleOwner: LifecycleOwner? = null
    private var isBound = false
    private var stateCollectionJob: Job? = null

    private val session = authStore.load()
    private var authToken: String? = session.token

    private val _uiState = MutableStateFlow(
        MainUiState(
            backendUrl = session.backendUrl,
            username = session.username,
            user = session.toUser(),
            syncStatus = if (session.toUser() != null) "Ready to start session" else "Waiting for login"
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CaptureService.LocalBinder
            val boundService = binder.getService()
            captureService = boundService
            isBound = true
            previewView?.let { preview ->
                boundService.captureManager?.bindPreview(preview)
            }

            stateCollectionJob?.cancel()
            stateCollectionJob = viewModelScope.launch {
                boundService.captureManager?.state?.collectLatest { runtime ->
                    val referenceSummary = _uiState.value.referenceNumber.trim()
                    _uiState.value = _uiState.value.copy(
                        isStreaming = runtime.isStreaming,
                        streamStatus = runtime.streamStatus,
                        syncStatus = runtime.syncStatus,
                        uploadQueueSummary = runtime.uploadQueueSummary,
                        pendingUploadCount = runtime.pendingUploadCount,
                        queuedUploadCount = runtime.queuedUploadCount,
                        uploadingUploadCount = runtime.uploadingUploadCount,
                        message = runtime.lastError ?: _uiState.value.message,
                        usingFrontCamera = runtime.usingFrontCamera,
                        canFlipCamera = runtime.canFlipCamera,
                        cameraSwitchInFlight = runtime.cameraSwitchInFlight,
                        thermalThrottling = runtime.thermalThrottling,
                        sessionSummary = runtime.sessionId?.let {
                            if (referenceSummary.isBlank()) {
                                "Session: $it"
                            } else {
                                "Ref: $referenceSummary  Session: $it"
                            }
                        } ?: "",
                        activeSessionId = runtime.sessionId
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            captureService = null
            isBound = false
            stateCollectionJob?.cancel()
        }
    }

    init {
        Intent(application, CaptureService::class.java).also { intent ->
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun bindPreview(previewView: PreviewView) {
        this.previewView = previewView
        captureService?.captureManager?.bindPreview(previewView)
    }

    fun unbindPreview() {
        this.previewView = null
        captureService?.captureManager?.unbindPreview()
    }

    fun setHighQualityMode(highQuality: Boolean) {
        _uiState.value = _uiState.value.copy(highQualityMode = highQuality)
    }

    fun updateReferenceNumber(referenceNumber: String) {
        val error = if (referenceNumber.isBlank()) "Reference number is required" else null
        _uiState.value = _uiState.value.copy(
            referenceNumber = referenceNumber,
            referenceError = error
        )
    }

    fun flipCamera() {
        captureService?.captureManager?.flipCamera()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loginInFlight = true, message = null)
            try {
                val user = repository.login(_uiState.value.backendUrl, username, password)
                val newSession = authStore.load()
                authToken = newSession.token
                
                _uiState.value = _uiState.value.copy(
                    loginInFlight = false,
                    user = user,
                    syncStatus = "Ready to start session"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loginInFlight = false,
                    message = "Login error: ${e.message}"
                )
            }
        }
    }

    fun startSession() {
        if (_uiState.value.actionInFlight || _uiState.value.isStreaming) return

        val token = authToken ?: return
        val user = _uiState.value.user ?: return
        val url = _uiState.value.backendUrl
        val referenceNumber = _uiState.value.referenceNumber.trim()

        if (referenceNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(
                referenceError = "Reference number is required before starting a session"
            )
            return
        }

        _uiState.value = _uiState.value.copy(actionInFlight = true, referenceError = null)

        viewModelScope.launch {
            try {
                Log.i("MainViewModel", "Starting worker session for reference=$referenceNumber backendUrl=$url")
                _uiState.value = _uiState.value.copy(syncStatus = "Creating backend session")
                val sessionResp = repository.createWorkerSession(url, token, user, referenceNumber)
                Log.i("MainViewModel", "Backend session created sessionId=${sessionResp.id}")
                _uiState.value = _uiState.value.copy(syncStatus = "Fetching LiveKit join token")
                val liveKitResp = repository.joinWorkerSession(url, token, sessionResp, user)
                Log.i(
                    "MainViewModel",
                    "LiveKit join token received sessionId=${sessionResp.id} room=${liveKitResp.roomName} liveKitUrl=${liveKitResp.liveKitUrl}"
                )

                val config = ActiveSessionConfig(
                    backendUrl = url,
                    liveKitUrl = liveKitResp.liveKitUrl,
                    token = liveKitResp.token,
                    authToken = token,
                    sessionId = sessionResp.id
                )

                _uiState.value = _uiState.value.copy(syncStatus = "Starting capture service")
                val service = awaitCaptureService()
                service.startForegroundCaptureService()
                delay(150)
                service.consumeLastStartError()?.let { error ->
                    throw IllegalStateException(error)
                }

                previewView?.let { preview ->
                    service.captureManager?.bindPreview(preview)
                }

                _uiState.value = _uiState.value.copy(syncStatus = "Connecting capture pipeline")
                val captureManager = service.captureManager
                    ?: throw IllegalStateException("Capture manager unavailable")
                captureManager.start(config, _uiState.value.highQualityMode)
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    activeSessionId = sessionResp.id,
                    sessionSummary = "Ref: $referenceNumber  Session: ${sessionResp.id}"
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to start worker session", e)
                if (handleExpiredAuth(e)) {
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    message = "Start session error: ${e.message}"
                )
            }
        }
    }

    private suspend fun awaitCaptureService(): CaptureService {
        try {
            return withTimeout(5_000) {
                while (captureService == null) {
                    delay(50)
                }
                captureService ?: throw IllegalStateException("Capture service unavailable")
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Capture service did not become ready in time")
        }
    }

    fun stopSession() {
        if (_uiState.value.actionInFlight || !_uiState.value.isStreaming) return
        stopCaptureAndEndSession(clearAuthAfter = false)
    }

    fun logout() {
        if (_uiState.value.actionInFlight) return
        stopCaptureAndEndSession(clearAuthAfter = true)
    }

    private fun stopCaptureAndEndSession(clearAuthAfter: Boolean) {
        val sessionId = _uiState.value.activeSessionId
        val token = authToken
        val backendUrl = _uiState.value.backendUrl

        _uiState.value = _uiState.value.copy(
            actionInFlight = true,
            streamStatus = "Stopping",
            syncStatus = "Finalizing recording"
        )
        captureService?.stopCaptureGracefully()

        viewModelScope.launch {
            var stopMessage: String? = null
            var sessionEnded = false

            if (!sessionId.isNullOrBlank() && !token.isNullOrBlank()) {
                try {
                    repository.endSession(backendUrl, token, sessionId)
                    sessionEnded = true
                    stopMessage = "Session ended"
                } catch (e: Exception) {
                    stopMessage = "Capture stopped, but failed to end backend session: ${e.message}"
                }
            } else {
                sessionEnded = sessionId.isNullOrBlank()
                stopMessage = if (sessionEnded) "Capture stopped" else "Capture stopped, but no auth token was available to end the backend session"
            }

            if (clearAuthAfter) {
                authStore.clearAuthenticatedSession()
                authToken = null
            }

            _uiState.value = _uiState.value.copy(
                actionInFlight = false,
                user = if (clearAuthAfter) null else _uiState.value.user,
                activeSessionId = if (sessionEnded) null else sessionId,
                sessionSummary = if (sessionEnded) "" else _uiState.value.sessionSummary,
                syncStatus = if (clearAuthAfter) "Waiting for login" else stopMessage,
                message = if (clearAuthAfter) null else stopMessage
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private fun handleExpiredAuth(error: Throwable): Boolean {
        if (!isExpiredAuthError(error)) {
            return false
        }

        authStore.clearAuthenticatedSession()
        authToken = null
        _uiState.value = _uiState.value.copy(
            loginInFlight = false,
            actionInFlight = false,
            user = null,
            activeSessionId = null,
            sessionSummary = "",
            isStreaming = false,
            streamStatus = "Idle",
            syncStatus = "Waiting for login",
            message = "Session expired. Please sign in again."
        )
        return true
    }

    private fun isExpiredAuthError(error: Throwable): Boolean {
        val httpError = error as? HttpException ?: return false
        if (httpError.code() !in setOf(401, 403)) {
            return false
        }

        val backendMessage = runCatching { httpError.response()?.errorBody()?.string() }
            .getOrNull()
            .orEmpty()
            .lowercase()
        val exceptionMessage = httpError.message().orEmpty().lowercase()
        val combinedMessage = "$exceptionMessage $backendMessage"

        return httpError.code() == 401 ||
            combinedMessage.contains("expired") ||
            combinedMessage.contains("jwt") ||
            combinedMessage.contains("token") ||
            combinedMessage.contains("unauthorized")
    }
}
