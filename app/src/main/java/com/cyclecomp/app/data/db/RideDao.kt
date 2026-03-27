package com.cyclecomp.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(trackPoints: List<TrackPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaps(laps: List<LapEntity>)

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: String): RideEntity?

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getTrackPointsForRide(rideId: String): List<TrackPointEntity>

    @Query("SELECT * FROM laps WHERE rideId = :rideId ORDER BY lapNumber ASC")
    suspend fun getLapsForRide(rideId: String): List<LapEntity>

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: String)

    @Transaction
    suspend fun insertFullRide(
        ride: RideEntity,
        trackPoints: List<TrackPointEntity>,
        laps: List<LapEntity>
    ) {
        insertRide(ride)
        insertTrackPoints(trackPoints)
        insertLaps(laps)
    }
}
