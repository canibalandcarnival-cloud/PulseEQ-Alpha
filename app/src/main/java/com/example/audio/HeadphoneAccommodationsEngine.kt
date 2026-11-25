package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.example.model.AccommodationToneProfile
import com.example.model.CustomAudioSetupUiState
import com.example.model.HeadphoneAccommodationsSpecs
import com.example.model.OutputTarget
import com.example.model.SetupAuditionSlot
import com.example.model.SoftSoundIntensity
import com.example.model.StudioHiResSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Apple-Style "Headphone Accommodations" DSP Processor:
 * Inserted in the signal chain right BEFORE the main 10-band graphic EQ.
 *
 * Implements:
 * - 3 Authentic Apple Tone Profiles (Balanced Tone, Vocal Range with 110Hz HPF, Brightness with neutral <1kHz)
 *   scaled by Boost Soft Sounds intensity (Off 0.6x, Slight 0.8x, Moderate 1.0x, Strong 1.2x).
 * - Frequency-Selective Parallel Upward Compression (1.2 kHz clarity HPF -> -38 dB / 3.5:1 / 4ms / 80ms compressor
 *   + clean clarity gain blended back at 0% / 18% / 32% / 48% without squashing loud peaks downward).
 */
class HeadphoneAccommodationsProcessor(private val sampleRate: Int = 44100) {

    private val vocalHpf110 = StereoBiquad()
    private val mudCutBand = StereoBiquad()
    private val eqBand1 = StereoBiquad()
    private val eqBand2 = StereoBiquad()
    private val eqBand3 = StereoBiquad()
    private val eqBand4 = StereoBiquad()
    private val mediatekSmoothnessDip3200 = StereoBiquad()

    private val parallelClarityHpf1200 = StereoBiquad()
    private val softSoundCompressor = DynamicRangeCompressor(sampleRate)

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var vocalHpfEnabled: Boolean = false

    @Volatile
    private var mudCutEnabled: Boolean = false

    @Volatile
    private var mediatekSmoothnessEnabled: Boolean = false

    @Volatile
    private var parallelLiftMix: Double = 0.0

    @Volatile
    private var outputGainLinear: Double = 1.0

    val latestGainReductionDb: Float
        get() = if (enabled && parallelLiftMix > 0.0) softSoundCompressor.latestGainReductionDb else 0f

    init {
        configure(
            isEnabled = false,
            profile = AccommodationToneProfile.BALANCED_TONE,
            intensity = SoftSoundIntensity.MODERATE,
            outputGainDb = 0f
        )
    }

