package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.model.AudioMixMode
import com.example.model.BackgroundSoundPreset
import com.example.model.EqPresetsCatalog
import com.example.model.HearingCheckUiState
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.SpatialMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1. Apple Music "Sound Check" Dynamic Crest Controller (Target: -16 LUFS):
 * Uses a K-weighted ITU-R BS.1770 style sidechain RMS detector (100Hz HPF + 1.6kHz +4dB shelf)
 * paired with a momentary transient crest tracker so quiet videos/songs and loud tracks smoothly
 * converge toward -16 LUFS without squashing musical punch or clipping dynamic transients.
 */
class SoundCheckNormalizer(private val sampleRate: Int = 44100) {
    private val targetLufs = com.example.model.AdaptiveMultibandPhaseSpecs.SOUND_CHECK_TARGET_LUFS

    // K-weighting detector stages
    private val kHighPass = StereoBiquad().apply { setHighPass(sampleRate, 100.0, 0.7071) }
    private val kHighShelf = StereoBiquad().apply { setHighShelf(sampleRate, 1600.0, 0.7071, 4.0) }

    private val rmsAlpha = exp(-1.0 / (0.350 * sampleRate)) // 350ms LUFS integration window
    private val peakAttackAlpha = exp(-1.0 / (0.005 * sampleRate))
    private val peakReleaseAlpha = exp(-1.0 / (0.180 * sampleRate))
    private val gainAttackAlpha = exp(-1.0 / (0.450 * sampleRate))
    private val gainReleaseAlpha = exp(-1.0 / (1.200 * sampleRate))

    private var meanSquareEnergy = 10.0.pow(-16.0 / 10.0)
    private var peakEnvelope = 10.0.pow(-16.0 / 20.0)
    private var currentTrimDb = 0.0

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var latestLufs: Float = -16f
        private set

    @Volatile
    var latestTrimDb: Float = 0f
        private set

    @Volatile
    var latestCrestDb: Float = 0f
        private set

    fun reset() {
        meanSquareEnergy = 10.0.pow(-16.0 / 10.0)
        peakEnvelope = 10.0.pow(-16.0 / 20.0)
        currentTrimDb = 0.0
        latestLufs = -60f
        latestTrimDb = 0f
        latestCrestDb = 0f
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val (hpL, hpR) = kHighPass.processStereo(inL, inR)
        val (kwL, kwR) = kHighShelf.processStereo(hpL, hpR)
        val instPower = 0.5 * (kwL * kwL + kwR * kwR)

        meanSquareEnergy = rmsAlpha * meanSquareEnergy + (1.0 - rmsAlpha) * instPower
        val lufs = if (meanSquareEnergy > 1e-9) {
            (-0.691 + 10.0 * log10(meanSquareEnergy)).coerceIn(-60.0, 0.0)
        } else {
            -60.0
        }
        latestLufs = lufs.toFloat()

        val instPeak = maxOf(abs(inL), abs(inR)).coerceAtLeast(1e-6)
        val pAlpha = if (instPeak > peakEnvelope) peakAttackAlpha else peakReleaseAlpha
        peakEnvelope = pAlpha * peakEnvelope + (1.0 - pAlpha) * instPeak
        val peakDb = (20.0 * log10(peakEnvelope.coerceAtLeast(1e-6))).coerceIn(-60.0, 0.0)
        val crestDb = (peakDb - lufs).coerceIn(0.0, 30.0)
        latestCrestDb = crestDb.toFloat()

        if (!enabled) {
            currentTrimDb = 0.0
            latestTrimDb = 0f
            return inL to inR
        }

        // Only normalize when meaningful audio signal is present (> -48 LUFS);
        // cap upward makeup cleanly against transient peak headroom to preserve musical punch.
        val desiredTrim = if (lufs > -48.0) {
            val rawTrim = (targetLufs - lufs).coerceIn(-12.0, 12.0)
            if (rawTrim > 0.0) {
                val headroomLimitDb = (-1.2 - peakDb).coerceAtLeast(0.0)
                minOf(rawTrim, headroomLimitDb)
            } else {
                rawTrim
            }
        } else {
            0.0
        }
        val slew = if (desiredTrim < currentTrimDb) gainAttackAlpha else gainReleaseAlpha
        currentTrimDb = slew * currentTrimDb + (1.0 - slew) * desiredTrim
        latestTrimDb = currentTrimDb.toFloat()

        val linear = 10.0.pow(currentTrimDb / 20.0)
        val outL = softCrestGuard(inL * linear)
        val outR = softCrestGuard(inR * linear)
        return outL to outR
    }

