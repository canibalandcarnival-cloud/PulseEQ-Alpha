package com.example.model

import androidx.compose.runtime.Immutable

enum class OutputTarget(
    val id: String,
    val label: String,
    val shortLabel: String,
    val acousticHint: String
) {
    SPEAKER(
        id = "speaker",
        label = "Phone Speaker",
        shortLabel = "Speaker",
        acousticHint = "Built-in chassis transducers"
    ),
    HEADPHONES(
        id = "headphones",
        label = "Headphones",
        shortLabel = "Headphones",
        acousticHint = "Wired / USB-C personal audio"
    ),
    BLUETOOTH(
        id = "bluetooth",
        label = "Bluetooth Speaker",
        shortLabel = "Bluetooth",
        acousticHint = "Wireless A2DP / BLE audio"
    )
}

@Immutable
data class EqBand(
    val index: Int,
    val frequencyHz: Int,
    val shortLabel: String,
    val fullLabel: String
)

enum class PresetGroup(val label: String) {
    ALL("All"),
    SET_1("Image 1"),
    SET_2("Image 2"),
    SET_3("Image 3")
}

@Immutable
data class EqPreset(
    val id: String,
    val name: String,
    val group: PresetGroup,
    val subtitle: String,
    val gainsDb: List<Float>,
    val preampDb: Float = 0f
)

enum class NightIntensity(
    val label: String,
    val thresholdDb: Float,
    val makeupGainDb: Float,
    val kneeDb: Float = 20f,
    val ratio: Float = 4f,
    val attackMs: Float = 5f,
    val releaseMs: Float = 250f
) {
    GENTLE(
        label = "Gentle",
        thresholdDb = -18f,
        makeupGainDb = 2f
    ),
    STANDARD(
        label = "Standard",
        thresholdDb = -24f,
        makeupGainDb = 4f
    ),
    HEAVY(
        label = "Heavy",
        thresholdDb = -30f,
        makeupGainDb = 6f
    )
}

enum class SpatialMode(
    val label: String,
    val subtitle: String,
    val defaultDepth: Float,
    val defaultHeight: Float
) {
    OFF(
        label = "Off",
        subtitle = "Stereo Direct",
        defaultDepth = 0.0f,
        defaultHeight = 0.0f
    ),
    ATMOS_MUSIC(
        label = "Atmos Music",
        subtitle = "Balanced Studio",
        defaultDepth = 0.65f,
        defaultHeight = 0.58f
    ),
    ATMOS_CINEMA(
        label = "Atmos Cinema",
        subtitle = "Wide 3D & Height",
        defaultDepth = 0.92f,
        defaultHeight = 0.88f
    )
}

enum class AudioMixMode(
    val id: String,
    val label: String,
    val badge: String,
    val subtitle: String
) {
    STANDARD(
        id = "standard",
        label = "Standard",
        badge = "L+R Native",
        subtitle = "Full-range transparent stereo mix"
    ),
    IN_FRAME(
        id = "in_frame",
        label = "In-Frame",
        badge = "Vocals Focused",
        subtitle = "Locks center vocals & trims off-axis wash"
    ),
    STUDIO(
        id = "studio",
        label = "Studio",
        badge = "Dry Booth",
        subtitle = "Strips room echo & tightens close-mic clarity"
    ),
    CINEMATIC(
        id = "cinematic",
        label = "Cinematic",
        badge = "Dialogue + Wide",
        subtitle = "Crisp anchored vocals over expansive stereo score"
    )
}

enum class BackgroundSoundPreset(
    val id: String,
    val label: String,
    val subtitle: String
) {
    OFF("off", "Off", "Ambient mixer off"),
    BALANCED_NOISE("balanced", "Balanced Noise", "Smooth neutral pink spectrum"),
    DARK_NOISE("dark", "Dark Noise", "Warm deep low-pass drone"),
    RAIN("rain", "Rain", "Soft steady rainfall texture"),
    OCEAN("ocean", "Ocean", "Slow rhythmic surf swells")
}