    fun configure(
        isEnabled: Boolean,
        profile: AccommodationToneProfile,
        intensity: SoftSoundIntensity,
        outputGainDb: Float = 0f,
        hfScale: Double = 1.0,
        outputTarget: OutputTarget = OutputTarget.HEADPHONES
    ) {
        enabled = isEnabled
        val scale = intensity.eqScale.toDouble()
        val cappedHfScale = hfScale.coerceIn(0.1, 1.0)
        val clampedOutDb = outputGainDb.coerceIn(
            HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
            HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
        )
        outputGainLinear = Math.pow(10.0, clampedOutDb / 20.0)
        parallelLiftMix = intensity.parallelMix.toDouble().coerceIn(0.0, 0.75)

        when (profile) {
            AccommodationToneProfile.BALANCED_TONE -> {
                vocalHpfEnabled = false
                mudCutEnabled = false
                mudCutBand.reset()
                eqBand1.setPeaking(sampleRate, 1000.0, 1.2, 1.5 * scale)
                eqBand2.setPeaking(sampleRate, 3000.0, 1.4, 2.5 * scale)
                eqBand3.setPeaking(sampleRate, 8000.0, 1.5, 3.2 * scale * cappedHfScale)
                eqBand4.setPeaking(sampleRate, 12000.0, 1.5, 2.5 * scale * cappedHfScale)
            }

            AccommodationToneProfile.VOCAL_RANGE -> {
                vocalHpfEnabled = true
                vocalHpf110.setHighPass(sampleRate, 110.0, 0.60)
                mudCutEnabled = true
                mudCutBand.setPeaking(sampleRate, 280.0, 1.3, -2.0)
                eqBand1.setPeaking(sampleRate, 1500.0, 1.4, 3.0 * scale)
                eqBand2.setPeaking(sampleRate, 3000.0, 1.5, 3.8 * scale)
                eqBand3.setPeaking(sampleRate, 6000.0, 1.6, 1.5 * scale * cappedHfScale)
                eqBand4.setPeaking(sampleRate, 12000.0, 1.5, 0.0)
            }

            AccommodationToneProfile.BRIGHTNESS -> {
                vocalHpfEnabled = false
                mudCutEnabled = false
                mudCutBand.reset()
                eqBand1.setPeaking(sampleRate, 4000.0, 1.8, 2.0 * scale * cappedHfScale)
                eqBand2.setPeaking(sampleRate, 8000.0, 1.8, 3.8 * scale * cappedHfScale)
                eqBand3.setPeaking(sampleRate, 14000.0, 1.6, 4.2 * scale * cappedHfScale)
                eqBand4.setPeaking(sampleRate, 16000.0, 1.6, 0.0)
            }
        }

        mediatekSmoothnessEnabled = isEnabled && (outputTarget != OutputTarget.SPEAKER)
        if (mediatekSmoothnessEnabled) {
            mediatekSmoothnessDip3200.setPeaking(
                sampleRate,
                HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_HZ,
                HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_Q,
                HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_DB
            )
        } else {
            mediatekSmoothnessDip3200.reset()
        }

        parallelClarityHpf1200.setHighPass(
            sampleRate,
            HeadphoneAccommodationsSpecs.PARALLEL_CLARITY_HPF_HZ,
            HeadphoneAccommodationsSpecs.PARALLEL_CLARITY_HPF_Q
        )

        softSoundCompressor.configureCustom(
            isEnabled = isEnabled && intensity.parallelMix > 0f,
            threshold = HeadphoneAccommodationsSpecs.PARALLEL_COMP_THRESHOLD_DB,
            knee = HeadphoneAccommodationsSpecs.PARALLEL_COMP_KNEE_DB,
            compRatio = HeadphoneAccommodationsSpecs.PARALLEL_COMP_RATIO,
            attackMs = HeadphoneAccommodationsSpecs.PARALLEL_COMP_ATTACK_MS,
            releaseMs = HeadphoneAccommodationsSpecs.PARALLEL_COMP_RELEASE_MS,
            makeupGainDb = intensity.clarityGainDb
        )
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) return inL to inR

        var l = inL
        var r = inR
        if (vocalHpfEnabled) {
            val (hpL, hpR) = vocalHpf110.processStereo(l, r)
            l = hpL
            r = hpR
        }
        if (mudCutEnabled) {
            val (cutL, cutR) = mudCutBand.processStereo(l, r)
            l = cutL
            r = cutR
        }
        val (e1L, e1R) = eqBand1.processStereo(l, r)
        val (e2L, e2R) = eqBand2.processStereo(e1L, e1R)
        val (e3L, e3R) = eqBand3.processStereo(e2L, e2R)
        val (e4L, e4R) = eqBand4.processStereo(e3L, e3R)
        val (eqL, eqR) = if (mediatekSmoothnessEnabled) {
            mediatekSmoothnessDip3200.processStereo(e4L, e4R)
        } else {
            e4L to e4R
        }

        val mix = parallelLiftMix
        val mixedL: Double
        val mixedR: Double
        if (mix > 0.0001) {
            val (clL, clR) = parallelClarityHpf1200.processStereo(eqL, eqR)
            val (compL, compR) = softSoundCompressor.processStereoSample(clL, clR)
            mixedL = eqL + compL * mix
            mixedR = eqR + compR * mix
        } else {
            mixedL = eqL
            mixedR = eqR
        }

