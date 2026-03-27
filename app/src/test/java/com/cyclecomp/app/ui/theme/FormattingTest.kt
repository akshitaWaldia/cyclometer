package com.cyclecomp.app.ui.theme

import com.cyclecomp.app.domain.model.formatDistanceKm
import com.cyclecomp.app.domain.model.parseDistanceKm
import com.cyclecomp.app.domain.model.parseHhMmSs
import com.cyclecomp.app.domain.model.toHhMmSs
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.long
import io.kotest.common.ExperimentalKotest
import io.kotest.property.checkAll
import kotlin.time.Duration.Companion.seconds

// Feature: cycling-computer, Property 12: Duration and Distance Formatting Round-Trip
// **Validates: Requirements 7.2, 7.4**
@OptIn(ExperimentalKotest::class)
class FormattingTest : StringSpec({

    "format duration to HH:MM:SS and parse back produces same duration truncated to seconds" {
        checkAll(PropTestConfig(iterations = 100), Arb.long(0L..359999L)) { totalSeconds ->
            val duration = totalSeconds.seconds
            val formatted = duration.toHhMmSs()
            val parsed = parseHhMmSs(formatted)
            // Round-trip should produce the same duration (truncated to whole seconds)
            parsed.inWholeSeconds shouldBe duration.inWholeSeconds
        }
    }

    "format distance to 2dp and parse back is within 0.005 km" {
        checkAll(PropTestConfig(iterations = 100), Arb.double(0.0..9999.99)) { distanceKm ->
            val formatted = formatDistanceKm(distanceKm)
            val parsed = parseDistanceKm(formatted)
            parsed shouldBe (distanceKm plusOrMinus 0.005)
        }
    }
})