enum class AccommodationToneProfile(
    val id: String,
    val label: String,
    val shortSummary: String,
    val detailBadge: String
) {
    BALANCED_TONE(
        id = "balanced_tone",
        label = "Balanced Tone",
        shortSummary = "Balanced presence & headroom-safe air",
        detailBadge = "300Hz -2 • 1k +1.0 • 3k +1.8 • 8k +2.1 • 14k +1.5 dB"
    ),
    VOCAL_RANGE(
        id = "vocal_range",
        label = "Vocal Range",
        shortSummary = "Speech clarity & 100Hz rumble cut",
        detailBadge = "100Hz HPF • 250Hz -2 • 1k +1.5 • 2.5k +2.1 • 4k +1.8 dB"
    ),
    BRIGHTNESS(
        id = "brightness",
        label = "Brightness",
        shortSummary = "Headroom-normalized high-frequency air",
        detailBadge = "300Hz -2 • 4k +1.1 • 8k +1.4 • 12k +1.7 • 16k +1.3 dB"
    )
}

enum class SpeakerTuningProfile(
    val id: String,
    val label: String,
    val subtitleTag: String,
    val detailSummary: String
) {
    PRO_MAX_MUSIC(
        id = "pro_max_music",
        label = "18 Pro Max Music",
        subtitleTag = "Warm Rich Bass",
        detailSummary = "88Hz HPF • 135Hz +3.5 • 340Hz -3.5 • 4.5k -3.0 • 7k -3.5 • 12k +1.0dB"
    ),
    PRO_WARM_DIALOGUE(
        id = "pro_warm_dialogue",
        label = "18 Pro",
        subtitleTag = "Warm Spoken Dialogue",
        detailSummary = "100Hz HPF • 150Hz +1.5 • 340Hz -2 • 1.8kHz +3.2 • 7k -3.5dB"
    )
}

enum class SoftSoundIntensity(
    val id: String,
    val label: String,
    val eqScale: Float,
    val thresholdDb: Float,
    val ratio: Float,
    val kneeDb: Float,
    val makeupGainDb: Float
) {
    SLIGHT(
        id = "slight",
        label = "Slight",
        eqScale = 0.6f,
        thresholdDb = -24f,
        ratio = 1.8f,
        kneeDb = 20f,
        makeupGainDb = 3.5f
    ),
    MODERATE(
        id = "moderate",
        label = "Moderate",
        eqScale = 1.0f,
        thresholdDb = -28f,
        ratio = 2.2f,
        kneeDb = 20f,
        makeupGainDb = 6.0f
    ),
    STRONG(
        id = "strong",
        label = "Strong",
        eqScale = 1.4f,
        thresholdDb = -32f,
        ratio = 2.6f,
        kneeDb = 15f,
        makeupGainDb = 8.5f
    )
}

enum class SetupAuditionSlot {
    STANDARD,
    OPTION_A,
    OPTION_B
}

@Immutable
data class CustomAudioSetupUiState(
    val isOpen: Boolean = false,
    val stepIndex: Int = 0, // 0: Option A vs B (Balanced vs Vocal), 1: Winner vs Brightness, 2: Summary
    val optionA: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
    val optionB: AccommodationToneProfile = AccommodationToneProfile.VOCAL_RANGE,
    val activeAuditionSlot: SetupAuditionSlot = SetupAuditionSlot.OPTION_A,
    val chosenProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
    val softIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE,
    val isCompleted: Boolean = false
)

@Immutable
data class AudioLabTelemetry(
    val inputLufs: Float = -60f,
    val soundCheckTrimDb: Float = 0f,
    val speechDetected: Boolean = false,
    val duckingGainLinear: Float = 1f,
    val voiceGateAttenuationDb: Float = 0f,
    val accommodationsGrDb: Float = 0f,
    val voiceMicMonitorActive: Boolean = false,
    val voiceMicLevelDb: Float = -60f,
    val loudnessRmsGrDb: Float = 0f,
    val loudnessBrickwallGrDb: Float = 0f,
    val loudnessAntiClipActive: Boolean = false,
    val siliconExcursionPct: Float = 0f,
    val siliconSvfCutoffHz: Float = 68f,
    val siliconCoilTempC: Float = 25f,
    val siliconClassHRailV: Float = 3.7f,
    val siliconTruePeakGrDb: Float = 0f,
    val trebleGainStagedCapActive: Boolean = false,
    val trebleCappedBoostDb: Float = 0f
)

