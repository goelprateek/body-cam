package com.company.bodycam

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authStore = AuthStore(application)
    private val repository = BackendRepository(authStore)
    private val captureManager = LiveCaptureManager(application)
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

    init {
        viewModelScope.launch {
            captureManager.state.collectLatest { runtime ->
                _uiState.value = _uiState.value.copy(
                    isStreaming = runtime.isStreaming,
                    streamStatus = runtime.streamStatus,
                    syncStatus = runtime.syncStatus,
                    message = runtime.lastError ?: _uiState.value.message
                )
            }
        }
    }

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        captureManager.bindPreview(previewView, lifecycleOwner)
    }

    fun setHighQualityMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(highQualityMode = enabled)
    }

    fun login(username: String, password: String) {
        val backendUrl = _uiState.value.backendUrl
        if (backendUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                message = "Backend configuration is missing for this build"
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            loginInFlight = true,
            message = null,
            username = username,
            password = password
        )
        authStore.saveBackendUrl(backendUrl)
        viewModelScope.launch {
            runCatching {
                repository.login(backendUrl, username, password)
            }.onSuccess { user ->
                authToken = authStore.load().token
                _uiState.value = _uiState.value.copy(
                    loginInFlight = false,
                    user = user,
                    syncStatus = "Ready to start session",
                    message = "Logged in successfully"
                )
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    loginInFlight = false,
                    message = exception.message ?: "Login failed"
                )
            }
        }
    }

    fun startSession() {
        val state = _uiState.value
        val user = state.user ?: return
        val token = authToken ?: return
        if (state.backendUrl.isBlank()) {
            _uiState.value = state.copy(message = "Backend configuration is missing for this build")
            return
        }
        _uiState.value = state.copy(actionInFlight = true, message = null)
        viewModelScope.launch {
            runCatching {
                val sessionResponse = repository.createWorkerSession(state.backendUrl, token, user)
                val joinResponse = repository.joinWorkerSession(state.backendUrl, token, sessionResponse, user)
                captureManager.start(
                    ActiveSessionConfig(
                        backendUrl = state.backendUrl,
                        liveKitUrl = joinResponse.liveKitUrl,
                        token = joinResponse.token,
                        authToken = token,
                        sessionId = sessionResponse.id
                    ),
                    highQuality = state.highQualityMode
                )
                sessionResponse to joinResponse
            }.onSuccess { (sessionResponse, joinResponse) ->
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    activeSessionId = sessionResponse.id,
                    sessionSummary = "Session ${sessionResponse.id.take(8)} in room ${joinResponse.roomName}",
                    message = "Session started"
                )
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    message = exception.message ?: "Unable to start session"
                )
            }
        }
    }

    fun stopSession() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val token = authToken ?: return
        val backendUrl = _uiState.value.backendUrl
        _uiState.value = _uiState.value.copy(actionInFlight = true, message = null)
        captureManager.stop()
        viewModelScope.launch {
            runCatching {
                repository.endSession(backendUrl, token, sessionId)
            }
            _uiState.value = _uiState.value.copy(
                actionInFlight = false,
                activeSessionId = null,
                sessionSummary = "",
                isStreaming = false,
                streamStatus = "Idle",
                syncStatus = "Session ended",
                message = "Session stopped"
            )
        }
    }

    fun logout() {
        captureManager.stop()
        authToken = null
        authStore.clear()
        _uiState.value = MainUiState()
    }

    fun release() {
        captureManager.release()
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}
