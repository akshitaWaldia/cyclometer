package com.cyclecomp.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Sends heart rate data from the watch to the phone app
 * via the Wearable Data Layer MessageClient.
 */
class DataLayerSender(context: Context) {

    companion object {
        private const val TAG = "DataLayerSender"
        const val HR_PATH = "/cyclecomp/hr"
    }

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /**
     * Sends a heart rate update to all connected phone nodes.
     */
    suspend fun sendHeartRate(bpm: Int) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found")
                return
            }

            val json = JSONObject().apply {
                put("hr", bpm)
                put("timestamp", System.currentTimeMillis())
            }
            val payload = json.toString().toByteArray(Charsets.UTF_8)

            for (node in nodes) {
                messageClient.sendMessage(node.id, HR_PATH, payload).await()
                Log.d(TAG, "Sent HR=$bpm to node ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send HR data", e)
        }
    }
}