@Immutable
data class HearingCheckUiState(
    val isActive: Boolean = false,
    val currentStepIndex: Int = 0,
    val frequenciesHz: List<Int> = listOf(500, 1000, 2000, 4000, 8000),
    val currentLevelDb: Float = -54f,
    val progressFraction: Float = 0f,
    val measuredThresholdsDb: List<Float> = emptyList(),
    val generatedEqCurveDb: List<Float>? = null,
    val isCompleted: Boolean = false
)

@Immutable
data class OutputEqProfile(
    val target: OutputTarget,
    val bandGainsDb: List<Float> = defaultGainsForTarget(target),
    val preampDb: Float = 0f,
    val isBypassed: Boolean = false,
    val selectedPresetId: String = defaultPresetIdForTarget(target),
    val lateNightEnabled: Boolean = false,
    val nightIntensity: NightIntensity = NightIntensity.STANDARD,
    val speakerDspEnabled: Boolean = (target == OutputTarget.SPEAKER),
    val virtualBassEnabled: Boolean = (target == OutputTarget.SPEAKER),
    val landscapeStereoStage: Boolean = (target == OutputTarget.SPEAKER),
    val speakerTuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
    val audioMixMode: AudioMixMode = AudioMixMode.STANDARD,
    val spatialMode: SpatialMode = SpatialMode.OFF,
    val spatialDepth: Float = SpatialMode.ATMOS_MUSIC.defaultDepth,
    val heightPresence: Float = SpatialMode.ATMOS_MUSIC.defaultHeight,
    val vocalAnchor: Boolean = true,
    val soundCheckEnabled: Boolean = false,
    val conversationAwarenessEnabled: Boolean = false,
    val backgroundSoundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF,
    val backgroundSoundVolume: Float = 0.28f,
    val voiceIsolationEnabled: Boolean = false,
    val audiogramGainsDb: List<Float>? = null,
    val tenBandEnabled: Boolean = true,
    val threeBandEnabled: Boolean = false,
    val threeBandBassDb: Float = 0f,
    val threeBandMidsDb: Float = 0f,
    val threeBandTrebleDb: Float = 0f,
    val accommodationsEnabled: Boolean = false,
    val accommodationsToneProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
    val accommodationsSoftIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE,
    val accommodationsApplyToMedia: Boolean = true,
    val accommodationsApplyToVoice: Boolean = false,
    val accommodationsOutputGainDb: Float = 0f,
    val loudnessMaximizerEnabled: Boolean = false,
    val loudnessBoostPercent: Float = 0.50f,
    val siliconDspEnabled: Boolean = true,
    val trebleSmoothnessPercent: Float = defaultTrebleSmoothnessForTarget(target)
) {
    companion object {
        fun defaultTrebleSmoothnessForTarget(target: OutputTarget): Float = when (target) {
            OutputTarget.SPEAKER -> 0.35f
            OutputTarget.HEADPHONES,
            OutputTarget.BLUETOOTH -> 0.15f
        }

        fun defaultGainsForTarget(target: OutputTarget): List<Float> = when (target) {
            // Warm iPhone Balance default curve: cuts sub-bass rattle, enriches 125-250Hz & vocals, smooths harsh highs
            OutputTarget.SPEAKER -> EqPresetsCatalog.WARM_IPHONE_BALANCE_PRESET.gainsDb
            // Full-range bass & crisp treble for headphones
            OutputTarget.HEADPHONES -> listOf(2.5f, 2.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f)
            OutputTarget.BLUETOOTH -> List(10) { 0f }
        }

        fun defaultPresetIdForTarget(target: OutputTarget): String = when (target) {
            OutputTarget.SPEAKER -> EqPresetsCatalog.WARM_IPHONE_BALANCE_ID
            OutputTarget.HEADPHONES -> EqPresetsCatalog.CUSTOM_ID
            OutputTarget.BLUETOOTH -> EqPresetsCatalog.FLAT_ID
        }
    }
}

