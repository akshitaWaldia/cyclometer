package com.cyclecomp.app.domain.sensor

import com.cyclecomp.app.domain.model.HeartRateZone
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

// Feature: cycling-computer, Property 4: Heart Rate Zone Classification
// **Validates: Requirements 2.3**
@OptIn(ExperimentalKotest::class)
class HeartRateZoneTest : StringSpec({

    "for any HR 0-220 bpm, fromBpm returns exactly one zone whose range contains the input" {
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..220)) { bpm ->
            val zone = HeartRateZone.fromBpm(bpm)
            // The returned zone's range must contain the input bpm
            (bpm in zone.range) shouldBe true
            // Exactly one zone should match — verify no other zone also contains this bpm
            val matchingZones = HeartRateZone.entries.filter { bpm in it.range }
            matchingZones.size shouldBe 1
            matchingZones.first() shouldBe zone
        }
    }
})
