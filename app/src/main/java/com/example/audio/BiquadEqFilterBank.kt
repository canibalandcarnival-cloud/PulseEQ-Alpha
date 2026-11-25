package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.example.model.AccommodationToneProfile
import com.example.model.AudioMixMode
import com.example.model.BackgroundSoundPreset
import com.example.model.EqPresetsCatalog
import com.example.model.FlagshipSpeakerSpecs
import com.example.model.ListeningMode
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.PhoneOrientationMode
import com.example.model.ProMaxHardwareRefinementSpecs
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.SpeakerTuningProfile
import com.example.model.TestAudioSource
import com.example.model.ThreeBandEqRanges
import com.example.model.TrebleBudgetAllocation
import com.example.model.TrebleGainStagingBudget
import com.example.model.TrebleGainStagingSpecs
import com.example.model.VisualizerFrame
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Reusable single stereo biquad filter supporting Peaking EQ, High-Pass, and Band-Pass modes.
 */
class StereoBiquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun reset() {
        b0 = 1.0
        b1 = 0.0
        b2 = 0.0
        a1 = 0.0
        a2 = 0.0
        z1L = 0.0
        z2L = 0.0
        z1R = 0.0
        z2R = 0.0
    }

    fun setPeaking(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = 1.0 + alpha * a
        val nb1 = -2.0 * cosW0
        val nb2 = 1.0 - alpha * a
        val na0 = 1.0 + alpha / a
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha / a

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setHighPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = (1.0 + cosW0) * 0.5
        val nb1 = -(1.0 + cosW0)
        val nb2 = (1.0 + cosW0) * 0.5
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setBandPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = alpha
        val nb1 = 0.0
        val nb2 = -alpha
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setLowPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = (1.0 - cosW0) * 0.5
        val nb1 = 1.0 - cosW0
        val nb2 = (1.0 - cosW0) * 0.5
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setHighShelf(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val nb0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val nb2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val na0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val na2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setLowShelf(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val nb0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val nb2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val na0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val na2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setAllPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = 1.0 - alpha
        val nb1 = -2.0 * cosW0
        val nb2 = 1.0 + alpha
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun magnitudeDbAt(sampleRate: Int, freqHz: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2.0 * w)
        val sin2W = sin(2.0 * w)

        val numRe = b0 + b1 * cosW + b2 * cos2W
        val numIm = -(b1 * sinW + b2 * sin2W)
        val denRe = 1.0 + a1 * cosW + a2 * cos2W
        val denIm = -(a1 * sinW + a2 * sin2W)

        val magSq = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
        return 10.0 * log10(magSq.coerceAtLeast(1e-12))
    }

    fun phaseRadAt(sampleRate: Int, freqHz: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2.0 * w)
        val sin2W = sin(2.0 * w)

        val numRe = b0 + b1 * cosW + b2 * cos2W
        val numIm = -(b1 * sinW + b2 * sin2W)
        val denRe = 1.0 + a1 * cosW + a2 * cos2W
        val denIm = -(a1 * sinW + a2 * sin2W)

        val denSq = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
        val real = (numRe * denRe + numIm * denIm) / denSq
        val imag = (numIm * denRe - numRe * denIm) / denSq
        return kotlin.math.atan2(imag, real)
    }

    fun processStereo(inL: Double, inR: Double): Pair<Double, Double> {
        val outL = b0 * inL + z1L
        z1L = b1 * inL - a1 * outL + z2L
        z2L = b2 * inL - a2 * outL

        val outR = b0 * inR + z1R
        z1R = b1 * inR - a1 * outR + z2R
        z2R = b2 * inR - a2 * outR

        return outL to outR
    }
}

/**
 * Real-time soft-knee Dynamic Range Compressor / Smart Limiter.
 * Used both for Late Night Mode (20 dB knee, 4:1 ratio, 5ms/250ms) and
 * iPhone Speaker Smart Limiter (-6 dB threshold, 12:1 ratio, 1ms/80ms, +3 dB makeup).
 */
class DynamicRangeCompressor(private val sampleRate: Int = 44100) {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var thresholdDb: Float = NightIntensity.STANDARD.thresholdDb

    @Volatile
    private var kneeDb: Float = NightIntensity.STANDARD.kneeDb

    @Volatile
    private var ratio: Float = NightIntensity.STANDARD.ratio

    @Volatile
    private var makeupGainLinear: Double = 10.0.pow(NightIntensity.STANDARD.makeupGainDb / 20.0)

    private var attackCoeff: Double = exp(-1.0 / (0.005 * sampleRate))
    private var releaseCoeff: Double = exp(-1.0 / (0.250 * sampleRate))
    private var envelopeDb: Double = -60.0

    @Volatile
    var latestGainReductionDb: Float = 0f
        private set

    fun configure(isEnabled: Boolean, intensity: NightIntensity) {
        configureCustom(
            isEnabled = isEnabled,
            threshold = intensity.thresholdDb,
            knee = intensity.kneeDb,
            compRatio = intensity.ratio,
            attackMs = intensity.attackMs,
            releaseMs = intensity.releaseMs,
            makeupGainDb = intensity.makeupGainDb
        )
    }

    fun configureCustom(
        isEnabled: Boolean,
        threshold: Float,
        knee: Float,
        compRatio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupGainDb: Float
    ) {
        enabled = isEnabled
        thresholdDb = threshold
        kneeDb = knee
        ratio = compRatio
        makeupGainLinear = 10.0.pow(makeupGainDb / 20.0)
        attackCoeff = exp(-1.0 / ((attackMs.coerceAtLeast(0.1f) / 1000.0) * sampleRate))
        releaseCoeff = exp(-1.0 / ((releaseMs.coerceAtLeast(1f) / 1000.0) * sampleRate))
        if (!isEnabled) {
            latestGainReductionDb = 0f
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            latestGainReductionDb = 0f
            return inL to inR
        }

        val detectorAmp = max(abs(inL), abs(inR)).coerceAtLeast(1e-6)
        val inputDb = 20.0 * log10(detectorAmp)

        val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
        envelopeDb = coeff * envelopeDb + (1.0 - coeff) * inputDb

        val grDb = computeGainReductionDb(
            inputDb = envelopeDb.toFloat(),
            thresholdDb = thresholdDb,
            kneeDb = kneeDb,
            ratio = ratio
        )
        latestGainReductionDb = grDb

        val totalGainLinear = 10.0.pow(grDb / 20.0) * makeupGainLinear
        return (inL * totalGainLinear) to (inR * totalGainLinear)
    }

    companion object {
        /**
         * Soft-knee compression curve returning gain reduction in dB (<= 0f).
         */
        fun computeGainReductionDb(
            inputDb: Float,
            thresholdDb: Float,
            kneeDb: Float,
            ratio: Float
        ): Float {
            if (ratio <= 1f) return 0f
            val halfKnee = kneeDb * 0.5f
            val over = inputDb - thresholdDb
            val outputDb = when {
                kneeDb > 0f && over > -halfKnee && over < halfKnee -> {
                    val delta = inputDb - thresholdDb + halfKnee
                    inputDb + ((1f / ratio) - 1f) * (delta * delta) / (2f * kneeDb)
                }

                over >= halfKnee -> {
                    thresholdDb + over / ratio
                }

                else -> inputDb
            }
            return (outputDb - inputDb).coerceAtMost(0f)
        }
    }
}

/**
 * iPhone 18 Pro Max (iOS 27) Hybrid Stereo Speaker System DSP Chain:
 * 1. Apple Porous-Cavity Bass Extension & Resonant Damping:
 *    - Sub-Sonic Excursion Guard: 86Hz 24dB/oct Butterworth HPF (100Hz in 18 Pro Voice Focus)
 *    - Porous-Cavity Compliance Shelf: 128Hz (+3.2dB, Q=1.25) + 2nd-Order Harmonic Warmth (110Hz–220Hz)
 *    - Surgical Chassis Boxiness Scoop: 350Hz (-3.5dB, Q=1.5)
 *    - Apple Sibilance & Harshness Guard: 3.0kHz (+2.0dB), 4.5kHz (-3.0dB, Q=1.8), 7.0kHz (-3.5dB, Q=2.5), 12kHz (+1.0dB)
 * 2. Hybrid Earpiece + Bottom Speaker Crossover Simulation:
 *    - Keeps bass (<180Hz) tight & centered while applying Orientation-Aware 2nd-Order Transaural XTC (-22% @ 0.18ms + +4.84% @ 0.36ms)
 *      or Cinema Landscape 2nd-Order Transaural XTC (-26% @ 0.17ms + +12% @ 0.34ms, 4.5kHz head-shadow LPF).
 * 3. Apple Dynamic Excursion Protection (Smart Bass Limiter):
 *    - Dynamic low-end (<220Hz) limiter (Threshold -14dB, Ratio 6:1, Attack 3ms, Release 120ms)
 *      feeding into +4.5dB clean makeup gain and -0.3dB Brickwall Limiter
 */
class PhoneSpeakerDspChain(private val sampleRate: Int = 44100) {

    // Stage 1: Cascaded 4th-order Butterworth HPF (24 dB/oct)
    private val hpfStage1 = StereoBiquad()
    private val hpfStage2 = StereoBiquad()

    // Stage 2: 2nd-Order Harmonic Bass Exciter (110Hz–220Hz)
    private val subExtractor = StereoBiquad().apply { setBandPass(sampleRate, 70.0, 0.90) }
    private val harmonicBandPass = StereoBiquad().apply { setBandPass(sampleRate, 155.0, 1.15) }

    // Stage 3: 18 Pro Max Porous-Cavity & V-Curve EQ (or 18 Pro Voice Focus)
    private val chamberBassEq = StereoBiquad()
    private val bodyWarmth125HzEq = StereoBiquad()
    private val bodyWarmthExtractor125 = StereoBiquad().apply {
        setBandPass(sampleRate, ProMaxHardwareRefinementSpecs.BODY_WARMTH_HZ, ProMaxHardwareRefinementSpecs.BODY_WARMTH_Q)
    }
    private val bodyWarmthHarmonicOut250 = StereoBiquad().apply {
        setBandPass(sampleRate, ProMaxHardwareRefinementSpecs.BODY_WARMTH_HZ * 2.0, 1.15)
    }
    private val boxinessScoop350Hz = StereoBiquad()
    private val dialogueFocus1800Hz = StereoBiquad()
    private val portraitVocalCenter2000Hz = StereoBiquad()
    private val presence3000Hz = StereoBiquad()
    private val harshnessCut4500Hz = StereoBiquad()
    private val deEsser7000Hz = StereoBiquad()
    private val crispAirShelf12000Hz = StereoBiquad()

