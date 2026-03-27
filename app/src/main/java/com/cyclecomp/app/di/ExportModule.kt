package com.cyclecomp.app.di

import com.cyclecomp.app.data.export.FitExporter
import com.cyclecomp.app.data.export.GpxExporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExportModule {

    @Provides
    @Singleton
    fun provideFitExporter(): FitExporter {
        return FitExporter()
    }

    @Provides
    @Singleton
    fun provideGpxExporter(): GpxExporter {
        return GpxExporter()
    }
}
