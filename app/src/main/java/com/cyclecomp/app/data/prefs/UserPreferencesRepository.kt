package com.cyclecomp.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.cyclecomp.app.domain.model.RiderProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val riderProfile: Flow<RiderProfile> = dataStore.data.map { prefs ->
        RiderProfile(
            riderWeightKg = prefs[PreferenceKeys.RIDER_WEIGHT_KG] ?: 75.0,
            bikeWeightKg = prefs[PreferenceKeys.BIKE_WEIGHT_KG] ?: 9.0,
            ftpW = prefs[PreferenceKeys.FTP_W] ?: 200
        )
    }

    val nightMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.NIGHT_MODE] ?: false
    }

    val largeFont: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.LARGE_FONT] ?: false
    }

    val pairedDevicesJson: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.PAIRED_DEVICES_JSON]
    }

    val stravaAccessToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.STRAVA_ACCESS_TOKEN]
    }

    val stravaRefreshToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.STRAVA_REFRESH_TOKEN]
    }

    val stravaTokenExpiry: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.STRAVA_TOKEN_EXPIRY]
    }

    suspend fun updateRiderProfile(profile: RiderProfile) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.RIDER_WEIGHT_KG] = profile.riderWeightKg
            prefs[PreferenceKeys.BIKE_WEIGHT_KG] = profile.bikeWeightKg
            prefs[PreferenceKeys.FTP_W] = profile.ftpW
        }
    }

    suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.NIGHT_MODE] = enabled
        }
    }

    suspend fun setLargeFont(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.LARGE_FONT] = enabled
        }
    }

    suspend fun setPairedDevicesJson(json: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PAIRED_DEVICES_JSON] = json
        }
    }

    suspend fun setStravaTokens(
        accessToken: String,
        refreshToken: String,
        expiryEpochSeconds: Long
    ) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.STRAVA_ACCESS_TOKEN] = accessToken
            prefs[PreferenceKeys.STRAVA_REFRESH_TOKEN] = refreshToken
            prefs[PreferenceKeys.STRAVA_TOKEN_EXPIRY] = expiryEpochSeconds
        }
    }

    suspend fun clearStravaTokens() {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.STRAVA_ACCESS_TOKEN)
            prefs.remove(PreferenceKeys.STRAVA_REFRESH_TOKEN)
            prefs.remove(PreferenceKeys.STRAVA_TOKEN_EXPIRY)
        }
    }
}