    // Stage 4: Hybrid Earpiece + Bottom Speaker Crossover (<180Hz 24dB/oct mono bass + Orientation/Cinema XTC)
    private val hybridSideHpf1 = StereoBiquad().apply {
        setHighPass(sampleRate, FlagshipSpeakerSpecs.HYBRID_CROSSOVER_BASS_HZ, 0.5411961)
    }
    private val hybridSideHpf2 = StereoBiquad().apply {
        setHighPass(sampleRate, FlagshipSpeakerSpecs.HYBRID_CROSSOVER_BASS_HZ, 1.3065630)
    }
    private val xtcHighPass180 = StereoBiquad().apply {
        setHighPass(sampleRate, FlagshipSpeakerSpecs.HYBRID_CROSSOVER_BASS_HZ, 0.7071)
    }
    private val cinemaHeadShadowLpf = StereoBiquad().apply {
        setLowPass(sampleRate, FlagshipSpeakerSpecs.CINEMA_XTC_HEAD_SHADOW_LPF_HZ, 0.7071)
    }
    private val xtcDelaySamples: Double = FlagshipSpeakerSpecs.XTC_DELAY_SEC * sampleRate
    private val xtcSecondaryDelaySamples: Double = FlagshipSpeakerSpecs.XTC_SECONDARY_DELAY_SEC * sampleRate
    private val cinemaPrimaryDelaySamples: Double = FlagshipSpeakerSpecs.CINEMA_XTC_PRIMARY_DELAY_SEC * sampleRate
    private val cinemaSecondaryDelaySamples: Double = FlagshipSpeakerSpecs.CINEMA_XTC_SECONDARY_DELAY_SEC * sampleRate
    private val xtcBufSize = 128
    private val xtcMask = xtcBufSize - 1
    private val xtcRingL = DoubleArray(xtcBufSize)
    private val xtcRingR = DoubleArray(xtcBufSize)
    private val cinemaRingL = DoubleArray(xtcBufSize)
    private val cinemaRingR = DoubleArray(xtcBufSize)
    private var xtcWriteIdx = 0

    // Stage 5a: Apple Dynamic Excursion Protection (fast dynamic low-end limiter < 220Hz)
    private val excursionLowPass = StereoBiquad().apply {
        setLowPass(sampleRate, FlagshipSpeakerSpecs.EXCURSION_LOW_BAND_HZ, 0.7071)
    }
    private val excursionAttackCoeff: Double = exp(-1.0 / ((FlagshipSpeakerSpecs.EXCURSION_ATTACK_MS / 1000.0) * sampleRate))
    private val excursionReleaseCoeff: Double = exp(-1.0 / ((FlagshipSpeakerSpecs.EXCURSION_RELEASE_MS / 1000.0) * sampleRate))
    private var excursionEnvDb: Double = -60.0

    @Volatile
    var latestExcursionReductionDb: Float = 0f
        private set

    // Stage 5b: -0.3 dB Zero-Clipping Brickwall Limiter preceded by +4.5 dB clean makeup gain
    private val makeupGainLinear: Double = 10.0.pow(FlagshipSpeakerSpecs.MAKEUP_GAIN_DB / 20.0)
    private val brickwallLimiter = DynamicRangeCompressor(sampleRate)

    @Volatile
    var speakerDspEnabled: Boolean = false

    @Volatile
    var virtualBassEnabled: Boolean = false

    @Volatile
    var landscapeStereoEnabled: Boolean = false

    @Volatile
    var portraitVocalFocusEnabled: Boolean = false

    @Volatile
    var bodyWarmthEnabled: Boolean = false

    @Volatile
    var tuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC
        private set

    val latestLimiterReductionDb: Float
        get() = if (speakerDspEnabled) {
            minOf(latestExcursionReductionDb, brickwallLimiter.latestGainReductionDb)
        } else {
            0f
        }

    init {
        configure(
            isSpeakerDspEnabled = false,
            isVirtualBassEnabled = false,
            isLandscapeStereoEnabled = false,
            profile = SpeakerTuningProfile.PRO_MAX_MUSIC,
            isPortraitVocalFocus = false,
            isBodyWarmthEnabled = false
        )
    }

