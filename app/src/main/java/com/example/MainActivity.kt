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
import com.example.audio.GlobalSystemEqEngine
import com.example.audio.MediaPlaybackForegroundService
import com.example.ui.EqualizerScreen
import com.example.ui.theme.PulseEqTheme

class MainActivity : ComponentActivity() {

    private lateinit var globalEqEngine: GlobalSystemEqEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Initialize Global System EQ Engine with maximum priority (Session 0 + MLBB/media sessions)
        globalEqEngine = GlobalSystemEqEngine.getInstance(this)
        globalEqEngine.start()

        // Start Persistent Foreground Equalizer Service for Infinix Hot 60 5G battery saver & background protection
        EqualizerService.start(this)

        // Start Foreground Media Service for continuous background playback protection
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
     * Dual-Target Global System-Wide Equalizer Hook (DynamicsProcessing Session 0 + Active Game Sessions)
     * mirroring active EQ and limiter settings to MLBB, Spotify, YouTube, etc.
     */
    fun toggleGlobalSystemWideEq(enabled: Boolean, preampDb: Float = 0f, limiterThresholdDb: Float = -0.3f) {
        globalEqEngine.setEnabled(enabled)
        globalEqEngine.setPreamp(preampDb)
        globalEqEngine.setLimiterThreshold(limiterThresholdDb)
    }

    /**
     * One-tap Mobile Legends: Bang Bang Competitive Clarity trigger
     */
    fun applyMlbbCompetitiveClarity() {
        globalEqEngine.applyMlbbPreset()
    }

    override fun onDestroy() {
        // Note: GlobalSystemEqEngine remains alive inside EqualizerService while in the background
        super.onDestroy()
    }
}
