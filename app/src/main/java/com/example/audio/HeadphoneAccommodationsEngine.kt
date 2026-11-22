package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.model.AccommodationToneProfile
import com.example.model.CustomAudioSetupUiState
import com.example.model.SetupAuditionSlot
import com.example.model.SoftSoundIntensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * - 3 Official Apple Tone Profiles (Balanced Tone, Vocal Range with 100Hz HPF, Brightness)
 * - Boost Soft Sounds: dedicated DynamicRangeCompressor + makeup gain while scaling the
 *   Tone Profile EQ gains by 0.6x (Slight), 1.0x (Moderate), or 1.4x (Strong).
 */
class HeadphoneAccommodationsProcessor(private val sampleRate: Int = 44100) {

    private val vocalHpf100 = StereoBiquad()
    private val mudCutBand = StereoBiquad()
    private val eqBand1 = StereoBiquad()
    private val eqBand2 = StereoBiquad()
    private val eqBand3 = StereoBiquad()
    private val eqBand4 = StereoBiquad()

    private val softSoundCompressor = DynamicRangeCompressor(sampleRate)

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var vocalHpfEnabled: Boolean = false

    @Volatile
    private var outputGainLinear: Double = 1.0

    val latestGainReductionDb: Float
        get() = if (enabled) softSoundCompressor.latestGainReductionDb else 0f

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
        outputGainDb: Float = 0f
    ) {
        enabled = isEnabled
        val scale = intensity.eqScale.toDouble()
        val clampedOutDb = outputGainDb.coerceIn(
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
            com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
        )
        outputGainLinear = Math.pow(10.0, clampedOutDb / 20.0)

        when (profile) {
            AccommodationToneProfile.BALANCED_TONE -> {
                vocalHpfEnabled = false
                mudCutBand.setPeaking(sampleRate, 300.0, 1.3, -2.0)
                eqBand1.setPeaking(sampleRate, 1000.0, 1.4, (1.0 * scale).coerceAtMost(3.0))
                eqBand2.setPeaking(sampleRate, 3000.0, 1.4, (1.8 * scale).coerceAtMost(3.0))
                eqBand3.setPeaking(sampleRate, 8000.0, 1.5, (2.1 * scale).coerceAtMost(3.0))
                eqBand4.setPeaking(sampleRate, 14000.0, 1.5, (1.5 * scale).coerceAtMost(3.0))
            }

            AccommodationToneProfile.VOCAL_RANGE -> {
                vocalHpfEnabled = true
                vocalHpf100.setHighPass(sampleRate, 100.0, 0.7071)
                mudCutBand.setPeaking(sampleRate, 250.0, 1.3, -2.0)
                eqBand1.setPeaking(sampleRate, 1000.0, 1.4, (1.5 * scale).coerceAtMost(3.0))
                eqBand2.setPeaking(sampleRate, 2500.0, 1.5, (2.1 * scale).coerceAtMost(3.0))
                eqBand3.setPeaking(sampleRate, 4000.0, 1.5, (1.8 * scale).coerceAtMost(3.0))
                eqBand4.setPeaking(sampleRate, 8000.0, 1.5, 0.0)
            }

            AccommodationToneProfile.BRIGHTNESS -> {
                vocalHpfEnabled = false
                mudCutBand.setPeaking(sampleRate, 300.0, 1.3, -2.0)
                eqBand1.setPeaking(sampleRate, 4000.0, 2.4, (1.1 * scale).coerceAtMost(3.0))
                eqBand2.setPeaking(sampleRate, 8000.0, 2.4, (1.4 * scale).coerceAtMost(3.0))
                eqBand3.setPeaking(sampleRate, 12000.0, 2.4, (1.7 * scale).coerceAtMost(3.0))
                eqBand4.setPeaking(sampleRate, 16000.0, 2.4, (1.3 * scale).coerceAtMost(3.0))
            }
        }

        softSoundCompressor.configureCustom(
            isEnabled = isEnabled,
            threshold = intensity.thresholdDb,
            knee = intensity.kneeDb,
            compRatio = intensity.ratio,
            attackMs = 8f,
            releaseMs = 160f,
            makeupGainDb = intensity.makeupGainDb
        )
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) return inL to inR

        var l = inL
        var r = inR
        if (vocalHpfEnabled) {
            val (hpL, hpR) = vocalHpf100.processStereo(l, r)
            l = hpL
            r = hpR
        }
        val (cutL, cutR) = mudCutBand.processStereo(l, r)
        val (e1L, e1R) = eqBand1.processStereo(cutL, cutR)
        val (e2L, e2R) = eqBand2.processStereo(e1L, e1R)
        val (e3L, e3R) = eqBand3.processStereo(e2L, e2R)
        val (e4L, e4R) = eqBand4.processStereo(e3L, e3R)

        val (compL, compR) = softSoundCompressor.processStereoSample(e4L, e4R)
        val outL = normalizePeakHeadroom(compL * outputGainLinear)
        val outR = normalizePeakHeadroom(compR * outputGainLinear)
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
         * Computes analytical EQ + HPF frequency response magnitude (in dB) for a given
         * Tone Profile and Soft Sound Intensity scaling (0.6x / 1.0x / 1.4x),
         * including the -2.0 dB 250Hz-350Hz headroom cut and +3.0 dB balanced boost ceiling.
         */
        fun computeResponseDbAt(
            freqHz: Double,
            profile: AccommodationToneProfile,
            intensity: SoftSoundIntensity,
            sampleRate: Int = 44100
        ): Float {
            val scale = intensity.eqScale.toDouble()
            var totalDb = 0.0

            val mud = StereoBiquad()
            val b1 = StereoBiquad()
            val b2 = StereoBiquad()
            val b3 = StereoBiquad()
            val b4 = StereoBiquad()

            when (profile) {
                AccommodationToneProfile.BALANCED_TONE -> {
                    mud.setPeaking(sampleRate, 300.0, 1.3, -2.0)
                    b1.setPeaking(sampleRate, 1000.0, 1.4, (1.0 * scale).coerceAtMost(3.0))
                    b2.setPeaking(sampleRate, 3000.0, 1.4, (1.8 * scale).coerceAtMost(3.0))
                    b3.setPeaking(sampleRate, 8000.0, 1.5, (2.1 * scale).coerceAtMost(3.0))
                    b4.setPeaking(sampleRate, 14000.0, 1.5, (1.5 * scale).coerceAtMost(3.0))
                }

                AccommodationToneProfile.VOCAL_RANGE -> {
                    val hpf = StereoBiquad().apply { setHighPass(sampleRate, 100.0, 0.7071) }
                    totalDb += hpf.magnitudeDbAt(sampleRate, freqHz).coerceAtLeast(-14.0)
                    mud.setPeaking(sampleRate, 250.0, 1.3, -2.0)
                    b1.setPeaking(sampleRate, 1000.0, 1.4, (1.5 * scale).coerceAtMost(3.0))
                    b2.setPeaking(sampleRate, 2500.0, 1.5, (2.1 * scale).coerceAtMost(3.0))
                    b3.setPeaking(sampleRate, 4000.0, 1.5, (1.8 * scale).coerceAtMost(3.0))
                    b4.setPeaking(sampleRate, 8000.0, 1.5, 0.0)
                }

                AccommodationToneProfile.BRIGHTNESS -> {
                    mud.setPeaking(sampleRate, 300.0, 1.3, -2.0)
                    b1.setPeaking(sampleRate, 4000.0, 2.4, (1.1 * scale).coerceAtMost(3.0))
                    b2.setPeaking(sampleRate, 8000.0, 2.4, (1.4 * scale).coerceAtMost(3.0))
                    b3.setPeaking(sampleRate, 12000.0, 2.4, (1.7 * scale).coerceAtMost(3.0))
                    b4.setPeaking(sampleRate, 16000.0, 2.4, (1.3 * scale).coerceAtMost(3.0))
                }
            }

            totalDb += mud.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b1.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b2.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b3.magnitudeDbAt(sampleRate, freqHz)
            totalDb += b4.magnitudeDbAt(sampleRate, freqHz)
            return totalDb.toFloat()
        }
    }
}

