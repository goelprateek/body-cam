package com.company.bodycam

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.video.BitmapFrameCapturer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveCaptureManager(
    private val appContext: Context
) {
    private enum class CameraFacing(val lensFacing: Int) {
        BACK(CameraSelector.LENS_FACING_BACK),
        FRONT(CameraSelector.LENS_FACING_FRONT)
    }

    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workManager = WorkManager.getInstance(appContext)
    private val _state = MutableStateFlow(CaptureRuntimeState())
    private val pendingUploadIds = linkedSetOf<UUID>()

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var room: Room? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var videoCapturer: BitmapFrameCapturer? = null
    @Volatile
    private var isTrackStarted = false
    private var activeConfig: ActiveSessionConfig? = null
    private var currentHighQuality = false
    private var currentCameraFacing = CameraFacing.BACK
    private var pendingCameraFacing: CameraFacing? = null
    private var isSwitchingCamera = false
    private var stopRequested = false

    var onStopSafeToRelease: (() -> Unit)? = null

    val state: StateFlow<CaptureRuntimeState> = _state.asStateFlow()

    private val segmentStopper = Runnable {
        activeRecording?.stop()
    }

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
    }

    fun release() {
        stopImmediate()
        cameraExecutor.shutdown()
        captureScope.cancel()
    }

    fun stopSession() {
        stopRequested = true
        isTrackStarted = false
        mainHandler.removeCallbacks(segmentStopper)
        _state.value = _state.value.copy(
            isStreaming = false,
            streamStatus = "Stopping",
            syncStatus = if (activeRecording != null) {
                "Finalizing current segment"
            } else {
                "Capture stopped; waiting for queued uploads"
            },
            cameraSwitchInFlight = false
        )

        val recording = activeRecording
        if (recording != null) {
            recording.stop()
        } else {
            teardownCapturePipeline()
            notifyStopSafeToReleaseIfIdle()
        }
    }

    fun stopImmediate() {
        stopRequested = false
        isTrackStarted = false
        mainHandler.removeCallbacks(segmentStopper)
        activeRecording?.stop()
        activeRecording = null
        pendingUploadIds.clear()
        teardownCapturePipeline()

        _state.value = CaptureRuntimeState(
            isStreaming = false,
            streamStatus = "Stopped",
            syncStatus = "Capture idle"
        )
    }

    private fun teardownCapturePipeline() {
        localVideoTrack?.stopCapture()
        localVideoTrack?.stop()
        localVideoTrack = null
        room?.disconnect()
        room = null
        videoCapturer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        activeConfig = null
        currentHighQuality = false
        currentCameraFacing = CameraFacing.BACK
        pendingCameraFacing = null
        isSwitchingCamera = false
    }

    fun start(config: ActiveSessionConfig, highQuality: Boolean = false) {
        val owner = lifecycleOwner ?: throw IllegalStateException("Preview not bound")
        val preview = previewView ?: throw IllegalStateException("Preview view not bound")
        activeConfig = config
        currentHighQuality = highQuality
        pendingCameraFacing = null
        isSwitchingCamera = false
        stopRequested = false
        captureScope.launch {
            try {
                _state.value = CaptureRuntimeState(
                    isStreaming = false,
                    streamStatus = "Connecting to LiveKit",
                    syncStatus = "Preparing capture pipeline"
                )
                val currentRoom = LiveKit.create(appContext)
                room = currentRoom
                launchRoomEvents(currentRoom)
                currentRoom.connect(config.liveKitUrl, config.token)

                val localParticipant = currentRoom.localParticipant
                val frameCapturer = BitmapFrameCapturer()
                videoCapturer = frameCapturer
                val track = localParticipant.createVideoTrack(
                    name = "bodycam-camera",
                    capturer = frameCapturer
                )
                track.startCapture()
                localVideoTrack = track
                isTrackStarted = true
                android.util.Log.d("LiveCaptureManager", "Video track started and capturer active")

                val published = localParticipant.publishVideoTrack(track)
                if (!published) {
                    throw IllegalStateException("LiveKit rejected video track publish")
                }
                android.util.Log.d("LiveCaptureManager", "Video track published successfully")
                localParticipant.setMicrophoneEnabled(true)

                bindCamera(owner, preview, highQuality)
                startSegmentRecording()

                _state.value = _state.value.copy(
                    isStreaming = true,
                    streamStatus = "Live and publishing",
                    syncStatus = "Recording local 30s clips and syncing after finalize",
                    usingFrontCamera = currentCameraFacing == CameraFacing.FRONT,
                    canFlipCamera = hasAlternateCamera(),
                    cameraSwitchInFlight = false
                )
            } catch (exception: Exception) {
                stopImmediate()
                _state.value = CaptureRuntimeState(
                    streamStatus = "Failed to start stream",
                    syncStatus = "Capture not started",
                    lastError = exception.message ?: "Unknown start failure"
                )
            }
        }
    }

    fun flipCamera() {
        if (!_state.value.isStreaming || isSwitchingCamera) {
            return
        }
        val targetFacing = alternateCameraFacing() ?: run {
            _state.value = _state.value.copy(syncStatus = "This device does not have another camera to switch to")
            return
        }
        pendingCameraFacing = targetFacing
        isSwitchingCamera = true
        _state.value = _state.value.copy(
            cameraSwitchInFlight = true,
            syncStatus = "Switching camera while keeping session ${activeConfig?.sessionId?.take(8) ?: ""}"
        )
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
        } else {
            switchCameraAndResume(targetFacing)
        }
    }

    private var lastThermalStatus: Int = -1
    private var thermalListener: ((Int) -> Unit)? = null

    private suspend fun bindCamera(owner: LifecycleOwner, previewView: PreviewView, highQuality: Boolean) {
        val provider = ProcessCameraProvider.getInstance(appContext).await()
        cameraProvider = provider

        // Monitor thermal state if available (API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (thermalListener == null) {
                val listener: (Int) -> Unit = { status ->
                    if (status != lastThermalStatus) {
                        lastThermalStatus = status
                        val statusName = when (status) {
                            android.os.PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                            android.os.PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                            android.os.PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                            android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                            android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                            android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                            else -> "NONE"
                        }
                        android.util.Log.w("LiveCaptureManager", "Thermal status changed: $statusName ($status)")
                        if (status >= android.os.PowerManager.THERMAL_STATUS_SEVERE) {
                            _state.value = _state.value.copy(
                                syncStatus = "Thermal warning: $statusName. Device may throttle.",
                                thermalThrottling = true
                            )
                        } else {
                            _state.value = _state.value.copy(thermalThrottling = false)
                        }
                    }
                }
                thermalListener = listener
                powerManager?.addThermalStatusListener(ContextCompat.getMainExecutor(appContext), listener)
            }
        }

        val preferredFacing = pendingCameraFacing ?: currentCameraFacing
        val cameraSelector = selectCamera(provider, preferredFacing)

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        // Use SD (480p) as default to save data/bandwidth, allow FHD (1080p) as high quality option
        val targetQuality = if (highQuality) Quality.FHD else Quality.SD
        android.util.Log.d("LiveCaptureManager", "Configuring recording with quality: $targetQuality")
        
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(targetQuality, FallbackStrategy.lowerQualityOrHigherThan(targetQuality)))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture
        
        // Also adjust streaming analysis resolution to match bandwidth expectations
        val analysisResolution = if (highQuality) Size(1280, 720) else Size(640, 480)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            analysisResolution,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .build()
            
        var lastFrameTime = 0L
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val currentTime = System.currentTimeMillis()
            // Throttle to ~15 FPS (approx 66ms between frames) to ensure VideoCapture has enough bandwidth/resources
            if (currentTime - lastFrameTime < 66) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastFrameTime = currentTime

            imageProxy.use { proxy ->
                // Capture current state into local variables to avoid races during stop()
                val currentCapturer = videoCapturer
                val isStreaming = _state.value.isStreaming
                
                if (currentCapturer == null || !isTrackStarted || !isStreaming) {
                    return@setAnalyzer
                }

                try {
                    // toBitmap() converts the ImageProxy (usually YUV) to a Bitmap for LiveKit
                    var bitmap = proxy.toBitmap()
                    val rotationDegrees = proxy.imageInfo.rotationDegrees

                    // Front camera frames from ImageAnalysis are typically mirrored; flip them back for the remote viewer
                    if (currentCameraFacing == CameraFacing.FRONT) {
                        val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
                        val flippedBitmap = android.graphics.Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        bitmap.recycle()
                        bitmap = flippedBitmap
                    }
                    
                    // Final check of volatility-guarded flags before pushing to SDK
                    if (isTrackStarted && _state.value.isStreaming) {
                        currentCapturer.pushBitmap(bitmap, rotationDegrees)
                    }
                } catch (e: IllegalStateException) {
                    // LiveKit's BitmapFrameCapturer throws ISE if the internal track/executor is not yet initialized or already stopped.
                    // This is expected during transition phases (start/stop) and should be handled gracefully.
                    android.util.Log.v("LiveCaptureManager", "BitmapFrameCapturer rejected frame (not ready or stopped): ${e.message}")
                } catch (e: Exception) {
                    android.util.Log.e("LiveCaptureManager", "Failed to process or push frame to LiveKit", e)
                }
            }
        }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .addUseCase(analysis)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                owner,
                cameraSelector,
                useCaseGroup
            )
        } catch (e: Exception) {
            android.util.Log.e("LiveCaptureManager", "Failed to bind UseCaseGroup, attempting without ImageAnalysis", e)
            try {
                val fallbackGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner,
                    cameraSelector,
                    fallbackGroup
                )
                _state.value = _state.value.copy(
                    isStreaming = false,
                    streamStatus = "Streaming disabled (HW limit)",
                    syncStatus = "Recording only"
                )
            } catch (e2: Exception) {
                android.util.Log.e("LiveCaptureManager", "Fatal: Failed to bind even basic recording", e2)
                throw e2
            }
        }

        currentCameraFacing = resolvedFacing(provider, preferredFacing)
        pendingCameraFacing = null
        _state.value = _state.value.copy(
            usingFrontCamera = currentCameraFacing == CameraFacing.FRONT,
            canFlipCamera = hasAlternateCamera(provider)
        )
    }

    private fun launchRoomEvents(currentRoom: Room) {
        captureScope.launch {
            currentRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Reconnecting -> {
                        _state.value = _state.value.copy(streamStatus = "Reconnecting")
                    }

                    is RoomEvent.Reconnected -> {
                        _state.value = _state.value.copy(streamStatus = "Reconnected")
                    }

                    is RoomEvent.Disconnected -> {
                        isTrackStarted = false
                        localVideoTrack?.stopCapture()
                        localVideoTrack?.stop()
                        localVideoTrack = null
                        room = null
                        videoCapturer = null
                        _state.value = _state.value.copy(
                            isStreaming = _state.value.isStreaming,
                            streamStatus = "Disconnected",
                            syncStatus = event.error?.message ?: "Room disconnected; local recording continues"
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun startSegmentRecording() {
        val capture = videoCapture ?: return
        val config = activeConfig ?: return
        val segmentsDir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "segments"
        ).apply { mkdirs() }
        val outputFile = File(segmentsDir, "segment-${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pendingRecording = capture.output.prepareRecording(appContext, outputOptions)

        try {
            pendingRecording = pendingRecording.withAudioEnabled()
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(syncStatus = "Local recording continuing without microphone track")
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(appContext)) { event ->
            handleRecordingEvent(config, outputFile, event)
        }
        mainHandler.postDelayed(segmentStopper, SEGMENT_DURATION_MS)
    }

    private fun handleRecordingEvent(
        config: ActiveSessionConfig,
        file: File,
        event: VideoRecordEvent
    ) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _state.value = _state.value.copy(syncStatus = "Recording segment ${file.name}")
            }

            is VideoRecordEvent.Finalize -> {
                mainHandler.removeCallbacks(segmentStopper)
                activeRecording = null
                
                val duration = nanosToSeconds(event.recordingStats.recordedDurationNanos)
                val size = file.length()
                
                if (!event.hasError() && file.exists() && duration >= MIN_UPLOAD_DURATION_SEC && size > 1024) {
                    android.util.Log.d("LiveCaptureManager", "Segment finalized: ${file.name}, duration: $duration s, size: $size bytes")
                    enqueueUpload(config, file, duration)
                } else {
                    if (event.hasError()) {
                        android.util.Log.e("LiveCaptureManager", "Segment finalize error: ${event.error}, cause: ${event.cause?.message}")
                        _state.value = _state.value.copy(
                            syncStatus = "Segment finalize error: ${event.cause?.message ?: event.error}"
                        )
                    } else {
                        android.util.Log.d("LiveCaptureManager", "Discarding short or empty segment: ${file.name}, duration: ${duration}s, size: $size")
                    }
                    file.delete()
                }

                if (pendingCameraFacing != null) {
                    switchCameraAndResume(pendingCameraFacing!!)
                } else if (_state.value.isStreaming) {
                    startSegmentRecording()
                } else if (stopRequested) {
                    teardownCapturePipeline()
                    _state.value = _state.value.copy(
                        streamStatus = "Stopped",
                        syncStatus = if (pendingUploadIds.isEmpty()) {
                            "Capture stopped"
                        } else {
                            "Waiting for ${pendingUploadIds.size} queued upload(s)"
                        }
                    )
                    notifyStopSafeToReleaseIfIdle()
                }
            }

            else -> Unit
        }
    }

    private fun enqueueUpload(config: ActiveSessionConfig, file: File, durationSeconds: Int) {
        val clipFacing = if (currentCameraFacing == CameraFacing.FRONT) "FRONT" else "BACK"
        val inputData = Data.Builder()
            .putString(UploadRecordingWorker.KEY_BACKEND_URL, config.backendUrl)
            .putString(UploadRecordingWorker.KEY_AUTH_TOKEN, config.authToken)
            .putString(UploadRecordingWorker.KEY_SESSION_ID, config.sessionId)
            .putString(UploadRecordingWorker.KEY_FILE_PATH, file.absolutePath)
            .putInt(UploadRecordingWorker.KEY_DURATION_SECONDS, durationSeconds)
            .putString(UploadRecordingWorker.KEY_CAMERA_FACING, clipFacing)
            .putString(UploadRecordingWorker.KEY_CAPTURED_AT, Instant.now().toString())
            .putBoolean(UploadRecordingWorker.KEY_HIGH_QUALITY, currentHighQuality)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadRecordingWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        
        val workId = request.id
        val fileName = file.name
        pendingUploadIds += workId
        workManager.enqueue(request)

        captureScope.launch {
            var terminalStateHandled = false
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                
                val status = when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> "Queued $fileName"
                    WorkInfo.State.RUNNING -> "Uploading $fileName..."
                    WorkInfo.State.SUCCEEDED -> "Uploaded $fileName"
                    WorkInfo.State.FAILED -> "Failed to upload $fileName"
                    WorkInfo.State.BLOCKED -> "Upload $fileName blocked (waiting for network)"
                    WorkInfo.State.CANCELLED -> "Upload $fileName cancelled"
                }
                
                // Only update the sync status if we're not currently recording a new segment
                // to avoid flickering, but usually we want to see the latest upload progress.
                _state.value = _state.value.copy(syncStatus = status)

                if (workInfo.state.isFinished && !terminalStateHandled) {
                    terminalStateHandled = true
                    pendingUploadIds.remove(workId)
                    if (stopRequested && !_state.value.isStreaming) {
                        if (pendingUploadIds.isEmpty()) {
                            _state.value = _state.value.copy(syncStatus = status)
                        } else {
                            _state.value = _state.value.copy(
                                syncStatus = "$status. ${pendingUploadIds.size} upload(s) remaining"
                            )
                        }
                    }
                    notifyStopSafeToReleaseIfIdle()
                }
            }
        }
    }

    private fun notifyStopSafeToReleaseIfIdle() {
        if (stopRequested && activeRecording == null && pendingUploadIds.isEmpty()) {
            onStopSafeToRelease?.invoke()
        }
    }

    private fun switchCameraAndResume(targetFacing: CameraFacing) {
        val owner = lifecycleOwner ?: return
        val preview = previewView ?: return
        
        if (!_state.value.isStreaming) {
            isSwitchingCamera = false
            pendingCameraFacing = null
            return
        }

        pendingCameraFacing = targetFacing
        captureScope.launch {
            runCatching {
                bindCamera(owner, preview, currentHighQuality)
            }.onSuccess {
                isSwitchingCamera = false
                _state.value = _state.value.copy(
                    cameraSwitchInFlight = false,
                    syncStatus = "Switched camera; continuing same session ${activeConfig?.sessionId?.take(8) ?: ""}"
                )
                if (_state.value.isStreaming) {
                    startSegmentRecording()
                }
            }.onFailure { exception ->
                isSwitchingCamera = false
                pendingCameraFacing = null
                _state.value = _state.value.copy(
                    cameraSwitchInFlight = false,
                    syncStatus = "Camera switch failed",
                    lastError = exception.message ?: "Unable to switch camera"
                )
                if (_state.value.isStreaming && activeRecording == null) {
                    startSegmentRecording()
                }
            }
        }
    }

    private fun selectCamera(provider: ProcessCameraProvider, preferredFacing: CameraFacing): CameraSelector {
        val preferredSelector = selectorFor(preferredFacing)
        if (provider.hasCamera(preferredSelector)) {
            android.util.Log.d("LiveCaptureManager", "Using ${preferredFacing.name} camera")
            return preferredSelector
        }
        val alternateFacing = alternateOf(preferredFacing)
        val alternateSelector = selectorFor(alternateFacing)
        if (provider.hasCamera(alternateSelector)) {
            android.util.Log.d("LiveCaptureManager", "Preferred camera unavailable; falling back to ${alternateFacing.name}")
            return alternateSelector
        }
        val availableCameras = provider.availableCameraInfos
        if (availableCameras.isNotEmpty()) {
            android.util.Log.d("LiveCaptureManager", "Falling back to first available camera: ${availableCameras[0]}")
            return availableCameras[0].cameraSelector
        }
        throw IllegalStateException("No available camera found")
    }

    private fun selectorFor(facing: CameraFacing): CameraSelector {
        return CameraSelector.Builder().requireLensFacing(facing.lensFacing).build()
    }

    private fun alternateCameraFacing(): CameraFacing? {
        val provider = cameraProvider ?: return null
        return alternateCameraFacing(provider)
    }

    private fun alternateCameraFacing(provider: ProcessCameraProvider): CameraFacing? {
        val alternateFacing = alternateOf(currentCameraFacing)
        return if (provider.hasCamera(selectorFor(alternateFacing))) alternateFacing else null
    }

    private fun hasAlternateCamera(provider: ProcessCameraProvider? = cameraProvider): Boolean {
        return provider?.let { alternateCameraFacing(it) != null } ?: false
    }

    private fun alternateOf(facing: CameraFacing): CameraFacing {
        return if (facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
    }

    private fun resolvedFacing(provider: ProcessCameraProvider, preferredFacing: CameraFacing): CameraFacing {
        return if (provider.hasCamera(selectorFor(preferredFacing))) {
            preferredFacing
        } else {
            alternateOf(preferredFacing)
        }
    }

    private fun nanosToSeconds(recordedDurationNanos: Long): Int {
        return (recordedDurationNanos / 1_000_000_000L).toInt()
    }

    companion object {
        private const val SEGMENT_DURATION_MS = 30_000L
        private const val MIN_UPLOAD_DURATION_SEC = 2
    }
}