        val outL = normalizePeakHeadroom(mixedL * outputGainLinear)
        val outR = normalizePeakHeadroom(mixedR * outputGainLinear)
        return outL to outR
    }

    private fun normalizePeakHeadroom(sample: Double): Double {
        // Transparent below 0.86; smoothly rounds extreme HF transient peaks so final limiter never ducks overall loudness
        val kneeStart = 0.86
        val absVal = abs(sample)
        if (absVal <= kneeStart) return sample
        val excess = absVal - kneeStart
        val headroom = 0.985 - kneeStart
        val compressed = kneeStart + headroom * kotlin.math.tanh(excess / headroom)
        return if (sample >= 0.0) compressed else -compressed
    }

    companion object {
        /**
         * Computes analytical Tone Curve + HPF frequency response magnitude (in dB) for a given
         * Tone Profile and Boost Soft Sounds intensity scaling (0.6x / 0.8x / 1.0x / 1.2x).
         */
        fun computeResponseDbAt(
            freqHz: Double,
            profile: AccommodationToneProfile,
            intensity: SoftSoundIntensity,
            sampleRate: Int = 44100,
            hfScale: Double = 1.0,
            isHeadphoneOrBluetooth: Boolean = true
        ): Float {
            val scale = intensity.eqScale.toDouble()
            val cappedHfScale = hfScale.coerceIn(0.1, 1.0)
            var totalDb = 0.0

            val mud = StereoBiquad()
            val b1 = StereoBiquad()
            val b2 = StereoBiquad()
            val b3 = StereoBiquad()
            val b4 = StereoBiquad()

            when (profile) {
                AccommodationToneProfile.BALANCED_TONE -> {
                    b1.setPeaking(sampleRate, 1000.0, 1.2, 1.5 * scale)
                    b2.setPeaking(sampleRate, 3000.0, 1.4, 2.5 * scale)
                    b3.setPeaking(sampleRate, 8000.0, 1.5, 3.2 * scale * cappedHfScale)
                    b4.setPeaking(sampleRate, 12000.0, 1.5, 2.5 * scale * cappedHfScale)
                }

                AccommodationToneProfile.VOCAL_RANGE -> {
                    val hpf = StereoBiquad().apply { setHighPass(sampleRate, 110.0, 0.60) }
                    totalDb += hpf.magnitudeDbAt(sampleRate, freqHz).coerceAtLeast(-16.0)
                    mud.setPeaking(sampleRate, 280.0, 1.3, -2.0)
                    b1.setPeaking(sampleRate, 1500.0, 1.4, 3.0 * scale)
                    b2.setPeaking(sampleRate, 3000.0, 1.5, 3.8 * scale)
                    b3.setPeaking(sampleRate, 6000.0, 1.6, 1.5 * scale * cappedHfScale)
                }

                AccommodationToneProfile.BRIGHTNESS -> {
                    b1.setPeaking(sampleRate, 4000.0, 1.8, 2.0 * scale * cappedHfScale)
                    b2.setPeaking(sampleRate, 8000.0, 1.8, 3.8 * scale * cappedHfScale)
                    b3.setPeaking(sampleRate, 14000.0, 1.6, 4.2 * scale * cappedHfScale)
                }
            }

            if (profile == AccommodationToneProfile.VOCAL_RANGE) {
                totalDb += mud.magnitudeDbAt(sampleRate, freqHz)
            }
            totalDb += b1.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b2.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b3.magnitudeDbAt(sampleRate, freqHz)
            if (profile == AccommodationToneProfile.BALANCED_TONE) {
                totalDb += b4.magnitudeDbAt(sampleRate, freqHz)
            }
            if (isHeadphoneOrBluetooth) {
                val mediatekDip = StereoBiquad().apply {
                    setPeaking(
                        sampleRate,
                        HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_HZ,
                        HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_Q,
                        HeadphoneAccommodationsSpecs.MEDIATEK_SMOOTHNESS_DB
                    )
                }
                totalDb += mediatekDip.magnitudeDbAt(sampleRate, freqHz)
            }
            return totalDb.toFloat()
        }
    }
}

/**
 * Interactive 3-Step Apple "Custom Audio Setup" A/B Paired-Comparison Wizard Engine:
 * - Step 1 (Speech Clarity Test): Standard vs. Vocal Focus (vocal sample).
 * - Step 2 (Music Detail & Brightness Test): Balanced Tone vs. Brightness (full-range track).
 * - Step 3 (Soft-Sound Sensitivity): Slight vs. Strong soft-sound upward lift, then applies winning combo.
 */
