package com.cyclecomp.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: String,
    val timestamp: Long,
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
