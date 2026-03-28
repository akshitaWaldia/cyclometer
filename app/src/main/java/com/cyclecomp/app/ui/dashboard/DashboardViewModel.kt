package com.cyclecomp.app.ui.dashboard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.db.LapEntity
import com.cyclecomp.app.data.db.RideDao
import com.cyclecomp.app.data.db.RideEntity
import com.cyclecomp.app.data.db.TrackPointEntity
import com.cyclecomp.app.data.export.FitExporter
import com.cyclecomp.app.data.export.GpxExporter
import com.cyclecomp.app.data.gps.GpsProvider
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.data.wearable.WearableHrReceiver
import com.cyclecomp.app.domain.calc.CalorieAndTssCalculator
import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.RideState
import com.cyclecomp.app.domain.model.SyncState
import com.cyclecomp.app.domain.model.toHhMmSs
import com.cyclecomp.app.domain.ride.AutoPauseController
import com.cyclecomp.app.domain.ride.LapManager
import com.cyclecomp.app.domain.ride.LapManagerImpl
import com.cyclecomp.app.domain.ride.RideRecorder
import com.cyclecomp.app.domain.ride.RideRecorderImpl
import com.cyclecomp.app.domain.sensor.PowerEstimatorV2
import com.cyclecomp.app.domain.sensor.SensorHub
import com.cyclecomp.app.domain.sync.HealthConnectWriteService
import com.cyclecomp.app.domain.sync.StravaSyncService
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sensorHub: SensorHub,
    private val gpsProvider: GpsProvider,
    private val bleManager: BleManager,
    private val wearableHrReceiver: WearableHrReceiver,
    private val powerEstimator: PowerEstimatorV2,
    private val calorieAndTssCalculator: CalorieAndTssCalculator,
    private val rideRecorder: RideRecorder,
    private val autoPauseController: AutoPauseController,
    private val lapManager: LapManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fitExporter: FitExporter,
    private val gpxExporter: GpxExporter,
    private val rideDao: RideDao,
    private val stravaSyncService: StravaSyncService,
    private val healthConnectWriteService: HealthConnectWriteService
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardVM"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        collectSensorData()
        collectRideState()
        collectPowerData()
        collectCalorieAndTssData()
        collectAutoPause()
        collectRiderProfile()
        collectGpsForMap()
        collectSyncState()
        monitorSensorConnections()
        monitorGpsSignal()
        monitorBluetoothState()
    }

    // Holds the last RideData from stop() for export
    private var lastRideData: RideData? = null
    // Holds the last exported FIT bytes for Strava upload
    private var lastFitBytes: ByteArray? = null

    private fun collectSensorData() {
        // Collect unified sensor snapshot
        viewModelScope.launch {
            sensorHub.sensorSnapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        heartRateBpm = snapshot.heartRateBpm,
                        cadenceRpm = snapshot.cadenceRpm,
                        currentSpeedKmh = snapshot.speedKmh ?: 0.0,
                        gradientPercent = snapshot.gradientPercent ?: 0.0
                    )
                }

                // Update power estimator with current altitude for air density calculation
                snapshot.altitudeM?.let { altitude ->
                    powerEstimator.updateAltitude(altitude)
                }

                // Feed speed to auto-pause controller when recording
                if (rideRecorder.rideState.value == RideState.RECORDING ||
                    autoPauseController.isAutoPaused.value
                ) {
                    autoPauseController.onSpeedUpdate(snapshot.speedKmh ?: 0.0)
                }

                // Feed power samples to lap manager
                if (rideRecorder.rideState.value == RideState.RECORDING) {
                    (lapManager as? LapManagerImpl)?.addPowerSample(powerEstimator.currentPowerW.value)
                }
            }
        }

        // Combine related flows to avoid cascading UI updates
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                gpsProvider.cumulativeDistanceKm,
                gpsProvider.cumulativeElevationGainM,
                gpsProvider.avgSpeedLastKmKmh,
                sensorHub.heartRateZone
            ) { dist, elev, avg, zone ->
                _uiState.update {
                    it.copy(
                        distanceKm = dist,
                        elevationGainM = elev,
                        avgSpeedLastKmKmh = avg,
                        heartRateZone = zone
                    )
                }
            }.collect()
        }
    }

    private fun collectRideState() {
        viewModelScope.launch {
            combine(
                rideRecorder.rideState,
                rideRecorder.elapsedTimeMs
            ) { state, ms ->
                _uiState.update { 
                    it.copy(
                        rideState = state,
                        elapsedTime = ms.milliseconds.toHhMmSs(),
                        elapsedTimeMs = ms
                    )
                }
            }.collect()
        }
    }

    private fun collectPowerData() {
        viewModelScope.launch {
            combine(
                powerEstimator.currentPowerW,
                powerEstimator.averagePowerW,
                powerEstimator.normalizedPowerW
            ) { current, avg, np ->
                _uiState.update { 
                    it.copy(
                        powerW = current.toInt(),
                        avgPowerW = avg.toInt(),
                        normalizedPowerW = np.toInt()
                    )
                }
            }.collect()
        }
    }

    private fun collectCalorieAndTssData() {
        viewModelScope.launch {
            combine(
                calorieAndTssCalculator.caloriesBurned,
                calorieAndTssCalculator.tss
            ) { cal, tss ->
                _uiState.update { 
                    it.copy(
                        caloriesKcal = cal.toInt(),
                        tss = tss.toInt()
                    )
                }
            }.collect()
        }
    }

    private fun collectAutoPause() {
        viewModelScope.launch {
            autoPauseController.isAutoPaused.collect { paused ->
                _uiState.update { it.copy(isAutoPaused = paused) }
                // Auto-pause/resume the ride recorder
                if (paused && rideRecorder.rideState.value == RideState.RECORDING) {
                    rideRecorder.pause()
                } else if (!paused && rideRecorder.rideState.value == RideState.PAUSED) {
                    rideRecorder.resume()
                }
            }
        }
    }

    private fun collectRiderProfile() {
        viewModelScope.launch {
            userPreferencesRepository.riderProfile.collect { profile ->
                powerEstimator.updateProfile(profile.riderWeightKg, profile.bikeWeightKg)
                calorieAndTssCalculator.updateProfile(
                    weightKg = profile.riderWeightKg,
                    age = 30, // default age
                    ftpW = profile.ftpW
                )
                (rideRecorder as? RideRecorderImpl)?.setRiderProfile(profile)
            }
        }
    }

    fun onStartRide() {
        // Clear previous ride state
        _uiState.update {
            it.copy(
                exportSuccess = null,
                exportErrorMessage = null,
                stravaSyncState = SyncState.IDLE,
                healthConnectWriteSuccess = null,
                healthConnectError = null,
                trackPoints = emptyList(),
                sensorDisconnected = false,
                gpsLost = false
            )
        }
        lastRideData = null
        lastFitBytes = null
        sensorHub.start()
        gpsProvider.reset()
        powerEstimator.reset()
        calorieAndTssCalculator.reset()
        autoPauseController.reset()
        lapManager.startNewRide()
        rideRecorder.start()
        // Start foreground service to keep BLE + GPS alive when backgrounded
        com.cyclecomp.app.domain.ride.RideRecordingService.start(appContext)
        // Send start command to watch to begin HR tracking
        viewModelScope.launch {
            wearableHrReceiver.sendStartCommand()
        }
    }

    fun onPauseRide() {
        rideRecorder.pause()
    }

    fun onResumeRide() {
        rideRecorder.resume()
    }

    fun onStopRide() {
        lastRideData = rideRecorder.stop()
        autoPauseController.reset()
        sensorHub.stop()
        gpsProvider.stop()
        // Stop foreground service
        com.cyclecomp.app.domain.ride.RideRecordingService.stop(appContext)
        // Send stop command to watch
        viewModelScope.launch {
            wearableHrReceiver.sendStopCommand()
        }
        // Show the stop dialog for save/discard
        if (lastRideData != null) {
            _uiState.update { it.copy(showStopDialog = true) }
        }
    }

    /** User chose "Save & Export" from the stop dialog */
    fun onSaveAndExport() {
        val ride = lastRideData ?: return
        _uiState.update { it.copy(showStopDialog = false, exportInProgress = true) }

        viewModelScope.launch {
            try {
                val (fitPath, gpxPath, fitBytes) = withContext(Dispatchers.IO) {
                    exportRideFiles(ride)
                }
                // Store FIT bytes for potential Strava upload
                lastFitBytes = fitBytes
                // Persist to Room
                withContext(Dispatchers.IO) {
                    persistRide(ride, fitPath, gpxPath)
                }
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportSuccess = true,
                        exportErrorMessage = null
                    )
                }
                // Auto-write to Health Connect if available
                autoWriteHealthConnect(ride)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportSuccess = false,
                        exportErrorMessage = e.message ?: "Export failed"
                    )
                }
            }
        }
    }

    private suspend fun autoWriteHealthConnect(ride: RideData) {
        try {
            if (healthConnectWriteService.isAvailable() && healthConnectWriteService.hasPermissions()) {
                val result = healthConnectWriteService.writeRide(ride)
                _uiState.update {
                    if (result.isSuccess) {
                        it.copy(healthConnectWriteSuccess = true)
                    } else {
                        it.copy(
                            healthConnectWriteSuccess = false,
                            healthConnectError = result.exceptionOrNull()?.message
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect auto-write failed", e)
        }
    }

    /** Retry export after failure */
    fun onRetryExport() {
        onSaveAndExport()
    }

    /** User chose "Discard" from the stop dialog */
    fun onDiscardRide() {
        lastRideData = null
        lastFitBytes = null
        _uiState.update { it.copy(showStopDialog = false) }
    }

    /** Dismiss export result toast/state */
    fun onDismissExportResult() {
        _uiState.update { it.copy(exportSuccess = null, exportErrorMessage = null) }
    }

    /** Toggle map kill switch */
    fun onToggleMap() {
        _uiState.update { it.copy(mapEnabled = !it.mapEnabled) }
    }

    fun onLapMark() {
        if (rideRecorder.rideState.value == RideState.RECORDING ||
            rideRecorder.rideState.value == RideState.PAUSED) {
            lapManager.markLap(
                currentDistanceKm = gpsProvider.cumulativeDistanceKm.value,
                currentPowerW = powerEstimator.currentPowerW.value,
                elapsedTimeMs = rideRecorder.elapsedTimeMs.value
            )
            // Add completed lap to ride recorder
            val laps = lapManager.completedLaps.value
            if (laps.isNotEmpty()) {
                (rideRecorder as? RideRecorderImpl)?.addLap(laps.last())
            }
        }
    }

    // --- Sensor / GPS / Bluetooth monitoring ---

    private fun monitorSensorConnections() {
        viewModelScope.launch {
            bleManager.connectionStates.collect { states ->
                val disconnected = states.entries.find {
                    it.value == com.cyclecomp.app.domain.model.ConnectionState.DISCONNECTED ||
                    it.value == com.cyclecomp.app.domain.model.ConnectionState.RECONNECTING
                }
                _uiState.update {
                    it.copy(
                        sensorDisconnected = disconnected != null,
                        disconnectedSensorName = disconnected?.key
                    )
                }
            }
        }
    }

    private fun monitorGpsSignal() {
        viewModelScope.launch {
            gpsProvider.location.collect { reading ->
                val gpsLost = reading == null ||
                    (System.currentTimeMillis() - reading.timestamp) > 5000L
                _uiState.update { it.copy(gpsLost = gpsLost) }
            }
        }
    }

    private fun monitorBluetoothState() {
        viewModelScope.launch {
            // Check Bluetooth state periodically
            while (true) {
                val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                val btDisabled = adapter == null || !adapter.isEnabled
                _uiState.update { it.copy(bluetoothDisabled = btDisabled) }
                kotlinx.coroutines.delay(3000L)
            }
        }
    }

    fun onReconnectSensor() {
        val address = _uiState.value.disconnectedSensorName ?: return
        viewModelScope.launch {
            bleManager.connect(address)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorHub.stop()
        gpsProvider.stop()
    }

    // --- Sync state collection ---

    private fun collectSyncState() {
        viewModelScope.launch {
            stravaSyncService.syncState.collect { state ->
                _uiState.update { it.copy(stravaSyncState = state) }
            }
        }
        viewModelScope.launch {
            stravaSyncService.isConnected.collect { connected ->
                _uiState.update { it.copy(stravaConnected = connected) }
            }
        }
    }

    /** Upload last ride to Strava */
    fun onUploadToStrava() {
        val fitBytes = lastFitBytes ?: return
        viewModelScope.launch {
            val result = stravaSyncService.upload(fitBytes, "CycleComp Ride")
            if (result.isSuccess) {
                _uiState.update { it.copy(stravaSyncState = SyncState.SUCCESS) }
            }
            // Failure state is handled by the service's syncState flow
        }
    }

    /** Write last ride to Health Connect */
    fun onWriteToHealthConnect() {
        val ride = lastRideData ?: return
        viewModelScope.launch {
            val result = healthConnectWriteService.writeRide(ride)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(healthConnectWriteSuccess = true, healthConnectError = null)
                } else {
                    it.copy(
                        healthConnectWriteSuccess = false,
                        healthConnectError = result.exceptionOrNull()?.message ?: "Write failed"
                    )
                }
            }
        }
    }

    fun onDismissHealthConnectResult() {
        _uiState.update { it.copy(healthConnectWriteSuccess = null, healthConnectError = null) }
    }

    fun onDismissStravaResult() {
        _uiState.update { it.copy(stravaSyncState = SyncState.IDLE) }
    }

    // --- Map GPS tracking ---

    private fun collectGpsForMap() {
        viewModelScope.launch {
            gpsProvider.location.collect { reading ->
                if (reading != null) {
                    val latLng = LatLng(reading.latitude, reading.longitude)
                    _uiState.update { current ->
                        current.copy(
                            currentLat = reading.latitude,
                            currentLon = reading.longitude,
                            trackPoints = if (rideRecorder.rideState.value == RideState.RECORDING) {
                                current.trackPoints + latLng
                            } else {
                                current.trackPoints
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Export helpers ---

    private fun exportRideFiles(ride: RideData): Triple<String, String, ByteArray> {
        val externalDir = appContext.getExternalFilesDir(null)
        val exportDir = if (externalDir != null) {
            File(externalDir, "rides")
        } else {
            // Fallback to internal storage
            File(appContext.filesDir, "rides")
        }
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw IOException("Failed to create export directory: ${exportDir.absolutePath}")
        }

        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(ride.startTime)

        // FIT export
        val fitFile = File(exportDir, "ride_${timestamp}.fit")
        val fitBytes = fitExporter.serialize(ride)
        fitFile.writeBytes(fitBytes)

        // GPX export
        val gpxFile = File(exportDir, "ride_${timestamp}.gpx")
        val gpxXml = gpxExporter.serialize(ride)
        gpxFile.writeText(gpxXml, Charsets.UTF_8)

        return Triple(fitFile.absolutePath, gpxFile.absolutePath, fitBytes)
    }

    private suspend fun persistRide(ride: RideData, fitPath: String, gpxPath: String) {
        val rideEntity = RideEntity(
            id = ride.id,
            startTime = ride.startTime.toEpochMilli(),
            endTime = ride.endTime.toEpochMilli(),
            elapsedDurationMs = ride.elapsedDuration.toMillis(),
            totalDistanceKm = ride.totalDistanceKm,
            totalElevationGainM = ride.totalElevationGainM,
            averageSpeedKmh = ride.averageSpeedKmh,
            averagePowerW = ride.averagePowerW,
            normalizedPowerW = ride.normalizedPowerW,
            caloriesKcal = ride.caloriesKcal,
            tss = ride.tss,
            fitFilePath = fitPath,
            gpxFilePath = gpxPath,
            stravaUploadId = null,
            healthConnectWritten = false
        )

        val trackPointEntities = ride.trackPoints.map { tp ->
            TrackPointEntity(
                rideId = ride.id,
                timestamp = tp.timestamp.toEpochMilli(),
                latitude = tp.latitude,
                longitude = tp.longitude,
                altitudeM = tp.altitudeM,
                speedKmh = tp.speedKmh,
                heartRateBpm = tp.heartRateBpm,
                cadenceRpm = tp.cadenceRpm,
                powerW = tp.powerW,
                gradientPercent = tp.gradientPercent,
                cumulativeDistanceKm = tp.cumulativeDistanceKm
            )
        }

        val lapEntities = ride.laps.map { lap ->
            LapEntity(
                rideId = ride.id,
                lapNumber = lap.lapNumber,
                startTime = lap.startTime.toEpochMilli(),
                endTime = lap.endTime.toEpochMilli(),
                distanceKm = lap.distanceKm,
                averagePowerW = lap.averagePowerW,
                elapsedTimeMs = lap.elapsedTime.toMillis()
            )
        }

        rideDao.insertFullRide(rideEntity, trackPointEntities, lapEntities)
    }
}
