package com.cyclecomp.app.domain.ride

import com.cyclecomp.app.domain.model.LapData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LapManagerImpl @Inject constructor() : LapManager {

    private val _currentLap = MutableStateFlow<LapData?>(null)
    override val currentLap: StateFlow<LapData?> = _currentLap.asStateFlow()

    private val _completedLaps = MutableStateFlow<List<LapData>>(emptyList())
    override val completedLaps: StateFlow<List<LapData>> = _completedLaps.asStateFlow()

    private var lapStartDistanceKm = 0.0
    private var lapStartTimeMs = 0L
    private var lapPowerSamples = mutableListOf<Double>()
    private var lapNumber = 1

    override fun startNewRide() {
        reset()
        lapNumber = 1
        lapStartDistanceKm = 0.0
        lapStartTimeMs = 0L
    }

    override fun markLap(currentDistanceKm: Double, currentPowerW: Double, elapsedTimeMs: Long) {
        val lapDistanceKm = currentDistanceKm - lapStartDistanceKm
        val lapElapsedMs = elapsedTimeMs - lapStartTimeMs
        val avgPower = if (lapPowerSamples.isNotEmpty()) lapPowerSamples.average() else 0.0

        val completedLap = LapData(
            lapNumber = lapNumber,
            startTime = Instant.now().minusMillis(lapElapsedMs),
            endTime = Instant.now(),
            distanceKm = lapDistanceKm,
            averagePowerW = avgPower,
            elapsedTime = Duration.ofMillis(lapElapsedMs)
        )

        _completedLaps.value = _completedLaps.value + completedLap

        // Reset for next lap
        lapNumber++
        lapStartDistanceKm = currentDistanceKm
        lapStartTimeMs = elapsedTimeMs
        lapPowerSamples.clear()
    }

    fun addPowerSample(powerW: Double) {
        lapPowerSamples.add(powerW)
    }

    override fun reset() {
        _currentLap.value = null
        _completedLaps.value = emptyList()
        lapStartDistanceKm = 0.0
        lapStartTimeMs = 0L
        lapPowerSamples.clear()
        lapNumber = 1
    }
}
