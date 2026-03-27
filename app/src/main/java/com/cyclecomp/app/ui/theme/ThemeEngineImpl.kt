package com.cyclecomp.app.ui.theme

import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeEngineImpl @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    scope: CoroutineScope
) : ThemeEngine {

    override val nightMode: StateFlow<Boolean> = prefsRepository.nightMode
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val largeFontEnabled: StateFlow<Boolean> = prefsRepository.largeFont
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun setNightMode(enabled: Boolean) {
        prefsRepository.setNightMode(enabled)
    }

    override suspend fun setLargeFont(enabled: Boolean) {
        prefsRepository.setLargeFont(enabled)
    }
}
