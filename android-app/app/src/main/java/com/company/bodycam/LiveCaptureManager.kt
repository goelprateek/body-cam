package com.company.bodycam

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
        mainHandler.removeCallbacks(segmentStopper)
        activeRecording?.stop()
        activeRecording = null
        localVideoTrack?.stop()
        localVideoTrack = null
        room?.disconnect()
        room?.release()
        room = null
        videoCapturer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        activeConfig = null
        _state.value = CaptureRuntimeState()
    }

    fun start(config: ActiveSessionConfig) {
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
                localVideoTrack = track
                localParticipant.publishVideoTrack(track)
                localParticipant.setMicrophoneEnabled(true)

                bindCamera(owner, preview)
                startSegmentRecording()

                _state.value = _state.value.copy(
                    isStreaming = true,
                    streamStatus = "Live and publishing",
                    syncStatus = "Recording local 15s clips and syncing after finalize"
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

    private suspend fun bindCamera(owner: LifecycleOwner, previewView: PreviewView) {
        val provider = ProcessCameraProvider.getInstance(appContext).await()
        cameraProvider = provider
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val bitmap = YuvFrameConverter.toBitmap(imageProxy) ?: return@setAnalyzer
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                videoCapturer?.pushBitmap(bitmap, rotationDegrees)
            } finally {
                imageProxy.close()
            }
        }

        provider.unbindAll()
        provider.bindToLifecycle(
            owner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
            analysis
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
                    enqueueUpload(config, file, nanosToSeconds(event.recordingStats.recordedDurationNanos))
                    if (_state.value.isStreaming) {
                        startSegmentRecording()
                    }
                } else {
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
            .build()
        workManager.enqueue(request)
        _state.value = _state.value.copy(syncStatus = "Queued ${file.name} for upload")
    }

    private fun nanosToSeconds(recordedDurationNanos: Long): Int {
        return (recordedDurationNanos / 1_000_000_000L).coerceAtLeast(1L).toInt()
    }

    companion object {
        private const val SEGMENT_DURATION_MS = 15_000L
    }
}
