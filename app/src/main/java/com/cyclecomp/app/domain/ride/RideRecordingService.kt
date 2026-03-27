package com.cyclecomp.app.domain.ride

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cyclecomp.app.MainActivity
import com.cyclecomp.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive during ride recording.
 * This ensures BLE connections and GPS remain active when the app is backgrounded.
 * The actual recording logic lives in RideRecorder — this service just keeps the process alive
 * and shows a persistent notification with ride stats.
 */
@AndroidEntryPoint
class RideRecordingService : Service() {

    companion object {
        private const val TAG = "RideRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ride_recording_channel"
        private const val CHANNEL_NAME = "Ride Recording"
        const val ACTION_STOP_RIDE = "com.cyclecomp.app.STOP_RIDE"

        fun start(context: Context) {
            val intent = Intent(context, RideRecordingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideRecordingService::class.java)
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var rideRecorder: RideRecorder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RIDE) {
            Log.d(TAG, "Stop ride action received from notification")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting foreground ride recording service")
        startForeground(NOTIFICATION_ID, buildNotification("00:00:00", null))
        acquireWakeLock()
        startNotificationUpdates()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Stopping foreground ride recording service")
        updateJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows ride recording status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedTime: String, heartRate: Int?): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RideRecordingService::class.java).apply {
            action = ACTION_STOP_RIDE
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append("Time: $elapsedTime")
            if (heartRate != null) {
                append(" | HR: $heartRate bpm")
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CycleComp — Recording")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_ride_notification)
            .setOngoing(true)
            .setContentIntent(pendingContent)
            .addAction(0, "Stop Ride", pendingStop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startNotificationUpdates() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val elapsedMs = rideRecorder.elapsedTimeMs.value
                val formatted = formatElapsedTime(elapsedMs)
                // We don't have direct HR access here, so pass null
                // The notification shows time; HR is visible on the dashboard
                val notification = buildNotification(formatted, null)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
                delay(1000L)
            }
        }
    }

    private fun formatElapsedTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CycleComp::RideRecording"
        ).apply {
            acquire()
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }
}
