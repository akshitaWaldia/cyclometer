package com.cyclecomp.app.domain.sync

import com.cyclecomp.app.domain.model.RideData

/**
 * Writes ride data to Health Connect after ride completion.
 */
interface HealthConnectWriteService {
    /** Check if Health Connect is available on this device. */
    suspend fun isAvailable(): Boolean

    /** Check if we have the required write permissions. */
    suspend fun hasPermissions(): Boolean

    /** Get the set of permissions we need. */
    fun getRequiredPermissions(): Set<String>

    /** Write a completed ride to Health Connect. */
    suspend fun writeRide(ride: RideData): Result<Unit>
}
