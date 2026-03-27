package com.cyclecomp.app.data.health

import kotlinx.coroutines.flow.StateFlow

/**
 * Reads heart rate data from Health Connect, which receives HR from
 * Samsung Health on the watch via Samsung Health on the phone.
 * Polls every 2-3 seconds for the latest HeartRateRecord.
 */
interface HealthConnectHrReader {
    val latestHeartRate: StateFlow<Int?>

    fun start()
    fun stop()
}
