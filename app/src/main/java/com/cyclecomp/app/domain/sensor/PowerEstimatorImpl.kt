package com.cyclecomp.app.domain.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class PowerEstimatorImpl @Inject constructor() : PowerEstimator {

    companion object {
        const val G = 9.8067          // gravitational acceleration m/s²
        const val CRR = 0.005         // rolling resistance coefficient
        const val CDA = 0.4           // drag area m²
        const val RHO = 1.225         // air density kg/m³
        const val NP_WINDOW_SEC = 30  // normalized power rolling window
    }

    private var riderWeightKg = 75.0
    private var bikeWeightKg = 9.0
    private val totalMass: Double get() = riderWeightKg + bikeWeightKg

    private var previousSpeedMps: Double? = null
    private var previousTimestampMs: Long? = null

    // Running average power
    private var powerSampleCount = 0L
    private var powerSampleSum = 0.0

    // Normalized power: 30s rolling average → 4th power → mean → 4th root
    private val rollingPowerSamples = mutableListOf<Double>() // ~1 sample/sec
    private val rollingAvg30s = mutableListOf<Double>()       // 30s rolling averages
    private var fourthPowerSum = 0.0
    private var fourthPowerCount = 0L

    private val _currentPowerW = MutableStateFlow(0.0)
    override val currentPowerW: StateFlow<Double> = _currentPowerW.asStateFlow()

    private val _averagePowerW = MutableStateFlow(0.0)
    override val averagePowerW: StateFlow<Double> = _averagePowerW.asStateFlow()

    private val _normalizedPowerW = MutableStateFlow(0.0)
    override val normalizedPowerW: StateFlow<Double> = _normalizedPowerW.asStateFlow()

    override fun update(speedMps: Double, gradientPercent: Double, headwindMps: Double) {
        val now = System.currentTimeMillis()

        if (speedMps <= 0.0) {
            _currentPowerW.value = 0.0
            previousSpeedMps = 0.0
            previousTimestampMs = now
            addPowerSample(0.0)
            return
        }

        val slopeRad = atan(gradientPercent / 100.0)

        // P_gravity
        val pGravity = totalMass * G * sin(slopeRad) * speedMps

        // P_rolling
        val pRolling = CRR * totalMass * G * cos(slopeRad) * speedMps

        // P_aero
        val effectiveSpeed = speedMps + headwindMps
        val pAero = 0.5 * CDA * RHO * effectiveSpeed * effectiveSpeed * speedMps

        // P_accel
        var pAccel = 0.0
        val prevSpeed = previousSpeedMps
        val prevTime = previousTimestampMs
        if (prevSpeed != null && prevTime != null) {
            val dtSec = (now - prevTime) / 1000.0
            if (dtSec > 0.0) {
                val acceleration = (speedMps - prevSpeed) / dtSec
                pAccel = totalMass * acceleration * speedMps
            }
        }

        previousSpeedMps = speedMps
        previousTimestampMs = now

        val totalPower = max(0.0, pGravity + pRolling + pAero + pAccel)
        _currentPowerW.value = totalPower

        addPowerSample(totalPower)
    }

    override fun updateProfile(riderWeightKg: Double, bikeWeightKg: Double) {
        this.riderWeightKg = riderWeightKg
        this.bikeWeightKg = bikeWeightKg
    }

    override fun reset() {
        _currentPowerW.value = 0.0
        _averagePowerW.value = 0.0
        _normalizedPowerW.value = 0.0
        previousSpeedMps = null
        previousTimestampMs = null
        powerSampleCount = 0L
        powerSampleSum = 0.0
        rollingPowerSamples.clear()
        rollingAvg30s.clear()
        fourthPowerSum = 0.0
        fourthPowerCount = 0L
    }

    private fun addPowerSample(power: Double) {
        // Update running average
        powerSampleCount++
        powerSampleSum += power
        _averagePowerW.value = powerSampleSum / powerSampleCount

        // Update normalized power
        rollingPowerSamples.add(power)

        // Keep a rolling window of NP_WINDOW_SEC samples (assuming ~1 sample/sec)
        if (rollingPowerSamples.size >= NP_WINDOW_SEC) {
            val windowAvg = rollingPowerSamples.takeLast(NP_WINDOW_SEC).average()
            fourthPowerSum += windowAvg.pow(4.0)
            fourthPowerCount++
            _normalizedPowerW.value = (fourthPowerSum / fourthPowerCount).pow(0.25)
        }

        // Trim old samples to avoid unbounded memory growth
        if (rollingPowerSamples.size > NP_WINDOW_SEC * 2) {
            rollingPowerSamples.removeAt(0)
        }
    }
}
