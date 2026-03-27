package com.cyclecomp.app.domain.sync

import android.app.Activity
import com.cyclecomp.app.domain.model.SyncState
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles Strava OAuth authentication and FIT file upload.
 */
interface StravaSyncService {
    val syncState: StateFlow<SyncState>
    val isConnected: StateFlow<Boolean>

    /** Launch OAuth 2.0 PKCE flow via AppAuth in a Chrome Custom Tab. */
    suspend fun authenticate(activity: Activity): Result<Unit>

    /** Upload a FIT file to Strava. Auto-refreshes token on 401. */
    suspend fun upload(fitData: ByteArray, rideName: String): Result<StravaUploadResult>

    /** Disconnect: clear stored tokens. */
    suspend fun disconnect()
}

data class StravaUploadResult(
    val uploadId: Long,
    val status: String,
    val error: String? = null
)
