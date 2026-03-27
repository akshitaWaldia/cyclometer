package com.cyclecomp.app.domain.ride

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoPauseControllerImpl @Inject constructor(
    private val scope: CoroutineScope
) : AutoPauseController {

    companion object {
        const val SPEED_THRESHOLD_KMH = 2.0
        const val PAUSE_DELAY_MS = 3000L
    }

    private val _isAutoPaused = MutableStateFlow(false)
    override val isAutoPaused: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

    private var pauseTimerJob: Job? = null
    private var hasEverMoved = false

    override fun onSpeedUpdate(speedKmh: Double) {
        if (speedKmh > SPEED_THRESHOLD_KMH) {
            hasEverMoved = true
            // Speed above threshold — cancel any pending pause and resume immediately
            pauseTimerJob?.cancel()
            pauseTimerJob = null
            if (_isAutoPaused.value) {
                _isAutoPaused.value = false
            }
        } else {
            // Only auto-pause if the rider has moved at least once
            // This prevents immediate pause when GPS hasn't locked yet
            if (hasEverMoved && !_isAutoPaused.value && pauseTimerJob == null) {
                pauseTimerJob = scope.launch {
                    delay(PAUSE_DELAY_MS)
                    _isAutoPaused.value = true
                    pauseTimerJob = null
                }
            }
        }
    }

    override fun reset() {
        pauseTimerJob?.cancel()
        pauseTimerJob = null
        _isAutoPaused.value = false
        hasEverMoved = false
    }
}
