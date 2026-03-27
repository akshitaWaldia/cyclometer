package com.cyclecomp.app.domain.ride

import android.os.SystemClock
import com.cyclecomp.app.data.gps.GpsProvider
import com.cyclecomp.app.domain.calc.CalorieAndTssCalculator
import com.cyclecomp.app.domain.model.LapData
import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RideState
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.SensorSnapshot
import com.cyclecomp.app.domain.model.TrackPoint
import com.cyclecomp.app.domain.sensor.PowerEstimator
import com.cyclecomp.app.domain.sensor.SensorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRecorderImpl @Inject constructor(
    private val sensorHub: SensorHub,
    private val powerEstimator: PowerEstimator,
    private val calorieAndTssCalculator: CalorieAndTssCalculator,
    private val gpsProvider: GpsProvider,
    private val scope: CoroutineScope
) : RideRecorder {

    private val _rideState = MutableStateFlow(RideState.IDLE)
    override val rideState: StateFlow<RideState> = _rideState.asStateFlow()

    private val _elapsedTimeMs = MutableStateFlow(0L)
    override val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs.asStateFlow()

    // Timing
    private var startRealtimeMs = 0L
    private var accumulatedTimeMs = 0L
    private var pauseRealtimeMs = 0L

    // Ride data
    private var rideStartInstant: Instant? = null
    private val trackPoints = mutableListOf<TrackPoint>()
    private val laps = mutableListOf<LapData>()
    private var riderProfile = RiderProfile()

    // Jobs
    private var timerJob: Job? = null
    private var snapshotCollectJob: Job? = null

    override fun start() {
        if (_rideState.value != RideState.IDLE && _rideState.value != RideState.STOPPED) return

        // Reset state
        trackPoints.clear()
        laps.clear()
        accumulatedTimeMs = 0L
        _elapsedTimeMs.value = 0L
        powerEstimator.reset()
        calorieAndTssCalculator.reset()

        rideStartInstant = Instant.now()
        startRealtimeMs = SystemClock.elapsedRealtime()
        _rideState.value = RideState.RECORDING

        startTimer()
        startSnapshotCollection()
    }

    override fun pause() {
        if (_rideState.value != RideState.RECORDING) return

        pauseRealtimeMs = SystemClock.elapsedRealtime()
        accumulatedTimeMs += pauseRealtimeMs - startRealtimeMs
        _rideState.value = RideState.PAUSED

        timerJob?.cancel()
    }

    override fun resume() {
        if (_rideState.value != RideState.PAUSED) return

        startRealtimeMs = SystemClock.elapsedRealtime()
        _rideState.value = RideState.RECORDING

        startTimer()
    }

    override fun stop(): RideData? {
        if (_rideState.value != RideState.RECORDING && _rideState.value != RideState.PAUSED) return null

        if (_rideState.value == RideState.RECORDING) {
            accumulatedTimeMs += SystemClock.elapsedRealtime() - startRealtimeMs
        }

        _rideState.value = RideState.STOPPED
        timerJob?.cancel()
        snapshotCollectJob?.cancel()

        val startInstant = rideStartInstant ?: return null
        val endInstant = Instant.now()

        val avgSpeed = if (accumulatedTimeMs > 0) {
            gpsProvider.cumulativeDistanceKm.value / (accumulatedTimeMs / 3_600_000.0)
        } else 0.0

        val avgPower = powerEstimator.averagePowerW.value
        val np = powerEstimator.normalizedPowerW.value
        val maxSpeed = trackPoints.maxOfOrNull { it.speedKmh } ?: 0.0
        val maxPower = trackPoints.maxOfOrNull { it.powerW } ?: 0.0
        val maxHr = trackPoints.mapNotNull { it.heartRateBpm }.maxOrNull()
        val avgHr = trackPoints.mapNotNull { it.heartRateBpm }.let { hrs ->
            if (hrs.isNotEmpty()) hrs.average().toInt() else null
        }
        val avgCadence = trackPoints.mapNotNull { it.cadenceRpm }.let { rpms ->
            if (rpms.isNotEmpty()) rpms.average().toInt() else null
        }

        return RideData(
            id = UUID.randomUUID().toString(),
            startTime = startInstant,
            endTime = endInstant,
            elapsedDuration = Duration.ofMillis(accumulatedTimeMs),
            totalDistanceKm = gpsProvider.cumulativeDistanceKm.value,
            totalElevationGainM = gpsProvider.cumulativeElevationGainM.value,
            averageSpeedKmh = avgSpeed,
            averagePowerW = avgPower,
            normalizedPowerW = np,
            maxSpeedKmh = maxSpeed,
            maxPowerW = maxPower,
            maxHeartRateBpm = maxHr,
            averageHeartRateBpm = avgHr,
            averageCadenceRpm = avgCadence,
            caloriesKcal = calorieAndTssCalculator.caloriesBurned.value,
            tss = calorieAndTssCalculator.tss.value,
            trackPoints = trackPoints.toList(),
            laps = laps.toList(),
            riderProfile = riderProfile
        )
    }

    fun setRiderProfile(profile: RiderProfile) {
        riderProfile = profile
    }

    fun addLap(lapData: LapData) {
        laps.add(lapData)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                if (_rideState.value == RideState.RECORDING) {
                    val currentElapsed = accumulatedTimeMs + (SystemClock.elapsedRealtime() - startRealtimeMs)
                    _elapsedTimeMs.value = currentElapsed
                }
                delay(1000L)
            }
        }
    }

    private fun startSnapshotCollection() {
        snapshotCollectJob?.cancel()
        snapshotCollectJob = scope.launch {
            sensorHub.sensorSnapshot.collect { snapshot ->
                if (_rideState.value == RideState.RECORDING) {
                    processSnapshot(snapshot)
                }
            }
        }
    }

    private fun processSnapshot(snapshot: SensorSnapshot) {
        val speedMps = (snapshot.speedKmh ?: 0.0) / 3.6
        val gradient = snapshot.gradientPercent ?: 0.0

        // Update power estimator
        powerEstimator.update(speedMps, gradient)

        // Update calorie/TSS calculator
        val durationSec = _elapsedTimeMs.value / 1000.0
        calorieAndTssCalculator.update(
            heartRateBpm = snapshot.heartRateBpm,
            normalizedPowerW = powerEstimator.normalizedPowerW.value,
            durationSec = durationSec
        )

        // Append track point
        if (snapshot.locationLat != null && snapshot.locationLon != null) {
            trackPoints.add(
                TrackPoint(
                    timestamp = Instant.now(),
                    latitude = snapshot.locationLat,
                    longitude = snapshot.locationLon,
                    altitudeM = snapshot.altitudeM ?: 0.0,
                    speedKmh = snapshot.speedKmh ?: 0.0,
                    heartRateBpm = snapshot.heartRateBpm,
                    cadenceRpm = snapshot.cadenceRpm,
                    powerW = powerEstimator.currentPowerW.value,
                    gradientPercent = gradient,
                    cumulativeDistanceKm = gpsProvider.cumulativeDistanceKm.value
                )
            )
        }
    }
}
