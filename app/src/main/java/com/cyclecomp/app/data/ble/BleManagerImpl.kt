@file:SuppressLint("MissingPermission")

package com.cyclecomp.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.domain.model.BleDevice
import com.cyclecomp.app.domain.model.ConnectionState
import com.cyclecomp.app.domain.model.PairedDevice
import com.cyclecomp.app.domain.model.SensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManagerImpl @Inject constructor(
    private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) : BleManager {

    companion object {
        private const val TAG = "BleManagerImpl"

        // Standard BLE service UUIDs
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

        // Standard BLE characteristic UUIDs
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_UUID: UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")

        // Client Characteristic Configuration Descriptor
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val RECONNECT_INTERVAL_MS = 5_000L
        private const val RECONNECT_TIMEOUT_MS = 60_000L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    override val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    // GATT connections keyed by device address
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()

    // Characteristic data flows keyed by "address:characteristicUuid"
    private val characteristicFlows = mutableMapOf<String, MutableSharedFlow<ByteArray>>()

    // Reconnection jobs keyed by device address
    private val reconnectJobs = mutableMapOf<String, Job>()

    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null

    init {
        // Auto-connect to previously paired devices on launch
        // Wrapped in try-catch because BLE permissions may not be granted yet
        scope.launch {
            try {
                autoConnectPairedDevices()
            } catch (e: SecurityException) {
                Log.w(TAG, "BLE permissions not granted yet, skipping auto-connect")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-connect failed", e)
            }
        }
    }

    override suspend fun startScan(serviceUuids: List<UUID>, timeoutMs: Long) {
        val bleScanner = scanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        // Clear previous results
        _discoveredDevices.value = emptyList()

        val filters = serviceUuids.map { uuid ->
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(uuid))
                .build()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val bleDevice = BleDevice(
                    address = device.address,
                    name = device.name,
                    serviceUuids = result.scanRecord?.serviceUuids
                        ?.map { it.uuid } ?: emptyList(),
                    rssi = result.rssi
                )
                _discoveredDevices.update { current ->
                    val existing = current.indexOfFirst { it.address == bleDevice.address }
                    if (existing >= 0) {
                        current.toMutableList().apply { set(existing, bleDevice) }
                    } else {
                        current + bleDevice
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
            }
        }

        scanCallback = callback
        bleScanner.startScan(filters, settings, callback)

        // Auto-stop scan after timeout
        scanJob = scope.launch {
            delay(timeoutMs)
            stopScan()
        }
    }

    override suspend fun stopScan() {
        scanCallback?.let { callback ->
            scanner?.stopScan(callback)
            scanCallback = null
        }
        scanJob?.cancel()
        scanJob = null
    }

    override suspend fun connect(deviceAddress: String): Result<Unit> {
        val adapter = bluetoothAdapter ?: return Result.failure(
            IllegalStateException("Bluetooth not available")
        )

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }

        // Cancel any existing reconnection job
        reconnectJobs[deviceAddress]?.cancel()
        reconnectJobs.remove(deviceAddress)

        updateConnectionState(deviceAddress, ConnectionState.CONNECTING)

        return try {
            val gatt = device.connectGatt(
                context,
                false,
                createGattCallback(deviceAddress),
                BluetoothDevice.TRANSPORT_LE
            )
            if (gatt != null) {
                gattConnections[deviceAddress] = gatt
                Result.success(Unit)
            } else {
                updateConnectionState(deviceAddress, ConnectionState.DISCONNECTED)
                Result.failure(IllegalStateException("Failed to create GATT connection"))
            }
        } catch (e: Exception) {
            updateConnectionState(deviceAddress, ConnectionState.DISCONNECTED)
            Result.failure(e)
        }
    }

    override suspend fun disconnect(deviceAddress: String) {
        reconnectJobs[deviceAddress]?.cancel()
        reconnectJobs.remove(deviceAddress)

        gattConnections[deviceAddress]?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        gattConnections.remove(deviceAddress)
        updateConnectionState(deviceAddress, ConnectionState.DISCONNECTED)
    }

    override fun getCharacteristicFlow(
        deviceAddress: String,
        characteristicUuid: UUID
    ): Flow<ByteArray> {
        val key = "$deviceAddress:$characteristicUuid"
        return characteristicFlows.getOrPut(key) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 64)
        }.asSharedFlow()
    }

    // --- Private helpers ---

    private fun createGattCallback(deviceAddress: String) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to $deviceAddress")
                    updateConnectionState(deviceAddress, ConnectionState.CONNECTED)
                    gatt.discoverServices()
                    savePairedDevice(deviceAddress, gatt)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from $deviceAddress (status=$status)")
                    gatt.close()
                    gattConnections.remove(deviceAddress)
                    handleDisconnection(deviceAddress)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for $deviceAddress")
                subscribeToNotifications(gatt, deviceAddress)
            } else {
                Log.e(TAG, "Service discovery failed for $deviceAddress, status=$status")
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val key = "$deviceAddress:${characteristic.uuid}"
            val flow = characteristicFlows[key]
            val data = characteristic.value
            if (data != null && flow != null) {
                flow.tryEmit(data)
            }
        }
    }

    private fun subscribeToNotifications(gatt: BluetoothGatt, deviceAddress: String) {
        val characteristicsToSubscribe = listOf(
            HR_SERVICE_UUID to HR_MEASUREMENT_UUID,
            CSC_SERVICE_UUID to CSC_MEASUREMENT_UUID
        )

        for ((serviceUuid, charUuid) in characteristicsToSubscribe) {
            val service = gatt.getService(serviceUuid) ?: continue
            val characteristic = service.getCharacteristic(charUuid) ?: continue

            // Ensure the shared flow exists
            val key = "$deviceAddress:$charUuid"
            characteristicFlows.getOrPut(key) {
                MutableSharedFlow(replay = 1, extraBufferCapacity = 64)
            }

            @Suppress("DEPRECATION")
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun handleDisconnection(deviceAddress: String) {
        // Start reconnection loop
        updateConnectionState(deviceAddress, ConnectionState.RECONNECTING)

        val job = scope.launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < RECONNECT_TIMEOUT_MS) {
                delay(RECONNECT_INTERVAL_MS)

                // Check if already reconnected
                if (_connectionStates.value[deviceAddress] == ConnectionState.CONNECTED) {
                    return@launch
                }

                Log.d(TAG, "Attempting reconnection to $deviceAddress")
                val result = connect(deviceAddress)
                if (result.isSuccess) {
                    return@launch
                }
            }

            // Exhausted all retries
            Log.w(TAG, "Reconnection to $deviceAddress failed after 60s")
            updateConnectionState(deviceAddress, ConnectionState.DISCONNECTED)
        }
        reconnectJobs[deviceAddress] = job
    }

    private fun updateConnectionState(address: String, state: ConnectionState) {
        _connectionStates.update { current ->
            current + (address to state)
        }
    }

    private fun savePairedDevice(deviceAddress: String, gatt: BluetoothGatt) {
        scope.launch {
            try {
                val sensorType = determineSensorType(gatt)
                val device = gatt.device
                val pairedDevice = PairedDevice(
                    address = deviceAddress,
                    name = device.name,
                    sensorType = sensorType,
                    lastConnected = Instant.now()
                )
                savePairedDeviceToPrefs(pairedDevice)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save paired device", e)
            }
        }
    }

    private fun determineSensorType(gatt: BluetoothGatt): SensorType {
        val services = gatt.services.map { it.uuid }
        return when {
            services.contains(HR_SERVICE_UUID) -> SensorType.HEART_RATE
            services.contains(CSC_SERVICE_UUID) -> SensorType.CADENCE
            else -> SensorType.HEART_RATE // default
        }
    }

    private suspend fun savePairedDeviceToPrefs(device: PairedDevice) {
        val currentJson = prefsRepository.pairedDevicesJson.first()
        val currentDevices = parsePairedDevices(currentJson)
            .filter { it.address != device.address }
            .toMutableList()
        currentDevices.add(device)
        val json = serializePairedDevices(currentDevices)
        prefsRepository.setPairedDevicesJson(json)
    }

    private suspend fun autoConnectPairedDevices() {
        try {
            val json = prefsRepository.pairedDevicesJson.first()
            val devices = parsePairedDevices(json)
            for (device in devices) {
                Log.d(TAG, "Auto-connecting to paired device: ${device.address}")
                connect(device.address)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-connect paired devices", e)
        }
    }

    // Simple JSON serialization for paired devices
    private fun serializePairedDevices(devices: List<PairedDevice>): String {
        return buildString {
            append("[")
            devices.forEachIndexed { index, device ->
                if (index > 0) append(",")
                append("{")
                append("\"address\":\"${device.address}\",")
                append("\"name\":${if (device.name != null) "\"${device.name}\"" else "null"},")
                append("\"sensorType\":\"${device.sensorType.name}\",")
                append("\"lastConnected\":\"${device.lastConnected}\"")
                append("}")
            }
            append("]")
        }
    }

    private fun parsePairedDevices(json: String?): List<PairedDevice> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            // Simple manual JSON parsing for the paired devices array
            val result = mutableListOf<PairedDevice>()
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

            val content = trimmed.substring(1, trimmed.length - 1).trim()
            if (content.isEmpty()) return emptyList()

            // Split by objects - find matching braces
            val objects = splitJsonObjects(content)
            for (obj in objects) {
                val address = extractJsonString(obj, "address") ?: continue
                val name = extractJsonString(obj, "name")
                val sensorTypeStr = extractJsonString(obj, "sensorType") ?: continue
                val lastConnectedStr = extractJsonString(obj, "lastConnected")

                val sensorType = try {
                    SensorType.valueOf(sensorTypeStr)
                } catch (e: Exception) {
                    SensorType.HEART_RATE
                }

                val lastConnected = try {
                    if (lastConnectedStr != null) Instant.parse(lastConnectedStr) else Instant.now()
                } catch (e: Exception) {
                    Instant.now()
                }

                result.add(PairedDevice(address, name, sensorType, lastConnected))
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse paired devices JSON", e)
            emptyList()
        }
    }

    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(content.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractJsonString(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val keyIndex = json.indexOf(keyPattern)
        if (keyIndex < 0) return null
        val colonIndex = json.indexOf(':', keyIndex + keyPattern.length)
        if (colonIndex < 0) return null
        val afterColon = json.substring(colonIndex + 1).trim()
        return if (afterColon.startsWith("null")) {
            null
        } else if (afterColon.startsWith("\"")) {
            val endQuote = afterColon.indexOf('"', 1)
            if (endQuote > 0) afterColon.substring(1, endQuote) else null
        } else {
            null
        }
    }
}
