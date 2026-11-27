package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.CirrusSiliconDspEngine
import com.example.audio.EqualizerAudioEngine
import com.example.audio.EqualizerEngineHolder
import com.example.audio.GlobalSystemEqEngine
import com.example.audio.MediaPlaybackForegroundService
import com.example.model.AccommodationToneProfile
import com.example.model.OutputTarget
import com.example.model.PhoneOrientationMode
import com.example.model.SiliconDspSpecs
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.TestAudioSource
import com.example.model.TrebleGainStagingBudget
import com.example.ui.EqualizerScreen
import com.example.ui.theme.PulseEqTheme
import com.example.viewmodel.EqualizerUiState
import com.example.viewmodel.EqualizerViewModel

class MainActivity : ComponentActivity() {

    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private lateinit var equalizerAudioEngine: EqualizerAudioEngine
    private lateinit var globalEqEngine: GlobalSystemEqEngine

    val dspEngine: CirrusSiliconDspEngine = CirrusSiliconDspEngine(48000)

    // Real-time @Volatile DSP state mirrors synchronized directly with UI controls & CirrusSiliconDspEngine
    @Volatile
    var isSpeakerMode: Boolean = true
        private set

    @Volatile
    var isLandscape: Boolean = false
        private set

    @Volatile
    var is3BandEqEnabled: Boolean = false
        private set

    @Volatile
    var isSmartTiltEnabled: Boolean = true
        private set

    @Volatile
    var eqLowDb: Float = 0f
        private set

    @Volatile
    var eqMidDb: Float = 0f
        private set

    @Volatile
    var eqHighDb: Float = 0f
        private set

    @Volatile
    var isAccommodationsEnabled: Boolean = false
        private set

    @Volatile
    var accommodationProfile: Int = 0
        private set

    @Volatile
    var accommodationBoostLevel: Int = 2
        private set

    @Volatile
    var isAtmosEnabled: Boolean = false
        private set

    @Volatile
    var atmosMode: Int = 0
        private set

    @Volatile
    var isLateNightEnabled: Boolean = false
        private set

    @Volatile
    var masterBoostDb: Float = 8.5f
        private set

    @Volatile
    var isBypassed: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        equalizerAudioEngine = EqualizerEngineHolder.get(this)

        // Initialize Global System EQ Engine with maximum priority (Session 0 + MLBB/media sessions)
        globalEqEngine = GlobalSystemEqEngine.getInstance(this)
        globalEqEngine.start()

        // Start Persistent Foreground Equalizer Service for Infinix Hot 60 5G battery saver & background protection
        EqualizerService.start(this)

