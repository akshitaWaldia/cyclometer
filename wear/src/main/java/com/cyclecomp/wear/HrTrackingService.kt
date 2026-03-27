package com.cyclecomp.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps HR tracking alive on the watch
 * even when the screen is off or the app is in the background.
 */
class HrTrackingService : Service() {

    companion object {
        private const val TAG = "HrTrackingService"
        private const val CHANNEL_ID = "hr_tracking"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HR tracking service started")

        val manager = PhoneCommandListenerService.sharedHealthServicesManager
        val sender = PhoneCommandListenerService.sharedDataLayerSender

        if (manager != null && sender != null) {
            serviceScope.launch {
                manager.startTracking()
            }
            serviceScope.launch {
                manager.heartRate.collectLatest { hr ->
                    if (hr != null) {
                        sender.sendHeartRate(hr)
                        val notification = createNotification("HR: $hr bpm")
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "HR tracking service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HR Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps heart rate tracking active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CycleComp")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