@Immutable
data class ConnectedDeviceState(
    val activeRoute: OutputTarget = OutputTarget.SPEAKER,
    val speakerAvailable: Boolean = true,
    val headphonesConnected: Boolean = false,
    val headphonesName: String? = null,
    val bluetoothConnected: Boolean = false,
    val bluetoothName: String? = null
)

enum class VisualizerMode(val label: String) {
    HYBRID("Dual"),
    SPECTRUM("Spectrum"),
    WAVEFORM("Waveform")
}

enum class TestAudioSource(val label: String) {
    OFF("Test Off"),
    AMBIENT_CHORD("Warm Chord"),
    BASS_GROOVE("Bass Pulse"),
    PINK_NOISE("Pink Noise")
}

@Immutable
data class VisualizerFrame(
    val spectrumBins: List<Float> = List(32) { 0f },
    val waveformSamples: List<Float> = List(96) { 0f },
    val peakDb: Float = -60f,
    val gainReductionDb: Float = 0f,
    val isSignalLive: Boolean = false
)

object ThreeBandEqRanges {
    const val BASS_MIN_DB = -12f
    const val BASS_MAX_DB = 12f
    const val MIDS_MIN_DB = -12f
    const val MIDS_MAX_DB = 12f
    const val TREBLE_MIN_DB = -12f
    const val TREBLE_MAX_DB = 12f
}

object LoudnessMaximizerSpecs {
    const val SUB_BASS_HPF_HZ = 115.0
    const val MAX_MAKEUP_BOOST_DB = 9.0f
    const val RMS_THRESHOLD_DB = -20.0f
    const val RMS_RATIO = 4.0f
    const val RMS_KNEE_DB = 15.0f
    const val RMS_ATTACK_MS = 5.0f
    const val RMS_RELEASE_MS = 150.0f
    const val BRICKWALL_THRESHOLD_DB = -0.3f
    const val BRICKWALL_RATIO = 20.0f
    const val BRICKWALL_KNEE_DB = 0.0f
    const val BRICKWALL_ATTACK_MS = 0.5f
    const val BRICKWALL_RELEASE_MS = 40.0f
}

object FlagshipSpeakerSpecs {
    const val SUB_BASS_HPF_HZ = 88.0
    const val CHAMBER_BASS_HZ = 135.0
    const val CHAMBER_BASS_Q = 1.1
    const val CHAMBER_BASS_DB = 3.5
    const val BOXINESS_CUT_HZ = 340.0
    const val BOXINESS_CUT_Q = 1.4
    const val BOXINESS_CUT_DB = -3.5
    const val PRESENCE_HZ = 3000.0
    const val PRESENCE_Q = 1.2
    const val PRESENCE_DB = 2.0
    const val HARSHNESS_CUT_HZ = 4500.0
    const val HARSHNESS_CUT_Q = 1.8
    const val HARSHNESS_CUT_DB = -3.0
    const val DE_ESSER_HZ = 7000.0
    const val DE_ESSER_Q = 2.5
    const val DE_ESSER_DB = -3.5
    const val AIR_SHELF_HZ = 12000.0
    const val AIR_SHELF_Q = 0.707
    const val AIR_SHELF_DB = 1.0

    // Hybrid Earpiece + Bottom Speaker Crossover Simulation
    const val HYBRID_CROSSOVER_BASS_HZ = 180.0
    const val XTC_CROSSFEED_GAIN = 0.22
    const val XTC_DELAY_SEC = 0.00019

