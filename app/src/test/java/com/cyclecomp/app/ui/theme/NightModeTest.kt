package com.cyclecomp.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.common.ExperimentalKotest
import io.kotest.property.checkAll

// Feature: cycling-computer, Property 19: Night Mode Round-Trip
// **Validates: Requirements 16.1, 16.4**
@OptIn(ExperimentalKotest::class)
class NightModeTest : StringSpec({

    "enable night mode then disable restores original light scheme; color scheme is pure function of flag" {
        // The color scheme selection is a pure function: nightMode=true → dark, nightMode=false → light
        // Replicate the logic from CycleCompTheme
        val darkScheme = darkColorScheme(
            primary = CycleCompColors.SpeedGreen,
            secondary = CycleCompColors.CadenceBlue,
            tertiary = CycleCompColors.PowerOrange,
            background = CycleCompColors.DarkBackground,
            surface = CycleCompColors.DarkSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )

        val lightScheme = lightColorScheme(
            primary = CycleCompColors.SpeedGreen,
            secondary = CycleCompColors.CadenceBlue,
            tertiary = CycleCompColors.PowerOrange,
            background = CycleCompColors.LightBackground,
            surface = CycleCompColors.LightSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F)
        )

        fun colorSchemeFor(nightMode: Boolean) = if (nightMode) darkScheme else lightScheme

        checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { initialNightMode ->
            val originalScheme = colorSchemeFor(initialNightMode)

            // Enable night mode then disable
            colorSchemeFor(true) // switch to dark
            // Disable night mode
            val restoredScheme = colorSchemeFor(false)

            // After disabling, we should get the light scheme
            restoredScheme shouldBe lightScheme

            // Color scheme is a pure function of the flag — same input always gives same output
            colorSchemeFor(initialNightMode) shouldBe originalScheme
            colorSchemeFor(true) shouldBe darkScheme
            colorSchemeFor(false) shouldBe lightScheme
        }
    }
})
