package com.cyclecomp.app.data.export

import com.cyclecomp.app.domain.model.LapData
import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.TrackPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class FitExporterTest : FunSpec({

    val exporter = FitExporter()

    fun sampleRide(trackPointCount: Int = 3, laps: List<LapData> = emptyList()): RideData {
        val start = Instant.parse("2024-06-15T10:00:00Z")
        val trackPoints = (0 until trackPointCount).map { i ->
            TrackPoint(
                timestamp = start.plusSeconds(i.toLong()),
                latitude = 37.4220 + i * 0.0001,
                longitude = -122.0841 + i * 0.0001,
                altitudeM = 10.0 + i,
                speedKmh = 25.0 + i,
                heartRateBpm = 140 + i,
                cadenceRpm = 85 + i,
                powerW = 200.0 + i * 10,
                gradientPercent = 2.0,
                cumulativeDistanceKm = i * 0.01
            )
        }
        return RideData(
            id = "test-ride-1",
            startTime = start,
            endTime = start.plusSeconds(trackPointCount.toLong()),
            elapsedDuration = Duration.ofSeconds(trackPointCount.toLong()),
            totalDistanceKm = trackPointCount * 0.01,
            totalElevationGainM = trackPointCount.toDouble(),
            averageSpeedKmh = 25.0,
            averagePowerW = 200.0,
            normalizedPowerW = 210.0,
            maxSpeedKmh = 30.0,
            maxPowerW = 250.0,
            maxHeartRateBpm = 160,
            averageHeartRateBpm = 145,
            averageCadenceRpm = 87,
            caloriesKcal = 150.0,
            tss = 45.0,
            trackPoints = trackPoints,
            laps = laps,
            riderProfile = RiderProfile()
        )
    }

    test("serialize produces valid FIT file header") {
        val ride = sampleRide()
        val bytes = exporter.serialize(ride)

        // FIT file header is 14 bytes
        bytes.size shouldBeGreaterThan 14

        // Header size byte
        bytes[0] shouldBe 14.toByte()

        // ".FIT" signature at bytes 8-11
        bytes[8] shouldBe '.'.code.toByte()
        bytes[9] shouldBe 'F'.code.toByte()
        bytes[10] shouldBe 'I'.code.toByte()
        bytes[11] shouldBe 'T'.code.toByte()
    }

    test("serialize produces non-empty output for ride with track points") {
        val ride = sampleRide(trackPointCount = 10)
        val bytes = exporter.serialize(ride)

        // Should have header (14) + data messages + CRC (2)
        bytes.size shouldBeGreaterThan 16
    }

    test("serialize handles ride with no track points") {
        val ride = sampleRide(trackPointCount = 0)
        val bytes = exporter.serialize(ride)

        // Should still produce valid output with FileId, Session, Activity, and a whole-ride Lap
        bytes.size shouldBeGreaterThan 14
    }

    test("serialize handles ride with laps") {
        val start = Instant.parse("2024-06-15T10:00:00Z")
        val laps = listOf(
            LapData(
                lapNumber = 1,
                startTime = start,
                endTime = start.plusSeconds(60),
                distanceKm = 0.5,
                averagePowerW = 200.0,
                elapsedTime = Duration.ofSeconds(60)
            ),
            LapData(
                lapNumber = 2,
                startTime = start.plusSeconds(60),
                endTime = start.plusSeconds(120),
                distanceKm = 0.6,
                averagePowerW = 220.0,
                elapsedTime = Duration.ofSeconds(60)
            )
        )
        val ride = sampleRide(trackPointCount = 5, laps = laps)
        val bytes = exporter.serialize(ride)

        // Should be larger than ride without laps
        val bytesNoLaps = exporter.serialize(sampleRide(trackPointCount = 5))
        bytes.size shouldBeGreaterThan bytesNoLaps.size
    }

    test("degreesToSemicircles converts correctly") {
        // 0 degrees should be 0
        FitExporter.degreesToSemicircles(0.0) shouldBe 0
        // 90 degrees should be ~1073741824
        val semi90 = FitExporter.degreesToSemicircles(90.0)
        (semi90 > 1_000_000_000) shouldBe true
        // 180 degrees wraps to negative due to int overflow (expected for semicircles)
        val semi180 = FitExporter.degreesToSemicircles(180.0)
        (semi180 != 0) shouldBe true
    }

    test("fitTimestamp converts correctly from FIT epoch") {
        // FIT epoch is Dec 31, 1989 00:00:00 UTC = 631065600 seconds before Unix epoch
        val fitEpochInstant = Instant.parse("1989-12-31T00:00:00Z")
        FitExporter.fitTimestamp(fitEpochInstant) shouldBe 0L

        // 1 hour after FIT epoch
        val oneHourLater = fitEpochInstant.plusSeconds(3600)
        FitExporter.fitTimestamp(oneHourLater) shouldBe 3600L
    }
})
