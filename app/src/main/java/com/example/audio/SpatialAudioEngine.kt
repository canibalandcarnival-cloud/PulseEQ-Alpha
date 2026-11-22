package com.example.audio

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
 * True 3D HRTF Virtual Speaker Panner modeling Woodworth spherical-head Interaural Time Difference (ITD),
 * Brown-Duda frequency-dependent acoustic head-shadowing ILD filters, and elevation pinna tilt.
 */
class HrtfSpeakerPanner(
    private val sampleRate: Int,
    azimuthDeg: Float,
    elevationDeg: Float
) {
    private val bufferSize = 128 // >2.8ms at 44.1kHz (max human ITD ~0.68ms)
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

        // Spherical-head ILD + elevation compensation
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

        // Brown-Duda contralateral head shadow 1-pole filter:
        // H(z) = (b0 + b1*z^-1) / (1 + a1*z^-1)
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
        // angleIncidence: +1.0 = ipsilateral ear, -1.0 = contralateral shadowed ear
        val alphaMin = 0.14
        val thetaNorm = ((1.0 - angleIncidence) * 0.5).coerceIn(0.0, 1.0) // 0 ipsi, 1 contra
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
 * Procedural 28ms Zero-Echo Apple Studio Micro-Ambience Impulse Response Convolver.
 * Generates a 28ms exponentially decaying stereo impulse response damped via a 3.5kHz LPF
 * and renders at 10% wet mix for non-echoic studio spaciousness.
 */
class AppleStudioMicroAmbienceConvolver(private val sampleRate: Int = 44100) {

    val irLengthSamples: Int = (sampleRate * 0.028).roundToInt().coerceAtLeast(32)
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

    private val dampLpfL = StereoBiquad().apply { setLowPass(sampleRate, 3500.0, 0.7071) }
    private val dampLpfR = StereoBiquad().apply { setLowPass(sampleRate, 3500.0, 0.7071) }
    private val subBassCutL = StereoBiquad().apply { setHighPass(sampleRate, 120.0, 0.7071) }
    private val subBassCutR = StereoBiquad().apply { setHighPass(sampleRate, 120.0, 0.7071) }

    init {
        val (rawL, rawR, tOffL, tGainL, tOffR, tGainR) = buildMicroAmbienceIr(sampleRate, irLengthSamples)
        irSamplesL = rawL
        irSamplesR = rawR
        tapOffsetsL = tOffL
        tapGainsL = tGainL
        tapOffsetsR = tOffR
        tapGainsR = tGainR
    }

    fun processStereo(inL: Double, inR: Double, wetMix: Double = 0.10): Pair<Double, Double> {
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

        // Smooth 3.5kHz damping & 120Hz mono-bass protection on wet studio ambience return
        val (dampedL, _) = dampLpfL.processStereo(convL, 0.0)
        val (_, dampedR) = dampLpfR.processStereo(0.0, convR)
        val (cleanWetL, _) = subBassCutL.processStereo(dampedL, 0.0)
        val (_, cleanWetR) = subBassCutR.processStereo(0.0, dampedR)

        val dry = 1.0 - (wetMix * 0.25)
        return (inL * dry + cleanWetL * wetMix) to (inR * dry + cleanWetR * wetMix)
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
         * Builds a strictly 28ms stereo impulse response with rapid exponential decay (tau = 5.5ms)
         * and 3.5kHz low-pass damping for clean zero-echo Apple Studio Micro-Ambience.
         */
        fun generateStudioMicroAmbienceIr(sampleRate: Int = 44100): Pair<FloatArray, FloatArray> {
            val len = (sampleRate * 0.028).roundToInt().coerceAtLeast(32)
            val bundle = buildMicroAmbienceIr(sampleRate, len)
            return bundle.irL to bundle.irR
        }

        private fun buildMicroAmbienceIr(sampleRate: Int, lengthSamples: Int): IrBundle {
            val rawL = DoubleArray(lengthSamples)
            val rawR = DoubleArray(lengthSamples)

            val tapCount = 72
            val offsetsL = IntArray(tapCount)
            val offsetsR = IntArray(tapCount)
            val gainsL = DoubleArray(tapCount)
            val gainsR = DoubleArray(tapCount)

            val tauSec = 0.0055 // Rapid decay: -44dB at 28ms -> zero audible echo
            var seed = 0x5A17C0DE

            fun nextRandSigned(): Double {
                seed = seed * 1664525 + 1013904223
                val u = ((seed ushr 8) and 0xFFFF) / 65535.0
                return u * 2.0 - 1.0
            }

            // Velvet-noise distributed early micro-reflections spanning 0.5ms .. 28ms
            val minStart = (sampleRate * 0.0005).toInt().coerceAtLeast(1)
            val span = (lengthSamples - 1 - minStart).coerceAtLeast(tapCount)
            var energySum = 0.0

            for (i in 0 until tapCount) {
                val frac = i.toDouble() / tapCount
                val baseIdx = minStart + (frac.pow(1.15) * span).toInt()
                val jitterL = (abs(nextRandSigned()) * 4.0).toInt()
                val jitterR = (abs(nextRandSigned()) * 4.0).toInt()

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

            // Bake 3.5kHz low-pass damping into the exported FloatArray IR as well
            val lpf = StereoBiquad().apply { setLowPass(sampleRate, 3500.0, 0.7071) }
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
 * Acoustic Crosstalk Cancellation (XTC) for Phone Speaker Mode:
 * Inverts and delays cross-channel audio by 0.18ms (~8 samples @ 44.1kHz) so stereo content
 * projects wide outside tightly spaced physical micro-drivers while preserving mono bass & vocal center.
 */
class AcousticCrosstalkCanceller(private val sampleRate: Int = 44100) {
    private val delaySamples: Double = 0.00018 * sampleRate // Strictly 0.18ms
    private val bufSize = 64
    private val mask = bufSize - 1
    private val ringL = DoubleArray(bufSize)
    private val ringR = DoubleArray(bufSize)
    private var writeIdx = 0

    // Keep cancellation band-limited (above 160Hz so low bass never phase-cancels)
    private val xtcHighPass = StereoBiquad().apply { setHighPass(sampleRate, 160.0, 0.7071) }
    private val xtcLowPass = StereoBiquad().apply { setLowPass(sampleRate, 10500.0, 0.7071) }

    private fun readDelayed(ring: DoubleArray): Double {
        val intD = delaySamples.toInt()
        val frac = delaySamples - intD
        val idx0 = (writeIdx - intD) and mask
        val idx1 = (writeIdx - intD - 1) and mask
        return ring[idx0] * (1.0 - frac) + ring[idx1] * frac
    }

    fun processStereo(
        inL: Double,
        inR: Double,
        spatialDepth: Double,
        vocalAnchor: Boolean
    ): Pair<Double, Double> {
        val (hpL, hpR) = xtcHighPass.processStereo(inL, inR)
        val (bandL, bandR) = xtcLowPass.processStereo(hpL, hpR)

        ringL[writeIdx] = bandL
        ringR[writeIdx] = bandR

        val delayedBandL = readDelayed(ringL)
        val delayedBandR = readDelayed(ringR)
        writeIdx = (writeIdx + 1) and mask

        // Crosstalk cancellation gain scaled by spatialDepth
        val xtcGain = (0.22 + 0.36 * spatialDepth).coerceIn(0.0, 0.62)

        // When Vocal Anchor is ON, lock Mid (center vocal) energy while full XTC widens stereo Side
        val mid = 0.5 * (inL + inR)
        val side = 0.5 * (inL - inR)

        val cancL = inL - xtcGain * delayedBandR
        val cancR = inR - xtcGain * delayedBandL

        val outL: Double
        val outR: Double
        if (vocalAnchor) {
            val cancMid = 0.5 * (cancL + cancR)
            val cancSide = 0.5 * (cancL - cancR)
            // Restore 100% locked Center vocal clarity + boosted wide Side projection
            val lockedMid = mid * 0.72 + cancMid * 0.28
            outL = lockedMid + cancSide * 1.15
            outR = lockedMid - cancSide * 1.15
        } else {
            outL = cancL + side * 0.08
            outR = cancR - side * 0.08
        }

        // Slight normalization to maintain neutral loudness
        return (outL * 0.96) to (outR * 0.96)
    }
}

/**
 * Apple-Style Spatial Audio / Dolby Atmos Binaural & Speaker XTC Engine:
 * 1. 7.1.2 Virtual Speaker Array (Center 0°/0° with <120Hz mono bass, Ls/Rs ±105°/0°, Ltf/Rtf ±65°/+45° @ 2.5kHz HPF)
 * 2. Pinna Height Psychoacoustic Curve (7.2kHz narrow notch -3.5dB + 12kHz high-shelf +3.0dB)
 * 3. Apple Studio Micro-Ambience (28ms Zero-Echo IR, 3.5kHz damping, 10% wet mix)
 * 4. Interactive Modes: Off | Atmos Music | Atmos Cinema, Depth, Height Presence, Vocal Anchor
 * 5. Automatic Phone Speaker Adaptation -> 0.18ms Acoustic Crosstalk Cancellation
 */
class SpatialAudioEngine(sampleRate: Int = 44100) {

    // 7.1.2 Virtual Speaker HRTF Panners
    private val centerFront = HrtfSpeakerPanner(sampleRate, azimuthDeg = 0f, elevationDeg = 0f)
    private val leftSurround = HrtfSpeakerPanner(sampleRate, azimuthDeg = -105f, elevationDeg = 0f)
    private val rightSurround = HrtfSpeakerPanner(sampleRate, azimuthDeg = 105f, elevationDeg = 0f)
    private val leftTopHeight = HrtfSpeakerPanner(sampleRate, azimuthDeg = -65f, elevationDeg = 45f)
    private val rightTopHeight = HrtfSpeakerPanner(sampleRate, azimuthDeg = 65f, elevationDeg = 45f)

    // <120Hz strict mono bass lock filters
    private val midLowPass120 = StereoBiquad().apply { setLowPass(sampleRate, 120.0, 0.7071) }
    private val midHighPass120 = StereoBiquad().apply { setHighPass(sampleRate, 120.0, 0.7071) }
    private val sideHighPass120 = StereoBiquad().apply { setHighPass(sampleRate, 120.0, 0.7071) }

    // Top Height channels high-passed at 2.5kHz so air/sparkles float overhead
    private val heightHighPass2500 = StereoBiquad().apply { setHighPass(sampleRate, 2500.0, 0.7071) }

    // Pinna Height Psychoacoustic Curve on Height/Surround bus: 7.2kHz notch -3.5dB + 12kHz shelf +3.0dB
    private val pinnaNotch7200 = StereoBiquad().apply { setPeaking(sampleRate, 7200.0, 5.5, -3.5) }
    private val pinnaShelf12000 = StereoBiquad().apply { setHighShelf(sampleRate, 12000.0, 0.7071, 3.0) }

    // Apple Studio Micro-Ambience (28ms Zero-Echo IR, 3.5kHz LPF, 10% wet mix)
    val studioConvolver = AppleStudioMicroAmbienceConvolver(sampleRate)

    // Phone Speaker Adaptation: 0.18ms Acoustic Crosstalk Cancellation
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

    fun configure(
        mode: SpatialMode,
        target: OutputTarget,
        spatialDepth: Float,
        heightPresence: Float,
        vocalAnchor: Boolean
    ) {
        this.mode = mode
        this.outputTarget = target
        this.spatialDepth = spatialDepth.coerceIn(0f, 1f)
        this.heightPresence = heightPresence.coerceIn(0f, 1f)
        this.vocalAnchor = vocalAnchor
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val currentMode = mode
        if (currentMode == SpatialMode.OFF) {
            return inL to inR
        }

        val depth = spatialDepth.toDouble()
        val height = heightPresence.toDouble()
        val cinemaScale = if (currentMode == SpatialMode.ATMOS_CINEMA) 1.32 else 1.0

        // Phone Speaker Mode Adaptation: automatically switch from Headphone HRTF to 0.18ms XTC
        if (outputTarget == OutputTarget.SPEAKER) {
            val effectiveDepth = (depth * if (currentMode == SpatialMode.ATMOS_CINEMA) 1.2 else 0.95).coerceIn(0.0, 1.0)
            val (xtcL, xtcR) = speakerCrosstalkCanceller.processStereo(
                inL = inL,
                inR = inR,
                spatialDepth = effectiveDepth,
                vocalAnchor = vocalAnchor
            )
            // Subtle 6% studio micro-ambience even on speakers without echo
            return studioConvolver.processStereo(xtcL, xtcR, wetMix = 0.06 * effectiveDepth)
        }

        // --- 7.1.2 Virtual Speaker Array (Headphones / Bluetooth HRTF Binaural) ---
        val mid = 0.5 * (inL + inR)
        val side = 0.5 * (inL - inR)

        // Strictly mono low-end (<120Hz) routed to Center Front (0°, 0°)
        val (monoBass, _) = midLowPass120.processStereo(mid, 0.0)
        val (midAbove120, _) = midHighPass120.processStereo(mid, 0.0)
        val (_, sideAbove120) = sideHighPass120.processStereo(0.0, side)

        // Center Front feed: strictly mono sub-120Hz + anchored mid vocals
        val centerVocalWeight = if (vocalAnchor) 1.08 else (0.94 - 0.12 * depth)
        val centerFeed = monoBass + midAbove120 * centerVocalWeight
        val (cL, cR) = centerFront.processMonoToBinaural(centerFeed)

        // Left & Right Surround (±105°, 0°): wrap-around Side + subtle front spread scaled by Depth
        val sideExpand = (0.45 + 0.95 * depth) * cinemaScale
        val ambientSpread = if (vocalAnchor) 0.10 * depth else (0.18 + 0.14 * depth)
        val lsFeed = sideAbove120 * sideExpand + midAbove120 * ambientSpread
        val rsFeed = -sideAbove120 * sideExpand + midAbove120 * ambientSpread

        val (lsL, lsR) = leftSurround.processMonoToBinaural(lsFeed)
        val (rsL, rsR) = rightSurround.processMonoToBinaural(rsFeed)

        // Left & Right Top Height (±65°, +45°): high-passed at 2.5kHz so airy details float overhead
        val heightInputL = sideAbove120 * 0.85 + midAbove120 * 0.28
        val heightInputR = -sideAbove120 * 0.85 + midAbove120 * 0.28
        val (h2500L, h2500R) = heightHighPass2500.processStereo(heightInputL, heightInputR)

        val heightGain = (0.25 + 0.90 * height) * cinemaScale
        val (ltfL, ltfR) = leftTopHeight.processMonoToBinaural(h2500L * heightGain)
        val (rtfL, rtfR) = rightTopHeight.processMonoToBinaural(h2500R * heightGain)

        // Surround + Height bus -> apply Pinna Height Psychoacoustic Curve (7.2kHz -3.5dB notch + 12kHz +3dB shelf)
        val busL = lsL + rsL + ltfL + rtfL
        val busR = lsR + rsR + ltfR + rtfR
        val (notchedL, notchedR) = pinnaNotch7200.processStereo(busL, busR)
        val (pinnaL, pinnaR) = pinnaShelf12000.processStereo(notchedL, notchedR)

        // Combine anchored Center Front with elevated Surround/Height bus
        val sumL = cL * 0.82 + pinnaL * 0.68
        val sumR = cR * 0.82 + pinnaR * 0.68

        // Apple Studio Micro-Ambience (strictly 28ms IR, 3.5kHz damping, 10% wet mix)
        val studioWet = (0.07 + 0.03 * depth) * if (currentMode == SpatialMode.ATMOS_CINEMA) 1.05 else 1.0
        return studioConvolver.processStereo(sumL, sumR, wetMix = studioWet.coerceIn(0.0, 0.12))
    }
}