        setContent {
            val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()
            var lastTrackSource by remember { mutableStateOf(uiState.testAudioSource) }

            LaunchedEffect(uiState.testAudioSource) {
                if (uiState.testAudioSource != lastTrackSource) {
                    lastTrackSource = uiState.testAudioSource
                    onTrackChangedOrStopped()
                }
            }

            LaunchedEffect(uiState) {
                syncUiStateToDsp(uiState)
            }

            PulseEqTheme {
                EqualizerScreen(viewModel = equalizerViewModel)
            }
        }
    }

    /**
     * Synchronizes every UI slider, mode selector, and toggle from [EqualizerUiState] into the
     * real-time @Volatile DSP properties, [dspEngine], [equalizerAudioEngine], and [globalEqEngine].
     */
    private fun syncUiStateToDsp(uiState: EqualizerUiState) {
        val profile = uiState.activeProfile
        val effectiveBypassed = profile.isBypassed || uiState.abCompareHeld

        isSpeakerMode = (uiState.selectedTarget == OutputTarget.SPEAKER)
        isLandscape = when (profile.phoneOrientationMode) {
            PhoneOrientationMode.AUTO_DETECT -> uiState.deviceIsLandscape
            PhoneOrientationMode.PORTRAIT -> false
            PhoneOrientationMode.LANDSCAPE -> true
        }

        is3BandEqEnabled = profile.threeBandEnabled
        isSmartTiltEnabled = profile.threeBandSmartCurveEnabled
        eqLowDb = profile.threeBandBassDb.coerceIn(-7.0f, 7.0f)
        eqMidDb = profile.threeBandMidsDb.coerceIn(-7.0f, 7.0f)
        eqHighDb = profile.threeBandTrebleDb.coerceIn(-7.0f, 7.0f)

        isAccommodationsEnabled = profile.accommodationsEnabled
        accommodationProfile = when (profile.accommodationsToneProfile) {
            AccommodationToneProfile.BALANCED_TONE -> 0
            AccommodationToneProfile.VOCAL_RANGE -> 1
            AccommodationToneProfile.BRIGHTNESS -> 2
        }
        accommodationBoostLevel = when (profile.accommodationsSoftIntensity) {
            SoftSoundIntensity.OFF -> 0
            SoftSoundIntensity.SLIGHT -> 1
            SoftSoundIntensity.MODERATE -> 2
            SoftSoundIntensity.STRONG -> 3
        }

        isAtmosEnabled = (profile.spatialMode != SpatialMode.OFF)
        atmosMode = when (profile.spatialMode) {
            SpatialMode.OFF -> 0
            SpatialMode.ATMOS_MUSIC -> 1
            SpatialMode.ATMOS_CINEMA -> 2
        }

        isLateNightEnabled = profile.lateNightEnabled
        masterBoostDb = if (profile.loudnessMaximizerEnabled) {
            (profile.loudnessBoostPercent.coerceIn(0f, 1f) * 16.0f).coerceIn(0f, 16f)
        } else {
            8.5f
        }

        isBypassed = effectiveBypassed

        val trebleBudget = TrebleGainStagingBudget.allocate(
            accommodationsActive = isAccommodationsEnabled && profile.accommodationsApplyToMedia && !effectiveBypassed,
            accommodationsProfile = profile.accommodationsToneProfile,
            accommodationsIntensity = profile.accommodationsSoftIntensity,
            spatialActive = isAtmosEnabled && !effectiveBypassed,
            spatialHeightPresence = profile.heightPresence,
            siliconDspActive = profile.siliconDspEnabled && !effectiveBypassed
        )

        dspEngine.configure(
            isEnabled = !effectiveBypassed && profile.siliconDspEnabled,
            target = uiState.selectedTarget,
            microAirGainDb = trebleBudget.cirrusAirShelfDb.toDouble()
                .coerceIn(0.0, SiliconDspSpecs.MICRO_AIR_GAIN_DB)
        )

        if (uiState.testAudioSource == TestAudioSource.OFF && effectiveBypassed) {
            dspEngine.reset()
        }
    }

    /**
     * Output Mode Toggle: Updates Speaker/Headphone DAC mode and Landscape orientation.
     */
    fun updateOutputMode(speakerMode: Boolean, landscape: Boolean) {
        isSpeakerMode = speakerMode
        isLandscape = landscape
        val target = if (speakerMode) OutputTarget.SPEAKER else OutputTarget.HEADPHONES
        equalizerViewModel.selectOutputTarget(target)
        equalizerViewModel.updateDetectedDeviceOrientation(landscape)
        equalizerViewModel.setPhoneOrientationMode(
            if (landscape) PhoneOrientationMode.LANDSCAPE else PhoneOrientationMode.PORTRAIT
        )
        dspEngine.configure(
            isEnabled = !isBypassed && equalizerViewModel.uiState.value.activeProfile.siliconDspEnabled,
            target = target
        )
    }

    /**
     * iOS 27 3-Band EQ (-7.0 dB to +7.0 dB): Updates 3-Band EQ enable, Smart Tilt, and Low/Mid/High dB.
     */
    fun updateThreeBandEq(
        enabled: Boolean,
        smartTilt: Boolean,
        lowDb: Float,
        midDb: Float,
        highDb: Float
    ) {
        is3BandEqEnabled = enabled
        isSmartTiltEnabled = smartTilt
        eqLowDb = lowDb.coerceIn(-7.0f, 7.0f)
        eqMidDb = midDb.coerceIn(-7.0f, 7.0f)
        eqHighDb = highDb.coerceIn(-7.0f, 7.0f)

        equalizerViewModel.selectEqMode(useThreeBand = enabled)
        equalizerViewModel.toggleThreeBandSmartCurve(smartTilt)
        equalizerViewModel.updateThreeBandBass(eqLowDb)
        equalizerViewModel.updateThreeBandMids(eqMidDb)
        equalizerViewModel.updateThreeBandTreble(eqHighDb)
    }

    /**
     * Headphone Accommodations: Updates enable flag, profile (0=Balanced, 1=Vocal, 2=Brightness),
     * and boost level (0=Off, 1=Slight, 2=Moderate, 3=Strong).
     */
    fun updateHeadphoneAccommodations(
        enabled: Boolean,
        profileIndex: Int,
        boostLevel: Int
    ) {
        isAccommodationsEnabled = enabled
        accommodationProfile = profileIndex.coerceIn(0, 2)
        accommodationBoostLevel = boostLevel.coerceIn(0, 3)

        val currentEnabled = equalizerViewModel.uiState.value.activeProfile.accommodationsEnabled
        if (currentEnabled != enabled) {
            equalizerViewModel.toggleHeadphoneAccommodations()
        }
        val toneProfile = when (accommodationProfile) {
            1 -> AccommodationToneProfile.VOCAL_RANGE
            2 -> AccommodationToneProfile.BRIGHTNESS
            else -> AccommodationToneProfile.BALANCED_TONE
        }
        val intensity = when (accommodationBoostLevel) {
            0 -> SoftSoundIntensity.OFF
            1 -> SoftSoundIntensity.SLIGHT
            3 -> SoftSoundIntensity.STRONG
            else -> SoftSoundIntensity.MODERATE
        }
        if (enabled) {
            equalizerViewModel.setAccommodationsToneProfile(toneProfile)
            equalizerViewModel.setAccommodationsSoftIntensity(intensity)
        }
    }

    /**
     * Spatial Audio / Dolby Atmos: Updates enable flag and mode (0=Off, 1=Music, 2=Cinema).
     */
    fun updateSpatialAtmos(enabled: Boolean, modeIndex: Int) {
        isAtmosEnabled = enabled
        atmosMode = if (enabled) modeIndex.coerceIn(0, 2) else 0
        val spatialMode = when (atmosMode) {
            1 -> SpatialMode.ATMOS_MUSIC
            2 -> SpatialMode.ATMOS_CINEMA
            else -> SpatialMode.OFF
        }
        equalizerViewModel.setSpatialMode(spatialMode)
    }

    /**
     * Late Night Mode & Master Output Boost (0 dB to +16 dB, default 8.5f).
     */
    fun updateLateNightAndBoost(lateNight: Boolean, boostDb: Float = 8.5f) {
        isLateNightEnabled = lateNight
        masterBoostDb = boostDb.coerceIn(0f, 16f)

        val currentLateNight = equalizerViewModel.uiState.value.activeProfile.lateNightEnabled
        if (currentLateNight != lateNight) {
            equalizerViewModel.toggleLateNightMode()
        }
        val currentBoostActive = equalizerViewModel.uiState.value.activeProfile.loudnessMaximizerEnabled
        if (masterBoostDb > 0f) {
            if (!currentBoostActive) {
                equalizerViewModel.toggleLoudnessMaximizer()
            }
            equalizerViewModel.updateLoudnessBoostPercent((masterBoostDb / 16.0f).coerceIn(0f, 1f))
        } else if (currentBoostActive) {
            equalizerViewModel.toggleLoudnessMaximizer()
        }
    }

    /**
     * Hold-to-Compare (A/B) Button: Sets bypass = true while pressed and false on release.
     */
    fun setHoldToCompareBypass(pressed: Boolean) {
        isBypassed = pressed
        equalizerViewModel.setAbCompareHeld(pressed)
        val profile = equalizerViewModel.uiState.value.activeProfile
        dspEngine.configure(
            isEnabled = !pressed && !profile.isBypassed && profile.siliconDspEnabled,
            target = equalizerViewModel.uiState.value.selectedTarget
        )
        if (!pressed) {
            dspEngine.reset()
        }
    }

    /**
     * Resets all filter states cleanly when switching tracks or hitting Pause/Stop
     * so Hi-Res audio starts and stops with zero residual ringing or beeping.
     */
    fun onTrackChangedOrStopped() {
        dspEngine.reset()
        equalizerAudioEngine.testSynthesizer.resetDspEngine()
    }

    /**
     * Stops playback cleanly and resets DSP filter states.
     */
    fun stopAllPlayback() {
        equalizerAudioEngine.setTestAudioSource(TestAudioSource.OFF)
        onTrackChangedOrStopped()
        try {
            val pauseIntent = Intent(this, MediaPlaybackForegroundService::class.java).apply {
                action = MediaPlaybackForegroundService.ACTION_PAUSE
            }
            startService(pauseIntent)
        } catch (_: Throwable) {
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
     * One-tap Mobile Legends: Bang Bang Competitive Clarity trigger.
     */
    fun applyMlbbCompetitiveClarity() {
        globalEqEngine.applyMlbbPreset()
        equalizerViewModel.applyMlbbCompetitiveClarity()
    }

    override fun onDestroy() {
        if (equalizerAudioEngine.testSynthesizer.currentSource.value == TestAudioSource.OFF) {
            dspEngine.reset()
        }
        // Note: GlobalSystemEqEngine remains alive inside EqualizerService while in the background
        super.onDestroy()
    }
}
