package com.cyclecomp.app.domain.model

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

enum class GpsSource {
    PHONE, WATCH, NONE
}

enum class HeartRateZone(val range: IntRange) {
    ZONE1(0..119),
    ZONE2(120..139),
    ZONE3(140..159),
    ZONE4(160..179),
    ZONE5(180..220);

    companion object {
        fun fromBpm(bpm: Int): HeartRateZone {
            return entries.first { bpm in it.range }
        }
    }
}

enum class SensorType {
    HEART_RATE, CADENCE
}

enum class RideState {
    IDLE, RECORDING, PAUSED, STOPPED
}

enum class SyncState {
    IDLE, AUTHENTICATING, UPLOADING, SUCCESS, FAILED
}
