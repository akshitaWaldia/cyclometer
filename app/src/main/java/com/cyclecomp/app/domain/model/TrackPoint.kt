package com.cyclecomp.app.domain.model

import java.time.Instant

data class TrackPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double,
    val speedKmh: Double,
    val heartRateBpm: Int?,
    val cadenceRpm: Int?,
    val powerW: Double,
    val gradientPercent: Double,
    val cumulativeDistanceKm: Double
)
