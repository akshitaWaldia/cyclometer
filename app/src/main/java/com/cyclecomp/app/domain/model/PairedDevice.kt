package com.cyclecomp.app.domain.model

import java.time.Instant

data class PairedDevice(
    val address: String,
    val name: String?,
    val sensorType: SensorType,
    val lastConnected: Instant
)
