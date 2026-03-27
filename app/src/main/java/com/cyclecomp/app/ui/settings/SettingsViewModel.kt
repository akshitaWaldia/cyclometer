package com.cyclecomp.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.domain.model.PairedDevice
import com.cyclecomp.app.domain.model.RiderProfile
import com.cyclecomp.app.domain.model.SyncState
import com.cyclecomp.app.domain.sync.StravaSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val riderWeightKg: String = "75.0",
    val bikeWeightKg: String = "9.0",
    val ftpW: String = "200",
    val nightMode: Boolean = false,
    val largeFont: Boolean = false,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val stravaConnected: Boolean = false,
    val stravaSyncState: SyncState = SyncState.IDLE,
    val saveSuccess: Boolean? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val stravaSyncService: StravaSyncService,
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        collectStravaState()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.riderProfile.collect { profile ->
                _uiState.update {
                    it.copy(
                        riderWeightKg = profile.riderWeightKg.toString(),
                        bikeWeightKg = profile.bikeWeightKg.toString(),
                        ftpW = profile.ftpW.toString()
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.nightMode.collect { enabled ->
                _uiState.update { it.copy(nightMode = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.largeFont.collect { enabled ->
                _uiState.update { it.copy(largeFont = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.pairedDevicesJson.collect { json ->
                val devices = parsePairedDevices(json)
                _uiState.update { it.copy(pairedDevices = devices) }
            }
        }
    }

    private fun collectStravaState() {
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

    fun onRiderWeightChanged(value: String) {
        _uiState.update { it.copy(riderWeightKg = value) }
    }

    fun onBikeWeightChanged(value: String) {
        _uiState.update { it.copy(bikeWeightKg = value) }
    }

    fun onFtpChanged(value: String) {
        _uiState.update { it.copy(ftpW = value) }
    }

    fun onSaveProfile() {
        val state = _uiState.value
        val weight = state.riderWeightKg.toDoubleOrNull() ?: 75.0
        val bikeWeight = state.bikeWeightKg.toDoubleOrNull() ?: 9.0
        val ftp = state.ftpW.toIntOrNull() ?: 200

        viewModelScope.launch {
            userPreferencesRepository.updateRiderProfile(
                RiderProfile(
                    riderWeightKg = weight,
                    bikeWeightKg = bikeWeight,
                    ftpW = ftp
                )
            )
            _uiState.update { it.copy(saveSuccess = true) }
        }
    }

    fun onDismissSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = null) }
    }

    fun onNightModeToggle(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNightMode(enabled)
        }
    }

    fun onLargeFontToggle(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLargeFont(enabled)
        }
    }

    fun onForgetDevice(address: String) {
        viewModelScope.launch {
            bleManager.disconnect(address)
            val currentJson = userPreferencesRepository.pairedDevicesJson.first()
            val devices = parsePairedDevices(currentJson).filter { it.address != address }
            val newJson = serializePairedDevices(devices)
            userPreferencesRepository.setPairedDevicesJson(newJson)
        }
    }

    fun onConnectStrava(activity: Activity) {
        viewModelScope.launch {
            stravaSyncService.authenticate(activity)
        }
    }

    fun onDisconnectStrava() {
        viewModelScope.launch {
            stravaSyncService.disconnect()
        }
    }

    private fun parsePairedDevices(json: String?): List<PairedDevice> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                PairedDevice(
                    address = obj.getString("address"),
                    name = obj.optString("name", null),
                    sensorType = com.cyclecomp.app.domain.model.SensorType.valueOf(
                        obj.getString("sensorType")
                    ),
                    lastConnected = java.time.Instant.ofEpochMilli(
                        obj.getLong("lastConnected")
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializePairedDevices(devices: List<PairedDevice>): String {
        val jsonArray = org.json.JSONArray()
        devices.forEach { device ->
            val obj = org.json.JSONObject()
            obj.put("address", device.address)
            obj.put("name", device.name ?: "")
            obj.put("sensorType", device.sensorType.name)
            obj.put("lastConnected", device.lastConnected.toEpochMilli())
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
