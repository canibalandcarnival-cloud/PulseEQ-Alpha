package com.example.audio

import com.example.model.AudioMixMode
import kotlin.math.abs
import kotlin.math.exp

/**
 * iOS 27 "Audio Mix" Real-Time Stem Shaper:
 * Dynamically decomposes incoming stereo audio into Mid (L+R)/2 vocals/dialogue and
 * Side (L-R)/2 music/room-ambience, applying mode-specific spectral & ambient gating:
 * - Standard: Bit-transparent pass-through (1.0x Mid + 1.0x Side)
 * - In-Frame: Focuses center vocal formants (+2.2 dB @ 2.1 kHz) and trims off-axis Side wash (0.36x)
 * - Studio: Dry close-mic booth — strips room echo/reverb tails via adaptive Side downward gating & 110Hz rumble cut
 * - Cinematic: Crisp anchored Mid dialogue (+2.0 dB @ 2.2 kHz) over wide high-passed Side score (1.32x)
 */
class AudioMixStemShaper(private val sampleRate: Int = 44100) {

    // Mid vocal formant filters (we use left channel inside StereoBiquad for Mid, right channel for Side)
    private val midSideSubCut = StereoBiquad().apply {
        setHighPass(sampleRate, 105.0, 0.7071)
    }
    private val midVocalPresence = StereoBiquad().apply {
        setPeaking(sampleRate, 2150.0, 1.15, 2.2)
    }
    private val midWarmBodyTrim = StereoBiquad().apply {
        setPeaking(sampleRate, 340.0, 1.4, -1.5)
    }
    private val sideHighPass180 = StereoBiquad().apply {
        setHighPass(sampleRate, 180.0, 0.7071)
    }

    // Fast adaptive envelope followers for Studio mode echo/reverb tail stripping
    private val envAttack = exp(-1.0 / (0.004 * sampleRate))
    private val envRelease = exp(-1.0 / (0.095 * sampleRate))
    private var midEnv = 0.0
    private var sideEnv = 0.0

    @Volatile
    var mode: AudioMixMode = AudioMixMode.STANDARD

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val currentMode = mode
        if (currentMode == AudioMixMode.STANDARD) {
            return inL to inR
        }

        val mid = 0.5 * (inL + inR)
        val side = 0.5 * (inL - inR)

        midEnv = (if (abs(mid) > midEnv) envAttack else envRelease) * midEnv +
            (1.0 - (if (abs(mid) > midEnv) envAttack else envRelease)) * abs(mid)
        sideEnv = (if (abs(side) > sideEnv) envAttack else envRelease) * sideEnv +
            (1.0 - (if (abs(side) > sideEnv) envAttack else envRelease)) * abs(side)

        return when (currentMode) {
            AudioMixMode.STANDARD -> inL to inR

            AudioMixMode.IN_FRAME -> {
                // Focus foreground vocals on Mid while attenuating off-axis Side wash
                val (cleanMid, hpSide) = midSideSubCut.processStereo(mid, side)
                val (focusedMid, _) = midVocalPresence.processStereo(cleanMid, 0.0)
                val framedMid = focusedMid * 1.10
                val trimmedSide = hpSide * 0.36
                (framedMid + trimmedSide) to (framedMid - trimmedSide)
            }

            AudioMixMode.STUDIO -> {
                // Dry booth: strip room reflections/reverb tails on Side & cut sub-105Hz + 340Hz room boom
                val (hpMid, hpSide) = midSideSubCut.processStereo(mid, side)
                val (dryMid, _) = midWarmBodyTrim.processStereo(hpMid, 0.0)
                val (boothMid, _) = midVocalPresence.processStereo(dryMid, 0.0)

                // Adaptive room-echo suppression: when Side energy lingers relative to Mid or during tails, gate down
                val ratio = (sideEnv / (midEnv + 1e-5)).coerceIn(0.0, 2.0)
                val echoGate = (1.0 / (1.0 + 3.4 * ratio)).coerceIn(0.08, 0.22)
                val strippedSide = hpSide * echoGate

                val studioMid = boothMid * 1.14
                (studioMid + strippedSide) to (studioMid - strippedSide)
            }

            AudioMixMode.CINEMATIC -> {
                // Anchored crisp dialogue in center + wide high-passed (>180Hz) ambient music score
                val (vocalMid, _) = midVocalPresence.processStereo(mid, 0.0)
                val (_, wideSideHp) = sideHighPass180.processStereo(0.0, side)
                val dialogMid = vocalMid * 1.06
                val cinemaSide = wideSideHp * 1.32
                (dialogMid + cinemaSide) to (dialogMid - cinemaSide)
            }
        }
    }

    companion object {
        fun computeFrequencyTiltDbAt(
            freqHz: Double,
            mode: AudioMixMode,
            sampleRate: Int = 44100
        ): Float {
            if (mode == AudioMixMode.STANDARD) return 0f
            val presence = StereoBiquad().apply {
                val boost = when (mode) {
                    AudioMixMode.STANDARD -> 0.0
                    AudioMixMode.IN_FRAME -> 2.2
                    AudioMixMode.STUDIO -> 2.5
                    AudioMixMode.CINEMATIC -> 2.0
                }
                setPeaking(sampleRate, 2150.0, 1.15, boost)
            }
            var db = presence.magnitudeDbAt(sampleRate, freqHz)
            if (mode == AudioMixMode.STUDIO || mode == AudioMixMode.IN_FRAME) {
                val hpf = StereoBiquad().apply { setHighPass(sampleRate, 105.0, 0.7071) }
                db += hpf.magnitudeDbAt(sampleRate, freqHz) * 0.65
            }
            if (mode == AudioMixMode.STUDIO) {
                val trim = StereoBiquad().apply { setPeaking(sampleRate, 340.0, 1.4, -1.5) }
                db += trim.magnitudeDbAt(sampleRate, freqHz)
            }
            return db.toFloat()
        }
    }
}