class CustomAudioSetupAuditionEngine {
    private val sampleRate = StudioHiResSpecs.SAMPLE_RATE_HZ
    private val processor = HeadphoneAccommodationsProcessor(sampleRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var auditionJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _state = MutableStateFlow(CustomAudioSetupUiState())
    val state: StateFlow<CustomAudioSetupUiState> = _state.asStateFlow()

    fun open(initialIntensity: SoftSoundIntensity) {
        val auditionBaseIntensity = if (initialIntensity == SoftSoundIntensity.OFF) {
            SoftSoundIntensity.MODERATE
        } else {
            initialIntensity
        }
        _state.value = CustomAudioSetupUiState(
            isOpen = true,
            stepIndex = 0,
            optionA = AccommodationToneProfile.BALANCED_TONE,
            optionB = AccommodationToneProfile.VOCAL_RANGE,
            activeAuditionSlot = SetupAuditionSlot.STANDARD,
            pickedVocalInStep1 = false,
            musicChoiceInStep2 = AccommodationToneProfile.BALANCED_TONE,
            chosenProfile = AccommodationToneProfile.BALANCED_TONE,
            softIntensity = auditionBaseIntensity,
            isCompleted = false
        )
        syncProcessorFromState()
        startAuditionLoop()
    }

    fun selectAuditionSlot(slot: SetupAuditionSlot) {
        val cur = _state.value
        if (!cur.isOpen) return
        val nextIntensity = if (cur.stepIndex == 2) {
            when (slot) {
                SetupAuditionSlot.OPTION_A -> SoftSoundIntensity.SLIGHT
                SetupAuditionSlot.OPTION_B -> SoftSoundIntensity.STRONG
                SetupAuditionSlot.STANDARD -> cur.softIntensity
            }
        } else {
            cur.softIntensity
        }
        _state.value = cur.copy(
            activeAuditionSlot = slot,
            softIntensity = nextIntensity
        )
        syncProcessorFromState()
    }

    fun selectIntensity(intensity: SoftSoundIntensity) {
        val cur = _state.value
        if (!cur.isOpen) return
        val nextSlot = if (cur.stepIndex == 2) {
            when (intensity) {
                SoftSoundIntensity.SLIGHT -> SetupAuditionSlot.OPTION_A
                SoftSoundIntensity.STRONG -> SetupAuditionSlot.OPTION_B
                else -> cur.activeAuditionSlot
            }
        } else {
            cur.activeAuditionSlot
        }
        _state.value = cur.copy(
            softIntensity = intensity,
            activeAuditionSlot = nextSlot
        )
        syncProcessorFromState()
    }

    fun chooseStep1Speech(preferVocalFocus: Boolean) {
        val cur = _state.value
        if (!cur.isOpen || cur.isCompleted) return
        val tentative = if (preferVocalFocus) {
            AccommodationToneProfile.VOCAL_RANGE
        } else {
            AccommodationToneProfile.BALANCED_TONE
        }
        _state.value = cur.copy(
            stepIndex = 1,
            optionA = AccommodationToneProfile.BALANCED_TONE,
            optionB = AccommodationToneProfile.BRIGHTNESS,
            activeAuditionSlot = SetupAuditionSlot.OPTION_A,
            pickedVocalInStep1 = preferVocalFocus,
            chosenProfile = tentative
        )
        syncProcessorFromState()
    }

    fun chooseStep2Music(musicChoice: AccommodationToneProfile) {
        val cur = _state.value
        if (!cur.isOpen || cur.isCompleted) return
        val normalizedMusic = if (musicChoice == AccommodationToneProfile.BRIGHTNESS) {
            AccommodationToneProfile.BRIGHTNESS
        } else {
            AccommodationToneProfile.BALANCED_TONE
        }
        val combinedProfile = when {
            normalizedMusic == AccommodationToneProfile.BRIGHTNESS -> AccommodationToneProfile.BRIGHTNESS
            cur.pickedVocalInStep1 -> AccommodationToneProfile.VOCAL_RANGE
            else -> AccommodationToneProfile.BALANCED_TONE
        }
        _state.value = cur.copy(
            stepIndex = 2,
            optionA = combinedProfile,
            optionB = combinedProfile,
            activeAuditionSlot = SetupAuditionSlot.OPTION_A,
            musicChoiceInStep2 = normalizedMusic,
            chosenProfile = combinedProfile,
            softIntensity = SoftSoundIntensity.SLIGHT
        )
        syncProcessorFromState()
    }

    fun chooseStep3Sensitivity(intensity: SoftSoundIntensity) {
        val cur = _state.value
        if (!cur.isOpen) return
        val chosenSlot = when (intensity) {
            SoftSoundIntensity.SLIGHT -> SetupAuditionSlot.OPTION_A
            SoftSoundIntensity.STRONG -> SetupAuditionSlot.OPTION_B
            else -> cur.activeAuditionSlot
        }
        _state.value = cur.copy(
            softIntensity = intensity,
            activeAuditionSlot = chosenSlot,
            isCompleted = true
        )
        syncProcessorFromState()
    }

    fun chooseOptionForCurrentRound(slot: SetupAuditionSlot) {
        val cur = _state.value
        if (!cur.isOpen || cur.isCompleted) return
        when (cur.stepIndex) {
            0 -> chooseStep1Speech(preferVocalFocus = (slot == SetupAuditionSlot.OPTION_B))
            1 -> chooseStep2Music(
                if (slot == SetupAuditionSlot.OPTION_B) {
                    AccommodationToneProfile.BRIGHTNESS
                } else {
                    AccommodationToneProfile.BALANCED_TONE
                }
            )
            else -> chooseStep3Sensitivity(
                if (slot == SetupAuditionSlot.OPTION_B) {
                    SoftSoundIntensity.STRONG
                } else {
                    SoftSoundIntensity.SLIGHT
                }
            )
        }
    }

    fun restartWizard() {
        open(_state.value.softIntensity)
    }

    fun close() {
        stopAuditionLoop()
        _state.value = CustomAudioSetupUiState(isOpen = false)
    }

    private fun syncProcessorFromState() {
        val st = _state.value
        when (st.stepIndex) {
            0 -> {
                when (st.activeAuditionSlot) {
                    SetupAuditionSlot.STANDARD -> processor.configure(
                        isEnabled = false,
                        profile = AccommodationToneProfile.BALANCED_TONE,
                        intensity = st.softIntensity
                    )
                    SetupAuditionSlot.OPTION_A,
                    SetupAuditionSlot.OPTION_B -> processor.configure(
                        isEnabled = true,
                        profile = AccommodationToneProfile.VOCAL_RANGE,
                        intensity = st.softIntensity
                    )
                }
            }

            1 -> {
                when (st.activeAuditionSlot) {
                    SetupAuditionSlot.STANDARD -> processor.configure(
                        isEnabled = false,
                        profile = AccommodationToneProfile.BALANCED_TONE,
                        intensity = st.softIntensity
                    )
                    SetupAuditionSlot.OPTION_A -> processor.configure(
                        isEnabled = true,
                        profile = AccommodationToneProfile.BALANCED_TONE,
                        intensity = st.softIntensity
                    )
                    SetupAuditionSlot.OPTION_B -> processor.configure(
                        isEnabled = true,
                        profile = AccommodationToneProfile.BRIGHTNESS,
                        intensity = st.softIntensity
                    )
                }
            }

            else -> {
                when (st.activeAuditionSlot) {
                    SetupAuditionSlot.STANDARD -> processor.configure(
                        isEnabled = false,
                        profile = st.chosenProfile,
                        intensity = st.softIntensity
                    )
                    SetupAuditionSlot.OPTION_A -> processor.configure(
                        isEnabled = true,
                        profile = st.chosenProfile,
                        intensity = if (st.isCompleted) st.softIntensity else SoftSoundIntensity.SLIGHT
                    )
                    SetupAuditionSlot.OPTION_B -> processor.configure(
                        isEnabled = true,
                        profile = st.chosenProfile,
                        intensity = if (st.isCompleted) st.softIntensity else SoftSoundIntensity.STRONG
                    )
                }
            }
        }
    }

    private fun startAuditionLoop() {
        stopAuditionLoop()
        val track = try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * StudioHiResSpecs.PLAYBACK_BUFFER_MULTIPLIER)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                    }
                }
                .build()
        } catch (_: Throwable) {
            return
        }

        audioTrack = track
        try {
            track.play()
        } catch (_: Throwable) {
            stopAuditionLoop()
            return
        }

        auditionJob = scope.launch {
            val frameCount = 1024
            val pcm = ShortArray(frameCount * 2)
            var cursor = 0L

            while (isActive) {
                val step = _state.value.stepIndex
                for (i in 0 until frameCount) {
                    val t = cursor.toDouble() / sampleRate
                    val phraseTime = t % 3.2

                    val rawL: Double
                    val rawR: Double
                    when (step) {
                        // Step 1: Speech Clarity Test (vocal syllables + chest mud + speech formants)
                        0 -> {
                            val syllableGate = when {
                                phraseTime in 0.12..0.52 -> sin(PI * (phraseTime - 0.12) / 0.40)
                                phraseTime in 0.68..1.18 -> sin(PI * (phraseTime - 0.68) / 0.50)
                                phraseTime in 1.38..1.86 -> sin(PI * (phraseTime - 1.38) / 0.48)
                                phraseTime in 2.05..2.75 -> 0.55 * sin(PI * (phraseTime - 2.05) / 0.70)
                                else -> 0.05
                            }
                            val pitch = 148.0 + 18.0 * sin(2.0 * PI * 0.8 * t)
                            val chest = 0.11 * sin(2.0 * PI * pitch * t) * syllableGate
                            val mud = 0.09 * sin(2.0 * PI * 280.0 * t) * syllableGate
                            val formant1 = 0.12 * sin(2.0 * PI * 1450.0 * t) * syllableGate
                            val formant2 = 0.09 * sin(2.0 * PI * 2950.0 * t) * syllableGate
                            val breath = 0.03 * sin(2.0 * PI * 5800.0 * t) * syllableGate
                            rawL = chest + mud + formant1 + formant2 + breath
                            rawR = rawL
                        }

                        // Step 2: Music Detail & Brightness Test (full-range bass + chord + sparkling air)
                        1 -> {
                            val beatEnv = exp(-((t % 0.6) * 5.5))
                            val subBass = 0.14 * sin(2.0 * PI * 64.0 * t) * beatEnv
                            val bass = 0.13 * sin(2.0 * PI * 128.0 * t) * beatEnv
                            val chord = (0.09 * sin(2.0 * PI * 261.63 * t) +
                                0.08 * sin(2.0 * PI * 329.63 * t) +
                                0.07 * sin(2.0 * PI * 523.25 * t))
                            val airSparkle = (0.045 * sin(2.0 * PI * 4200.0 * t) +
                                0.040 * sin(2.0 * PI * 8200.0 * t) +
                                0.032 * sin(2.0 * PI * 13500.0 * t)) * (0.45 + 0.55 * beatEnv)
                            val stereoSpread = 0.028 * sin(2.0 * PI * 6400.0 * t) * sin(2.0 * PI * 0.5 * t)
                            rawL = subBass + bass + chord + airSparkle + stereoSpread
                            rawR = subBass + bass + chord + airSparkle - stereoSpread
                        }

                        // Step 3: Soft-Sound Sensitivity (alternating quiet acoustic whisper & normal phrase)
                        else -> {
                            val isQuietDetail = phraseTime in 1.4..3.0
                            val env = if (isQuietDetail) {
                                0.09 * (0.6 + 0.4 * sin(2.0 * PI * 1.5 * t))
                            } else {
                                0.42 * (0.5 + 0.5 * exp(-((t % 0.45) * 6.0)))
                            }
                            val body = 0.20 * sin(2.0 * PI * 220.0 * t) * env
                            val detailMid = 0.18 * sin(2.0 * PI * 1850.0 * t) * env
                            val detailHigh = 0.14 * sin(2.0 * PI * 4600.0 * t) * env
                            val shimmer = 0.09 * sin(2.0 * PI * 9200.0 * t) * env
                            rawL = body + detailMid + detailHigh + shimmer
                            rawR = body + detailMid + detailHigh - shimmer * 0.6
                        }
                    }

                    val (outL, outR) = processor.processStereoSample(rawL, rawR)
                    pcm[i * 2] = (outL.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                    pcm[i * 2 + 1] = (outR.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                    cursor++
                }

                try {
                    track.write(pcm, 0, pcm.size)
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }

    fun stopAuditionLoop() {
        auditionJob?.cancel()
        auditionJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
    }
}

/**
 * Monitors & processes Microphone / Voice Input frames through a dedicated
 * HeadphoneAccommodationsProcessor instance when "Apply to Voice/Mic Input" is enabled,
 * reporting real-time conditioned voice level telemetry without speaker feedback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceMicAccommodationsMonitor(
    private val onTelemetry: (active: Boolean, levelDb: Float) -> Unit
) {
    private val hwScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconcileSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var monitorJob: Job? = null

    @Volatile
    private var activeCapture: UnprocessedMicFactory.OpenMicCapture? = null

    @Volatile
    private var desiredEnabled: Boolean = false

    @Volatile
    private var desiredProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE

    @Volatile
    private var desiredIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE

    @Volatile
    private var hasRecordPermission: Boolean = false

    @Volatile
    private var wasActiveTelemetry: Boolean = false

    init {
        hwScope.launch {
            for (signal in reconcileSignal) {
                reconcileHardware()
            }
        }
    }

    fun configure(
        enabled: Boolean,
        profile: AccommodationToneProfile,
        intensity: SoftSoundIntensity,
        hasRecordPermission: Boolean
    ) {
        this.desiredEnabled = enabled
        this.desiredProfile = profile
        this.desiredIntensity = intensity
        this.hasRecordPermission = hasRecordPermission
        if ((!enabled || !hasRecordPermission) && wasActiveTelemetry) {
            wasActiveTelemetry = false
            onTelemetry(false, -60f)
        }
        reconcileSignal.trySend(Unit)
    }

    @SuppressLint("MissingPermission")
    private suspend fun reconcileHardware() {
        val shouldRun = desiredEnabled && hasRecordPermission
        if (!shouldRun) {
            stopBlocking()
            return
        }
        if (monitorJob?.isActive == true) return

        stopBlocking()
        if (!desiredEnabled || !hasRecordPermission) return

        val capture = UnprocessedMicFactory.openRawMicCapture()
        if (capture == null) {
            wasActiveTelemetry = false
            onTelemetry(false, -60f)
            return
        }
        activeCapture = capture
        val record = capture.audioRecord
        val localProcessor = HeadphoneAccommodationsProcessor(capture.sampleRate).apply {
            configure(isEnabled = desiredEnabled, profile = desiredProfile, intensity = desiredIntensity)
        }

        monitorJob = streamScope.launch {
            val frameSamples = (capture.sampleRate * 0.04).toInt().coerceAtLeast(640)
            val buf = ShortArray(frameSamples)
            var lastReportMs = 0L
            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        localProcessor.configure(
                            isEnabled = desiredEnabled,
                            profile = desiredProfile,
                            intensity = desiredIntensity
                        )
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = buf[i] / 32768.0
                            val (procL, _) = localProcessor.processStereoSample(s, s)
                            sumSq += procL * procL
                        }
                        val rms = sqrt(sumSq / read).coerceAtLeast(1e-6)
                        val levelDb = (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastReportMs >= 80L) {
                            lastReportMs = nowMs
                            wasActiveTelemetry = true
                            onTelemetry(true, levelDb)
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                capture.release()
                if (activeCapture === capture) {
                    activeCapture = null
                }
                if (wasActiveTelemetry) {
                    wasActiveTelemetry = false
                    onTelemetry(false, -60f)
                }
            }
        }
    }

    private suspend fun stopBlocking() {
        val job = monitorJob
        monitorJob = null
        try {
            activeCapture?.audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            job?.cancelAndJoin()
        } catch (_: Throwable) {
        }
        activeCapture?.release()
        activeCapture = null
    }

    fun stop() {
        desiredEnabled = false
        if (wasActiveTelemetry) {
            wasActiveTelemetry = false
            onTelemetry(false, -60f)
        }
        reconcileSignal.trySend(Unit)
    }
}
