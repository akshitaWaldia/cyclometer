package com.cyclecomp.app.data.export

import com.cyclecomp.app.domain.model.LapData
import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.TrackPoint
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight custom FIT binary serializer.
 * Writes enough of the FIT protocol for Strava/TrainingPeaks/Garmin Connect to import.
 *
 * FIT format overview:
 * - 14-byte file header
 * - Data records (definition messages + data messages)
 * - 2-byte CRC at end
 *
 * Key message types: FileId(0), Record(20), Lap(19), Session(18), Activity(34)
 */
@Singleton
class FitExporter @Inject constructor() {

    companion object {
        // FIT epoch: Dec 31, 1989 00:00:00 UTC
        private val FIT_EPOCH = Instant.parse("1989-12-31T00:00:00Z")

        // Message types
        private const val MESG_FILE_ID: Int = 0
        private const val MESG_SESSION: Int = 18
        private const val MESG_LAP: Int = 19
        private const val MESG_RECORD: Int = 20
        private const val MESG_ACTIVITY: Int = 34

        // Field types
        private const val FIT_UINT8: Int = 0
        private const val FIT_SINT8: Int = 1
        private const val FIT_UINT16: Int = 132
        private const val FIT_SINT16: Int = 131
        private const val FIT_UINT32: Int = 134
        private const val FIT_SINT32: Int = 133
        private const val FIT_ENUM: Int = 0

        fun degreesToSemicircles(degrees: Double): Int {
            return (degrees * (2147483648.0 / 180.0)).toInt()
        }

        fun fitTimestamp(instant: Instant): Long {
            return instant.epochSecond - FIT_EPOCH.epochSecond
        }
    }

    fun serialize(ride: RideData): ByteArray {
        val dataBytes = ByteArrayOutputStream()

        // Write data messages
        writeFileIdMessage(dataBytes, ride)
        // Write record messages (one per track point)
        for (tp in ride.trackPoints) {
            writeRecordMessage(dataBytes, tp)
        }
        // Write lap messages
        for (lap in ride.laps) {
            writeLapMessage(dataBytes, lap)
        }
        // If no laps, write a single lap covering the whole ride
        if (ride.laps.isEmpty()) {
            writeWholeLapMessage(dataBytes, ride)
        }
        // Write session message
        writeSessionMessage(dataBytes, ride)
        // Write activity message
        writeActivityMessage(dataBytes, ride)

        val dataArray = dataBytes.toByteArray()

        // Build complete file: header + data + CRC
        val output = ByteArrayOutputStream()
        writeFileHeader(output, dataArray.size)
        output.write(dataArray)
        val crc = calculateCrc(dataArray)
        output.write(crc and 0xFF)
        output.write((crc shr 8) and 0xFF)

        return output.toByteArray()
    }

    // --- File Header (14 bytes) ---
    private fun writeFileHeader(out: OutputStream, dataSize: Int) {
        val header = ByteArray(14)
        header[0] = 14 // header size
        header[1] = 20 // protocol version (2.0)
        // Profile version 21.141 → little-endian uint16
        val profileVersion = 2141
        header[2] = (profileVersion and 0xFF).toByte()
        header[3] = ((profileVersion shr 8) and 0xFF).toByte()
        // Data size (little-endian uint32)
        header[4] = (dataSize and 0xFF).toByte()
        header[5] = ((dataSize shr 8) and 0xFF).toByte()
        header[6] = ((dataSize shr 16) and 0xFF).toByte()
        header[7] = ((dataSize shr 24) and 0xFF).toByte()
        // ".FIT" ASCII
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        // Header CRC (2 bytes)
        val headerCrc = calculateCrc(header.sliceArray(0..11))
        header[12] = (headerCrc and 0xFF).toByte()
        header[13] = ((headerCrc shr 8) and 0xFF).toByte()
        out.write(header)
    }

    // --- Definition + Data message helpers ---

