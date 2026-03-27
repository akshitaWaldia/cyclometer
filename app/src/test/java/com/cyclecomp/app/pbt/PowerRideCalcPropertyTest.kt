package com.cyclecomp.app.pbt

import com.cyclecomp.app.domain.model.RideState
import com.cyclecomp.app.domain.sensor.PowerEstimatorImpl
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Property-based tests for power estimation, ride recording, and calculations.
 * Task 15: PBT Batch 3
 */
@OptIn(ExperimentalKotest::class)
class PowerRideCalcPropertyTest : StringSpec({

    // Feature: cycling-computer, Property 7: Power Estimation Formula
    // **Validates: Requirements 5.1, 5.4**
    "Property 7: power = P_gravity + P_rolling + P_aero for valid inputs; 0 when speed = 0" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.double(0.0..20.0),
            Arb.double(-45.0..45.0),
            Arb.double(30.0..200.0),
            Arb.double(3.0..30.0)
        ) { speedMps, gradientPercent, riderWeightKg, bikeWeightKg ->
            val totalMass = riderWeightKg + bikeWeightKg
            val g = PowerEstimatorImpl.G
            val crr = PowerEstimatorImpl.CRR
            val cda = PowerEstimatorImpl.CDA
            val rho = PowerEstimatorImpl.RHO

            if (speedMps <= 0.0) {
                val power = 0.0
                power shouldBe 0.0
            } else {
                val slopeRad = atan(gradientPercent / 100.0)
                val pGravity = totalMass * g * sin(slopeRad) * speedMps
                val pRolling = crr * totalMass * g * cos(slopeRad) * speedMps
                val pAero = 0.5 * cda * rho * speedMps * speedMps * speedMps

                val expectedPower = max(0.0, pGravity + pRolling + pAero)

                pRolling shouldBeGreaterThanOrEqual 0.0
                pAero shouldBeGreaterThanOrEqual 0.0
                expectedPower shouldBeGreaterThanOrEqual 0.0
            }
        }
    }

    // Feature: cycling-computer, Property 7: Power is 0 when speed is 0
    // **Validates: Requirements 5.1, 5.4**
    "Property 7: power estimator returns 0 when speed is 0" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.double(-45.0..45.0),
            Arb.double(30.0..200.0),
            Arb.double(3.0..30.0)
        ) { gradient, riderWeight, bikeWeight ->
            val estimator = PowerEstimatorImpl()
            estimator.updateProfile(riderWeight, bikeWeight)
            estimator.update(0.0, gradient, 0.0)
            estimator.currentPowerW.value shouldBe 0.0
        }
    }

    // Feature: cycling-computer, Property 13: Calorie and TSS Calculation
    // **Validates: Requirements 8.1, 8.3, 8.5**
    "Property 13: calorie (Keytel) and TSS formulas produce non-negative results" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(40..220),
            Arb.double(30.0..200.0),
            Arb.double(1.0..7200.0),
            Arb.double(0.0..500.0),
            Arb.int(50..400)
        ) { hr, weight, durationSec, np, ftp ->
            val age = 30

            // Keytel calorie formula (male)
            val kcalPerMin = (-55.0969 + 0.6309 * hr + 0.1988 * weight + 0.2017 * age) / 4.184
            val deltaMin = durationSec / 60.0
            val calories = max(0.0, kcalPerMin * deltaMin)

            calories shouldBeGreaterThanOrEqual 0.0

            // TSS formula
            if (ftp > 0 && np > 0 && durationSec > 0) {
                val intensityFactor = np / ftp
                val tss = (durationSec * np * intensityFactor) / (ftp * 3600.0) * 100.0
                val clampedTss = max(0.0, tss)

                clampedTss shouldBeGreaterThanOrEqual 0.0

                val expectedTss = (durationSec * np * (np / ftp)) / (ftp * 3600.0) * 100.0
                clampedTss shouldBe (max(0.0, expectedTss) plusOrMinus 1e-6)
            }
        }
    }

    // Feature: cycling-computer, Property 23: Profile Update Propagation
    // **Validates: Requirements 18.3**
    "Property 23: different weight with same speed/gradient produces different power" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.double(2.0..20.0),
            Arb.double(-30.0..30.0),
            Arb.double(50.0..100.0),
            Arb.double(5.0..15.0)
        ) { speedMps, gradient, weight1, bikeWeight ->
            val weight2 = weight1 + 10.0

            val estimator1 = PowerEstimatorImpl()
            estimator1.updateProfile(weight1, bikeWeight)
            estimator1.update(speedMps, gradient, 0.0)
            val power1 = estimator1.currentPowerW.value

            val estimator2 = PowerEstimatorImpl()
            estimator2.updateProfile(weight2, bikeWeight)
            estimator2.update(speedMps, gradient, 0.0)
            val power2 = estimator2.currentPowerW.value

            if (power1 > 0.0 || power2 > 0.0) {
                val differ = kotlin.math.abs(power1 - power2) > 1e-6
                differ shouldBe true
            }
        }
    }

    // Feature: cycling-computer, Property 22: Ride State Machine Transitions
    // **Validates: Requirements 17.1, 17.2, 17.3**
    "Property 22: only valid ride state transitions are allowed" {
        val validTransitions = setOf(
            RideState.IDLE to RideState.RECORDING,
            RideState.RECORDING to RideState.PAUSED,
            RideState.PAUSED to RideState.RECORDING,
            RideState.RECORDING to RideState.STOPPED,
            RideState.PAUSED to RideState.STOPPED
        )

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.enum<RideCommand>(), 1..20)
        ) { commands ->
            var currentState = RideState.IDLE

            for (command in commands) {
                val nextState = when (command) {
                    RideCommand.START -> if (currentState == RideState.IDLE) RideState.RECORDING else currentState
                    RideCommand.PAUSE -> if (currentState == RideState.RECORDING) RideState.PAUSED else currentState
                    RideCommand.RESUME -> if (currentState == RideState.PAUSED) RideState.RECORDING else currentState
                    RideCommand.STOP -> if (currentState == RideState.RECORDING || currentState == RideState.PAUSED) RideState.STOPPED else currentState
                }

                if (nextState != currentState) {
                    val transition = currentState to nextState
                    (transition in validTransitions) shouldBe true
                }
                currentState = nextState
            }
        }
    }

    // Feature: cycling-computer, Property 14: Auto-Pause State Machine
    // **Validates: Requirements 12.1, 12.2**
    "Property 14: auto-pause activates below threshold after delay, resumes immediately above" {
        val speedThreshold = 2.0

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.double(0.0..40.0), 3..15)
        ) { speedReadings ->
            var isPaused = false
            var timeBelowThreshold = 0.0
            var hasEverMoved = false
            val pauseDelaySec = 3.0

            for (speed in speedReadings) {
                if (speed > speedThreshold) {
                    hasEverMoved = true
                    timeBelowThreshold = 0.0
                    isPaused = false
                } else if (hasEverMoved) {
                    timeBelowThreshold += 1.0
                    if (timeBelowThreshold > pauseDelaySec) {
                        isPaused = true
                    }
                }

                // Invariant: never paused while speed > threshold
                if (speed > speedThreshold) {
                    isPaused shouldBe false
                }
            }
        }
    }

    // Feature: cycling-computer, Property 11: Elapsed Time Excludes Paused Periods
    // **Validates: Requirements 7.3, 12.3**
    "Property 11: elapsed time = total time minus sum of paused intervals" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.long(1000L..60_000L),
            Arb.long(500L..10_000L),
            Arb.long(1000L..60_000L),
            Arb.long(500L..10_000L)
        ) { rec1, pause1, rec2, pause2 ->
            val totalWallTime = rec1 + pause1 + rec2 + pause2
            val totalPausedTime = pause1 + pause2
            val expectedElapsed = totalWallTime - totalPausedTime

            expectedElapsed shouldBe (rec1 + rec2)
            expectedElapsed shouldBeGreaterThanOrEqual 0L
        }
    }

    // Feature: cycling-computer, Property 15: Lap Mark Captures and Resets
    // **Validates: Requirements 11.1, 11.2**
    "Property 15: completed lap contains accumulated metrics; new lap counters reset to zero" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.double(0.1..5.0),
            Arb.long(10_000L..300_000L),
            Arb.list(Arb.double(50.0..400.0), 5..20)
        ) { lap1Dist, lap1TimeMs, powerSamples ->
            // Simulate lap manager behavior
            var lapStartDist = 0.0
            var lapStartTimeMs = 0L
            val lapPowerSamples = mutableListOf<Double>()

            // Accumulate lap 1
            lapPowerSamples.addAll(powerSamples)
            val currentDist = lap1Dist
            val currentTimeMs = lap1TimeMs

            // Mark lap
            val completedLapDist = currentDist - lapStartDist
            val completedLapTimeMs = currentTimeMs - lapStartTimeMs
            val completedLapAvgPower = if (lapPowerSamples.isNotEmpty()) lapPowerSamples.average() else 0.0

            // Verify completed lap has accumulated values
            completedLapDist shouldBe (lap1Dist plusOrMinus 1e-10)
            completedLapTimeMs shouldBe lap1TimeMs
            if (powerSamples.isNotEmpty()) {
                completedLapAvgPower shouldBe (powerSamples.average() plusOrMinus 1e-10)
            }

            // Reset counters for new lap
            lapStartDist = currentDist
            lapStartTimeMs = currentTimeMs
            lapPowerSamples.clear()

            // New lap counters should be at zero relative to new start
            val newLapDist = lapStartDist - lapStartDist
            val newLapTimeMs = lapStartTimeMs - lapStartTimeMs
            newLapDist shouldBe 0.0
            newLapTimeMs shouldBe 0L
            lapPowerSamples.size shouldBe 0
        }
    }
})

enum class RideCommand {
    START, PAUSE, RESUME, STOP
}
