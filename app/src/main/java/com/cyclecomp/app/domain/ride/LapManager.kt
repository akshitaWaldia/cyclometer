package com.cyclecomp.app.domain.ride

import com.cyclecomp.app.domain.model.LapData
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages lap tracking during a ride.
 * On lap mark: captures current lap metrics and resets counters.
 */
interface LapManager {
    val currentLap: StateFlow<LapData?>
    val completedLaps: StateFlow<List<LapData>>

    fun startNewRide()
    fun markLap(currentDistanceKm: Double, currentPowerW: Double, elapsedTimeMs: Long)
    fun reset()
}
