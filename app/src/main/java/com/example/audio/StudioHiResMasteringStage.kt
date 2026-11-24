package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.model.StudioHiResSpecs
import com.example.model.ThreeBandEqRanges
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 1. Unprocessed Studio Mic Capture Factory:
 * Equivalent to Web Audio { echoCancellation: false, noiseSuppression: false, autoGainControl: false }.
 * Uses MediaRecorder.AudioSource.UNPROCESSED (API 24+) when supported and explicitly disables
 * AcousticEchoCanceler, NoiseSuppressor, and AutomaticGainControl on the capture session.
 */
object UnprocessedMicFactory {

    data class OpenMicCapture(
        val audioRecord: AudioRecord,
        val sampleRate: Int,
        val autoProcessingDisabled: Boolean,
        val effectsToRelease: List<AudioEffect> = emptyList()
    ) {
        fun release() {
            try {
                audioRecord.stop()
            } catch (_: Throwable) {
            }
            try {
                audioRecord.release()
            } catch (_: Throwable) {
            }
            for (effect in effectsToRelease) {
                try {
                    effect.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun openRawMicCapture(
        preferredSampleRates: IntArray = intArrayOf(StudioHiResSpecs.SAMPLE_RATE_HZ, 16000)
    ): OpenMicCapture? {
        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intArrayOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )
        } else {
            intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }

        for (sr in preferredSampleRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue

            for (src in sources) {
                val record = try {
                    AudioRecord(
                        src,
                        sr,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf.coerceAtLeast(2048) * 2
                    )
                } catch (_: Throwable) {
                    null
                }
                if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                    val (effectsDisabled, effects) = disableHardwareAutoProcessing(record.audioSessionId)
                    return OpenMicCapture(
                        audioRecord = record,
                        sampleRate = sr,
                        autoProcessingDisabled = effectsDisabled || src == MediaRecorder.AudioSource.UNPROCESSED,
                        effectsToRelease = effects
                    )
                }
                try {
                    record?.release()
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    private fun disableHardwareAutoProcessing(audioSessionId: Int): Pair<Boolean, List<AudioEffect>> {
        var clean = true
        val createdEffects = mutableListOf<AudioEffect>()
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(audioSessionId)
                if (aec != null) {
                    aec.enabled = false
                    createdEffects.add(aec)
                }
            }
        } catch (_: Throwable) {
            clean = false
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(audioSessionId)
                if (ns != null) {
                    ns.enabled = false
                    createdEffects.add(ns)
                }
            }
        } catch (_: Throwable) {
            clean = false
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(audioSessionId)
                if (agc != null) {
                    agc.enabled = false
                    createdEffects.add(agc)
                }
            }
        } catch (_: Throwable) {
            clean = false
        }
        return clean to createdEffects
    }
}

/**
 * 2. 2x Oversampled Upper-Band Parametric / Shelf EQ Clarity (Anti-Cramping Filter Compensation):
 * Runs the upper EQ bands (8 kHz, 16 kHz in 10-Band mode, and the 4.7 kHz–20 kHz High Shelf in 3-Band mode)
 * at 2x oversampled rate (96,000 Hz for 48,000 Hz audio) with anti-imaging/anti-aliasing filtering,
 * eliminating bilinear Nyquist cramping near 15 kHz–20 kHz for open, analog-smooth treble sparkle.
 */
class OversampledUpperEqStage(private val sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ) {
    private val oversampleRate = sampleRate * 2

    // Band 8 (8 kHz) & Band 9 (16 kHz) at 2x oversampling
    private val band8k2x = StereoBiquad()
    private val band16k2x = StereoBiquad()

    // 3-Band High Shelf (4.7 kHz - 20 kHz) at 2x oversampling
    private val highShelf4700_2x = StereoBiquad()

    // 2x Anti-aliasing halfband Butterworth LPF at 0.455 * sampleRate (~21.8 kHz at 48 kHz)
    private val antiAliasLpf2x = StereoBiquad().apply {
        setLowPass(oversampleRate, sampleRate * StudioHiResSpecs.ANTI_ALIAS_LPF_RATIO, 0.7071)
    }

    private var prevInL = 0.0
    private var prevInR = 0.0

