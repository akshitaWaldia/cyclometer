package com.cyclecomp.app.domain.model

import java.util.UUID

data class BleDevice(
    val address: String,
    val name: String?,
    val serviceUuids: List<UUID>,
    val rssi: Int
)
