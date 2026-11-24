package com.example.audio

import com.example.model.AtmosAsarSpecs
import com.example.model.OutputTarget
import com.example.model.SpatialMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True 3D Diffuse-Field HRTF Virtual Speaker Panner modeling Woodworth spherical-head
 * Interaural Time Difference (ITD) and Brown-Duda frequency-dependent acoustic head-shadowing ILD filters.
 */
class HrtfSpeakerPanner(
    private val sampleRate: Int,
    azimuthDeg: Float,
    elevationDeg: Float
) {
    private val bufferSize = 128 // >2.6ms at 48kHz (max human ITD ~0.68ms)
    private val mask = bufferSize - 1
    private val delayLine = DoubleArray(bufferSize)
    private var writeIndex = 0

    private val leftDelaySamples: Double
    private val rightDelaySamples: Double
    private val leftGain: Double
    private val rightGain: Double

    // 1-pole head-shadowing shelf filters per ear (contralateral high attenuation, ipsilateral pinna lift)
    private val leftShadowA1: Double
    private val leftShadowB0: Double
    private val leftShadowB1: Double
    private var leftZ1X = 0.0
    private var leftZ1Y = 0.0

    private val rightShadowA1: Double
    private val rightShadowB0: Double
    private val rightShadowB1: Double
    private var rightZ1X = 0.0
    private var rightZ1Y = 0.0

    init {
        val azRad = Math.toRadians(azimuthDeg.toDouble())
        val elRad = Math.toRadians(elevationDeg.toDouble())
        val headRadiusM = 0.0875
        val speedOfSound = 343.0

        // Woodworth spherical-head ITD scaled by cos(elevation)
        val absAz = abs(azRad)
        val itdSec = (headRadiusM / speedOfSound) * (sin(absAz) + absAz) * cos(elRad)
        val itdSamples = (itdSec * sampleRate).coerceIn(0.0, (bufferSize - 4).toDouble())

        if (azimuthDeg >= 0f) {
            // Positive azimuth = listener's right ear is closer (ipsilateral)
            rightDelaySamples = 0.0
            leftDelaySamples = itdSamples
        } else {
            // Negative azimuth = listener's left ear is closer (ipsilateral)
            leftDelaySamples = 0.0
            rightDelaySamples = itdSamples
        }

        // Spherical-head ILD + elevation diffuse-field compensation
        val lateralWeight = sin(azRad) * cos(elRad) // -1 (far left) .. +1 (far right)
        val ipsiGain = 1.04
        val contraGain = (1.0 - 0.38 * abs(lateralWeight)).coerceIn(0.55, 1.0)
        if (azimuthDeg >= 0f) {
            rightGain = ipsiGain
            leftGain = contraGain
        } else {
            leftGain = ipsiGain
            rightGain = contraGain
        }

        val lCoeffs = computeShadowCoeffs(angleIncidence = -lateralWeight)
        leftShadowB0 = lCoeffs[0]
        leftShadowB1 = lCoeffs[1]
        leftShadowA1 = lCoeffs[2]

        val rCoeffs = computeShadowCoeffs(angleIncidence = lateralWeight)
        rightShadowB0 = rCoeffs[0]
        rightShadowB1 = rCoeffs[1]
        rightShadowA1 = rCoeffs[2]
    }

    private fun computeShadowCoeffs(angleIncidence: Double): DoubleArray {
        val alphaMin = 0.14
        val thetaNorm = ((1.0 - angleIncidence) * 0.5).coerceIn(0.0, 1.0)
        val alpha = 1.0 + alphaMin + (1.0 - alphaMin) * cos(thetaNorm * PI)
        val w0 = 2.0 * PI * 3200.0 / sampleRate
        val norm = 1.0 + 1.0 / w0
        val b0 = (alpha + 1.0 / w0) / norm
        val b1 = (alpha - 1.0 / w0) / norm
        val a1 = (1.0 - 1.0 / w0) / norm
        return doubleArrayOf(b0, b1, a1)
    }

    private fun readInterpolated(delaySamples: Double): Double {
        val intDelay = delaySamples.toInt()
        val frac = delaySamples - intDelay
        val idx0 = (writeIndex - intDelay) and mask
        val idx1 = (writeIndex - intDelay - 1) and mask
        return delayLine[idx0] * (1.0 - frac) + delayLine[idx1] * frac
    }

    fun processMonoToBinaural(monoIn: Double): Pair<Double, Double> {
        delayLine[writeIndex] = monoIn

        val delayedL = readInterpolated(leftDelaySamples) * leftGain
        val delayedR = readInterpolated(rightDelaySamples) * rightGain

        writeIndex = (writeIndex + 1) and mask

        val outL = leftShadowB0 * delayedL + leftShadowB1 * leftZ1X - leftShadowA1 * leftZ1Y
        leftZ1X = delayedL
        leftZ1Y = outL

        val outR = rightShadowB0 * delayedR + rightShadowB1 * rightZ1X - rightShadowA1 * rightZ1Y
        rightZ1X = delayedR
        rightZ1Y = outR

        return outL to outR
    }
}