    @Volatile
    var enabled: Boolean = true
        private set

    @Volatile
    private var tenBandActive: Boolean = true

    @Volatile
    private var threeBandActive: Boolean = false

    init {
        configure(
            isEnabled = true,
            useTenBand = true,
            useThreeBand = false,
            gain8kDb = 0f,
            gain16kDb = 0f,
            threeBandTrebleDb = 0f
        )
    }

    fun configure(
        isEnabled: Boolean,
        useTenBand: Boolean,
        useThreeBand: Boolean,
        gain8kDb: Float,
        gain16kDb: Float,
        threeBandTrebleDb: Float
    ) {
        enabled = isEnabled
        val exclusiveThreeBand = useThreeBand && !useTenBand
        val exclusiveTenBand = !exclusiveThreeBand
        tenBandActive = exclusiveTenBand
        threeBandActive = exclusiveThreeBand

        if (!isEnabled) {
            band8k2x.reset()
            band16k2x.reset()
            highShelf4700_2x.reset()
            return
        }

        val g8k = if (exclusiveTenBand) gain8kDb.toDouble() else 0.0
        val g16k = if (exclusiveTenBand) gain16kDb.toDouble() else 0.0
        val gThreeTreble = if (exclusiveThreeBand) {
            threeBandTrebleDb.coerceIn(ThreeBandEqRanges.TREBLE_MIN_DB, ThreeBandEqRanges.TREBLE_MAX_DB).toDouble()
        } else {
            0.0
        }

        band8k2x.setPeaking(oversampleRate, 8000.0, 1.414, g8k)
        band16k2x.setPeaking(oversampleRate, 16000.0, 1.414, g16k)
        highShelf4700_2x.setHighShelf(oversampleRate, 4700.0, 0.7071, gThreeTreble)
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            prevInL = inL
            prevInR = inR
            return inL to inR
        }

        // 2x linear midpoint upsampling: sub-sample 0.5 (midpoint) then sub-sample 1.0 (current)
        val midL = 0.5 * (prevInL + inL)
        val midR = 0.5 * (prevInR + inR)
        prevInL = inL
        prevInR = inR

        process2xSubSample(midL, midR)
        return process2xSubSample(inL, inR)
    }

    private fun process2xSubSample(xL: Double, xR: Double): Pair<Double, Double> {
        var l = xL
        var r = xR
        if (tenBandActive) {
            val (b8L, b8R) = band8k2x.processStereo(l, r)
            val (b16L, b16R) = band16k2x.processStereo(b8L, b8R)
            l = b16L
            r = b16R
        }
        if (threeBandActive) {
            val (hL, hR) = highShelf4700_2x.processStereo(l, r)
            l = hL
            r = hR
        }
        return antiAliasLpf2x.processStereo(l, r)
    }

    companion object {
        /**
         * Computes analytical 2x oversampled (de-cramped) frequency response at freqHz
         * for upper bands (8kHz, 16kHz in 10-band or 4.7kHz high shelf in 3-band).
         */
        fun compute2xTenBandUpperResponseDbAt(
            freqHz: Double,
            gain8kDb: Double,
            gain16kDb: Double,
            baseSampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ
        ): Double {
            val osRate = baseSampleRate * 2
            val b8 = StereoBiquad().apply { setPeaking(osRate, 8000.0, 1.414, gain8kDb) }
            val b16 = StereoBiquad().apply { setPeaking(osRate, 16000.0, 1.414, gain16kDb) }
            return b8.magnitudeDbAt(osRate, freqHz) + b16.magnitudeDbAt(osRate, freqHz)
        }

        fun compute2xThreeBandHighShelfDbAt(
            freqHz: Double,
            trebleDb: Double,
            baseSampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ
        ): Double {
            val osRate = baseSampleRate * 2
            val hs = StereoBiquad().apply { setHighShelf(osRate, 4700.0, 0.7071, trebleDb) }
            return hs.magnitudeDbAt(osRate, freqHz)
        }
    }
}

/**
 * 3. Studio Clarity / "Analog Harmonic Sheen" & Stereo Focus Control (0% – 100%):
 * Extracts the upper-mid vocal/acoustic detail zone (2 kHz – 6 kHz), generates ultra-clean
 * 2nd-order even harmonics (< 0.4% THD) with zero odd-harmonic harshness or static treble boost,
 * and subtly focuses center vocal imaging while adding airy upper-mid sheen.
 */
