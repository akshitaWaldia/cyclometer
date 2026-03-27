package com.cyclecomp.app.data.export

import com.cyclecomp.app.domain.model.RideData
import com.cyclecomp.app.domain.model.TrackPoint
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates GPX 1.1 XML from ride data.
 * Includes Garmin TrackPointExtension for HR, cadence, and power.
 */
@Singleton
class GpxExporter @Inject constructor() {

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT
    }

    fun serialize(ride: RideData): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="CycleComp"""")
        sb.appendLine("""  xmlns="http://www.topografix.com/GPX/1/1"""")
        sb.appendLine("""  xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"""")
        sb.appendLine("""  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"""")
        sb.appendLine("""  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")

        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>CycleComp Ride</name>")
        sb.appendLine("    <time>${formatInstant(ride.startTime)}</time>")
        sb.appendLine("  </metadata>")

        sb.appendLine("  <trk>")
        sb.appendLine("    <name>CycleComp Ride</name>")
        sb.appendLine("    <type>cycling</type>")
        sb.appendLine("    <trkseg>")

        for (tp in ride.trackPoints) {
            writeTrackPoint(sb, tp)
        }

        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")

        return sb.toString()
    }

    private fun writeTrackPoint(sb: StringBuilder, tp: TrackPoint) {
        sb.appendLine("""      <trkpt lat="${tp.latitude}" lon="${tp.longitude}">""")
        sb.appendLine("        <ele>${tp.altitudeM}</ele>")
        sb.appendLine("        <time>${formatInstant(tp.timestamp)}</time>")

        // Extensions: HR, cadence, power
        val hasExtensions = tp.heartRateBpm != null || tp.cadenceRpm != null || tp.powerW > 0
        if (hasExtensions) {
            sb.appendLine("        <extensions>")
            val hasGarminExt = tp.heartRateBpm != null || tp.cadenceRpm != null
            if (hasGarminExt) {
                sb.appendLine("          <gpxtpx:TrackPointExtension>")
                tp.heartRateBpm?.let {
                    sb.appendLine("            <gpxtpx:hr>$it</gpxtpx:hr>")
                }
                tp.cadenceRpm?.let {
                    sb.appendLine("            <gpxtpx:cad>$it</gpxtpx:cad>")
                }
                sb.appendLine("          </gpxtpx:TrackPointExtension>")
            }
            if (tp.powerW > 0) {
                sb.appendLine("          <power>${tp.powerW.toInt()}</power>")
            }
            sb.appendLine("        </extensions>")
        }

        sb.appendLine("      </trkpt>")
    }

    private fun formatInstant(instant: java.time.Instant): String {
        return ISO_FORMATTER.format(instant)
    }
}
