package com.cyclecomp.app.domain.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Physics-based cycling power estimator with GPS noise filtering.
 *
 * Based on established cycling physics formulas from:
 * - gribble.org cycling power calculator
 * - Omnicalculator cycling wattage
 * - GPS Visualizer elevation filtering recommendations
 *
 * Power equation: P = (F_gravity + F_rolling + F_aero + F_accel) × v_ground / (1 - drivetrain_loss)
 *
 * Key improvements over V1:
 * - Exponential Moving Average (EMA) for speed smoothing
 * - Elevation threshold filtering (ignores changes < threshold)
 * - Minimum time delta for acceleration calculation
 * - Realistic bounds on all inputs and outputs
 * - Altitude-adjusted air density
 * - Rider position presets for CdA
 */
@Singleton
class PowerEstimatorV2 @Inject constructor() : PowerEstimator {

    // ==================== PHYSICS CONSTANTS ====================
    companion object {
        // Gravitational acceleration (m/s²)
        const val G = 9.8067

        // Air density at sea level, 15°C (kg/m³)
        const val RHO_SEA_LEVEL = 1.225

        // Drivetrain efficiency loss (typical 2-4%)
        const val DRIVETRAIN_LOSS = 0.025

        // Rolling resistance coefficients by surface
        const val CRR_ROAD_SLICK = 0.004    // Road bike, slick tires
        const val CRR_ROAD_NORMAL = 0.005   // Road bike, standard tires
        const val CRR_GRAVEL = 0.008        // Gravel/mixed surface
        const val CRR_MTB = 0.012           // Mountain bike, knobby tires

        // CdA values by rider position (from research)
        const val CDA_AEROBARS = 0.29       // Triathlon/TT position
        const val CDA_DROPS = 0.31          // Road bike, drops
        const val CDA_HOODS = 0.32          // Road bike, hoods
        const val CDA_TOPS = 0.41           // Upright position

        // ==================== FILTER CONSTANTS ====================

        // Speed EMA smoothing factor (0.0 = no change, 1.0 = no smoothing)
        // 0.3 provides good balance: responsive but filters GPS jitter
        const val SPEED_EMA_ALPHA = 0.3

        // Minimum time delta for acceleration calculation (seconds)
        // Below this, acceleration is unreliable due to GPS timing jitter
        const val MIN_ACCEL_DT_SEC = 0.8

        // Maximum time delta for acceleration (seconds)
        // Above this, acceleration is stale/discontinuous
        const val MAX_ACCEL_DT_SEC = 2.0

        // Maximum realistic cycling acceleration (m/s²)
        // Sprint from standstill: ~2-3 m/s², sustained: ~1 m/s²
        const val MAX_ACCELERATION = 2.0

        // Elevation change threshold (meters)
        // Changes below this are likely GPS noise
        const val ELEVATION_THRESHOLD_M = 2.0

        // Maximum realistic gradient (%)
        // Above this is likely GPS error (30% is extremely steep)
        const val MAX_GRADIENT_PERCENT = 30.0

        // Maximum realistic power output (watts)
        // World-class sprinters: ~2400W, recreational: rarely >1000W
        const val MAX_POWER_W = 2000.0

        // Minimum speed for power calculation (m/s)
        // Below this, assume stopped (0.5 m/s ≈ 1.8 km/h)
        const val MIN_SPEED_MPS = 0.5

        // Normalized power window (seconds)
        const val NP_WINDOW_SEC = 30
    }

    // ==================== RIDER PROFILE ====================
    private var riderWeightKg = 75.0
    private var bikeWeightKg = 9.0
    private var cdA = CDA_HOODS
    private var crr = CRR_ROAD_NORMAL
    private var altitudeM = 0.0  // For air density adjustment

    private val totalMass: Double get() = riderWeightKg + bikeWeightKg

    // ==================== SMOOTHING STATE ====================
    private var smoothedSpeedMps = 0.0
    private var smoothedGradient = 0.0
    private var lastValidAltitudeM: Double? = null

    // ==================== TIMING STATE ====================
    private var previousSpeedMps: Double? = null
    private var previousTimestampMs: Long? = null

    // ==================== POWER AVERAGING ====================
    private var powerSampleCount = 0L
    private var powerSampleSum = 0.0

    // Normalized power: 30s rolling window of powers
    private val powerWindow = ArrayDeque<Double>(NP_WINDOW_SEC + 10)
    private var npSum = 0.0
    private var npCount = 0L