    private fun softCrestGuard(x: Double): Double {
        val thresh = 0.89
        val ax = abs(x)
        if (ax <= thresh) return x
        val ceil = com.example.model.AdaptiveMultibandPhaseSpecs.CREST_SOFT_LIMITER_CEILING
        val headroom = ceil - thresh
        val shaped = thresh + headroom * kotlin.math.tanh((ax - thresh) / headroom)
        return if (x >= 0.0) shaped else -shaped
    }

    companion object {
        /**
         * Helper for external system audio streams measured via Visualizer/RMS peak.
         */
        fun computeTargetTrimDb(measuredRmsDbfs: Float): Float {
            if (measuredRmsDbfs < -48f) return 0f
            return (-16f - measuredRmsDbfs).coerceIn(-12f, 12f)
        }
    }
}

/**
 * 6. Voice Isolation Mode (Podcast & Speech Noise Gate):
 * Combines a 120Hz-6.5kHz vocal bandpass filter with a smooth downward expander / noise gate
 * (-38 dB threshold) to strip background hiss, fan noise, and low-end rumble.
 */
class VoiceIsolationProcessor(private val sampleRate: Int = 44100) {
    private val hpf120 = StereoBiquad().apply { setHighPass(sampleRate, 120.0, 0.7071) }
    private val lpf6500 = StereoBiquad().apply { setLowPass(sampleRate, 6500.0, 0.7071) }
    private val presenceLift = StereoBiquad().apply { setPeaking(sampleRate, 2200.0, 1.1, 2.0) }

    private val thresholdDb = -38.0
    private val expanderRatio = 3.2
    private val attackCoeff = exp(-1.0 / (0.003 * sampleRate))  // 3ms fast open
    private val releaseCoeff = exp(-1.0 / (0.140 * sampleRate)) // 140ms natural close

    private var envDb = -60.0

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var latestGateAttenuationDb: Float = 0f
        private set

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            latestGateAttenuationDb = 0f
            return inL to inR
        }

        // 120Hz - 6.5kHz vocal bandpass + subtle clarity lift
        val (hpL, hpR) = hpf120.processStereo(inL, inR)
        val (bpL, bpR) = lpf6500.processStereo(hpL, hpR)
        val (vL, vR) = presenceLift.processStereo(bpL, bpR)

        // Smooth downward expander below -38 dB threshold
        val amp = maxOf(abs(vL), abs(vR)).coerceAtLeast(1e-6)
        val levelDb = 20.0 * log10(amp)
        val coeff = if (levelDb > envDb) attackCoeff else releaseCoeff
        envDb = coeff * envDb + (1.0 - coeff) * levelDb

        val attenDb = if (envDb < thresholdDb) {
            ((envDb - thresholdDb) * (expanderRatio - 1.0)).coerceIn(-36.0, 0.0)
        } else {
            0.0
        }
        latestGateAttenuationDb = attenDb.toFloat()

        val gateGain = 10.0.pow(attenDb / 20.0)
        return (vL * gateGain) to (vR * gateGain)
    }
}

/**
 * 3. iOS Background Sounds Mixer:
 * Procedural stereo ambient synthesizer for Apple's 4 presets:
 * Balanced Noise | Dark Noise | Rain | Ocean
 */
class BackgroundSoundsSynthesizer(private val sampleRate: Int = 44100) {
    private var seedL = 0x13579BDF
    private var seedR = 0x2468ACE1

    // Paul Kellet pink-noise filter state per channel
    private var b0L = 0.0; private var b1L = 0.0; private var b2L = 0.0
    private var b3L = 0.0; private var b4L = 0.0; private var b5L = 0.0; private var b6L = 0.0
    private var b0R = 0.0; private var b1R = 0.0; private var b2R = 0.0
    private var b3R = 0.0; private var b4R = 0.0; private var b5R = 0.0; private var b6R = 0.0

    // Dark noise brown/warm filters
    private var brownL = 0.0
    private var brownR = 0.0
    private val darkLpf = StereoBiquad().apply { setLowPass(sampleRate, 420.0, 0.85) }

