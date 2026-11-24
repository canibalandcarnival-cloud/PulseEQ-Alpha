package com.example.audio

import com.example.model.ListeningMode
import com.example.model.OutputTarget
import com.example.model.StudioHiResSpecs
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * iOS 27 AirPods-Style Listening Mode DSP Stage for Headphones & Bluetooth:
 * - OFF: Bit-transparent bypass
 * - TRANSPARENCY: Open-ear pressure relief (-1.8 dB lowShelf @ 85 Hz), natural external pinna
 *   openness (+1.6 dB @ 2.2 kHz), and subtle >300 Hz M/S spatial air breathing (+8% side)
 * - ADAPTIVE: Dynamic content-aware blend — cleans sub-rumble (-2.5 dB lowShelf @ 95 Hz),
 *   protects speech presence (+1.0 dB @ 1.5 kHz), and dynamically ducks excessive 4.2 kHz transient clutter
 * - NOISE_CANCELLATION: Active ANC isolation contour — 48 Hz sub-rumble HPF + 75 Hz cabin hum dip (-4.5 dB),
 *   125 Hz tight seal bass restoration (+2.0 dB), and 1.8 kHz intimate studio focus (+1.2 dB)
 *
 * Automatically disengages on Phone Speaker output.
 */
class ListeningModeProcessor(private val sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ) {

    // Transparency filters
    private val transSubRelief85 = StereoBiquad()
    private val transOpenness2200 = StereoBiquad()
    private val transSideAirHpf300 = StereoBiquad().apply {
        setHighPass(sampleRate, 300.0, 0.7071)
    }

    // Adaptive Audio filters & dynamic clutter detector
    private val adaptiveRumble95 = StereoBiquad()
    private val adaptiveSpeech1500 = StereoBiquad()
    private val adaptiveClutterDetector4200 = StereoBiquad().apply {
        setBandPass(sampleRate, 4200.0, 1.2)
    }
    private val adaptiveDynamicDip4200 = StereoBiquad()
    private val envAttackCoeff = exp(-1.0 / (0.012 * sampleRate))
    private val envReleaseCoeff = exp(-1.0 / (0.160 * sampleRate))
    private var adaptiveClutterEnv = 0.0
    private var lastAppliedClutterDb = 0.0
    private var adaptiveUpdateCounter = 0

    // Active Noise Cancellation (ANC) contour filters
    private val ancSubRumbleHpf48 = StereoBiquad()
    private val ancHumNotch75 = StereoBiquad()
    private val ancSealBass125 = StereoBiquad()
    private val ancFocus1800 = StereoBiquad()

    @Volatile
    var activeMode: ListeningMode = ListeningMode.OFF
        private set

    @Volatile
    var isEngaged: Boolean = false
        private set

    @Volatile
    var latestIsolationDb: Float = 0f
        private set

    init {
        configure(
            isEnabled = false,
            mode = ListeningMode.OFF,
            outputTarget = OutputTarget.HEADPHONES
        )
    }

