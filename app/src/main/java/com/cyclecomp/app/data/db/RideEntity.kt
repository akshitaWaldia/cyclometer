package com.cyclecomp.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long,
    val elapsedDurationMs: Long,
    val totalDistanceKm: Double,
    val totalElevationGainM: Double,
    val averageSpeedKmh: Double,
    val averagePowerW: Double,
    val normalizedPowerW: Double,
    val caloriesKcal: Double,
    val tss: Double,
    val fitFilePath: String?,
    val gpxFilePath: String?,
    val stravaUploadId: String?,
    val healthConnectWritten: Boolean = false
)
