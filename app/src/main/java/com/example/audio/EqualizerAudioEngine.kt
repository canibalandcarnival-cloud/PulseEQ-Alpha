package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.AudioSessionRegistry
import com.example.model.AccommodationToneProfile
import com.example.model.AudioLabTelemetry
import com.example.model.BackgroundSoundPreset
import com.example.model.ConnectedDeviceState
import com.example.model.CustomAudioSetupUiState
import com.example.model.EqPresetsCatalog
import com.example.model.HearingCheckUiState
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.TestAudioSource
import com.example.model.VisualizerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

object EqualizerEngineHolder {
    @Volatile
    private var instance: EqualizerAudioEngine? = null

    fun get(context: Context): EqualizerAudioEngine {
        return instance ?: synchronized(this) {
            instance ?: EqualizerAudioEngine(context.applicationContext).also {
                instance = it
            }
        }
    }
}

class EqualizerAudioEngine(private val context: Context) {

    private data class SessionEffect(
        val sessionId: Int,
        val dynamicsProcessing: DynamicsProcessing? = null,
        val legacyEqualizer: Equalizer? = null
    ) {
        fun release() {
            try {
                dynamicsProcessing?.release()
            } catch (_: Throwable) {
            }
            try {
                legacyEqualizer?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val testSynthesizer = TestAudioSynthesizer()
    val audiogramEngine = AudiogramCheckEngine()
    val customAudioSetupEngine = CustomAudioSetupAuditionEngine()
    private val standaloneBackgroundPlayer = StandaloneBackgroundAudioPlayer()

    private val sessionEffects = ConcurrentHashMap<Int, SessionEffect>()
    private var systemVisualizer: Visualizer? = null

    private val _connectedDevices = MutableStateFlow(ConnectedDeviceState())
    val connectedDevices: StateFlow<ConnectedDeviceState> = _connectedDevices.asStateFlow()

    private val _dspStatusLabel = MutableStateFlow("Initializing DSP...")
    val dspStatusLabel: StateFlow<String> = _dspStatusLabel.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(checkAudioPermission())
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _globalMixFallbackEnabled = MutableStateFlow(true)
    val globalMixFallbackEnabled: StateFlow<Boolean> = _globalMixFallbackEnabled.asStateFlow()

    private val _audioLabTelemetry = MutableStateFlow(AudioLabTelemetry())
    val audioLabTelemetry: StateFlow<AudioLabTelemetry> = _audioLabTelemetry.asStateFlow()

    val hearingCheckState: StateFlow<HearingCheckUiState> = audiogramEngine.state
    val customAudioSetupState: StateFlow<CustomAudioSetupUiState> = customAudioSetupEngine.state

    private val _abCompareHeld = MutableStateFlow(false)
    val abCompareHeld: StateFlow<Boolean> = _abCompareHeld.asStateFlow()

    private val _systemVisualizerFrame = MutableStateFlow(VisualizerFrame())
    private val _visualizerFrame = MutableStateFlow(VisualizerFrame())
    val visualizerFrame: StateFlow<VisualizerFrame> = _visualizerFrame.asStateFlow()

    // Upper cutoff frequencies for the 10 user PreEq bands (32Hz..16kHz)
    private val bandUpperCutoffsHz = floatArrayOf(
        45f, 90f, 180f, 355f, 710f, 1400f, 2800f, 5600f, 11200f, 20000f
    )

    // Upper cutoff frequencies for the 6 PostEq iPhone Speaker DSP shaping bands:
    // [0]: <70Hz steep cut, [1]: 70-110Hz HPF shoulder, [2]: 110-250Hz virtual bass shelf,
    // [3]: 250-550Hz boxiness dip (400Hz -3dB), [4]: 550-2800Hz vocal clarity (2kHz +2dB),
    // [5]: 2800-4800Hz screech tame (3.5kHz -1.5dB), [6]: 4800-20kHz crisp air (10kHz +2.5dB)
    private val speakerPostEqCutoffsHz = floatArrayOf(
        70f, 110f, 250f, 550f, 2800f, 4800f, 20000f
    )

    @Volatile
    var currentTarget: OutputTarget = OutputTarget.SPEAKER
        private set

    @Volatile
    var currentGainsDb: List<Float> = List(10) { 0f }
        private set

    @Volatile
    var currentPreampDb: Float = 0f
        private set

    @Volatile
    var currentBypassed: Boolean = false
        private set

    @Volatile
    var currentLateNightEnabled: Boolean = false
        private set

    @Volatile
    var currentNightIntensity: NightIntensity = NightIntensity.STANDARD
        private set

    @Volatile
    var currentSpeakerDspEnabled: Boolean = true
        private set

    @Volatile
    var currentVirtualBassEnabled: Boolean = true
        private set

    @Volatile
    var currentSpatialMode: SpatialMode = SpatialMode.OFF
        private set

    @Volatile
    var currentSpatialDepth: Float = SpatialMode.ATMOS_MUSIC.defaultDepth
        private set

    @Volatile
    var currentHeightPresence: Float = SpatialMode.ATMOS_MUSIC.defaultHeight
        private set

    @Volatile
    var currentVocalAnchor: Boolean = true
        private set

    @Volatile
    var currentSoundCheckEnabled: Boolean = false
        private set

    @Volatile
    var currentConversationAwarenessEnabled: Boolean = false
        private set

    @Volatile
    var currentBgSoundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF
        private set

    @Volatile
    var currentBgSoundVolume: Float = 0.28f
        private set

    @Volatile
    var currentVoiceIsolationEnabled: Boolean = false
        private set

    @Volatile
    var currentThreeBandEnabled: Boolean = true
        private set

    @Volatile
    var currentThreeBandBassDb: Float = 0f
        private set

    @Volatile
    var currentThreeBandMidsDb: Float = 0f
        private set

    @Volatile
    var currentThreeBandTrebleDb: Float = 0f
        private set

    @Volatile
    var currentAccommodationsEnabled: Boolean = false
        private set

    @Volatile
    var currentAccommodationsToneProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE
        private set

    @Volatile
    var currentAccommodationsSoftIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE
        private set

    @Volatile
    var currentAccommodationsApplyToMedia: Boolean = true
        private set

    @Volatile
    var currentAccommodationsApplyToVoice: Boolean = false
        private set

    @Volatile
    private var systemSoundCheckTrimDb: Float = 0f

    @Volatile
    private var currentDuckGainLinear: Float = 1f

    private val voiceMicMonitor = VoiceMicAccommodationsMonitor { active, levelDb ->
        _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
            voiceMicMonitorActive = active,
            voiceMicLevelDb = levelDb
        )
    }

    private val conversationMonitor = ConversationAwarenessMonitor { speechDetected, duckGain ->
        currentDuckGainLinear = duckGain
        testSynthesizer.setConversationDuckGain(duckGain)
        _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
            speechDetected = speechDetected,
            duckingGainLinear = duckGain
        )
        scope.launch {
            applyToAllSessions()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshConnectedDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshConnectedDevices()
        }
    }

    init {
        refreshConnectedDevices()
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        } catch (_: Throwable) {
        }

        reconcileAttachedSessions()
        refreshSystemVisualizer()

        scope.launch {
            combine(
                AudioSessionRegistry.activeSessions,
                _globalMixFallbackEnabled
            ) { sessions, globalFallback ->
                sessions.keys to globalFallback
            }.collect {
                reconcileAttachedSessions()
            }
        }

        scope.launch {
            combine(
                testSynthesizer.currentSource,
                testSynthesizer.visualizerFrame,
                _systemVisualizerFrame
            ) { testSource, synthFrame, sysFrame ->
                if (testSource != TestAudioSource.OFF) {
                    _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
                        inputLufs = testSynthesizer.latestInputLufs,
                        soundCheckTrimDb = testSynthesizer.latestSoundCheckTrimDb,
                        voiceGateAttenuationDb = testSynthesizer.latestVoiceGateDb,
                        accommodationsGrDb = testSynthesizer.latestAccommodationsGrDb
                    )
                    synthFrame
                } else {
                    sysFrame
                }
            }.collect { frame ->
                _visualizerFrame.value = frame
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        refreshSystemVisualizer()
        conversationMonitor.setEnabled(currentConversationAwarenessEnabled, granted)
        voiceMicMonitor.configure(
            enabled = currentAccommodationsEnabled && currentAccommodationsApplyToVoice && !currentBypassed,
            profile = currentAccommodationsToneProfile,
            intensity = currentAccommodationsSoftIntensity,
            hasRecordPermission = granted
        )
    }

    fun setAbCompareHeld(held: Boolean) {
        if (_abCompareHeld.value == held) return
        _abCompareHeld.value = held
        applyParameters(
            target = currentTarget,
            gainsDb = currentGainsDb,
            preampDb = currentPreampDb,
            isBypassed = currentBypassed,
            lateNightEnabled = currentLateNightEnabled,
            nightIntensity = currentNightIntensity,
            speakerDspEnabled = currentSpeakerDspEnabled,
            virtualBassEnabled = currentVirtualBassEnabled,
            spatialMode = currentSpatialMode,
            spatialDepth = currentSpatialDepth,
            heightPresence = currentHeightPresence,
            vocalAnchor = currentVocalAnchor,
            soundCheckEnabled = currentSoundCheckEnabled,
            conversationAwarenessEnabled = currentConversationAwarenessEnabled,
            bgSoundPreset = currentBgSoundPreset,
            bgSoundVolume = currentBgSoundVolume,
            voiceIsolationEnabled = currentVoiceIsolationEnabled,
            threeBandEnabled = currentThreeBandEnabled,
            threeBandBassDb = currentThreeBandBassDb,
            threeBandMidsDb = currentThreeBandMidsDb,
            threeBandTrebleDb = currentThreeBandTrebleDb,
            accommodationsEnabled = currentAccommodationsEnabled,
            accommodationsToneProfile = currentAccommodationsToneProfile,
            accommodationsSoftIntensity = currentAccommodationsSoftIntensity,
            accommodationsApplyToMedia = currentAccommodationsApplyToMedia,
            accommodationsApplyToVoice = currentAccommodationsApplyToVoice
        )
    }

    fun setGlobalMixFallback(enabled: Boolean) {
        _globalMixFallbackEnabled.value = enabled
        reconcileAttachedSessions()
    }

    fun refreshConnectedDevices() {
        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Throwable) {
            emptyArray()
        }

        var headphonesConnected = false
        var headphonesName: String? = null
        var bluetoothConnected = false
        var bluetoothName: String? = null

        for (device in outputs) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    headphonesConnected = true
                    val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                    if (headphonesName == null && name != null) {
                        headphonesName = name
                    }
                }

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_HEARING_AID -> {
                    bluetoothConnected = true
                    val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                    if (bluetoothName == null && name != null) {
                        bluetoothName = name
                    }
                }
            }
        }

        val activeRoute = when {
            bluetoothConnected -> OutputTarget.BLUETOOTH
            headphonesConnected -> OutputTarget.HEADPHONES
            else -> OutputTarget.SPEAKER
        }

        _connectedDevices.value = ConnectedDeviceState(
            activeRoute = activeRoute,
            speakerAvailable = true,
            headphonesConnected = headphonesConnected,
            headphonesName = headphonesName,
            bluetoothConnected = bluetoothConnected,
            bluetoothName = bluetoothName
        )
    }

    fun reconcileAttachedSessions() {
        val targetSessions = buildSet {
            addAll(AudioSessionRegistry.activeSessions.value.keys)
            if (_globalMixFallbackEnabled.value) {
                add(0)
            }
        }

        val toRemove = sessionEffects.keys.filter { it !in targetSessions }
        for (id in toRemove) {
            sessionEffects.remove(id)?.release()
        }

        for (sessionId in targetSessions) {
            if (!sessionEffects.containsKey(sessionId)) {
                createEffectForSession(sessionId)?.let { created ->
                    sessionEffects[sessionId] = created
                }
            }
        }

        applyToAllSessions()
    }

    private fun createEffectForSession(sessionId: Int): SessionEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10,
                    true, 1,
                    true, speakerPostEqCutoffsHz.size,
                    true
                )
                val preEq = DynamicsProcessing.Eq(true, true, 10)
                for (i in 0 until 10) {
                    preEq.setBand(i, DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], 0f))
                }
                builder.setPreEqAllChannelsTo(preEq)

                val postEq = DynamicsProcessing.Eq(true, false, speakerPostEqCutoffsHz.size)
                for (i in speakerPostEqCutoffsHz.indices) {
                    postEq.setBand(i, DynamicsProcessing.EqBand(true, speakerPostEqCutoffsHz[i], 0f))
                }
                builder.setPostEqAllChannelsTo(postEq)

                val mbc = DynamicsProcessing.Mbc(true, false, 1)
                mbc.setBand(
                    0,
                    DynamicsProcessing.MbcBand(
                        false,
                        22000f,
                        NightIntensity.STANDARD.attackMs,
                        NightIntensity.STANDARD.releaseMs,
                        NightIntensity.STANDARD.ratio,
                        NightIntensity.STANDARD.thresholdDb,
                        NightIntensity.STANDARD.kneeDb,
                        -90f,
                        1f,
                        0f,
                        NightIntensity.STANDARD.makeupGainDb
                    )
                )
                builder.setMbcAllChannelsTo(mbc)

                val limiter = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1.0f,
                    60.0f,
                    10.0f,
                    -0.5f,
                    0.0f
                )
                builder.setLimiterAllChannelsTo(limiter)

                val dp = DynamicsProcessing(0, sessionId, builder.build())
                dp.enabled = true
                return SessionEffect(sessionId = sessionId, dynamicsProcessing = dp)
            } catch (_: Throwable) {
            }
        }

        return try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = true
            SessionEffect(sessionId = sessionId, legacyEqualizer = eq)
        } catch (_: Throwable) {
            null
        }
    }

    fun applyParameters(
        target: OutputTarget = currentTarget,
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean = currentLateNightEnabled,
        nightIntensity: NightIntensity = currentNightIntensity,
        speakerDspEnabled: Boolean = currentSpeakerDspEnabled,
        virtualBassEnabled: Boolean = currentVirtualBassEnabled,
        spatialMode: SpatialMode = currentSpatialMode,
        spatialDepth: Float = currentSpatialDepth,
        heightPresence: Float = currentHeightPresence,
        vocalAnchor: Boolean = currentVocalAnchor,
        soundCheckEnabled: Boolean = currentSoundCheckEnabled,
        conversationAwarenessEnabled: Boolean = currentConversationAwarenessEnabled,
        bgSoundPreset: BackgroundSoundPreset = currentBgSoundPreset,
        bgSoundVolume: Float = currentBgSoundVolume,
        voiceIsolationEnabled: Boolean = currentVoiceIsolationEnabled,
        threeBandEnabled: Boolean = currentThreeBandEnabled,
        threeBandBassDb: Float = currentThreeBandBassDb,
        threeBandMidsDb: Float = currentThreeBandMidsDb,
        threeBandTrebleDb: Float = currentThreeBandTrebleDb,
        accommodationsEnabled: Boolean = currentAccommodationsEnabled,
        accommodationsToneProfile: AccommodationToneProfile = currentAccommodationsToneProfile,
        accommodationsSoftIntensity: SoftSoundIntensity = currentAccommodationsSoftIntensity,
        accommodationsApplyToMedia: Boolean = currentAccommodationsApplyToMedia,
        accommodationsApplyToVoice: Boolean = currentAccommodationsApplyToVoice
    ) {
        currentTarget = target
        currentGainsDb = gainsDb
        currentPreampDb = preampDb
        currentBypassed = isBypassed
        currentLateNightEnabled = lateNightEnabled
        currentNightIntensity = nightIntensity
        currentSpeakerDspEnabled = speakerDspEnabled
        currentVirtualBassEnabled = virtualBassEnabled
        currentSpatialMode = spatialMode
        currentSpatialDepth = spatialDepth
        currentHeightPresence = heightPresence
        currentVocalAnchor = vocalAnchor
        currentSoundCheckEnabled = soundCheckEnabled
        currentConversationAwarenessEnabled = conversationAwarenessEnabled
        currentBgSoundPreset = bgSoundPreset
        currentBgSoundVolume = bgSoundVolume
        currentVoiceIsolationEnabled = voiceIsolationEnabled
        currentThreeBandEnabled = threeBandEnabled
        currentThreeBandBassDb = threeBandBassDb
        currentThreeBandMidsDb = threeBandMidsDb
        currentThreeBandTrebleDb = threeBandTrebleDb
        currentAccommodationsEnabled = accommodationsEnabled
        currentAccommodationsToneProfile = accommodationsToneProfile
        currentAccommodationsSoftIntensity = accommodationsSoftIntensity
        currentAccommodationsApplyToMedia = accommodationsApplyToMedia
        currentAccommodationsApplyToVoice = accommodationsApplyToVoice

        val isSpeakerDspActive = (target == OutputTarget.SPEAKER) && speakerDspEnabled
        val isAbHeld = _abCompareHeld.value
        val hasMicPerm = checkAudioPermission()

        conversationMonitor.setEnabled(conversationAwarenessEnabled, hasMicPerm)
        voiceMicMonitor.configure(
            enabled = accommodationsEnabled && accommodationsApplyToVoice && !isBypassed,
            profile = accommodationsToneProfile,
            intensity = accommodationsSoftIntensity,
            hasRecordPermission = hasMicPerm
        )
        standaloneBackgroundPlayer.update(
            preset = bgSoundPreset,
            volume = bgSoundVolume,
            suppressBecauseSynthPlaying = (testSynthesizer.currentSource.value != TestAudioSource.OFF)
        )

        testSynthesizer.updateEq(
            gainsDb = gainsDb,
            preampDb = preampDb,
            isBypassed = isBypassed,
            lateNightEnabled = lateNightEnabled,
            nightIntensity = nightIntensity,
            speakerDspEnabled = isSpeakerDspActive,
            virtualBassEnabled = isSpeakerDspActive && virtualBassEnabled,
            outputTarget = target,
            spatialMode = spatialMode,
            spatialDepth = spatialDepth,
            heightPresence = heightPresence,
            vocalAnchor = vocalAnchor,
            soundCheckEnabled = soundCheckEnabled,
            voiceIsolationEnabled = voiceIsolationEnabled,
            abCompareHeld = isAbHeld,
            bgSoundPreset = bgSoundPreset,
            bgSoundVolume = bgSoundVolume,
            threeBandEnabled = threeBandEnabled,
            threeBandBassDb = threeBandBassDb,
            threeBandMidsDb = threeBandMidsDb,
            threeBandTrebleDb = threeBandTrebleDb,
            accommodationsEnabled = accommodationsEnabled,
            accommodationsToneProfile = accommodationsToneProfile,
            accommodationsSoftIntensity = accommodationsSoftIntensity,
            accommodationsApplyToMedia = accommodationsApplyToMedia
        )
        applyToAllSessions()
    }

    private fun applyToAllSessions() {
        var anyDpActive = false
        var anyLegacyActive = false
        val isAbHeld = _abCompareHeld.value
        val effectiveActive = !currentBypassed && !isAbHeld
        val isSpeakerDspActive = effectiveActive && (currentTarget == OutputTarget.SPEAKER) && currentSpeakerDspEnabled
        val isSpatialActive = effectiveActive && currentSpatialMode != SpatialMode.OFF
        val threeBandActive = currentThreeBandEnabled &&
            (kotlin.math.abs(currentThreeBandBassDb) > 0.01f ||
                kotlin.math.abs(currentThreeBandMidsDb) > 0.01f ||
                kotlin.math.abs(currentThreeBandTrebleDb) > 0.01f)
        val duckDb = if (currentDuckGainLinear < 0.99f) {
            (20.0 * log10(currentDuckGainLinear.toDouble().coerceAtLeast(0.15))).toFloat()
        } else {
            0f
        }

        for (bundle in sessionEffects.values) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bundle.dynamicsProcessing != null) {
                val dp = bundle.dynamicsProcessing
                try {
                    dp.enabled = !currentBypassed
                    if (isAbHeld && !currentBypassed) {
                        // A/B Compare: bypass all tonal/dynamics stages and match perceived loudness
                        val threeBandAvg = if (currentThreeBandEnabled) {
                            (currentThreeBandBassDb * 0.25f + currentThreeBandMidsDb * 0.50f + currentThreeBandTrebleDb * 0.25f)
                        } else {
                            0f
                        }
                        val accommodationsMatchBoost = if (currentAccommodationsEnabled && currentAccommodationsApplyToMedia) {
                            currentAccommodationsSoftIntensity.makeupGainDb * 0.65f +
                                1.0f * currentAccommodationsSoftIntensity.eqScale
                        } else {
                            0f
                        }
                        val matchGainDb = AudiogramCheckEngine.computeLoudnessMatchGainDb(
                            bandGainsDb = currentGainsDb,
                            preampDb = currentPreampDb + threeBandAvg + accommodationsMatchBoost,
                            lateNightEnabled = currentLateNightEnabled,
                            nightIntensity = currentNightIntensity,
                            target = currentTarget,
                            speakerDspEnabled = currentSpeakerDspEnabled,
                            spatialMode = currentSpatialMode
                        )
                        dp.setInputGainAllChannelsTo(matchGainDb + duckDb)
                        for (i in 0 until 10) {
                            dp.setPreEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], 0f)
                            )
                        }
                        dp.setPostEqAllChannelsTo(DynamicsProcessing.Eq(true, false, speakerPostEqCutoffsHz.size))
                        dp.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, false, 1))
                        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1.0f, 60.0f, 10.0f, -0.5f, 0.0f))
                    } else if (!currentBypassed) {
                        val accommodationsMediaActive = currentAccommodationsEnabled && currentAccommodationsApplyToMedia
                        val scGain = if (currentSoundCheckEnabled) systemSoundCheckTrimDb else 0f
                        dp.setInputGainAllChannelsTo(currentPreampDb + scGain + duckDb)
                        for (i in 0 until 10) {
                            val tenBandGain = currentGainsDb.getOrElse(i) { 0f }
                                .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
                            val toneOffset = if (threeBandActive) {
                                ThreeBandToneFilter.computeCombinedResponseDbAt(
                                    freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                    bassDb = currentThreeBandBassDb,
                                    midsDb = currentThreeBandMidsDb,
                                    trebleDb = currentThreeBandTrebleDb
                                )
                            } else {
                                0f
                            }
                            val accommodationsOffset = if (accommodationsMediaActive) {
                                HeadphoneAccommodationsProcessor.computeResponseDbAt(
                                    freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                    profile = currentAccommodationsToneProfile,
                                    intensity = currentAccommodationsSoftIntensity
                                )
                            } else {
                                0f
                            }
                            val combinedGain = (tenBandGain + toneOffset + accommodationsOffset).coerceIn(-15f, 15f)
                            dp.setPreEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], combinedGain)
                            )
                        }

                        // PostEq stage: Voice Isolation / iPhone Speaker DSP / Pinna Height Atmos contour
                        val heightScale = currentHeightPresence.coerceIn(0f, 1f)
                        val cinemaBoost = if (currentSpatialMode == SpatialMode.ATMOS_CINEMA) 1.25f else 1.0f
                        val postEqEnabled = isSpeakerDspActive || isSpatialActive || currentVoiceIsolationEnabled
                        val postEqGains = when {
                            currentVoiceIsolationEnabled -> floatArrayOf(
                                -12.0f, // <70Hz rumble cut
                                -10.0f, // 70-110Hz sub cut
                                0.0f,   // 120-250Hz speech body
                                0.5f,   // 250-550Hz chest clarity
                                2.0f,   // 550-2800Hz speech presence
                                -2.5f,  // 2.8-4.8kHz sibilance tame
                                -10.0f  // >6.5kHz hiss cut
                            )
                            isSpeakerDspActive -> floatArrayOf(
                                -12.0f, // <70Hz steep sub-bass protection
                                -10.0f, // 70-110Hz steep HPF shoulder
                                if (currentVirtualBassEnabled) 2.2f else 0.0f, // 120-250Hz harmonic punch
                                -3.0f,  // 400Hz surgical boxiness removal
                                if (isSpatialActive && currentVocalAnchor) 2.6f else 2.0f, // 2kHz vocal clarity
                                -1.5f,  // 3.5kHz harshness tame
                                if (isSpatialActive) 2.5f + 1.5f * heightScale else 2.5f // 10kHz air + height shelf
                            )
                            isSpatialActive -> floatArrayOf(
                                0.4f, // Tight <70Hz center mono punch
                                0.2f, // 70-110Hz anchor
                                0.0f,
                                0.0f,
                                if (currentVocalAnchor) 1.4f else 0.0f, // Vocal Anchor lock
                                (-1.8f * heightScale * cinemaBoost).coerceIn(-3.5f, 0f), // Pinna dip zone
                                (2.4f * heightScale * cinemaBoost).coerceIn(0f, 3.5f)   // 12kHz height air shelf
                            )
                            else -> FloatArray(speakerPostEqCutoffsHz.size) { 0f }
                        }
                        val postEq = DynamicsProcessing.Eq(true, postEqEnabled, speakerPostEqCutoffsHz.size)
                        for (i in speakerPostEqCutoffsHz.indices) {
                            postEq.setBand(
                                i,
                                DynamicsProcessing.EqBand(postEqEnabled, speakerPostEqCutoffsHz[i], postEqGains[i])
                            )
                        }
                        dp.setPostEqAllChannelsTo(postEq)

                        // Mbc stage: Late Night 4:1 compressor OR Boost Soft Sounds OR Voice Isolation -38dB downward gate
                        val compEnabled = currentLateNightEnabled || accommodationsMediaActive
                        val mbc = DynamicsProcessing.Mbc(true, compEnabled, 1)
                        val mbcAttack = if (currentLateNightEnabled) currentNightIntensity.attackMs else 8f
                        val mbcRelease = if (currentLateNightEnabled) currentNightIntensity.releaseMs else 160f
                        val mbcRatio = if (currentLateNightEnabled) {
                            maxOf(currentNightIntensity.ratio, if (accommodationsMediaActive) currentAccommodationsSoftIntensity.ratio else 1f)
                        } else {
                            currentAccommodationsSoftIntensity.ratio
                        }
                        val mbcThreshold = if (currentLateNightEnabled) {
                            currentNightIntensity.thresholdDb
                        } else {
                            currentAccommodationsSoftIntensity.thresholdDb
                        }
                        val mbcKnee = if (currentLateNightEnabled) {
                            currentNightIntensity.kneeDb
                        } else {
                            currentAccommodationsSoftIntensity.kneeDb
                        }
                        val mbcMakeup = (if (currentLateNightEnabled) currentNightIntensity.makeupGainDb else 0f) +
                            (if (accommodationsMediaActive) currentAccommodationsSoftIntensity.makeupGainDb else 0f)

                        mbc.setBand(
                            0,
                            DynamicsProcessing.MbcBand(
                                compEnabled,
                                22000f,
                                mbcAttack,
                                mbcRelease,
                                mbcRatio,
                                mbcThreshold,
                                mbcKnee,
                                if (currentVoiceIsolationEnabled) -38f else -90f,
                                if (currentVoiceIsolationEnabled) 3.2f else 1f,
                                0f,
                                if (compEnabled) mbcMakeup else 0f
                            )
                        )
                        dp.setMbcAllChannelsTo(mbc)

                        // Limiter stage: iPhone Smart Speaker Limiter (-6dB, 12:1, 1ms/80ms, +3dB makeup)
                        val limiter = if (isSpeakerDspActive) {
                            DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                1.0f,
                                80.0f,
                                12.0f,
                                -6.0f,
                                3.0f
                            )
                        } else {
                            DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                1.0f,
                                60.0f,
                                10.0f,
                                -0.5f,
                                0.0f
                            )
                        }
                        dp.setLimiterAllChannelsTo(limiter)
                    }
                    anyDpActive = true
                } catch (_: Throwable) {
                }
            } else if (bundle.legacyEqualizer != null) {
                val legEq = bundle.legacyEqualizer
                try {
                    legEq.enabled = effectiveActive
                    if (effectiveActive) {
                        val range = legEq.bandLevelRange
                        val minMb = range[0].toInt()
                        val maxMb = range[1].toInt()
                        val numBands = legEq.numberOfBands.toInt()
                        for (b in 0 until numBands) {
                            val centerHz = legEq.getCenterFreq(b.toShort()) / 1000f
                            val speakerOffset = if (isSpeakerDspActive && centerHz < 110f) -10f else 0f
                            val mappedDb = interpolateGainForFrequency(centerHz, currentGainsDb) +
                                currentPreampDb * 0.5f + speakerOffset
                            val levelMb = (mappedDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                            legEq.setBandLevel(b.toShort(), levelMb)
                        }
                    }
                    anyLegacyActive = true
                } catch (_: Throwable) {
                }
            }
        }

        val sessionCount = AudioSessionRegistry.activeSessions.value.size
        val accMediaActive = effectiveActive && currentAccommodationsEnabled && currentAccommodationsApplyToMedia
        _dspStatusLabel.value = when {
            isAbHeld -> "A/B Compare: Original"
            currentBypassed -> "DSP Bypassed"
            currentVoiceIsolationEnabled -> "Voice Isolation"
            isSpatialActive && currentTarget == OutputTarget.SPEAKER -> "Speaker XTC Spatial"
            isSpatialActive -> "Spatial ${currentSpatialMode.label}"
            accMediaActive -> "Accommodations • ${currentAccommodationsToneProfile.label}"
            isSpeakerDspActive && currentLateNightEnabled -> "Speaker DSP + Late Night"
            isSpeakerDspActive -> "iPhone Speaker DSP"
            anyDpActive && sessionCount > 0 -> {
                if (currentLateNightEnabled) "App DSP + Late Night ($sessionCount)" else "App DSP ($sessionCount active)"
            }
            anyDpActive -> {
                if (currentLateNightEnabled) "Global DSP + Late Night" else "10-Band Global DSP"
            }
            anyLegacyActive -> "System EQ Active"
            !_globalMixFallbackEnabled.value && sessionCount == 0 -> "Waiting for Media App"
            else -> if (currentLateNightEnabled) "Studio DSP + Late Night" else "Studio 10-Band DSP"
        }
    }

    private fun interpolateGainForFrequency(freqHz: Float, gainsDb: List<Float>): Float {
        val bands = EqPresetsCatalog.BANDS
        if (freqHz <= bands.first().frequencyHz) return gainsDb.firstOrNull() ?: 0f
        if (freqHz >= bands.last().frequencyHz) return gainsDb.lastOrNull() ?: 0f

        for (i in 0 until bands.size - 1) {
            val f1 = bands[i].frequencyHz.toFloat()
            val f2 = bands[i + 1].frequencyHz.toFloat()
            if (freqHz in f1..f2) {
                val t = (log10(freqHz) - log10(f1)) / (log10(f2) - log10(f1))
                val g1 = gainsDb.getOrElse(i) { 0f }
                val g2 = gainsDb.getOrElse(i + 1) { 0f }
                return g1 + (g2 - g1) * t
            }
        }
        return 0f
    }

    fun refreshSystemVisualizer() {
        releaseSystemVisualizer()
        if (!checkAudioPermission()) {
            _systemVisualizerFrame.value = VisualizerFrame()
            return
        }

        try {
            val vis = Visualizer(0)
            val sizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = sizeRange[1].coerceAtMost(1024).coerceAtLeast(sizeRange[0])
            vis.captureSize = captureSize

            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        val samples = MutableList(96) { idx ->
                            val raw = waveform[(idx * waveform.size) / 96].toInt() and 0xFF
                            ((raw - 128) / 128f).coerceIn(-1f, 1f)
                        }
                        var peak = 0f
                        for (s in samples) {
                            val a = kotlin.math.abs(s)
                            if (a > peak) peak = a
                        }
                        val live = peak > 0.01f
                        val peakDb = if (peak > 1e-4f) (20f * log10(peak)).coerceIn(-60f, 0f) else -60f

                        val isSpeakerDspActive = (currentTarget == OutputTarget.SPEAKER) && currentSpeakerDspEnabled
                        val lateNightGr = if (live && currentLateNightEnabled && !currentBypassed) {
                            DynamicRangeCompressor.computeGainReductionDb(
                                inputDb = peakDb,
                                thresholdDb = currentNightIntensity.thresholdDb,
                                kneeDb = currentNightIntensity.kneeDb,
                                ratio = currentNightIntensity.ratio
                            )
                        } else {
                            0f
                        }
                        val speakerLimiterGr = if (live && isSpeakerDspActive && !currentBypassed) {
                            DynamicRangeCompressor.computeGainReductionDb(
                                inputDb = peakDb,
                                thresholdDb = -6f,
                                kneeDb = 4f,
                                ratio = 12f
                            )
                        } else {
                            0f
                        }

                        val desiredSoundCheck = if (live && currentSoundCheckEnabled) {
                            SoundCheckNormalizer.computeTargetTrimDb(peakDb - 3f)
                        } else {
                            0f
                        }
                        systemSoundCheckTrimDb = systemSoundCheckTrimDb * 0.85f + desiredSoundCheck * 0.15f
                        if (testSynthesizer.currentSource.value == TestAudioSource.OFF) {
                            _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
                                inputLufs = (peakDb - 3f).coerceIn(-60f, 0f),
                                soundCheckTrimDb = systemSoundCheckTrimDb,
                                voiceGateAttenuationDb = if (live || !currentVoiceIsolationEnabled) 0f else -24f
                            )
                        }

                        _systemVisualizerFrame.value = _systemVisualizerFrame.value.copy(
                            waveformSamples = samples,
                            peakDb = peakDb,
                            gainReductionDb = minOf(lateNightGr, speakerLimiterGr),
                            isSignalLive = live
                        )
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        val n = fft.size / 2
                        val sr = (samplingRate / 1000.0).coerceAtLeast(8000.0)
                        val bars = MutableList(32) { 0f }
                        val minFreq = 30.0
                        val maxFreq = (sr * 0.45).coerceAtLeast(8000.0)

                        var anySignal = false
                        for (b in 0 until 32) {
                            val fLow = minFreq * (maxFreq / minFreq).pow(b / 32.0)
                            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1) / 32.0)
                            val binLow = ((fLow * fft.size) / sr).toInt().coerceIn(1, n - 1)
                            val binHigh = ((fHigh * fft.size) / sr).toInt().coerceIn(binLow, n - 1)

                            var maxMag = 0.0
                            for (k in binLow..binHigh) {
                                val re = fft[2 * k].toDouble()
                                val im = fft[2 * k + 1].toDouble()
                                val mag = hypot(re, im)
                                if (mag > maxMag) maxMag = mag
                            }
                            if (maxMag > 1.5) anySignal = true
                            val norm = if (maxMag > 0.5) {
                                ((20.0 * log10(maxMag / 128.0) + 48.0) / 48.0).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            bars[b] = norm
                        }

                        _systemVisualizerFrame.value = _systemVisualizerFrame.value.copy(
                            spectrumBins = bars,
                            isSignalLive = anySignal || _systemVisualizerFrame.value.isSignalLive
                        )
                    }
                },
                Visualizer.getMaxCaptureRate().coerceAtMost(20000),
                true,
                true
            )
            vis.enabled = true
            systemVisualizer = vis
        } catch (_: Throwable) {
            systemVisualizer = null
        }
    }

    fun setTestAudioSource(source: TestAudioSource) {
        testSynthesizer.setSource(source)
        standaloneBackgroundPlayer.update(
            preset = currentBgSoundPreset,
            volume = currentBgSoundVolume,
            suppressBecauseSynthPlaying = (source != TestAudioSource.OFF)
        )
        val isSpeakerDspActive = (currentTarget == OutputTarget.SPEAKER) && currentSpeakerDspEnabled
        testSynthesizer.updateEq(
            gainsDb = currentGainsDb,
            preampDb = currentPreampDb,
            isBypassed = currentBypassed,
            lateNightEnabled = currentLateNightEnabled,
            nightIntensity = currentNightIntensity,
            speakerDspEnabled = isSpeakerDspActive,
            virtualBassEnabled = isSpeakerDspActive && currentVirtualBassEnabled,
            outputTarget = currentTarget,
            spatialMode = currentSpatialMode,
            spatialDepth = currentSpatialDepth,
            heightPresence = currentHeightPresence,
            vocalAnchor = currentVocalAnchor,
            soundCheckEnabled = currentSoundCheckEnabled,
            voiceIsolationEnabled = currentVoiceIsolationEnabled,
            abCompareHeld = _abCompareHeld.value,
            bgSoundPreset = currentBgSoundPreset,
            bgSoundVolume = currentBgSoundVolume,
            threeBandEnabled = currentThreeBandEnabled,
            threeBandBassDb = currentThreeBandBassDb,
            threeBandMidsDb = currentThreeBandMidsDb,
            threeBandTrebleDb = currentThreeBandTrebleDb,
            accommodationsEnabled = currentAccommodationsEnabled,
            accommodationsToneProfile = currentAccommodationsToneProfile,
            accommodationsSoftIntensity = currentAccommodationsSoftIntensity,
            accommodationsApplyToMedia = currentAccommodationsApplyToMedia
        )
    }

    private fun releaseSystemVisualizer() {
        try {
            systemVisualizer?.enabled = false
            systemVisualizer?.release()
        } catch (_: Throwable) {
        }
        systemVisualizer = null
    }

    fun releaseAllSessions() {
        sessionEffects.values.forEach { it.release() }
        sessionEffects.clear()
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Throwable) {
        }
        conversationMonitor.stop()
        voiceMicMonitor.stop()
        standaloneBackgroundPlayer.stop()
        audiogramEngine.stopTone()
        customAudioSetupEngine.stopAuditionLoop()
        releaseSystemVisualizer()
        testSynthesizer.stop()
        releaseAllSessions()
    }
}
