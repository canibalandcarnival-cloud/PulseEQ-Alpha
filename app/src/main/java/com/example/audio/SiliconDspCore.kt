package com.example.audio

import com.example.model.OutputTarget
import com.example.model.SiliconDspSpecs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Sample-accurate 48 kHz Cirrus Logic / Apple Audio Silicon DSP Core (CS35L-style hardware model).
 * Placed right after the EQ/dynamics stages and right before the final output destination.
 *
 * Implements 5 hardware-modeled stages sample-by-sample:
 * 1. True 5 ms Lookahead Ring Buffer (240 samples at 48 kHz)
 * 2. 2nd-Order Electromechanical Voice-Coil Excursion (f0 = 92 Hz, Qts = 0.85) + Dual-Tau Thermal Model (1.8s / 18.0s)
 *    modulating a 2-pole Cytomic TPT State-Variable High-Pass Filter (86 Hz–120 Hz on Phone Speaker, 24 Hz fixed on Headphones)
 * 3. Adaptive Class-H Charge-Pump Rail (3.7 V – 11.5 V, 0.2 ms attack / 60 ms hold-decay) +
 *    4x Oversampled Asymmetric tanh Soft-Knee Saturation (linear below -3.0 dBFS, ceiling 0.992)
 * 4. Minimum-Phase Reconstruction (19.2 kHz 2-pole Butterworth LPF) + Micro-Air (+1.1 dB @ 14.5 kHz) Filter
 * 5. 4x Oversampled -0.2 dBTP Inter-Sample True-Peak Brickwall Limiter
 */
typealias CirrusSiliconDspEngine = SiliconDspCore

class SiliconDspCore(private val sampleRate: Int = 48000) {

    // Stage 1: True 5ms lookahead ring buffer (240 samples @ 48 kHz)
    val lookaheadSamples: Int = (sampleRate * SiliconDspSpecs.LOOKAHEAD_SEC).roundToInt().coerceAtLeast(1)
    private val ringL = DoubleArray(lookaheadSamples)
    private val ringR = DoubleArray(lookaheadSamples)
    private var ringIdx = 0

    // Stage 2a: 0ms sidechain < 140Hz LPF + 2nd-order electromechanical spring-mass-damper (f0=92Hz, Qts=0.85)
    private val sidechainBassLpf = StereoBiquad().apply {
        setLowPass(sampleRate, SiliconDspSpecs.SIDECHAIN_LPF_HZ, StereoBiquad.BUTTERWORTH_Q)
    }
    private val omega0 = 2.0 * PI * SiliconDspSpecs.RESONANT_F0_HZ
    private val dampingFactor = omega0 / SiliconDspSpecs.QTS
    private val dt = 1.0 / sampleRate
    private var xPos = 0.0
    private var xVel = 0.0

    private val svfAttackCoeff = exp(-1.0 / (SiliconDspSpecs.SVF_ATTACK_SEC * sampleRate))
    private val svfReleaseCoeff = exp(-1.0 / (SiliconDspSpecs.SVF_RELEASE_SEC * sampleRate))
    private var excursionEnv = 0.0

    // Cytomic TPT 2-pole State-Variable High-Pass Filter state (L & R)
    private var svfIc1L = 0.0
    private var svfIc2L = 0.0
    private var svfIc1R = 0.0
    private var svfIc2R = 0.0
    private var smoothedSvfCutoffHz = SiliconDspSpecs.SVF_MIN_HZ

    // Stage 2b: Dual time-constant thermal model (tau_coil = 1.8s, tau_magnet = 18.0s)
    private val alphaCoil = 1.0 - exp(-1.0 / (SiliconDspSpecs.THERMAL_TAU_COIL_SEC * sampleRate))
    private val alphaMagnet = 1.0 - exp(-1.0 / (SiliconDspSpecs.THERMAL_TAU_MAGNET_SEC * sampleRate))
    private var deltaTempCoil = 0.0
    private var deltaTempMagnet = 0.0