/**
 * Procedural 18ms Studio Early-Reflection Impulse Convolver:
 * - Generates an exact 18ms exponentially decaying stereo early-reflection impulse
 * - Low-pass damped above 3.2kHz and high-pass guarded above 150Hz
 * - Mixed at 8% wet on the diffuse spatial component ONLY (never on dry <150Hz bass or 1–3.5kHz center vocals)
 */
class AppleStudioMicroAmbienceConvolver(private val sampleRate: Int = 48000) {

    val irLengthSamples: Int = (sampleRate * AtmosAsarSpecs.STUDIO_IR_SEC).roundToInt().coerceAtLeast(32)
    val irSamplesL: FloatArray
    val irSamplesR: FloatArray

    private val tapOffsetsL: IntArray
    private val tapGainsL: DoubleArray
    private val tapOffsetsR: IntArray
    private val tapGainsR: DoubleArray

    private val histSize = Integer.highestOneBit(irLengthSamples * 2 - 1).shl(1)
    private val histMask = histSize - 1
    private val histL = DoubleArray(histSize)
    private val histR = DoubleArray(histSize)
    private var writeIdx = 0

    private val dampLpfL = StereoBiquad().apply { setLowPass(sampleRate, AtmosAsarSpecs.STUDIO_IR_LPF_HZ, 0.7071) }
    private val dampLpfR = StereoBiquad().apply { setLowPass(sampleRate, AtmosAsarSpecs.STUDIO_IR_LPF_HZ, 0.7071) }
    private val subBassCutL = StereoBiquad().apply { setHighPass(sampleRate, AtmosAsarSpecs.DRY_SUB_BASS_CUTOFF_HZ, 0.7071) }
    private val subBassCutR = StereoBiquad().apply { setHighPass(sampleRate, AtmosAsarSpecs.DRY_SUB_BASS_CUTOFF_HZ, 0.7071) }

    init {
        val bundle = buildEarlyReflectionIr(sampleRate, irLengthSamples)
        irSamplesL = bundle.irL
        irSamplesR = bundle.irR
        tapOffsetsL = bundle.offL
        tapGainsL = bundle.gainL
        tapOffsetsR = bundle.offR
        tapGainsR = bundle.gainR
    }

    fun processStereo(
        inL: Double,
        inR: Double,
        wetMix: Double = AtmosAsarSpecs.STUDIO_IR_WET_MIX
    ): Pair<Double, Double> {
        histL[writeIdx] = inL
        histR[writeIdx] = inR

        var convL = 0.0
        for (i in tapOffsetsL.indices) {
            val idx = (writeIdx - tapOffsetsL[i]) and histMask
            convL += histL[idx] * tapGainsL[i]
        }

        var convR = 0.0
        for (i in tapOffsetsR.indices) {
            val idx = (writeIdx - tapOffsetsR[i]) and histMask
            convR += histR[idx] * tapGainsR[i]
        }

        writeIdx = (writeIdx + 1) and histMask

        // Damped above 3.2kHz and <150Hz sub-bass clean
        val (dampedL, _) = dampLpfL.processStereo(convL, 0.0)
        val (_, dampedR) = dampLpfR.processStereo(0.0, convR)
        val (cleanWetL, _) = subBassCutL.processStereo(dampedL, 0.0)
        val (_, cleanWetR) = subBassCutR.processStereo(0.0, dampedR)

        return (inL + cleanWetL * wetMix) to (inR + cleanWetR * wetMix)
    }

    private data class IrBundle(
        val irL: FloatArray,
        val irR: FloatArray,
        val offL: IntArray,
        val gainL: DoubleArray,
        val offR: IntArray,
        val gainR: DoubleArray
    )

