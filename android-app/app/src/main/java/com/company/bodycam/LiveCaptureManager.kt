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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveCaptureManager(
    private val appContext: Context
) {

    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workManager = WorkManager.getInstance(appContext)
    private val _state = MutableStateFlow(CaptureRuntimeState())

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

    val state: StateFlow<CaptureRuntimeState> = _state.asStateFlow()

    private val segmentStopper = Runnable {
        activeRecording?.stop()
    }

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
    }

    fun release() {
        stop()
        cameraExecutor.shutdown()
        captureScope.cancel()
    }

    fun stop() {
        isTrackStarted = false
        mainHandler.removeCallbacks(segmentStopper)
        activeRecording?.stop()
        activeRecording = null
        localVideoTrack?.stopCapture()
        localVideoTrack?.stop()
        localVideoTrack = null
        room?.disconnect()
        room = null
        videoCapturer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        activeConfig = null

        _state.value = CaptureRuntimeState(
            isStreaming = false,
            streamStatus = "Stopped",
            syncStatus = "Capture idle"
        )
    }

    fun start(config: ActiveSessionConfig, highQuality: Boolean = false) {
        val owner = lifecycleOwner ?: throw IllegalStateException("Preview not bound")
        val preview = previewView ?: throw IllegalStateException("Preview view not bound")
        activeConfig = config
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
                    syncStatus = "Recording local 5m clips and syncing after finalize"
                )
            } catch (exception: Exception) {
                stop()
                _state.value = CaptureRuntimeState(
                    streamStatus = "Failed to start stream",
                    syncStatus = "Capture not started",
                    lastError = exception.message ?: "Unknown start failure"
                )
            }
        }
    }

    private suspend fun bindCamera(owner: LifecycleOwner, previewView: PreviewView, highQuality: Boolean) {
        val provider = ProcessCameraProvider.getInstance(appContext).await()
        cameraProvider = provider

        // Emulator debugging: explicitly check for any camera if front/back fails
        val cameraSelector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                android.util.Log.d("LiveCaptureManager", "Using DEFAULT_BACK_CAMERA")
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                android.util.Log.d("LiveCaptureManager", "Using DEFAULT_FRONT_CAMERA")
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            else -> {
                val availableCameras = provider.availableCameraInfos
                if (availableCameras.isNotEmpty()) {
                    android.util.Log.d("LiveCaptureManager", "Falling back to first available camera: ${availableCameras[0]}")
                    availableCameras[0].cameraSelector
                } else {
                    throw IllegalStateException("No available camera found")
                }
            }
        }

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
                    val bitmap = proxy.toBitmap()
                    val rotationDegrees = proxy.imageInfo.rotationDegrees
                    
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

        provider.unbindAll()
        provider.bindToLifecycle(
            owner,
            cameraSelector,
            useCaseGroup
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
                        stop()
                        _state.value = _state.value.copy(
                            isStreaming = false,
                            streamStatus = "Disconnected",
                            syncStatus = event.error?.message ?: "Room disconnected"
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
                if (!event.hasError() && file.exists()) {
                    val duration = nanosToSeconds(event.recordingStats.recordedDurationNanos)
                    val size = file.length()
                    android.util.Log.d("LiveCaptureManager", "Segment finalized: ${file.name}, duration: $duration s, size: $size bytes")
                    enqueueUpload(config, file, duration)
                    if (_state.value.isStreaming) {
                        startSegmentRecording()
                    }
                } else {
                    android.util.Log.e("LiveCaptureManager", "Segment finalize error: ${event.error}, cause: ${event.cause?.message}")
                    file.delete()
                    _state.value = _state.value.copy(
                        syncStatus = "Segment finalize error: ${event.cause?.message ?: event.error}"
                    )
                    if (_state.value.isStreaming) {
                        startSegmentRecording()
                    }
                }
            }

            else -> Unit
        }
    }

    private fun enqueueUpload(config: ActiveSessionConfig, file: File, durationSeconds: Int) {
        val inputData = Data.Builder()
            .putString(UploadRecordingWorker.KEY_BACKEND_URL, config.backendUrl)
            .putString(UploadRecordingWorker.KEY_AUTH_TOKEN, config.authToken)
            .putString(UploadRecordingWorker.KEY_SESSION_ID, config.sessionId)
            .putString(UploadRecordingWorker.KEY_FILE_PATH, file.absolutePath)
            .putInt(UploadRecordingWorker.KEY_DURATION_SECONDS, durationSeconds)
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
        workManager.enqueue(request)
        _state.value = _state.value.copy(syncStatus = "Queued ${file.name} for upload")
    }

    private fun nanosToSeconds(recordedDurationNanos: Long): Int {
        return (recordedDurationNanos / 1_000_000_000L).coerceAtLeast(1L).toInt()
    }

    companion object {
        private const val SEGMENT_DURATION_MS = 300_000L
    }
}
