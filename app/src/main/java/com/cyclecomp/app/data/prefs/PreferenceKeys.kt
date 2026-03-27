package com.cyclecomp.app.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val RIDER_WEIGHT_KG = doublePreferencesKey("rider_weight_kg")
    val BIKE_WEIGHT_KG = doublePreferencesKey("bike_weight_kg")
    val FTP_W = intPreferencesKey("ftp_w")
    val NIGHT_MODE = booleanPreferencesKey("night_mode")
    val LARGE_FONT = booleanPreferencesKey("large_font")
    val PAIRED_DEVICES_JSON = stringPreferencesKey("paired_devices")
    val STRAVA_ACCESS_TOKEN = stringPreferencesKey("strava_access_token")
    val STRAVA_REFRESH_TOKEN = stringPreferencesKey("strava_refresh_token")
    val STRAVA_TOKEN_EXPIRY = longPreferencesKey("strava_token_expiry")
}