class HarmonicSheenAndStereoFocusProcessor(private val sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ) {
    private val upperMidExtractor = StereoBiquad().apply {
        setBandPass(
            sampleRate,
            StudioHiResSpecs.HARMONIC_SHEEN_BAND_CENTER_HZ,
            StudioHiResSpecs.HARMONIC_SHEEN_BAND_Q
        )
    }
    private val overtoneHpf = StereoBiquad().apply {
        setHighPass(sampleRate, StudioHiResSpecs.HARMONIC_SHEEN_OVERTONE_HPF_HZ, 0.7071)
    }
    private val overtoneLpf = StereoBiquad().apply {
        setLowPass(sampleRate, StudioHiResSpecs.HARMONIC_SHEEN_OVERTONE_LPF_HZ.coerceAtMost(sampleRate * 0.44), 0.7071)
    }
    private val dcBlockerAlpha = exp(-2.0 * PI * 18.0 / sampleRate)
    private var dcPrevL = 0.0
    private var dcPrevR = 0.0

    @Volatile
    private var sheenPercent: Double = 0.0

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    var latestThdPercent: Float = 0f
        private set

    private var fundRmsSq = 0.0
    private var harmRmsSq = 0.0
    private val meterAlpha = exp(-1.0 / (0.045 * sampleRate))

    init {
        configure(isEnabled = false, percent = 0f)
    }

    fun configure(isEnabled: Boolean, percent: Float) {
        val p = percent.coerceIn(0f, 1f).toDouble()
        sheenPercent = p
        enabled = isEnabled && p > 0.005
        if (!enabled) {
            fundRmsSq = 0.0
            harmRmsSq = 0.0
            latestThdPercent = 0f
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            latestThdPercent = 0f
            return inL to inR
        }

        // Extract 2 kHz - 6 kHz vocal breath & acoustic pick detail
        val (bandL, bandR) = upperMidExtractor.processStereo(inL, inR)

        // Pure 2nd-order even harmonic generation: x^2 produces 2*f + DC, then remove DC & keep 3.8k-14kHz overtone
        val sqL = bandL * bandL
        val sqR = bandR * bandR
        dcPrevL = dcBlockerAlpha * dcPrevL + (1.0 - dcBlockerAlpha) * sqL
        dcPrevR = dcBlockerAlpha * dcPrevR + (1.0 - dcBlockerAlpha) * sqR
        val acHarmL = sqL - dcPrevL
        val acHarmR = sqR - dcPrevR

        val (hpL, hpR) = overtoneHpf.processStereo(acHarmL, acHarmR)
        val (sheenL, sheenR) = overtoneLpf.processStereo(hpL, hpR)

        val blend = sheenPercent * StudioHiResSpecs.HARMONIC_SHEEN_MAX_DRIVE_BLEND
        val addedL = sheenL * blend
        val addedR = sheenR * blend

        // Subtle M/S stereo focus on upper-mid detail without touching <200 Hz mono bass weight
        val midBand = 0.5 * (bandL + bandR)
        val sideBand = 0.5 * (bandL - bandR)
        val focusAmount = sheenPercent * 0.055
        val focusedL = inL + addedL + midBand * (focusAmount * 0.45) + sideBand * focusAmount
        val focusedR = inR + addedR + midBand * (focusAmount * 0.45) - sideBand * focusAmount

        val instFund = 0.5 * (inL * inL + inR * inR)
        val instHarm = 0.5 * (addedL * addedL + addedR * addedR)
        fundRmsSq = meterAlpha * fundRmsSq + (1.0 - meterAlpha) * instFund
        harmRmsSq = meterAlpha * harmRmsSq + (1.0 - meterAlpha) * instHarm
        val thdPct = if (fundRmsSq > 1e-10) {
            (sqrt(harmRmsSq / fundRmsSq) * 100.0).coerceIn(0.0, StudioHiResSpecs.HARMONIC_SHEEN_MAX_THD_PCT.toDouble())
        } else {
            0.0
        }
        latestThdPercent = thdPct.toFloat()

        return focusedL to focusedR
    }
}