    /**
     * Writes a definition message followed by a data message.
     * localMesgNum: 0-15 for local message assignment
     * globalMesgNum: FIT global message number
     * fields: list of (fieldDefNum, size, baseType, valueBytes)
     */
    private fun writeDefinitionAndData(
        out: OutputStream,
        localMesgNum: Int,
        globalMesgNum: Int,
        fields: List<FieldEntry>
    ) {
        // Definition message record header: bit6=1 (definition), bits 0-3 = local mesg num
        val defHeader = (0x40 or (localMesgNum and 0x0F)).toByte()
        out.write(defHeader.toInt())
        out.write(0) // reserved
        out.write(0) // architecture: 0 = little-endian
        // Global message number (little-endian uint16)
        out.write(globalMesgNum and 0xFF)
        out.write((globalMesgNum shr 8) and 0xFF)
        // Number of fields
        out.write(fields.size)
        // Field definitions: 3 bytes each (fieldDefNum, size, baseType)
        for (f in fields) {
            out.write(f.fieldDefNum)
            out.write(f.size)
            out.write(f.baseType)
        }

        // Data message record header: bit6=0 (data), bits 0-3 = local mesg num
        val dataHeader = (localMesgNum and 0x0F).toByte()
        out.write(dataHeader.toInt())
        // Field values
        for (f in fields) {
            out.write(f.value)
        }
    }

