package com.cyclecomp.app.data.wearable

import kotlinx.coroutines.flow.StateFlow

/**
 * Receives real-time heart rate data from the Wear OS companion app
 * via the Wearable Data Layer MessageClient.
 *
 * Replaces HealthConnectHrReader as the primary HR source when a
 * Galaxy Watch 8 is paired and running the CycleComp Watch app.
 */
interface WearableHrReceiver {
    /** Latest heart rate in bpm from the watch, or null if no data. */
    val latestHeartRate: StateFlow<Int?>

    /** Whether the receiver is actively listening for watch messages. */
    val isConnected: StateFlow<Boolean>

    /** Start listening for HR messages from the watch. */
    fun start()

    /** Stop listening for HR messages. */
    fun stop()

    /** Send a start-tracking command to the watch. */
    suspend fun sendStartCommand()

    /** Send a stop-tracking command to the watch. */
    suspend fun sendStopCommand()
}
