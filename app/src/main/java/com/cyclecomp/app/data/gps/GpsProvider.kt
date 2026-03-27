package com.cyclecomp.app.data.gps

import com.cyclecomp.app.domain.model.GpsReading
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides GPS location, distance, elevation, gradient, and average speed data.
 * Uses FusedLocationProviderClient for phone GPS.
 */
interface GpsProvider {
    val location: StateFlow<GpsReading?>
    val cumulativeDistanceKm: StateFlow<Double>
    val cumulativeElevationGainM: StateFlow<Double>
    val currentGradientPercent: StateFlow<Double>
    val avgSpeedLastKmKmh: StateFlow<Double>

    fun start()
    fun stop()
    fun reset()
}
