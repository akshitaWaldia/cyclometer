package com.cyclecomp.app.domain.model

import java.time.Duration
import java.time.Instant

data class RideData(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val elapsedDuration: Duration,
    val totalDistanceKm: Double,
    val totalElevationGainM: Double,
    val averageSpeedKmh: Double,
    val averagePowerW: Double,
    val normalizedPowerW: Double,
    val maxSpeedKmh: Double,
    val maxPowerW: Double,
    val maxHeartRateBpm: Int?,
    val averageHeartRateBpm: Int?,
    val averageCadenceRpm: Int?,
    val caloriesKcal: Double,
    val tss: Double,
    val trackPoints: List<TrackPoint>,
    val laps: List<LapData>,
    val riderProfile: RiderProfile
)
