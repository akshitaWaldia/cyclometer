package com.cyclecomp.app.data.ble

import com.cyclecomp.app.domain.model.BleDevice
import com.cyclecomp.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages BLE scanning, connection, and data streaming for cycling sensors.
 * Permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT) are checked at the UI layer
 * before calling these methods.
 */
interface BleManager {
    val discoveredDevices: StateFlow<List<BleDevice>>
    val connectionStates: StateFlow<Map<String, ConnectionState>>

    suspend fun startScan(serviceUuids: List<UUID>, timeoutMs: Long = 10_000)
    suspend fun stopScan()
    suspend fun connect(deviceAddress: String): Result<Unit>
    suspend fun disconnect(deviceAddress: String)
    fun getCharacteristicFlow(deviceAddress: String, characteristicUuid: UUID): Flow<ByteArray>
}
