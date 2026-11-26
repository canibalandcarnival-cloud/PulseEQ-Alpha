package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thread-safe registry of active third-party media audio sessions captured via
 * AudioEffect control session broadcasts (e.g., Spotify, YouTube, Apple Music).
 */
object AudioSessionRegistry {
    private val _activeSessions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeSessions: StateFlow<Map<Int, String>> = _activeSessions.asStateFlow()

    fun onSessionOpened(sessionId: Int, packageName: String) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            current + (sessionId to packageName)
        }
    }

    fun onSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            current - sessionId
        }
    }

    fun clearAll() {
        _activeSessions.value = emptyMap()
    }
}

/**
 * BroadcastReceiver capturing AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
 * and AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to attach DynamicsProcessing
 * to individual media app audio sessions automatically.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(
            AudioEffect.EXTRA_AUDIO_SESSION,
            AudioEffect.ERROR_BAD_VALUE
        )
        if (sessionId <= 0) return

        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "media.app"

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioSessionRegistry.onSessionOpened(sessionId, packageName)
                com.example.audio.GlobalSystemEqEngine.getInstance(context).attachSession(sessionId, packageName)
                EqualizerService.notifySessionsUpdated(context)
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioSessionRegistry.onSessionClosed(sessionId)
                com.example.audio.GlobalSystemEqEngine.getInstance(context).detachSession(sessionId)
                EqualizerService.notifySessionsUpdated(context)
            }
        }
    }
}
