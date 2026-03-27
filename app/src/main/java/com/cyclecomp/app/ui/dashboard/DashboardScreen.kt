package com.cyclecomp.app.ui.dashboard

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cyclecomp.app.domain.model.RideState
import com.cyclecomp.app.domain.model.SyncState
import com.cyclecomp.app.ui.theme.CycleCompColors
import com.cyclecomp.app.ui.theme.LocalFontScale
import com.cyclecomp.app.ui.theme.LocalNightMode
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSensorScan: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isNightMode = LocalNightMode.current

    // Runtime permission launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Start ride regardless of permission result
        viewModel.onStartRide()
    }

    val context = LocalContext.current

    fun startRideWithPermissions() {
        val fineLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocation == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            viewModel.onStartRide()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Keep screen awake while ride is active (including auto-paused)
    KeepScreenOn(enabled = uiState.rideState == RideState.RECORDING || uiState.isAutoPaused)

    // Stop dialog
    if (uiState.showStopDialog) {
        RideStopDialog(
            onSaveAndExport = viewModel::onSaveAndExport,
            onDiscard = viewModel::onDiscardRide
        )
    }

    // Export error dialog with retry
    if (uiState.exportSuccess == false && uiState.exportErrorMessage != null) {
        ExportErrorDialog(
            errorMessage = uiState.exportErrorMessage!!,
            onRetry = viewModel::onRetryExport,
            onDismiss = viewModel::onDismissExportResult
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CycleComp",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Ride state indicator
                    if (uiState.rideState != RideState.IDLE) {
                        RideStateChip(rideState = uiState.rideState)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onNavigateToSensorScan) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Sensor Setup"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Bluetooth disabled banner
            if (uiState.bluetoothDisabled) {
                ErrorBanner(
                    text = "Bluetooth is disabled",
                    actionText = "Enable",
                    onAction = {
                        val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        (context as? androidx.activity.ComponentActivity)?.startActivity(enableBtIntent)
                    },
                    color = CycleCompColors.HeartRateRed
                )
            }

            // Sensor disconnected banner
            if (uiState.sensorDisconnected && !uiState.bluetoothDisabled) {
                ErrorBanner(
                    text = "Sensor disconnected",
                    actionText = "Reconnect",
                    onAction = { viewModel.onReconnectSensor() },
                    color = CycleCompColors.ElevationAmber
                )
            }

            // Auto-pause banner
            if (uiState.isAutoPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CycleCompColors.ElevationAmber.copy(alpha = 0.3f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PAUSED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CycleCompColors.ElevationAmber
                    )
                }
            }

            // Export in progress indicator
            if (uiState.exportInProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CycleCompColors.CadenceBlue.copy(alpha = 0.2f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Exporting ride...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CycleCompColors.CadenceBlue
                        )
                    }
                }
            }

            // Export success banner
            if (uiState.exportSuccess == true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CycleCompColors.SpeedGreen.copy(alpha = 0.2f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ride saved (FIT + GPX)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CycleCompColors.SpeedGreen
                    )
                }

                // Post-save action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.stravaConnected) {
                        Button(
                            onClick = viewModel::onUploadToStrava,
                            enabled = uiState.stravaSyncState != SyncState.UPLOADING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFC4C02)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (uiState.stravaSyncState) {
                                    SyncState.UPLOADING -> "Uploading..."
                                    SyncState.SUCCESS -> "Uploaded ✓"
                                    SyncState.FAILED -> "Retry Strava"
                                    else -> "Upload to Strava"
                                },
                                color = Color.White
                            )
                        }
                    }
                }

                // Health Connect status
                if (uiState.healthConnectWriteSuccess == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CycleCompColors.SpeedGreen.copy(alpha = 0.15f))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Synced to Health Connect ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = CycleCompColors.SpeedGreen
                        )
                    }
                } else if (uiState.healthConnectWriteSuccess == false) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CycleCompColors.HeartRateRed.copy(alpha = 0.15f))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Health Connect: ${uiState.healthConnectError ?: "Failed"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CycleCompColors.HeartRateRed
                        )
                    }
                }
            }

            // Top row: Power | Speed + Avg Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricTile(
                    label = "Power",
                    value = "${uiState.powerW}",
                    unit = "W",
                    accentColor = CycleCompColors.PowerOrange,
                    tileBg = if (isNightMode) CycleCompColors.PowerTileBg else CycleCompColors.PowerTileBgLight,
                    modifier = Modifier.weight(1f)
                )
                SpeedTile(
                    currentSpeed = uiState.currentSpeedKmh,
                    avgSpeed = uiState.avgSpeedLastKmKmh,
                    gpsLost = uiState.gpsLost,
                    isNightMode = isNightMode,
                    modifier = Modifier.weight(1f)
                )
            }

            // Second row: Heart Rate (double-sized) | Cadence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeartRateTile(
                    bpm = uiState.heartRateBpm,
                    zone = uiState.heartRateZone?.name,
                    isNightMode = isNightMode,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Cadence",
                    value = uiState.cadenceRpm?.toString() ?: "--",
                    unit = "RPM",
                    accentColor = CycleCompColors.CadenceBlue,
                    tileBg = if (isNightMode) CycleCompColors.CadenceTileBg else CycleCompColors.CadenceTileBgLight,
                    modifier = Modifier.weight(1f).height(115.dp)
                )
            }

            // Map section with kill switch
            MapSection(
                mapEnabled = uiState.mapEnabled,
                currentLat = uiState.currentLat,
                currentLon = uiState.currentLon,
                trackPoints = uiState.trackPoints,
                isNightMode = isNightMode,
                onToggleMap = viewModel::onToggleMap,
                modifier = Modifier.fillMaxWidth()
            )

            // Bottom row: Distance | Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricTile(
                    label = "Distance",
                    value = String.format("%.2f", uiState.distanceKm),
                    unit = "km",
                    accentColor = CycleCompColors.DistancePurple,
                    tileBg = if (isNightMode) CycleCompColors.DistanceTileBg else CycleCompColors.DistanceTileBgLight,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Time",
                    value = uiState.elapsedTime,
                    unit = "",
                    accentColor = CycleCompColors.TimeTeal,
                    tileBg = if (isNightMode) CycleCompColors.TimeTileBg else CycleCompColors.TimeTileBgLight,
                    modifier = Modifier.weight(1f)
                )
            }

            // Additional metrics row: Gradient | TSS | Elevation | Calories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallMetricTile(
                    label = "Gradient",
                    value = String.format("%.1f%%", uiState.gradientPercent),
                    accentColor = CycleCompColors.GradientCyan,
                    modifier = Modifier.weight(1f)
                )
                SmallMetricTile(
                    label = "TSS",
                    value = "${uiState.tss}",
                    accentColor = CycleCompColors.TssIndigo,
                    modifier = Modifier.weight(1f)
                )
                SmallMetricTile(
                    label = "Elev. Gain",
                    value = "${uiState.elevationGainM.toInt()}m",
                    accentColor = CycleCompColors.ElevationAmber,
                    modifier = Modifier.weight(1f)
                )
                SmallMetricTile(
                    label = "Calories",
                    value = "${uiState.caloriesKcal}",
                    accentColor = CycleCompColors.CaloriesDeepOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ride control buttons
            RideControls(
                rideState = uiState.rideState,
                onStart = { startRideWithPermissions() },
                onPause = viewModel::onPauseRide,
                onResume = viewModel::onResumeRide,
                onStop = viewModel::onStopRide,
                onLap = viewModel::onLapMark
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


// --- Map Section with Kill Switch ---

@Composable
private fun MapSection(
    mapEnabled: Boolean,
    currentLat: Double?,
    currentLon: Double?,
    trackPoints: List<LatLng>,
    isNightMode: Boolean,
    onToggleMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (mapEnabled) {
            CycleCompMapView(
                currentLat = currentLat,
                currentLon = currentLon,
                trackPoints = trackPoints,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MapKillSwitchPlaceholder(
                isNightMode = isNightMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Kill switch toggle button (top-right corner)
        IconButton(
            onClick = onToggleMap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (mapEnabled) Color.Black.copy(alpha = 0.5f)
                    else CycleCompColors.ElevationAmber.copy(alpha = 0.8f)
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (mapEnabled) Icons.Default.BatteryAlert else Icons.Default.Map,
                contentDescription = if (mapEnabled) "Disable map (battery saver)" else "Enable map",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CycleCompMapView(
    currentLat: Double?,
    currentLon: Double?,
    trackPoints: List<LatLng>,
    modifier: Modifier = Modifier
) {
    val defaultPosition = LatLng(37.4219999, -122.0840575) // Default: Googleplex
    val currentPosition = if (currentLat != null && currentLon != null) {
        LatLng(currentLat, currentLon)
    } else null

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentPosition ?: defaultPosition,
            15f
        )
    }

    // Auto-center on current location
    LaunchedEffect(currentPosition) {
        if (currentPosition != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(currentPosition, 16f),
                durationMs = 500
            )
        }
    }

    val mapProperties = remember {
        MapProperties(
            isMyLocationEnabled = false // We draw our own marker
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        // Draw route polyline
        if (trackPoints.size >= 2) {
            Polyline(
                points = trackPoints,
                color = CycleCompColors.SpeedGreen,
                width = 8f
            )
        }

        // Current position marker
        if (currentPosition != null) {
            Marker(
                state = MarkerState(position = currentPosition),
                title = "Current Location"
            )
        }
    }
}

@Composable
private fun MapKillSwitchPlaceholder(
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isNightMode) CycleCompColors.MapPlaceholderBg else CycleCompColors.MapPlaceholderBgLight
    Box(
        modifier = modifier
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = CycleCompColors.ElevationAmber.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Map disabled (battery saver)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// --- Dialogs ---

@Composable
private fun RideStopDialog(
    onSaveAndExport: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Don't dismiss on outside tap */ },
        title = { Text("Ride Complete") },
        text = { Text("Save your ride and export to FIT and GPX files?") },
        confirmButton = {
            Button(
                onClick = onSaveAndExport,
                colors = ButtonDefaults.buttonColors(containerColor = CycleCompColors.SpeedGreen)
            ) {
                Text("Save & Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = CycleCompColors.HeartRateRed)
            }
        }
    )
}

@Composable
private fun ExportErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Failed") },
        text = { Text("Could not export ride: $errorMessage") },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}


// --- Metric Tiles ---

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    tileBg: Color,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontScale.current
    Box(
        modifier = modifier
            .height(95.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tileBg)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = value,
                fontSize = (36 * fontScale).sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SpeedTile(
    currentSpeed: Double,
    avgSpeed: Double,
    gpsLost: Boolean,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontScale.current
    val tileBg = if (isNightMode) CycleCompColors.SpeedTileBg else CycleCompColors.SpeedTileBgLight
    Box(
        modifier = modifier
            .height(95.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tileBg)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Speed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CycleCompColors.SpeedGreen
                )
                if (gpsLost) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "No GPS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CycleCompColors.HeartRateRed
                    )
                }
            }
            Text(
                text = if (gpsLost) "--" else String.format("%.1f", currentSpeed),
                fontSize = (36 * fontScale).sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (gpsLost) CycleCompColors.SpeedGreen.copy(alpha = 0.4f) else CycleCompColors.SpeedGreen
            )
            Text(
                text = "Avg: ${String.format("%.1f", avgSpeed)} km/h",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CycleCompColors.SpeedGreen.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun HeartRateTile(
    bpm: Int?,
    zone: String?,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontScale.current
    val tileBg = if (isNightMode) CycleCompColors.HeartRateTileBg else CycleCompColors.HeartRateTileBgLight
    val hrFontSize = (44 * fontScale).sp

    Box(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tileBg)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = CycleCompColors.HeartRateRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Heart Rate",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CycleCompColors.HeartRateRed
                )
            }
            Text(
                text = bpm?.toString() ?: "--",
                fontSize = hrFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = CycleCompColors.HeartRateRed
            )
            Text(
                text = if (zone != null) "bpm ($zone)" else "bpm",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CycleCompColors.HeartRateRed.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SmallMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isNightMode = LocalNightMode.current
    val bg = if (isNightMode) CycleCompColors.DarkTileBg else CycleCompColors.LightTileBg
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Ride State & Controls ---

@Composable
private fun RideStateChip(rideState: RideState) {
    val (text, color) = when (rideState) {
        RideState.RECORDING -> "REC" to CycleCompColors.HeartRateRed
        RideState.PAUSED -> "PAUSED" to CycleCompColors.ElevationAmber
        RideState.STOPPED -> "STOPPED" to Color.Gray
        RideState.IDLE -> "" to Color.Transparent
    }
    if (text.isNotEmpty()) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun RideControls(
    rideState: RideState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        when (rideState) {
            RideState.IDLE -> {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.SpeedGreen
                    ),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
            RideState.RECORDING -> {
                OutlinedButton(
                    onClick = onLap,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lap")
                }
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.ElevationAmber
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pause", color = Color.Black)
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.HeartRateRed
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
            RideState.PAUSED -> {
                OutlinedButton(
                    onClick = onLap,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lap", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.SpeedGreen
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resume", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.HeartRateRed
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }
            RideState.STOPPED -> {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CycleCompColors.SpeedGreen
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("New Ride")
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    text: String,
    actionText: String?,
    onAction: (() -> Unit)?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.weight(1f)
            )
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAction) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? ComponentActivity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
