package com.cyclecomp.app.data.ble

/**
 * Parsers for standard BLE GATT characteristics used by cycling sensors.
 * Follows the Bluetooth GATT specification for Heart Rate Measurement (0x2A37)
 * and CSC Measurement (0x2A5B).
 */
object BleCharacteristicParsers {

    /**
     * Parses a Heart Rate Measurement characteristic (0x2A37).
     *
     * Format per Bluetooth GATT spec:
     * - Byte 0, bit 0: HR Value Format flag (0 = UINT8, 1 = UINT16)
     * - If UINT8: HR value is byte 1
     * - If UINT16: HR value is bytes 1-2 (little-endian)
     *
     * @return heart rate in bpm, or null if data is invalid
     */
    fun parseHeartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null

        val flags = data[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0

        return if (isUint16) {
            if (data.size < 3) return null
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            if (data.size < 2) return null
            data[1].toInt() and 0xFF
        }
    }

    /**
     * Result of parsing a CSC Measurement characteristic.
     */
    data class CscMeasurement(
        val cumulativeCrankRevolutions: Int,
        val lastCrankEventTime: Int // in 1/1024 seconds
    )

    /**
     * Parses a CSC Measurement characteristic (0x2A5B).
     *
     * Format per Bluetooth GATT spec:
     * - Byte 0: flags
     *   - Bit 0: Wheel Revolution Data Present
     *   - Bit 1: Crank Revolution Data Present
     * - If crank data present (after any wheel data):
     *   - Cumulative Crank Revolutions: UINT16 (little-endian)
     *   - Last Crank Event Time: UINT16 (little-endian, in 1/1024 seconds)
     *
     * @return CscMeasurement with crank data, or null if crank data not present or invalid
     */
    fun parseCscMeasurement(data: ByteArray): CscMeasurement? {
        if (data.isEmpty()) return null

        val flags = data[0].toInt() and 0xFF
        val hasCrankData = (flags and 0x02) != 0
        if (!hasCrankData) return null

        // Determine offset: skip wheel revolution data if present
        val hasWheelData = (flags and 0x01) != 0
        val crankOffset = if (hasWheelData) {
            // Wheel data: 4 bytes cumulative wheel revs + 2 bytes last wheel event time = 6 bytes
            1 + 6
        } else {
            1
        }

        if (data.size < crankOffset + 4) return null

        val cumulativeCrankRevolutions =
            (data[crankOffset].toInt() and 0xFF) or
                    ((data[crankOffset + 1].toInt() and 0xFF) shl 8)

        val lastCrankEventTime =
            (data[crankOffset + 2].toInt() and 0xFF) or
                    ((data[crankOffset + 3].toInt() and 0xFF) shl 8)

        return CscMeasurement(cumulativeCrankRevolutions, lastCrankEventTime)
    }

    /**
     * Derives cadence RPM from two consecutive CSC measurements.
     *
     * RPM = (delta_revolutions / delta_time_seconds) * 60
     * where delta_time_seconds = delta_time_raw / 1024
     *
     * Handles rollover of the 16-bit counters.
     *
     * @return cadence in RPM, or null if calculation is not possible
     */
    fun deriveCadenceRpm(
        previous: CscMeasurement,
        current: CscMeasurement
    ): Int? {
        var deltaRevs = current.cumulativeCrankRevolutions - previous.cumulativeCrankRevolutions
        var deltaTime = current.lastCrankEventTime - previous.lastCrankEventTime

        // Handle 16-bit rollover
        if (deltaRevs < 0) deltaRevs += 65536
        if (deltaTime < 0) deltaTime += 65536

        if (deltaTime == 0) return null

        val deltaTimeSec = deltaTime.toDouble() / 1024.0
        val rpm = (deltaRevs.toDouble() / deltaTimeSec) * 60.0

        return rpm.toInt().coerceIn(0, 300) // Reasonable cadence range
    }
}
