package com.cyclecomp.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cyclecomp.app.domain.sync.StravaSyncServiceImpl
import com.cyclecomp.app.ui.navigation.CycleCompNavHost
import com.cyclecomp.app.ui.theme.CycleCompTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var stravaSyncService: com.cyclecomp.app.domain.sync.StravaSyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CycleComp", "MainActivity.onCreate started")

        // Handle Strava OAuth callback
        handleStravaCallback(intent)

        try {
            setContent {
                CycleCompTheme(
                    nightMode = true,
                    largeFontEnabled = false
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CycleCompNavHost()
                    }
                }
            }
            Log.d("CycleComp", "MainActivity.onCreate completed")
        } catch (e: Exception) {
            Log.e("CycleComp", "Crash in onCreate", e)
            setContent {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${e.message}")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStravaCallback(intent)
    }

    private fun handleStravaCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "com.cyclecomp.app" && uri.host == "strava-callback") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d("CycleComp", "Strava auth code received: ${code.take(5)}...")
                CoroutineScope(Dispatchers.Main).launch {
                    (stravaSyncService as? StravaSyncServiceImpl)?.handleAuthCode(code)
                }
            } else {
                val error = uri.getQueryParameter("error")
                Log.e("CycleComp", "Strava auth error: $error")
            }
        }
    }
}