    fun configure(
        isSpeakerDspEnabled: Boolean,
        isVirtualBassEnabled: Boolean,
        isLandscapeStereoEnabled: Boolean = false,
        profile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
        isPortraitVocalFocus: Boolean = false,
        isBodyWarmthEnabled: Boolean = false
    ) {
        speakerDspEnabled = isSpeakerDspEnabled
        virtualBassEnabled = isVirtualBassEnabled
        landscapeStereoEnabled = isLandscapeStereoEnabled
        portraitVocalFocusEnabled = isPortraitVocalFocus && !isLandscapeStereoEnabled
        bodyWarmthEnabled = isBodyWarmthEnabled
        tuningProfile = profile

        if (isBodyWarmthEnabled) {
            bodyWarmth125HzEq.setPeaking(
                sampleRate,
                ProMaxHardwareRefinementSpecs.BODY_WARMTH_HZ,
                ProMaxHardwareRefinementSpecs.BODY_WARMTH_Q,
                ProMaxHardwareRefinementSpecs.BODY_WARMTH_DB
            )
        } else {
            bodyWarmth125HzEq.reset()
        }

        if (portraitVocalFocusEnabled) {
            portraitVocalCenter2000Hz.setPeaking(sampleRate, 2000.0, 1.0, 2.2)
        } else {
            portraitVocalCenter2000Hz.reset()
        }

        when (profile) {
            SpeakerTuningProfile.PRO_MAX_MUSIC,
            SpeakerTuningProfile.CINEMA_LANDSCAPE -> {
                hpfStage1.setHighPass(sampleRate, FlagshipSpeakerSpecs.SUB_BASS_HPF_HZ, 0.5411961)
                hpfStage2.setHighPass(sampleRate, FlagshipSpeakerSpecs.SUB_BASS_HPF_HZ, 1.3065630)
                chamberBassEq.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.CHAMBER_BASS_HZ,
                    FlagshipSpeakerSpecs.CHAMBER_BASS_Q,
                    FlagshipSpeakerSpecs.CHAMBER_BASS_DB
                )
                boxinessScoop350Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.BOXINESS_CUT_HZ,
                    FlagshipSpeakerSpecs.BOXINESS_CUT_Q,
                    FlagshipSpeakerSpecs.BOXINESS_CUT_DB
                )
                dialogueFocus1800Hz.setPeaking(sampleRate, 1800.0, 1.0, 0.0)
                presence3000Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.PRESENCE_HZ,
                    FlagshipSpeakerSpecs.PRESENCE_Q,
                    FlagshipSpeakerSpecs.PRESENCE_DB
                )
                harshnessCut4500Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_HZ,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_Q,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_DB
                )
                deEsser7000Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.DE_ESSER_HZ,
                    FlagshipSpeakerSpecs.DE_ESSER_Q,
                    FlagshipSpeakerSpecs.DE_ESSER_DB
                )
                crispAirShelf12000Hz.setHighShelf(
                    sampleRate,
                    FlagshipSpeakerSpecs.AIR_SHELF_HZ,
                    FlagshipSpeakerSpecs.AIR_SHELF_Q,
                    FlagshipSpeakerSpecs.AIR_SHELF_DB
                )
            }

            SpeakerTuningProfile.PRO_WARM_DIALOGUE -> {
                hpfStage1.setHighPass(sampleRate, 100.0, 0.5411961)
                hpfStage2.setHighPass(sampleRate, 100.0, 1.3065630)
                chamberBassEq.setPeaking(sampleRate, 150.0, 1.0, 1.5)
                boxinessScoop350Hz.setPeaking(sampleRate, FlagshipSpeakerSpecs.BOXINESS_CUT_HZ, FlagshipSpeakerSpecs.BOXINESS_CUT_Q, -2.5)
                dialogueFocus1800Hz.setPeaking(sampleRate, 1800.0, 1.0, 3.2)
                presence3000Hz.setPeaking(sampleRate, 3000.0, 1.2, 1.2)
                harshnessCut4500Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_HZ,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_Q,
                    FlagshipSpeakerSpecs.HARSHNESS_CUT_DB
                )
                deEsser7000Hz.setPeaking(
                    sampleRate,
                    FlagshipSpeakerSpecs.DE_ESSER_HZ,
                    FlagshipSpeakerSpecs.DE_ESSER_Q,
                    FlagshipSpeakerSpecs.DE_ESSER_DB
                )
                crispAirShelf12000Hz.setHighShelf(sampleRate, 12000.0, 0.707, 0.5)
            }
        }

        if (!isSpeakerDspEnabled) {
            latestExcursionReductionDb = 0f
        }

        brickwallLimiter.configureCustom(
            isEnabled = isSpeakerDspEnabled,
            threshold = FlagshipSpeakerSpecs.BRICKWALL_THRESHOLD_DB,
            knee = 0f,
            compRatio = FlagshipSpeakerSpecs.BRICKWALL_RATIO,
            attackMs = FlagshipSpeakerSpecs.BRICKWALL_ATTACK_MS,
            releaseMs = FlagshipSpeakerSpecs.BRICKWALL_RELEASE_MS,
            makeupGainDb = 0f
        )
    }

    private fun readXtcDelayed(ring: DoubleArray, delaySamples: Double = xtcDelaySamples): Double {
        val intD = delaySamples.toInt()
        val frac = delaySamples - intD
        val idx0 = (xtcWriteIdx - intD) and xtcMask
        val idx1 = (xtcWriteIdx - intD - 1) and xtcMask
        return ring[idx0] * (1.0 - frac) + ring[idx1] * frac
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!speakerDspEnabled) return inL to inR

        val profile = tuningProfile

        // Stage 2 extraction pre-HPF so sub-bass energy below 86Hz feeds warm 2nd-order harmonic synthesis (110Hz–220Hz)
        val (harmL, harmR) = if (virtualBassEnabled) {
            val (subL, subR) = subExtractor.processStereo(inL, inR)
            val driveL = (subL * 2.2).coerceIn(-1.25, 1.25)
            val driveR = (subR * 2.2).coerceIn(-1.25, 1.25)
            // Warm 2nd-order even-harmonic excitation + gentle soft saturation
            val shapedL = (driveL * driveL) + 0.35 * tanh(driveL)
            val shapedR = (driveR * driveR) + 0.35 * tanh(driveR)
            harmonicBandPass.processStereo(shapedL, shapedR)
        } else {
            0.0 to 0.0
        }

        // Stage 1: Steep 86Hz (24dB/oct) sub-sonic excursion guard
        val (hp1L, hp1R) = hpfStage1.processStereo(inL, inR)
        val (hp2L, hp2R) = hpfStage2.processStereo(hp1L, hp1R)

        // Blend synthesized 110Hz–220Hz warm 2nd-order harmonic bass back in
        val harmBlend = if (profile == SpeakerTuningProfile.PRO_WARM_DIALOGUE) 0.40 else 0.85
        var l = hp2L + harmL * harmBlend
        var r = hp2R + harmR * harmBlend

        // Stage 3: Gain-Staged Porous-Cavity Compliance Shelf (+3.2dB @ 128Hz, Q=1.25) + 350Hz Boxiness Scoop (-3.5dB, Q=1.5)
        val (cbL, cbR) = chamberBassEq.processStereo(l, r)
        val (bwL, bwR) = if (bodyWarmthEnabled) {
            val (eqWarmL, eqWarmR) = bodyWarmth125HzEq.processStereo(cbL, cbR)
            val (bandL, bandR) = bodyWarmthExtractor125.processStereo(cbL, cbR)
            val dL = (bandL * 1.8).coerceIn(-1.1, 1.1)
            val dR = (bandR * 1.8).coerceIn(-1.1, 1.1)
            val satL = (dL * dL * 0.55) + 0.18 * tanh(dL)
            val satR = (dR * dR * 0.55) + 0.18 * tanh(dR)
            val (hWarmL, hWarmR) = bodyWarmthHarmonicOut250.processStereo(satL, satR)
            (eqWarmL + hWarmL * 0.18) to (eqWarmR + hWarmR * 0.18)
        } else {
            cbL to cbR
        }
        val (scL, scR) = boxinessScoop350Hz.processStereo(bwL, bwR)
        val (dfL, dfR) = dialogueFocus1800Hz.processStereo(scL, scR)
        val (prL, prR) = presence3000Hz.processStereo(dfL, dfR)
        val (hcL, hcR) = harshnessCut4500Hz.processStereo(prL, prR)
        val (deL, deR) = deEsser7000Hz.processStereo(hcL, hcR)
        val (arL, arR) = crispAirShelf12000Hz.processStereo(deL, deR)
        l = arL
        r = arR

        // Stage 4: Orientation-Aware & Cinema Landscape Hybrid Earpiece + Bottom Speaker Crossover Simulation
        // Always keeps <180Hz bass centered mono L=R for balanced vertical weight.
        // - Portrait: focuses center vocals (2kHz) & preserves stereo above 180Hz.
        // - Landscape: widens horizontal stereo via 2nd-Order Transaural XTC (-22% @ 0.18ms + +4.84% @ 0.36ms).
        // - Cinema Landscape profile: engages 2nd-order transaural XTC (-26% @ 0.17ms + +12% @ 0.34ms, 4.5kHz shadow LPF).
        val isCinemaLandscape = profile == SpeakerTuningProfile.CINEMA_LANDSCAPE
        val mid = 0.5 * (l + r)
        val side = 0.5 * (l - r)
        val (_, s1) = hybridSideHpf1.processStereo(0.0, side)
        val (_, sideAbove180) = hybridSideHpf2.processStereo(0.0, s1)
        val effectiveSide = if (portraitVocalFocusEnabled && !isCinemaLandscape) sideAbove180 * 0.62 else sideAbove180
        val focusedMid = if (portraitVocalFocusEnabled && !isCinemaLandscape) {
            portraitVocalCenter2000Hz.processStereo(mid, mid).first
        } else {
            mid
        }
        val centeredL = focusedMid + effectiveSide
        val centeredR = focusedMid - effectiveSide

        val (hpXtcL, hpXtcR) = xtcHighPass180.processStereo(centeredL, centeredR)
        val (cinL, cinR) = cinemaHeadShadowLpf.processStereo(hpXtcL, hpXtcR)
        xtcRingL[xtcWriteIdx] = hpXtcL
        xtcRingR[xtcWriteIdx] = hpXtcR
        cinemaRingL[xtcWriteIdx] = cinL
        cinemaRingR[xtcWriteIdx] = cinR

        val delayedXtcL = readXtcDelayed(xtcRingL, xtcDelaySamples)
        val delayedXtcR = readXtcDelayed(xtcRingR, xtcDelaySamples)
        val delayedSecXtcL = readXtcDelayed(xtcRingL, xtcSecondaryDelaySamples)
        val delayedSecXtcR = readXtcDelayed(xtcRingR, xtcSecondaryDelaySamples)
        val cinemaPrimL = readXtcDelayed(cinemaRingL, cinemaPrimaryDelaySamples)
        val cinemaPrimR = readXtcDelayed(cinemaRingR, cinemaPrimaryDelaySamples)
        val cinemaSecL = readXtcDelayed(cinemaRingL, cinemaSecondaryDelaySamples)
        val cinemaSecR = readXtcDelayed(cinemaRingR, cinemaSecondaryDelaySamples)
        xtcWriteIdx = (xtcWriteIdx + 1) and xtcMask

        if (isCinemaLandscape) {
            l = centeredL -
                FlagshipSpeakerSpecs.CINEMA_XTC_PRIMARY_GAIN * cinemaPrimR +
                FlagshipSpeakerSpecs.CINEMA_XTC_SECONDARY_GAIN * cinemaSecL
            r = centeredR -
                FlagshipSpeakerSpecs.CINEMA_XTC_PRIMARY_GAIN * cinemaPrimL +
                FlagshipSpeakerSpecs.CINEMA_XTC_SECONDARY_GAIN * cinemaSecR
        } else if (landscapeStereoEnabled) {
            val crossGain = FlagshipSpeakerSpecs.XTC_CROSSFEED_GAIN
            val secondaryGain = FlagshipSpeakerSpecs.XTC_SECONDARY_GAIN
            l = centeredL - crossGain * delayedXtcR + secondaryGain * delayedSecXtcL
            r = centeredR - crossGain * delayedXtcL + secondaryGain * delayedSecXtcR
        } else {
            l = centeredL
            r = centeredR
        }

        // Stage 5a: Apple Dynamic Excursion Protection (fast dynamic low-end limiter: -14dB, 6:1, 3ms/120ms)
        val (lowL, lowR) = excursionLowPass.processStereo(l, r)
        val highL = l - lowL
        val highR = r - lowR

        val lowPeak = max(abs(lowL), abs(lowR)).coerceAtLeast(1e-6)
        val lowInputDb = 20.0 * log10(lowPeak)
        val envCoeff = if (lowInputDb > excursionEnvDb) excursionAttackCoeff else excursionReleaseCoeff
        excursionEnvDb = envCoeff * excursionEnvDb + (1.0 - envCoeff) * lowInputDb

        val excGrDb = DynamicRangeCompressor.computeGainReductionDb(
            inputDb = excursionEnvDb.toFloat(),
            thresholdDb = FlagshipSpeakerSpecs.EXCURSION_THRESHOLD_DB,
            kneeDb = FlagshipSpeakerSpecs.EXCURSION_KNEE_DB,
            ratio = FlagshipSpeakerSpecs.EXCURSION_RATIO
        )
        latestExcursionReductionDb = excGrDb
        val excGainLinear = 10.0.pow(excGrDb / 20.0)

        val protectedL = lowL * excGainLinear + highL
        val protectedR = lowR * excGainLinear + highR

        // Stage 5b: +4.5 dB clean makeup gain feeding into -0.3 dB Brickwall Limiter
        val makeupL = protectedL * makeupGainLinear
        val makeupR = protectedR * makeupGainLinear
        val (limL, limR) = brickwallLimiter.processStereoSample(makeupL, makeupR)
        return limL.coerceIn(-0.985, 0.985) to limR.coerceIn(-0.985, 0.985)
    }

    companion object {
        fun computeBodyWarmthResponseDbAt(
            freqHz: Double,
            sampleRate: Int = 44100
        ): Float {
            val bw = StereoBiquad().apply {
                setPeaking(
                    sampleRate,
                    ProMaxHardwareRefinementSpecs.BODY_WARMTH_HZ,
                    ProMaxHardwareRefinementSpecs.BODY_WARMTH_Q,
                    ProMaxHardwareRefinementSpecs.BODY_WARMTH_DB
                )
            }
            return bw.magnitudeDbAt(sampleRate, freqHz).toFloat()
        }

        fun computeVcurveResponseDbAt(
            freqHz: Double,
            profile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate: Int = 44100,
            isBodyWarmthEnabled: Boolean = false
        ): Float {
            val h1 = StereoBiquad()
            val h2 = StereoBiquad()
            val bass = StereoBiquad()
            val scoop = StereoBiquad()
            val dlg = StereoBiquad()
            val pres = StereoBiquad()
            val harsh = StereoBiquad()
            val deEss = StereoBiquad()
            val air = StereoBiquad()

            when (profile) {
                SpeakerTuningProfile.PRO_MAX_MUSIC,
                SpeakerTuningProfile.CINEMA_LANDSCAPE -> {
                    h1.setHighPass(sampleRate, FlagshipSpeakerSpecs.SUB_BASS_HPF_HZ, 0.5411961)
                    h2.setHighPass(sampleRate, FlagshipSpeakerSpecs.SUB_BASS_HPF_HZ, 1.3065630)
                    bass.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.CHAMBER_BASS_HZ,
                        FlagshipSpeakerSpecs.CHAMBER_BASS_Q,
                        FlagshipSpeakerSpecs.CHAMBER_BASS_DB
                    )
                    scoop.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.BOXINESS_CUT_HZ,
                        FlagshipSpeakerSpecs.BOXINESS_CUT_Q,
                        FlagshipSpeakerSpecs.BOXINESS_CUT_DB
                    )
                    dlg.setPeaking(sampleRate, 1800.0, 1.0, 0.0)
                    pres.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.PRESENCE_HZ,
                        FlagshipSpeakerSpecs.PRESENCE_Q,
                        FlagshipSpeakerSpecs.PRESENCE_DB
                    )
                    harsh.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_HZ,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_Q,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_DB
                    )
                    deEss.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.DE_ESSER_HZ,
                        FlagshipSpeakerSpecs.DE_ESSER_Q,
                        FlagshipSpeakerSpecs.DE_ESSER_DB
                    )
                    air.setHighShelf(
                        sampleRate,
                        FlagshipSpeakerSpecs.AIR_SHELF_HZ,
                        FlagshipSpeakerSpecs.AIR_SHELF_Q,
                        FlagshipSpeakerSpecs.AIR_SHELF_DB
                    )
                }

                SpeakerTuningProfile.PRO_WARM_DIALOGUE -> {
                    h1.setHighPass(sampleRate, 100.0, 0.5411961)
                    h2.setHighPass(sampleRate, 100.0, 1.3065630)
                    bass.setPeaking(sampleRate, 150.0, 1.0, 1.5)
                    scoop.setPeaking(sampleRate, FlagshipSpeakerSpecs.BOXINESS_CUT_HZ, FlagshipSpeakerSpecs.BOXINESS_CUT_Q, -2.5)
                    dlg.setPeaking(sampleRate, 1800.0, 1.0, 3.2)
                    pres.setPeaking(sampleRate, 3000.0, 1.2, 1.2)
                    harsh.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_HZ,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_Q,
                        FlagshipSpeakerSpecs.HARSHNESS_CUT_DB
                    )
                    deEss.setPeaking(
                        sampleRate,
                        FlagshipSpeakerSpecs.DE_ESSER_HZ,
                        FlagshipSpeakerSpecs.DE_ESSER_Q,
                        FlagshipSpeakerSpecs.DE_ESSER_DB
                    )
                    air.setHighShelf(sampleRate, 12000.0, 0.707, 0.5)
                }
            }

            val bodyExtra = if (isBodyWarmthEnabled) computeBodyWarmthResponseDbAt(freqHz, sampleRate) else 0f

            return (h1.magnitudeDbAt(sampleRate, freqHz) +
                h2.magnitudeDbAt(sampleRate, freqHz) +
                bass.magnitudeDbAt(sampleRate, freqHz) +
                scoop.magnitudeDbAt(sampleRate, freqHz) +
                dlg.magnitudeDbAt(sampleRate, freqHz) +
                pres.magnitudeDbAt(sampleRate, freqHz) +
                harsh.magnitudeDbAt(sampleRate, freqHz) +
                deEss.magnitudeDbAt(sampleRate, freqHz) +
                air.magnitudeDbAt(sampleRate, freqHz)).toFloat() + bodyExtra
        }
    }
}

