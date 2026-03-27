package com.cyclecomp.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives start/stop commands from the phone app via the Wearable Data Layer.
 * Directly starts/stops HR tracking without needing the Activity to be open.
 */
class PhoneCommandListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneCommandListener"
        const val COMMAND_PATH = "/cyclecomp/command"

        // Shared singleton so the service and activity use the same manager
        var sharedHealthServicesManager: HealthServicesManager? = null
        var sharedDataLayerSender: DataLayerSender? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == COMMAND_PATH) {
            val command = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received command from phone: $command")

            // Initialize managers if not already done
            if (sharedHealthServicesManager == null) {
                sharedHealthServicesManager = HealthServicesManager(applicationContext)
            }
            if (sharedDataLayerSender == null) {
                sharedDataLayerSender = DataLayerSender(applicationContext)
            }

            val manager = sharedHealthServicesManager!!
            val sender = sharedDataLayerSender!!

            when (command.lowercase()) {
                "start" -> {
                    Log.d(TAG, "Starting HR tracking from phone command")
                    // Start foreground service to keep tracking alive
                    val serviceIntent = Intent(applicationContext, HrTrackingService::class.java)
                    applicationContext.startForegroundService(serviceIntent)
                    // Also launch the activity so user can see it on watch
                    val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(launchIntent)
                }
                "stop" -> {
                    Log.d(TAG, "Stopping HR tracking from phone command")
                    val serviceIntent = Intent(applicationContext, HrTrackingService::class.java)
                    applicationContext.stopService(serviceIntent)
                    serviceScope.launch {
                        manager.stopTracking()
                    }
                }
                else -> Log.w(TAG, "Unknown command: $command")
            }
        }
    }
}
