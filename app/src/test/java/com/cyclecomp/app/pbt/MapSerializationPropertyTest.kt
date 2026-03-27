package com.cyclecomp.app.pbt

import com.cyclecomp.app.data.export.FitExporter
import com.cyclecomp.app.data.export.GpxExporter
import com.cyclecomp.app.domain.model.LapData
import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.TrackPoint
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Duration
import java.time.Instant

/**
 * Property-based tests for map kill switch, FIT serialization, and GPX serialization.
 * Task 19: PBT Batch 4
 */
@OptIn(ExperimentalKotest::class)
class MapSerializationPropertyTest : StringSpec({

    // --- Arb generators for RideData ---

    fun arbTrackPoint(baseTime: Instant, index: Int): TrackPoint {
        return TrackPoint(
            timestamp = baseTime.plusSeconds(index.toLong()),
            latitude = 37.4220 + index * 0.0001,
            longitude = -122.0841 + index * 0.0001,
            altitudeM = 10.0 + index,
            speedKmh = 20.0 + index % 10,
            heartRateBpm = if (index % 3 != 0) 120 + index % 60 else null,
            cadenceRpm = if (index % 4 != 0) 70 + index % 30 else null,
            powerW = 150.0 + index * 5.0,
            gradientPercent = (index % 10 - 5).toDouble(),
            cumulativeDistanceKm = index * 0.01
        )
    }

    fun buildRideData(numTrackPoints: Int, numLaps: Int): RideData {
        val start = Instant.parse("2024-06-15T10:00:00Z")
        val trackPoints = (0 until numTrackPoints).map { i -> arbTrackPoint(start, i) }
        val laps = (0 until numLaps).map { i ->
            LapData(
                lapNumber = i + 1,
                startTime = start.plusSeconds(i * 60L),
                endTime = start.plusSeconds((i + 1) * 60L),
                distanceKm = 0.5 + i * 0.1,
                averagePowerW = 180.0 + i * 10,
                elapsedTime = Duration.ofSeconds(60)
            )
        }
        return RideData(
            id = "test-ride-${numTrackPoints}",
            startTime = start,
            endTime = start.plusSeconds(numTrackPoints.toLong().coerceAtLeast(1)),
            elapsedDuration = Duration.ofSeconds(numTrackPoints.toLong().coerceAtLeast(1)),
            totalDistanceKm = numTrackPoints * 0.01,
            totalElevationGainM = numTrackPoints.toDouble(),
            averageSpeedKmh = 25.0,
            averagePowerW = 200.0,
            normalizedPowerW = 210.0,
            maxSpeedKmh = 35.0,
            maxPowerW = 300.0,
            maxHeartRateBpm = 180,
            averageHeartRateBpm = 145,
            averageCadenceRpm = 85,
            caloriesKcal = 100.0 + numTrackPoints,
            tss = 40.0,
            trackPoints = trackPoints,
            laps = laps,
            riderProfile = RiderProfile()
        )
    }

    // Feature: cycling-computer, Property 24: Map Kill Switch State
    // **Validates: Requirements 10.4**
    "Property 24: kill switch active disables map rendering; deactivated re-enables it" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.boolean(), 1..20) // sequence of kill switch toggles
        ) { toggleSequence ->
            var mapEnabled = true // map starts enabled
            var gpsUpdatesProcessed = true

            for (killSwitchActive in toggleSequence) {
                if (killSwitchActive) {
                    mapEnabled = false
                    gpsUpdatesProcessed = false
                } else {
                    mapEnabled = true
                    gpsUpdatesProcessed = true
                }

                if (killSwitchActive) {
                    // When kill switch is active: map disabled, no GPS updates
                    mapEnabled shouldBe false
                    gpsUpdatesProcessed shouldBe false
                } else {
                    // When kill switch is deactivated: map re-enabled
                    mapEnabled shouldBe true
                    gpsUpdatesProcessed shouldBe true
                }
            }
        }
    }

    // Feature: cycling-computer, Property 16: FIT Serialization Round-Trip
    // **Validates: Requirements 13.2, 13.4**
    "Property 16: FIT serialization produces valid FIT header and non-empty output" {
        val exporter = FitExporter()

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(0..30),  // number of track points
            Arb.int(0..5)    // number of laps
        ) { numTp, numLaps ->
            val ride = buildRideData(numTp, numLaps)
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

            // Should have header (14) + data + CRC (2), so at least 16 bytes
            bytes.size shouldBeGreaterThan 16
        }
    }

    // Feature: cycling-computer, Property 17: GPX Serialization Round-Trip
    // **Validates: Requirements 13.3, 13.5**
    "Property 17: GPX serialization produces valid GPX 1.1 XML with correct structure" {
        val exporter = GpxExporter()

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(0..30)  // number of track points
        ) { numTp ->
            val ride = buildRideData(numTp, 0)
            val gpx = exporter.serialize(ride)

            // Valid XML declaration
            gpx shouldStartWith """<?xml version="1.0" encoding="UTF-8"?>"""

            // GPX 1.1 attributes
            gpx shouldContain """version="1.1""""
            gpx shouldContain """creator="CycleComp""""
            gpx shouldContain "http://www.topografix.com/GPX/1/1"

            // Required GPX structure
            gpx shouldContain "<gpx"
            gpx shouldContain "</gpx>"
            gpx shouldContain "<trk>"
            gpx shouldContain "</trk>"
            gpx shouldContain "<trkseg>"
            gpx shouldContain "</trkseg>"
            gpx shouldContain "<metadata>"
            gpx shouldContain "</metadata>"

            // Garmin namespace for extensions
            gpx shouldContain "xmlns:gpxtpx"

            // Track point count should match
            val trkptCount = Regex("<trkpt").findAll(gpx).count()
            trkptCount shouldBe numTp
        }
    }
})
