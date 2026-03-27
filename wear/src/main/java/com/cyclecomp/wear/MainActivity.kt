package com.cyclecomp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WearMainActivity"
    }

    private lateinit var healthServicesManager: HealthServicesManager
    private lateinit var dataLayerSender: DataLayerSender

    private val requiredPermissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            Log.d(TAG, "All permissions granted")
        } else {
            Log.w(TAG, "Some permissions denied: $grants")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use shared managers so service and activity share state
        if (PhoneCommandListenerService.sharedHealthServicesManager == null) {
            PhoneCommandListenerService.sharedHealthServicesManager =
                HealthServicesManager(applicationContext)
        }
        if (PhoneCommandListenerService.sharedDataLayerSender == null) {
            PhoneCommandListenerService.sharedDataLayerSender =
                DataLayerSender(applicationContext)
        }
        healthServicesManager = PhoneCommandListenerService.sharedHealthServicesManager!!
        dataLayerSender = PhoneCommandListenerService.sharedDataLayerSender!!

        requestPermissionsIfNeeded()
        collectAndSendHeartRate()

        setContent {
            WatchApp(
                healthServicesManager = healthServicesManager,
                onToggleTracking = {
                    lifecycleScope.launch {
                        if (healthServicesManager.isTracking.value) {
                            healthServicesManager.stopTracking()
                        } else {
                            healthServicesManager.startTracking()
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop tracking on destroy — service keeps it running
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun collectAndSendHeartRate() {
        lifecycleScope.launch {
            healthServicesManager.heartRate.collectLatest { hr ->
                if (hr != null) {
                    dataLayerSender.sendHeartRate(hr)
                }
            }
        }
    }
}

@Composable
fun WatchApp(
    healthServicesManager: HealthServicesManager,
    onToggleTracking: () -> Unit
) {
    val heartRate by healthServicesManager.heartRate.collectAsState()
    val isTracking by healthServicesManager.isTracking.collectAsState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CycleComp Watch",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (heartRate != null) "$heartRate bpm" else "-- bpm",
                color = if (heartRate != null) Color(0xFFFF5252) else Color.Gray,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onToggleTracking) {
                Text(
                    text = if (isTracking) "Stop" else "Start",
                    fontSize = 14.sp
                )
            }
        }
    }
}
