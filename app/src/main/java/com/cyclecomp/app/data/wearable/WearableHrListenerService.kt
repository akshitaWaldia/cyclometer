package com.cyclecomp.app.data.wearable

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Background WearableListenerService that receives HR messages from the watch
 * even when the app is not in the foreground. Delegates to the singleton
 * WearableHrReceiverImpl via a static callback.
 *
 * This ensures HR data is captured during active rides when the phone
 * screen may be off or the app backgrounded.
 */
class WearableHrListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableHrListenerSvc"
        const val HR_PATH = "/cyclecomp/hr"

        /**
         * Static callback set by WearableHrReceiverImpl so the service
         * can forward messages to the active receiver instance.
         */
        var onHrReceived: ((Int, Long) -> Unit)? = null
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == HR_PATH) {
            try {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                val hr = json.getInt("hr")
                val timestamp = json.getLong("timestamp")
                Log.d(TAG, "Background HR received: $hr bpm")
                onHrReceived?.invoke(hr, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse HR message in service", e)
            }
        }
    }
}
