package com.cyclecomp.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await

/**
 * Manages Health Services exercise tracking on the watch.
 * Tracks heart rate continuously and emits updates via StateFlow.
 */
class HealthServicesManager(context: Context) {

    companion object {
        private const val TAG = "HealthServicesManager"
    }

    private val exerciseClient: ExerciseClient =
        HealthServices.getClient(context).exerciseClient

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val hrDataPoints = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            if (hrDataPoints.isNotEmpty()) {
                val latestHr = hrDataPoints.last().value.toInt()
                _heartRate.value = latestHr
                Log.d(TAG, "HR update: $latestHr bpm")
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
            // Not used for HR streaming
        }

        override fun onRegistered() {
            Log.d(TAG, "Exercise callback registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "Exercise callback registration failed", throwable)
        }

        override fun onAvailabilityChanged(
            dataType: DataType<*, *>,
            availability: Availability
        ) {
            when (availability) {
                is DataTypeAvailability -> {
                    Log.d(TAG, "DataType $dataType availability: $availability")
                }
                is LocationAvailability -> {
                    Log.d(TAG, "Location availability: $availability")
                }
            }
        }
    }

    suspend fun startTracking() {
        if (_isTracking.value) return

        try {
            val dataTypes = setOf(DataType.HEART_RATE_BPM)

            val exerciseConfig = ExerciseConfig.builder(ExerciseType.BIKING)
                .setDataTypes(dataTypes)
                .build()

            exerciseClient.setUpdateCallback(exerciseCallback)
            exerciseClient.startExerciseAsync(exerciseConfig).await()
            _isTracking.value = true
            Log.d(TAG, "Exercise tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start exercise tracking", e)
            _isTracking.value = false
        }
    }

    suspend fun stopTracking() {
        if (!_isTracking.value) return

        try {
            exerciseClient.endExerciseAsync().await()
            Log.d(TAG, "Exercise tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop exercise tracking", e)
        } finally {
            _isTracking.value = false
            _heartRate.value = null
            exerciseClient.clearUpdateCallbackAsync(exerciseCallback)
        }
    }
}