    // --- FileId Message (mesg 0) ---
    private fun writeFileIdMessage(out: OutputStream, ride: RideData) {
        val fields = mutableListOf<FieldEntry>()
        // type: field 0, enum, 1 byte — 4 = activity
        fields.add(FieldEntry(0, 1, FIT_ENUM, byteArrayOf(4)))
        // manufacturer: field 1, uint16 — 1 = Garmin (use generic)
        fields.add(FieldEntry(1, 2, FIT_UINT16, uint16Le(1)))
        // product: field 2, uint16
        fields.add(FieldEntry(2, 2, FIT_UINT16, uint16Le(1)))
        // serial_number: field 3, uint32z
        fields.add(FieldEntry(3, 4, FIT_UINT32, uint32Le(12345)))
        // time_created: field 4, uint32
        fields.add(FieldEntry(4, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.startTime).toInt())))
        writeDefinitionAndData(out, 0, MESG_FILE_ID, fields)
    }

    // --- Record Message (mesg 20) — one per track point ---
    private fun writeRecordMessage(out: OutputStream, tp: TrackPoint) {
        val fields = mutableListOf<FieldEntry>()
        // timestamp: field 253, uint32
        fields.add(FieldEntry(253, 4, FIT_UINT32, uint32Le(fitTimestamp(tp.timestamp).toInt())))
        // position_lat: field 0, sint32 (semicircles)
        fields.add(FieldEntry(0, 4, FIT_SINT32, sint32Le(degreesToSemicircles(tp.latitude))))
        // position_long: field 1, sint32 (semicircles)
        fields.add(FieldEntry(1, 4, FIT_SINT32, sint32Le(degreesToSemicircles(tp.longitude))))
        // altitude: field 2, uint16 — (altitude + 500) * 5, in units of 1/5 m with 500m offset
        val altScaled = ((tp.altitudeM + 500.0) * 5.0).toInt().coerceIn(0, 65535)
        fields.add(FieldEntry(2, 2, FIT_UINT16, uint16Le(altScaled)))
        // heart_rate: field 3, uint8
        fields.add(FieldEntry(3, 1, FIT_UINT8, byteArrayOf((tp.heartRateBpm ?: 0xFF).toByte())))
        // cadence: field 4, uint8
        fields.add(FieldEntry(4, 1, FIT_UINT8, byteArrayOf((tp.cadenceRpm ?: 0xFF).toByte())))
        // distance: field 5, uint32 — in units of 1/100 m
        val distCm = (tp.cumulativeDistanceKm * 100_000.0).toLong().coerceAtLeast(0)
        fields.add(FieldEntry(5, 4, FIT_UINT32, uint32Le(distCm.toInt())))
        // speed: field 6, uint16 — in units of 1/1000 m/s
        val speedMmps = ((tp.speedKmh / 3.6) * 1000.0).toInt().coerceAtLeast(0)
        fields.add(FieldEntry(6, 2, FIT_UINT16, uint16Le(speedMmps)))
        // power: field 7, uint16 — watts
        fields.add(FieldEntry(7, 2, FIT_UINT16, uint16Le(tp.powerW.toInt().coerceAtLeast(0))))
        writeDefinitionAndData(out, 1, MESG_RECORD, fields)
    }

    // --- Lap Message (mesg 19) ---
    private fun writeLapMessage(out: OutputStream, lap: LapData) {
        val fields = mutableListOf<FieldEntry>()
        // timestamp: field 253
        fields.add(FieldEntry(253, 4, FIT_UINT32, uint32Le(fitTimestamp(lap.endTime).toInt())))
        // start_time: field 2
        fields.add(FieldEntry(2, 4, FIT_UINT32, uint32Le(fitTimestamp(lap.startTime).toInt())))
        // total_elapsed_time: field 7, uint32 — in ms (scaled 1/1000)
        fields.add(FieldEntry(7, 4, FIT_UINT32, uint32Le((lap.elapsedTime.toMillis()).toInt())))
        // total_timer_time: field 8, uint32 — same as elapsed for simplicity
        fields.add(FieldEntry(8, 4, FIT_UINT32, uint32Le((lap.elapsedTime.toMillis()).toInt())))
        // total_distance: field 9, uint32 — in 1/100 m
        fields.add(FieldEntry(9, 4, FIT_UINT32, uint32Le((lap.distanceKm * 100_000.0).toInt())))
        // avg_power: field 19, uint16
        fields.add(FieldEntry(19, 2, FIT_UINT16, uint16Le(lap.averagePowerW.toInt())))
        writeDefinitionAndData(out, 2, MESG_LAP, fields)
    }

    private fun writeWholeLapMessage(out: OutputStream, ride: RideData) {
        val fields = mutableListOf<FieldEntry>()
        fields.add(FieldEntry(253, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.endTime).toInt())))
        fields.add(FieldEntry(2, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.startTime).toInt())))
        val elapsedMs = ride.elapsedDuration.toMillis().toInt()
        fields.add(FieldEntry(7, 4, FIT_UINT32, uint32Le(elapsedMs)))
        fields.add(FieldEntry(8, 4, FIT_UINT32, uint32Le(elapsedMs)))
        fields.add(FieldEntry(9, 4, FIT_UINT32, uint32Le((ride.totalDistanceKm * 100_000.0).toInt())))
        fields.add(FieldEntry(19, 2, FIT_UINT16, uint16Le(ride.averagePowerW.toInt())))
        writeDefinitionAndData(out, 2, MESG_LAP, fields)
    }

    // --- Session Message (mesg 18) ---
    private fun writeSessionMessage(out: OutputStream, ride: RideData) {
        val fields = mutableListOf<FieldEntry>()
        // timestamp
        fields.add(FieldEntry(253, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.endTime).toInt())))
        // start_time
        fields.add(FieldEntry(2, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.startTime).toInt())))
        // total_elapsed_time (ms)
        fields.add(FieldEntry(7, 4, FIT_UINT32, uint32Le(ride.elapsedDuration.toMillis().toInt())))
        // total_timer_time (ms)
        fields.add(FieldEntry(8, 4, FIT_UINT32, uint32Le(ride.elapsedDuration.toMillis().toInt())))
        // total_distance (1/100 m)
        fields.add(FieldEntry(9, 4, FIT_UINT32, uint32Le((ride.totalDistanceKm * 100_000.0).toInt())))
        // total_calories
        fields.add(FieldEntry(11, 2, FIT_UINT16, uint16Le(ride.caloriesKcal.toInt())))
        // avg_speed (1/1000 m/s)
        val avgSpeedMmps = ((ride.averageSpeedKmh / 3.6) * 1000.0).toInt()
        fields.add(FieldEntry(14, 2, FIT_UINT16, uint16Le(avgSpeedMmps)))
        // max_speed
        val maxSpeedMmps = ((ride.maxSpeedKmh / 3.6) * 1000.0).toInt()
        fields.add(FieldEntry(15, 2, FIT_UINT16, uint16Le(maxSpeedMmps)))
        // avg_power
        fields.add(FieldEntry(20, 2, FIT_UINT16, uint16Le(ride.averagePowerW.toInt())))
        // total_ascent (m)
        fields.add(FieldEntry(22, 2, FIT_UINT16, uint16Le(ride.totalElevationGainM.toInt())))
        // sport: field 5, enum — 2 = cycling
        fields.add(FieldEntry(5, 1, FIT_ENUM, byteArrayOf(2)))
        // sub_sport: field 6, enum — 0 = generic
        fields.add(FieldEntry(6, 1, FIT_ENUM, byteArrayOf(0)))
        // num_laps
        val numLaps = if (ride.laps.isEmpty()) 1 else ride.laps.size
        fields.add(FieldEntry(26, 2, FIT_UINT16, uint16Le(numLaps)))
        writeDefinitionAndData(out, 3, MESG_SESSION, fields)
    }

    // --- Activity Message (mesg 34) ---
    private fun writeActivityMessage(out: OutputStream, ride: RideData) {
        val fields = mutableListOf<FieldEntry>()
        // timestamp
        fields.add(FieldEntry(253, 4, FIT_UINT32, uint32Le(fitTimestamp(ride.endTime).toInt())))
        // total_timer_time (1/1000 s)
        fields.add(FieldEntry(0, 4, FIT_UINT32, uint32Le(ride.elapsedDuration.toMillis().toInt())))
        // num_sessions: field 1, uint16
        fields.add(FieldEntry(1, 2, FIT_UINT16, uint16Le(1)))
        // type: field 2, enum — 0 = manual
        fields.add(FieldEntry(2, 1, FIT_ENUM, byteArrayOf(0)))
        // event: field 3, enum — 26 = activity
        fields.add(FieldEntry(3, 1, FIT_ENUM, byteArrayOf(26)))
        // event_type: field 4, enum — 1 = stop
        fields.add(FieldEntry(4, 1, FIT_ENUM, byteArrayOf(1)))
        writeDefinitionAndData(out, 4, MESG_ACTIVITY, fields)
    }

    // --- CRC calculation (FIT uses CRC-16/CCITT) ---
    private fun calculateCrc(data: ByteArray): Int {
        val crcTable = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400
        )
        var crc = 0
        for (byte in data) {
            val b = byte.toInt() and 0xFF
            // Low nibble
            var tmp = crcTable[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor crcTable[b and 0xF]
            // High nibble
            tmp = crcTable[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor crcTable[(b shr 4) and 0xF]
        }
        return crc
    }

    // --- Byte encoding helpers ---
    private fun uint16Le(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    private fun uint32Le(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun sint32Le(value: Int): ByteArray = uint32Le(value)

    private data class FieldEntry(
        val fieldDefNum: Int,
        val size: Int,
        val baseType: Int,
        val value: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FieldEntry) return false
            return fieldDefNum == other.fieldDefNum && size == other.size &&
                    baseType == other.baseType && value.contentEquals(other.value)
        }
        override fun hashCode(): Int {
            var result = fieldDefNum
            result = 31 * result + size
            result = 31 * result + baseType
            result = 31 * result + value.contentHashCode()
            return result
        }
    }
}