    // Stage 3: Adaptive Class-H Charge-Pump Rail & 4x Oversampled Soft-Knee Saturation
    private val railAttackCoeff = exp(-1.0 / (SiliconDspSpecs.CLASS_H_ATTACK_SEC * sampleRate))
    private val railDecayCoeff = exp(-1.0 / (SiliconDspSpecs.CLASS_H_DECAY_SEC * sampleRate))
    private var railEnvelope = 0.0
    private val softKneeThresh = 10.0.pow(SiliconDspSpecs.SOFT_KNEE_THRESHOLD_DBFS / 20.0)
    private val softKneeCeiling = SiliconDspSpecs.SOFT_KNEE_CEILING
    private var prevSvfL = 0.0
    private var prevSvfR = 0.0

    // Stage 4: Minimum-Phase Reconstruction (19.2 kHz 2-pole Butterworth) & Micro-Air (+1.1 dB @ 14.5 kHz)
    private val reconLpf = StereoBiquad().apply {
        val safeCutoff = SiliconDspSpecs.RECON_LPF_HZ.coerceAtMost(sampleRate * 0.44)
        setLowPass(sampleRate, safeCutoff, StereoBiquad.BUTTERWORTH_Q)
    }
    private val microAirShelf = StereoBiquad().apply {
        val safeAirHz = SiliconDspSpecs.MICRO_AIR_HZ.coerceAtMost(sampleRate * 0.42)
        setDeCrampedHighShelf(sampleRate, safeAirHz, StereoBiquad.BUTTERWORTH_Q, SiliconDspSpecs.MICRO_AIR_GAIN_DB)
    }

    // Stage 5: 4x Oversampled -0.2 dBTP Inter-Sample True-Peak Brickwall Limiter
    private val truePeakCeilingLinear = 10.0.pow(SiliconDspSpecs.TRUE_PEAK_CEILING_DBTP / 20.0)
    private val tpHistL = DoubleArray(4)
    private val tpHistR = DoubleArray(4)
    private val tpReleaseCoeff = exp(-1.0 / (0.035 * sampleRate))
    private var tpGainLinear = 1.0

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var outputTarget: OutputTarget = OutputTarget.SPEAKER
        private set

    // Live hardware telemetry readouts (polled at ~60fps)
    @Volatile
    var latestExcursionPercent: Float = 0f
        private set

    @Volatile
    var latestSvfCutoffHz: Float = SiliconDspSpecs.SVF_MIN_HZ.toFloat()
        private set

    @Volatile
    var latestCoilTempC: Float = SiliconDspSpecs.AMBIENT_TEMP_C.toFloat()
        private set

    @Volatile
    var latestClassHRailV: Float = SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat()
        private set

    @Volatile
    var latestTruePeakGrDb: Float = 0f
        private set

    fun configure(
        isEnabled: Boolean,
        target: OutputTarget,
        microAirGainDb: Double = SiliconDspSpecs.MICRO_AIR_GAIN_DB
    ) {
        enabled = isEnabled
        outputTarget = target
        val safeAirHz = SiliconDspSpecs.MICRO_AIR_HZ.coerceAtMost(sampleRate * 0.42)
        microAirShelf.setDeCrampedHighShelf(
            sampleRate,
            safeAirHz,
            StereoBiquad.BUTTERWORTH_Q,
            microAirGainDb.coerceIn(0.0, SiliconDspSpecs.MICRO_AIR_GAIN_DB)
        )
        if (!isEnabled) {
            latestExcursionPercent = 0f
            latestSvfCutoffHz = if (target == OutputTarget.SPEAKER) {
                SiliconDspSpecs.SVF_MIN_HZ.toFloat()
            } else {
                SiliconDspSpecs.SVF_HEADPHONES_HZ.toFloat()
            }
            latestClassHRailV = SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat()
            latestTruePeakGrDb = 0f
        }
    }