/**
 * Dedicated 3-Band Tone Equalizer (iOS 27 AirPods 3-Band Custom Equalizer & Linear Independent):
 * - Range: -7.0 dB to +7.0 dB per band.
 * - iOS 27 Smart Curve (smartCurveEnabled = true):
 *   1. Low Band (20Hz – 250Hz): 250Hz lowshelf (Q = 0.707) without altering mids or highs.
 *   2. Mid Band (250Hz – 4.7kHz):
 *      - Mid > 0 dB: Vocal presence boost (600Hz–4.7kHz, fc = 1679.3Hz), automatic mud trim
 *        (-0.4 dB <250Hz per +1 dB Mid boost), and crisp clarity (+0.5 dB >3.3kHz per +1 dB Mid boost).
 *      - Mid < 0 dB: Boxy midrange cut (300Hz–2.6kHz, fc = 883.2Hz), automatic proportional V-curve
 *        warmth (+0.7 dB <250Hz and +0.5 dB >3.3kHz per -1 dB Mid cut).
 *   3. High Band (4.7kHz – 20kHz): 4700Hz highshelf + Apple inverse warmth tilt (-0.15 dB <2.5kHz per +1 dB High boost).
 * - Linear Independent (smartCurveEnabled = false):
 *   Clean independent Low (250Hz shelf), Mid (1084Hz Q=0.35 peaking), High (4700Hz shelf) with zero cross-band tilt.
 */
class ThreeBandToneFilter(private val sampleRate: Int = 44100) {
    private val subSonicGuard = StereoBiquad()
    private val lowShelf = StereoBiquad()
    private val midPeaking = StereoBiquad()
    private val smartClarityShelf = StereoBiquad()
    private val inverseWarmthShelf = StereoBiquad()
    private val highShelf = StereoBiquad()

    @Volatile
    private var enabled: Boolean = true

    init {
        configure(
            isEnabled = true,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 0f,
            isSpeakerTarget = false,
            smartCurveEnabled = true
        )
    }

    fun configure(
        isEnabled: Boolean,
        bassDb: Float,
        midsDb: Float,
        trebleDb: Float,
        isSpeakerTarget: Boolean = false,
        skipHighShelf: Boolean = false,
        smartCurveEnabled: Boolean = true
    ) {
        enabled = isEnabled
        val clampedBass = if (isEnabled) {
            bassDb.coerceIn(ThreeBandEqRanges.BASS_MIN_DB, ThreeBandEqRanges.BASS_MAX_DB).toDouble()
        } else {
            0.0
        }
        val clampedMids = if (isEnabled) {
            midsDb.coerceIn(ThreeBandEqRanges.MIDS_MIN_DB, ThreeBandEqRanges.MIDS_MAX_DB).toDouble()
        } else {
            0.0
        }
        val clampedTrebleRaw = if (isEnabled) {
            trebleDb.coerceIn(ThreeBandEqRanges.TREBLE_MIN_DB, ThreeBandEqRanges.TREBLE_MAX_DB).toDouble()
        } else {
            0.0
        }
        val clampedTreble = if (!skipHighShelf) clampedTrebleRaw else 0.0

        val guardFreq = if (isSpeakerTarget) 95.0 else 20.0
        if (isEnabled) {
            subSonicGuard.setHighPass(sampleRate, guardFreq, 0.7071)
        } else {
            subSonicGuard.reset()
        }

        val midLowCompDb = if (!smartCurveEnabled) {
            0.0
        } else if (clampedMids >= 0.0) {
            -0.4 * clampedMids
        } else {
            0.7 * (-clampedMids)
        }

        val midClarityDb = if (smartCurveEnabled) {
            kotlin.math.abs(clampedMids) * 0.5
        } else {
            0.0
        }

        val trebleInverseWarmthDb = if (smartCurveEnabled && clampedTrebleRaw > 0.0) {
            -0.15 * clampedTrebleRaw
        } else {
            0.0
        }

        lowShelf.setLowShelf(sampleRate, 250.0, 0.7071, clampedBass + midLowCompDb)
        when {
            !smartCurveEnabled -> midPeaking.setPeaking(sampleRate, 1084.0, 0.35, clampedMids)
            clampedMids >= 0.0 -> midPeaking.setPeaking(sampleRate, 1679.3, 0.58, clampedMids)
            else -> midPeaking.setPeaking(sampleRate, 883.2, 0.55, clampedMids)
        }
        smartClarityShelf.setHighShelf(sampleRate, 3300.0, 0.7071, midClarityDb)
        inverseWarmthShelf.setLowShelf(sampleRate, 2500.0, 0.60, trebleInverseWarmthDb)
        highShelf.setHighShelf(sampleRate, 4700.0, 0.7071, clampedTreble)
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) return inL to inR
        val (gL, gR) = subSonicGuard.processStereo(inL, inR)
        val (lL, lR) = lowShelf.processStereo(gL, gR)
        val (mL, mR) = midPeaking.processStereo(lL, lR)
        val (cL, cR) = smartClarityShelf.processStereo(mL, mR)
        val (wL, wR) = inverseWarmthShelf.processStereo(cL, cR)
        return highShelf.processStereo(wL, wR)
    }

    companion object {
        fun computeCombinedResponseDbAt(
            freqHz: Double,
            bassDb: Float,
            midsDb: Float,
            trebleDb: Float,
            isSpeakerTarget: Boolean = false,
            sampleRate: Int = 44100,
            smartCurveEnabled: Boolean = true
        ): Float {
            val clampedBass = bassDb.coerceIn(ThreeBandEqRanges.BASS_MIN_DB, ThreeBandEqRanges.BASS_MAX_DB).toDouble()
            val clampedMids = midsDb.coerceIn(ThreeBandEqRanges.MIDS_MIN_DB, ThreeBandEqRanges.MIDS_MAX_DB).toDouble()
            val clampedTreble = trebleDb.coerceIn(ThreeBandEqRanges.TREBLE_MIN_DB, ThreeBandEqRanges.TREBLE_MAX_DB).toDouble()

            val midLowCompDb = if (!smartCurveEnabled) {
                0.0
            } else if (clampedMids >= 0.0) {
                -0.4 * clampedMids
            } else {
                0.7 * (-clampedMids)
            }
            val midClarityDb = if (smartCurveEnabled) kotlin.math.abs(clampedMids) * 0.5 else 0.0
            val trebleInverseWarmthDb = if (smartCurveEnabled && clampedTreble > 0.0) -0.15 * clampedTreble else 0.0

            val guardFreq = if (isSpeakerTarget) 95.0 else 20.0
            val guard = StereoBiquad().apply { setHighPass(sampleRate, guardFreq, 0.7071) }
            val low = StereoBiquad().apply { setLowShelf(sampleRate, 250.0, 0.7071, clampedBass + midLowCompDb) }
            val mid = StereoBiquad().apply {
                when {
                    !smartCurveEnabled -> setPeaking(sampleRate, 1084.0, 0.35, clampedMids)
                    clampedMids >= 0.0 -> setPeaking(sampleRate, 1679.3, 0.58, clampedMids)
                    else -> setPeaking(sampleRate, 883.2, 0.55, clampedMids)
                }
            }
            val clarity = StereoBiquad().apply { setHighShelf(sampleRate, 3300.0, 0.7071, midClarityDb) }
            val inverseWarmth = StereoBiquad().apply { setLowShelf(sampleRate, 2500.0, 0.60, trebleInverseWarmthDb) }
            val high = StereoBiquad().apply { setHighShelf(sampleRate, 4700.0, 0.7071, clampedTreble) }

            return (guard.magnitudeDbAt(sampleRate, freqHz) +
                low.magnitudeDbAt(sampleRate, freqHz) +
                mid.magnitudeDbAt(sampleRate, freqHz) +
                clarity.magnitudeDbAt(sampleRate, freqHz) +
                inverseWarmth.magnitudeDbAt(sampleRate, freqHz) +
                high.magnitudeDbAt(sampleRate, freqHz)).toFloat()
        }
    }
}

/**
 * iPhone Warmth / Treble Smoothness (De-Harsh) Contour Processor:
 * - Smoothly rolls off harsh 3.5 kHz – 10 kHz ear fatigue (-4.5 dB peaking @ 5.5 kHz, -2.5 dB shelf @ 8.5 kHz at 100%)
 * - Adds up to +2.0 dB of warm low-mid chassis body at 160 Hz.
 */
class TrebleSmoothnessProcessor(private val sampleRate: Int = 44100) {
    private val warmBody160Hz = StereoBiquad()
    private val fatigueDip5500Hz = StereoBiquad()
    private val silkyShelf8500Hz = StereoBiquad()

    @Volatile
    private var enabled: Boolean = false

    init {
        configure(isEnabled = false, smoothnessPercent = 0f)
    }

    fun configure(isEnabled: Boolean, smoothnessPercent: Float) {
        val p = smoothnessPercent.coerceIn(0f, 1f).toDouble()
        enabled = isEnabled && p > 0.005
        if (enabled) {
            warmBody160Hz.setPeaking(sampleRate, 160.0, 0.95, TrebleGainStagingSpecs.WARM_BODY_160_MAX_DB * p)
            fatigueDip5500Hz.setPeaking(sampleRate, 5500.0, 0.65, TrebleGainStagingSpecs.DEHARSH_5500_MAX_CUT_DB * p)
            silkyShelf8500Hz.setHighShelf(sampleRate, 8500.0, 0.65, TrebleGainStagingSpecs.DEHARSH_SHELF_8500_MAX_CUT_DB * p)
        } else {
            warmBody160Hz.reset()
            fatigueDip5500Hz.reset()
            silkyShelf8500Hz.reset()
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) return inL to inR
        val (bL, bR) = warmBody160Hz.processStereo(inL, inR)
        val (dL, dR) = fatigueDip5500Hz.processStereo(bL, bR)
        return silkyShelf8500Hz.processStereo(dL, dR)
    }

    companion object {
        fun computeResponseDbAt(
            freqHz: Double,
            smoothnessPercent: Float,
            sampleRate: Int = 44100
        ): Float {
            val p = smoothnessPercent.coerceIn(0f, 1f).toDouble()
            if (p <= 0.005) return 0f
            val body = StereoBiquad().apply {
                setPeaking(sampleRate, 160.0, 0.95, TrebleGainStagingSpecs.WARM_BODY_160_MAX_DB * p)
            }
            val dip = StereoBiquad().apply {
                setPeaking(sampleRate, 5500.0, 0.65, TrebleGainStagingSpecs.DEHARSH_5500_MAX_CUT_DB * p)
            }
            val shelf = StereoBiquad().apply {
                setHighShelf(sampleRate, 8500.0, 0.65, TrebleGainStagingSpecs.DEHARSH_SHELF_8500_MAX_CUT_DB * p)
            }
            return (body.magnitudeDbAt(sampleRate, freqHz) +
                dip.magnitudeDbAt(sampleRate, freqHz) +
                shelf.magnitudeDbAt(sampleRate, freqHz)).toFloat()
        }
    }
}

/**
 * iOS Volume-Step Acoustic Contouring & High-Volume De-Harshing Guard (5kHz–9kHz):
 * - Below 40% volume: gently lifts 115Hz bass (+3 dB) & 2.8kHz presence (+2 dB) for rich low-level listening.
 * - Between 40% and 80% volume: transparent 0 dB reference response.
 * - Above 80% volume: smoothly flattens <100Hz sub-bass (-3.5 dB shelf @ 85Hz), boosts 1kHz–2kHz midrange (+2 dB @ 1.5kHz)
 *   for room-filling projection, AND automatically attenuates piercing 6.5kHz sibilance/treble harshness (5kHz–9kHz, -3.5 dB).
 */
