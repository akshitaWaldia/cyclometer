package com.cyclecomp.app.ui.sensor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cyclecomp.app.domain.model.BleDevice
import com.cyclecomp.app.domain.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: SensorScanViewModel = hiltViewModel()
) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val connectionStates by viewModel.connectionStates.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // Runtime BLE permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.startScan()
        }
    }

    fun startScanWithPermissions() {
        val context = viewModel // we check from the composable context below
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // Find disconnected sensors that were previously connected
    val disconnectedSensors = connectionStates.filter { it.value == ConnectionState.DISCONNECTED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Disconnected sensors banner
            if (disconnectedSensors.isNotEmpty()) {
                DisconnectedBanner(
                    count = disconnectedSensors.size,
                    onRetry = {
                        disconnectedSensors.keys.forEach { address ->
                            viewModel.connectDevice(address)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Scan button
            Button(
                onClick = {
                    if (isScanning) viewModel.stopScan() else startScanWithPermissions()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan for Sensors")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (devices.isEmpty() && !isScanning) {
                EmptyState()
            } else {
                Text(
                    text = "Discovered Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        val state = connectionStates[device.address]
                            ?: ConnectionState.DISCONNECTED
                        SensorDeviceTile(
                            device = device,
                            connectionState = state,
                            onTap = {
                                when (state) {
                                    ConnectionState.DISCONNECTED ->
                                        viewModel.connectDevice(device.address)
                                    ConnectionState.CONNECTED ->
                                        viewModel.disconnectDevice(device.address)
                                    else -> { /* connecting/reconnecting — no action */ }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorDeviceTile(
    device: BleDevice,
    connectionState: ConnectionState,
    onTap: () -> Unit
) {
    val containerColor = when (connectionState) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection state icon
            ConnectionStateIcon(connectionState)

            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = connectionStateLabel(connectionState),
                    style = MaterialTheme.typography.labelSmall,
                    color = connectionStateColor(connectionState)
                )
            }

            // Signal strength
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = "Signal strength",
                    modifier = Modifier.size(16.dp),
                    tint = signalColor(device.rssi)
                )
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConnectionStateIcon(state: ConnectionState) {
    when (state) {
        ConnectionState.CONNECTED -> {
            Icon(
                imageVector = Icons.Default.BluetoothConnected,
                contentDescription = "Connected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        ConnectionState.CONNECTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp
            )
        }
        ConnectionState.RECONNECTING -> {
            // Pulsing bluetooth icon
            val infiniteTransition = rememberInfiniteTransition(label = "reconnecting")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "reconnecting_alpha"
            )
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Reconnecting",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }
        ConnectionState.DISCONNECTED -> {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Disconnected",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun DisconnectedBanner(count: Int, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onRetry)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count sensor(s) disconnected. Tap to retry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No sensors found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = "Make sure your sensors are powered on and nearby",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun connectionStateLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.CONNECTING -> "Connecting..."
    ConnectionState.RECONNECTING -> "Reconnecting..."
    ConnectionState.DISCONNECTED -> "Tap to connect"
}

@Composable
private fun connectionStateColor(state: ConnectionState) = when (state) {
    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
    ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
    ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun signalColor(rssi: Int) = when {
    rssi >= -60 -> MaterialTheme.colorScheme.primary
    rssi >= -80 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}
