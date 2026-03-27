package com.cyclecomp.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RideEntity::class, TrackPointEntity::class, LapEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CycleCompDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
