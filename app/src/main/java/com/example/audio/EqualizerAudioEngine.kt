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
import com.example.model.AdaptiveMultibandPhaseSpecs
import com.example.model.AudioLabTelemetry
import com.example.model.AudioMixMode
import com.example.model.BackgroundSoundPreset
import com.example.model.ConnectedDeviceState
import com.example.model.CustomAudioSetupUiState
import com.example.model.EqPresetsCatalog
import com.example.model.FlagshipSpeakerSpecs
import com.example.model.HearingCheckUiState
import com.example.model.ListeningMode
import com.example.model.LoudnessMaximizerSpecs
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.PhoneOrientationMode
import com.example.model.ProMaxHardwareRefinementSpecs
import com.example.model.SiliconDspSpecs
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.SpeakerTuningProfile
import com.example.model.StudioHiResSpecs
import com.example.model.TestAudioSource
import com.example.model.TrebleGainStagingBudget
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

    // Upper cutoff frequencies for the 7 PostEq iPhone 18 Pro Max Speaker DSP shaping bands:
    // [0]: <=55Hz sub-rumble cut, [1]: 55-88Hz 24dB/oct HPF shoulder, [2]: 88-220Hz chamber bass (135Hz +3.5dB),
    // [3]: 220-480Hz lower-mid scoop (340Hz -3.5dB), [4]: 480-3600Hz crisp presence (3.0kHz +2.0dB),
    // [5]: 3600-9000Hz harshness + de-esser dip (4.5kHz -3.0dB, 7.0kHz -3.5dB), [6]: 9000-20kHz gain-staged air (12.0kHz +1.0dB)
    private val speakerPostEqCutoffsHz = floatArrayOf(
        55f, 88f, 220f, 480f, 3600f, 9000f, 20000f
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
    var currentLandscapeStereoStage: Boolean = true
        private set

    @Volatile
    var currentSpeakerTuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC
        private set

    @Volatile
    var currentAudioMixMode: AudioMixMode = AudioMixMode.STANDARD
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
    var currentTenBandEnabled: Boolean = true
        private set

    @Volatile
    var currentThreeBandEnabled: Boolean = false
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
    var currentAccommodationsOutputGainDb: Float = 0f
        private set

    @Volatile
    var currentLoudnessMaximizerEnabled: Boolean = false
        private set

    @Volatile
    var currentLoudnessBoostPercent: Float = 0.50f
        private set

    @Volatile
    var currentSiliconDspEnabled: Boolean = true
        private set

    @Volatile
    var currentTrebleSmoothnessPercent: Float = 0.35f
        private set

    @Volatile
    var currentPhoneOrientationMode: PhoneOrientationMode = PhoneOrientationMode.AUTO_DETECT
        private set

    @Volatile
    var currentDeviceIsLandscape: Boolean = false
        private set

    @Volatile
    var currentMasterVolumePercent: Float = 0.75f
        private set

    @Volatile
    var currentProMaxBodyWarmthEnabled: Boolean = true
        private set

    @Volatile
    var currentAdaptiveMultibandEnabled: Boolean = true
        private set

    @Volatile
    var currentSpeakerPhaseAlignmentEnabled: Boolean = true
        private set

    @Volatile
    var currentOversampledEqEnabled: Boolean = true
        private set

    @Volatile
    var currentHarmonicSheenPercent: Float = 0.25f
        private set

    @Volatile
    var currentNoiseFloorGateEnabled: Boolean = true
        private set

    @Volatile
    var currentListeningMode: ListeningMode = ListeningMode.OFF
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
                        accommodationsGrDb = testSynthesizer.latestAccommodationsGrDb,
                        loudnessRmsGrDb = testSynthesizer.latestLoudnessRmsGrDb,
                        loudnessBrickwallGrDb = testSynthesizer.latestLoudnessBrickwallGrDb,
                        loudnessAntiClipActive = testSynthesizer.isLoudnessAntiClipActive,
                        siliconExcursionPct = testSynthesizer.latestSiliconExcursionPct,
                        siliconSvfCutoffHz = testSynthesizer.latestSiliconSvfCutoffHz,
                        siliconCoilTempC = testSynthesizer.latestSiliconCoilTempC,
                        siliconClassHRailV = testSynthesizer.latestSiliconClassHRailV,
                        siliconTruePeakGrDb = testSynthesizer.latestSiliconTruePeakGrDb,
                        trebleGainStagedCapActive = testSynthesizer.isTrebleCapActive,
                        trebleCappedBoostDb = testSynthesizer.latestTrebleCappedBoostDb,
                        resolvedOrientationLandscape = testSynthesizer.latestResolvedOrientationLandscape,
                        volumeContourStatus = testSynthesizer.latestVolumeContourStatus,
                        highVolumeDeHarshActive = testSynthesizer.isHighVolumeDeHarshActive,
                        multibandLowGrDb = testSynthesizer.latestMultibandLowGrDb,
                        multibandMidGrDb = testSynthesizer.latestMultibandMidGrDb,
                        multibandHighGrDb = testSynthesizer.latestMultibandHighGrDb,
                        phaseAlignmentActive = testSynthesizer.isPhaseAlignmentActive,
                        soundCheckCrestDb = testSynthesizer.latestSoundCheckCrestDb,
                        studioSampleRateHz = StudioHiResSpecs.SAMPLE_RATE_HZ,
                        oversampledEqActive = testSynthesizer.isOversampledEqActive,
                        harmonicSheenThdPct = testSynthesizer.latestHarmonicSheenThdPct,
                        noiseFloorGateMuted = testSynthesizer.isNoiseFloorGateMuted,
                        activeListeningMode = testSynthesizer.activeListeningMode,
                        listeningModeIsolationDb = testSynthesizer.latestListeningModeIsolationDb
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
            landscapeStereoStage = currentLandscapeStereoStage,
            speakerTuningProfile = currentSpeakerTuningProfile,
            audioMixMode = currentAudioMixMode,
            spatialMode = currentSpatialMode,
            spatialDepth = currentSpatialDepth,
            heightPresence = currentHeightPresence,
            vocalAnchor = currentVocalAnchor,
            soundCheckEnabled = currentSoundCheckEnabled,
            conversationAwarenessEnabled = currentConversationAwarenessEnabled,
            bgSoundPreset = currentBgSoundPreset,
            bgSoundVolume = currentBgSoundVolume,
            voiceIsolationEnabled = currentVoiceIsolationEnabled,
            tenBandEnabled = currentTenBandEnabled,
            threeBandEnabled = currentThreeBandEnabled,
            threeBandBassDb = currentThreeBandBassDb,
            threeBandMidsDb = currentThreeBandMidsDb,
            threeBandTrebleDb = currentThreeBandTrebleDb,
            accommodationsEnabled = currentAccommodationsEnabled,
            accommodationsToneProfile = currentAccommodationsToneProfile,
            accommodationsSoftIntensity = currentAccommodationsSoftIntensity,
            accommodationsApplyToMedia = currentAccommodationsApplyToMedia,
            accommodationsApplyToVoice = currentAccommodationsApplyToVoice,
            accommodationsOutputGainDb = currentAccommodationsOutputGainDb,
            loudnessMaximizerEnabled = currentLoudnessMaximizerEnabled,
            loudnessBoostPercent = currentLoudnessBoostPercent,
            siliconDspEnabled = currentSiliconDspEnabled,
            trebleSmoothnessPercent = currentTrebleSmoothnessPercent,
            phoneOrientationMode = currentPhoneOrientationMode,
            deviceIsLandscape = currentDeviceIsLandscape,
            masterVolumePercent = currentMasterVolumePercent,
            proMaxBodyWarmthEnabled = currentProMaxBodyWarmthEnabled,
            adaptiveMultibandEnabled = currentAdaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = currentSpeakerPhaseAlignmentEnabled,
            oversampledEqEnabled = currentOversampledEqEnabled,
            harmonicSheenPercent = currentHarmonicSheenPercent,
            noiseFloorGateEnabled = currentNoiseFloorGateEnabled
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

                val mbc = DynamicsProcessing.Mbc(true, false, 3)
                val mbcCutoffs = floatArrayOf(
                    AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ.toFloat(),
                    AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ.toFloat(),
                    22000f
                )
                for (b in mbcCutoffs.indices) {
                    mbc.setBand(
                        b,
                        DynamicsProcessing.MbcBand(
                            false,
                            mbcCutoffs[b],
                            AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS,
                            AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS,
                            AdaptiveMultibandPhaseSpecs.BAND_RATIO,
                            AdaptiveMultibandPhaseSpecs.MID_THRESHOLD_DB,
                            AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
                            -90f,
                            1f,
                            0f,
                            0f
                        )
                    )
                }
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
        landscapeStereoStage: Boolean = currentLandscapeStereoStage,
        speakerTuningProfile: SpeakerTuningProfile = currentSpeakerTuningProfile,
        audioMixMode: AudioMixMode = currentAudioMixMode,
        spatialMode: SpatialMode = currentSpatialMode,
        spatialDepth: Float = currentSpatialDepth,
        heightPresence: Float = currentHeightPresence,
        vocalAnchor: Boolean = currentVocalAnchor,
        soundCheckEnabled: Boolean = currentSoundCheckEnabled,
        conversationAwarenessEnabled: Boolean = currentConversationAwarenessEnabled,
        bgSoundPreset: BackgroundSoundPreset = currentBgSoundPreset,
        bgSoundVolume: Float = currentBgSoundVolume,
        voiceIsolationEnabled: Boolean = currentVoiceIsolationEnabled,
        tenBandEnabled: Boolean = currentTenBandEnabled,
        threeBandEnabled: Boolean = currentThreeBandEnabled,
        threeBandBassDb: Float = currentThreeBandBassDb,
        threeBandMidsDb: Float = currentThreeBandMidsDb,
        threeBandTrebleDb: Float = currentThreeBandTrebleDb,
        accommodationsEnabled: Boolean = currentAccommodationsEnabled,
        accommodationsToneProfile: AccommodationToneProfile = currentAccommodationsToneProfile,
        accommodationsSoftIntensity: SoftSoundIntensity = currentAccommodationsSoftIntensity,
        accommodationsApplyToMedia: Boolean = currentAccommodationsApplyToMedia,
        accommodationsApplyToVoice: Boolean = currentAccommodationsApplyToVoice,
        accommodationsOutputGainDb: Float = currentAccommodationsOutputGainDb,
        loudnessMaximizerEnabled: Boolean = currentLoudnessMaximizerEnabled,
        loudnessBoostPercent: Float = currentLoudnessBoostPercent,
        siliconDspEnabled: Boolean = currentSiliconDspEnabled,
        trebleSmoothnessPercent: Float = currentTrebleSmoothnessPercent,
        phoneOrientationMode: PhoneOrientationMode = currentPhoneOrientationMode,
        deviceIsLandscape: Boolean = currentDeviceIsLandscape,
        masterVolumePercent: Float = currentMasterVolumePercent,
        proMaxBodyWarmthEnabled: Boolean = currentProMaxBodyWarmthEnabled,
        adaptiveMultibandEnabled: Boolean = currentAdaptiveMultibandEnabled,
        speakerPhaseAlignmentEnabled: Boolean = currentSpeakerPhaseAlignmentEnabled,
        oversampledEqEnabled: Boolean = currentOversampledEqEnabled,
        harmonicSheenPercent: Float = currentHarmonicSheenPercent,
        noiseFloorGateEnabled: Boolean = currentNoiseFloorGateEnabled,
        listeningMode: ListeningMode = currentListeningMode
    ) {
        val normalizedThreeBand = threeBandEnabled && !tenBandEnabled
        val normalizedTenBand = !normalizedThreeBand

        currentTarget = target
        currentGainsDb = gainsDb
        currentPreampDb = preampDb
        currentBypassed = isBypassed
        currentLateNightEnabled = lateNightEnabled
        currentNightIntensity = nightIntensity
        currentSpeakerDspEnabled = speakerDspEnabled
        currentVirtualBassEnabled = virtualBassEnabled
        currentLandscapeStereoStage = landscapeStereoStage
        currentSpeakerTuningProfile = speakerTuningProfile
        currentAudioMixMode = audioMixMode
        currentSpatialMode = spatialMode
        currentSpatialDepth = spatialDepth
        currentHeightPresence = heightPresence
        currentVocalAnchor = vocalAnchor
        currentSoundCheckEnabled = soundCheckEnabled
        currentConversationAwarenessEnabled = conversationAwarenessEnabled
        currentBgSoundPreset = bgSoundPreset
        currentBgSoundVolume = bgSoundVolume
        currentVoiceIsolationEnabled = voiceIsolationEnabled
        currentTenBandEnabled = normalizedTenBand
        currentThreeBandEnabled = normalizedThreeBand
        currentThreeBandBassDb = threeBandBassDb
        currentThreeBandMidsDb = threeBandMidsDb
        currentThreeBandTrebleDb = threeBandTrebleDb
        currentAccommodationsEnabled = accommodationsEnabled
        currentAccommodationsToneProfile = accommodationsToneProfile
        currentAccommodationsSoftIntensity = accommodationsSoftIntensity
        currentAccommodationsApplyToMedia = accommodationsApplyToMedia
        currentAccommodationsApplyToVoice = accommodationsApplyToVoice
        currentAccommodationsOutputGainDb = accommodationsOutputGainDb.coerceIn(
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
        )
        currentLoudnessMaximizerEnabled = loudnessMaximizerEnabled
        currentLoudnessBoostPercent = loudnessBoostPercent.coerceIn(0f, 1f)
        currentSiliconDspEnabled = siliconDspEnabled
        currentTrebleSmoothnessPercent = trebleSmoothnessPercent.coerceIn(0f, 1f)
        currentPhoneOrientationMode = phoneOrientationMode
        currentDeviceIsLandscape = deviceIsLandscape
        currentMasterVolumePercent = masterVolumePercent.coerceIn(0f, 1f)
        currentProMaxBodyWarmthEnabled = proMaxBodyWarmthEnabled
        currentAdaptiveMultibandEnabled = adaptiveMultibandEnabled
        currentSpeakerPhaseAlignmentEnabled = speakerPhaseAlignmentEnabled
        currentOversampledEqEnabled = oversampledEqEnabled
        currentHarmonicSheenPercent = harmonicSheenPercent.coerceIn(0f, 1f)
        currentNoiseFloorGateEnabled = noiseFloorGateEnabled
        currentListeningMode = listeningMode

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
            landscapeStereoStage = isSpeakerDspActive && landscapeStereoStage,
            speakerTuningProfile = speakerTuningProfile,
            audioMixMode = audioMixMode,
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
            tenBandEnabled = normalizedTenBand,
            threeBandEnabled = normalizedThreeBand,
            threeBandBassDb = threeBandBassDb,
            threeBandMidsDb = threeBandMidsDb,
            threeBandTrebleDb = threeBandTrebleDb,
            accommodationsEnabled = accommodationsEnabled,
            accommodationsToneProfile = accommodationsToneProfile,
            accommodationsSoftIntensity = accommodationsSoftIntensity,
            accommodationsApplyToMedia = accommodationsApplyToMedia,
            accommodationsOutputGainDb = currentAccommodationsOutputGainDb,
            loudnessMaximizerEnabled = loudnessMaximizerEnabled,
            loudnessBoostPercent = currentLoudnessBoostPercent,
            siliconDspEnabled = siliconDspEnabled,
            trebleSmoothnessPercent = currentTrebleSmoothnessPercent,
            phoneOrientationMode = currentPhoneOrientationMode,
            deviceIsLandscape = currentDeviceIsLandscape,
            masterVolumePercent = currentMasterVolumePercent,
            proMaxBodyWarmthEnabled = isSpeakerDspActive && currentProMaxBodyWarmthEnabled,
            adaptiveMultibandEnabled = adaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = speakerPhaseAlignmentEnabled,
            oversampledEqEnabled = oversampledEqEnabled,
            harmonicSheenPercent = currentHarmonicSheenPercent,
            noiseFloorGateEnabled = noiseFloorGateEnabled,
            listeningMode = listeningMode
        )
        applyToAllSessions()
    }

    private fun applyToAllSessions() {
        var anyDpActive = false
        var anyLegacyActive = false
        val isAbHeld = _abCompareHeld.value
        val effectiveActive = !currentBypassed && !isAbHeld
        val siliconActive = effectiveActive && currentSiliconDspEnabled
        val isSpeakerDspActive = effectiveActive && (currentTarget == OutputTarget.SPEAKER) && currentSpeakerDspEnabled
        val isSpatialActive = effectiveActive && currentSpatialMode != SpatialMode.OFF
        val useThreeBand = currentThreeBandEnabled && !currentTenBandEnabled
        val useTenBand = !useThreeBand
        val threeBandActive = useThreeBand &&
            (kotlin.math.abs(currentThreeBandBassDb) > 0.01f ||
                kotlin.math.abs(currentThreeBandMidsDb) > 0.01f ||
                kotlin.math.abs(currentThreeBandTrebleDb) > 0.01f)
        val duckDb = if (currentDuckGainLinear < 0.99f) {
            (20.0 * log10(currentDuckGainLinear.toDouble().coerceAtLeast(0.15))).toFloat()
        } else {
            0f
        }
        val trebleBudget = TrebleGainStagingBudget.allocate(
            accommodationsActive = effectiveActive && currentAccommodationsEnabled && currentAccommodationsApplyToMedia,
            accommodationsProfile = currentAccommodationsToneProfile,
            accommodationsIntensity = currentAccommodationsSoftIntensity,
            spatialActive = isSpatialActive,
            spatialHeightPresence = currentHeightPresence,
            siliconDspActive = siliconActive
        )
        val resolvedLandscape = when (currentPhoneOrientationMode) {
            PhoneOrientationMode.AUTO_DETECT -> currentDeviceIsLandscape
            PhoneOrientationMode.PORTRAIT -> false
            PhoneOrientationMode.LANDSCAPE -> true
        }
        val volumeContourStatus = when {
            !effectiveActive -> "Bypassed"
            currentMasterVolumePercent < ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD -> "Low-Vol Contour (+115Hz / +2.8kHz)"
            currentMasterVolumePercent > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD -> "Projection + 5–9kHz De-Harsh Guard"
            else -> "Linear (40–80%)"
        }
        val highVolDeHarshActive = effectiveActive &&
            currentMasterVolumePercent > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD

        val resolvedListeningMode = if (
            effectiveActive &&
            (currentTarget == OutputTarget.HEADPHONES || currentTarget == OutputTarget.BLUETOOTH)
        ) {
            currentListeningMode
        } else {
            ListeningMode.OFF
        }
        val resolvedListeningIsolationDb = when (resolvedListeningMode) {
            ListeningMode.OFF -> 0f
            ListeningMode.TRANSPARENCY -> 1.6f
            ListeningMode.ADAPTIVE -> -10.0f
            ListeningMode.NOISE_CANCELLATION -> -28.0f
        }

        _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
            trebleGainStagedCapActive = effectiveActive && trebleBudget.isCapEngaged,
            trebleCappedBoostDb = if (effectiveActive) trebleBudget.cappedCombinedHfDb else 0f,
            resolvedOrientationLandscape = resolvedLandscape,
            volumeContourStatus = volumeContourStatus,
            highVolumeDeHarshActive = highVolDeHarshActive,
            phaseAlignmentActive = isSpeakerDspActive && currentSpeakerPhaseAlignmentEnabled,
            studioSampleRateHz = StudioHiResSpecs.SAMPLE_RATE_HZ,
            oversampledEqActive = effectiveActive && currentOversampledEqEnabled,
            activeListeningMode = resolvedListeningMode,
            listeningModeIsolationDb = resolvedListeningIsolationDb
        )

        for (bundle in sessionEffects.values) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bundle.dynamicsProcessing != null) {
                val dp = bundle.dynamicsProcessing
                try {
                    dp.enabled = !currentBypassed
                    if (isAbHeld && !currentBypassed) {
                        // A/B Compare: bypass all tonal/dynamics stages and match perceived loudness
                        val activeBandGains = if (useTenBand) currentGainsDb else List(10) { 0f }
                        val threeBandAvg = if (useThreeBand) {
                            (currentThreeBandBassDb * 0.25f + currentThreeBandMidsDb * 0.50f + currentThreeBandTrebleDb * 0.25f)
                        } else {
                            0f
                        }
                        val accommodationsMatchBoost = if (currentAccommodationsEnabled && currentAccommodationsApplyToMedia) {
                            (currentAccommodationsSoftIntensity.makeupGainDb + currentAccommodationsOutputGainDb) * 0.65f +
                                0.8f * currentAccommodationsSoftIntensity.eqScale
                        } else {
                            0f
                        }
                        val loudnessMatchBoost = if (currentLoudnessMaximizerEnabled) {
                            currentLoudnessBoostPercent * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB * 0.65f + 2.2f
                        } else {
                            0f
                        }
                        val matchGainDb = AudiogramCheckEngine.computeLoudnessMatchGainDb(
                            bandGainsDb = activeBandGains,
                            preampDb = currentPreampDb + threeBandAvg + accommodationsMatchBoost + loudnessMatchBoost,
                            lateNightEnabled = currentLateNightEnabled,
                            nightIntensity = currentNightIntensity,
                            target = currentTarget,
                            speakerDspEnabled = currentSpeakerDspEnabled,
                            spatialMode = currentSpatialMode,
                            audioMixMode = currentAudioMixMode
                        )
                        dp.setInputGainAllChannelsTo(matchGainDb + duckDb)
                        for (i in 0 until 10) {
                            dp.setPreEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], 0f)
                            )
                        }
                        dp.setPostEqAllChannelsTo(DynamicsProcessing.Eq(true, false, speakerPostEqCutoffsHz.size))
                        dp.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, false, 3))
                        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1.0f, 60.0f, 10.0f, -0.5f, 0.0f))
                    } else if (!currentBypassed) {
                        val accommodationsMediaActive = currentAccommodationsEnabled && currentAccommodationsApplyToMedia
                        val loudnessMaxActive = currentLoudnessMaximizerEnabled
                        val scGain = if (currentSoundCheckEnabled) systemSoundCheckTrimDb else 0f
                        dp.setInputGainAllChannelsTo(currentPreampDb + scGain + duckDb)
                        for (i in 0 until 10) {
                            val tenBandGain = if (useTenBand) {
                                currentGainsDb.getOrElse(i) { 0f }
                                    .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
                            } else {
                                0f
                            }
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
                                    intensity = currentAccommodationsSoftIntensity,
                                    hfScale = trebleBudget.accHfScale.toDouble()
                                )
                            } else {
                                0f
                            }
                            val trebleSmoothnessOffset = if (currentTrebleSmoothnessPercent > 0.005f) {
                                TrebleSmoothnessProcessor.computeResponseDbAt(
                                    freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                    smoothnessPercent = currentTrebleSmoothnessPercent
                                )
                            } else {
                                0f
                            }
                            val loudnessPresenceOffset = if (loudnessMaxActive) {
                                IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
                                    freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                    target = currentTarget
                                )
                            } else {
                                0f
                            }
                            val audioMixOffset = AudioMixStemShaper.computeFrequencyTiltDbAt(
                                freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                mode = currentAudioMixMode
                            )
                            val volumeContourOffset = IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(
                                freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                volumePercent = currentMasterVolumePercent
                            )
                            val listeningModeOffset = ListeningModeProcessor.computeContourResponseDbAt(
                                freqHz = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble(),
                                mode = resolvedListeningMode,
                                target = currentTarget
                            )
                            val combinedGain = (tenBandGain + toneOffset + accommodationsOffset + trebleSmoothnessOffset + loudnessPresenceOffset + audioMixOffset + volumeContourOffset + listeningModeOffset)
                                .coerceIn(-15f, 15f)
                            dp.setPreEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], combinedGain)
                            )
                        }

                        // PostEq stage: Voice Isolation / iPhone 18 Pro Max Speaker DSP / Gain-Staged Height Atmos contour
                        val heightScale = currentHeightPresence.coerceIn(0f, 1f)
                        val cinemaBoost = if (currentSpatialMode == SpatialMode.ATMOS_CINEMA) 1.25f else 1.0f
                        val cappedSpatialShelfDb = trebleBudget.spatialPinnaShelfDb
                        val bodyWarmthDb = if (isSpeakerDspActive && currentProMaxBodyWarmthEnabled) 2.5f else 0f
                        val portraitVocalBoostDb = if (isSpeakerDspActive && currentLandscapeStereoStage && !resolvedLandscape) 1.2f else 0f
                        val postEqEnabled = isSpeakerDspActive || isSpatialActive || currentVoiceIsolationEnabled
                        val postEqGains = when {
                            currentVoiceIsolationEnabled -> floatArrayOf(
                                -12.0f, // <55Hz rumble cut
                                -10.0f, // 55-95Hz sub cut
                                0.0f,   // 95-240Hz speech body
                                0.5f,   // 240-520Hz chest clarity
                                2.0f,   // 520-2400Hz speech presence
                                -2.5f,  // 2.4-4.6kHz sibilance tame
                                -10.0f  // >6.5kHz hiss cut
                            )
                            isSpeakerDspActive && currentSpeakerTuningProfile == SpeakerTuningProfile.PRO_MAX_MUSIC -> floatArrayOf(
                                -12.0f, // <=55Hz steep sub-rumble protection
                                -10.0f, // 55-88Hz steep 24dB/oct HPF shoulder
                                (if (currentVirtualBassEnabled) 3.5f else 1.5f) + bodyWarmthDb, // 135Hz punch + optional 125Hz Body Warmth (+2.5dB)
                                -3.5f,  // 340Hz lower-mid scoop (-3.5dB)
                                (if (isSpatialActive && currentVocalAnchor) 2.4f else 2.0f) + portraitVocalBoostDb, // 3.0kHz presence + Portrait vocal center focus
                                -3.2f,  // 4.5kHz -3.0dB harshness + 7.0kHz -3.5dB de-esser cut
                                if (isSpatialActive) (1.0f + cappedSpatialShelfDb * 0.5f * heightScale).coerceAtMost(3.0f) else 1.0f // 12kHz gain-staged air (+1.0dB)
                            )
                            isSpeakerDspActive -> floatArrayOf(
                                -12.0f, // <=55Hz sub-rumble cut
                                -11.0f, // 100Hz dialogue HPF protection
                                1.5f + bodyWarmthDb,   // 150Hz warm body + optional 125Hz Body Warmth
                                -2.0f,  // 340Hz anti-boxiness
                                3.2f + portraitVocalBoostDb,   // 1.8kHz–3kHz warm spoken dialogue focus
                                -3.5f,  // 4.5k-7.0kHz sibilance tame
                                0.5f    // gentle top air
                            )
                            isSpatialActive -> floatArrayOf(
                                0.4f, // Tight <55Hz center mono punch
                                0.2f, // 55-88Hz anchor
                                0.0f,
                                0.0f,
                                if (currentVocalAnchor) 1.4f else 0.0f, // Vocal Anchor lock
                                (-1.8f * heightScale * cinemaBoost).coerceIn(-3.5f, 0f), // Pinna dip zone
                                (cappedSpatialShelfDb * heightScale).coerceIn(0f, 3.0f)   // 12kHz gain-staged height air shelf
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

                        // Mbc stage: 3-Band Adaptive Dynamic Tone Balancer (220Hz / 3.8kHz, 2.2:1, 8ms/90ms)
                        // combined with Late Night / Boost Soft Sounds / Loudness / Excursion when active
                        val strongGlobalComp = currentLateNightEnabled || accommodationsMediaActive || loudnessMaxActive || isSpeakerDspActive
                        val compEnabled = strongGlobalComp || currentAdaptiveMultibandEnabled
                        val mbc = DynamicsProcessing.Mbc(true, compEnabled, 3)
                        val mbcCutoffs = floatArrayOf(
                            AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ.toFloat(),
                            AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ.toFloat(),
                            22000f
                        )
                        val multibandThresholds = floatArrayOf(
                            AdaptiveMultibandPhaseSpecs.LOW_THRESHOLD_DB,
                            AdaptiveMultibandPhaseSpecs.MID_THRESHOLD_DB,
                            AdaptiveMultibandPhaseSpecs.HIGH_THRESHOLD_DB
                        )
                        val mbcAttack = when {
                            loudnessMaxActive -> LoudnessMaximizerSpecs.RMS_ATTACK_MS
                            isSpeakerDspActive -> FlagshipSpeakerSpecs.EXCURSION_ATTACK_MS
                            currentLateNightEnabled -> currentNightIntensity.attackMs
                            else -> AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS
                        }
                        val mbcRelease = when {
                            loudnessMaxActive -> LoudnessMaximizerSpecs.RMS_RELEASE_MS
                            isSpeakerDspActive -> FlagshipSpeakerSpecs.EXCURSION_RELEASE_MS
                            currentLateNightEnabled -> currentNightIntensity.releaseMs
                            else -> AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS
                        }
                        val globalRatio = maxOf(
                            if (currentLateNightEnabled) currentNightIntensity.ratio else 1f,
                            if (accommodationsMediaActive) currentAccommodationsSoftIntensity.ratio else 1f,
                            if (loudnessMaxActive) LoudnessMaximizerSpecs.RMS_RATIO else 1f,
                            if (isSpeakerDspActive) FlagshipSpeakerSpecs.EXCURSION_RATIO else 1f
                        )
                        val mbcRatio = maxOf(
                            globalRatio,
                            if (currentAdaptiveMultibandEnabled) AdaptiveMultibandPhaseSpecs.BAND_RATIO else 1f
                        )
                        val globalThreshold = when {
                            currentLateNightEnabled -> currentNightIntensity.thresholdDb
                            loudnessMaxActive -> LoudnessMaximizerSpecs.RMS_THRESHOLD_DB
                            isSpeakerDspActive -> FlagshipSpeakerSpecs.EXCURSION_THRESHOLD_DB
                            accommodationsMediaActive -> currentAccommodationsSoftIntensity.thresholdDb
                            else -> AdaptiveMultibandPhaseSpecs.MID_THRESHOLD_DB
                        }
                        val mbcKnee = when {
                            loudnessMaxActive -> LoudnessMaximizerSpecs.RMS_KNEE_DB
                            isSpeakerDspActive -> FlagshipSpeakerSpecs.EXCURSION_KNEE_DB
                            currentLateNightEnabled -> currentNightIntensity.kneeDb
                            accommodationsMediaActive -> currentAccommodationsSoftIntensity.kneeDb
                            else -> AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB
                        }
                        val loudnessMakeupDb = if (loudnessMaxActive) {
                            currentLoudnessBoostPercent * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB
                        } else {
                            0f
                        }
                        val speakerMakeupDb = if (isSpeakerDspActive) FlagshipSpeakerSpecs.MAKEUP_GAIN_DB else 0f
                        val mbcMakeup = (if (currentLateNightEnabled) currentNightIntensity.makeupGainDb else 0f) +
                            (if (accommodationsMediaActive) {
                                currentAccommodationsSoftIntensity.makeupGainDb + currentAccommodationsOutputGainDb
                            } else {
                                0f
                            }) +
                            loudnessMakeupDb +
                            speakerMakeupDb

                        for (b in mbcCutoffs.indices) {
                            val bandThreshold = if (strongGlobalComp) globalThreshold else multibandThresholds[b]
                            mbc.setBand(
                                b,
                                DynamicsProcessing.MbcBand(
                                    compEnabled,
                                    mbcCutoffs[b],
                                    mbcAttack,
                                    mbcRelease,
                                    mbcRatio,
                                    bandThreshold,
                                    mbcKnee,
                                    if (currentVoiceIsolationEnabled) -38f else -90f,
                                    if (currentVoiceIsolationEnabled) 3.2f else 1f,
                                    0f,
                                    if (compEnabled) mbcMakeup else 0f
                                )
                            )
                        }
                        dp.setMbcAllChannelsTo(mbc)

                        // Limiter stage: Zero-Clipping Brickwall (-0.2 dBTP Silicon Core or -0.3 dB Speaker/Loudness)
                        val limiter = when {
                            siliconActive -> DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                0.2f,
                                35.0f,
                                20.0f,
                                SiliconDspSpecs.TRUE_PEAK_CEILING_DBTP.toFloat(),
                                0.0f
                            )
                            loudnessMaxActive -> DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                LoudnessMaximizerSpecs.BRICKWALL_ATTACK_MS,
                                LoudnessMaximizerSpecs.BRICKWALL_RELEASE_MS,
                                LoudnessMaximizerSpecs.BRICKWALL_RATIO,
                                LoudnessMaximizerSpecs.BRICKWALL_THRESHOLD_DB,
                                0.0f
                            )
                            isSpeakerDspActive -> DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                FlagshipSpeakerSpecs.BRICKWALL_ATTACK_MS,
                                FlagshipSpeakerSpecs.BRICKWALL_RELEASE_MS,
                                FlagshipSpeakerSpecs.BRICKWALL_RATIO,
                                FlagshipSpeakerSpecs.BRICKWALL_THRESHOLD_DB,
                                0.0f
                            )
                            else -> DynamicsProcessing.Limiter(
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
                            val activeEqDb = if (useTenBand) {
                                interpolateGainForFrequency(centerHz, currentGainsDb)
                            } else {
                                ThreeBandToneFilter.computeCombinedResponseDbAt(
                                    freqHz = centerHz.toDouble(),
                                    bassDb = currentThreeBandBassDb,
                                    midsDb = currentThreeBandMidsDb,
                                    trebleDb = currentThreeBandTrebleDb
                                )
                            }
                            val mappedDb = activeEqDb + currentPreampDb * 0.5f + speakerOffset
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
        val loudnessMaxActive = effectiveActive && currentLoudnessMaximizerEnabled
        val audioMixActive = effectiveActive && currentAudioMixMode != AudioMixMode.STANDARD
        val activeEqPrefix = if (useThreeBand) "3-Band" else "10-Band"
        val siliconTag = if (siliconActive) " • Silicon Core" else ""
        val listeningTag = if (resolvedListeningMode != ListeningMode.OFF) {
            " • ${resolvedListeningMode.label}"
        } else {
            ""
        }
        _dspStatusLabel.value = when {
            isAbHeld -> "A/B Compare: Original"
            currentBypassed -> "DSP Bypassed"
            currentVoiceIsolationEnabled -> "Voice Isolation$siliconTag$listeningTag"
            loudnessMaxActive -> "Loudness Maximizer +${(currentLoudnessBoostPercent * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB).toInt()}dB$siliconTag$listeningTag"
            audioMixActive -> "Audio Mix • ${currentAudioMixMode.label}$siliconTag$listeningTag"
            isSpatialActive && currentTarget == OutputTarget.SPEAKER -> "Speaker XTC Spatial$siliconTag"
            isSpatialActive -> "Spatial ${currentSpatialMode.label}$siliconTag$listeningTag"
            accMediaActive -> "Accommodations • ${currentAccommodationsToneProfile.label}$siliconTag$listeningTag"
            isSpeakerDspActive && currentLateNightEnabled -> "iPhone 18 DSP + Late Night$siliconTag"
            isSpeakerDspActive -> if (currentSpeakerTuningProfile == SpeakerTuningProfile.PRO_WARM_DIALOGUE) {
                "18 Pro • Dialogue DSP$siliconTag"
            } else if (currentLandscapeStereoStage && resolvedLandscape) {
                "18 Pro Max • Landscape XTC$siliconTag"
            } else if (currentLandscapeStereoStage) {
                "18 Pro Max • Portrait Vocal$siliconTag"
            } else {
                "18 Pro Max Speaker DSP$siliconTag"
            }
            anyDpActive && sessionCount > 0 -> {
                if (currentLateNightEnabled) "App DSP + Late Night ($sessionCount)$siliconTag$listeningTag" else "$activeEqPrefix App DSP ($sessionCount)$siliconTag$listeningTag"
            }
            anyDpActive -> {
                if (currentLateNightEnabled) "Global DSP + Late Night$siliconTag$listeningTag" else "$activeEqPrefix Global DSP$siliconTag$listeningTag"
            }
            anyLegacyActive -> "System EQ Active$siliconTag$listeningTag"
            !_globalMixFallbackEnabled.value && sessionCount == 0 -> "Waiting for Media App"
            siliconActive -> "Silicon Core • $activeEqPrefix DSP$listeningTag"
            else -> if (currentLateNightEnabled) "Studio DSP + Late Night$listeningTag" else "$activeEqPrefix Studio DSP$listeningTag"
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
                                thresholdDb = FlagshipSpeakerSpecs.EXCURSION_THRESHOLD_DB,
                                kneeDb = FlagshipSpeakerSpecs.EXCURSION_KNEE_DB,
                                ratio = FlagshipSpeakerSpecs.EXCURSION_RATIO
                            )
                        } else {
                            0f
                        }

                        val loudnessRmsGr = if (live && currentLoudnessMaximizerEnabled && !currentBypassed && !_abCompareHeld.value) {
                            DynamicRangeCompressor.computeGainReductionDb(
                                inputDb = peakDb,
                                thresholdDb = LoudnessMaximizerSpecs.RMS_THRESHOLD_DB,
                                kneeDb = LoudnessMaximizerSpecs.RMS_KNEE_DB,
                                ratio = LoudnessMaximizerSpecs.RMS_RATIO
                            )
                        } else {
                            0f
                        }
                        val loudnessMakeupDb = currentLoudnessBoostPercent * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB
                        val postRmsDb = peakDb + loudnessRmsGr + loudnessMakeupDb
                        val loudnessBrickwallGr = if (live && currentLoudnessMaximizerEnabled && !currentBypassed && !_abCompareHeld.value) {
                            DynamicRangeCompressor.computeGainReductionDb(
                                inputDb = postRmsDb,
                                thresholdDb = LoudnessMaximizerSpecs.BRICKWALL_THRESHOLD_DB,
                                kneeDb = LoudnessMaximizerSpecs.BRICKWALL_KNEE_DB,
                                ratio = LoudnessMaximizerSpecs.BRICKWALL_RATIO
                            )
                        } else {
                            0f
                        }
                        val loudnessAntiClip = live && currentLoudnessMaximizerEnabled && !currentBypassed && !_abCompareHeld.value &&
                            (loudnessBrickwallGr < -0.15f || postRmsDb > LoudnessMaximizerSpecs.BRICKWALL_THRESHOLD_DB)

                        val desiredSoundCheck = if (live && currentSoundCheckEnabled) {
                            SoundCheckNormalizer.computeTargetTrimDb(peakDb - 3f)
                        } else {
                            0f
                        }
                        systemSoundCheckTrimDb = systemSoundCheckTrimDb * 0.85f + desiredSoundCheck * 0.15f
                        if (testSynthesizer.currentSource.value == TestAudioSource.OFF) {
                            val silActive = live && currentSiliconDspEnabled && !currentBypassed && !_abCompareHeld.value
                            val estExcursionPct = if (silActive) (peak * 92f).coerceIn(0f, 100f) else 0f
                            val estSvfHz = if (currentTarget == OutputTarget.SPEAKER) {
                                val ratio = ((estExcursionPct / 100f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                (SiliconDspSpecs.SVF_MIN_HZ + ratio * (SiliconDspSpecs.SVF_MAX_HZ - SiliconDspSpecs.SVF_MIN_HZ)).toFloat()
                            } else {
                                SiliconDspSpecs.SVF_HEADPHONES_HZ.toFloat()
                            }
                            val prevTemp = _audioLabTelemetry.value.siliconCoilTempC
                            val targetTemp = if (silActive) (25f + peak * peak * 42f).coerceIn(25f, 95f) else 25f
                            val smoothedTemp = prevTemp * 0.94f + targetTemp * 0.06f
                            val estRailV = if (silActive) {
                                (SiliconDspSpecs.CLASS_H_MIN_VOLTS +
                                    (SiliconDspSpecs.CLASS_H_MAX_VOLTS - SiliconDspSpecs.CLASS_H_MIN_VOLTS) *
                                    peak.coerceIn(0f, 1f)).toFloat()
                            } else {
                                SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat()
                            }
                            val estTpGr = if (silActive) {
                                DynamicRangeCompressor.computeGainReductionDb(
                                    inputDb = peakDb,
                                    thresholdDb = SiliconDspSpecs.TRUE_PEAK_CEILING_DBTP.toFloat(),
                                    kneeDb = 0f,
                                    ratio = 20f
                                )
                            } else {
                                0f
                            }
                            val mbActive = live && currentAdaptiveMultibandEnabled && !currentBypassed && !_abCompareHeld.value
                            val estLowGr = if (mbActive) {
                                DynamicRangeCompressor.computeGainReductionDb(
                                    inputDb = peakDb - 1.5f,
                                    thresholdDb = AdaptiveMultibandPhaseSpecs.LOW_THRESHOLD_DB,
                                    kneeDb = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
                                    ratio = AdaptiveMultibandPhaseSpecs.BAND_RATIO
                                )
                            } else {
                                0f
                            }
                            val estMidGr = if (mbActive) {
                                DynamicRangeCompressor.computeGainReductionDb(
                                    inputDb = peakDb - 2.0f,
                                    thresholdDb = AdaptiveMultibandPhaseSpecs.MID_THRESHOLD_DB,
                                    kneeDb = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
                                    ratio = AdaptiveMultibandPhaseSpecs.BAND_RATIO
                                )
                            } else {
                                0f
                            }
                            val estHighGr = if (mbActive) {
                                DynamicRangeCompressor.computeGainReductionDb(
                                    inputDb = peakDb - 4.0f,
                                    thresholdDb = AdaptiveMultibandPhaseSpecs.HIGH_THRESHOLD_DB,
                                    kneeDb = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
                                    ratio = AdaptiveMultibandPhaseSpecs.BAND_RATIO
                                )
                            } else {
                                0f
                            }
                            var sumSq = 0.0
                            for (s in samples) sumSq += s * s
                            val rmsDb = if (sumSq > 1e-8) {
                                (10.0 * log10(sumSq / samples.size)).toFloat().coerceIn(-60f, 0f)
                            } else {
                                -60f
                            }
                            val estCrestDb = if (live) (peakDb - rmsDb).coerceIn(0f, 24f) else 0f

                            _audioLabTelemetry.value = _audioLabTelemetry.value.copy(
                                inputLufs = (peakDb - 3f).coerceIn(-60f, 0f),
                                soundCheckTrimDb = systemSoundCheckTrimDb,
                                soundCheckCrestDb = estCrestDb,
                                voiceGateAttenuationDb = if (live || !currentVoiceIsolationEnabled) 0f else -24f,
                                loudnessRmsGrDb = loudnessRmsGr,
                                loudnessBrickwallGrDb = loudnessBrickwallGr,
                                loudnessAntiClipActive = loudnessAntiClip,
                                siliconExcursionPct = estExcursionPct,
                                siliconSvfCutoffHz = estSvfHz,
                                siliconCoilTempC = smoothedTemp,
                                siliconClassHRailV = estRailV,
                                siliconTruePeakGrDb = estTpGr,
                                multibandLowGrDb = estLowGr,
                                multibandMidGrDb = estMidGr,
                                multibandHighGrDb = estHighGr,
                                phaseAlignmentActive = !currentBypassed && !_abCompareHeld.value &&
                                    currentTarget == OutputTarget.SPEAKER &&
                                    currentSpeakerDspEnabled &&
                                    currentSpeakerPhaseAlignmentEnabled,
                                studioSampleRateHz = StudioHiResSpecs.SAMPLE_RATE_HZ,
                                oversampledEqActive = !currentBypassed && !_abCompareHeld.value && currentOversampledEqEnabled,
                                harmonicSheenThdPct = if (live && !currentBypassed && !_abCompareHeld.value) {
                                    (currentHarmonicSheenPercent * 0.28f).coerceAtMost(StudioHiResSpecs.HARMONIC_SHEEN_MAX_THD_PCT)
                                } else {
                                    0f
                                },
                                noiseFloorGateMuted = !live && currentNoiseFloorGateEnabled && !currentBypassed && !_abCompareHeld.value,
                                activeListeningMode = if (!currentBypassed && !_abCompareHeld.value &&
                                    (currentTarget == OutputTarget.HEADPHONES || currentTarget == OutputTarget.BLUETOOTH)
                                ) currentListeningMode else ListeningMode.OFF,
                                listeningModeIsolationDb = when {
                                    currentBypassed || _abCompareHeld.value || currentTarget == OutputTarget.SPEAKER -> 0f
                                    currentListeningMode == ListeningMode.TRANSPARENCY -> 1.6f
                                    currentListeningMode == ListeningMode.ADAPTIVE -> -10.0f
                                    currentListeningMode == ListeningMode.NOISE_CANCELLATION -> -28.0f
                                    else -> 0f
                                }
                            )
                        }

                        _systemVisualizerFrame.value = _systemVisualizerFrame.value.copy(
                            waveformSamples = samples,
                            peakDb = peakDb,
                            gainReductionDb = minOf(lateNightGr, speakerLimiterGr, loudnessRmsGr, loudnessBrickwallGr),
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
            landscapeStereoStage = isSpeakerDspActive && currentLandscapeStereoStage,
            speakerTuningProfile = currentSpeakerTuningProfile,
            audioMixMode = currentAudioMixMode,
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
            tenBandEnabled = currentTenBandEnabled,
            threeBandEnabled = currentThreeBandEnabled,
            threeBandBassDb = currentThreeBandBassDb,
            threeBandMidsDb = currentThreeBandMidsDb,
            threeBandTrebleDb = currentThreeBandTrebleDb,
            accommodationsEnabled = currentAccommodationsEnabled,
            accommodationsToneProfile = currentAccommodationsToneProfile,
            accommodationsSoftIntensity = currentAccommodationsSoftIntensity,
            accommodationsApplyToMedia = currentAccommodationsApplyToMedia,
            accommodationsOutputGainDb = currentAccommodationsOutputGainDb,
            loudnessMaximizerEnabled = currentLoudnessMaximizerEnabled,
            loudnessBoostPercent = currentLoudnessBoostPercent,
            siliconDspEnabled = currentSiliconDspEnabled,
            trebleSmoothnessPercent = currentTrebleSmoothnessPercent,
            phoneOrientationMode = currentPhoneOrientationMode,
            deviceIsLandscape = currentDeviceIsLandscape,
            masterVolumePercent = currentMasterVolumePercent,
            proMaxBodyWarmthEnabled = isSpeakerDspActive && currentProMaxBodyWarmthEnabled,
            adaptiveMultibandEnabled = currentAdaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = currentSpeakerPhaseAlignmentEnabled,
            oversampledEqEnabled = currentOversampledEqEnabled,
            harmonicSheenPercent = currentHarmonicSheenPercent,
            noiseFloorGateEnabled = currentNoiseFloorGateEnabled,
            listeningMode = currentListeningMode
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
