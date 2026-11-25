package com.example.audio

import com.example.model.AdaptiveMultibandPhaseSpecs
import com.example.model.OutputTarget

/**
 * iPhone 18 Pro Max Final Mastering Stage:
 * 1. 3-Band Dynamic Tone Balancer (Real-Time Content-Aware DSP):
 *    - Phase-coherent Linkwitz-Riley 4th-order (LR4 = cascaded Q=0.7071 Butterworth pairs) crossovers:
 *      Low (<220Hz), Mid (220Hz–3.8kHz), High (>3.8kHz), with a 3.8kHz Allpass on the Low band
 *      so Low + Mid + High sum to unity magnitude with zero crossover phase cancellation.
 *    - Independent per-band soft-knee dynamic compression (Ratio 2.2:1, Attack 6ms, Release 85ms, 0dB makeup)
 *      so heavy bass never ducks vocal clarity and loud snare/treble hits never cause mid-vocal pumping,
 *      while strictly respecting existing 4.5kHz/7.0kHz de-harsh cuts and the +3.0 dB treble cap.
 */
class AdaptiveMultibandToneBalancer(private val sampleRate: Int = 44100) {

    // LR4 Split 1 @ 220 Hz
    private val lowLp1 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val lowLp2 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val lowHp1 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val lowHp2 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.LOW_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }

    // Allpass phase-matching filter at 3.8 kHz on Low band for LR4 3-way phase-coherent sum
    private val lowPhaseMatch3800 = StereoBiquad().apply {
        setAllPass(sampleRate, AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }

    // LR4 Split 2 @ 3.8 kHz (splits >220Hz into Mid 220Hz-3.8kHz and High >3.8kHz)
    private val highLp1 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val highLp2 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val highHp1 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val highHp2 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.HIGH_CROSSOVER_HZ, StereoBiquad.BUTTERWORTH_Q)
    }

    // Independent per-band compressors: Ratio 2.2:1, Attack 6ms, Release 85ms, 0dB makeup
    private val lowCompressor = DynamicRangeCompressor(sampleRate)
    private val midCompressor = DynamicRangeCompressor(sampleRate)
    private val highCompressor = DynamicRangeCompressor(sampleRate)

    @Volatile
    var enabled: Boolean = true
        private set

    val latestLowGrDb: Float
        get() = if (enabled) lowCompressor.latestGainReductionDb else 0f

    val latestMidGrDb: Float
        get() = if (enabled) midCompressor.latestGainReductionDb else 0f

    val latestHighGrDb: Float
        get() = if (enabled) highCompressor.latestGainReductionDb else 0f

    init {
        configure(isEnabled = true)
    }

    fun configure(isEnabled: Boolean) {
        enabled = isEnabled
        lowCompressor.configureCustom(
            isEnabled = isEnabled,
            threshold = AdaptiveMultibandPhaseSpecs.LOW_THRESHOLD_DB,
            knee = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
            compRatio = AdaptiveMultibandPhaseSpecs.BAND_RATIO,
            attackMs = AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS,
            releaseMs = AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS,
            makeupGainDb = 0f
        )
        midCompressor.configureCustom(
            isEnabled = isEnabled,
            threshold = AdaptiveMultibandPhaseSpecs.MID_THRESHOLD_DB,
            knee = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
            compRatio = AdaptiveMultibandPhaseSpecs.BAND_RATIO,
            attackMs = AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS,
            releaseMs = AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS,
            makeupGainDb = 0f
        )
        highCompressor.configureCustom(
            isEnabled = isEnabled,
            threshold = AdaptiveMultibandPhaseSpecs.HIGH_THRESHOLD_DB,
            knee = AdaptiveMultibandPhaseSpecs.BAND_KNEE_DB,
            compRatio = AdaptiveMultibandPhaseSpecs.BAND_RATIO,
            attackMs = AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS,
            releaseMs = AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS,
            makeupGainDb = 0f
        )
    }

    fun splitBandsStereo(inL: Double, inR: Double): Triple<Pair<Double, Double>, Pair<Double, Double>, Pair<Double, Double>> {
        val (l1L, l1R) = lowLp1.processStereo(inL, inR)
        val (l2L, l2R) = lowLp2.processStereo(l1L, l1R)
        val (lowL, lowR) = lowPhaseMatch3800.processStereo(l2L, l2R)

        val (h1L, h1R) = lowHp1.processStereo(inL, inR)
        val (above220L, above220R) = lowHp2.processStereo(h1L, h1R)

        val (m1L, m1R) = highLp1.processStereo(above220L, above220R)
        val (midL, midR) = highLp2.processStereo(m1L, m1R)

        val (hi1L, hi1R) = highHp1.processStereo(above220L, above220R)
        val (highL, highR) = highHp2.processStereo(hi1L, hi1R)

        return Triple(lowL to lowR, midL to midR, highL to highR)
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) return inL to inR

        val (lowBand, midBand, highBand) = splitBandsStereo(inL, inR)
        val (cLowL, cLowR) = lowCompressor.processStereoSample(lowBand.first, lowBand.second)
        val (cMidL, cMidR) = midCompressor.processStereoSample(midBand.first, midBand.second)
        val (cHighL, cHighR) = highCompressor.processStereoSample(highBand.first, highBand.second)

        return (cLowL + cMidL + cHighL) to (cLowR + cMidR + cHighR)
    }
}

