package com.cyclecomp.app.domain.model

import java.time.Duration
import java.time.Instant

data class LapData(
    val lapNumber: Int,
    val startTime: Instant,
    val endTime: Instant,
    val distanceKm: Double,
    val averagePowerW: Double,
    val elapsedTime: Duration
)