class IosVolumeContourAndDeHarshProcessor(private val sampleRate: Int = 44100) {
    private val lowVolBass115 = StereoBiquad()
    private val lowVolPresence2800 = StereoBiquad()
    private val highVolSubFlatten85 = StereoBiquad()
    private val highVolMidProj1500 = StereoBiquad()
    private val highVolDeHarsh6500 = StereoBiquad()

    @Volatile
    private var contourActive: Boolean = false

    @Volatile
    var isDeHarshGuardActive: Boolean = false
        private set

    @Volatile
    var volumeStatusLabel: String = "Linear (40–80%)"
        private set

    @Volatile
    var outputGainLinear: Double = 1.0
        private set

    @Volatile
    private var applyOutputGain: Boolean = false

    init {
        configure(isEnabled = false, volumePercent = 0.75f, applyVolumeScale = false)
    }

    fun configure(
        isEnabled: Boolean,
        volumePercent: Float,
        applyVolumeScale: Boolean = false
    ) {
        val vol = volumePercent.coerceIn(0f, 1f)
        applyOutputGain = applyVolumeScale
        outputGainLinear = if (!applyVolumeScale) {
            1.0
        } else if (vol <= 0.005f) {
            0.0
        } else {
            vol.toDouble().pow(1.4)
        }

        if (!isEnabled) {
            contourActive = false
            isDeHarshGuardActive = false
            volumeStatusLabel = "Bypassed"
            lowVolBass115.reset()
            lowVolPresence2800.reset()
            highVolSubFlatten85.reset()
            highVolMidProj1500.reset()
            highVolDeHarsh6500.reset()
            return
        }

        val lowFactor = if (vol < ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD) {
            ((ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD - vol) / ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD).coerceIn(0f, 1f).toDouble()
        } else {
            0.0
        }

        val highFactor = if (vol > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) {
            ((vol - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) / (1.0f - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD)).coerceIn(0f, 1f).toDouble()
        } else {
            0.0
        }

        contourActive = lowFactor > 0.005 || highFactor > 0.005
        isDeHarshGuardActive = highFactor > 0.005
        volumeStatusLabel = when {
            lowFactor > 0.005 -> "Low-Vol Contour (+115Hz / +2.8kHz)"
            highFactor > 0.005 -> "Projection + 5–9kHz De-Harsh Guard"
            else -> "Linear (40–80%)"
        }

        if (lowFactor > 0.005) {
            lowVolBass115.setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.LOW_VOL_BASS_HZ, 1.0, ProMaxHardwareRefinementSpecs.LOW_VOL_BASS_MAX_DB * lowFactor)
            lowVolPresence2800.setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.LOW_VOL_PRESENCE_HZ, 1.2, ProMaxHardwareRefinementSpecs.LOW_VOL_PRESENCE_MAX_DB * lowFactor)
        } else {
            lowVolBass115.reset()
            lowVolPresence2800.reset()
        }

        if (highFactor > 0.005) {
            highVolSubFlatten85.setLowShelf(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_SUB_FLATTEN_HZ, 0.7071, ProMaxHardwareRefinementSpecs.HIGH_VOL_SUB_FLATTEN_MAX_DB * highFactor)
            highVolMidProj1500.setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_MID_PROJ_HZ, 0.85, ProMaxHardwareRefinementSpecs.HIGH_VOL_MID_PROJ_MAX_DB * highFactor)
            highVolDeHarsh6500.setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_HZ, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_Q, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_MAX_DB * highFactor)
        } else {
            highVolSubFlatten85.reset()
            highVolMidProj1500.reset()
            highVolDeHarsh6500.reset()
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        var l = inL
        var r = inR
        if (contourActive) {
            val (bL, bR) = lowVolBass115.processStereo(l, r)
            val (pL, pR) = lowVolPresence2800.processStereo(bL, bR)
            val (sL, sR) = highVolSubFlatten85.processStereo(pL, pR)
            val (mL, mR) = highVolMidProj1500.processStereo(sL, sR)
            val (dL, dR) = highVolDeHarsh6500.processStereo(mL, mR)
            l = dL
            r = dR
        }
        return if (applyOutputGain) {
            (l * outputGainLinear) to (r * outputGainLinear)
        } else {
            l to r
        }
    }

    companion object {
        fun computeContourResponseDbAt(
            freqHz: Double,
            volumePercent: Float,
            sampleRate: Int = 44100
        ): Float {
            val vol = volumePercent.coerceIn(0f, 1f)
            if (vol < ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD) {
                val lowFactor = ((ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD - vol) / ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD).coerceIn(0f, 1f).toDouble()
                if (lowFactor <= 0.005) return 0f
                val bass = StereoBiquad().apply {
                    setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.LOW_VOL_BASS_HZ, 1.0, ProMaxHardwareRefinementSpecs.LOW_VOL_BASS_MAX_DB * lowFactor)
                }
                val pres = StereoBiquad().apply {
                    setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.LOW_VOL_PRESENCE_HZ, 1.2, ProMaxHardwareRefinementSpecs.LOW_VOL_PRESENCE_MAX_DB * lowFactor)
                }
                return (bass.magnitudeDbAt(sampleRate, freqHz) + pres.magnitudeDbAt(sampleRate, freqHz)).toFloat()
            } else if (vol > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) {
                val highFactor = ((vol - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) / (1.0f - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD)).coerceIn(0f, 1f).toDouble()
                if (highFactor <= 0.005) return 0f
                val sub = StereoBiquad().apply {
                    setLowShelf(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_SUB_FLATTEN_HZ, 0.7071, ProMaxHardwareRefinementSpecs.HIGH_VOL_SUB_FLATTEN_MAX_DB * highFactor)
                }
                val mid = StereoBiquad().apply {
                    setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_MID_PROJ_HZ, 0.85, ProMaxHardwareRefinementSpecs.HIGH_VOL_MID_PROJ_MAX_DB * highFactor)
                }
                val deHarsh = StereoBiquad().apply {
                    setPeaking(sampleRate, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_HZ, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_Q, ProMaxHardwareRefinementSpecs.HIGH_VOL_DEHARSH_MAX_DB * highFactor)
                }
                return (sub.magnitudeDbAt(sampleRate, freqHz) +
                    mid.magnitudeDbAt(sampleRate, freqHz) +
                    deHarsh.magnitudeDbAt(sampleRate, freqHz)).toFloat()
            }
            return 0f
        }
    }
}

/**
 * Cascaded 10-band stereo biquad peaking EQ filter + 3-band Bass/Mids/Treble tone stage +
 * preamp + Sound Check + Voice Isolation + Late Night compressor + Spatial Audio + Phone Speaker DSP +
 * iOS Volume-Step Acoustic Contour & High-Volume De-Harshing Guard.
 */
class BiquadEqFilterBank(private val sampleRate: Int = 44100) {

    private val headphoneAccommodationsProcessor = HeadphoneAccommodationsProcessor(sampleRate)
    private val bandFilters = Array(10) { StereoBiquad() }
    private val threeBandToneFilter = ThreeBandToneFilter(sampleRate)
    private val oversampledUpperEqStage = OversampledUpperEqStage(sampleRate)
    private val trebleSmoothnessProcessor = TrebleSmoothnessProcessor(sampleRate)
    private val harmonicSheenProcessor = HarmonicSheenAndStereoFocusProcessor(sampleRate)
    private val volumeContourProcessor = IosVolumeContourAndDeHarshProcessor(sampleRate)
    private val audioMixStemShaper = AudioMixStemShaper(sampleRate)
    private val lateNightCompressor = DynamicRangeCompressor(sampleRate)
    private val speakerDspChain = PhoneSpeakerDspChain(sampleRate)
    private val loudnessMaximizerChain = IphoneLoudnessMaximizerChain(sampleRate)
    private val siliconDspCore = SiliconDspCore(sampleRate)
    private val spatialAudioEngine = SpatialAudioEngine(sampleRate)
    private val soundCheckNormalizer = SoundCheckNormalizer(sampleRate)
    private val voiceIsolationProcessor = VoiceIsolationProcessor(sampleRate)
    private val adaptiveMultibandBalancer = AdaptiveMultibandToneBalancer(sampleRate)
    private val speakerPhaseAlignmentFilter = SpeakerPhaseAlignmentFilter(sampleRate)
    private val listeningModeProcessor = ListeningModeProcessor(sampleRate)
    private val noiseFloorGate = ZeroNoiseFloorGateAndDitherer(sampleRate)

    @Volatile
    private var preampLinear: Double = 1.0

    @Volatile
    private var bypassed: Boolean = false

    @Volatile
    private var abCompareHeld: Boolean = false

    @Volatile
    private var abLoudnessMatchLinear: Double = 1.0

    @Volatile
    var conversationDuckLinear: Double = 1.0

    @Volatile
    var conversationSpeechActive: Boolean = false

    val latestGainReductionDb: Float
        get() {
            if (bypassed || abCompareHeld) return 0f
            val minGr = minOf(
                lateNightCompressor.latestGainReductionDb,
                speakerDspChain.latestLimiterReductionDb,
                loudnessMaximizerChain.latestRmsGrDb,
                loudnessMaximizerChain.latestBrickwallGrDb,
                adaptiveMultibandBalancer.latestLowGrDb,
                adaptiveMultibandBalancer.latestMidGrDb,
                adaptiveMultibandBalancer.latestHighGrDb,
                if (siliconDspCore.enabled) siliconDspCore.latestTruePeakGrDb else 0f
            )
            return if (minGr < -0.05f) minGr else 0f
        }

    val latestInputLufs: Float
        get() = soundCheckNormalizer.latestLufs

    val latestSoundCheckTrimDb: Float
        get() = if (bypassed || abCompareHeld) 0f else soundCheckNormalizer.latestTrimDb

    val latestSoundCheckCrestDb: Float
        get() = soundCheckNormalizer.latestCrestDb

    val latestMultibandLowGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else adaptiveMultibandBalancer.latestLowGrDb

    val latestMultibandMidGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else adaptiveMultibandBalancer.latestMidGrDb

    val latestMultibandHighGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else adaptiveMultibandBalancer.latestHighGrDb

    val isPhaseAlignmentActive: Boolean
        get() = !bypassed && !abCompareHeld && speakerPhaseAlignmentFilter.isActive

    val latestVoiceGateDb: Float
        get() = if (bypassed || abCompareHeld) 0f else voiceIsolationProcessor.latestGateAttenuationDb

    val latestAccommodationsGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else headphoneAccommodationsProcessor.latestGainReductionDb

    val latestLoudnessRmsGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else loudnessMaximizerChain.latestRmsGrDb

    val latestLoudnessBrickwallGrDb: Float
        get() = if (bypassed || abCompareHeld) 0f else loudnessMaximizerChain.latestBrickwallGrDb

    val isLoudnessAntiClipActive: Boolean
        get() = !bypassed && !abCompareHeld && loudnessMaximizerChain.isAntiClipActive

