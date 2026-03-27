package com.cyclecomp.app.domain.model

data class SensorSnapshot(
    val heartRateBpm: Int?,
    val cadenceRpm: Int?,
    val speedKmh: Double?,
    val locationLat: Double?,
    val locationLon: Double?,
    val altitudeM: Double?,
    val gradientPercent: Double?,
    val gpsSource: GpsSource,
    val timestamp: Long
)
