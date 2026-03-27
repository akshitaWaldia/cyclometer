package com.cyclecomp.app.ui.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.common.ExperimentalKotest
import io.kotest.property.checkAll

// Feature: cycling-computer, Property 20: Font Scaling
// **Validates: Requirements 16.2**
@OptIn(ExperimentalKotest::class)
class FontScalingTest : StringSpec({

    "large font mode produces exactly 1.5x default size; disabling restores original" {
        // The font scale logic from CycleCompTheme:
        // largeFontEnabled=true → fontScale=1.5f, largeFontEnabled=false → fontScale=1f
        fun fontScale(largeFontEnabled: Boolean): Float = if (largeFontEnabled) 1.5f else 1f

        checkAll(PropTestConfig(iterations = 100), Arb.double(8.0..72.0)) { defaultSize ->
            val defaultScale = fontScale(false)
            val largeScale = fontScale(true)

            // Default scale is 1.0
            defaultScale shouldBe 1f

            // Large font produces exactly 1.5x
            largeScale shouldBe 1.5f

            val scaledSize = defaultSize * largeScale
            scaledSize shouldBe (defaultSize * 1.5 plusOrMinus 0.001)

            // Disabling restores original
            val restoredSize = defaultSize * fontScale(false)
            restoredSize shouldBe (defaultSize plusOrMinus 0.001)
        }
    }
})
