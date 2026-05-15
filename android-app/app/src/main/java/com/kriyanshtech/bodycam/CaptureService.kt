package com.kriyanshtech.bodycam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class CaptureService : LifecycleService() {

    private val binder = LocalBinder()
    var captureManager: LiveCaptureManager? = null
        private set
    @Volatile
    private var lastStartError: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): CaptureService = this@CaptureService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        captureManager = LiveCaptureManager(
            appContext = applicationContext,
            captureLifecycleOwner = this
        ).apply {
            onStopSafeToRelease = {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        createNotificationChannel()
    }

    override fun onDestroy() {
        captureManager?.release()
        captureManager = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return try {
            val notification = createNotification("BodyCam is recording...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            lastStartError = null
            START_STICKY
        } catch (exception: Exception) {
            lastStartError = exception.message ?: "Unable to start foreground capture service"
            Log.e("CaptureService", "Failed to enter foreground mode", exception)
            captureManager?.stopImmediate()
            stopSelf()
            START_NOT_STICKY
        }
    }

    fun stopCaptureGracefully() {
        val manager = captureManager
        if (manager == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        manager.stopSession()
    }

    fun startForegroundCaptureService() {
        lastStartError = null
        ContextCompat.startForegroundService(
            this,
            Intent(this, CaptureService::class.java)
        )
    }

    fun consumeLastStartError(): String? {
        val error = lastStartError
        lastStartError = null
        return error
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "capture_service_channel"
    }
}