    // ==================== OUTPUT FLOWS ====================
    private val _currentPowerW = MutableStateFlow(0.0)
    override val currentPowerW: StateFlow<Double> = _currentPowerW.asStateFlow()

    private val _averagePowerW = MutableStateFlow(0.0)
    override val averagePowerW: StateFlow<Double> = _averagePowerW.asStateFlow()

    private val _normalizedPowerW = MutableStateFlow(0.0)
    override val normalizedPowerW: StateFlow<Double> = _normalizedPowerW.asStateFlow()

    // ==================== MAIN UPDATE FUNCTION ====================

    /**
     * Update power estimate with new sensor data.
     *
     * @param speedMps Raw GPS ground speed in m/s
     * @param gradientPercent Gradient from GPS elevation (may be noisy)
     * @param headwindMps Headwind speed (positive = against rider)
     */
    override fun update(speedMps: Double, gradientPercent: Double, headwindMps: Double) {
        val now = System.currentTimeMillis()

        // Handle stopped/very slow
        if (speedMps < MIN_SPEED_MPS) {
            _currentPowerW.value = 0.0
            previousSpeedMps = 0.0
            previousTimestampMs = now
            addPowerSample(0.0)
            return
        }

        // ========== STEP 1: Smooth speed with EMA ==========
        smoothedSpeedMps = if (smoothedSpeedMps == 0.0) {
            speedMps  // First reading, no smoothing
        } else {
            SPEED_EMA_ALPHA * speedMps + (1 - SPEED_EMA_ALPHA) * smoothedSpeedMps
        }

        // ========== STEP 2: Filter and clamp gradient ==========
        // Only update gradient if change exceeds threshold (reduces noise)
        val gradientDelta = abs(gradientPercent - smoothedGradient)
        if (gradientDelta > 0.5) {  // Hysteresis: only change if significant
            // Apply EMA to gradient as well
            smoothedGradient = 0.4 * gradientPercent + 0.6 * smoothedGradient
        }
        // Clamp to realistic range
        val clampedGradient = smoothedGradient.coerceIn(-MAX_GRADIENT_PERCENT, MAX_GRADIENT_PERCENT)

        // ========== STEP 3: Calculate air density (altitude adjusted) ==========
        val rho = calculateAirDensity(altitudeM)

        // ========== STEP 4: Calculate force components ==========
        val slopeRad = atan(clampedGradient / 100.0)

        // F_gravity: Force to climb/descend (negative when descending)
        val fGravity = totalMass * G * sin(slopeRad)

        // F_rolling: Rolling resistance force
        val fRolling = crr * totalMass * G * cos(slopeRad)

        // F_aero: Aerodynamic drag force
        // Air speed = ground speed + headwind (positive headwind = harder)
        val airSpeedMps = (smoothedSpeedMps + headwindMps).coerceAtLeast(0.0)
        val fAero = 0.5 * cdA * rho * airSpeedMps * airSpeedMps

        // F_accel: Force for acceleration (only if timing is reliable)
        val fAccel = calculateAccelerationForce(smoothedSpeedMps, now)

        // ========== STEP 5: Calculate power ==========
        // Power = Force × Velocity / (1 - drivetrain_loss)
        val totalForce = fGravity + fRolling + fAero + fAccel
        val rawPower = totalForce * smoothedSpeedMps / (1.0 - DRIVETRAIN_LOSS)

        // Clamp to realistic range (can be negative when descending fast)
        val finalPower = rawPower.coerceIn(0.0, MAX_POWER_W)

        _currentPowerW.value = finalPower
        previousSpeedMps = smoothedSpeedMps
        previousTimestampMs = now

        addPowerSample(finalPower)
    }

    /**
     * Calculate acceleration force with timing validation.
     * Returns 0 if timing is unreliable.
     */
    private fun calculateAccelerationForce(currentSpeed: Double, nowMs: Long): Double {
        val prevSpeed = previousSpeedMps ?: return 0.0
        val prevTime = previousTimestampMs ?: return 0.0

        val dtSec = (nowMs - prevTime) / 1000.0

        // Only calculate if time delta is in reliable range
        if (dtSec < MIN_ACCEL_DT_SEC || dtSec > MAX_ACCEL_DT_SEC) {
            return 0.0
        }

        // Calculate and clamp acceleration
        val rawAccel = (currentSpeed - prevSpeed) / dtSec
        val clampedAccel = rawAccel.coerceIn(-MAX_ACCELERATION, MAX_ACCELERATION)

        return totalMass * clampedAccel
    }

