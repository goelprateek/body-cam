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
            liveKitUrl = session.liveKitUrl,
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

    fun login(backendUrl: String, liveKitUrl: String, username: String, password: String) {
        _uiState.value = _uiState.value.copy(
            loginInFlight = true,
            message = null,
            backendUrl = backendUrl,
            liveKitUrl = liveKitUrl,
            username = username,
            password = password
        )
        authStore.saveUrls(backendUrl, liveKitUrl)
        viewModelScope.launch {
            runCatching {
                repository.login(backendUrl, liveKitUrl, username, password)
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
        _uiState.value = state.copy(actionInFlight = true, message = null)
        viewModelScope.launch {
            runCatching {
                val sessionResponse = repository.createWorkerSession(state.backendUrl, token, user)
                val joinResponse = repository.joinWorkerSession(state.backendUrl, token, sessionResponse, user)
                val liveKitUrl = if (state.liveKitUrl.isBlank()) joinResponse.liveKitUrl else state.liveKitUrl
                captureManager.start(
                    ActiveSessionConfig(
                        backendUrl = state.backendUrl,
                        liveKitUrl = liveKitUrl,
                        token = joinResponse.token,
                        authToken = token,
                        sessionId = sessionResponse.id,
                        roomName = joinResponse.roomName
                    )
                )
                sessionResponse to joinResponse
            }.onSuccess { (sessionResponse, joinResponse) ->
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    activeSessionId = sessionResponse.id,
                    sessionSummary = "Session ${sessionResponse.id.take(8)} in room ${joinResponse.roomName}",
                    liveKitUrl = if (_uiState.value.liveKitUrl.isBlank()) joinResponse.liveKitUrl else _uiState.value.liveKitUrl,
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
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}
