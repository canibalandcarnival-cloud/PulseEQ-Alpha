package com.example.audio

import com.example.model.LoudnessMaximizerSpecs
import com.example.model.OutputTarget
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * "iPhone Loudness Maximizer (Safe Volume Boost)" placed at the very end of the DSP chain:
 *
 * 1. Safe Sub-Bass Filter (115 Hz Steep 24 dB/octave Butterworth High-Pass):
 *    Automatically strips inaudible sub-bass below 115 Hz when Phone Speaker mode is active
 *    to preserve amplifier headroom and eliminate speaker rattle/distortion.
 * 2. Presence & Fletcher-Munson Loudness Curve:
 *    - 350 Hz: -2.5 dB (muddy boxiness removal)
 *    - 1.5 kHz: +2.0 dB
 *    - 3.0 kHz: +3.5 dB
 *    - 8.0 kHz: +2.5 dB
 * 3. Two-Stage iPhone Limiter & RMS Maximizer:
 *    - Smooth RMS Compressor (Threshold: -20 dB, Ratio: 4:1, Knee: 15 dB, Attack: 5ms, Release: 150ms)
 *      + Clean Makeup Gain (0%..100% -> 0..+9.0 dB)
 *    - Zero-Clipping Brickwall Limiter (Threshold: -0.3 dB, Ratio: 20:1, Attack: 0.5ms, Release: 40ms)
 */
class IphoneLoudnessMaximizerChain(private val sampleRate: Int = 44100) {

    // Stage 1: Cascaded 4th-order Butterworth HPF @ 115 Hz (24 dB/oct) for Phone Speaker mode
    private val hpfStage1 = StereoBiquad().apply {
        setHighPass(sampleRate, LoudnessMaximizerSpecs.SUB_BASS_HPF_HZ, 0.5411961)
    }
    private val hpfStage2 = StereoBiquad().apply {
        setHighPass(sampleRate, LoudnessMaximizerSpecs.SUB_BASS_HPF_HZ, 1.3065630)
    }

    // Stage 2: Presence & Fletcher-Munson Loudness Curve
    private val mudCut350Hz = StereoBiquad().apply { setPeaking(sampleRate, 350.0, 1.8, -2.5) }
    private val presence1500Hz = StereoBiquad().apply { setPeaking(sampleRate, 1500.0, 2.0, 2.0) }
    private val loudness3000Hz = StereoBiquad().apply { setPeaking(sampleRate, 3000.0, 2.0, 3.5) }
    private val air8000Hz = StereoBiquad().apply { setPeaking(sampleRate, 8000.0, 2.0, 2.5) }

    // Stage 3a: Smooth RMS Compressor (-20 dB, 4:1, 15 dB knee, 5ms attack, 150ms release)
    private val rmsWindowAlpha = 1.0 - exp(-1.0 / (0.018 * sampleRate)) // 18ms smooth RMS detector window
    private val rmsAttackCoeff = exp(-1.0 / ((LoudnessMaximizerSpecs.RMS_ATTACK_MS / 1000.0) * sampleRate))
    private val rmsReleaseCoeff = exp(-1.0 / ((LoudnessMaximizerSpecs.RMS_RELEASE_MS / 1000.0) * sampleRate))
    private var rmsMeanSquare = 1e-8
    private var rmsEnvelopeDb = -60.0

    // Stage 3b: Zero-Clipping Brickwall Limiter (-0.3 dB, 20:1, 0.5ms attack, 40ms release)
    private val brickwallAttackCoeff = exp(-1.0 / ((LoudnessMaximizerSpecs.BRICKWALL_ATTACK_MS / 1000.0) * sampleRate))
    private val brickwallReleaseCoeff = exp(-1.0 / ((LoudnessMaximizerSpecs.BRICKWALL_RELEASE_MS / 1000.0) * sampleRate))
    private var brickwallEnvelopeDb = -60.0
    private val brickwallCeilingLinear = 10.0.pow(-0.15 / 20.0) // ~0.983 zero-clipping safety cap

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var speakerSubHpfActive: Boolean = false
        private set

    @Volatile
    var volumeBoostPercent: Float = 0.50f
        private set

    @Volatile
    private var makeupGainLinear: Double = 10.0.pow((0.50 * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB) / 20.0)

    @Volatile
    var latestRmsGrDb: Float = 0f
        private set

    @Volatile
    var latestBrickwallGrDb: Float = 0f
        private set

    @Volatile
    var isAntiClipActive: Boolean = false
        private set

