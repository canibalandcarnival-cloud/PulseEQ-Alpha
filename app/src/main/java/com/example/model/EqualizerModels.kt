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
    val virtualBassEnabled: Boolean = (target == OutputTarget.SPEAKER)
) {
    companion object {
        fun defaultGainsForTarget(target: OutputTarget): List<Float> = when (target) {
            // Cuts sub-bass below 100Hz to prevent phone speaker rattle & boosts vocal clarity
            OutputTarget.SPEAKER -> listOf(-6.0f, -3.5f, 0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 2.5f, 1.0f, 0.0f)
            // Full-range bass & crisp treble for headphones
            OutputTarget.HEADPHONES -> listOf(2.5f, 2.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f)
            OutputTarget.BLUETOOTH -> List(10) { 0f }
        }

        fun defaultPresetIdForTarget(target: OutputTarget): String = when (target) {
            OutputTarget.SPEAKER -> EqPresetsCatalog.CUSTOM_ID
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

object EqPresetsCatalog {
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val FLAT_ID = "flat"
    const val CUSTOM_ID = "custom"
    const val LATE_NIGHT_ID = "late_night"

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

    val LATE_NIGHT_PRESET = EqPreset(
        id = LATE_NIGHT_ID,
        name = "Late Night",
        group = PresetGroup.ALL,
        subtitle = "Dynamic compression + low-volume Fletcher-Munson contour",
        gainsDb = listOf(3.5f, 2.5f, 1.0f, 0.0f, 0.0f, 1.0f, 2.0f, 2.5f, 3.0f, 3.5f)
    )

    /**
     * 21 presets mapped from the 3 reference preset images + iPhone-style Late Night preset:
     * Image 1: Small Speakers, Spoken Word, Rock, Pop, Reduce Bass
     * Image 2: Jazz, Increase Vocals, Loudness, Lounge, Latin, R&B, Piano, Reduce Treble
     * Image 3: Acoustic, Classical, Dance, Deep, Hip-Hop, Electronic, Increase Bass, Increase Treble
     */
    val PRESETS: List<EqPreset> = listOf(
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
