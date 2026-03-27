package com.cyclecomp.app.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.domain.model.RiderProfile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.common.ExperimentalKotest
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first
import java.io.File

// Feature: cycling-computer, Property 21: Preference Persistence Round-Trip
// **Validates: Requirements 16.3, 18.2**
@OptIn(ExperimentalKotest::class)
class PreferencePersistenceTest : StringSpec({

    "save rider profile and theme prefs to DataStore, load back, verify identical values" {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.double(30.0..200.0),
            Arb.double(3.0..30.0),
            Arb.int(50..500),
            Arb.boolean(),
            Arb.boolean()
        ) { weight, bikeWeight, ftp, nightMode, largeFont ->
            val testFile = File.createTempFile("test_prefs_", ".preferences_pb")
            testFile.deleteOnExit()
            // Delete the file so DataStore creates it fresh
            testFile.delete()

            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                produceFile = { testFile }
            )

            val repo = UserPreferencesRepository(dataStore)

            // Save
            val profile = RiderProfile(
                riderWeightKg = weight,
                bikeWeightKg = bikeWeight,
                ftpW = ftp
            )
            repo.updateRiderProfile(profile)
            repo.setNightMode(nightMode)
            repo.setLargeFont(largeFont)

            // Load back
            val loadedProfile = repo.riderProfile.first()
            val loadedNightMode = repo.nightMode.first()
            val loadedLargeFont = repo.largeFont.first()

            loadedProfile.riderWeightKg shouldBe weight
            loadedProfile.bikeWeightKg shouldBe bikeWeight
            loadedProfile.ftpW shouldBe ftp
            loadedNightMode shouldBe nightMode
            loadedLargeFont shouldBe largeFont
        }
    }
})