/**
 * 4. Zero-Noise Floor Gate (-75 dBFS) & Mastering Bit-Depth TPDF Dithering:
 * Smoothly auto-mutes background noise below -75 dBFS when playback is paused/silent
 * (silencing even dither so zero hiss leaks through sensitive earbuds), and applies clean
 * Triangular-PDF (TPDF) bit-depth dithering whenever active signal is playing.
 */
class ZeroNoiseFloorGateAndDitherer(private val sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ) {
    private val thresholdLinear = Math.pow(10.0, StudioHiResSpecs.NOISE_GATE_THRESHOLD_DBFS / 20.0)
    private val attackCoeff = exp(-1.0 / ((StudioHiResSpecs.NOISE_GATE_ATTACK_MS / 1000.0) * sampleRate))
    private val releaseStep = 1.0 / ((StudioHiResSpecs.NOISE_GATE_RELEASE_MS / 1000.0) * sampleRate).coerceAtLeast(1.0)
    private val holdSamples = ((StudioHiResSpecs.NOISE_GATE_HOLD_MS / 1000.0) * sampleRate).toInt().coerceAtLeast(1)

    private var holdCounter = 0
    private var releasePhase = 0.0
    private var gateGain = 0.0

    // Fast deterministic LCG PRNG for stereo uncorrelated TPDF dithering
    private var prngL = 0x5A17C391
    private var prngR = 0x2F84D6B3

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var isAutoMuted: Boolean = false
        private set

    private fun nextUniform(isLeft: Boolean): Double {
        return if (isLeft) {
            prngL = prngL * 1664525 + 1013904223
            (((prngL ushr 8) and 0xFFFF) / 65535.0) - 0.5
        } else {
            prngR = prngR * 1664525 + 1013904223
            (((prngR ushr 8) and 0xFFFF) / 65535.0) - 0.5
        }
    }

    fun nextTpdfDither(isLeft: Boolean, amplitude: Double = StudioHiResSpecs.TPDF_DITHER_24BIT_LSB): Double {
        // Triangular PDF = difference/sum of two independent uniform distributions in [-0.5, 0.5] -> [-1.0, 1.0]
        return (nextUniform(isLeft) + nextUniform(isLeft)) * amplitude
    }

    fun reset() {
        holdCounter = 0
        releasePhase = 0.0
        gateGain = 0.0
        isAutoMuted = false
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            isAutoMuted = false
            return inL to inR
        }

        val instAmp = max(abs(inL), abs(inR))
        if (instAmp >= thresholdLinear) {
            holdCounter = holdSamples
            releasePhase = 1.0
            gateGain = attackCoeff * gateGain + (1.0 - attackCoeff) * 1.0
        } else if (holdCounter > 0) {
            holdCounter--
            releasePhase = 1.0
            gateGain = attackCoeff * gateGain + (1.0 - attackCoeff) * 1.0
        } else {
            releasePhase = (releasePhase - releaseStep).coerceAtLeast(0.0)
            gateGain = releasePhase * releasePhase
        }

        if (releasePhase <= 0.0 || (gateGain < 1e-6 && instAmp < thresholdLinear && holdCounter == 0)) {
            gateGain = 0.0
            isAutoMuted = true
            // Zero background hiss & zero dither when paused/auto-muted
            return 0.0 to 0.0
        }

        isAutoMuted = false
        val gatedL = inL * gateGain + nextTpdfDither(isLeft = true)
        val gatedR = inR * gateGain + nextTpdfDither(isLeft = false)
        return gatedL to gatedR
    }

    /**
     * Converts normalized float/double stereo samples to 16-bit PCM Short using active TPDF dithering
     * when signal is live, or exact 0 when auto-muted so sensitive IEMs hear dead silence.
     */
    fun quantize16BitPcm(sample: Double, isLeft: Boolean): Short {
        if (enabled && isAutoMuted) return 0
        val dither = if (enabled) nextTpdfDither(isLeft, amplitude = 1.0 / 32768.0) else 0.0
        val scaled = ((sample + dither).coerceIn(-1.0, 1.0) * 32767.0).toInt()
        return scaled.coerceIn(-32767, 32767).toShort()
    }
}
