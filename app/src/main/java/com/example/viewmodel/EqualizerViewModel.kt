package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AudioSessionRegistry
import com.example.EqualizerService
import com.example.audio.EqualizerEngineHolder
import com.example.data.EqPreferencesRepository
import com.example.model.AudioLabTelemetry
import com.example.model.BackgroundSoundPreset
import com.example.model.ConnectedDeviceState
import com.example.model.EqPreset
import com.example.model.EqPresetsCatalog
import com.example.model.HearingCheckUiState
import com.example.model.NightIntensity
import com.example.model.OutputEqProfile
import com.example.model.OutputTarget
import com.example.model.SpatialMode
import com.example.model.TestAudioSource
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
    val abCompareHeld: Boolean = false
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
            profilesFlow
        ) { target, autoSwitch, visMode, profiles ->
            CorePrefState(target, autoSwitch, visMode, profiles)
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
            audioEngine.abCompareHeld
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            RoutingAndLabState(
                globalMix = arr[0] as Boolean,
                serviceRunning = arr[1] as Boolean,
                sessions = arr[2] as Map<Int, String>,
                labTelemetry = arr[3] as AudioLabTelemetry,
                hearingCheck = arr[4] as HearingCheckUiState,
                abHeld = arr[5] as Boolean
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
            abCompareHeld = routing.abHeld
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
        val abHeld: Boolean
    )

    private data class CorePrefState(
        val target: OutputTarget,
        val autoSwitch: Boolean,
        val visMode: VisualizerMode,
        val profiles: Map<OutputTarget, OutputEqProfile>
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
            selectedPresetId = matchedPreset
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
            lateNightEnabled = isLateNightPreset
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
                lateNightEnabled = true
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
            audiogramGainsDb = curveDb
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
            spatialMode = profile.spatialMode,
            spatialDepth = profile.spatialDepth,
            heightPresence = profile.heightPresence,
            vocalAnchor = profile.vocalAnchor,
            soundCheckEnabled = profile.soundCheckEnabled,
            conversationAwarenessEnabled = profile.conversationAwarenessEnabled,
            bgSoundPreset = profile.backgroundSoundPreset,
            bgSoundVolume = profile.backgroundSoundVolume,
            voiceIsolationEnabled = profile.voiceIsolationEnabled
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
        val clamped = value.coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
        return (clamped * 2f).roundToInt() / 2f
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
