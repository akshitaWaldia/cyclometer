package com.cyclecomp.app.domain.sync

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.cyclecomp.app.BuildConfig
import com.cyclecomp.app.data.prefs.UserPreferencesRepository
import com.cyclecomp.app.domain.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class StravaSyncServiceImpl @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : StravaSyncService {

    companion object {
        private const val TAG = "StravaSyncService"
        private const val AUTH_ENDPOINT = "https://www.strava.com/oauth/authorize"
        private const val TOKEN_ENDPOINT = "https://www.strava.com/oauth/token"
        private const val UPLOAD_URL = "https://www.strava.com/api/v3/uploads"
        private const val REDIRECT_URI = "com.cyclecomp.app://strava-callback"
        private const val SCOPE = "activity:write"
    }

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Check if we already have tokens
        // This will be updated when tokens are loaded
    }

    override suspend fun authenticate(activity: Activity): Result<Unit> {
        _syncState.value = SyncState.AUTHENTICATING
        return try {
            val clientId = BuildConfig.STRAVA_CLIENT_ID
            if (clientId.isBlank() || clientId == "YOUR_STRAVA_CLIENT_ID_HERE") {
                _syncState.value = SyncState.FAILED
                return Result.failure(IllegalStateException("Strava Client ID not configured"))
            }

            // Build Strava OAuth URL directly (no AppAuth — Strava doesn't like extra params)
            val authUrl = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("approval_prompt", "auto")
                .build()

            // Open in browser
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUrl)
            activity.startActivity(intent)

            _syncState.value = SyncState.IDLE
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            _syncState.value = SyncState.FAILED
            Result.failure(e)
        }
    }

    /**
     * Handle the OAuth callback with auth code from redirect URI.
     */
    suspend fun handleAuthCode(code: String): Result<Unit> {
        return try {
            _syncState.value = SyncState.AUTHENTICATING
            val tokenResult = exchangeCodeForTokens(code)
            if (tokenResult.isSuccess) {
                _isConnected.value = true
                _syncState.value = SyncState.IDLE
                Result.success(Unit)
            } else {
                _syncState.value = SyncState.FAILED
                Result.failure(tokenResult.exceptionOrNull() ?: Exception("Token exchange failed"))
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Result.failure(e)
        }
    }

    /**
     * Handle the OAuth callback. Called from the activity's onActivityResult.
     */
    suspend fun handleAuthResponse(
        response: AuthorizationResponse?,
        exception: AuthorizationException?
    ): Result<Unit> {
        if (exception != null || response == null) {
            _syncState.value = SyncState.FAILED
            return Result.failure(exception ?: IllegalStateException("No auth response"))
        }

        return try {
            _syncState.value = SyncState.AUTHENTICATING
            // Exchange authorization code for tokens
            val tokenResult = exchangeCodeForTokens(response.authorizationCode!!)
            if (tokenResult.isSuccess) {
                _isConnected.value = true
                _syncState.value = SyncState.IDLE
                Result.success(Unit)
            } else {
                _syncState.value = SyncState.FAILED
                Result.failure(tokenResult.exceptionOrNull() ?: Exception("Token exchange failed"))
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForTokens(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val clientId = BuildConfig.STRAVA_CLIENT_ID
            val clientSecret = BuildConfig.STRAVA_CLIENT_SECRET
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("client_id", clientId)
                .addFormDataPart("client_secret", clientSecret)
                .addFormDataPart("code", code)
                .addFormDataPart("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Token exchange failed: ${response.code}"))
            }

            val json = JSONObject(response.body?.string() ?: "")
            val accessToken = json.getString("access_token")
            val refreshToken = json.getString("refresh_token")
            val expiresAt = json.getLong("expires_at")

            userPreferencesRepository.setStravaTokens(accessToken, refreshToken, expiresAt)
            _isConnected.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            Result.failure(e)
        }
    }

    override suspend fun upload(fitData: ByteArray, rideName: String): Result<StravaUploadResult> {
        _syncState.value = SyncState.UPLOADING
        return try {
            var accessToken = userPreferencesRepository.stravaAccessToken.first()
            if (accessToken.isNullOrBlank()) {
                _syncState.value = SyncState.FAILED
                return Result.failure(IllegalStateException("Not authenticated with Strava"))
            }

            // Check token expiry and refresh if needed
            val expiry = userPreferencesRepository.stravaTokenExpiry.first() ?: 0L
            if (System.currentTimeMillis() / 1000 >= expiry) {
                val refreshResult = refreshToken()
                if (refreshResult.isFailure) {
                    _syncState.value = SyncState.FAILED
                    return Result.failure(refreshResult.exceptionOrNull()!!)
                }
                accessToken = userPreferencesRepository.stravaAccessToken.first()
            }

            val result = doUpload(fitData, rideName, accessToken!!)

            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                // If 401, try refresh and retry once
                if (ex is StravaHttpException && ex.code == 401) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        val newToken = userPreferencesRepository.stravaAccessToken.first()!!
                        val retryResult = doUpload(fitData, rideName, newToken)
                        if (retryResult.isSuccess) {
                            _syncState.value = SyncState.SUCCESS
                            return retryResult
                        }
                    }
                }
                _syncState.value = SyncState.FAILED
                return result
            }

            _syncState.value = SyncState.SUCCESS
            result
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            _syncState.value = SyncState.FAILED
            Result.failure(e)
        }
    }

    private suspend fun doUpload(
        fitData: ByteArray,
        rideName: String,
        accessToken: String
    ): Result<StravaUploadResult> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data_type", "fit")
                .addFormDataPart("activity_type", "ride")
                .addFormDataPart("name", rideName)
                .addFormDataPart(
                    "file",
                    "ride.fit",
                    fitData.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // Check for duplicate upload (Strava returns 409 or error with "duplicate")
                if (response.code == 409 || responseBody.contains("duplicate", ignoreCase = true)) {
                    return@withContext Result.success(
                        StravaUploadResult(
                            uploadId = 0,
                            status = "duplicate",
                            error = "Activity already uploaded"
                        )
                    )
                }
                return@withContext Result.failure(
                    StravaHttpException(response.code, "Upload failed: ${response.code} $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            Result.success(
                StravaUploadResult(
                    uploadId = json.optLong("id", 0),
                    status = json.optString("status", "unknown")
                )
            )
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun refreshToken(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = userPreferencesRepository.stravaRefreshToken.first()
            if (refreshToken.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("No refresh token"))
            }

            val clientId = BuildConfig.STRAVA_CLIENT_ID
            val clientSecret = BuildConfig.STRAVA_CLIENT_SECRET
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("client_id", clientId)
                .addFormDataPart("client_secret", clientSecret)
                .addFormDataPart("refresh_token", refreshToken)
                .addFormDataPart("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Token refresh failed: ${response.code}"))
            }

            val json = JSONObject(response.body?.string() ?: "")
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.getString("refresh_token")
            val expiresAt = json.getLong("expires_at")

            userPreferencesRepository.setStravaTokens(newAccessToken, newRefreshToken, expiresAt)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        userPreferencesRepository.clearStravaTokens()
        _isConnected.value = false
        _syncState.value = SyncState.IDLE
    }

    /** Call this on init to check if tokens exist */
    suspend fun checkConnectionStatus() {
        val token = userPreferencesRepository.stravaAccessToken.first()
        _isConnected.value = !token.isNullOrBlank()
    }
}

class StravaHttpException(val code: Int, message: String) : IOException(message)

const val STRAVA_AUTH_REQUEST_CODE = 9001
