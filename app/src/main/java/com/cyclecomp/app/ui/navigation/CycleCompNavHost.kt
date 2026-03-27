package com.cyclecomp.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cyclecomp.app.ui.dashboard.DashboardScreen
import com.cyclecomp.app.ui.sensor.SensorScanScreen
import com.cyclecomp.app.ui.settings.SettingsScreen

object Destinations {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val SENSOR_SCAN = "sensor_scan"
}

@Composable
fun CycleCompNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.DASHBOARD
    ) {
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onNavigateToSettings = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onNavigateToSensorScan = {
                    navController.navigate(Destinations.SENSOR_SCAN)
                }
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Destinations.SENSOR_SCAN) {
            SensorScanScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
