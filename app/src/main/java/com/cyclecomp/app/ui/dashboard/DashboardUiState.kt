package com.cyclecomp.app.ui.dashboard

import com.cyclecomp.app.domain.model.HeartRateZone
import com.cyclecomp.app.domain.model.RideState
import com.cyclecomp.app.domain.model.SyncState

data class DashboardUiState(
    val powerW: Int = 0,
    val avgPowerW: Int = 0,
    val normalizedPowerW: Int = 0,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedLastKmKmh: Double = 0.0,
    val heartRateBpm: Int? = null,
    val heartRateZone: HeartRateZone? = null,
    val cadenceRpm: Int? = null,
    val distanceKm: Double = 0.0,
    val elapsedTime: String = "00:00:00",
    val elapsedTimeMs: Long = 0L,
    val gradientPercent: Double = 0.0,
    val tss: Int = 0,
    val elevationGainM: Double = 0.0,
    val caloriesKcal: Int = 0,
    val rideState: RideState = RideState.IDLE,
    val isAutoPaused: Boolean = false,
    // Map state
    val mapEnabled: Boolean = true,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val trackPoints: List<com.google.android.gms.maps.model.LatLng> = emptyList(),
    // Export dialog state
    val showStopDialog: Boolean = false,
    val exportInProgress: Boolean = false,
    val exportSuccess: Boolean? = null,
    val exportErrorMessage: String? = null,
    // Strava sync state
    val stravaSyncState: SyncState = SyncState.IDLE,
    val stravaConnected: Boolean = false,
    // Health Connect state
    val healthConnectWriteSuccess: Boolean? = null,
    val healthConnectError: String? = null,
    // Error/status banners
    val sensorDisconnected: Boolean = false,
    val disconnectedSensorName: String? = null,
    val gpsLost: Boolean = false,
    val bluetoothDisabled: Boolean = false
)
