package com.cyclecomp.app.ui.theme

import kotlinx.coroutines.flow.StateFlow

interface ThemeEngine {
    val nightMode: StateFlow<Boolean>
    val largeFontEnabled: StateFlow<Boolean>

    suspend fun setNightMode(enabled: Boolean)
    suspend fun setLargeFont(enabled: Boolean)
}