    fun reset() {
        ringL.fill(0.0)
        ringR.fill(0.0)
        ringIdx = 0
        sidechainBassLpf.reset()
        sidechainBassLpf.setLowPass(sampleRate, SiliconDspSpecs.SIDECHAIN_LPF_HZ, StereoBiquad.BUTTERWORTH_Q)
        xPos = 0.0
        xVel = 0.0
        excursionEnv = 0.0
        svfIc1L = 0.0
        svfIc2L = 0.0
        svfIc1R = 0.0
        svfIc2R = 0.0
        smoothedSvfCutoffHz = SiliconDspSpecs.SVF_MIN_HZ
        deltaTempCoil = 0.0
        deltaTempMagnet = 0.0
        railEnvelope = 0.0
        prevSvfL = 0.0
        prevSvfR = 0.0
        reconLpf.setLowPass(sampleRate, SiliconDspSpecs.RECON_LPF_HZ.coerceAtMost(sampleRate * 0.44), StereoBiquad.BUTTERWORTH_Q)
        microAirShelf.setDeCrampedHighShelf(
            sampleRate,
            SiliconDspSpecs.MICRO_AIR_HZ.coerceAtMost(sampleRate * 0.42),
            StereoBiquad.BUTTERWORTH_Q,
            SiliconDspSpecs.MICRO_AIR_GAIN_DB
        )
        tpHistL.fill(0.0)
        tpHistR.fill(0.0)
        tpGainLinear = 1.0
        latestExcursionPercent = 0f
        latestCoilTempC = SiliconDspSpecs.AMBIENT_TEMP_C.toFloat()
        latestClassHRailV = SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat()
        latestTruePeakGrDb = 0f
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            return inL to inR
        }

        // --- Stage 1: True 5ms Lookahead Ring Buffer (240 samples at 48 kHz) ---
        val delayedL = ringL[ringIdx]
        val delayedR = ringR[ringIdx]
        ringL[ringIdx] = inL
        ringR[ringIdx] = inR
        ringIdx = (ringIdx + 1) % lookaheadSamples

        // --- Stage 2: 0ms Lookahead Sidechain Electromechanical Excursion & Dual-Tau Thermal Model ---
        val sideMono = 0.5 * (inL + inR)
        val (bassDrive, _) = sidechainBassLpf.processStereo(sideMono, 0.0)

        // 2nd-order spring-mass-damper differential equation:
        // d^2x/dt^2 + (w0/Qts)*dx/dt + w0^2*x = w0^2 * K * v_drive
        val driveScaled = bassDrive * SiliconDspSpecs.EXCURSION_DRIVE_SCALE
        val accel = (omega0 * omega0) * (driveScaled - xPos) - dampingFactor * xVel
        xVel += accel * dt
        xPos += xVel * dt

        val instExcursion = abs(xPos).coerceIn(0.0, 1.5)
        val excCoeff = if (instExcursion > excursionEnv) svfAttackCoeff else svfReleaseCoeff
        excursionEnv = excCoeff * excursionEnv + (1.0 - excCoeff) * instExcursion

        val isSpeaker = (outputTarget == OutputTarget.SPEAKER)
        val targetCutoffHz = if (isSpeaker) {
            // Smoothly modulate SVF cutoff 68Hz -> 122Hz as |x[n]| approaches Xmax (1.0)
            val ratio = ((excursionEnv - 0.35) / 0.65).coerceIn(0.0, 1.0)
            SiliconDspSpecs.SVF_MIN_HZ + ratio * (SiliconDspSpecs.SVF_MAX_HZ - SiliconDspSpecs.SVF_MIN_HZ)
        } else {
            SiliconDspSpecs.SVF_HEADPHONES_HZ
        }
        smoothedSvfCutoffHz = smoothedSvfCutoffHz * 0.992 + targetCutoffHz * 0.008