/**
 * Interactive "Custom Audio Setup" A/B Wizard Engine:
 * Loops a speech + acoustic detail audition phrase and lets the user toggle live between
 * Standard vs. Custom Profile A / B (comparing Balanced Tone, Vocal Range, and Brightness)
 * before automatically applying the winning profile.
 */
class CustomAudioSetupAuditionEngine {
    private val sampleRate = 44100
    private val processor = HeadphoneAccommodationsProcessor(sampleRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var auditionJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _state = MutableStateFlow(CustomAudioSetupUiState())
    val state: StateFlow<CustomAudioSetupUiState> = _state.asStateFlow()

    fun open(initialIntensity: SoftSoundIntensity) {
        _state.value = CustomAudioSetupUiState(
            isOpen = true,
            stepIndex = 0,
            optionA = AccommodationToneProfile.BALANCED_TONE,
            optionB = AccommodationToneProfile.VOCAL_RANGE,
            activeAuditionSlot = SetupAuditionSlot.OPTION_A,
            chosenProfile = AccommodationToneProfile.BALANCED_TONE,
            softIntensity = initialIntensity,
            isCompleted = false
        )
        syncProcessorFromState()
        startAuditionLoop()
    }

    fun selectAuditionSlot(slot: SetupAuditionSlot) {
        val cur = _state.value
        if (!cur.isOpen) return
        _state.value = cur.copy(activeAuditionSlot = slot)
        syncProcessorFromState()
    }

    fun selectIntensity(intensity: SoftSoundIntensity) {
        val cur = _state.value
        if (!cur.isOpen) return
        _state.value = cur.copy(softIntensity = intensity)
        syncProcessorFromState()
    }

    fun chooseOptionForCurrentRound(slot: SetupAuditionSlot) {
        val cur = _state.value
        if (!cur.isOpen || cur.isCompleted) return

        val pickedProfile = when (slot) {
            SetupAuditionSlot.STANDARD -> AccommodationToneProfile.BALANCED_TONE
            SetupAuditionSlot.OPTION_A -> cur.optionA
            SetupAuditionSlot.OPTION_B -> cur.optionB
        }

        if (cur.stepIndex == 0) {
            // Round 1 winner advances against Brightness in Round 2
            _state.value = cur.copy(
                stepIndex = 1,
                optionA = pickedProfile,
                optionB = AccommodationToneProfile.BRIGHTNESS,
                activeAuditionSlot = SetupAuditionSlot.OPTION_A,
                chosenProfile = pickedProfile
            )
            syncProcessorFromState()
        } else {
            // Final winner chosen
            _state.value = cur.copy(
                stepIndex = 2,
                chosenProfile = pickedProfile,
                activeAuditionSlot = slot,
                isCompleted = true
            )
            syncProcessorFromState()
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
        when (st.activeAuditionSlot) {
            SetupAuditionSlot.STANDARD -> {
                processor.configure(
                    isEnabled = false,
                    profile = st.chosenProfile,
                    intensity = st.softIntensity
                )
            }

            SetupAuditionSlot.OPTION_A -> {
                processor.configure(
                    isEnabled = true,
                    profile = if (st.isCompleted) st.chosenProfile else st.optionA,
                    intensity = st.softIntensity
                )
            }

            SetupAuditionSlot.OPTION_B -> {
                processor.configure(
                    isEnabled = true,
                    profile = if (st.isCompleted) st.chosenProfile else st.optionB,
                    intensity = st.softIntensity
                )
            }
        }
    }

    private fun startAuditionLoop() {
        stopAuditionLoop()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = try {
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
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
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
                for (i in 0 until frameCount) {
                    val t = cursor.toDouble() / sampleRate
                    val phraseTime = t % 3.2

                    // Warm acoustic bed + quiet whisper/soft-detail chime + speech-like formant cadence
                    val syllableGate = when {
                        phraseTime in 0.15..0.55 -> sin(PI * (phraseTime - 0.15) / 0.40)
                        phraseTime in 0.70..1.25 -> sin(PI * (phraseTime - 0.70) / 0.55)
                        phraseTime in 1.45..1.90 -> sin(PI * (phraseTime - 1.45) / 0.45)
                        phraseTime in 2.15..2.85 -> 0.35 * sin(PI * (phraseTime - 2.15) / 0.70) // soft detail passage
                        else -> 0.08
                    }

                    val rumble = 0.08 * sin(2.0 * PI * 68.0 * t)
                    val warmFundamental = 0.14 * sin(2.0 * PI * 196.0 * t) * syllableGate
                    val speechFormant1 = 0.13 * sin(2.0 * PI * 1050.0 * t) * syllableGate
                    val speechFormant2 = 0.10 * sin(2.0 * PI * 2650.0 * t) * syllableGate
                    val airChime = (0.045 * sin(2.0 * PI * 6200.0 * t) + 0.03 * sin(2.0 * PI * 11200.0 * t)) *
                        (0.35 + 0.65 * exp(-((t % 0.8) * 6.0)))
                    val stereoShimmer = 0.025 * sin(2.0 * PI * 4200.0 * t) * sin(2.0 * PI * 0.4 * t)

                    val rawL = rumble + warmFundamental + speechFormant1 + speechFormant2 + airChime + stereoShimmer
                    val rawR = rumble + warmFundamental + speechFormant1 + speechFormant2 + airChime - stereoShimmer

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
class VoiceMicAccommodationsMonitor(
    private val onTelemetry: (active: Boolean, levelDb: Float) -> Unit
) {
    private val sampleRate = 16000
    private val processor = HeadphoneAccommodationsProcessor(sampleRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    @SuppressLint("MissingPermission")
    fun configure(
        enabled: Boolean,
        profile: AccommodationToneProfile,
        intensity: SoftSoundIntensity,
        hasRecordPermission: Boolean
    ) {
        processor.configure(isEnabled = enabled, profile = profile, intensity = intensity)
        if (!enabled || !hasRecordPermission) {
            stop()
            return
        }
        if (monitorJob != null) return

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: Throwable) {
            onTelemetry(false, -60f)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record.release()
            } catch (_: Throwable) {
            }
            onTelemetry(false, -60f)
            return
        }

        monitorJob = scope.launch {
            val buf = ShortArray(640)
            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = buf[i] / 32768.0
                            val (procL, _) = processor.processStereoSample(s, s)
                            sumSq += procL * procL
                        }
                        val rms = sqrt(sumSq / read).coerceAtLeast(1e-6)
                        val levelDb = (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
                        onTelemetry(true, levelDb)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                try {
                    record.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        onTelemetry(false, -60f)
    }
}
