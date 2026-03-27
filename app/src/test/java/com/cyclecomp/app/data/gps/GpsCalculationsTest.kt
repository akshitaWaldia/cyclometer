package com.cyclecomp.app.data.gps

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class GpsCalculationsTest : FunSpec({

    test("haversineKm returns 0 for identical points") {
        val dist = haversineKm(51.5074, -0.1278, 51.5074, -0.1278)
        dist shouldBe (0.0 plusOrMinus 0.0001)
    }

    test("haversineKm London to Paris is approximately 343 km") {
        // London: 51.5074, -0.1278  Paris: 48.8566, 2.3522
        val dist = haversineKm(51.5074, -0.1278, 48.8566, 2.3522)
        dist shouldBe (343.5 plusOrMinus 2.0)
    }

    test("haversineKm short distance - 100m apart") {
        // Two points roughly 100m apart on the equator
        // 1 degree longitude at equator ≈ 111.32 km
        // 0.001 degree ≈ 111.32 m
        val dist = haversineKm(0.0, 0.0, 0.0, 0.001)
        dist shouldBe (0.111 plusOrMinus 0.005)
    }

    test("haversineKm is symmetric") {
        val d1 = haversineKm(40.7128, -74.0060, 34.0522, -118.2437)
        val d2 = haversineKm(34.0522, -118.2437, 40.7128, -74.0060)
        d1 shouldBe (d2 plusOrMinus 0.001)
    }

    test("haversineKm is always non-negative") {
        val dist = haversineKm(-33.8688, 151.2093, 35.6762, 139.6503)
        (dist >= 0.0) shouldBe true
    }
})