    val latestSiliconExcursionPct: Float
        get() = if (bypassed || abCompareHeld || !siliconDspCore.enabled) 0f else siliconDspCore.latestExcursionPercent

    val latestSiliconSvfCutoffHz: Float
        get() = siliconDspCore.latestSvfCutoffHz

    val latestSiliconCoilTempC: Float
        get() = siliconDspCore.latestCoilTempC

    val latestSiliconClassHRailV: Float
        get() = if (bypassed || abCompareHeld || !siliconDspCore.enabled) 3.7f else siliconDspCore.latestClassHRailV

    val latestSiliconTruePeakGrDb: Float
        get() = if (bypassed || abCompareHeld || !siliconDspCore.enabled) 0f else siliconDspCore.latestTruePeakGrDb

    @Volatile
    var latestTrebleBudget: TrebleBudgetAllocation? = null
        private set

    val isTrebleCapActive: Boolean
        get() = !bypassed && !abCompareHeld && (latestTrebleBudget?.isCapEngaged == true)

    val latestTrebleCappedBoostDb: Float
        get() = if (bypassed || abCompareHeld) 0f else (latestTrebleBudget?.cappedCombinedHfDb ?: 0f)

    @Volatile
    var latestResolvedOrientationLandscape: Boolean = true
        private set

    val isHighVolumeDeHarshActive: Boolean
        get() = !bypassed && !abCompareHeld && volumeContourProcessor.isDeHarshGuardActive

    val latestVolumeContourStatus: String
        get() = if (bypassed || abCompareHeld) "Bypassed" else volumeContourProcessor.volumeStatusLabel

    val isOversampledEqActive: Boolean
        get() = !bypassed && !abCompareHeld && oversampledUpperEqStage.enabled

    val latestHarmonicSheenThdPct: Float
        get() = if (bypassed || abCompareHeld) 0f else harmonicSheenProcessor.latestThdPercent

    val isNoiseFloorGateMuted: Boolean
        get() = !bypassed && !abCompareHeld && noiseFloorGate.isAutoMuted

    fun quantize16BitPcm(sample: Double, isLeft: Boolean): Short {
        return noiseFloorGate.quantize16BitPcm(sample, isLeft)
    }

    init {
        updateParameters(
            gainsDb = List(10) { 0f },
            preampDb = 0f,
            isBypassed = false,
            lateNightEnabled = false,
            nightIntensity = NightIntensity.STANDARD,
            speakerDspEnabled = false,
            virtualBassEnabled = false,
            outputTarget = OutputTarget.HEADPHONES,
            spatialMode = SpatialMode.OFF,
            spatialDepth = SpatialMode.ATMOS_MUSIC.defaultDepth,
            heightPresence = SpatialMode.ATMOS_MUSIC.defaultHeight,
            vocalAnchor = true,
            vocalFocus = 0.70f,
            soundCheckEnabled = false,
            voiceIsolationEnabled = false,
            abCompareHeld = false,
            tenBandEnabled = true,
            threeBandEnabled = false,
            threeBandBassDb = 0f,
            threeBandMidsDb = 0f,
            threeBandTrebleDb = 0f,
            siliconDspEnabled = false,
            adaptiveMultibandEnabled = false,
            speakerPhaseAlignmentEnabled = false,
            oversampledEqEnabled = false,
            harmonicSheenPercent = 0f,
            noiseFloorGateEnabled = false
        )
    }

    fun updateParameters(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean = false,
        nightIntensity: NightIntensity = NightIntensity.STANDARD,
        speakerDspEnabled: Boolean = false,
        virtualBassEnabled: Boolean = false,
        landscapeStereoStage: Boolean = false,
        speakerTuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
        audioMixMode: AudioMixMode = AudioMixMode.STANDARD,
        outputTarget: OutputTarget = OutputTarget.HEADPHONES,
        spatialMode: SpatialMode = SpatialMode.OFF,
        spatialDepth: Float = SpatialMode.ATMOS_MUSIC.defaultDepth,
        heightPresence: Float = SpatialMode.ATMOS_MUSIC.defaultHeight,
        vocalAnchor: Boolean = true,
        vocalFocus: Float = 0.70f,
        soundCheckEnabled: Boolean = false,
        voiceIsolationEnabled: Boolean = false,
        abCompareHeld: Boolean = false,
        threeBandEnabled: Boolean = false,
        tenBandEnabled: Boolean = !threeBandEnabled,
        threeBandBassDb: Float = 0f,
        threeBandMidsDb: Float = 0f,
        threeBandTrebleDb: Float = 0f,
        threeBandSmartCurveEnabled: Boolean = true,
        accommodationsEnabled: Boolean = false,
        accommodationsToneProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
        accommodationsSoftIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE,
        accommodationsApplyToMedia: Boolean = true,
        accommodationsOutputGainDb: Float = 0f,
        loudnessMaximizerEnabled: Boolean = false,
        loudnessBoostPercent: Float = 0.50f,
        siliconDspEnabled: Boolean = false,
        trebleSmoothnessPercent: Float = 0f,
        phoneOrientationMode: PhoneOrientationMode = PhoneOrientationMode.AUTO_DETECT,
        deviceIsLandscape: Boolean = false,
        masterVolumePercent: Float = 0.75f,
        applyMasterVolumeGain: Boolean = false,
        proMaxBodyWarmthEnabled: Boolean = false,
        adaptiveMultibandEnabled: Boolean = false,
        speakerPhaseAlignmentEnabled: Boolean = false,
        oversampledEqEnabled: Boolean = false,
        harmonicSheenPercent: Float = 0f,
        noiseFloorGateEnabled: Boolean = false,
        listeningMode: ListeningMode = ListeningMode.OFF,
        ambientPassThroughPercent: Float = 0.65f
    ) {
        bypassed = isBypassed
        this.abCompareHeld = abCompareHeld
        preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)

        val effectiveProcessing = !isBypassed && !abCompareHeld
        // Strict mutual exclusion: never allow 10-Band and 3-Band EQ to double-process audio
        val activeThreeBand = threeBandEnabled && !tenBandEnabled
        val activeTenBand = !activeThreeBand

        val resolvedLandscape = when (phoneOrientationMode) {
            PhoneOrientationMode.AUTO_DETECT -> deviceIsLandscape
            PhoneOrientationMode.PORTRAIT -> false
            PhoneOrientationMode.LANDSCAPE -> true
        }
        latestResolvedOrientationLandscape = resolvedLandscape

        val trebleBudget = TrebleGainStagingBudget.allocate(
            accommodationsActive = accommodationsEnabled && accommodationsApplyToMedia && effectiveProcessing,
            accommodationsProfile = accommodationsToneProfile,
            accommodationsIntensity = accommodationsSoftIntensity,
            spatialActive = effectiveProcessing && spatialMode != SpatialMode.OFF,
            spatialHeightPresence = heightPresence,
            siliconDspActive = siliconDspEnabled && effectiveProcessing
        )
        latestTrebleBudget = trebleBudget

        soundCheckNormalizer.enabled = soundCheckEnabled && effectiveProcessing
        voiceIsolationProcessor.enabled = voiceIsolationEnabled && effectiveProcessing
        audioMixStemShaper.mode = if (effectiveProcessing) audioMixMode else AudioMixMode.STANDARD
        headphoneAccommodationsProcessor.configure(
            isEnabled = accommodationsEnabled && accommodationsApplyToMedia && effectiveProcessing,
            profile = accommodationsToneProfile,
            intensity = accommodationsSoftIntensity,
            outputGainDb = accommodationsOutputGainDb,
            hfScale = trebleBudget.accHfScale.toDouble()
        )
        threeBandToneFilter.configure(
            isEnabled = activeThreeBand && effectiveProcessing,
            bassDb = if (activeThreeBand) threeBandBassDb else 0f,
            midsDb = if (activeThreeBand) threeBandMidsDb else 0f,
            trebleDb = if (activeThreeBand) threeBandTrebleDb else 0f,
            isSpeakerTarget = (outputTarget == OutputTarget.SPEAKER),
            skipHighShelf = oversampledEqEnabled,
            smartCurveEnabled = threeBandSmartCurveEnabled
        )
        oversampledUpperEqStage.configure(
            isEnabled = oversampledEqEnabled && effectiveProcessing,
            useTenBand = activeTenBand,
            useThreeBand = activeThreeBand,
            gain8kDb = gainsDb.getOrElse(8) { 0f },
            gain16kDb = gainsDb.getOrElse(9) { 0f },
            threeBandTrebleDb = threeBandTrebleDb
        )
        trebleSmoothnessProcessor.configure(
            isEnabled = effectiveProcessing && trebleSmoothnessPercent > 0.005f,
            smoothnessPercent = trebleSmoothnessPercent
        )
        harmonicSheenProcessor.configure(
            isEnabled = effectiveProcessing && harmonicSheenPercent > 0.005f,
            percent = harmonicSheenPercent
        )
        noiseFloorGate.enabled = noiseFloorGateEnabled && effectiveProcessing
        if (!noiseFloorGate.enabled) {
            noiseFloorGate.reset()
        }
        volumeContourProcessor.configure(
            isEnabled = effectiveProcessing,
            volumePercent = masterVolumePercent,
            applyVolumeScale = applyMasterVolumeGain
        )
        lateNightCompressor.configure(isEnabled = lateNightEnabled && effectiveProcessing, intensity = nightIntensity)
        val isCinemaProfile = speakerTuningProfile == SpeakerTuningProfile.CINEMA_LANDSCAPE
        speakerDspChain.configure(
            isSpeakerDspEnabled = speakerDspEnabled && effectiveProcessing,
            isVirtualBassEnabled = virtualBassEnabled && effectiveProcessing,
            isLandscapeStereoEnabled = ((landscapeStereoStage && resolvedLandscape) || isCinemaProfile) && effectiveProcessing,
            profile = speakerTuningProfile,
            isPortraitVocalFocus = landscapeStereoStage && !resolvedLandscape && !isCinemaProfile && effectiveProcessing,
            isBodyWarmthEnabled = proMaxBodyWarmthEnabled && effectiveProcessing
        )
        spatialAudioEngine.configure(
            mode = if (effectiveProcessing) spatialMode else SpatialMode.OFF,
            target = outputTarget,
            spatialDepth = spatialDepth,
            heightPresence = heightPresence,
            vocalAnchor = vocalAnchor,
            pinnaAirGainDb = trebleBudget.spatialPinnaShelfDb.toDouble(),
            vocalFocus = vocalFocus
        )
        loudnessMaximizerChain.configure(
            isEnabled = loudnessMaximizerEnabled && effectiveProcessing,
            outputTarget = outputTarget,
            boostPercent = loudnessBoostPercent
        )
        siliconDspCore.configure(
            isEnabled = siliconDspEnabled && effectiveProcessing,
            target = outputTarget,
            microAirGainDb = trebleBudget.cirrusAirShelfDb.toDouble()
        )
        adaptiveMultibandBalancer.configure(
            isEnabled = adaptiveMultibandEnabled && effectiveProcessing
        )
        speakerPhaseAlignmentFilter.configure(
            isEnabled = speakerPhaseAlignmentEnabled && effectiveProcessing,
            outputTarget = outputTarget,
            speakerDspEnabled = speakerDspEnabled
        )
        listeningModeProcessor.configure(
            mode = listeningMode,
            outputTarget = outputTarget,
            isBypassed = !effectiveProcessing,
            ambientPassThroughPercent = ambientPassThroughPercent,
            conversationSpeechActive = conversationSpeechActive
        )