        // Cytomic TPT 2-pole State-Variable High-Pass Filter (zero zipper modulation)
        val g = tan(PI * smoothedSvfCutoffHz.coerceIn(18.0, 400.0) / sampleRate)
        val k = sqrt(2.0) // exact 1 / (1 / sqrt(2.0)) Butterworth damping
        val a1 = 1.0 / (1.0 + g * (g + k))
        val a2 = g * a1
        val a3 = g * a2

        val v3L = delayedL - svfIc2L
        val v1L = a1 * svfIc1L + a2 * v3L
        val v2L = svfIc2L + a2 * svfIc1L + a3 * v3L
        svfIc1L = 2.0 * v1L - svfIc1L
        svfIc2L = 2.0 * v2L - svfIc2L
        val svfL = delayedL - k * v1L - v2L

        val v3R = delayedR - svfIc2R
        val v1R = a1 * svfIc1R + a2 * v3R
        val v2R = svfIc2R + a2 * svfIc1R + a3 * v3R
        svfIc1R = 2.0 * v1R - svfIc1R
        svfIc2R = 2.0 * v2R - svfIc2R
        val svfR = delayedR - k * v1R - v2R

        // Dual time-constant thermal model (I^2*R integration, tau_coil=1.8s, tau_magnet=18.0s)
        val powerI2R = (0.5 * (inL * inL + inR * inR) + 1.4 * bassDrive * bassDrive).coerceAtMost(2.5)
        deltaTempCoil += alphaCoil * (powerI2R * SiliconDspSpecs.THERMAL_COIL_GAIN - deltaTempCoil)
        deltaTempMagnet += alphaMagnet * (deltaTempCoil * 0.58 - deltaTempMagnet)
        val coilTempC = (SiliconDspSpecs.AMBIENT_TEMP_C + deltaTempCoil + deltaTempMagnet)
            .coerceIn(SiliconDspSpecs.AMBIENT_TEMP_C, SiliconDspSpecs.MAX_TEMP_C)

        val thermalGain = if (coilTempC > SiliconDspSpecs.THERMAL_PROTECT_START_C) {
            val over = ((coilTempC - SiliconDspSpecs.THERMAL_PROTECT_START_C) /
                (SiliconDspSpecs.MAX_TEMP_C - SiliconDspSpecs.THERMAL_PROTECT_START_C)).coerceIn(0.0, 1.0)
            10.0.pow((-SiliconDspSpecs.THERMAL_MAX_REDUCTION_DB * over) / 20.0)
        } else {
            1.0
        }

        val postThermalL = svfL * thermalGain
        val postThermalR = svfR * thermalGain

        // --- Stage 3: Adaptive Class-H Charge-Pump Rail (3.7V–11.5V) & 4x Oversampled Soft-Knee Saturation ---
        val detectorPeak = max(
            max(abs(inL), abs(inR)),
            max(abs(postThermalL), abs(postThermalR))
        )
        val rCoeff = if (detectorPeak > railEnvelope) railAttackCoeff else railDecayCoeff
        railEnvelope = rCoeff * railEnvelope + (1.0 - rCoeff) * detectorPeak
        val railVolts = (SiliconDspSpecs.CLASS_H_MIN_VOLTS +
            (SiliconDspSpecs.CLASS_H_MAX_VOLTS - SiliconDspSpecs.CLASS_H_MIN_VOLTS) *
            railEnvelope.coerceIn(0.0, 1.0)).coerceIn(
            SiliconDspSpecs.CLASS_H_MIN_VOLTS,
            SiliconDspSpecs.CLASS_H_MAX_VOLTS
        )

        // 4x oversampled asymmetric tanh soft-knee saturation (linear y = x below -3.0 dBFS, ceiling 0.992)
        val satL = process4xOversampledSoftKnee(prevSvfL, postThermalL)
        val satR = process4xOversampledSoftKnee(prevSvfR, postThermalR)
        prevSvfL = postThermalL
        prevSvfR = postThermalR