    companion object {
        /**
         * Builds a strictly 18ms stereo early-reflection impulse response with rapid decay
         * and 3.2kHz low-pass damping for natural studio room depth with zero bathroom reverb.
         */
        fun generateStudioMicroAmbienceIr(sampleRate: Int = 48000): Pair<FloatArray, FloatArray> {
            val len = (sampleRate * AtmosAsarSpecs.STUDIO_IR_SEC).roundToInt().coerceAtLeast(32)
            val bundle = buildEarlyReflectionIr(sampleRate, len)
            return bundle.irL to bundle.irR
        }

        private fun buildEarlyReflectionIr(sampleRate: Int, lengthSamples: Int): IrBundle {
            val rawL = DoubleArray(lengthSamples)
            val rawR = DoubleArray(lengthSamples)

            val tapCount = 64
            val offsetsL = IntArray(tapCount)
            val offsetsR = IntArray(tapCount)
            val gainsL = DoubleArray(tapCount)
            val gainsR = DoubleArray(tapCount)

            val tauSec = 0.0036 // Decays smoothly to ~-43 dB by 18ms -> zero bathroom reverb
            var seed = 0x5A17C0DE

            fun nextRandSigned(): Double {
                seed = seed * 1664525 + 1013904223
                val u = ((seed ushr 8) and 0xFFFF) / 65535.0
                return u * 2.0 - 1.0
            }

            // Velvet-noise distributed studio early reflections spanning 0.4ms .. 18ms
            val minStart = (sampleRate * 0.0004).toInt().coerceAtLeast(1)
            val span = (lengthSamples - 1 - minStart).coerceAtLeast(tapCount)
            var energySum = 0.0

            for (i in 0 until tapCount) {
                val frac = i.toDouble() / tapCount
                val baseIdx = minStart + (frac.pow(1.12) * span).toInt()
                val jitterL = (abs(nextRandSigned()) * 3.0).toInt()
                val jitterR = (abs(nextRandSigned()) * 3.0).toInt()

                val idxL = (baseIdx + jitterL).coerceIn(1, lengthSamples - 1)
                val idxR = (baseIdx + jitterR + 2).coerceIn(1, lengthSamples - 1)

                val tL = idxL.toDouble() / sampleRate
                val tR = idxR.toDouble() / sampleRate

                val ampL = exp(-tL / tauSec) * (0.65 + 0.35 * abs(nextRandSigned())) *
                    if (nextRandSigned() >= 0) 1.0 else -1.0
                val ampR = exp(-tR / tauSec) * (0.65 + 0.35 * abs(nextRandSigned())) *
                    if (nextRandSigned() >= 0) 1.0 else -1.0

                offsetsL[i] = idxL
                offsetsR[i] = idxR
                gainsL[i] = ampL
                gainsR[i] = ampR

                rawL[idxL] += ampL
                rawR[idxR] += ampR
                energySum += ampL * ampL + ampR * ampR
            }

            val norm = 1.0 / sqrt((energySum * 0.5).coerceAtLeast(1e-6))
            for (i in 0 until tapCount) {
                gainsL[i] *= norm
                gainsR[i] *= norm
            }

            // Bake 3.2kHz low-pass damping into the exported FloatArray IR as well
            val lpf = StereoBiquad().apply { setLowPass(sampleRate, AtmosAsarSpecs.STUDIO_IR_LPF_HZ, 0.7071) }
            val irOutL = FloatArray(lengthSamples)
            val irOutR = FloatArray(lengthSamples)
            for (n in 0 until lengthSamples) {
                val (fL, fR) = lpf.processStereo(rawL[n] * norm, rawR[n] * norm)
                irOutL[n] = fL.toFloat()
                irOutR[n] = fR.toFloat()
            }

            return IrBundle(irOutL, irOutR, offsetsL, gainsL, offsetsR, gainsR)
        }
    }
}

/**
 * iPhone 18 Pro Max Built-In Speaker 2nd-Order Transaural Acoustic Crosstalk Cancellation (XTC):
 * Operates strictly on the Side channel (>150Hz):
 * - Primary tap: -26% phase-inverted crossfeed delayed by 0.17ms, low-passed at 4.5kHz (head-shadowing)
 * - Secondary tap: +12% secondary bounce delayed by 0.34ms
 * Keeps center vocals (1–3.5kHz) and sub-bass (<150Hz) 100% dry with zero hollow phase cancellation.
 */
