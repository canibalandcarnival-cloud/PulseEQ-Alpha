package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated Global System-Wide EQ & Dynamics Processing Engine.
 *
 * Tailored for the Infinix Hot 60 5G (MediaTek Dimensity 7020 / XOS) to ensure
 * reliable system-wide DSP operation on Mobile Legends: Bang Bang (MLBB),
 * Spotify, YouTube, and global audio streams.
 *
 * Architecture Highlights:
 * 1. Dual-Target DynamicsProcessing Attachment:
 *    - Session 0: Hooks Android's global audio mixer output (system-wide HAL mix).
 *    - Active Game/Media Sessions: Intercepts game-specific audio sessions created by
 *      Unity / FMOD (MLBB, PUBG, Genshin) and media players (Spotify, YouTube) via
 *      AudioManager.AudioPlaybackCallback and AudioEffect session broadcasts.
 * 2. Maximum Priority (Int.MAX_VALUE):
 *    - Overrides manufacturer/OEM audio processing layers with zero dropout.
 * 3. MLBB Competitive Clarity Preset:
 *    - One-tap competitive gaming tuning (+1.5 dB Sub/Bass punch, +2.5 dB Mid skill/voice cues,
 *      +1.2 dB High hit-confirm air, and -0.3 dBTP brickwall limiter).
 * 4. Zero-GC / Keep-Alive Lifecycle:
 *    - Keeps all active DynamicsProcessing instances referenced in a thread-safe registry
 *      inside the ForegroundService so Android never garbage-collects or drops effects
 *      during background execution or gameplay.
 */
