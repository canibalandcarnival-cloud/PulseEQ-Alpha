package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.audio.EqualizerAudioEngine
import com.example.audio.EqualizerEngineHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Persistent foreground mediaPlayback service that keeps DynamicsProcessing attached
 * across captured app audio sessions (and optional Session 0 Global Mix fallback)
 * while the user listens in Spotify, YouTube, or other apps.
 */
class EqualizerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioEngine: EqualizerAudioEngine
    private val sessionReceiver = AudioSessionReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        audioEngine = EqualizerEngineHolder.get(this)
        createNotificationChannel()
        registerSessionReceiver()

        startInForeground(buildNotification("10-Band EQ & Dynamics active"))
        _isRunning.value = true

        serviceScope.launch {
            combine(
                AudioSessionRegistry.activeSessions,
                audioEngine.dspStatusLabel,
                audioEngine.connectedDevices
            ) { sessions, status, devices ->
                Triple(sessions, status, devices)
            }.collect { (sessions, status, devices) ->
                val sessionDesc = if (sessions.isEmpty()) {
                    if (audioEngine.globalMixFallbackEnabled.value) "Global Mix (Session 0)" else "Waiting for media app"
                } else {
                    sessions.values.distinct().joinToString(", ") { pkg ->
                        pkg.substringAfterLast('.')
                    }
                }
                val content = "${devices.activeRoute.label} • $status • $sessionDesc"
                updateNotification(content)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_BYPASS -> {
                audioEngine.applyParameters(
                    target = audioEngine.currentTarget,
                    gainsDb = audioEngine.currentGainsDb,
                    preampDb = audioEngine.currentPreampDb,
                    isBypassed = !audioEngine.currentBypassed,
                    lateNightEnabled = audioEngine.currentLateNightEnabled,
                    nightIntensity = audioEngine.currentNightIntensity,
                    speakerDspEnabled = audioEngine.currentSpeakerDspEnabled,
                    virtualBassEnabled = audioEngine.currentVirtualBassEnabled,
                    spatialMode = audioEngine.currentSpatialMode,
                    spatialDepth = audioEngine.currentSpatialDepth,
                    heightPresence = audioEngine.currentHeightPresence,
                    vocalAnchor = audioEngine.currentVocalAnchor,
                    soundCheckEnabled = audioEngine.currentSoundCheckEnabled,
                    conversationAwarenessEnabled = audioEngine.currentConversationAwarenessEnabled,
                    bgSoundPreset = audioEngine.currentBgSoundPreset,
                    bgSoundVolume = audioEngine.currentBgSoundVolume,
                    voiceIsolationEnabled = audioEngine.currentVoiceIsolationEnabled,
                    threeBandEnabled = audioEngine.currentThreeBandEnabled,
                    threeBandBassDb = audioEngine.currentThreeBandBassDb,
                    threeBandMidsDb = audioEngine.currentThreeBandMidsDb,
                    threeBandTrebleDb = audioEngine.currentThreeBandTrebleDb,
                    accommodationsEnabled = audioEngine.currentAccommodationsEnabled,
                    accommodationsToneProfile = audioEngine.currentAccommodationsToneProfile,
                    accommodationsSoftIntensity = audioEngine.currentAccommodationsSoftIntensity,
                    accommodationsApplyToMedia = audioEngine.currentAccommodationsApplyToMedia,
                    accommodationsApplyToVoice = audioEngine.currentAccommodationsApplyToVoice,
                    loudnessMaximizerEnabled = audioEngine.currentLoudnessMaximizerEnabled,
                    loudnessBoostPercent = audioEngine.currentLoudnessBoostPercent
                )
            }

            ACTION_SESSIONS_UPDATED -> {
                audioEngine.reconcileAttachedSessions()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isRunning.value = false
        unregisterSessionReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerSessionReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(
            this,
            sessionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterSessionReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(sessionReceiver)
        } catch (_: Throwable) {
        }
        receiverRegistered = false
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bypassIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EqualizerService::class.java).apply {
                action = ACTION_TOGGLE_BYPASS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, EqualizerService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bypassLabel = if (audioEngine.currentBypassed) "Resume EQ" else "Bypass EQ"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("PulseEQ System Equalizer")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, bypassLabel, bypassIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Equalizer Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps 10-band EQ and Late Night Dynamics active during playback"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pulse_eq_engine"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.ACTION_STOP_EQ_SERVICE"
        const val ACTION_TOGGLE_BYPASS = "com.example.ACTION_TOGGLE_EQ_BYPASS"
        const val ACTION_SESSIONS_UPDATED = "com.example.ACTION_SESSIONS_UPDATED"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, EqualizerService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EqualizerService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
            }
        }

        fun notifySessionsUpdated(context: Context) {
            if (!_isRunning.value) return
            val intent = Intent(context, EqualizerService::class.java).apply {
                action = ACTION_SESSIONS_UPDATED
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
