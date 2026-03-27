package com.cyclecomp.app.domain.ride

import kotlinx.coroutines.flow.StateFlow

/**
 * Automatically pauses/resumes ride recording based on speed.
 * Pause when speed < 2 km/h for >3 seconds; resume immediately when speed > 2 km/h.
 */
interface AutoPauseController {
    val isAutoPaused: StateFlow<Boolean>

    fun onSpeedUpdate(speedKmh: Double)
    fun reset()
}