    // Rain droplet shimmer & bandpass
    private val rainBandPass = StereoBiquad().apply { setBandPass(sampleRate, 1800.0, 0.65) }
    private var rainDropEnvL = 0.0
    private var rainDropEnvR = 0.0

    // Ocean wave swell state
    private val oceanLowPass = StereoBiquad().apply { setLowPass(sampleRate, 680.0, 0.707) }
    private var oceanSampleCursor = 0L

    private fun nextWhiteL(): Double {
        seedL = seedL * 1664525 + 1013904223
        return (((seedL ushr 8) and 0xFFFF) / 32767.5) - 1.0
    }

    private fun nextWhiteR(): Double {
        seedR = seedR * 1664525 + 1013904223
        return (((seedR ushr 8) and 0xFFFF) / 32767.5) - 1.0
    }

    private fun nextPinkStereo(): Pair<Double, Double> {
        val wL = nextWhiteL()
        b0L = 0.99886 * b0L + wL * 0.0555179
        b1L = 0.99332 * b1L + wL * 0.0750759
        b2L = 0.96900 * b2L + wL * 0.1538520
        b3L = 0.86650 * b3L + wL * 0.3104856
        b4L = 0.55000 * b4L + wL * 0.5329522
        b5L = -0.7616 * b5L - wL * 0.0168980
        val pL = (b0L + b1L + b2L + b3L + b4L + b5L + b6L + wL * 0.5362) * 0.11
        b6L = wL * 0.115926

        val wR = nextWhiteR()
        b0R = 0.99886 * b0R + wR * 0.0555179
        b1R = 0.99332 * b1R + wR * 0.0750759
        b2R = 0.96900 * b2R + wR * 0.1538520
        b3R = 0.86650 * b3R + wR * 0.3104856
        b4R = 0.55000 * b4R + wR * 0.5329522
        b5R = -0.7616 * b5R - wR * 0.0168980
        val pR = (b0R + b1R + b2R + b3R + b4R + b5R + b6R + wR * 0.5362) * 0.11
        b6R = wR * 0.115926

        return pL to pR
    }

    fun nextStereoSample(preset: BackgroundSoundPreset, volume: Float): Pair<Double, Double> {
        if (preset == BackgroundSoundPreset.OFF || volume <= 0.001f) {
            return 0.0 to 0.0
        }

        val (rawL, rawR) = when (preset) {
            BackgroundSoundPreset.OFF -> 0.0 to 0.0

            BackgroundSoundPreset.BALANCED_NOISE -> {
                val (pL, pR) = nextPinkStereo()
                (pL * 0.42) to (pR * 0.42)
            }

            BackgroundSoundPreset.DARK_NOISE -> {
                brownL = (brownL + nextWhiteL() * 0.035).coerceIn(-1.0, 1.0) * 0.992
                brownR = (brownR + nextWhiteR() * 0.035).coerceIn(-1.0, 1.0) * 0.992
                val (dL, dR) = darkLpf.processStereo(brownL * 0.95, brownR * 0.95)
                (dL * 0.65) to (dR * 0.65)
            }

            BackgroundSoundPreset.RAIN -> {
                val (pL, pR) = nextPinkStereo()
                val wL = nextWhiteL()
                val wR = nextWhiteR()
                if (abs(wL) > 0.993) rainDropEnvL = 0.45
                if (abs(wR) > 0.993) rainDropEnvR = 0.45
                rainDropEnvL *= 0.992
                rainDropEnvR *= 0.992
                val (sL, sR) = rainBandPass.processStereo(wL * (0.28 + rainDropEnvL), wR * (0.28 + rainDropEnvR))
                (pL * 0.25 + sL * 0.26) to (pR * 0.25 + sR * 0.26)
            }

            BackgroundSoundPreset.OCEAN -> {
                val (pL, pR) = nextPinkStereo()
                val t = oceanSampleCursor++.toDouble() / sampleRate
                // ~9 second surf cycle (0.11 Hz) + slower secondary swell
                val swell = 0.28 + 0.72 * (0.5 + 0.5 * sin(2.0 * PI * 0.11 * t)) *
                    (0.75 + 0.25 * sin(2.0 * PI * 0.047 * t + 1.1))
                val (surfL, surfR) = oceanLowPass.processStereo(pL, pR)
                val foamL = pL * 0.22 * swell * swell
                val foamR = pR * 0.22 * swell * swell
                ((surfL * 0.52 * swell) + foamL) to ((surfR * 0.52 * swell) + foamR)
            }
        }

        val gain = (volume.coerceIn(0f, 1f).toDouble()).pow(1.4) * 0.55
        return (rawL * gain) to (rawR * gain)
    }
}