    // Apple Dynamic Excursion Protection (Smart Bass Limiter -> -0.3 dB Brickwall with +4.5 dB clean makeup)
    const val EXCURSION_LOW_BAND_HZ = 220.0
    const val EXCURSION_THRESHOLD_DB = -14.0f
    const val EXCURSION_RATIO = 6.0f
    const val EXCURSION_KNEE_DB = 6.0f
    const val EXCURSION_ATTACK_MS = 3.0f
    const val EXCURSION_RELEASE_MS = 120.0f
    const val MAKEUP_GAIN_DB = 4.5f
    const val BRICKWALL_THRESHOLD_DB = -0.3f
    const val BRICKWALL_RATIO = 20.0f
    const val BRICKWALL_ATTACK_MS = 0.5f
    const val BRICKWALL_RELEASE_MS = 40.0f
}

object HeadphoneAccommodationsSpecs {
    const val OUTPUT_GAIN_MIN_DB = 0f
    const val OUTPUT_GAIN_MAX_DB = 10f
    const val MAX_PROFILE_BOOST_DB = 3.0f
}

object SiliconDspSpecs {
    const val SAMPLE_RATE_HZ = 48000
    const val LOOKAHEAD_SEC = 0.005
    const val SIDECHAIN_LPF_HZ = 140.0
    const val RESONANT_F0_HZ = 92.0
    const val QTS = 0.85
    const val EXCURSION_DRIVE_SCALE = 1.35
    const val SVF_ATTACK_SEC = 0.0005
    const val SVF_RELEASE_SEC = 0.120
    const val SVF_MIN_HZ = 68.0
    const val SVF_MAX_HZ = 122.0
    const val SVF_HEADPHONES_HZ = 24.0
    const val THERMAL_TAU_COIL_SEC = 1.8
    const val THERMAL_TAU_MAGNET_SEC = 18.0
    const val THERMAL_COIL_GAIN = 120.0
    const val AMBIENT_TEMP_C = 25.0
    const val MAX_TEMP_C = 95.0
    const val THERMAL_PROTECT_START_C = 72.0
    const val THERMAL_MAX_REDUCTION_DB = 6.0
    const val CLASS_H_ATTACK_SEC = 0.0002
    const val CLASS_H_DECAY_SEC = 0.060
    const val CLASS_H_MIN_VOLTS = 3.7
    const val CLASS_H_MAX_VOLTS = 11.5
    const val SOFT_KNEE_THRESHOLD_DBFS = -3.0
    const val SOFT_KNEE_CEILING = 0.992
    const val RECON_LPF_HZ = 19200.0
    const val MICRO_AIR_HZ = 14500.0
    const val MICRO_AIR_GAIN_DB = 1.1
    const val TRUE_PEAK_CEILING_DBTP = -0.2
}

object TrebleGainStagingSpecs {
    const val MAX_COMBINED_HF_BOOST_DB = 3.0f
    const val WARM_BODY_160_MAX_DB = 2.0
    const val DEHARSH_5500_MAX_CUT_DB = -4.5
    const val DEHARSH_SHELF_8500_MAX_CUT_DB = -2.5
}

@Immutable
data class TrebleBudgetAllocation(
    val accHfScale: Float,
    val spatialPinnaShelfDb: Float,
    val cirrusAirShelfDb: Float,
    val rawCombinedHfDb: Float,
    val cappedCombinedHfDb: Float
) {
    val isCapEngaged: Boolean
        get() = rawCombinedHfDb > TrebleGainStagingSpecs.MAX_COMBINED_HF_BOOST_DB + 0.05f
}

