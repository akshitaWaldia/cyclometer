package com.cyclecomp.app.di

import android.content.Context
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.domain.sync.HealthConnectWriteService
import com.cyclecomp.app.domain.sync.HealthConnectWriteServiceImpl
import com.cyclecomp.app.domain.sync.StravaSyncService
import com.cyclecomp.app.domain.sync.StravaSyncServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideStravaSyncService(
        userPreferencesRepository: UserPreferencesRepository
    ): StravaSyncService {
        return StravaSyncServiceImpl(userPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideHealthConnectWriteService(
        @ApplicationContext context: Context
    ): HealthConnectWriteService {
        return HealthConnectWriteServiceImpl(context)
    }
}