/**
 * Standalone Background Sounds AudioTrack runner used when the built-in Test Audio Synthesizer is OFF,
 * allowing Apple's ambient sounds to play underneath Spotify, YouTube, or Podcasts.
 */
class StandaloneBackgroundAudioPlayer {
    private val sampleRate = 44100
    private val synth = BackgroundSoundsSynthesizer(sampleRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var activePreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF

    @Volatile
    private var activeVolume: Float = 0.28f

    fun update(preset: BackgroundSoundPreset, volume: Float, suppressBecauseSynthPlaying: Boolean) {
        activePreset = preset
        activeVolume = volume

        val shouldRun = preset != BackgroundSoundPreset.OFF && volume > 0.005f && !suppressBecauseSynthPlaying
        if (shouldRun && playJob == null) {
            start()
        } else if (!shouldRun && playJob != null) {
            stop()
        }
    }

    private fun start() {
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
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
            stop()
            return
        }

        playJob = scope.launch {
            val frames = 1024
            val pcm = ShortArray(frames * 2)
            while (isActive) {
                val preset = activePreset
                val vol = activeVolume
                for (i in 0 until frames) {
                    val (l, r) = synth.nextStereoSample(preset, vol)
                    pcm[i * 2] = (l.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                    pcm[i * 2 + 1] = (r.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                }
                try {
                    track.write(pcm, 0, pcm.size)
                } catch (_: Throwable) {
                    break
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
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
 * 2. Conversation Awareness (Smart Auto-Ducking):
 * Monitors microphone audio via 300Hz-3kHz speech bandpass, smoothly ducks music by 75% (to 0.25x)
 * when speech is detected, and restores full volume 2 seconds after speaking stops.
 */
class ConversationAwarenessMonitor(
    private val onDuckStateChanged: (speechDetected: Boolean, duckGainLinear: Float) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val speechLowCut = StereoBiquad().apply { setHighPass(16000, 300.0, 0.707) }
    private val speechHighCut = StereoBiquad().apply { setLowPass(16000, 3000.0, 0.707) }

    @Volatile
    var isSpeechDetected: Boolean = false
        private set

    @Volatile
    var currentDuckGain: Float = 1f
        private set

    @SuppressLint("MissingPermission")
    fun setEnabled(enabled: Boolean, hasRecordPermission: Boolean) {
        if (!enabled || !hasRecordPermission) {
            stop()
            return
        }
        if (monitorJob != null) return

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: Throwable) {
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record.release()
            } catch (_: Throwable) {
            }
            return
        }

        monitorJob = scope.launch {
            val buf = ShortArray(640) // 40ms frames
            var lastVoiceTimestampMs = 0L
            var speechActive = false
            var gain = 1f

            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val norm = buf[i] / 32768.0
                            val (hp, _) = speechLowCut.processStereo(norm, 0.0)
                            val (bp, _) = speechHighCut.processStereo(hp, 0.0)
                            sumSq += bp * bp
                        }
                        val rms = kotlin.math.sqrt(sumSq / read)
                        val rmsDb = if (rms > 1e-6) 20.0 * log10(rms) else -90.0
                        val now = System.currentTimeMillis()

                        // Threshold tuned for close-field speech in 300Hz-3kHz band
                        if (rmsDb > -33.0) {
                            lastVoiceTimestampMs = now
                            speechActive = true
                        } else if (speechActive && (now - lastVoiceTimestampMs) >= 2000L) {
                            speechActive = false
                        }

                        val targetGain = if (speechActive) 0.25f else 1.0f // 75% duck
                        val stepAlpha = if (targetGain < gain) 0.35f else 0.14f
                        gain += (targetGain - gain) * stepAlpha
                        if (abs(gain - targetGain) < 0.005f) gain = targetGain

                        isSpeechDetected = speechActive
                        currentDuckGain = gain
                        onDuckStateChanged(speechActive, gain)
                    } else {
                        delay(30)
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
        isSpeechDetected = false
        currentDuckGain = 1f
        onDuckStateChanged(false, 1f)
    }
}

/**
 * 4. Quick Hearing Check (60-Second Audiogram EQ Generator):
 * Plays pure test tones at 500Hz, 1kHz, 2kHz, 4kHz, 8kHz rising smoothly in level
 * and builds a personalized 10-band compensation EQ curve from the user's "I Hear It" taps.
 */
class AudiogramCheckEngine {
    private val frequenciesHz = listOf(500, 1000, 2000, 4000, 8000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var toneJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _state = MutableStateFlow(HearingCheckUiState())
    val state: StateFlow<HearingCheckUiState> = _state.asStateFlow()

    @Volatile
    private var stepStartMs = 0L

    @Volatile
    private var currentLevelDb = -54f

    fun startCheck() {
        stopTone()
        _state.value = HearingCheckUiState(
            isActive = true,
            currentStepIndex = 0,
            frequenciesHz = frequenciesHz,
            currentLevelDb = -54f,
            progressFraction = 0f,
            measuredThresholdsDb = emptyList(),
            generatedEqCurveDb = null,
            isCompleted = false
        )
        launchToneForStep(0)
    }

    fun recordHeardCurrentTone() {
        val current = _state.value
        if (!current.isActive || current.isCompleted) return
        advanceWithThreshold(currentLevelDb)
    }

    fun skipCurrentTone() {
        val current = _state.value
        if (!current.isActive || current.isCompleted) return
        advanceWithThreshold(-12f) // Assume mild elevation if inaudible
    }

    private fun advanceWithThreshold(thresholdDb: Float) {
        val current = _state.value
        val updatedThresholds = current.measuredThresholdsDb + thresholdDb
        val nextStep = current.currentStepIndex + 1

        if (nextStep >= frequenciesHz.size) {
            stopTone()
            val curve = computeCompensationEqFromThresholds(updatedThresholds)
            _state.value = current.copy(
                measuredThresholdsDb = updatedThresholds,
                generatedEqCurveDb = curve,
                progressFraction = 1f,
                isCompleted = true
            )
        } else {
            _state.value = current.copy(
                currentStepIndex = nextStep,
                currentLevelDb = -54f,
                measuredThresholdsDb = updatedThresholds,
                progressFraction = nextStep.toFloat() / frequenciesHz.size
            )
            launchToneForStep(nextStep)
        }
    }

    private fun launchToneForStep(stepIdx: Int) {
        stopTone()
        val freq = frequenciesHz[stepIdx].toDouble()
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            trackFallbackTimer(stepIdx)
            return
        }

        audioTrack = track
        try {
            track.play()
        } catch (_: Throwable) {
            stopTone()
            trackFallbackTimer(stepIdx)
            return
        }

        stepStartMs = System.currentTimeMillis()
        currentLevelDb = -54f

        toneJob = scope.launch {
            val frames = 512
            val buf = ShortArray(frames)
            var phase = 0.0
            val phaseInc = 2.0 * PI * freq / sampleRate

            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - stepStartMs) / 1000f).coerceIn(0f, 12f)
                // 12 seconds per step * 5 tones = 60-second check, ramping -54 dBFS -> -6 dBFS
                val db = (-54f + elapsedSec * 4.0f).coerceAtMost(-6f)
                currentLevelDb = db
                val amp = 10.0.pow(db / 20.0)

                val stepProgress = (stepIdx + (elapsedSec / 12f)) / frequenciesHz.size
                _state.value = _state.value.copy(
                    currentLevelDb = db,
                    progressFraction = stepProgress.coerceIn(0f, 1f)
                )

                for (i in 0 until frames) {
                    buf[i] = (sin(phase) * amp * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
                    phase += phaseInc
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                }

                try {
                    track.write(buf, 0, buf.size)
                } catch (_: Throwable) {
                    break
                }

                if (elapsedSec >= 12f && _state.value.currentStepIndex == stepIdx && !_state.value.isCompleted) {
                    advanceWithThreshold(-6f)
                    break
                }
            }
        }
    }

    private fun trackFallbackTimer(stepIdx: Int) {
        stepStartMs = System.currentTimeMillis()
        toneJob = scope.launch {
            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - stepStartMs) / 1000f).coerceIn(0f, 12f)
                val db = (-54f + elapsedSec * 4.0f).coerceAtMost(-6f)
                currentLevelDb = db
                val stepProgress = (stepIdx + (elapsedSec / 12f)) / frequenciesHz.size
                _state.value = _state.value.copy(
                    currentLevelDb = db,
                    progressFraction = stepProgress.coerceIn(0f, 1f)
                )
                if (elapsedSec >= 12f) {
                    advanceWithThreshold(-6f)
                    break
                }
                delay(100)
            }
        }
    }