        val activeBandGains = if (activeTenBand) gainsDb else List(10) { 0f }
        val threeBandAvg = if (activeThreeBand) {
            (threeBandBassDb * 0.25f + threeBandMidsDb * 0.50f + threeBandTrebleDb * 0.25f)
        } else {
            0f
        }
        val accommodationsMatchBoost = if (accommodationsEnabled && accommodationsApplyToMedia) {
            (accommodationsSoftIntensity.clarityGainDb * accommodationsSoftIntensity.parallelMix +
                accommodationsOutputGainDb) * 0.65f +
                0.8f * accommodationsSoftIntensity.eqScale
        } else {
            0f
        }
        val loudnessMatchBoost = if (loudnessMaximizerEnabled) {
            loudnessBoostPercent.coerceIn(0f, 1f) * com.example.model.LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB * 0.65f + 2.2f
        } else {
            0f
        }
        val matchDb = AudiogramCheckEngine.computeLoudnessMatchGainDb(
            bandGainsDb = activeBandGains,
            preampDb = preampDb + threeBandAvg + accommodationsMatchBoost + loudnessMatchBoost,
            lateNightEnabled = lateNightEnabled,
            nightIntensity = nightIntensity,
            target = outputTarget,
            speakerDspEnabled = speakerDspEnabled,
            spatialMode = spatialMode,
            audioMixMode = audioMixMode
        )
        abLoudnessMatchLinear = 10.0.pow(matchDb.toDouble() / 20.0)

        for (i in 0 until 10) {
            val freq = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble()
            // Flat 0 dB bypass on all 10-band biquads whenever 3-Band EQ is active;
            // when 2x Oversampled EQ is active, 8kHz (8) and 16kHz (9) run at 96 kHz in oversampledUpperEqStage.
            val processedAt2x = oversampledEqEnabled && i >= 8
            val db = if (activeTenBand && effectiveProcessing && !processedAt2x) gainsDb.getOrElse(i) { 0f }.toDouble() else 0.0
            bandFilters[i].setPeaking(sampleRate, freq, 1.414, db)
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val duck = conversationDuckLinear.coerceIn(0.15, 1.0)
        if (bypassed) {
            return (inL * duck) to (inR * duck)
        }
        // Sound Check always monitors input LUFS; when A/B Compare is held, switch cleanly to loudness-matched original
        val (scL, scR) = soundCheckNormalizer.processStereoSample(inL, inR)
        if (abCompareHeld) {
            val abL = tanh(inL * abLoudnessMatchLinear * duck * 0.92)
            val abR = tanh(inR * abLoudnessMatchLinear * duck * 0.92)
            return abL to abR
        }

        var l = scL * preampLinear
        var r = scR * preampLinear

        // Headphone Accommodations inserted in signal chain right BEFORE the main 10-band EQ
        val (accL, accR) = headphoneAccommodationsProcessor.processStereoSample(l, r)
        l = accL
        r = accR

        for (band in 0 until 10) {
            val (nextL, nextR) = bandFilters[band].processStereo(l, r)
            l = nextL
            r = nextR
        }

        val (toneL, toneR) = threeBandToneFilter.processStereoSample(l, r)
        val (osL, osR) = oversampledUpperEqStage.processStereoSample(toneL, toneR)
        val (smoothL, smoothR) = trebleSmoothnessProcessor.processStereoSample(osL, osR)
        val (sheenL, sheenR) = harmonicSheenProcessor.processStereoSample(smoothL, smoothR)
        val (mixL, mixR) = audioMixStemShaper.processStereoSample(sheenL, sheenR)
        val (isoL, isoR) = voiceIsolationProcessor.processStereoSample(mixL, mixR)
        val (compL, compR) = lateNightCompressor.processStereoSample(isoL, isoR)
        val (spatL, spatR) = spatialAudioEngine.processStereoSample(compL, compR)
        val (spkL, spkR) = speakerDspChain.processStereoSample(spatL, spatR)
        val (phaseL, phaseR) = speakerPhaseAlignmentFilter.processStereoSample(spkL, spkR)
        val (maxL, maxR) = loudnessMaximizerChain.processStereoSample(phaseL, phaseR)
        val (mbL, mbR) = adaptiveMultibandBalancer.processStereoSample(maxL, maxR)
        val (silL, silR) = siliconDspCore.processStereoSample(mbL, mbR)
        val (lmL, lmR) = listeningModeProcessor.processMusicSample(silL, silR)
        val (volL, volR) = volumeContourProcessor.processStereoSample(lmL, lmR)

        // Final safety ceiling + Conversation Awareness auto-ducking -> Zero-Noise Gate (-75 dBFS) + TPDF Dither
        val (safeL, safeR) = if (loudnessMaximizerChain.enabled || speakerDspChain.speakerDspEnabled || siliconDspCore.enabled) {
            (volL * duck).coerceIn(-0.99, 0.99) to (volR * duck).coerceIn(-0.99, 0.99)
        } else {
            tanh(volL * duck * 0.92) to tanh(volR * duck * 0.92)
        }
        return noiseFloorGate.processStereoSample(safeL, safeR)
    }
}

