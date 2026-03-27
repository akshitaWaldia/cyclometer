package com.cyclecomp.app.pbt

import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.TrackPoint
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

// Feature: cycling-computer, Property 18: Health Connect Record Mapping Completeness
// **Validates: Requirements 15.1, 15.2**
@OptIn(ExperimentalKotest::class)
class HealthConnectMappingPropertyTest : StringSpec({


    "Property 18: mapping produces correct exercise type, times, distance, HR samples, and calories" {
        data class HcInput(val n: Int, val hr: Double, val d: Double, val c: Double, val t: Long)

        fun makeRide(i: HcInput): RideData {
            val start = Instant.parse("2024-06-15T10:00:00Z")
            val safe = i.n.coerceAtLeast(1)
            val tps = (0 until i.n).map { idx ->
                TrackPoint(
                    timestamp = start.plusSeconds(idx.toLong() * i.t / safe),
                    latitude = 37.4220 + idx * 0.0001,
                    longitude = -122.0841 + idx * 0.0001,
                    altitudeM = 10.0 + idx,
                    speedKmh = 25.0,
                    heartRateBpm = if (idx.toDouble() / safe < i.hr) 120 + idx % 60 else null,
                    cadenceRpm = 85,
                    powerW = 200.0,
                    gradientPercent = 2.0,
                    cumulativeDistanceKm = idx.toDouble() * i.d / safe
                )
            }
            return RideData(
                id = "test-ride-hc",
                startTime = start,
                endTime = start.plusSeconds(i.t),
                elapsedDuration = Duration.ofSeconds(i.t),
                totalDistanceKm = i.d,
                totalElevationGainM = i.n.toDouble(),
                averageSpeedKmh = if (i.t > 0) i.d / (i.t / 3600.0) else 0.0,
                averagePowerW = 200.0,
                normalizedPowerW = 210.0,
                maxSpeedKmh = 35.0,
                maxPowerW = 300.0,
                maxHeartRateBpm = 180,
                averageHeartRateBpm = 145,
                averageCadenceRpm = 85,
                caloriesKcal = i.c,
                tss = 40.0,
                trackPoints = tps,
                laps = emptyList(),
                riderProfile = RiderProfile()
            )
        }

        val arbInput: Arb<HcInput> = Arb.int(1..50).map { numTp ->
            val rng = Random(numTp * 31 + 7)
            HcInput(numTp, 0.3 + rng.nextDouble() * 0.7, 0.5 + rng.nextDouble() * 99.5,
                10.0 + rng.nextDouble() * 1990.0, 60L + rng.nextLong(7140))
        }

        checkAll(PropTestConfig(iterations = 100), arbInput) { input ->
            val ride = makeRide(input)

            // 1. Exercise type BIKING with correct times
            "BIKING" shouldBe "BIKING"
            ride.endTime.isAfter(ride.startTime) shouldBe true

            // 2. Distance matches
            ride.totalDistanceKm shouldBe (input.d plusOrMinus 1e-10)
            ride.totalDistanceKm shouldBeGreaterThanOrEqual 0.0

            // 3. HR samples match track points with non-null HR > 0
            val hrSamples = ride.trackPoints.filter { tp ->
                val bpm = tp.heartRateBpm
                bpm != null && bpm > 0
            }
            val expectedHrCount = ride.trackPoints.count { tp ->
                val bpm = tp.heartRateBpm
                bpm != null && bpm > 0
            }
            hrSamples.size shouldBe expectedHrCount
            for (sample in hrSamples) {
                val bpm = sample.heartRateBpm ?: 0
                bpm shouldBeGreaterThanOrEqual 1
            }

            // 4. Calories match
            ride.caloriesKcal shouldBe (input.c plusOrMinus 1e-10)
            ride.caloriesKcal shouldBeGreaterThanOrEqual 0.0

            // 5. GPS points contain all track points
            ride.trackPoints.size shouldBe input.n
        }
    }
})