    fun cancelCheck() {
        stopTone()
        _state.value = HearingCheckUiState()
    }

    fun stopTone() {
        toneJob?.cancel()
        toneJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
    }

    companion object {
        /**
         * Converts 5 measured hearing thresholds (500Hz, 1kHz, 2kHz, 4kHz, 8kHz in dBFS)
         * into a smooth 10-band audiogram compensation EQ curve (32Hz..16kHz).
         * Normal acute threshold ~ -42 dBFS -> 0 dB offset; higher threshold -> proportional gentle boost.
         */
        fun computeCompensationEqFromThresholds(thresholdsDb: List<Float>): List<Float> {
            val baselineDb = -40f
            val deltas = List(5) { idx ->
                val measured = thresholdsDb.getOrElse(idx) { baselineDb }
                // Half-gain NAL style compensation relative to healthy reference -40 dBFS
                ((measured - baselineDb) * 0.45f).coerceIn(0f, 10.5f)
            }
            // Interpolate across 10 bands: [32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k]
            // deltas indices: 0=500Hz, 1=1kHz, 2=2kHz, 3=4kHz, 4=8kHz
            val raw = listOf(
                deltas[0] * 0.15f,                    // 32Hz
                deltas[0] * 0.25f,                    // 64Hz
                deltas[0] * 0.45f,                    // 125Hz
                deltas[0] * 0.72f,                    // 250Hz
                deltas[0],                            // 500Hz
                deltas[1],                            // 1kHz
                deltas[2],                            // 2kHz
                deltas[3],                            // 4kHz
                deltas[4],                            // 8kHz
                (deltas[4] * 0.85f).coerceIn(0f, 10f) // 16kHz
            )
            // Center around neutral mean so overall level stays clean and snap to 0.5 dB
            val avgBoost = raw.average().toFloat()
            val offset = if (avgBoost > 3.5f) -(avgBoost - 2.0f) * 0.35f else 0f
            return raw.map { valDb ->
                val clamped = (valDb + offset).coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
                (clamped * 2f).roundToInt() / 2f
            }
        }

        /**
         * Estimates perceived loudness shift from active EQ/DSP so Hold-to-Compare (A/B)
         * matches original bypassed volume to processed output loudness cleanly.
         */
        fun computeLoudnessMatchGainDb(
            bandGainsDb: List<Float>,
            preampDb: Float,
            lateNightEnabled: Boolean,
            nightIntensity: NightIntensity,
            target: OutputTarget,
            speakerDspEnabled: Boolean,
            spatialMode: SpatialMode,
            audioMixMode: AudioMixMode = AudioMixMode.STANDARD
        ): Float {
            // Mid/speech-weighted average of user EQ bands (125Hz..8kHz carry highest loudness weight)
            val weights = floatArrayOf(0.05f, 0.08f, 0.10f, 0.12f, 0.14f, 0.16f, 0.14f, 0.10f, 0.07f, 0.04f)
            var weightedEq = 0f
            for (i in 0 until 10) {
                weightedEq += bandGainsDb.getOrElse(i) { 0f } * weights[i]
            }
            val lateNightBoost = if (lateNightEnabled) nightIntensity.makeupGainDb * 0.6f else 0f
            val speakerBoost = if (target == OutputTarget.SPEAKER && speakerDspEnabled) 2.5f else 0f
            val spatialBoost = if (spatialMode != SpatialMode.OFF) -0.8f else 0f
            val audioMixShift = when (audioMixMode) {
                AudioMixMode.STANDARD -> 0f
                AudioMixMode.IN_FRAME -> 0.4f
                AudioMixMode.STUDIO -> 0.6f
                AudioMixMode.CINEMATIC -> 0.8f
            }
            return (preampDb + weightedEq + lateNightBoost + speakerBoost + spatialBoost + audioMixShift)
                .coerceIn(-12f, 12f)
        }
    }
}
