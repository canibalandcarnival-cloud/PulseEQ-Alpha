package com.example

import com.example.audio.BiquadEqFilterBank
import com.example.audio.DynamicRangeCompressor
import com.example.model.EqPresetsCatalog
import com.example.model.NightIntensity
import com.example.model.PresetGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun all_presets_including_late_night_have_10_valid_bands() {
        assertEquals(22, EqPresetsCatalog.PRESETS.size)
        assertEquals(5, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_1 })
        assertEquals(8, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_2 })
        assertEquals(8, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_3 })

        val lateNight = EqPresetsCatalog.findById(EqPresetsCatalog.LATE_NIGHT_ID)
        assertEquals(
            listOf(3.5f, 2.5f, 1.0f, 0.0f, 0.0f, 1.0f, 2.0f, 2.5f, 3.0f, 3.5f),
            lateNight?.gainsDb
        )

        EqPresetsCatalog.ALL_WITH_FLAT.forEach { preset ->
            assertEquals("Preset ${preset.name} must have 10 bands", 10, preset.gainsDb.size)
            preset.gainsDb.forEach { gain ->
                assertTrue(
                    "Gain $gain in ${preset.name} out of range",
                    gain in EqPresetsCatalog.MIN_GAIN_DB..EqPresetsCatalog.MAX_GAIN_DB
                )
            }
        }
    }

    @Test
    fun dynamic_range_compressor_reduces_peaks_and_scales_with_intensity() {
        val standardGr = DynamicRangeCompressor.computeGainReductionDb(
            inputDb = -6f,
            thresholdDb = NightIntensity.STANDARD.thresholdDb,
            kneeDb = NightIntensity.STANDARD.kneeDb,
            ratio = NightIntensity.STANDARD.ratio
        )
        val heavyGr = DynamicRangeCompressor.computeGainReductionDb(
            inputDb = -6f,
            thresholdDb = NightIntensity.HEAVY.thresholdDb,
            kneeDb = NightIntensity.HEAVY.kneeDb,
            ratio = NightIntensity.HEAVY.ratio
        )
        assertTrue("Expected negative gain reduction at -6 dB input", standardGr < -5f)
        assertTrue("Heavy intensity should compress more than Standard", heavyGr < standardGr)
    }

    @Test
    fun speaker_profile_cuts_sub_bass_and_headphones_boosts_full_range() {
        val speakerGains = com.example.model.OutputEqProfile.defaultGainsForTarget(com.example.model.OutputTarget.SPEAKER)
        val headphoneGains = com.example.model.OutputEqProfile.defaultGainsForTarget(com.example.model.OutputTarget.HEADPHONES)
        assertTrue("Phone speaker should cut 32Hz sub-bass", speakerGains[0] < 0f)
        assertTrue("Phone speaker should cut 64Hz sub-bass", speakerGains[1] < 0f)
        assertTrue("Phone speaker should boost vocal clarity at 2kHz", speakerGains[6] > 0f)
        assertTrue("Headphones should boost 32Hz bass", headphoneGains[0] > 0f)
        assertTrue("Headphones should boost 16kHz treble", headphoneGains[9] > 0f)
    }

    @Test
    fun biquad_filter_bank_respects_bypass_and_applies_preamp() {
        val bank = BiquadEqFilterBank()
        bank.updateParameters(
            gainsDb = List(10) { 12f },
            preampDb = 6f,
            isBypassed = true
        )
        val (bypassL, bypassR) = bank.processStereoSample(0.25, -0.25)
        assertEquals(0.25, bypassL, 1e-6)
        assertEquals(-0.25, bypassR, 1e-6)

        bank.updateParameters(
            gainsDb = List(10) { 0f },
            preampDb = 6f,
            isBypassed = false
        )
        val (boostedL, _) = bank.processStereoSample(0.25, 0.25)
        assertTrue("Preamp +6dB should boost output magnitude", abs(boostedL) > 0.35)
    }

    @Test
    fun phone_speaker_dsp_attenuates_sub_bass_and_generates_virtual_bass_harmonics() {
        val sampleRate = 44100
        val noVirtualBass = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = false)
        }
        val withVirtualBass = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = true)
        }

        // Feed pure 50Hz sub-bass sine wave (well below 110Hz HPF cutoff)
        var rmsNoVirtual = 0.0
        var rmsWithVirtual = 0.0
        val numSamples = 4410 // 100ms
        for (i in 0 until numSamples) {
            val s = 0.5 * kotlin.math.sin(2.0 * Math.PI * 50.0 * i / sampleRate)
            val (outNo, _) = noVirtualBass.processStereoSample(s, s)
            val (outYes, _) = withVirtualBass.processStereoSample(s, s)
            if (i > 2205) { // steady-state second half
                rmsNoVirtual += outNo * outNo
                rmsWithVirtual += outYes * outYes
            }
        }
        rmsNoVirtual = kotlin.math.sqrt(rmsNoVirtual / 2205)
        rmsWithVirtual = kotlin.math.sqrt(rmsWithVirtual / 2205)

        // 24dB/oct HPF at 110Hz cuts 50Hz (>1 octave below) by >24dB (<0.06x input RMS 0.353)
        assertTrue("110Hz 24dB/oct HPF should strongly attenuate 50Hz sub-bass", rmsNoVirtual < 0.06)
        // Virtual Bass should synthesize audible 120-250Hz harmonics above the HPF-only baseline
        assertTrue("Virtual Bass should add harmonic energy above HPF-only baseline", rmsWithVirtual > rmsNoVirtual * 1.4)
    }

    @Test
    fun spatial_studio_micro_ambience_ir_is_strictly_28ms_and_rapidly_decays() {
        val sampleRate = 44100
        val (irL, irR) = com.example.audio.AppleStudioMicroAmbienceConvolver.generateStudioMicroAmbienceIr(sampleRate)
        val expectedSamples = kotlin.math.round(sampleRate * 0.028).toInt()
        assertEquals(expectedSamples, irL.size)
        assertEquals(expectedSamples, irR.size)

        var earlyPeak = 0f
        var tailPeak = 0f
        for (i in 0 until irL.size / 3) {
            earlyPeak = maxOf(earlyPeak, abs(irL[i]), abs(irR[i]))
        }
        for (i in (irL.size * 3 / 4) until irL.size) {
            tailPeak = maxOf(tailPeak, abs(irL[i]), abs(irR[i]))
        }
        assertTrue("Early studio IR must have non-zero micro-reflections", earlyPeak > 0.01f)
        assertTrue("28ms IR tail should decay rapidly for zero echo", tailPeak < earlyPeak * 0.15f)
    }

    @Test
    fun spatial_engine_routes_712_hrtf_on_headphones_and_switches_to_xtc_on_speaker() {
        val sampleRate = 44100
        val engine = com.example.audio.SpatialAudioEngine(sampleRate)

        // 1. OFF mode is bit-transparent
        engine.configure(
            mode = com.example.model.SpatialMode.OFF,
            target = com.example.model.OutputTarget.HEADPHONES,
            spatialDepth = 0.8f,
            heightPresence = 0.8f,
            vocalAnchor = true
        )
        val (offL, offR) = engine.processStereoSample(0.4, -0.2)
        assertEquals(0.4, offL, 1e-6)
        assertEquals(-0.2, offR, 1e-6)

        // 2. Pure mono <120Hz bass stays symmetric/mono in Center Front (0°, 0°)
        engine.configure(
            mode = com.example.model.SpatialMode.ATMOS_MUSIC,
            target = com.example.model.OutputTarget.HEADPHONES,
            spatialDepth = 0.8f,
            heightPresence = 0.8f,
            vocalAnchor = true
        )
        var maxStereoDiffAt80Hz = 0.0
        for (i in 0 until 2000) {
            val s = 0.4 * kotlin.math.sin(2.0 * Math.PI * 80.0 * i / sampleRate)
            val (l, r) = engine.processStereoSample(s, s)
            if (i > 1000) {
                maxStereoDiffAt80Hz = maxOf(maxStereoDiffAt80Hz, abs(l - r))
            }
        }
        assertTrue("Mono 80Hz sub-bass should remain locked to center", maxStereoDiffAt80Hz < 0.02)

        // 3. Stereo 3.2kHz signal on Headphones HRTF vs Phone Speaker 0.18ms XTC produces distinct outputs
        val hrtfEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_CINEMA,
                target = com.example.model.OutputTarget.HEADPHONES,
                spatialDepth = 0.9f,
                heightPresence = 0.9f,
                vocalAnchor = true
            )
        }
        val xtcEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_CINEMA,
                target = com.example.model.OutputTarget.SPEAKER,
                spatialDepth = 0.9f,
                heightPresence = 0.9f,
                vocalAnchor = true
            )
        }
        var diffHrtfVsXtc = 0.0
        var stereoWidthXtc = 0.0
        for (i in 0 until 2000) {
            val sigL = 0.4 * kotlin.math.sin(2.0 * Math.PI * 3200.0 * i / sampleRate)
            val sigR = 0.1 * kotlin.math.sin(2.0 * Math.PI * 3200.0 * i / sampleRate)
            val (hL, _) = hrtfEngine.processStereoSample(sigL, sigR)
            val (xL, xR) = xtcEngine.processStereoSample(sigL, sigR)
            if (i > 1000) {
                diffHrtfVsXtc += abs(hL - xL)
                stereoWidthXtc += abs(xL - xR)
            }
        }
        assertTrue("Phone Speaker XTC should differ distinctly from Headphone HRTF", diffHrtfVsXtc > 10.0)
        assertTrue("Phone Speaker XTC should project wide L/R separation", stereoWidthXtc > 50.0)
    }

    @Test
    fun sound_check_normalizes_loud_and_quiet_tracks_toward_minus_16_lufs() {
        val sampleRate = 44100
        val hotNormalizer = com.example.audio.SoundCheckNormalizer(sampleRate).apply { enabled = true }
        val quietNormalizer = com.example.audio.SoundCheckNormalizer(sampleRate).apply { enabled = true }

        // Feed loud (-3 dBFS) 1kHz sine for 1.5s and quiet (-28 dBFS) 1kHz sine for 1.5s
        for (i in 0 until (sampleRate * 1.5).toInt()) {
            val loudSample = 0.707 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val quietSample = 0.040 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            hotNormalizer.processStereoSample(loudSample, loudSample)
            quietNormalizer.processStereoSample(quietSample, quietSample)
        }

        assertTrue("Loud track should receive negative trim toward -16 LUFS", hotNormalizer.latestTrimDb < -6.0f)
        assertTrue("Quiet track should receive positive boost toward -16 LUFS", quietNormalizer.latestTrimDb > 5.0f)
    }

    @Test
    fun voice_isolation_attenuates_noise_below_threshold_and_preserves_speech_band() {
        val sampleRate = 44100
        val iso = com.example.audio.VoiceIsolationProcessor(sampleRate).apply { enabled = true }

        // Feed quiet 60Hz rumble / hiss at -48 dBFS (< -38dB threshold)
        var quietOutRms = 0.0
        for (i in 0 until 8820) { // 200ms
            val quietRumble = 0.004 * kotlin.math.sin(2.0 * Math.PI * 60.0 * i / sampleRate)
            val (oL, _) = iso.processStereoSample(quietRumble, quietRumble)
            if (i > 4410) quietOutRms += oL * oL
        }
        quietOutRms = kotlin.math.sqrt(quietOutRms / 4410)
        assertTrue("Signals below -38dB + <120Hz rumble should be heavily gated", quietOutRms < 0.0008)

        // Feed clear 1.5kHz voice tone at -14 dBFS (> -38dB threshold)
        var speechOutRms = 0.0
        for (i in 0 until 4410) {
            val speech = 0.2 * kotlin.math.sin(2.0 * Math.PI * 1500.0 * i / sampleRate)
            val (oL, _) = iso.processStereoSample(speech, speech)
            if (i > 2205) speechOutRms += oL * oL
        }
        speechOutRms = kotlin.math.sqrt(speechOutRms / 2205)
        assertTrue("Speech in 120Hz-6.5kHz above -38dB should pass clearly", speechOutRms > 0.10)
    }

    @Test
    fun background_sounds_and_audiogram_eq_generator_produce_valid_outputs() {
        val synth = com.example.audio.BackgroundSoundsSynthesizer(44100)
        listOf(
            com.example.model.BackgroundSoundPreset.BALANCED_NOISE,
            com.example.model.BackgroundSoundPreset.DARK_NOISE,
            com.example.model.BackgroundSoundPreset.RAIN,
            com.example.model.BackgroundSoundPreset.OCEAN
        ).forEach { preset ->
            var energy = 0.0
            for (i in 0 until 1000) {
                val (l, r) = synth.nextStereoSample(preset, 0.5f)
                assertTrue("Sample bounded in -1..1", l in -1.0..1.0 && r in -1.0..1.0)
                energy += l * l + r * r
            }
            assertTrue("Preset ${preset.label} must produce non-silent audio", energy > 0.001)
        }

        val thresholds = listOf(-38f, -36f, -30f, -22f, -18f) // High-frequency roll-off hearing profile
        val curve = com.example.audio.AudiogramCheckEngine.computeCompensationEqFromThresholds(thresholds)
        assertEquals(10, curve.size)
        assertTrue("Higher threshold at 8kHz should yield higher boost than 500Hz", curve[8] > curve[4])
        curve.forEach { gain ->
            assertTrue("Gain $gain within -12..+12 dB", gain in -12f..12f)
        }
    }

    @Test
    fun three_band_tone_filter_targets_correct_bands_and_enforces_asymmetric_limits() {
        val sampleRate = 44100

        // 1. Bass low-shelf (<250 Hz, -7..+7 dB) boosts 100 Hz while keeping 6 kHz near unity
        val bassBoostAt100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 100.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        val bassBoostAt6kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 6000.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        assertTrue("Bass +7dB should boost 100Hz clearly", bassBoostAt100Hz > 5.0f)
        assertTrue("Bass +7dB should leave 6kHz near 0 dB", abs(bassBoostAt6kHz) < 0.3f)

        // 2. Mids peaking (1.2 kHz, spanning ~300 Hz to 4.7 kHz, -6..+3 dB) attenuates 1.2kHz while leaving 50Hz & 12kHz near unity
        val midsCutAt1200Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 1200.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        val midsCutAt50Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 50.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        val midsCutAt12kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 12000.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        assertTrue("Mids -6dB should attenuate 1.2kHz to ~-6 dB", midsCutAt1200Hz < -5.5f)
        assertTrue("Mids -6dB should leave 50Hz near unity", abs(midsCutAt50Hz) < 0.6f)
        assertTrue("Mids -6dB should leave 12kHz near unity", abs(midsCutAt12kHz) < 0.8f)

        // 3. Treble high-shelf (>3.3 kHz, -6..+7 dB) boosts 8kHz while leaving 100Hz near unity
        val trebleBoostAt8kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 8000.0,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 7.0f,
            sampleRate = sampleRate
        )
        val trebleBoostAt100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 100.0,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 7.0f,
            sampleRate = sampleRate
        )
        assertTrue("Treble +7dB should boost 8kHz clearly", trebleBoostAt8kHz > 5.0f)
        assertTrue("Treble +7dB should leave 100Hz near 0 dB", abs(trebleBoostAt100Hz) < 0.3f)

        // 4. Clamping respects asymmetric limits (Bass -7..+7, Mids -6..+3, Treble -6..+7)
        val clampedMidsOver = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 1200.0,
            bassDb = 0f,
            midsDb = 12.0f, // should clamp to +3.0 dB
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        assertEquals(3.0f, clampedMidsOver, 0.1f)

        // 5. Real-time sample processing passes cleanly when disabled and boosts when enabled
        val filter = com.example.audio.ThreeBandToneFilter(sampleRate)
        filter.configure(isEnabled = false, bassDb = 7f, midsDb = -6f, trebleDb = 7f)
        val (bypassL, bypassR) = filter.processStereoSample(0.25, -0.25)
        assertEquals(0.25, bypassL, 1e-6)
        assertEquals(-0.25, bypassR, 1e-6)

        filter.configure(isEnabled = true, bassDb = 7f, midsDb = 0f, trebleDb = 0f)
        var outRms = 0.0
        for (i in 0 until 4410) {
            val s = 0.2 * kotlin.math.sin(2.0 * Math.PI * 100.0 * i / sampleRate)
            val (oL, _) = filter.processStereoSample(s, s)
            if (i > 2205) outRms += oL * oL
        }
        outRms = kotlin.math.sqrt(outRms / 2205)
        val inRms = 0.2 / kotlin.math.sqrt(2.0)
        assertTrue("Active Bass +7dB should increase 100Hz RMS significantly", outRms > inRms * 1.8)
    }
}