class AcousticCrosstalkCanceller(private val sampleRate: Int = 48000) {
    private val primaryDelaySamples: Double = AtmosAsarSpecs.XTC_PRIMARY_DELAY_SEC * sampleRate
    private val secondaryDelaySamples: Double = AtmosAsarSpecs.XTC_SECONDARY_DELAY_SEC * sampleRate

    private val bufSize = 128
    private val mask = bufSize - 1
    private val ringL = DoubleArray(bufSize)
    private val ringR = DoubleArray(bufSize)
    private val shadowRingL = DoubleArray(bufSize)
    private val shadowRingR = DoubleArray(bufSize)
    private var writeIdx = 0

    // 4.5kHz physical head-shadowing low-pass on primary crossfeed path
    private val headShadowLpf = StereoBiquad().apply {
        setLowPass(sampleRate, AtmosAsarSpecs.XTC_PRIMARY_LPF_HZ, 0.7071)
    }

    private fun readDelayed(ring: DoubleArray, delaySamples: Double): Double {
        val intD = delaySamples.toInt()
        val frac = delaySamples - intD
        val idx0 = (writeIdx - intD) and mask
        val idx1 = (writeIdx - intD - 1) and mask
        return ring[idx0] * (1.0 - frac) + ring[idx1] * frac
    }

    fun processSideChannel(
        sideAbove150: Double,
        intensityScale: Double = 1.0
    ): Pair<Double, Double> {
        val sL = sideAbove150 * intensityScale
        val sR = -sideAbove150 * intensityScale

        val (shadowedL, shadowedR) = headShadowLpf.processStereo(sL, sR)

        ringL[writeIdx] = sL
        ringR[writeIdx] = sR
        shadowRingL[writeIdx] = shadowedL
        shadowRingR[writeIdx] = shadowedR

        // 1st-order cancellation: -26% opposite ear delayed 0.17ms (low-passed @ 4.5kHz)
        val cross1FromR = readDelayed(shadowRingR, primaryDelaySamples)
        val cross1FromL = readDelayed(shadowRingL, primaryDelaySamples)

        // 2nd-order bounce: +12% delayed 0.34ms
        val bounce2L = readDelayed(ringL, secondaryDelaySamples)
        val bounce2R = readDelayed(ringR, secondaryDelaySamples)

        writeIdx = (writeIdx + 1) and mask

        val outSideL = sL -
            AtmosAsarSpecs.XTC_PRIMARY_GAIN * cross1FromR +
            AtmosAsarSpecs.XTC_SECONDARY_GAIN * bounce2L
        val outSideR = sR -
            AtmosAsarSpecs.XTC_PRIMARY_GAIN * cross1FromL +
            AtmosAsarSpecs.XTC_SECONDARY_GAIN * bounce2R

        return outSideL to outSideR
    }
}

/**
 * Apple Spatial Audio Renderer (ASAR) for iPhone 18 Pro Max (Speakers & Headphones):
 * 1. Dry Center Vocal & Sub-Bass Lock (Zero Hollow Echo):
 *    - Extracts Mid (L+R)*0.5 and keeps <150Hz sub-bass and 1–3.5kHz primary vocal formants 100% dry
 *      (bypassing all spatial reverb and delay taps), modulated by Vocal Focus (0%–100%).
 * 2. Built-In Speaker Atmos (2nd-Order Transaural XTC):
 *    - Applies -26% @ 0.17ms (4.5kHz LPF) + +12% @ 0.34ms on Side (>150Hz) to project wide surround cues.
 * 3. Headphone 7.1.4 Binaural Elevation (Diffuse-Field HRTF):
 *    - Routes Side (>150Hz) into Surround L/R (±110°, 0°) + Top Height L/R (±60°, +50° @ 2.8kHz HPF)
 *      with authentic Pinna elevation filter (6.8kHz notch -3.0dB + 11.8kHz air lift +2.5dB, gain-staged).
 *    - Convolves ONLY the diffuse spatial component with a procedural 18ms Studio Early-Reflection IR
 *      (3.2kHz LPF, 8% wet mix).
 */
class SpatialAudioEngine(sampleRate: Int = 48000) {

