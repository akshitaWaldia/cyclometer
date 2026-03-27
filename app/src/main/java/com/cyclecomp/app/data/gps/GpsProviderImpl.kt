@file:SuppressLint("MissingPermission")

package com.cyclecomp.app.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.cyclecomp.app.domain.model.GpsReading
import com.cyclecomp.app.domain.model.GpsSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class GpsProviderImpl @Inject constructor(
    private val context: Context
) : GpsProvider {

    companion object {
        private const val TAG = "GpsProviderImpl"
        private const val EARTH_RADIUS_KM = 6371.0
        private const val GRADIENT_WINDOW_M = 50.0
        private const val GPS_INTERVAL_MS = 1000L
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<GpsReading?>(null)
    override val location: StateFlow<GpsReading?> = _location.asStateFlow()

    private val _cumulativeDistanceKm = MutableStateFlow(0.0)
    override val cumulativeDistanceKm: StateFlow<Double> = _cumulativeDistanceKm.asStateFlow()

    private val _cumulativeElevationGainM = MutableStateFlow(0.0)
    override val cumulativeElevationGainM: StateFlow<Double> = _cumulativeElevationGainM.asStateFlow()

    private val _currentGradientPercent = MutableStateFlow(0.0)
    override val currentGradientPercent: StateFlow<Double> = _currentGradientPercent.asStateFlow()

    private val _avgSpeedLastKmKmh = MutableStateFlow(0.0)
    override val avgSpeedLastKmKmh: StateFlow<Double> = _avgSpeedLastKmKmh.asStateFlow()

    // Internal tracking state
    private var previousReading: GpsReading? = null
    private var totalDistanceKm = 0.0
    private var totalElevationGainM = 0.0

    // Gradient rolling window: list of (cumulativeDistanceM, altitudeM)
    private val gradientWindow = mutableListOf<Pair<Double, Double>>()

    // Track points for avg speed over last km: (cumulativeDistanceKm, timestampMs)
    private val trackPoints = mutableListOf<Pair<Double, Long>>()

    private var locationCallback: LocationCallback? = null

    override fun start() {
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(GPS_INTERVAL_MS / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val reading = GpsReading(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeM = loc.altitude,
                    speedMps = loc.speed.toDouble(),
                    accuracyM = loc.accuracy,
                    source = GpsSource.PHONE,
                    timestamp = System.currentTimeMillis()
                )
                processReading(reading)
            }
        }

        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            Log.d(TAG, "GPS updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    override fun stop() {
        locationCallback?.let { cb ->
            fusedClient.removeLocationUpdates(cb)
            locationCallback = null
            Log.d(TAG, "GPS updates stopped")
        }
    }

    override fun reset() {
        previousReading = null
        totalDistanceKm = 0.0
        totalElevationGainM = 0.0
        gradientWindow.clear()
        trackPoints.clear()
        _location.value = null
        _cumulativeDistanceKm.value = 0.0
        _cumulativeElevationGainM.value = 0.0
        _currentGradientPercent.value = 0.0
        _avgSpeedLastKmKmh.value = 0.0
    }

    private fun processReading(reading: GpsReading) {
        _location.value = reading

        val prev = previousReading
        if (prev != null) {
            // Distance via Haversine
            val segmentKm = haversineKm(
                prev.latitude, prev.longitude,
                reading.latitude, reading.longitude
            )
            totalDistanceKm += segmentKm
            _cumulativeDistanceKm.value = totalDistanceKm

            // Elevation gain (positive deltas only)
            val altDelta = reading.altitudeM - prev.altitudeM
            if (altDelta > 0) {
                totalElevationGainM += altDelta
                _cumulativeElevationGainM.value = totalElevationGainM
            }

            // Gradient over 50m rolling window
            updateGradient(reading.altitudeM)

            // Track points for avg speed over last km
            trackPoints.add(Pair(totalDistanceKm, reading.timestamp))
            updateAvgSpeedLastKm()
        } else {
            // First point — seed the gradient window
            gradientWindow.add(Pair(0.0, reading.altitudeM))
            trackPoints.add(Pair(0.0, reading.timestamp))
        }

        previousReading = reading
    }

    private fun updateGradient(currentAltitude: Double) {
        val currentDistanceM = totalDistanceKm * 1000.0
        gradientWindow.add(Pair(currentDistanceM, currentAltitude))

        // Remove points outside the 50m window from the back
        while (gradientWindow.size > 1 &&
            (currentDistanceM - gradientWindow.first().first) > GRADIENT_WINDOW_M
        ) {
            gradientWindow.removeAt(0)
        }

        if (gradientWindow.size >= 2) {
            val first = gradientWindow.first()
            val last = gradientWindow.last()
            val horizontalDist = last.first - first.first
            if (horizontalDist > 1.0) { // At least 1m to avoid division by near-zero
                val altChange = last.second - first.second
                val gradient = (altChange / horizontalDist) * 100.0
                _currentGradientPercent.value = gradient.coerceIn(-100.0, 100.0)
            }
        }
    }

    private fun updateAvgSpeedLastKm() {
        if (totalDistanceKm < 1.0) {
            _avgSpeedLastKmKmh.value = 0.0
            return
        }

        // Find the point where distance was (totalDistanceKm - 1.0)
        val targetDist = totalDistanceKm - 1.0
        var startIdx = 0
        for (i in trackPoints.indices) {
            if (trackPoints[i].first >= targetDist) {
                startIdx = i
                break
            }
        }

        val startPoint = trackPoints[startIdx]
        val endPoint = trackPoints.last()
        val timeDeltaMs = endPoint.second - startPoint.second
        val distDelta = endPoint.first - startPoint.first

        if (timeDeltaMs > 0 && distDelta > 0) {
            val timeDeltaH = timeDeltaMs / 3_600_000.0
            _avgSpeedLastKmKmh.value = distDelta / timeDeltaH
        }

        // Prune old track points that are well before the 1km window
        if (startIdx > 1) {
            trackPoints.subList(0, startIdx - 1).clear()
        }
    }
}

/**
 * Haversine distance between two GPS coordinates in kilometers.
 */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6371.0 * c
}