    fun configure(
        isEnabled: Boolean,
        outputTarget: OutputTarget,
        boostPercent: Float
    ) {
        enabled = isEnabled
        speakerSubHpfActive = isEnabled && (outputTarget == OutputTarget.SPEAKER)
        val clampedBoost = boostPercent.coerceIn(0f, 1f)
        volumeBoostPercent = clampedBoost
        val makeupDb = clampedBoost * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB
        makeupGainLinear = 10.0.pow(makeupDb.toDouble() / 20.0)
        if (!isEnabled) {
            latestRmsGrDb = 0f
            latestBrickwallGrDb = 0f
            isAntiClipActive = false
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            latestRmsGrDb = 0f
            latestBrickwallGrDb = 0f
            isAntiClipActive = false
            return inL to inR
        }

        // Stage 1: Automatic 115 Hz 24 dB/oct Steep HPF when Phone Speaker mode is active
        var l = inL
        var r = inR
        if (speakerSubHpfActive) {
            val (hp1L, hp1R) = hpfStage1.processStereo(l, r)
            val (hp2L, hp2R) = hpfStage2.processStereo(hp1L, hp1R)
            l = hp2L
            r = hp2R
        }

        // Stage 2: Presence & Fletcher-Munson Loudness Curve
        val (mL, mR) = mudCut350Hz.processStereo(l, r)
        val (p1L, p1R) = presence1500Hz.processStereo(mL, mR)
        val (p2L, p2R) = loudness3000Hz.processStereo(p1L, p1R)
        val (aL, aR) = air8000Hz.processStereo(p2L, p2R)

        // Stage 3a: Smooth RMS Compressor (-20 dB, 4:1, 15 dB knee, 5ms / 150ms) + Boost Makeup (0..+9 dB)
        val instPower = 0.5 * (aL * aL + aR * aR)
        rmsMeanSquare += rmsWindowAlpha * (instPower - rmsMeanSquare)
        val rmsInputDb = 10.0 * log10(rmsMeanSquare.coerceAtLeast(1e-12))
        val rmsCoeff = if (rmsInputDb > rmsEnvelopeDb) rmsAttackCoeff else rmsReleaseCoeff
        rmsEnvelopeDb = rmsCoeff * rmsEnvelopeDb + (1.0 - rmsCoeff) * rmsInputDb

        val rmsGrDb = DynamicRangeCompressor.computeGainReductionDb(
            inputDb = rmsEnvelopeDb.toFloat(),
            thresholdDb = LoudnessMaximizerSpecs.RMS_THRESHOLD_DB,
            kneeDb = LoudnessMaximizerSpecs.RMS_KNEE_DB,
            ratio = LoudnessMaximizerSpecs.RMS_RATIO
        )
        latestRmsGrDb = rmsGrDb

        val stage3aGain = 10.0.pow(rmsGrDb / 20.0) * makeupGainLinear
        val boostedL = aL * stage3aGain
        val boostedR = aR * stage3aGain

        // Stage 3b: Zero-Clipping Brickwall Limiter (-0.3 dB, 20:1, 0.5ms / 40ms)
        val peakAmp = max(abs(boostedL), abs(boostedR)).coerceAtLeast(1e-6)
        val peakDb = 20.0 * log10(peakAmp)
        val bwCoeff = if (peakDb > brickwallEnvelopeDb) brickwallAttackCoeff else brickwallReleaseCoeff
        brickwallEnvelopeDb = bwCoeff * brickwallEnvelopeDb + (1.0 - bwCoeff) * peakDb

        val bwGrDb = DynamicRangeCompressor.computeGainReductionDb(
            inputDb = brickwallEnvelopeDb.toFloat(),
            thresholdDb = LoudnessMaximizerSpecs.BRICKWALL_THRESHOLD_DB,
            kneeDb = LoudnessMaximizerSpecs.BRICKWALL_KNEE_DB,
            ratio = LoudnessMaximizerSpecs.BRICKWALL_RATIO
        )
        latestBrickwallGrDb = bwGrDb
        isAntiClipActive = (bwGrDb < -0.15f) || (peakAmp > brickwallCeilingLinear)

        val bwGain = 10.0.pow(bwGrDb / 20.0)
        val outL = (boostedL * bwGain).coerceIn(-brickwallCeilingLinear, brickwallCeilingLinear)
        val outR = (boostedR * bwGain).coerceIn(-brickwallCeilingLinear, brickwallCeilingLinear)
        return outL to outR
    }

    companion object {
        /**
         * Analytical dB magnitude response of the Loudness Maximizer EQ stages
         * (115 Hz 24dB/oct HPF on Phone Speaker + Fletcher-Munson Presence curve).
         */
        fun computePresenceAndHpfResponseDbAt(
            freqHz: Double,
            target: OutputTarget,
            sampleRate: Int = 44100
        ): Float {
            var db = 0.0
            if (target == OutputTarget.SPEAKER) {
                val h1 = StereoBiquad().apply { setHighPass(sampleRate, LoudnessMaximizerSpecs.SUB_BASS_HPF_HZ, 0.5411961) }
                val h2 = StereoBiquad().apply { setHighPass(sampleRate, LoudnessMaximizerSpecs.SUB_BASS_HPF_HZ, 1.3065630) }
                db += (h1.magnitudeDbAt(sampleRate, freqHz) + h2.magnitudeDbAt(sampleRate, freqHz))
                    .coerceAtLeast(-15.0)
            }
            val mud = StereoBiquad().apply { setPeaking(sampleRate, 350.0, 1.8, -2.5) }
            val p1500 = StereoBiquad().apply { setPeaking(sampleRate, 1500.0, 2.0, 2.0) }
            val p3000 = StereoBiquad().apply { setPeaking(sampleRate, 3000.0, 2.0, 3.5) }
            val p8000 = StereoBiquad().apply { setPeaking(sampleRate, 8000.0, 2.0, 2.5) }

            db += mud.magnitudeDbAt(sampleRate, freqHz)
            db += p1500.magnitudeDbAt(sampleRate, freqHz)
            db += p3000.magnitudeDbAt(sampleRate, freqHz)
            db += p8000.magnitudeDbAt(sampleRate, freqHz)
            return db.toFloat()
        }
    }
}
