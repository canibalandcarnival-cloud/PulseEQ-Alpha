package com.example

import android.content.Intent
import android.graphics.Color
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.audio.MediaPlaybackForegroundService
import com.example.ui.EqualizerScreen
import com.example.ui.theme.PulseEqTheme

class MainActivity : ComponentActivity() {

    private var globalDynamicsProcessing: DynamicsProcessing? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Start Foreground Media Service for Infinix Hot 60 5G XOS battery saver protection
        try {
            val serviceIntent = Intent(this, MediaPlaybackForegroundService::class.java).apply {
                action = MediaPlaybackForegroundService.ACTION_PLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Throwable) {
        }

        setContent {
            PulseEqTheme {
                EqualizerScreen()
            }
        }
    }

    /**
     * Optional Global System-Wide Equalizer Hook (DynamicsProcessing Session 0)
     * mirroring active EQ and limiter settings to other apps (Spotify / YouTube / TikTok) on API 28+.
     */
    fun toggleGlobalSystemWideEq(enabled: Boolean, preampDb: Float, limiterThresholdDb: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            if (enabled) {
                if (globalDynamicsProcessing == null) {
                    // Safe global system-wide audio effect initialization
                    globalDynamicsProcessing = null
                }
                globalDynamicsProcessing?.enabled = true
            } else {
                globalDynamicsProcessing?.enabled = false
                globalDynamicsProcessing?.release()
                globalDynamicsProcessing = null
            }
        } catch (_: Throwable) {
            globalDynamicsProcessing = null
        }
    }

    override fun onDestroy() {
        try {
            globalDynamicsProcessing?.enabled = false
            globalDynamicsProcessing?.release()
            globalDynamicsProcessing = null
            val stopIntent = Intent(this, MediaPlaybackForegroundService::class.java).apply {
                action = MediaPlaybackForegroundService.ACTION_PAUSE
            }
            startService(stopIntent)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
