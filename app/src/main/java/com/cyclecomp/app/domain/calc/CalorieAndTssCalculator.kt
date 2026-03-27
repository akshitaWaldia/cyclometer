package com.cyclecomp.app.domain.calc

import kotlinx.coroutines.flow.StateFlow

/**
 * Calculates calories burned (Keytel formula) and Training Stress Score (TSS).
 */
interface CalorieAndTssCalculator {
    val caloriesBurned: StateFlow<Double>
    val tss: StateFlow<Double>

    fun update(heartRateBpm: Int?, normalizedPowerW: Double, durationSec: Double)
    fun updateProfile(weightKg: Double, age: Int, ftpW: Int)
    fun reset()
}