    /**
     * Calculate air density adjusted for altitude.
     * Uses barometric formula approximation.
     */
    private fun calculateAirDensity(altitudeM: Double): Double {
        // Simplified barometric formula:
        // ρ = ρ₀ × (1 - 0.0000225577 × h)^5.25588
        // At 1000m: ~1.11 kg/m³, at 2000m: ~1.01 kg/m³
        val factor = (1.0 - 0.0000225577 * altitudeM).pow(5.25588)
        return RHO_SEA_LEVEL * factor.coerceIn(0.5, 1.2)
    }

    // ==================== POWER AVERAGING ====================

    private fun addPowerSample(power: Double) {
        // Running average
        powerSampleCount++
        powerSampleSum += power
        _averagePowerW.value = powerSampleSum / powerSampleCount

        // Normalized power calculation
        // NP = 4th root of mean of 30-second rolling average powers raised to 4th
        powerWindow.addLast(power)

        if (powerWindow.size >= NP_WINDOW_SEC) {
            // Calculate 30-second rolling average
            val windowAvg = powerWindow.average()

            // Add to 4th power sum
            npSum += windowAvg.pow(4.0)
            npCount++

            // Calculate NP
            _normalizedPowerW.value = (npSum / npCount).pow(0.25)

            // Remove oldest sample to maintain window size
            powerWindow.removeFirst()
        }
    }

    // ==================== CONFIGURATION ====================

    override fun updateProfile(riderWeightKg: Double, bikeWeightKg: Double) {
        this.riderWeightKg = riderWeightKg.coerceIn(30.0, 200.0)
        this.bikeWeightKg = bikeWeightKg.coerceIn(3.0, 30.0)
    }

    /**
     * Set rider position for aerodynamic calculation.
     */
    fun setRiderPosition(position: RiderPosition) {
        cdA = when (position) {
            RiderPosition.AEROBARS -> CDA_AEROBARS
            RiderPosition.DROPS -> CDA_DROPS
            RiderPosition.HOODS -> CDA_HOODS
            RiderPosition.TOPS -> CDA_TOPS
        }
    }

    /**
     * Set surface type for rolling resistance.
     */
    fun setSurfaceType(surface: SurfaceType) {
        crr = when (surface) {
            SurfaceType.ROAD_SLICK -> CRR_ROAD_SLICK
            SurfaceType.ROAD_NORMAL -> CRR_ROAD_NORMAL
            SurfaceType.GRAVEL -> CRR_GRAVEL
            SurfaceType.MTB -> CRR_MTB
        }
    }

    /**
     * Update current altitude for air density calculation.
     */
    fun updateAltitude(altitudeM: Double) {
        // Apply elevation threshold filter
        val lastAlt = lastValidAltitudeM
        if (lastAlt == null || abs(altitudeM - lastAlt) >= ELEVATION_THRESHOLD_M) {
            lastValidAltitudeM = altitudeM
            this.altitudeM = altitudeM
        }
    }

    /**
     * Allow setting custom CdA (for advanced users who know their value).
     */
    fun setCustomCdA(cdA: Double) {
        this.cdA = cdA.coerceIn(0.2, 0.6)
    }

    /**
     * Allow setting custom Crr (for advanced users).
     */
    fun setCustomCrr(crr: Double) {
        this.crr = crr.coerceIn(0.002, 0.02)
    }

    override fun reset() {
        _currentPowerW.value = 0.0
        _averagePowerW.value = 0.0
        _normalizedPowerW.value = 0.0

        smoothedSpeedMps = 0.0
        smoothedGradient = 0.0
        lastValidAltitudeM = null

        previousSpeedMps = null
        previousTimestampMs = null

        powerSampleCount = 0L
        powerSampleSum = 0.0

        powerWindow.clear()
        npSum = 0.0
        npCount = 0L
    }

    // ==================== ENUMS ====================

    enum class RiderPosition {
        AEROBARS,   // TT/Triathlon position
        DROPS,      // Racing position, hands in drops
        HOODS,      // Normal riding, hands on hoods
        TOPS        // Upright, relaxed position
    }

    enum class SurfaceType {
        ROAD_SLICK,   // Smooth tires on smooth road
        ROAD_NORMAL,  // Standard road tires
        GRAVEL,       // Gravel or rough roads
        MTB           // Mountain bike, off-road
    }
}
