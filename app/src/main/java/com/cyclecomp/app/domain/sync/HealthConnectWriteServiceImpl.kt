package com.cyclecomp.app.domain.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.cyclecomp.app.domain.model.RideData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectWriteServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HealthConnectWriteService {

    companion object {
        private const val TAG = "HealthConnectWrite"
    }

    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect not available", e)
            null
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            getRequiredPermissions().all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    override fun getRequiredPermissions(): Set<String> {
        return setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
        )
    }

    override suspend fun writeRide(ride: RideData): Result<Unit> {
        val client = healthConnectClient
            ?: return Result.failure(IllegalStateException("Health Connect not available"))

        return try {
            val records = mutableListOf<androidx.health.connect.client.records.Record>()

            // Exercise session
            val session = ExerciseSessionRecord(
                startTime = ride.startTime,
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.startTime),
                endTime = ride.endTime,
                endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.endTime),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                title = "Cycling Ride"
            )
            records.add(session)

            // Distance record
            records.add(
                DistanceRecord(
                    startTime = ride.startTime,
                    startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.startTime),
                    endTime = ride.endTime,
                    endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.endTime),
                    distance = Length.kilometers(ride.totalDistanceKm)
                )
            )

            // Total calories
            if (ride.caloriesKcal > 0) {
                records.add(
                    TotalCaloriesBurnedRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.startTime),
                        endTime = ride.endTime,
                        endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.endTime),
                        energy = Energy.kilocalories(ride.caloriesKcal)
                    )
                )
            }

            // Heart rate samples
            val hrSamples = ride.trackPoints
                .filter { it.heartRateBpm != null && it.heartRateBpm > 0 }
                .map { tp ->
                    HeartRateRecord.Sample(
                        time = tp.timestamp,
                        beatsPerMinute = tp.heartRateBpm!!.toLong()
                    )
                }
            if (hrSamples.isNotEmpty()) {
                records.add(
                    HeartRateRecord(
                        startTime = ride.startTime,
                        startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.startTime),
                        endTime = ride.endTime,
                        endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(ride.endTime),
                        samples = hrSamples
                    )
                )
            }

            client.insertRecords(records)
            Log.i(TAG, "Successfully wrote ${records.size} records to Health Connect")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to Health Connect", e)
            Result.failure(e)
        }
    }
}
