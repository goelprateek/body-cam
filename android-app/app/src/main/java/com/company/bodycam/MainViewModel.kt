package com.company.bodycam

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authStore = AuthStore(application)
    private val repository = BackendRepository(authStore)
    private var captureService: CaptureService? = null
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

            stateCollectionJob?.cancel()
            stateCollectionJob = viewModelScope.launch {
                boundService.captureManager?.state?.collectLatest { runtime ->
                    _uiState.value = _uiState.value.copy(
                        isStreaming = runtime.isStreaming,
                        streamStatus = runtime.streamStatus,
                        syncStatus = runtime.syncStatus,
                        message = runtime.lastError ?: _uiState.value.message,
                        usingFrontCamera = runtime.usingFrontCamera,
                        canFlipCamera = runtime.canFlipCamera,
                        cameraSwitchInFlight = runtime.cameraSwitchInFlight,
                        thermalThrottling = runtime.thermalThrottling
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

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            // Wait for service to be bound if it's not yet
            while (captureService == null) {
                kotlinx.coroutines.delay(100)
            }
            captureService?.captureManager?.bindPreview(previewView, lifecycleOwner)
        }
    }

    fun setHighQualityMode(highQuality: Boolean) {
        _uiState.value = _uiState.value.copy(highQualityMode = highQuality)
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
        val token = authToken ?: return
        val user = _uiState.value.user ?: return
        val url = _uiState.value.backendUrl

        _uiState.value = _uiState.value.copy(actionInFlight = true)

        viewModelScope.launch {
            try {
                val sessionResp = repository.createWorkerSession(url, token, user)
                val liveKitResp = repository.joinWorkerSession(url, token, sessionResp, user)
                
                val context = getApplication<Application>()
                val serviceIntent = Intent(context, CaptureService::class.java)
                context.startForegroundService(serviceIntent)

                val config = ActiveSessionConfig(
                    backendUrl = url,
                    liveKitUrl = liveKitResp.liveKitUrl,
                    token = liveKitResp.token,
                    authToken = token,
                    sessionId = sessionResp.id
                )
                
                // Ensure service is bound before calling start
                while (captureService == null) {
                    kotlinx.coroutines.delay(50)
                }

                captureService?.captureManager?.start(config, _uiState.value.highQualityMode)
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    activeSessionId = sessionResp.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    message = "Start session error: ${e.message}"
                )
            }
        }
    }

    fun stopSession() {
        _uiState.value = _uiState.value.copy(
            actionInFlight = true,
            streamStatus = "Stopping",
            syncStatus = "Finalizing recording"
        )
        captureService?.stopCaptureGracefully()
        _uiState.value = _uiState.value.copy(
            actionInFlight = false,
            activeSessionId = null
        )
    }

    fun logout() {
        stopSession()
        authStore.clear()
        authToken = null
        _uiState.value = _uiState.value.copy(
            user = null,
            syncStatus = "Waiting for login",
            message = null
        )
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
}
