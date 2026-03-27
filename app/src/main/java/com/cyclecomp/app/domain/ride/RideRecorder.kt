package com.cyclecomp.app.domain.ride

import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RideState
import kotlinx.coroutines.flow.StateFlow

/**
 * Records ride data with state machine: IDLE → RECORDING → PAUSED → STOPPED.
 * Tracks elapsed time excluding paused periods.
 */
interface RideRecorder {
    val rideState: StateFlow<RideState>
    val elapsedTimeMs: StateFlow<Long>

    fun start()
    fun pause()
    fun resume()
    fun stop(): RideData?
}
