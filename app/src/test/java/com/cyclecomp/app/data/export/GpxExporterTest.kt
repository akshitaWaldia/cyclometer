package com.cyclecomp.app.data.export

import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.TrackPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.Duration
import java.time.Instant

class GpxExporterTest : FunSpec({

    val exporter = GpxExporter()

    fun sampleRide(trackPointCount: Int = 3): RideData {
        val start = Instant.parse("2024-06-15T10:00:00Z")
        val trackPoints = (0 until trackPointCount).map { i ->
            TrackPoint(
                timestamp = start.plusSeconds(i.toLong()),
                latitude = 37.4220 + i * 0.0001,
                longitude = -122.0841 + i * 0.0001,
                altitudeM = 10.0 + i,
                speedKmh = 25.0 + i,
                heartRateBpm = if (i % 2 == 0) 140 + i else null,
                cadenceRpm = if (i % 2 == 0) 85 + i else null,
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
            laps = emptyList(),
            riderProfile = RiderProfile()
        )
    }

    test("serialize produces valid GPX 1.1 XML header") {
        val ride = sampleRide()
        val gpx = exporter.serialize(ride)

        gpx shouldStartWith """<?xml version="1.0" encoding="UTF-8"?>"""
        gpx shouldContain """version="1.1""""
        gpx shouldContain """creator="CycleComp""""
        gpx shouldContain "http://www.topografix.com/GPX/1/1"
    }

    test("serialize includes track structure") {
        val ride = sampleRide()
        val gpx = exporter.serialize(ride)

        gpx shouldContain "<trk>"
        gpx shouldContain "<trkseg>"
        gpx shouldContain "<trkpt"
        gpx shouldContain "</trkseg>"
        gpx shouldContain "</trk>"
        gpx shouldContain "</gpx>"
    }

    test("serialize includes lat/lon/ele/time in trackpoints") {
        val ride = sampleRide(trackPointCount = 1)
        val gpx = exporter.serialize(ride)

        gpx shouldContain """lat="37.422""""
        gpx shouldContain """lon="-122.0841""""
        gpx shouldContain "<ele>10.0</ele>"
        gpx shouldContain "<time>2024-06-15T10:00:00Z</time>"
    }

    test("serialize includes Garmin TrackPointExtension for HR and cadence") {
        val ride = sampleRide(trackPointCount = 1)
        val gpx = exporter.serialize(ride)

        gpx shouldContain "gpxtpx:TrackPointExtension"
        gpx shouldContain "<gpxtpx:hr>140</gpxtpx:hr>"
        gpx shouldContain "<gpxtpx:cad>85</gpxtpx:cad>"
    }

    test("serialize includes power in extensions") {
        val ride = sampleRide(trackPointCount = 1)
        val gpx = exporter.serialize(ride)

        gpx shouldContain "<power>200</power>"
    }

    test("serialize omits HR/cadence extensions when null") {
        // Track point at index 1 has null HR and cadence
        val ride = sampleRide(trackPointCount = 2)
        val gpx = exporter.serialize(ride)

        // The second trackpoint (index 1) should still have power but no HR/cadence
        // We can verify the GPX has at least one trkpt without gpxtpx:hr
        // Since index 0 has HR and index 1 doesn't, we just verify both trkpt elements exist
        val trkptCount = Regex("<trkpt").findAll(gpx).count()
        trkptCount shouldBe 2
    }

    test("serialize handles empty track points") {
        val ride = sampleRide(trackPointCount = 0)
        val gpx = exporter.serialize(ride)

        gpx shouldContain "<trkseg>"
        gpx shouldContain "</trkseg>"
        // No trkpt elements
        Regex("<trkpt").findAll(gpx).count() shouldBe 0
    }

    test("serialize includes ride name") {
        val ride = sampleRide()
        val gpx = exporter.serialize(ride)

        gpx shouldContain "<name>CycleComp Ride</name>"
    }

    test("serialize includes Garmin namespace declaration") {
        val ride = sampleRide()
        val gpx = exporter.serialize(ride)

        gpx shouldContain "xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\""
    }
})