object TrebleGainStagingBudget {
    /**
     * Prevents stacked treble boosts (>4kHz) across Headphone Accommodations, Spatial Audio,
     * and the Cirrus Logic Air Shelf by scaling active >4kHz boosts whenever their sum > +3.0 dB.
     */
    fun allocate(
        accommodationsActive: Boolean,
        accommodationsProfile: AccommodationToneProfile,
        accommodationsIntensity: SoftSoundIntensity,
        spatialActive: Boolean,
        spatialHeightPresence: Float,
        siliconDspActive: Boolean
    ): TrebleBudgetAllocation {
        val eqScale = accommodationsIntensity.eqScale
        val accRawDb = if (!accommodationsActive) {
            0f
        } else {
            when (accommodationsProfile) {
                AccommodationToneProfile.BALANCED_TONE -> (2.1f * eqScale).coerceAtMost(HeadphoneAccommodationsSpecs.MAX_PROFILE_BOOST_DB)
                AccommodationToneProfile.VOCAL_RANGE -> (1.8f * eqScale).coerceAtMost(HeadphoneAccommodationsSpecs.MAX_PROFILE_BOOST_DB)
                AccommodationToneProfile.BRIGHTNESS -> (1.7f * eqScale).coerceAtMost(HeadphoneAccommodationsSpecs.MAX_PROFILE_BOOST_DB)
            }
        }
        val spatialRawDb = if (spatialActive) {
            (2.4f * (0.65f + 0.35f * spatialHeightPresence.coerceIn(0f, 1f))).coerceIn(1.5f, 3.0f)
        } else {
            0f
        }
        val cirrusRawDb = if (siliconDspActive) SiliconDspSpecs.MICRO_AIR_GAIN_DB.toFloat() else 0f

        val rawSum = accRawDb + spatialRawDb + cirrusRawDb
        val cap = TrebleGainStagingSpecs.MAX_COMBINED_HF_BOOST_DB
        val scale = if (rawSum > cap && rawSum > 0f) (cap / rawSum).coerceIn(0.1f, 1.0f) else 1.0f

        return TrebleBudgetAllocation(
            accHfScale = scale,
            spatialPinnaShelfDb = if (spatialActive) (3.0f * scale) else 0f,
            cirrusAirShelfDb = if (siliconDspActive) (SiliconDspSpecs.MICRO_AIR_GAIN_DB.toFloat() * scale) else 0f,
            rawCombinedHfDb = rawSum,
            cappedCombinedHfDb = minOf(rawSum, cap)
        )
    }
}

object EqPresetsCatalog {
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val FLAT_ID = "flat"
    const val CUSTOM_ID = "custom"
    const val LATE_NIGHT_ID = "late_night"
    const val WARM_IPHONE_BALANCE_ID = "warm_iphone_balance"

    val BANDS: List<EqBand> = listOf(
        EqBand(0, 32, "32", "32Hz"),
        EqBand(1, 64, "64", "64Hz"),
        EqBand(2, 125, "125", "125Hz"),
        EqBand(3, 250, "250", "250Hz"),
        EqBand(4, 500, "500", "500Hz"),
        EqBand(5, 1000, "1K", "1kHz"),
        EqBand(6, 2000, "2K", "2kHz"),
        EqBand(7, 4000, "4K", "4kHz"),
        EqBand(8, 8000, "8K", "8kHz"),
        EqBand(9, 16000, "16K", "16kHz")
    )

    val FLAT_PRESET = EqPreset(
        id = FLAT_ID,
        name = "Flat",
        group = PresetGroup.ALL,
        subtitle = "Neutral reference 0 dB",
        gainsDb = List(10) { 0f }
    )

    val WARM_IPHONE_BALANCE_PRESET = EqPreset(
        id = WARM_IPHONE_BALANCE_ID,
        name = "Warm iPhone Balance",
        group = PresetGroup.ALL,
        subtitle = "Rich chassis warmth, clear vocals & fatigue-free highs",
        gainsDb = listOf(-4.5f, -2.0f, 2.0f, 1.0f, 0.5f, 1.5f, 2.0f, -0.5f, -1.0f, 0.5f)
    )

    val LATE_NIGHT_PRESET = EqPreset(
        id = LATE_NIGHT_ID,
        name = "Late Night",
        group = PresetGroup.ALL,
        subtitle = "Dynamic compression + low-volume Fletcher-Munson contour",
        gainsDb = listOf(3.5f, 2.5f, 1.0f, 0.0f, 0.0f, 1.0f, 2.0f, 2.5f, 3.0f, 3.5f)
    )

