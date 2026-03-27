package com.cyclecomp.app.domain.calc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class CalorieAndTssCalculatorImpl @Inject constructor() : CalorieAndTssCalculator {

    private var weightKg = 75.0
    private var age = 30
    private var ftpW = 200

    private var lastUpdateSec = 0.0

    private val _caloriesBurned = MutableStateFlow(0.0)
    override val caloriesBurned: StateFlow<Double> = _caloriesBurned.asStateFlow()

    private val _tss = MutableStateFlow(0.0)
    override val tss: StateFlow<Double> = _tss.asStateFlow()

    override fun update(heartRateBpm: Int?, normalizedPowerW: Double, durationSec: Double) {
        // Calculate calories using Keytel formula if HR is available
        if (heartRateBpm != null && heartRateBpm > 0 && durationSec > lastUpdateSec) {
            val deltaSec = durationSec - lastUpdateSec
            val deltaMin = deltaSec / 60.0
            val kcalPerMin = (-55.0969 + 0.6309 * heartRateBpm + 0.1988 * weightKg + 0.2017 * age) / 4.184
            val deltaKcal = max(0.0, kcalPerMin * deltaMin)
            _caloriesBurned.value += deltaKcal
        }
        lastUpdateSec = durationSec

        // Calculate TSS: (duration_sec * NP * IF) / (FTP * 3600) * 100
        if (ftpW > 0 && normalizedPowerW > 0 && durationSec > 0) {
            val intensityFactor = normalizedPowerW / ftpW
            val tssValue = (durationSec * normalizedPowerW * intensityFactor) / (ftpW * 3600.0) * 100.0
            _tss.value = max(0.0, tssValue)
        }
    }

    override fun updateProfile(weightKg: Double, age: Int, ftpW: Int) {
        this.weightKg = weightKg
        this.age = age
        this.ftpW = if (ftpW > 0) ftpW else 200
    }

    override fun reset() {
        _caloriesBurned.value = 0.0
        _tss.value = 0.0
        lastUpdateSec = 0.0
    }
}