/**
 * 2. Hybrid Earpiece-to-Bottom-Driver Phase & Arrival Alignment:
 * - Biquad Allpass tuned to 1.45 kHz (Q = 0.7071) active in Phone Speaker Mode.
 * - 2.2 kHz LR4 crossover + 0.14 ms fractional micro-arrival offset on the upper treble (> 2.2 kHz)
 *   simulating physical spacing between bottom main driver and top earpiece tweeter to anchor vocals dead-center.
 */
class SpeakerPhaseAlignmentFilter(private val sampleRate: Int = 44100) {

    private val allpassFilter = StereoBiquad().apply {
        setAllPass(
            sampleRate = sampleRate,
            freqHz = AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ,
            q = AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_Q
        )
    }

    // 2.2 kHz LR4 split for upper-treble (>2.2 kHz) 0.14 ms micro-arrival offset
    private val trebleSplitLp1 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val trebleSplitLp2 = StereoBiquad().apply {
        setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val trebleSplitHp1 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val trebleSplitHp2 = StereoBiquad().apply {
        setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
    }

    private val microDelaySamples: Double =
        AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_ARRIVAL_OFFSET_SEC * sampleRate
    private val delayBufSize = 64
    private val delayMask = delayBufSize - 1
    private val delayRingL = DoubleArray(delayBufSize)
    private val delayRingR = DoubleArray(delayBufSize)
    private var delayWriteIdx = 0

    @Volatile
    var isActive: Boolean = false
        private set

    private fun readDelayed(ring: DoubleArray): Double {
        val intD = microDelaySamples.toInt()
        val frac = microDelaySamples - intD
        val idx0 = (delayWriteIdx - intD) and delayMask
        val idx1 = (delayWriteIdx - intD - 1) and delayMask
        return ring[idx0] * (1.0 - frac) + ring[idx1] * frac
    }

    fun configure(
        isEnabled: Boolean,
        outputTarget: OutputTarget,
        speakerDspEnabled: Boolean
    ) {
        val active = isEnabled && outputTarget == OutputTarget.SPEAKER && speakerDspEnabled
        if (active && !isActive) {
            allpassFilter.setAllPass(
                sampleRate = sampleRate,
                freqHz = AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ,
                q = AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_Q
            )
            trebleSplitLp1.setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
            trebleSplitLp2.setLowPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
            trebleSplitHp1.setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
            trebleSplitHp2.setHighPass(sampleRate, AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_OFFSET_CUTOFF_HZ, StereoBiquad.BUTTERWORTH_Q)
            delayRingL.fill(0.0)
            delayRingR.fill(0.0)
            delayWriteIdx = 0
        }
        isActive = active
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!isActive) return inL to inR

        val (apL, apR) = allpassFilter.processStereo(inL, inR)

        // Split <2.2kHz and >2.2kHz via LR4; delay >2.2kHz by 0.14ms (~111-177 deg across 2.2-3.5kHz).
        // Subtracting the delayed high band aligns polarity across the 2.2-3.8kHz vocal zone with zero dip.
        val (lp1L, lp1R) = trebleSplitLp1.processStereo(apL, apR)
        val (lowL, lowR) = trebleSplitLp2.processStereo(lp1L, lp1R)
        val (hp1L, hp1R) = trebleSplitHp1.processStereo(apL, apR)
        val (highL, highR) = trebleSplitHp2.processStereo(hp1L, hp1R)

        delayRingL[delayWriteIdx] = highL
        delayRingR[delayWriteIdx] = highR
        val delayedHighL = readDelayed(delayRingL)
        val delayedHighR = readDelayed(delayRingR)
        delayWriteIdx = (delayWriteIdx + 1) and delayMask

        return (lowL - delayedHighL) to (lowR - delayedHighR)
    }
}