class GlobalSystemEqEngine private constructor(private val appContext: Context) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Thread-safe effect references protecting against background GC
    private val activeDynamicsProcessors = ConcurrentHashMap<Int, DynamicsProcessing>()
    private val activeLegacyEqualizers = ConcurrentHashMap<Int, Equalizer>()
    private val sessionPackageMap = ConcurrentHashMap<Int, String>()

    // State flows for UI and foreground notification badges
    private val _activeSessionsCount = MutableStateFlow(0)
    val activeSessionsCount: StateFlow<Int> = _activeSessionsCount.asStateFlow()

    private val _activeSessionsBadge = MutableStateFlow("Active Sessions Hooked: 0 (Standby)")
    val activeSessionsBadge: StateFlow<String> = _activeSessionsBadge.asStateFlow()

    private val _isSession0Hooked = MutableStateFlow(false)
    val isSession0Hooked: StateFlow<Boolean> = _isSession0Hooked.asStateFlow()

    private val _activeSessionPackages = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeSessionPackages: StateFlow<Map<Int, String>> = _activeSessionPackages.asStateFlow()

    private val _isEngineEnabled = MutableStateFlow(true)
    val isEngineEnabled: StateFlow<Boolean> = _isEngineEnabled.asStateFlow()

    private val _isMlbbPresetActive = MutableStateFlow(false)
    val isMlbbPresetActive: StateFlow<Boolean> = _isMlbbPresetActive.asStateFlow()

    private val _currentPreampDb = MutableStateFlow(0.0f)
    val currentPreampDb: StateFlow<Float> = _currentPreampDb.asStateFlow()

    private val _currentLimiterThresholdDb = MutableStateFlow(-0.3f)
    val currentLimiterThresholdDb: StateFlow<Float> = _currentLimiterThresholdDb.asStateFlow()

    private val _current10BandGains = MutableStateFlow(FloatArray(PRE_EQ_BAND_COUNT) { 0.0f })
    val current10BandGains: StateFlow<FloatArray> = _current10BandGains.asStateFlow()

    private val _currentBassDb = MutableStateFlow(0.0f)
    val currentBassDb: StateFlow<Float> = _currentBassDb.asStateFlow()

    private val _currentMidDb = MutableStateFlow(0.0f)
    val currentMidDb: StateFlow<Float> = _currentMidDb.asStateFlow()

    private val _currentTrebleDb = MutableStateFlow(0.0f)
    val currentTrebleDb: StateFlow<Float> = _currentTrebleDb.asStateFlow()

    // Callbacks & Receivers
    private var isStarted = false
    private var sessionReceiverRegistered = false
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null

    private val sessionBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (sessionId <= 0) return

            val rawPackage = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() }
            val resolvedPackage = resolvePackageLabel(sessionId, rawPackage)

            when (action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.d(TAG, "AudioEffect session OPEN broadcast received: ID=$sessionId, package=$resolvedPackage")
                    attachSession(sessionId, resolvedPackage)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.d(TAG, "AudioEffect session CLOSE broadcast received: ID=$sessionId")
                    detachSession(sessionId)
                }
            }
        }
    }

    /**
     * Start the global system EQ engine:
     * - Immediately hooks Session 0 (Global System Output Mix).
     * - Registers AudioEffect session broadcast listener.
     * - Registers AudioManager.AudioPlaybackCallback (API 26+) for game session discovery.
     */
    @Synchronized
    fun start() {
        if (isStarted) return
        isStarted = true
        Log.i(TAG, "Initializing GlobalSystemEqEngine with priority Int.MAX_VALUE on Infinix XOS...")

        // 1. Attach primary Session 0 (Global System Output Mix)
        attachSession0()

        // 2. Register AudioEffect broadcast receiver for standard media apps (Spotify, YouTube, etc.)
        registerSessionReceiver()

        // 3. Register AudioPlaybackCallback for real-time game audio session sniffing (MLBB, Unity, FMOD)
        registerPlaybackCallback()

        updateStatus()
    }

    /**
     * Stop the global system EQ engine and release all active sessions.
     */
    @Synchronized
    fun stop() {
        if (!isStarted) return
        isStarted = false
        Log.i(TAG, "Stopping GlobalSystemEqEngine and releasing all hooked sessions...")

        unregisterSessionReceiver()
        unregisterPlaybackCallback()
        releaseAllSessions()
        updateStatus()
    }

    /**
     * Attaches DynamicsProcessing to Session 0 (System Global Output Mix).
     */
    @Synchronized
    fun attachSession0() {
        try {
            if (activeDynamicsProcessors.containsKey(SESSION_GLOBAL_MIX) ||
                activeLegacyEqualizers.containsKey(SESSION_GLOBAL_MIX)
            ) {
                _isSession0Hooked.value = true
                updateStatus()
                return
            }

            sessionPackageMap[SESSION_GLOBAL_MIX] = "System Global Mix (Session 0)"
            createAndStoreEffect(SESSION_GLOBAL_MIX)
            _isSession0Hooked.value = true
            Log.i(TAG, "Successfully attached high-priority DynamicsProcessing to Session 0 (Global Mix)")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to attach DynamicsProcessing to Session 0: ${e.message}, attempting fallback", e)
            fallbackToLegacyEqualizer(SESSION_GLOBAL_MIX)
            _isSession0Hooked.value = activeLegacyEqualizers.containsKey(SESSION_GLOBAL_MIX)
        }
        updateStatus()
    }

    /**
     * Detaches Session 0.
     */
    @Synchronized
    fun detachSession0() {
        releaseSessionEffect(SESSION_GLOBAL_MIX)
        _isSession0Hooked.value = false
        sessionPackageMap.remove(SESSION_GLOBAL_MIX)
        updateStatus()
    }

    /**
     * Attaches a synced DynamicsProcessing instance (priority = Int.MAX_VALUE) to an active session ID.
     */
    @Synchronized
    fun attachSession(sessionId: Int, packageName: String = "game.or.media.app") {
        if (sessionId < 0) return
        val currentLabel = sessionPackageMap[sessionId]
        val cleanLabel = if (currentLabel != null && !currentLabel.contains("playback_session") && packageName.contains("playback_session")) {
            currentLabel
        } else {
            packageName
        }

        sessionPackageMap[sessionId] = cleanLabel

        if (activeDynamicsProcessors.containsKey(sessionId)) {
            // Already attached, update its parameters to ensure active state
            val dp = activeDynamicsProcessors[sessionId]
            dp?.let { applyParametersToDp(it) }
        } else if (activeLegacyEqualizers.containsKey(sessionId)) {
            val eq = activeLegacyEqualizers[sessionId]
            eq?.let { applyParametersToLegacyEq(it) }
        } else {
            createAndStoreEffect(sessionId)
        }

        if (sessionId == SESSION_GLOBAL_MIX) {
            _isSession0Hooked.value = true
        }

        updateStatus()
    }

    /**
     * Detaches an audio session and cleans up its native effect.
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        if (sessionId == SESSION_GLOBAL_MIX) {
            detachSession0()
            return
        }
        releaseSessionEffect(sessionId)
        sessionPackageMap.remove(sessionId)
        updateStatus()
    }

    // =========================================================================
    // Preset & EQ Tuning APIs
    // =========================================================================

    /**
     * One-Tap "MLBB Competitive Clarity" Preset.
     *
     * Engineered specifically for Mobile Legends: Bang Bang:
     * - Low: +1.5 dB (clean hero footsteps & attack impact without mud).
     * - Mid: +2.5 dB (brings forward spell cast cues, ultimate announcements, and voice callouts).
     * - High: +1.2 dB (crisp skill confirmation & directional air).
     * - Limiter: -0.3 dBTP (brickwall inter-sample peak protection preventing speaker crackle).
     */
    fun applyMlbbPreset() {
        _isMlbbPresetActive.value = true
        _currentBassDb.value = MLBB_LOW_GAIN_DB
        _currentMidDb.value = MLBB_MID_GAIN_DB
        _currentTrebleDb.value = MLBB_HIGH_GAIN_DB
        _currentPreampDb.value = 0.0f
        _currentLimiterThresholdDb.value = MLBB_LIMITER_THRESHOLD_DB

        val gains = MLBB_10_BAND_GAINS.clone()
        _current10BandGains.value = gains

        syncAllEffects()
        Log.i(TAG, "MLBB Competitive Clarity Preset applied to ${activeDynamicsProcessors.size} active sessions")
    }

    /**
     * Apply custom 10-band EQ gains (32 Hz to 16 kHz).
     */
    fun apply10BandGains(gains: FloatArray) {
        if (gains.size != PRE_EQ_BAND_COUNT) return
        _isMlbbPresetActive.value = false
        _current10BandGains.value = gains.clone()
        syncAllEffects()
    }

    /**
     * Apply 3-band tone controls (Bass, Mids, Treble).
     */
    fun apply3BandGains(bassDb: Float, midDb: Float, trebleDb: Float) {
        _isMlbbPresetActive.value = false
        _currentBassDb.value = bassDb
        _currentMidDb.value = midDb
        _currentTrebleDb.value = trebleDb

        // Distribute 3-band tone into the 10 bands
        val gains = FloatArray(PRE_EQ_BAND_COUNT)
        // Sub & Bass (32Hz, 64Hz, 125Hz, 250Hz)
        gains[0] = bassDb * 0.9f
        gains[1] = bassDb * 1.0f
        gains[2] = bassDb * 0.95f
        gains[3] = bassDb * 0.7f + midDb * 0.3f
        // Midrange (500Hz, 1kHz, 2kHz)
        gains[4] = midDb * 0.9f
        gains[5] = midDb * 1.0f
        gains[6] = midDb * 0.9f
        // Presence & Treble (4kHz, 8kHz, 16kHz)
        gains[7] = midDb * 0.3f + trebleDb * 0.7f
        gains[8] = trebleDb * 1.0f
        gains[9] = trebleDb * 0.85f

        _current10BandGains.value = gains
        syncAllEffects()
    }

    /**
     * Adjust overall Preamp gain in dB.
     */
    fun setPreamp(preampDb: Float) {
        _currentPreampDb.value = preampDb.coerceIn(-12.0f, 12.0f)
        syncAllEffects()
    }

    /**
     * Set Output Limiter threshold in dB.
     */
    fun setLimiterThreshold(thresholdDb: Float) {
        _currentLimiterThresholdDb.value = thresholdDb.coerceIn(-6.0f, 0.0f)
        syncAllEffects()
    }

    /**
     * Master toggle for system-wide EQ processing.
     */
    fun setEnabled(enabled: Boolean) {
        _isEngineEnabled.value = enabled
        activeDynamicsProcessors.values.forEach { dp ->
            try {
                dp.enabled = enabled
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to toggle DynamicsProcessing: ${t.message}")
            }
        }
        activeLegacyEqualizers.values.forEach { eq ->
            try {
                eq.enabled = enabled
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to toggle Equalizer: ${t.message}")
            }
        }
        updateStatus()
    }

    // =========================================================================
    // Internal Native Effect Management
    // =========================================================================

    private fun createAndStoreEffect(sessionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val config = buildDynamicsProcessingConfig()
                // Priority Int.MAX_VALUE ensures override of OEM default processing on Dimensity/XOS
                val dp = DynamicsProcessing(Int.MAX_VALUE, sessionId, config)
                dp.enabled = _isEngineEnabled.value
                activeDynamicsProcessors[sessionId] = dp
                Log.d(TAG, "Attached DynamicsProcessing(priority=MAX) to session $sessionId")
                return
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing initialization failed for session $sessionId: ${e.message}, falling back to Equalizer")
            }
        }

        // Fallback for older APIs or if DynamicsProcessing is unavailable
        fallbackToLegacyEqualizer(sessionId)
    }

    private fun fallbackToLegacyEqualizer(sessionId: Int) {
        try {
            val eq = Equalizer(Int.MAX_VALUE, sessionId)
            eq.enabled = _isEngineEnabled.value
            applyParametersToLegacyEq(eq)
            activeLegacyEqualizers[sessionId] = eq
            Log.d(TAG, "Attached legacy Equalizer(priority=MAX) to session $sessionId")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to attach legacy Equalizer to session $sessionId: ${t.message}")
        }
    }

    private fun buildDynamicsProcessingConfig(): DynamicsProcessing.Config {
        val gains = _current10BandGains.value
        val preamp = _currentPreampDb.value
        val limiterThresh = _currentLimiterThresholdDb.value
        val isEnabled = _isEngineEnabled.value

        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT_STEREO,
            true, PRE_EQ_BAND_COUNT,
            true, MBC_BAND_COUNT,
            true, POST_EQ_BAND_COUNT,
            true
        )

        builder.setPreferredFrameDuration(10.0f) // 10ms processing frame
        builder.setInputGainAllChannelsTo(preamp)

        // 1. 10-Band Pre-EQ
        val preEq = DynamicsProcessing.Eq(true, isEnabled, PRE_EQ_BAND_COUNT)
        for (i in 0 until PRE_EQ_BAND_COUNT) {
            val gain = if (isEnabled) gains.getOrElse(i) { 0.0f } else 0.0f
            preEq.setBand(i, DynamicsProcessing.EqBand(true, BAND_UPPER_CUTOFFS_HZ[i], gain))
        }
        builder.setPreEqAllChannelsTo(preEq)

        // 2. 3-Band Multiband Compressor (MBC)
        val mbc = DynamicsProcessing.Mbc(true, isEnabled, MBC_BAND_COUNT)
        for (b in 0 until MBC_BAND_COUNT) {
            mbc.setBand(
                b,
                DynamicsProcessing.MbcBand(
                    isEnabled,
                    MBC_UPPER_CUTOFFS_HZ[b],
                    MBC_ATTACK_TIME_MS,
                    MBC_RELEASE_TIME_MS,
                    MBC_RATIO,
                    MBC_THRESHOLD_DB,
                    MBC_KNEE_WIDTH_DB,
                    MBC_NOISE_GATE_THRESHOLD_DB,
                    MBC_EXPANDER_RATIO,
                    0.0f,
                    0.0f
                )
            )
        }
        builder.setMbcAllChannelsTo(mbc)

        // 3. 7-Band Post-EQ for Speaker/Acoustic Resonance Control
        val postEq = DynamicsProcessing.Eq(true, false, POST_EQ_BAND_COUNT)
        for (i in 0 until POST_EQ_BAND_COUNT) {
            postEq.setBand(i, DynamicsProcessing.EqBand(true, SPEAKER_POST_EQ_CUTOFFS_HZ[i], 0.0f))
        }
        builder.setPostEqAllChannelsTo(postEq)

        // 4. Output Brickwall Limiter (-0.3 dBTP)
        val limiter = DynamicsProcessing.Limiter(
            true,
            isEnabled,
            0,
            LIMITER_ATTACK_TIME_MS,
            LIMITER_RELEASE_TIME_MS,
            LIMITER_RATIO,
            limiterThresh,
            0.0f
        )
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }

    private fun applyParametersToDp(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val isEnabled = _isEngineEnabled.value
            dp.enabled = isEnabled
            dp.setInputGainAllChannelsTo(_currentPreampDb.value)

            val gains = _current10BandGains.value
            for (i in 0 until PRE_EQ_BAND_COUNT) {
                val gain = if (isEnabled) gains.getOrElse(i) { 0.0f } else 0.0f
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, BAND_UPPER_CUTOFFS_HZ[i], gain))
            }

            val limiter = DynamicsProcessing.Limiter(
                true,
                isEnabled,
                0,
                LIMITER_ATTACK_TIME_MS,
                LIMITER_RELEASE_TIME_MS,
                LIMITER_RATIO,
                _currentLimiterThresholdDb.value,
                0.0f
            )
            dp.setLimiterAllChannelsTo(limiter)
        } catch (t: Throwable) {
            Log.w(TAG, "Error applying parameters to DynamicsProcessing: ${t.message}")
        }
    }

    private fun applyParametersToLegacyEq(eq: Equalizer) {
        try {
            val isEnabled = _isEngineEnabled.value
            eq.enabled = isEnabled
            val gains = _current10BandGains.value
            val numBands = eq.numberOfBands.toInt()
            val (minMilliBels, maxMilliBels) = eq.bandLevelRange

            for (b in 0 until numBands) {
                val indexIn10Band = ((b.toFloat() / (numBands.coerceAtLeast(1))) * PRE_EQ_BAND_COUNT).toInt().coerceIn(0, PRE_EQ_BAND_COUNT - 1)
                val gainDb = if (isEnabled) gains[indexIn10Band] else 0.0f
                val milliBels = (gainDb * 100.0f).toInt().coerceIn(minMilliBels.toInt(), maxMilliBels.toInt()).toShort()
                eq.setBandLevel(b.toShort(), milliBels)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error applying parameters to legacy Equalizer: ${t.message}")
        }
    }

    private fun syncAllEffects() {
        activeDynamicsProcessors.values.forEach { dp ->
            applyParametersToDp(dp)
        }
        activeLegacyEqualizers.values.forEach { eq ->
            applyParametersToLegacyEq(eq)
        }
    }

    private fun releaseSessionEffect(sessionId: Int) {
        activeDynamicsProcessors.remove(sessionId)?.let { dp ->
            try {
                dp.enabled = false
                dp.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Error releasing DynamicsProcessing for session $sessionId: ${t.message}")
            }
        }

        activeLegacyEqualizers.remove(sessionId)?.let { eq ->
            try {
                eq.enabled = false
                eq.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Error releasing Equalizer for session $sessionId: ${t.message}")
            }
        }
    }

    private fun releaseAllSessions() {
        activeDynamicsProcessors.keys().toList().forEach { releaseSessionEffect(it) }
        activeLegacyEqualizers.keys().toList().forEach { releaseSessionEffect(it) }
        sessionPackageMap.clear()
        _isSession0Hooked.value = false
    }

    // =========================================================================
    // Session Sniffing & Callbacks
    // =========================================================================

    private fun registerSessionReceiver() {
        if (sessionReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                sessionBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            sessionReceiverRegistered = true
            Log.d(TAG, "Registered AudioEffect session broadcast receiver")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register AudioEffect receiver: ${t.message}")
        }
    }

    private fun unregisterSessionReceiver() {
        if (!sessionReceiverRegistered) return
        try {
            appContext.unregisterReceiver(sessionBroadcastReceiver)
        } catch (_: Throwable) {
        }
        sessionReceiverRegistered = false
    }

    private fun registerPlaybackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || audioManager == null) return
        if (audioPlaybackCallback != null) return

        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                super.onPlaybackConfigChanged(configs)
                configs?.forEach { config ->
                    extractSessionIdFromPlaybackConfig(config)?.let { discoveredSessionId ->
                        if (discoveredSessionId > 0 && !activeDynamicsProcessors.containsKey(discoveredSessionId)) {
                            val pkg = sessionPackageMap[discoveredSessionId] ?: "game.or.media.session.$discoveredSessionId"
                            Log.i(TAG, "Discovered active game/media audio playback session ID: $discoveredSessionId ($pkg)")
                            attachSession(discoveredSessionId, pkg)
                        }
                    }
                }
            }
        }

        try {
            audioManager.registerAudioPlaybackCallback(callback, mainHandler)
            audioPlaybackCallback = callback
            Log.d(TAG, "Registered AudioManager.AudioPlaybackCallback for low-latency session hooking")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register AudioPlaybackCallback: ${t.message}")
        }
    }

    private fun unregisterPlaybackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioPlaybackCallback != null && audioManager != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback!!)
            } catch (_: Throwable) {
            }
            audioPlaybackCallback = null
        }
    }

    /**
     * Safely extracts the audioSessionId from AudioPlaybackConfiguration across
     * varying vendor Android builds (MediaTek Dimensity / Transsion XOS / AOSP).
     */
    private fun extractSessionIdFromPlaybackConfig(config: AudioPlaybackConfiguration): Int? {
        // Reflection check 1: getSessionId()
        try {
            val method = config.javaClass.getMethod("getSessionId")
            val id = method.invoke(config) as? Int
            if (id != null && id > 0) return id
        } catch (_: Throwable) {
        }

        // Reflection check 2: getAudioSessionId()
        try {
            val method = config.javaClass.getMethod("getAudioSessionId")
            val id = method.invoke(config) as? Int
            if (id != null && id > 0) return id
        } catch (_: Throwable) {
        }

        // Reflection check 3: private field mSessionId
        try {
            val field = config.javaClass.getDeclaredField("mSessionId")
            field.isAccessible = true
            val id = field.getInt(config)
            if (id > 0) return id
        } catch (_: Throwable) {
        }

        // Reflection check 4: player proxy reflection
        try {
            val playerField = config.javaClass.getDeclaredField("mPlayerRef")
            playerField.isAccessible = true
            val player = playerField.get(config)
            if (player != null) {
                val sessionMethod = player.javaClass.getMethod("getSessionId")
                val id = sessionMethod.invoke(player) as? Int
                if (id != null && id > 0) return id
            }
        } catch (_: Throwable) {
        }

        return null
    }

    private fun resolvePackageLabel(sessionId: Int, rawPackage: String?): String {
        if (!rawPackage.isNullOrBlank()) {
            return when {
                rawPackage.contains("mobile.legends", ignoreCase = true) || rawPackage.contains("mlbb", ignoreCase = true) -> "Mobile Legends: Bang Bang"
                rawPackage.contains("spotify", ignoreCase = true) -> "Spotify"
                rawPackage.contains("youtube", ignoreCase = true) -> "YouTube"
                rawPackage.contains("apple.android.music", ignoreCase = true) -> "Apple Music"
                rawPackage.contains("dts.freefire", ignoreCase = true) -> "Free Fire"
                rawPackage.contains("pubg", ignoreCase = true) -> "PUBG Mobile"
                rawPackage.contains("genshin", ignoreCase = true) -> "Genshin Impact"
                else -> rawPackage
            }
        }
        return "AudioSession_$sessionId"
    }

    private fun updateStatus() {
        val count = (activeDynamicsProcessors.size + activeLegacyEqualizers.size)
        _activeSessionsCount.value = count
        _activeSessionPackages.value = sessionPackageMap.toMap()

        val nonZeroSessions = sessionPackageMap.filter { it.key != SESSION_GLOBAL_MIX }
        val hasSession0 = _isSession0Hooked.value

        val targetLabel = when {
            nonZeroSessions.isNotEmpty() -> {
                val labels = nonZeroSessions.values.map { label ->
                    when {
                        label.contains("Mobile Legends", ignoreCase = true) || label.contains("mlbb", ignoreCase = true) -> "MLBB"
                        label.contains("Spotify", ignoreCase = true) -> "Spotify"
                        label.contains("YouTube", ignoreCase = true) -> "YouTube"
                        label.contains("Free Fire", ignoreCase = true) -> "Free Fire"
                        label.contains("PUBG", ignoreCase = true) -> "PUBG"
                        label.contains("Genshin", ignoreCase = true) -> "Genshin"
                        else -> label.substringAfterLast('.').take(12)
                    }
                }.distinct()

                if (hasSession0) {
                    "Session 0 + " + labels.joinToString("/")
                } else {
                    labels.joinToString("/")
                }
            }
            hasSession0 -> "Session 0 Global Mix"
            else -> "Standby"
        }

        _activeSessionsBadge.value = "Active Sessions Hooked: $count ($targetLabel)"
    }

    companion object {
        private const val TAG = "GlobalSystemEqEngine"
        const val SESSION_GLOBAL_MIX = 0
        const val CHANNEL_COUNT_STEREO = 2

        const val PRE_EQ_BAND_COUNT = 10
        const val MBC_BAND_COUNT = 3
        const val POST_EQ_BAND_COUNT = 7

        // MLBB Competitive Clarity Preset Values
        const val MLBB_PRESET_NAME = "MLBB Competitive Clarity"
        const val MLBB_LOW_GAIN_DB = 1.5f
        const val MLBB_MID_GAIN_DB = 2.5f
        const val MLBB_HIGH_GAIN_DB = 1.2f
        const val MLBB_LIMITER_THRESHOLD_DB = -0.3f

        // 10-Band Gains for MLBB (+1.2 to +1.8 low, +2.3 to +2.5 mids, +1.0 to +2.0 highs)
        val MLBB_10_BAND_GAINS = floatArrayOf(
            1.2f, // 32 Hz
            1.5f, // 64 Hz (Hero movement / bass weight)
            1.5f, // 125 Hz
            1.8f, // 250 Hz (Attack impact)
            2.3f, // 500 Hz (Voice clarity)
            2.5f, // 1 kHz (Skill audio cues & ultimate announcements)
            2.5f, // 2 kHz (Tactical callouts & alert pings)
            2.0f, // 4 kHz (Hit-marker confirmation)
            1.2f, // 8 kHz (Weapon clash & projectile zip)
            1.0f  // 16 kHz (Spatial air)
        )

        val BAND_UPPER_CUTOFFS_HZ = floatArrayOf(
            45f, 90f, 180f, 355f, 710f, 1400f, 2800f, 5600f, 11200f, 20000f
        )

        val SPEAKER_POST_EQ_CUTOFFS_HZ = floatArrayOf(
            55f, 86f, 220f, 480f, 3600f, 9000f, 20000f
        )

        val MBC_UPPER_CUTOFFS_HZ = floatArrayOf(
            220f, 3800f, 20000f
        )

        // Multiband Compressor Specs
        const val MBC_ATTACK_TIME_MS = 15.0f
        const val MBC_RELEASE_TIME_MS = 60.0f
        const val MBC_RATIO = 2.0f
        const val MBC_THRESHOLD_DB = -18.0f
        const val MBC_KNEE_WIDTH_DB = 6.0f
        const val MBC_NOISE_GATE_THRESHOLD_DB = -90.0f
        const val MBC_EXPANDER_RATIO = 1.0f

        // Output Brickwall Limiter Specs
        const val LIMITER_ATTACK_TIME_MS = 1.0f
        const val LIMITER_RELEASE_TIME_MS = 60.0f
        const val LIMITER_RATIO = 10.0f

        @Volatile
        private var instance: GlobalSystemEqEngine? = null

        fun getInstance(context: Context): GlobalSystemEqEngine {
            return instance ?: synchronized(this) {
                instance ?: GlobalSystemEqEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
