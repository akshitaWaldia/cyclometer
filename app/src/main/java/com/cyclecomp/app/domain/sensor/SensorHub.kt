package com.cyclecomp.app.domain.sensor

import com.cyclecomp.app.domain.model.HeartRateZone
import com.cyclecomp.app.domain.model.SensorSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Combines HR (from Health Connect), cadence (from BLE), and GPS data
 * into a unified SensorSnapshot stream. Implements staleness detection
 * and heart rate zone classification.
 */
interface SensorHub {
    val sensorSnapshot: StateFlow<SensorSnapshot>
    val heartRateZone: StateFlow<HeartRateZone?>

    fun start()
    fun stop()
}
