package com.cyclecomp.app.di

import android.content.Context
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.ble.BleManagerImpl
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context,
        prefsRepository: UserPreferencesRepository,
        scope: CoroutineScope
    ): BleManager {
        return BleManagerImpl(context, prefsRepository, scope)
    }
}