    // Headphone 7.1.4 Virtual Speaker Diffuse-Field HRTF Panners
    private val leftSurround = HrtfSpeakerPanner(
        sampleRate = sampleRate,
        azimuthDeg = -AtmosAsarSpecs.SURROUND_AZIMUTH_DEG,
        elevationDeg = AtmosAsarSpecs.SURROUND_ELEVATION_DEG
    )
    private val rightSurround = HrtfSpeakerPanner(
        sampleRate = sampleRate,
        azimuthDeg = AtmosAsarSpecs.SURROUND_AZIMUTH_DEG,
        elevationDeg = AtmosAsarSpecs.SURROUND_ELEVATION_DEG
    )
    private val leftTopHeight = HrtfSpeakerPanner(
        sampleRate = sampleRate,
        azimuthDeg = -AtmosAsarSpecs.HEIGHT_AZIMUTH_DEG,
        elevationDeg = AtmosAsarSpecs.HEIGHT_ELEVATION_DEG
    )
    private val rightTopHeight = HrtfSpeakerPanner(
        sampleRate = sampleRate,
        azimuthDeg = AtmosAsarSpecs.HEIGHT_AZIMUTH_DEG,
        elevationDeg = AtmosAsarSpecs.HEIGHT_ELEVATION_DEG
    )

    // <150Hz strict dry mono sub-bass protection on Side channel (LR4 24dB/oct @ 150Hz)
    private val sideHpf150A = StereoBiquad().apply { setHighPass(sampleRate, AtmosAsarSpecs.DRY_SUB_BASS_CUTOFF_HZ, 0.7071) }
    private val sideHpf150B = StereoBiquad().apply { setHighPass(sampleRate, AtmosAsarSpecs.DRY_SUB_BASS_CUTOFF_HZ, 0.7071) }

    // 1kHz–3.5kHz dry center vocal formant focus peaking filter (geometric center 1870Hz, Q=0.75, 100% dry)
    private val dryVocalFocusEq = StereoBiquad().apply {
        setPeaking(sampleRate, 1870.0, 0.75, 1.45)
    }

    // Top Height channels high-passed at 2.8kHz
    private val heightHighPass2800 = StereoBiquad().apply { setHighPass(sampleRate, AtmosAsarSpecs.HEIGHT_HPF_HZ, 0.7071) }

    // Pinna Elevation Filter on Top Height channels: 6.8kHz notch -3.0dB + 11.8kHz air lift +2.5dB (budget-capped)
    private val pinnaNotch6800 = StereoBiquad().apply {
        setPeaking(sampleRate, AtmosAsarSpecs.PINNA_NOTCH_HZ, AtmosAsarSpecs.PINNA_NOTCH_Q, AtmosAsarSpecs.PINNA_NOTCH_DB)
    }
    private val pinnaAirShelf11800 = StereoBiquad().apply {
        setHighShelf(sampleRate, AtmosAsarSpecs.PINNA_AIR_HZ, 0.7071, AtmosAsarSpecs.PINNA_AIR_DB.toDouble())
    }

    // Procedural 18ms Studio Early-Reflection Impulse (3.2kHz LPF damped, 8% wet mix on diffuse component only)
    val studioConvolver = AppleStudioMicroAmbienceConvolver(sampleRate)

    // iPhone 18 Pro Max Built-In Speaker 2nd-Order Transaural XTC
    private val speakerCrosstalkCanceller = AcousticCrosstalkCanceller(sampleRate)

    @Volatile
    var mode: SpatialMode = SpatialMode.OFF
        private set

    @Volatile
    var outputTarget: OutputTarget = OutputTarget.HEADPHONES
        private set

    @Volatile
    var spatialDepth: Float = SpatialMode.ATMOS_MUSIC.defaultDepth
        private set

    @Volatile
    var heightPresence: Float = SpatialMode.ATMOS_MUSIC.defaultHeight
        private set

    @Volatile
    var vocalAnchor: Boolean = true
        private set

    @Volatile
    var vocalFocus: Float = 0.70f
        private set

    private val sampleRateRef: Int = sampleRate

