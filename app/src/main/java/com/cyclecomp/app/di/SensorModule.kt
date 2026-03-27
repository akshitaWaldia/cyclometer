package com.cyclecomp.app.di

import android.content.Context
import com.cyclecomp.app.data.ble.BleManager
import com.cyclecomp.app.data.gps.GpsProvider
import com.cyclecomp.app.data.gps.GpsProviderImpl
import com.cyclecomp.app.data.health.HealthConnectHrReader
import com.cyclecomp.app.data.health.HealthConnectHrReaderImpl
import com.cyclecomp.app.data.wearable.WearableHrReceiver
import com.cyclecomp.app.data.wearable.WearableHrReceiverImpl
import com.cyclecomp.app.domain.calc.CalorieAndTssCalculator
import com.cyclecomp.app.domain.calc.CalorieAndTssCalculatorImpl
import com.cyclecomp.app.domain.ride.AutoPauseController
import com.cyclecomp.app.domain.ride.AutoPauseControllerImpl
import com.cyclecomp.app.domain.ride.LapManager
import com.cyclecomp.app.domain.ride.LapManagerImpl
import com.cyclecomp.app.domain.ride.RideRecorder
import com.cyclecomp.app.domain.ride.RideRecorderImpl
import com.cyclecomp.app.domain.sensor.PowerEstimator
import com.cyclecomp.app.domain.sensor.PowerEstimatorImpl
import com.cyclecomp.app.domain.sensor.SensorHub
import com.cyclecomp.app.domain.sensor.SensorHubImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideGpsProvider(
        @ApplicationContext context: Context
    ): GpsProvider {
        return GpsProviderImpl(context)
    }

    @Provides
    @Singleton
    fun provideHealthConnectHrReader(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): HealthConnectHrReader {
        return HealthConnectHrReaderImpl(context, scope)
    }

    @Provides
    @Singleton
    fun provideWearableHrReceiver(
        @ApplicationContext context: Context
    ): WearableHrReceiver {
        return WearableHrReceiverImpl(context)
    }

    @Provides
    @Singleton
    fun provideSensorHub(
        wearableHrReceiver: WearableHrReceiver,
        healthConnectHrReader: HealthConnectHrReader,
        bleManager: BleManager,
        gpsProvider: GpsProvider,
        scope: CoroutineScope
    ): SensorHub {
        return SensorHubImpl(wearableHrReceiver, healthConnectHrReader, bleManager, gpsProvider, scope)
    }

    @Provides
    @Singleton
    fun providePowerEstimator(): PowerEstimator {
        return PowerEstimatorImpl()
    }

    @Provides
    @Singleton
    fun provideCalorieAndTssCalculator(): CalorieAndTssCalculator {
        return CalorieAndTssCalculatorImpl()
    }

    @Provides
    @Singleton
    fun provideAutoPauseController(
        scope: CoroutineScope
    ): AutoPauseController {
        return AutoPauseControllerImpl(scope)
    }

    @Provides
    @Singleton
    fun provideLapManager(): LapManager {
        return LapManagerImpl()
    }

    @Provides
    @Singleton
    fun provideRideRecorder(
        sensorHub: SensorHub,
        powerEstimator: PowerEstimator,
        calorieAndTssCalculator: CalorieAndTssCalculator,
        gpsProvider: GpsProvider,
        scope: CoroutineScope
    ): RideRecorder {
        return RideRecorderImpl(sensorHub, powerEstimator, calorieAndTssCalculator, gpsProvider, scope)
    }
}