/**
 * Built-in real-time 48 kHz audio synthesizer so users can audition and visualize
 * Speaker DSP, Silicon DSP Core, Headphones, Bluetooth EQ tuning, Spatial Atmos,
 * Late Night compression, Sound Check, Voice Isolation & Background Sounds immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestAudioSynthesizer {
    private val sampleRate = com.example.model.StudioHiResSpecs.SAMPLE_RATE_HZ
    private val filterBank = BiquadEqFilterBank(sampleRate)
    private val backgroundSynth = BackgroundSoundsSynthesizer(sampleRate)
    private val hwScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val synthScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconcileSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var playJob: Job? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var desiredSource: TestAudioSource = TestAudioSource.OFF

    private val _currentSource = MutableStateFlow(TestAudioSource.OFF)
    val currentSource: StateFlow<TestAudioSource> = _currentSource.asStateFlow()

    private val _visualizerFrame = MutableStateFlow(VisualizerFrame())
    val visualizerFrame: StateFlow<VisualizerFrame> = _visualizerFrame.asStateFlow()

    @Volatile
    var audioSessionId: Int = 0
        private set

    init {
        hwScope.launch {
            for (signal in reconcileSignal) {
                reconcilePlayback()
            }
        }
    }

    @Volatile
    private var backgroundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF

    @Volatile
    private var backgroundVolume: Float = 0.28f

    val latestInputLufs: Float
        get() = filterBank.latestInputLufs

    val latestSoundCheckTrimDb: Float
        get() = filterBank.latestSoundCheckTrimDb

    val latestSoundCheckCrestDb: Float
        get() = filterBank.latestSoundCheckCrestDb

    val latestMultibandLowGrDb: Float
        get() = filterBank.latestMultibandLowGrDb

    val latestMultibandMidGrDb: Float
        get() = filterBank.latestMultibandMidGrDb

    val latestMultibandHighGrDb: Float
        get() = filterBank.latestMultibandHighGrDb

    val isPhaseAlignmentActive: Boolean
        get() = filterBank.isPhaseAlignmentActive

    val latestVoiceGateDb: Float
        get() = filterBank.latestVoiceGateDb

    val latestAccommodationsGrDb: Float
        get() = filterBank.latestAccommodationsGrDb

    val latestLoudnessRmsGrDb: Float
        get() = filterBank.latestLoudnessRmsGrDb

    val latestLoudnessBrickwallGrDb: Float
        get() = filterBank.latestLoudnessBrickwallGrDb

    val isLoudnessAntiClipActive: Boolean
        get() = filterBank.isLoudnessAntiClipActive

    val latestSiliconExcursionPct: Float
        get() = filterBank.latestSiliconExcursionPct

    val latestSiliconSvfCutoffHz: Float
        get() = filterBank.latestSiliconSvfCutoffHz

    val latestSiliconCoilTempC: Float
        get() = filterBank.latestSiliconCoilTempC

    val latestSiliconClassHRailV: Float
        get() = filterBank.latestSiliconClassHRailV

    val latestSiliconTruePeakGrDb: Float
        get() = filterBank.latestSiliconTruePeakGrDb

    val isTrebleCapActive: Boolean
        get() = filterBank.isTrebleCapActive

    val latestTrebleCappedBoostDb: Float
        get() = filterBank.latestTrebleCappedBoostDb

    val latestResolvedOrientationLandscape: Boolean
        get() = filterBank.latestResolvedOrientationLandscape

    val isHighVolumeDeHarshActive: Boolean
        get() = filterBank.isHighVolumeDeHarshActive

    val latestVolumeContourStatus: String
        get() = filterBank.latestVolumeContourStatus

    val isOversampledEqActive: Boolean
        get() = filterBank.isOversampledEqActive

    val latestHarmonicSheenThdPct: Float
        get() = filterBank.latestHarmonicSheenThdPct

    val isNoiseFloorGateMuted: Boolean
        get() = filterBank.isNoiseFloorGateMuted

    fun setConversationDuckGain(duckGainLinear: Float, speechDetected: Boolean = false) {
        filterBank.conversationDuckLinear = duckGainLinear.toDouble()
        filterBank.conversationSpeechActive = speechDetected
    }

    fun updateEq(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean,
        nightIntensity: NightIntensity,
        speakerDspEnabled: Boolean,
        virtualBassEnabled: Boolean,
        landscapeStereoStage: Boolean = false,
        speakerTuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
        audioMixMode: AudioMixMode = AudioMixMode.STANDARD,
        outputTarget: OutputTarget,
        spatialMode: SpatialMode,
        spatialDepth: Float,
        heightPresence: Float,
        vocalAnchor: Boolean,
        vocalFocus: Float = 0.70f,
        soundCheckEnabled: Boolean = false,
        voiceIsolationEnabled: Boolean = false,
        abCompareHeld: Boolean = false,
        bgSoundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF,
        bgSoundVolume: Float = 0.28f,
        tenBandEnabled: Boolean = true,
        threeBandEnabled: Boolean = false,
        threeBandBassDb: Float = 0f,
        threeBandMidsDb: Float = 0f,
        threeBandTrebleDb: Float = 0f,
        threeBandSmartCurveEnabled: Boolean = true,
        accommodationsEnabled: Boolean = false,
        accommodationsToneProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
        accommodationsSoftIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE,
        accommodationsApplyToMedia: Boolean = true,
        accommodationsOutputGainDb: Float = 0f,
        loudnessMaximizerEnabled: Boolean = false,
        loudnessBoostPercent: Float = 0.50f,
        siliconDspEnabled: Boolean = false,
        trebleSmoothnessPercent: Float = 0f,
        phoneOrientationMode: PhoneOrientationMode = PhoneOrientationMode.AUTO_DETECT,
        deviceIsLandscape: Boolean = false,
        masterVolumePercent: Float = 0.75f,
        proMaxBodyWarmthEnabled: Boolean = false,
        adaptiveMultibandEnabled: Boolean = false,
        speakerPhaseAlignmentEnabled: Boolean = false,
        oversampledEqEnabled: Boolean = false,
        harmonicSheenPercent: Float = 0f,
        noiseFloorGateEnabled: Boolean = false,
        listeningMode: ListeningMode = ListeningMode.OFF,
        ambientPassThroughPercent: Float = 0.65f
    ) {
        backgroundPreset = bgSoundPreset
        backgroundVolume = bgSoundVolume
        filterBank.updateParameters(
            gainsDb = gainsDb,
            preampDb = preampDb,
            isBypassed = isBypassed,
            lateNightEnabled = lateNightEnabled,
            nightIntensity = nightIntensity,
            speakerDspEnabled = speakerDspEnabled,
            virtualBassEnabled = virtualBassEnabled,
            landscapeStereoStage = landscapeStereoStage,
            speakerTuningProfile = speakerTuningProfile,
            audioMixMode = audioMixMode,
            outputTarget = outputTarget,
            spatialMode = spatialMode,
            spatialDepth = spatialDepth,
            heightPresence = heightPresence,
            vocalAnchor = vocalAnchor,
            vocalFocus = vocalFocus,
            soundCheckEnabled = soundCheckEnabled,
            voiceIsolationEnabled = voiceIsolationEnabled,
            abCompareHeld = abCompareHeld,
            threeBandEnabled = threeBandEnabled,
            tenBandEnabled = tenBandEnabled,
            threeBandBassDb = threeBandBassDb,
            threeBandMidsDb = threeBandMidsDb,
            threeBandTrebleDb = threeBandTrebleDb,
            threeBandSmartCurveEnabled = threeBandSmartCurveEnabled,
            accommodationsEnabled = accommodationsEnabled,
            accommodationsToneProfile = accommodationsToneProfile,
            accommodationsSoftIntensity = accommodationsSoftIntensity,
            accommodationsApplyToMedia = accommodationsApplyToMedia,
            accommodationsOutputGainDb = accommodationsOutputGainDb,
            loudnessMaximizerEnabled = loudnessMaximizerEnabled,
            loudnessBoostPercent = loudnessBoostPercent,
            siliconDspEnabled = siliconDspEnabled,
            trebleSmoothnessPercent = trebleSmoothnessPercent,
            phoneOrientationMode = phoneOrientationMode,
            deviceIsLandscape = deviceIsLandscape,
            masterVolumePercent = masterVolumePercent,
            applyMasterVolumeGain = true,
            proMaxBodyWarmthEnabled = proMaxBodyWarmthEnabled,
            adaptiveMultibandEnabled = adaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = speakerPhaseAlignmentEnabled,
            oversampledEqEnabled = oversampledEqEnabled,
            harmonicSheenPercent = harmonicSheenPercent,
            noiseFloorGateEnabled = noiseFloorGateEnabled,
            listeningMode = listeningMode,
            ambientPassThroughPercent = ambientPassThroughPercent
        )
    }

    fun setSource(source: TestAudioSource) {
        if (desiredSource == source && _currentSource.value == source) return
        desiredSource = source
        _currentSource.value = source
        if (source == TestAudioSource.OFF) {
            _visualizerFrame.value = VisualizerFrame()
        }
        reconcileSignal.trySend(Unit)
    }

    fun stop() {
        desiredSource = TestAudioSource.OFF
        _currentSource.value = TestAudioSource.OFF
        _visualizerFrame.value = VisualizerFrame()
        reconcileSignal.trySend(Unit)
    }

    private suspend fun reconcilePlayback() {
        stopBlocking()
        val targetSource = desiredSource
        if (targetSource != TestAudioSource.OFF) {
            startPlayback(targetSource)
        }
    }

    private suspend fun stopBlocking() {
        val job = playJob
        playJob = null
        val track = audioTrack
        audioTrack = null
        audioSessionId = 0
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Throwable) {
        }
        try {
            job?.cancelAndJoin()
        } catch (_: Throwable) {
        }
    }

    private fun startPlayback(source: TestAudioSource) {
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
                .setBufferSizeInBytes(minBuf * com.example.model.StudioHiResSpecs.PLAYBACK_BUFFER_MULTIPLIER)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                    }
                }
                .build()
        } catch (_: Throwable) {
            _currentSource.value = TestAudioSource.OFF
            desiredSource = TestAudioSource.OFF
            return
        }

        if (desiredSource != source) {
            try {
                track.release()
            } catch (_: Throwable) {
            }
            return
        }

        audioTrack = track
        audioSessionId = track.audioSessionId
        try {
            track.play()
        } catch (_: Throwable) {
            try {
                track.release()
            } catch (_: Throwable) {
            }
            audioTrack = null
            audioSessionId = 0
            _currentSource.value = TestAudioSource.OFF
            desiredSource = TestAudioSource.OFF
            return
        }

        playJob = synthScope.launch {
            // 48,000 Hz / 768 samples = 62.5 fps telemetry & audio cadence (exact 16ms blocks)
            val frameCount = 768
            val pcmBuffer = ShortArray(frameCount * 2)
            val monoAnalysis = DoubleArray(512)
            var sampleCursor = 0L

            val pinkRows = DoubleArray(16)
            var pinkRunningSum = 0.0
            var pinkCounter = 0

            while (isActive) {
                var peak = 0.0
                for (i in 0 until frameCount) {
                    val t = sampleCursor.toDouble() / sampleRate
                    val (rawL, rawR) = when (source) {
                        TestAudioSource.AMBIENT_CHORD -> {
                            val swell = 0.45 + 0.55 * abs(sin(2.0 * PI * 0.28 * t))
                            val sub = sin(2.0 * PI * 55.0 * t) * 0.24
                            val bass = sin(2.0 * PI * 110.0 * t) * 0.22
                            val lowMid = sin(2.0 * PI * 220.0 * t) * 0.16
                            val mid1 = sin(2.0 * PI * 329.63 * t) * 0.14
                            val mid2 = sin(2.0 * PI * 440.0 * t) * 0.12
                            val pres = sin(2.0 * PI * 1318.5 * t) * 0.08
                            val air1 = sin(2.0 * PI * 3520.0 * t) * 0.05
                            val air2 = sin(2.0 * PI * 7040.0 * t) * 0.04
                            val air3 = sin(2.0 * PI * 12000.0 * t) * 0.025
                            val stereoSpread = sin(2.0 * PI * 0.7 * t) * 0.04
                            val base = (sub + bass + lowMid + mid1 + mid2 + pres + air1 + air2 + air3) * swell
                            (base + stereoSpread) to (base - stereoSpread)
                        }

                        TestAudioSource.BASS_GROOVE -> {
                            val beatPos = (t * 2.0) % 1.0
                            val kickEnv = exp(-beatPos * 11.0)
                            val kickFreq = 42.0 + 110.0 * exp(-beatPos * 26.0)
                            val kick = sin(2.0 * PI * kickFreq * beatPos) * kickEnv * 0.50

                            val bassNote = if (((t * 2.0).toInt() % 4) < 2) 65.41 else 82.41
                            val bassWave = (sin(2.0 * PI * bassNote * t) + 0.4 * sin(2.0 * PI * bassNote * 2.0 * t)) * 0.22

                            val hatPos = (t * 4.0) % 1.0
                            val hatEnv = exp(-hatPos * 35.0)
                            val white = (Math.random() * 2.0 - 1.0)
                            val hat = white * hatEnv * 0.15

                            val pad = sin(2.0 * PI * 523.25 * t) * 0.07 + sin(2.0 * PI * 1568.0 * t) * 0.04
                            val l = (kick + bassWave + hat + pad) * 0.85
                            val r = (kick + bassWave + hat * 0.85 + pad * 1.1) * 0.85
                            l to r
                        }

                        TestAudioSource.PINK_NOISE -> {
                            pinkCounter = (pinkCounter + 1) and 0xFFFF
                            var idx = 0
                            var k = pinkCounter
                            while (idx < 16 && (k and 1) == 0) {
                                k = k shr 1
                                idx++
                            }
                            if (idx < 16) {
                                pinkRunningSum -= pinkRows[idx]
                                val newVal = Math.random() * 2.0 - 1.0
                                pinkRows[idx] = newVal
                                pinkRunningSum += newVal
                            }
                            val white = Math.random() * 2.0 - 1.0
                            val pink = ((pinkRunningSum + white) / 17.0) * 0.75
                            pink to pink
                        }

                        TestAudioSource.OFF -> 0.0 to 0.0
                    }

                    val (procL, procR) = filterBank.processStereoSample(rawL, rawR)
                    val (bgL, bgR) = backgroundSynth.nextStereoSample(backgroundPreset, backgroundVolume)
                    val mixedL = procL + bgL * filterBank.conversationDuckLinear
                    val mixedR = procR + bgR * filterBank.conversationDuckLinear
                    val clampedL = mixedL.coerceIn(-1.0, 1.0)
                    val clampedR = mixedR.coerceIn(-1.0, 1.0)
                    pcmBuffer[i * 2] = filterBank.quantize16BitPcm(clampedL, isLeft = true)
                    pcmBuffer[i * 2 + 1] = filterBank.quantize16BitPcm(clampedR, isLeft = false)

                    val mono = (clampedL + clampedR) * 0.5
                    if (i < 512) monoAnalysis[i] = mono
                    val absMono = abs(mono)
                    if (absMono > peak) peak = absMono
                    sampleCursor++
                }

                try {
                    track.write(pcmBuffer, 0, pcmBuffer.size)
                } catch (_: Throwable) {
                    break
                }

                val spectrum = computeLogSpectrum(monoAnalysis)
                val waveform = List(96) { idx ->
                    val srcIdx = (idx * 512) / 96
                    monoAnalysis[srcIdx].toFloat().coerceIn(-1f, 1f)
                }
                val peakDb = if (peak > 1e-4) (20.0 * log10(peak)).toFloat().coerceIn(-60f, 0f) else -60f

                _visualizerFrame.value = VisualizerFrame(
                    spectrumBins = spectrum,
                    waveformSamples = waveform,
                    peakDb = peakDb,
                    gainReductionDb = filterBank.latestGainReductionDb,
                    isSignalLive = true
                )
            }
        }
    }

    private fun computeLogSpectrum(samples: DoubleArray): List<Float> {
        val n = samples.size
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = samples[i] * w
        }
        fftInPlace(real, imag)

        val mags = DoubleArray(n / 2)
        for (k in 0 until n / 2) {
            mags[k] = hypot(real[k], imag[k]) / (n * 0.25)
        }

        val bars = MutableList(32) { 0f }
        val minFreq = 30.0
        val maxFreq = 18000.0
        for (b in 0 until 32) {
            val fLow = minFreq * (maxFreq / minFreq).pow(b / 32.0)
            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1) / 32.0)
            val binLow = ((fLow * n) / sampleRate).toInt().coerceIn(1, n / 2 - 1)
            val binHigh = ((fHigh * n) / sampleRate).toInt().coerceIn(binLow, n / 2 - 1)

            var maxMag = 0.0
            for (k in binLow..binHigh) {
                if (mags[k] > maxMag) maxMag = mags[k]
            }
            val db = if (maxMag > 1e-5) 20.0 * log10(maxMag) else -60.0
            bars[b] = ((db + 54.0) / 54.0).toFloat().coerceIn(0f, 1f)
        }
        return bars
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenR = cos(ang)
            val wLenI = sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
