package com.cyclecomp.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectHrReaderImpl @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope
) : HealthConnectHrReader {

    companion object {
        private const val TAG = "HealthConnectHrReader"
        private const val POLL_INTERVAL_MS = 2500L
        private const val LOOKBACK_SECONDS = 5L

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    private val _latestHeartRate = MutableStateFlow<Int?>(null)
    override val latestHeartRate: StateFlow<Int?> = _latestHeartRate.asStateFlow()

    private var pollJob: Job? = null
    private var healthConnectClient: HealthConnectClient? = null

    override fun start() {
        if (pollJob != null) return

        // Check if Health Connect is available
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Log.w(TAG, "Health Connect not available (status=$status)")
            return
        }

        try {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Health Connect client", e)
            return
        }

        pollJob = scope.launch {
            Log.d(TAG, "Starting HR polling from Health Connect")
            while (isActive) {
                try {
                    pollHeartRate()
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling HR from Health Connect", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        _latestHeartRate.value = null
        Log.d(TAG, "Stopped HR polling")
    }

    private suspend fun pollHeartRate() {
        val client = healthConnectClient ?: return

        val now = Instant.now()
        val lookback = now.minusSeconds(LOOKBACK_SECONDS)

        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(lookback, now)
        )

        val response = client.readRecords(request)
        val records = response.records

        if (records.isNotEmpty()) {
            // Get the most recent sample from the most recent record
            val latestRecord = records.last()
            val latestSample = latestRecord.samples.lastOrNull()
            if (latestSample != null) {
                _latestHeartRate.value = latestSample.beatsPerMinute.toInt()
            }
        } else {
            // No recent HR data — mark as null (stale)
            _latestHeartRate.value = null
        }
    }
}
