package com.cyclecomp.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.ui.theme.ThemeEngine
import com.cyclecomp.app.ui.theme.ThemeEngineImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        dataStore: DataStore<Preferences>
    ): UserPreferencesRepository {
        return UserPreferencesRepository(dataStore)
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @Provides
    @Singleton
    fun provideThemeEngine(
        prefsRepository: UserPreferencesRepository,
        scope: CoroutineScope
    ): ThemeEngine {
        return ThemeEngineImpl(prefsRepository, scope)
    }
}
