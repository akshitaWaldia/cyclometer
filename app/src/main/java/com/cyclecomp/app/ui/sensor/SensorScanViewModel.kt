package com.cyclecomp.app.ui.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.ble.BleManagerImpl
import com.cyclecomp.app.domain.model.BleDevice
import com.cyclecomp.app.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorScanViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStates: StateFlow<Map<String, ConnectionState>> = bleManager.connectionStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            bleManager.startScan(
                serviceUuids = listOf(
                    BleManagerImpl.HR_SERVICE_UUID,
                    BleManagerImpl.CSC_SERVICE_UUID
                ),
                timeoutMs = 10_000
            )
            _isScanning.value = false
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            bleManager.stopScan()
            _isScanning.value = false
        }
    }

    fun connectDevice(address: String) {
        viewModelScope.launch {
            bleManager.connect(address)
        }
    }

    fun disconnectDevice(address: String) {
        viewModelScope.launch {
            bleManager.disconnect(address)
        }
    }
}
