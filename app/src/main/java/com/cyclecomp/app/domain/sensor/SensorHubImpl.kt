package com.cyclecomp.app.domain.sensor

import android.util.Log
import com.cyclecomp.app.data.ble.BleCharacteristicParsers
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.ble.BleManagerImpl
import com.cyclecomp.app.data.gps.GpsProvider
import com.cyclecomp.app.data.health.HealthConnectHrReader
import com.cyclecomp.app.data.wearable.WearableHrReceiver
import com.cyclecomp.app.domain.model.GpsSource
import com.cyclecomp.app.domain.model.HeartRateZone
import com.cyclecomp.app.domain.model.SensorSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorHubImpl @Inject constructor(
    private val wearableHrReceiver: WearableHrReceiver,
    private val healthConnectHrReader: HealthConnectHrReader,
    private val bleManager: BleManager,
    private val gpsProvider: GpsProvider,
    private val scope: CoroutineScope
) : SensorHub {

    companion object {
        private const val TAG = "SensorHub"
        private const val HR_STALENESS_MS = 10_000L
        private const val CADENCE_STALENESS_MS = 5_000L
        private const val GPS_STALENESS_MS = 5_000L
    }

    private val _sensorSnapshot = MutableStateFlow(
        SensorSnapshot(
            heartRateBpm = null,
            cadenceRpm = null,
            speedKmh = null,
            locationLat = null,
            locationLon = null,
            altitudeM = null,
            gradientPercent = null,
            gpsSource = GpsSource.NONE,
            timestamp = System.currentTimeMillis()
        )
    )
    override val sensorSnapshot: StateFlow<SensorSnapshot> = _sensorSnapshot.asStateFlow()

    private val _heartRateZone = MutableStateFlow<HeartRateZone?>(null)
    override val heartRateZone: StateFlow<HeartRateZone?> = _heartRateZone.asStateFlow()

    // Tracked latest values with timestamps for staleness
    private var lastHrBpm: Int? = null
    private var lastHrTimestamp: Long = 0L

    private var lastCadenceRpm: Int? = null
    private var lastCadenceTimestamp: Long = 0L
    private var previousCscMeasurement: BleCharacteristicParsers.CscMeasurement? = null

    private var collectJobs = mutableListOf<Job>()
    private val cscJobs = mutableMapOf<String, Job>()

    override fun start() {
        // Start sub-providers
        // Primary HR source: Wearable Data Layer from Galaxy Watch
        wearableHrReceiver.start()
        // Fallback HR source: Health Connect (kept but secondary)
        healthConnectHrReader.start()
        gpsProvider.start()

        // Collect HR from Wearable Data Layer (primary)
        collectJobs += scope.launch {
            wearableHrReceiver.latestHeartRate.collectLatest { hr ->
                if (hr != null) {
                    lastHrBpm = hr
                    lastHrTimestamp = System.currentTimeMillis()
                }
                emitSnapshot()
            }
        }

        // Collect HR from Health Connect (fallback — only used if wearable has no data)
        collectJobs += scope.launch {
            healthConnectHrReader.latestHeartRate.collectLatest { hr ->
                // Only use Health Connect HR if wearable HR is stale
                val now = System.currentTimeMillis()
                val wearableStale = lastHrBpm == null || (now - lastHrTimestamp) > HR_STALENESS_MS
                if (hr != null && wearableStale) {
                    lastHrBpm = hr
                    lastHrTimestamp = System.currentTimeMillis()
                }
                emitSnapshot()
            }
        }

        // Also collect from all connected CSC devices
        collectJobs += scope.launch {
            bleManager.connectionStates.collectLatest { states ->
                // Re-subscribe to CSC flows for connected devices
                for ((address, state) in states) {
                    if (state == com.cyclecomp.app.domain.model.ConnectionState.CONNECTED) {
                        collectCscFromDevice(address)
                    }
                }
            }
        }

        // Collect GPS data
        collectJobs += scope.launch {
            gpsProvider.location.collectLatest {
                emitSnapshot()
            }
        }

        Log.d(TAG, "SensorHub started")
    }

    private fun collectCscFromDevice(address: String) {
        cscJobs[address]?.cancel()
        cscJobs[address] = scope.launch {
            bleManager.getCharacteristicFlow(address, BleManagerImpl.CSC_MEASUREMENT_UUID)
                .collectLatest { data ->
                    processCscData(data)
                    emitSnapshot()
                }
        }
    }

    private fun processCscData(data: ByteArray) {
        val measurement = BleCharacteristicParsers.parseCscMeasurement(data) ?: return
        val prev = previousCscMeasurement
        if (prev != null) {
            val rpm = BleCharacteristicParsers.deriveCadenceRpm(prev, measurement)
            if (rpm != null) {
                lastCadenceRpm = rpm
                lastCadenceTimestamp = System.currentTimeMillis()
            }
        }
        previousCscMeasurement = measurement
    }

    override fun stop() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        cscJobs.values.forEach { it.cancel() }
        cscJobs.clear()
        wearableHrReceiver.stop()
        healthConnectHrReader.stop()
        gpsProvider.stop()
        previousCscMeasurement = null
        Log.d(TAG, "SensorHub stopped")
    }

    private fun emitSnapshot() {
        val now = System.currentTimeMillis()

        // Staleness: if HR hasn't updated within timeout, report null
        val hr = if (lastHrBpm != null && (now - lastHrTimestamp) < HR_STALENESS_MS) {
            lastHrBpm
        } else {
            null
        }

        // Staleness: if cadence hasn't updated within timeout, report null
        val cadence = if (lastCadenceRpm != null && (now - lastCadenceTimestamp) < CADENCE_STALENESS_MS) {
            lastCadenceRpm
        } else {
            null
        }

        // GPS data
        val gpsReading = gpsProvider.location.value
        val gpsStale = gpsReading == null || (now - gpsReading.timestamp) > GPS_STALENESS_MS

        val speedKmh = if (!gpsStale && gpsReading != null) {
            gpsReading.speedMps * 3.6
        } else {
            null
        }

        val gpsSource = if (!gpsStale && gpsReading != null) {
            gpsReading.source
        } else {
            GpsSource.NONE
        }

        // Heart rate zone
        val zone = if (hr != null) {
            try {
                HeartRateZone.fromBpm(hr)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        _heartRateZone.value = zone

        val gradient = gpsProvider.currentGradientPercent.value

        _sensorSnapshot.value = SensorSnapshot(
            heartRateBpm = hr,
            cadenceRpm = cadence,
            speedKmh = speedKmh,
            locationLat = if (!gpsStale) gpsReading?.latitude else null,
            locationLon = if (!gpsStale) gpsReading?.longitude else null,
            altitudeM = if (!gpsStale) gpsReading?.altitudeM else null,
            gradientPercent = if (!gpsStale) gradient else null,
            gpsSource = gpsSource,
            timestamp = now
        )
    }
}
