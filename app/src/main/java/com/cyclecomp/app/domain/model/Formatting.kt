package com.cyclecomp.app.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Formats a [Duration] to HH:MM:SS string, truncating sub-second precision.
 */
fun Duration.toHhMmSs(): String {
    val totalSeconds = inWholeSeconds
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/**
 * Parses an HH:MM:SS string back to a [Duration].
 */
fun parseHhMmSs(formatted: String): Duration {
    val parts = formatted.split(":")
    require(parts.size == 3) { "Expected HH:MM:SS format" }
    val h = parts[0].toLong()
    val m = parts[1].toLong()
    val s = parts[2].toLong()
    return h.hours + m.minutes + s.seconds
}

/**
 * Formats a distance in km to a string with exactly 2 decimal places.
 */
fun formatDistanceKm(distanceKm: Double): String {
    return "%.2f".format(distanceKm)
}

/**
 * Parses a formatted distance string back to a Double.
 */
fun parseDistanceKm(formatted: String): Double {
    return formatted.toDouble()
}