    fun configure(
        mode: SpatialMode,
        target: OutputTarget,
        spatialDepth: Float = mode.defaultDepth,
        heightPresence: Float = mode.defaultHeight,
        vocalAnchor: Boolean = true,
        pinnaAirGainDb: Double = AtmosAsarSpecs.PINNA_AIR_DB.toDouble(),
        vocalFocus: Float = 0.70f
    ) {
        this.mode = mode
        this.outputTarget = target
        this.spatialDepth = spatialDepth.coerceIn(0f, 1f)
        this.heightPresence = heightPresence.coerceIn(0f, 1f)
        this.vocalAnchor = vocalAnchor
        val clampedFocus = vocalFocus.coerceIn(0f, 1f)
        this.vocalFocus = clampedFocus
        // Smoothly shapes dry 1–3.5kHz center vocal presence from -1.0 dB (0%) to +2.5 dB (100%)
        dryVocalFocusEq.setPeaking(sampleRateRef, 1870.0, 0.75, -1.0 + 3.5 * clampedFocus)
        pinnaAirShelf11800.setHighShelf(
            sampleRateRef,
            AtmosAsarSpecs.PINNA_AIR_HZ,
            0.7071,
            pinnaAirGainDb.coerceIn(0.0, AtmosAsarSpecs.PINNA_AIR_DB.toDouble())
        )
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val currentMode = mode
        if (currentMode == SpatialMode.OFF) {
            return inL to inR
        }

        val isCinema = currentMode == SpatialMode.ATMOS_CINEMA
        val focus = vocalFocus.toDouble()

        // Stage 1: Dry Center Vocal (1–3.5kHz) & Sub-Bass (<150Hz) Lock (100% dry, zero reverb/delay)
        val mid = 0.5 * (inL + inR)
        val side = 0.5 * (inL - inR)

        val (dryCenter, _) = dryVocalFocusEq.processStereo(mid, 0.0)

        // Strict >150Hz high-pass on Side so <150Hz bass stays strictly mono & dry
        val (_, sideS1) = sideHpf150A.processStereo(0.0, side)
        val (_, sideAbove150) = sideHpf150B.processStereo(0.0, sideS1)

        // Stage 2: iPhone 18 Pro Max Built-In Speaker Atmos (2nd-Order Transaural XTC on Side channel)
        if (outputTarget == OutputTarget.SPEAKER) {
            val sideScale = (if (isCinema) 1.24 else 1.02) * (1.08 - 0.18 * focus)
            val (xtcSideL, xtcSideR) = speakerCrosstalkCanceller.processSideChannel(
                sideAbove150 = sideAbove150,
                intensityScale = sideScale
            )
            return (dryCenter + xtcSideL) to (dryCenter + xtcSideR)
        }

        // Stage 3: Headphone 7.1.4 Binaural Elevation (Diffuse-Field HRTF on Side channel)
        val surroundWidth = (if (isCinema) 1.22 else 0.94) * (1.10 - 0.20 * focus)
        val heightGain = if (isCinema) 1.18 else 0.85

        // Surround L / R (±110° azimuth, 0° elevation) fed by Side >150Hz
        val (lsL, lsR) = leftSurround.processMonoToBinaural(sideAbove150 * surroundWidth)
        val (rsL, rsR) = rightSurround.processMonoToBinaural(-sideAbove150 * surroundWidth)

        // Top Height L / R (±60° azimuth, +50° elevation, high-passed at 2.8kHz)
        val (h2800L, h2800R) = heightHighPass2800.processStereo(
            sideAbove150 * heightGain,
            -sideAbove150 * heightGain
        )
        val (ltfL, ltfR) = leftTopHeight.processMonoToBinaural(h2800L)
        val (rtfL, rtfR) = rightTopHeight.processMonoToBinaural(h2800R)

        // Authentic Pinna Elevation Filter (6.8kHz notch -3.0dB + 11.8kHz air lift +2.5dB budget-capped)
        val heightSumL = ltfL + rtfL
        val heightSumR = ltfR + rtfR
        val (pinnaNotchedL, pinnaNotchedR) = pinnaNotch6800.processStereo(heightSumL, heightSumR)
        val (pinnaHeightL, pinnaHeightR) = pinnaAirShelf11800.processStereo(pinnaNotchedL, pinnaNotchedR)

        // Diffuse spatial bus (Surround + Elevated Height)
        val diffuseSpatialL = (lsL + rsL) * 0.54 + pinnaHeightL * 0.56
        val diffuseSpatialR = (lsR + rsR) * 0.54 + pinnaHeightR * 0.56

        // Convolve ONLY the diffuse spatial component with 18ms Studio Early-Reflection Impulse (3.2kHz LPF, 8% wet)
        val (diffusedL, diffusedR) = studioConvolver.processStereo(
            inL = diffuseSpatialL,
            inR = diffuseSpatialR,
            wetMix = AtmosAsarSpecs.STUDIO_IR_WET_MIX
        )

        // Sum 100% dry center vocal & <150Hz sub-bass with diffuse 7.1.4 spatial field
        return (dryCenter + diffusedL) to (dryCenter + diffusedR)
    }
}
