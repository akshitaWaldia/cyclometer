package com.cyclecomp.app.domain.sensor

import kotlinx.coroutines.flow.StateFlow

/**
 * Estimates cycling power using a physics-based model.
 * P_total = P_gravity + P_rolling + P_aero + P_accel
 */
interface PowerEstimator {
    val currentPowerW: StateFlow<Double>
    val averagePowerW: StateFlow<Double>
    val normalizedPowerW: StateFlow<Double>

    fun update(speedMps: Double, gradientPercent: Double, headwindMps: Double = 0.0)
    fun updateProfile(riderWeightKg: Double, bikeWeightKg: Double)
    fun reset()
}
