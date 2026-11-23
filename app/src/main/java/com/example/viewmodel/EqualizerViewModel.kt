package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AudioSessionRegistry
import com.example.EqualizerService
import com.example.audio.EqualizerEngineHolder
import com.example.data.EqPreferencesRepository
import com.example.model.AccommodationToneProfile
import com.example.model.AudioLabTelemetry
import com.example.model.AudioMixMode
import com.example.model.BackgroundSoundPreset
import com.example.model.ConnectedDeviceState
import com.example.model.CustomAudioSetupUiState
import com.example.model.EqPreset
import com.example.model.EqPresetsCatalog
import com.example.model.HearingCheckUiState
import com.example.model.NightIntensity
import com.example.model.OutputEqProfile
import com.example.model.OutputTarget
import com.example.model.PhoneOrientationMode
import com.example.model.SetupAuditionSlot
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.SpeakerTuningProfile
import com.example.model.TestAudioSource
import com.example.model.ThreeBandEqRanges
import com.example.model.VisualizerFrame
import com.example.model.VisualizerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class EqualizerUiState(
    val selectedTarget: OutputTarget = OutputTarget.SPEAKER,
    val autoSwitchByDevice: Boolean = true,
    val globalMixFallbackEnabled: Boolean = true,
    val serviceRunning: Boolean = false,
    val activeAudioSessions: Map<Int, String> = emptyMap(),
    val visualizerMode: VisualizerMode = VisualizerMode.HYBRID,
    val testAudioSource: TestAudioSource = TestAudioSource.OFF,
    val profiles: Map<OutputTarget, OutputEqProfile> = OutputTarget.entries.associateWith {
        OutputEqProfile(target = it)
    },
    val connectedDevices: ConnectedDeviceState = ConnectedDeviceState(),
    val visualizerFrame: VisualizerFrame = VisualizerFrame(),
    val dspStatusLabel: String = "10-Band Studio DSP",
    val hasAudioPermission: Boolean = false,
    val audioLabTelemetry: AudioLabTelemetry = AudioLabTelemetry(),
    val hearingCheckState: HearingCheckUiState = HearingCheckUiState(),
    val customAudioSetupState: CustomAudioSetupUiState = CustomAudioSetupUiState(),
    val abCompareHeld: Boolean = false,
    val deviceIsLandscape: Boolean = false
) {
    val activeProfile: OutputEqProfile
        get() = profiles[selectedTarget] ?: OutputEqProfile(target = selectedTarget)

    val activePresetName: String
        get() = when (activeProfile.selectedPresetId) {
            EqPresetsCatalog.CUSTOM_ID -> when (selectedTarget) {
                OutputTarget.SPEAKER -> "Speaker Tuned"
                OutputTarget.HEADPHONES -> "Headphones Tuned"
                OutputTarget.BLUETOOTH -> "Custom"
            }

            else -> EqPresetsCatalog.findById(activeProfile.selectedPresetId)?.name ?: "Flat"
        }
}

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EqPreferencesRepository(application)
    private val audioEngine = EqualizerEngineHolder.get(application)

    private val selectedTargetFlow = MutableStateFlow(OutputTarget.SPEAKER)
    private val autoSwitchFlow = MutableStateFlow(true)
    private val visualizerModeFlow = MutableStateFlow(VisualizerMode.HYBRID)
    private val deviceIsLandscapeFlow = MutableStateFlow(false)
    private val profilesFlow = MutableStateFlow(
        OutputTarget.entries.associateWith { OutputEqProfile(target = it) }
    )

    private var lastObservedHardwareRoute: OutputTarget? = null
    private val preLateNightProfiles = mutableMapOf<OutputTarget, OutputEqProfile>()

    val uiState: StateFlow<EqualizerUiState> = combine(
        combine(
            selectedTargetFlow,
            autoSwitchFlow,
            visualizerModeFlow,
            profilesFlow,
            deviceIsLandscapeFlow
        ) { target, autoSwitch, visMode, profiles, isLandscape ->
            CorePrefState(target, autoSwitch, visMode, profiles, isLandscape)
        },
        combine(
            audioEngine.connectedDevices,
            audioEngine.visualizerFrame,
            audioEngine.dspStatusLabel,
            audioEngine.hasAudioPermission,
            audioEngine.testSynthesizer.currentSource
        ) { devices, frame, status, hasPerm, testSource ->
            EngineStreamState(devices, frame, status, hasPerm, testSource)
        },
        combine(
            audioEngine.globalMixFallbackEnabled,
            EqualizerService.isRunning,
            AudioSessionRegistry.activeSessions,
            audioEngine.audioLabTelemetry,
            audioEngine.hearingCheckState,
            audioEngine.customAudioSetupState,
            audioEngine.abCompareHeld
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            RoutingAndLabState(
                globalMix = arr[0] as Boolean,
                serviceRunning = arr[1] as Boolean,
                sessions = arr[2] as Map<Int, String>,
                labTelemetry = arr[3] as AudioLabTelemetry,
                hearingCheck = arr[4] as HearingCheckUiState,
                customAudioSetup = arr[5] as CustomAudioSetupUiState,
                abHeld = arr[6] as Boolean
            )
        }
    ) { core, engine, routing ->
        EqualizerUiState(
            selectedTarget = core.target,
            autoSwitchByDevice = core.autoSwitch,
            globalMixFallbackEnabled = routing.globalMix,
            serviceRunning = routing.serviceRunning,
            activeAudioSessions = routing.sessions,
            visualizerMode = core.visMode,
            testAudioSource = engine.testSource,
            profiles = core.profiles,
            connectedDevices = engine.devices,
            visualizerFrame = engine.frame,
            dspStatusLabel = engine.status,
            hasAudioPermission = engine.hasPerm,
            audioLabTelemetry = routing.labTelemetry,
            hearingCheckState = routing.hearingCheck,
            customAudioSetupState = routing.customAudioSetup,
            abCompareHeld = routing.abHeld,
            deviceIsLandscape = core.isLandscape
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EqualizerUiState()
    )

    private data class RoutingAndLabState(
        val globalMix: Boolean,
        val serviceRunning: Boolean,
        val sessions: Map<Int, String>,
        val labTelemetry: AudioLabTelemetry,
        val hearingCheck: HearingCheckUiState,
        val customAudioSetup: CustomAudioSetupUiState,
        val abHeld: Boolean
    )

    private data class CorePrefState(
        val target: OutputTarget,
        val autoSwitch: Boolean,
        val visMode: VisualizerMode,
        val profiles: Map<OutputTarget, OutputEqProfile>,
        val isLandscape: Boolean
    )

    private data class EngineStreamState(
        val devices: ConnectedDeviceState,
        val frame: VisualizerFrame,
        val status: String,
        val hasPerm: Boolean,
        val testSource: TestAudioSource
    )

    init {
        viewModelScope.launch {
            val stored = repository.preferencesFlow.first()
            profilesFlow.value = stored.profiles
            autoSwitchFlow.value = stored.autoSwitchByDevice
            visualizerModeFlow.value = stored.visualizerMode
            audioEngine.setGlobalMixFallback(stored.globalMixFallbackEnabled)

            val hwRoute = audioEngine.connectedDevices.value.activeRoute
            lastObservedHardwareRoute = hwRoute
            val initialTarget = if (stored.autoSwitchByDevice) hwRoute else stored.selectedTarget
            selectedTargetFlow.value = initialTarget

            val active = stored.profiles[initialTarget] ?: OutputEqProfile(target = initialTarget)
            applyToAudioEngine(active)
        }

        // Auto-switch EQ profile when Headphones or Bluetooth Speaker connects/disconnects
        viewModelScope.launch {
            audioEngine.connectedDevices.collect { devices ->
                val previous = lastObservedHardwareRoute
                lastObservedHardwareRoute = devices.activeRoute
                if (previous != null && previous != devices.activeRoute && autoSwitchFlow.value) {
                    selectOutputTarget(devices.activeRoute)
                }
            }
        }
    }

    fun selectOutputTarget(target: OutputTarget) {
        selectedTargetFlow.value = target
        val profile = profilesFlow.value[target] ?: OutputEqProfile(target = target)
        applyToAudioEngine(profile)
        viewModelScope.launch {
            repository.saveSelectedTarget(target)
        }
    }

    fun toggleAutoSwitch() {
        val next = !autoSwitchFlow.value
        autoSwitchFlow.value = next
        if (next) {
            selectOutputTarget(audioEngine.connectedDevices.value.activeRoute)
        }
        viewModelScope.launch {
            repository.saveAutoSwitch(next)
        }
    }

    fun toggleGlobalMixFallback() {
        val next = !audioEngine.globalMixFallbackEnabled.value
        audioEngine.setGlobalMixFallback(next)
        viewModelScope.launch {
            repository.saveGlobalMixFallback(next)
        }
    }

    fun toggleBackgroundService() {
        val app = getApplication<Application>()
        if (EqualizerService.isRunning.value) {
            EqualizerService.stop(app)
        } else {
            EqualizerService.start(app)
        }
    }

    fun ensureServiceStarted() {
        val app = getApplication<Application>()
        if (!EqualizerService.isRunning.value) {
            EqualizerService.start(app)
        }
    }

    fun updateBandGain(bandIndex: Int, rawGainDb: Float) {
        if (bandIndex !in 0..9) return
        val snapped = snapHalfDb(rawGainDb)
        val current = currentProfile()
        val updatedGains = current.bandGainsDb.toMutableList().apply {
            this[bandIndex] = snapped
        }
        val matchedPreset = findMatchingPresetId(updatedGains)
        val updated = current.copy(
            bandGainsDb = updatedGains,
            selectedPresetId = matchedPreset,
            tenBandEnabled = true,
            threeBandEnabled = false
        )
        commitProfile(updated)
    }

    fun updatePreamp(rawPreampDb: Float) {
        val snapped = snapHalfDb(rawPreampDb)
        val updated = currentProfile().copy(preampDb = snapped)
        commitProfile(updated)
    }

    fun applyPreset(preset: EqPreset) {
        val current = currentProfile()
        val isLateNightPreset = preset.id == EqPresetsCatalog.LATE_NIGHT_ID
        if (isLateNightPreset && !current.lateNightEnabled) {
            preLateNightProfiles[current.target] = current
        }
        val updated = current.copy(
            bandGainsDb = preset.gainsDb.toList(),
            preampDb = preset.preampDb,
            isBypassed = false,
            selectedPresetId = preset.id,
            lateNightEnabled = isLateNightPreset,
            tenBandEnabled = true,
            threeBandEnabled = false
        )
        commitProfile(updated)
    }

    fun selectEqMode(useThreeBand: Boolean) {
        val current = currentProfile()
        val updated = current.copy(
            tenBandEnabled = !useThreeBand,
            threeBandEnabled = useThreeBand,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun toggleTenBandEnabled() {
        val current = currentProfile()
        val nextTenBand = !current.tenBandEnabled
        val updated = current.copy(
            tenBandEnabled = nextTenBand,
            threeBandEnabled = !nextTenBand,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun toggleLateNightMode() {
        val current = currentProfile()
        if (!current.lateNightEnabled) {
            preLateNightProfiles[current.target] = current
            val updated = current.copy(
                bandGainsDb = EqPresetsCatalog.LATE_NIGHT_PRESET.gainsDb.toList(),
                preampDb = 0f,
                isBypassed = false,
                selectedPresetId = EqPresetsCatalog.LATE_NIGHT_ID,
                lateNightEnabled = true,
                tenBandEnabled = true,
                threeBandEnabled = false
            )
            commitProfile(updated)
        } else {
            val restored = preLateNightProfiles.remove(current.target)
            val updated = if (restored != null && current.selectedPresetId == EqPresetsCatalog.LATE_NIGHT_ID) {
                restored.copy(
                    lateNightEnabled = false,
                    nightIntensity = current.nightIntensity
                )
            } else {
                current.copy(
                    lateNightEnabled = false,
                    bandGainsDb = if (current.selectedPresetId == EqPresetsCatalog.LATE_NIGHT_ID) {
                        List(10) { 0f }
                    } else {
                        current.bandGainsDb
                    },
                    selectedPresetId = if (current.selectedPresetId == EqPresetsCatalog.LATE_NIGHT_ID) {
                        EqPresetsCatalog.FLAT_ID
                    } else {
                        current.selectedPresetId
                    }
                )
            }
            commitProfile(updated)
        }
    }

    fun setNightIntensity(intensity: NightIntensity) {
        val current = currentProfile()
        val updated = current.copy(nightIntensity = intensity)
        commitProfile(updated)
    }

    fun toggleSpeakerDsp() {
        val current = currentProfile()
        val updated = current.copy(speakerDspEnabled = !current.speakerDspEnabled)
        commitProfile(updated)
    }

    fun toggleVirtualBass() {
        val current = currentProfile()
        val updated = current.copy(
            virtualBassEnabled = !current.virtualBassEnabled,
            speakerDspEnabled = if (!current.virtualBassEnabled) true else current.speakerDspEnabled
        )
        commitProfile(updated)
    }

    fun toggleLandscapeStereoStage() {
        val current = currentProfile()
        val next = !current.landscapeStereoStage
        val updated = current.copy(
            landscapeStereoStage = next,
            speakerDspEnabled = if (next) true else current.speakerDspEnabled
        )
        commitProfile(updated)
    }

    fun selectSpeakerTuningProfile(profile: SpeakerTuningProfile) {
        val current = currentProfile()
        val updated = current.copy(
            speakerTuningProfile = profile,
            speakerDspEnabled = true,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun updateDetectedDeviceOrientation(isLandscape: Boolean) {
        if (deviceIsLandscapeFlow.value == isLandscape) return
        deviceIsLandscapeFlow.value = isLandscape
        val active = currentProfile()
        if (active.phoneOrientationMode == PhoneOrientationMode.AUTO_DETECT) {
            applyToAudioEngine(active)
        }
    }

    fun setPhoneOrientationMode(mode: PhoneOrientationMode) {
        val current = currentProfile()
        val updated = current.copy(
            phoneOrientationMode = mode,
            speakerDspEnabled = true,
            landscapeStereoStage = true,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun toggleProMaxBodyWarmth() {
        val current = currentProfile()
        val next = !current.proMaxBodyWarmthEnabled
        val updated = current.copy(
            proMaxBodyWarmthEnabled = next,
            speakerDspEnabled = if (next) true else current.speakerDspEnabled,
            isBypassed = if (next && current.isBypassed) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun updateMasterVolume(rawPercent: Float) {
        val current = currentProfile()
        val snapped = ((rawPercent.coerceIn(0f, 1f) * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(masterVolumePercent = snapped)
        commitProfile(updated)
    }

    fun setAudioMixMode(mode: AudioMixMode) {
        val current = currentProfile()
        val updated = current.copy(
            audioMixMode = mode,
            isBypassed = if (mode != AudioMixMode.STANDARD) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun setSpatialMode(mode: SpatialMode) {
        val current = currentProfile()
        val updated = when (mode) {
            SpatialMode.OFF -> current.copy(spatialMode = SpatialMode.OFF)
            SpatialMode.ATMOS_MUSIC,
            SpatialMode.ATMOS_CINEMA -> current.copy(
                spatialMode = mode,
                spatialDepth = mode.defaultDepth,
                heightPresence = mode.defaultHeight,
                isBypassed = false
            )
        }
        commitProfile(updated)
    }

    fun updateSpatialDepth(depth: Float) {
        val current = currentProfile()
        val clamped = ((depth * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(
            spatialDepth = clamped,
            spatialMode = if (current.spatialMode == SpatialMode.OFF && clamped > 0f) {
                SpatialMode.ATMOS_MUSIC
            } else {
                current.spatialMode
            }
        )
        commitProfile(updated)
    }

    fun updateHeightPresence(height: Float) {
        val current = currentProfile()
        val clamped = ((height * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(
            heightPresence = clamped,
            spatialMode = if (current.spatialMode == SpatialMode.OFF && clamped > 0f) {
                SpatialMode.ATMOS_MUSIC
            } else {
                current.spatialMode
            }
        )
        commitProfile(updated)
    }

    fun toggleVocalAnchor() {
        val current = currentProfile()
        val updated = current.copy(vocalAnchor = !current.vocalAnchor)
        commitProfile(updated)
    }

    fun toggleSoundCheck() {
        val current = currentProfile()
        val updated = current.copy(soundCheckEnabled = !current.soundCheckEnabled)
        commitProfile(updated)
    }

    fun toggleConversationAwareness() {
        val current = currentProfile()
        val updated = current.copy(conversationAwarenessEnabled = !current.conversationAwarenessEnabled)
        commitProfile(updated)
    }

    fun selectBackgroundSound(preset: BackgroundSoundPreset) {
        val current = currentProfile()
        val updated = current.copy(backgroundSoundPreset = preset)
        commitProfile(updated)
    }

    fun updateBackgroundVolume(volume: Float) {
        val current = currentProfile()
        val clamped = ((volume * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(backgroundSoundVolume = clamped)
        commitProfile(updated)
    }

    fun toggleVoiceIsolation() {
        val current = currentProfile()
        val updated = current.copy(voiceIsolationEnabled = !current.voiceIsolationEnabled)
        commitProfile(updated)
    }

    fun updateThreeBandBass(rawDb: Float) {
        val clamped = snapHalfDbRange(rawDb, ThreeBandEqRanges.BASS_MIN_DB, ThreeBandEqRanges.BASS_MAX_DB)
        val updated = currentProfile().copy(
            threeBandBassDb = clamped,
            threeBandEnabled = true,
            tenBandEnabled = false,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun updateThreeBandMids(rawDb: Float) {
        val clamped = snapHalfDbRange(rawDb, ThreeBandEqRanges.MIDS_MIN_DB, ThreeBandEqRanges.MIDS_MAX_DB)
        val updated = currentProfile().copy(
            threeBandMidsDb = clamped,
            threeBandEnabled = true,
            tenBandEnabled = false,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun updateThreeBandTreble(rawDb: Float) {
        val clamped = snapHalfDbRange(rawDb, ThreeBandEqRanges.TREBLE_MIN_DB, ThreeBandEqRanges.TREBLE_MAX_DB)
        val updated = currentProfile().copy(
            threeBandTrebleDb = clamped,
            threeBandEnabled = true,
            tenBandEnabled = false,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun toggleThreeBandEnabled() {
        val current = currentProfile()
        val nextThreeBand = !current.threeBandEnabled
        val updated = current.copy(
            threeBandEnabled = nextThreeBand,
            tenBandEnabled = !nextThreeBand,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun resetThreeBand() {
        val current = currentProfile()
        val updated = current.copy(
            threeBandEnabled = true,
            tenBandEnabled = false,
            threeBandBassDb = 0f,
            threeBandMidsDb = 0f,
            threeBandTrebleDb = 0f
        )
        commitProfile(updated)
    }

    fun toggleHeadphoneAccommodations() {
        val current = currentProfile()
        val updated = current.copy(
            accommodationsEnabled = !current.accommodationsEnabled,
            isBypassed = if (!current.accommodationsEnabled) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun setAccommodationsToneProfile(profile: AccommodationToneProfile) {
        val current = currentProfile()
        val updated = current.copy(
            accommodationsToneProfile = profile,
            accommodationsEnabled = true,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun setAccommodationsSoftIntensity(intensity: SoftSoundIntensity) {
        val current = currentProfile()
        val updated = current.copy(
            accommodationsSoftIntensity = intensity,
            accommodationsEnabled = true,
            isBypassed = false
        )
        commitProfile(updated)
    }

    fun toggleAccommodationsMedia() {
        val current = currentProfile()
        val updated = current.copy(accommodationsApplyToMedia = !current.accommodationsApplyToMedia)
        commitProfile(updated)
    }

    fun toggleAccommodationsVoice() {
        val current = currentProfile()
        val updated = current.copy(accommodationsApplyToVoice = !current.accommodationsApplyToVoice)
        commitProfile(updated)
    }

    fun updateAccommodationsOutputGain(rawDb: Float) {
        val current = currentProfile()
        val snapped = snapHalfDbRange(
            rawDb,
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
        )
        val updated = current.copy(
            accommodationsOutputGainDb = snapped,
            accommodationsEnabled = if (snapped > 0f) true else current.accommodationsEnabled,
            isBypassed = if (snapped > 0f) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun toggleLoudnessMaximizer() {
        val current = currentProfile()
        val enabling = !current.loudnessMaximizerEnabled
        val updated = current.copy(
            loudnessMaximizerEnabled = enabling,
            isBypassed = if (enabling) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun updateLoudnessBoostPercent(rawPercent: Float) {
        val current = currentProfile()
        val snapped = ((rawPercent.coerceIn(0f, 1f) * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(
            loudnessBoostPercent = snapped,
            loudnessMaximizerEnabled = if (snapped > 0f) true else current.loudnessMaximizerEnabled,
            isBypassed = if (snapped > 0f) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun toggleSiliconDsp() {
        val current = currentProfile()
        val enabling = !current.siliconDspEnabled
        val updated = current.copy(
            siliconDspEnabled = enabling,
            isBypassed = if (enabling) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun updateTrebleSmoothness(rawPercent: Float) {
        val current = currentProfile()
        val snapped = ((rawPercent.coerceIn(0f, 1f) * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
        val updated = current.copy(
            trebleSmoothnessPercent = snapped,
            isBypassed = if (snapped > 0f && current.isBypassed) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun toggleAdaptiveMultiband() {
        val current = currentProfile()
        val enabling = !current.adaptiveMultibandEnabled
        val updated = current.copy(
            adaptiveMultibandEnabled = enabling,
            isBypassed = if (enabling && current.isBypassed) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun toggleSpeakerPhaseAlignment() {
        val current = currentProfile()
        val enabling = !current.speakerPhaseAlignmentEnabled
        val updated = current.copy(
            speakerPhaseAlignmentEnabled = enabling,
            speakerDspEnabled = if (enabling && current.target == OutputTarget.SPEAKER) true else current.speakerDspEnabled,
            isBypassed = if (enabling && current.isBypassed) false else current.isBypassed
        )
        commitProfile(updated)
    }

    fun openCustomAudioSetup() {
        audioEngine.customAudioSetupEngine.open(currentProfile().accommodationsSoftIntensity)
    }

    fun selectCustomSetupAudition(slot: SetupAuditionSlot) {
        audioEngine.customAudioSetupEngine.selectAuditionSlot(slot)
    }

    fun selectCustomSetupIntensity(intensity: SoftSoundIntensity) {
        audioEngine.customAudioSetupEngine.selectIntensity(intensity)
    }

    fun pickCustomSetupWinner(slot: SetupAuditionSlot) {
        audioEngine.customAudioSetupEngine.chooseOptionForCurrentRound(slot)
    }

    fun restartCustomAudioSetup() {
        audioEngine.customAudioSetupEngine.restartWizard()
    }

    fun applyCustomAudioSetupResult() {
        val wizardState = audioEngine.customAudioSetupEngine.state.value
        val current = currentProfile()
        val updated = current.copy(
            accommodationsEnabled = true,
            accommodationsToneProfile = wizardState.chosenProfile,
            accommodationsSoftIntensity = wizardState.softIntensity,
            isBypassed = false
        )
        commitProfile(updated)
        audioEngine.customAudioSetupEngine.close()
    }

    fun closeCustomAudioSetup() {
        audioEngine.customAudioSetupEngine.close()
    }

    fun setAbCompareHeld(held: Boolean) {
        audioEngine.setAbCompareHeld(held)
    }

    fun startHearingCheck() {
        audioEngine.audiogramEngine.startCheck()
    }

    fun recordHearingCheckTap() {
        audioEngine.audiogramEngine.recordHeardCurrentTone()
    }

    fun skipHearingCheckStep() {
        audioEngine.audiogramEngine.skipCurrentTone()
    }

    fun cancelHearingCheck() {
        audioEngine.audiogramEngine.cancelCheck()
    }

    fun applyAudiogramCurve(curveDb: List<Float>) {
        if (curveDb.size != 10) return
        val current = currentProfile()
        val updated = current.copy(
            bandGainsDb = curveDb,
            isBypassed = false,
            selectedPresetId = EqPresetsCatalog.CUSTOM_ID,
            audiogramGainsDb = curveDb,
            tenBandEnabled = true,
            threeBandEnabled = false
        )
        commitProfile(updated)
        audioEngine.audiogramEngine.cancelCheck()
    }

    fun toggleBypass() {
        val current = currentProfile()
        val updated = current.copy(isBypassed = !current.isBypassed)
        commitProfile(updated)
    }

    fun resetToFlat() {
        val current = currentProfile()
        preLateNightProfiles.remove(current.target)
        val updated = current.copy(
            bandGainsDb = List(10) { 0f },
            preampDb = 0f,
            isBypassed = false,
            selectedPresetId = EqPresetsCatalog.FLAT_ID,
            lateNightEnabled = false
        )
        commitProfile(updated)
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        visualizerModeFlow.value = mode
        viewModelScope.launch {
            repository.saveVisualizerMode(mode)
        }
    }

    fun cycleTestAudioSource() {
        val entries = TestAudioSource.entries
        val nextIdx = (entries.indexOf(audioEngine.testSynthesizer.currentSource.value) + 1) % entries.size
        audioEngine.setTestAudioSource(entries[nextIdx])
    }

    fun onAudioPermissionResult(granted: Boolean) {
        audioEngine.onPermissionResult(granted)
        if (!granted && currentProfile().conversationAwarenessEnabled) {
            val updated = currentProfile().copy(conversationAwarenessEnabled = false)
            commitProfile(updated)
        }
        if (!granted && currentProfile().accommodationsApplyToVoice) {
            val updated = currentProfile().copy(accommodationsApplyToVoice = false)
            commitProfile(updated)
        }
    }

    private fun currentProfile(): OutputEqProfile {
        val target = selectedTargetFlow.value
        return profilesFlow.value[target] ?: OutputEqProfile(target = target)
    }

    private fun applyToAudioEngine(profile: OutputEqProfile) {
        audioEngine.applyParameters(
            target = profile.target,
            gainsDb = profile.bandGainsDb,
            preampDb = profile.preampDb,
            isBypassed = profile.isBypassed,
            lateNightEnabled = profile.lateNightEnabled,
            nightIntensity = profile.nightIntensity,
            speakerDspEnabled = profile.speakerDspEnabled,
            virtualBassEnabled = profile.virtualBassEnabled,
            landscapeStereoStage = profile.landscapeStereoStage,
            speakerTuningProfile = profile.speakerTuningProfile,
            audioMixMode = profile.audioMixMode,
            spatialMode = profile.spatialMode,
            spatialDepth = profile.spatialDepth,
            heightPresence = profile.heightPresence,
            vocalAnchor = profile.vocalAnchor,
            soundCheckEnabled = profile.soundCheckEnabled,
            conversationAwarenessEnabled = profile.conversationAwarenessEnabled,
            bgSoundPreset = profile.backgroundSoundPreset,
            bgSoundVolume = profile.backgroundSoundVolume,
            voiceIsolationEnabled = profile.voiceIsolationEnabled,
            tenBandEnabled = profile.tenBandEnabled,
            threeBandEnabled = profile.threeBandEnabled,
            threeBandBassDb = profile.threeBandBassDb,
            threeBandMidsDb = profile.threeBandMidsDb,
            threeBandTrebleDb = profile.threeBandTrebleDb,
            accommodationsEnabled = profile.accommodationsEnabled,
            accommodationsToneProfile = profile.accommodationsToneProfile,
            accommodationsSoftIntensity = profile.accommodationsSoftIntensity,
            accommodationsApplyToMedia = profile.accommodationsApplyToMedia,
            accommodationsApplyToVoice = profile.accommodationsApplyToVoice,
            accommodationsOutputGainDb = profile.accommodationsOutputGainDb,
            loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
            loudnessBoostPercent = profile.loudnessBoostPercent,
            siliconDspEnabled = profile.siliconDspEnabled,
            trebleSmoothnessPercent = profile.trebleSmoothnessPercent,
            phoneOrientationMode = profile.phoneOrientationMode,
            deviceIsLandscape = deviceIsLandscapeFlow.value,
            masterVolumePercent = profile.masterVolumePercent,
            proMaxBodyWarmthEnabled = profile.proMaxBodyWarmthEnabled,
            adaptiveMultibandEnabled = profile.adaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = profile.speakerPhaseAlignmentEnabled
        )
    }

    private fun commitProfile(profile: OutputEqProfile) {
        profilesFlow.value = profilesFlow.value.toMutableMap().apply {
            put(profile.target, profile)
        }
        applyToAudioEngine(profile)
        viewModelScope.launch {
            repository.saveProfile(profile)
        }
    }

    private fun snapHalfDb(value: Float): Float {
        return snapHalfDbRange(value, EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
    }

    private fun snapHalfDbRange(value: Float, minDb: Float, maxDb: Float): Float {
        val clamped = value.coerceIn(minDb, maxDb)
        return ((clamped * 2f).roundToInt() / 2f).coerceIn(minDb, maxDb)
    }

    private fun findMatchingPresetId(gains: List<Float>): String {
        val match = EqPresetsCatalog.ALL_WITH_FLAT.firstOrNull { preset ->
            preset.gainsDb.indices.all { idx ->
                kotlin.math.abs(preset.gainsDb[idx] - gains[idx]) < 0.05f
            }
        }
        return match?.id ?: EqPresetsCatalog.CUSTOM_ID
    }
}