        // --- Stage 4: Minimum-Phase Reconstruction (19.2 kHz Butterworth LPF) & +1.1 dB @ 14.5 kHz Micro-Air ---
        val (reconL, reconR) = reconLpf.processStereo(satL, satR)
        val (airL, airR) = microAirShelf.processStereo(reconL, reconR)

        // --- Stage 5: 4x Oversampled -0.2 dBTP Inter-Sample True-Peak Brickwall Limiter ---
        val interSamplePeakL = detect4xTruePeak(tpHistL, airL)
        val interSamplePeakR = detect4xTruePeak(tpHistR, airR)
        val maxTruePeak = max(interSamplePeakL, interSamplePeakR)

        val targetGain = if (maxTruePeak > truePeakCeilingLinear) {
            (truePeakCeilingLinear / maxTruePeak).coerceIn(0.05, 1.0)
        } else {
            1.0
        }

        tpGainLinear = if (targetGain < tpGainLinear) {
            targetGain // Instant brickwall catch for inter-sample true peaks
        } else {
            tpReleaseCoeff * tpGainLinear + (1.0 - tpReleaseCoeff) * targetGain
        }

        val outL = (airL * tpGainLinear).coerceIn(-truePeakCeilingLinear, truePeakCeilingLinear)
        val outR = (airR * tpGainLinear).coerceIn(-truePeakCeilingLinear, truePeakCeilingLinear)

        // Update 60fps Silicon Hardware Telemetry readouts
        latestExcursionPercent = (excursionEnv * 100.0).coerceIn(0.0, 100.0).toFloat()
        latestSvfCutoffHz = smoothedSvfCutoffHz.toFloat()
        latestCoilTempC = coilTempC.toFloat()
        latestClassHRailV = railVolts.toFloat()
        latestTruePeakGrDb = if (tpGainLinear < 0.999) {
            (20.0 * log10(tpGainLinear)).toFloat().coerceAtMost(0f)
        } else {
            0f
        }

        return outL to outR
    }

    private fun softKneeTransfer(x: Double): Double {
        val thresh = softKneeThresh
        if (abs(x) <= thresh) return x
        val ceil = softKneeCeiling
        val headroom = ceil - thresh
        return if (x > 0.0) {
            thresh + headroom * tanh((x - thresh) / headroom)
        } else {
            // Slight even-harmonic asymmetry modeling adaptive Class-H charge-pump output stage
            val asym = 1.04
            -thresh - headroom * tanh((-x - thresh) * asym / headroom) / asym
        }
    }

    private fun process4xOversampledSoftKnee(prev: Double, curr: Double): Double {
        // 4x linear upsampling -> asymmetric tanh soft-knee -> 4-tap boxcar decimation
        var sum = 0.0
        for (step in 1..4) {
            val t = step * 0.25
            val interp = prev + (curr - prev) * t
            sum += softKneeTransfer(interp)
        }
        return sum * 0.25
    }

    private fun detect4xTruePeak(hist: DoubleArray, nextSample: Double): Double {
        hist[0] = hist[1]
        hist[1] = hist[2]
        hist[2] = hist[3]
        hist[3] = nextSample

        val p0 = hist[0]
        val p1 = hist[1]
        val p2 = hist[2]
        val p3 = hist[3]

        // 4-point Catmull-Rom cubic Hermite interpolation at 0.25, 0.50, 0.75, 1.0
        val a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3
        val b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3
        val c = -0.5 * p0 + 0.5 * p2
        val d = p1

        var maxPeak = max(abs(p1), abs(p2))
        for (step in 1..3) {
            val t = step * 0.25
            val interp = ((a * t + b) * t + c) * t + d
            val absVal = abs(interp)
            if (absVal > maxPeak) maxPeak = absVal
        }
        return max(maxPeak, abs(nextSample))
    }
}
