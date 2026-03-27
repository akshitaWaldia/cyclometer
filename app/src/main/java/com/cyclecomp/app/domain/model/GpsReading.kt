package com.cyclecomp.app.domain.model

data class GpsReading(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double,
    val speedMps: Double,
    val accuracyM: Float,
    val source: GpsSource,
    val timestamp: Long
)