    fun configure(
        isEnabled: Boolean,
        mode: ListeningMode,
        outputTarget: OutputTarget
    ) {
        val personalAudioTarget = outputTarget == OutputTarget.HEADPHONES || outputTarget == OutputTarget.BLUETOOTH
        val resolvedMode = if (isEnabled && personalAudioTarget) mode else ListeningMode.OFF
        activeMode = resolvedMode
        isEngaged = resolvedMode != ListeningMode.OFF

        when (resolvedMode) {
            ListeningMode.OFF -> {
                transSubRelief85.reset()
                transOpenness2200.reset()
                transSideAirHpf300.reset()
                adaptiveRumble95.reset()
                adaptiveSpeech1500.reset()
                adaptiveClutterDetector4200.reset()
                adaptiveDynamicDip4200.reset()
                adaptiveClutterEnv = 0.0
                lastAppliedClutterDb = 0.0
                ancSubRumbleHpf48.reset()
                ancHumNotch75.reset()
                ancSealBass125.reset()
                ancFocus1800.reset()
                latestIsolationDb = 0f
            }

            ListeningMode.TRANSPARENCY -> {
                transSubRelief85.setLowShelf(sampleRate, 85.0, 0.7071, -1.8)
                transOpenness2200.setPeaking(sampleRate, 2200.0, 0.85, 1.6)
                latestIsolationDb = 1.6f
            }

            ListeningMode.ADAPTIVE -> {
                adaptiveRumble95.setLowShelf(sampleRate, 95.0, 0.7071, -2.5)
                adaptiveSpeech1500.setPeaking(sampleRate, 1500.0, 0.90, 1.0)
                adaptiveDynamicDip4200.setPeaking(sampleRate, 4200.0, 1.15, lastAppliedClutterDb)
                latestIsolationDb = (-10.0 - abs(lastAppliedClutterDb) * 1.4).toFloat().coerceIn(-18f, -8f)
            }

            ListeningMode.NOISE_CANCELLATION -> {
                ancSubRumbleHpf48.setHighPass(sampleRate, 48.0, 0.7071)
                ancHumNotch75.setPeaking(sampleRate, 75.0, 1.1, -4.5)
                ancSealBass125.setPeaking(sampleRate, 125.0, 1.0, 2.0)
                ancFocus1800.setPeaking(sampleRate, 1800.0, 1.1, 1.2)
                latestIsolationDb = -28.0f
            }
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!isEngaged) return inL to inR

        return when (activeMode) {
            ListeningMode.OFF -> inL to inR

            ListeningMode.TRANSPARENCY -> {
                val (subL, subR) = transSubRelief85.processStereo(inL, inR)
                val (openL, openR) = transOpenness2200.processStereo(subL, subR)
                val (airL, airR) = transSideAirHpf300.processStereo(openL, openR)
                val sideAir = 0.5 * (airL - airR) * 0.08
                (openL + sideAir) to (openR - sideAir)
            }

            ListeningMode.ADAPTIVE -> {
                val (rumL, rumR) = adaptiveRumble95.processStereo(inL, inR)
                val (spL, spR) = adaptiveSpeech1500.processStereo(rumL, rumR)

                // Monitor upper-mid clutter peaks and smoothly engage -0..-3.5 dB attenuation when hot
                val (clL, clR) = adaptiveClutterDetector4200.processStereo(spL, spR)
                val clutterPeak = max(abs(clL), abs(clR))
                val coeff = if (clutterPeak > adaptiveClutterEnv) envAttackCoeff else envReleaseCoeff
                adaptiveClutterEnv = coeff * adaptiveClutterEnv + (1.0 - coeff) * clutterPeak

                adaptiveUpdateCounter++
                if (adaptiveUpdateCounter >= 32) {
                    adaptiveUpdateCounter = 0
                    val clutterDb = if (adaptiveClutterEnv > 1e-6) 20.0 * log10(adaptiveClutterEnv) else -90.0
                    val targetDipDb = if (clutterDb > -18.0) {
                        ((clutterDb + 18.0) * -0.22).coerceIn(-3.5, 0.0)
                    } else {
                        0.0
                    }
                    if (abs(targetDipDb - lastAppliedClutterDb) > 0.05) {
                        lastAppliedClutterDb = targetDipDb
                        adaptiveDynamicDip4200.setPeaking(sampleRate, 4200.0, 1.15, targetDipDb)
                    }
                    latestIsolationDb = (-10.0 - abs(lastAppliedClutterDb) * 1.4).toFloat().coerceIn(-18f, -8f)
                }

                adaptiveDynamicDip4200.processStereo(spL, spR)
            }

            ListeningMode.NOISE_CANCELLATION -> {
                val (hpfL, hpfR) = ancSubRumbleHpf48.processStereo(inL, inR)
                val (humL, humR) = ancHumNotch75.processStereo(hpfL, hpfR)
                val (sealL, sealR) = ancSealBass125.processStereo(humL, humR)
                ancFocus1800.processStereo(sealL, sealR)
            }
        }
    }

    companion object {
        fun computeContourResponseDbAt(
            freqHz: Double,
            mode: ListeningMode,
            target: OutputTarget,
            sampleRate: Int = 44100
        ): Float {
            if (target == OutputTarget.SPEAKER || mode == ListeningMode.OFF) return 0f

            return when (mode) {
                ListeningMode.OFF -> 0f
                ListeningMode.TRANSPARENCY -> {
                    val sub = StereoBiquad().apply { setLowShelf(sampleRate, 85.0, 0.7071, -1.8) }
                    val open = StereoBiquad().apply { setPeaking(sampleRate, 2200.0, 0.85, 1.6) }
                    (sub.magnitudeDbAt(sampleRate, freqHz) + open.magnitudeDbAt(sampleRate, freqHz)).toFloat()
                }
                ListeningMode.ADAPTIVE -> {
                    val rum = StereoBiquad().apply { setLowShelf(sampleRate, 95.0, 0.7071, -2.5) }
                    val sp = StereoBiquad().apply { setPeaking(sampleRate, 1500.0, 0.90, 1.0) }
                    (rum.magnitudeDbAt(sampleRate, freqHz) + sp.magnitudeDbAt(sampleRate, freqHz)).toFloat()
                }
                ListeningMode.NOISE_CANCELLATION -> {
                    val hpf = StereoBiquad().apply { setHighPass(sampleRate, 48.0, 0.7071) }
                    val hum = StereoBiquad().apply { setPeaking(sampleRate, 75.0, 1.1, -4.5) }
                    val seal = StereoBiquad().apply { setPeaking(sampleRate, 125.0, 1.0, 2.0) }
                    val focus = StereoBiquad().apply { setPeaking(sampleRate, 1800.0, 1.1, 1.2) }
                    (hpf.magnitudeDbAt(sampleRate, freqHz) +
                        hum.magnitudeDbAt(sampleRate, freqHz) +
                        seal.magnitudeDbAt(sampleRate, freqHz) +
                        focus.magnitudeDbAt(sampleRate, freqHz)).toFloat()
                }
            }
        }
    }
}
