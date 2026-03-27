package com.cyclecomp.app.data.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableHrReceiverImpl @Inject constructor(
    private val context: Context
) : WearableHrReceiver, MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WearableHrReceiver"
        const val HR_PATH = "/cyclecomp/hr"
        const val COMMAND_PATH = "/cyclecomp/command"
    }

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val _latestHeartRate = MutableStateFlow<Int?>(null)
    override val latestHeartRate: StateFlow<Int?> = _latestHeartRate.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var isListening = false

    override fun start() {
        if (isListening) return
        messageClient.addListener(this)
        isListening = true
        _isConnected.value = true
        Log.d(TAG, "Started listening for watch HR messages")
    }

    override fun stop() {
        if (!isListening) return
        messageClient.removeListener(this)
        isListening = false
        _isConnected.value = false
        _latestHeartRate.value = null
        Log.d(TAG, "Stopped listening for watch HR messages")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == HR_PATH) {
            try {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                val hr = json.getInt("hr")
                _latestHeartRate.value = hr
                Log.d(TAG, "Received HR from watch: $hr bpm")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse HR message", e)
            }
        }
    }

    override suspend fun sendStartCommand() {
        sendCommand("start")
    }

    override suspend fun sendStopCommand() {
        sendCommand("stop")
    }

    private suspend fun sendCommand(command: String) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected watch nodes to send command")
                return
            }
            val payload = command.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, COMMAND_PATH, payload).await()
                Log.d(TAG, "Sent '$command' command to watch node ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command '$command' to watch", e)
        }
    }
}