    /**
     * 21 presets mapped from the 3 reference preset images + Warm iPhone Balance + Late Night:
     * Image 1: Small Speakers, Spoken Word, Rock, Pop, Reduce Bass
     * Image 2: Jazz, Increase Vocals, Loudness, Lounge, Latin, R&B, Piano, Reduce Treble
     * Image 3: Acoustic, Classical, Dance, Deep, Hip-Hop, Electronic, Increase Bass, Increase Treble
     */
    val PRESETS: List<EqPreset> = listOf(
        WARM_IPHONE_BALANCE_PRESET,
        LATE_NIGHT_PRESET,
        // --- Image 1 ---
        EqPreset(
            id = "small_speakers",
            name = "Small Speakers",
            group = PresetGroup.SET_1,
            subtitle = "Warm low-end lift, gentle highs",
            gainsDb = listOf(6.0f, 4.5f, 3.5f, 2.5f, 1.5f, 0.0f, -1.5f, -2.5f, -3.5f, -4.5f)
        ),
        EqPreset(
            id = "spoken_word",
            name = "Spoken Word",
            group = PresetGroup.SET_1,
            subtitle = "Vocal clarity 500Hz–4kHz",
            gainsDb = listOf(-4.0f, -0.5f, 0.0f, 1.0f, 3.5f, 4.5f, 5.0f, 4.5f, 2.5f, 0.0f)
        ),
        EqPreset(
            id = "rock",
            name = "Rock",
            group = PresetGroup.SET_1,
            subtitle = "Punchy bass & crisp treble scoop",
            gainsDb = listOf(5.0f, 4.0f, 3.0f, 1.5f, -0.5f, -1.0f, 0.5f, 2.5f, 3.5f, 4.5f)
        ),
        EqPreset(
            id = "pop",
            name = "Pop",
            group = PresetGroup.SET_1,
            subtitle = "Forward mid presence",
            gainsDb = listOf(-1.5f, -1.0f, 0.0f, 2.0f, 4.0f, 4.0f, 2.0f, 0.0f, -1.0f, -1.5f)
        ),
        EqPreset(
            id = "reduce_bass",
            name = "Reduce Bass",
            group = PresetGroup.SET_1,
            subtitle = "Attenuates sub & low frequencies",
            gainsDb = listOf(-6.0f, -4.5f, -3.5f, -2.5f, -1.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        ),

        // --- Image 2 ---
        EqPreset(
            id = "jazz",
            name = "Jazz",
            group = PresetGroup.SET_2,
            subtitle = "Warm upright bass & cymbal air",
            gainsDb = listOf(4.0f, 3.0f, 1.5f, 2.0f, -1.5f, -1.5f, 0.0f, 1.5f, 3.0f, 4.0f)
        ),
        EqPreset(
            id = "increase_vocals",
            name = "Increase Vocals",
            group = PresetGroup.SET_2,
            subtitle = "Cuts rumble, lifts vocal range",
            gainsDb = listOf(-1.5f, -3.0f, -3.0f, 1.5f, 3.5f, 3.5f, 3.0f, 1.5f, 0.0f, -1.5f)
        ),
        EqPreset(
            id = "loudness",
            name = "Loudness",
            group = PresetGroup.SET_2,
            subtitle = "Low-volume Fletcher-Munson contour",
            gainsDb = listOf(6.0f, 4.0f, 0.0f, 0.0f, -2.0f, 0.0f, -1.0f, -5.0f, 5.0f, 1.0f)
        ),
        EqPreset(
            id = "lounge",
            name = "Lounge",
            group = PresetGroup.SET_2,
            subtitle = "Smooth mid warmth & airy top",
            gainsDb = listOf(-3.0f, -1.5f, -0.5f, 1.5f, 4.0f, 2.5f, 0.0f, -1.5f, 2.0f, 1.0f)
        ),
        EqPreset(
            id = "latin",
            name = "Latin",
            group = PresetGroup.SET_2,
            subtitle = "Percussive low-end & bright brass",
            gainsDb = listOf(4.0f, 3.0f, 0.0f, 0.0f, -1.5f, -1.5f, -1.5f, 0.0f, 3.0f, 4.0f)
        ),
        EqPreset(
            id = "rnb",
            name = "R&B",
            group = PresetGroup.SET_2,
            subtitle = "Deep 64Hz groove & silky detail",
            gainsDb = listOf(2.5f, 6.5f, 5.5f, 1.5f, -2.0f, -1.5f, 2.5f, 2.5f, 3.0f, 4.0f)
        ),
        EqPreset(
            id = "piano",
            name = "Piano",
            group = PresetGroup.SET_2,
            subtitle = "Rich body & hammer articulation",
            gainsDb = listOf(3.0f, 2.0f, 0.0f, 2.5f, 3.0f, 1.5f, 3.5f, 4.5f, 3.0f, 3.5f)
        ),
        EqPreset(
            id = "reduce_treble",
            name = "Reduce Treble",
            group = PresetGroup.SET_2,
            subtitle = "Tames harshness above 1kHz",
            gainsDb = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.5f, -2.5f, -3.5f, -4.5f, -6.0f)
        ),

        // --- Image 3 ---
        EqPreset(
            id = "acoustic",
            name = "Acoustic",
            group = PresetGroup.SET_3,
            subtitle = "Natural string resonance & body",
            gainsDb = listOf(5.0f, 5.0f, 4.0f, 1.0f, 2.0f, 2.0f, 3.5f, 4.0f, 3.5f, 2.0f)
        ),
        EqPreset(
            id = "classical",
            name = "Classical",
            group = PresetGroup.SET_3,
            subtitle = "Orchestral depth & string sheen",
            gainsDb = listOf(4.5f, 3.5f, 3.0f, 2.5f, -1.5f, -1.5f, 0.0f, 2.0f, 3.0f, 4.0f)
        ),
        EqPreset(
            id = "dance",
            name = "Dance",
            group = PresetGroup.SET_3,
            subtitle = "Club kick punch & bright synth",
            gainsDb = listOf(3.5f, 6.5f, 5.0f, 0.0f, 2.0f, 3.5f, 5.0f, 4.5f, 3.5f, 0.0f)
        ),
        EqPreset(
            id = "deep",
            name = "Deep",
            group = PresetGroup.SET_3,
            subtitle = "Full low-mid weight, dark highs",
            gainsDb = listOf(4.5f, 3.5f, 1.5f, 1.0f, 2.5f, 2.0f, 1.5f, -2.5f, -3.5f, -5.0f)
        ),
        EqPreset(
            id = "hip_hop",
            name = "Hip-Hop",
            group = PresetGroup.SET_3,
            subtitle = "Heavy 808 sub & crisp snare",
            gainsDb = listOf(5.0f, 4.0f, 1.5f, 3.0f, -1.0f, -1.0f, 1.5f, -0.5f, 2.0f, 3.0f)
        ),
        EqPreset(
            id = "electronic",
            name = "Electronic",
            group = PresetGroup.SET_3,
            subtitle = "Sub weight & sparkling top end",
            gainsDb = listOf(4.0f, 3.5f, 1.5f, 0.0f, -2.0f, 2.0f, 1.0f, 1.5f, 4.0f, 4.5f)
        ),
        EqPreset(
            id = "increase_bass",
            name = "Increase Bass",
            group = PresetGroup.SET_3,
            subtitle = "Smooth boost below 1kHz",
            gainsDb = listOf(6.0f, 4.5f, 3.5f, 2.5f, 1.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        ),
        EqPreset(
            id = "increase_treble",
            name = "Increase Treble",
            group = PresetGroup.SET_3,
            subtitle = "Airy lift from 1kHz to 16kHz",
            gainsDb = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f)
        )
    )

    val ALL_WITH_FLAT: List<EqPreset> = listOf(FLAT_PRESET) + PRESETS

    fun findById(id: String): EqPreset? = ALL_WITH_FLAT.firstOrNull { it.id == id }
}
