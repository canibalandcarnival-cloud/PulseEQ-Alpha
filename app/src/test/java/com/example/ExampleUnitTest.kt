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
import kotlin.math.roundToInt

class ExampleUnitTest {

    @Test
    fun all_presets_including_late_night_have_10_valid_bands() {
        assertEquals(23, EqPresetsCatalog.PRESETS.size)
        assertEquals(5, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_1 })
        assertEquals(8, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_2 })
        assertEquals(8, EqPresetsCatalog.PRESETS.count { it.group == PresetGroup.SET_3 })

        val warmBalance = EqPresetsCatalog.findById(EqPresetsCatalog.WARM_IPHONE_BALANCE_ID)
        assertEquals("Warm iPhone Balance", warmBalance?.name)

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
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC
            )
        }
        val withVirtualBass = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = true,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC
            )
        }

        // 0. Hybrid Dual-Speaker Mode defaults to enabled in Phone Speaker mode
        assertTrue(
            "Hybrid Dual-Speaker Mode must be enabled by default for Phone Speaker",
            com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.SPEAKER).landscapeStereoStage
        )

        // 1. Feed 45Hz sub-bass sine wave (well below 88Hz 24dB/oct HPF cutoff)
        var rmsNoVirtual = 0.0
        val numSamples = 4410 // 100ms
        for (i in 0 until numSamples) {
            val s = 0.45 * kotlin.math.sin(2.0 * Math.PI * 45.0 * i / sampleRate)
            val (outNo, _) = noVirtualBass.processStereoSample(s, s)
            if (i > 2205) { // steady-state second half
                rmsNoVirtual += outNo * outNo
            }
        }
        rmsNoVirtual = kotlin.math.sqrt(rmsNoVirtual / 2205)
        assertTrue("88Hz 24dB/oct HPF should strongly attenuate 45Hz sub-bass (rms=$rmsNoVirtual)", rmsNoVirtual < 0.06)

        // 2. Feed 50Hz sub-bass excitation (below 88Hz): 2nd-Order Harmonic Bass Exciter synthesizes 110-220Hz harmonics
        val baseline50 = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = false)
        }
        var rmsBase50 = 0.0
        var rmsVirtual50 = 0.0
        for (i in 0 until numSamples) {
            val s = 0.5 * kotlin.math.sin(2.0 * Math.PI * 50.0 * i / sampleRate)
            val (outNo, _) = baseline50.processStereoSample(s, s)
            val (outYes, _) = withVirtualBass.processStereoSample(s, s)
            if (i > 2205) {
                rmsBase50 += outNo * outNo
                rmsVirtual50 += outYes * outYes
            }
        }
        rmsBase50 = kotlin.math.sqrt(rmsBase50 / 2205)
        rmsVirtual50 = kotlin.math.sqrt(rmsVirtual50 / 2205)
        assertTrue(
            "2nd-Order Harmonic Bass Exciter should synthesize harmonics above HPF-only baseline ($rmsVirtual50 vs $rmsBase50)",
            rmsVirtual50 > rmsBase50 * 1.4
        )

        // 3. Acoustic Chamber & V-Curve EQ profile verification
        val resp135 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            135.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp340 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            340.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp3000 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            3000.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp4500 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            4500.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp7000 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            7000.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp16000 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            16000.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        assertTrue("135Hz Pro Max Chamber Bass Punch should boost > +2.0 dB ($resp135)", resp135 > 2.0f)
        assertTrue("340Hz lower-mid scoop should dip < -2.2 dB ($resp340)", resp340 < -2.2f)
        assertTrue("3.0kHz presence should boost > +0.75 dB ($resp3000)", resp3000 > 0.75f)
        assertTrue("4.5kHz harshness cut should dip < -2.0 dB ($resp4500)", resp4500 < -2.0f)
        assertTrue("7.0kHz surgical de-esser should dip < -2.5 dB ($resp7000)", resp7000 < -2.5f)
        assertTrue("12kHz gain-staged air shelf should stay gentle (+0.2..+1.8 dB at 16kHz, got $resp16000)", resp16000 in 0.2f..1.8f)

        // 3b. 18 Pro (Warm Spoken Dialogue) emphasizes 1.8kHz dialogue clarity while taming high air relative to Music
        val dialogue1800 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            1800.0,
            com.example.model.SpeakerTuningProfile.PRO_WARM_DIALOGUE,
            sampleRate
        )
        val music1800 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            1800.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        assertTrue("18 Pro Dialogue should emphasize 1.8kHz speech more than Music ($dialogue1800 vs $music1800)", dialogue1800 > music1800 + 2.0f)

        // 4. Hybrid Dual-Speaker Mode: keeps bass (<180Hz) tight & centered while widening >180Hz via -22% 0.19ms XTC
        val xtcOff = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = false, isLandscapeStereoEnabled = false)
        }
        val xtcOn = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = false, isLandscapeStereoEnabled = true)
        }
        var bassSideEnergy = 0.0
        var sideEnergyOff = 0.0
        var sideEnergyOn = 0.0
        for (i in 0 until 2000) {
            // Stereo 110Hz bass should be centered (low L/R diff)
            val bassL = 0.12 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate)
            val bassR = -0.12 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate)
            val (bOutL, bOutR) = xtcOn.processStereoSample(bassL, bassR)
            if (i > 1200) {
                bassSideEnergy += abs(bOutL - bOutR)
            }
        }
        assertTrue("Hybrid Dual-Speaker Mode must center <180Hz stereo bass (side=$bassSideEnergy)", bassSideEnergy < 45.0)

        for (i in 0 until 2000) {
            val sigL = 0.08 * kotlin.math.sin(2.0 * Math.PI * 600.0 * i / sampleRate)
            val sigR = 0.01 * kotlin.math.sin(2.0 * Math.PI * 600.0 * i / sampleRate)
            val (offL, offR) = xtcOff.processStereoSample(sigL, sigR)
            val (onL, onR) = xtcOn.processStereoSample(sigL, sigR)
            if (i > 1000) {
                sideEnergyOff += abs(offL - offR)
                sideEnergyOn += abs(onL - onR)
            }
        }
        assertTrue(
            "Hybrid Dual-Speaker Landscape XTC (-22% 0.19ms) should widen >180Hz L/R separation ($sideEnergyOn vs $sideEnergyOff)",
            sideEnergyOn > sideEnergyOff * 1.12
        )

        // 5. Apple Dynamic Excursion Protection (-14dB 6:1 3ms/120ms) +4.5dB makeup -> -0.3dB Brickwall Limiter
        val excursionChain = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(isSpeakerDspEnabled = true, isVirtualBassEnabled = true, isLandscapeStereoEnabled = true)
        }
        var minExcursionGr = 0f
        for (i in 0 until 2200) {
            val heavyBass = 0.70 * kotlin.math.sin(2.0 * Math.PI * 135.0 * i / sampleRate)
            excursionChain.processStereoSample(heavyBass, heavyBass)
            minExcursionGr = minOf(minExcursionGr, excursionChain.latestExcursionReductionDb)
        }
        assertTrue("Heavy 135Hz bass swell must trigger Dynamic Excursion Protection ($minExcursionGr)", minExcursionGr < -2.0f)

        var maxHotPeak = 0.0
        for (i in 0 until 1000) {
            val hot = 0.95 * kotlin.math.sin(2.0 * Math.PI * 2000.0 * i / sampleRate)
            val (oL, oR) = xtcOn.processStereoSample(hot, hot)
            maxHotPeak = maxOf(maxHotPeak, abs(oL), abs(oR))
        }
        assertTrue("Flagship -0.3dB Brickwall limiter must cap hot peaks <= 0.985 (max=$maxHotPeak)", maxHotPeak <= 0.985)
    }

    @Test
    fun spatial_studio_micro_ambience_ir_is_strictly_25ms_and_rapidly_decays() {
        val sampleRate = 44100
        val (irL, irR) = com.example.audio.AppleStudioMicroAmbienceConvolver.generateStudioMicroAmbienceIr(sampleRate)
        val expectedSamples = (sampleRate * 0.025).roundToInt()
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
        assertTrue("25ms IR tail should decay rapidly for zero echo", tailPeak < earlyPeak * 0.15f)
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
    fun three_band_tone_filter_targets_correct_bands_and_enforces_limits() {
        val sampleRate = 44100

        // 1. Low band low-shelf (250 Hz, -12..+12 dB) boosts 100 Hz while keeping 10 kHz near unity
        val bassBoostAt100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 100.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        val bassBoostAt10kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 10000.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        assertTrue("Low Band +7dB should boost 100Hz clearly", bassBoostAt100Hz > 5.0f)
        assertTrue("Low Band +7dB should leave 10kHz near 0 dB", abs(bassBoostAt10kHz) < 0.5f)

        // 2. Mid band peaking (1084 Hz, Q=0.35, spanning 250Hz to 4.7kHz, -12..+12 dB)
        val midsCutAt1084Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 1084.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate
        )
        assertTrue("Mid Band -6dB should attenuate 1084Hz", midsCutAt1084Hz < -4.0f)

        // 3. High band high-shelf (4.7 kHz, -12..+12 dB) boosts 10kHz while leaving 100Hz near unity
        val trebleBoostAt10kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 10000.0,
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
        assertTrue("High Band +7dB should boost 10kHz clearly", trebleBoostAt10kHz > 4.5f)
        assertTrue("High Band +7dB should leave 100Hz near 0 dB", abs(trebleBoostAt100Hz) < 0.5f)

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
        assertTrue("Active Low Band +7dB should increase 100Hz RMS significantly", outRms > inRms * 1.8)
    }

    @Test
    fun headphone_accommodations_tone_profiles_and_soft_sound_scaling_work_accurately() {
        val sampleRate = 44100

        // 1. Balanced Tone @ Moderate (1.0x): -2 dB headroom cut @ 300Hz, boosts capped <= +3.0 dB
        val bal300 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            300.0,
            com.example.model.AccommodationToneProfile.BALANCED_TONE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val bal8k = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            8000.0,
            com.example.model.AccommodationToneProfile.BALANCED_TONE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        assertEquals(-2.0f, bal300, 0.5f)
        assertTrue("Balanced 8kHz boost should be around +2.1 dB and <= +3.0 dB", bal8k in 1.5f..3.0f)

        // 2. Vocal Range @ Moderate (1.0x): 100Hz HPF cuts 50Hz rumble, -2.0 dB @ 250Hz, balanced boost @ 2.5kHz (<= +3.0 dB)
        val vocal50 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            50.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val vocal250 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            250.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val vocal2500 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            2500.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        assertTrue("Vocal Range 100Hz HPF must strongly attenuate 50Hz rumble", vocal50 < -10f)
        assertEquals(-2.0f, vocal250, 0.6f)
        assertTrue("Vocal Range 2.5kHz lift should stay balanced <= +3.0 dB", vocal2500 in 1.6f..3.0f)

        // 3. Brightness scaling: Slight (0.6x) < Moderate (1.0x) < Strong (1.4x) at 12kHz, all <= +3.0 dB (+0.6 dB tolerance)
        val brightSlight = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            12000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.SLIGHT,
            sampleRate
        )
        val brightMod = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            12000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val brightStrong = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            12000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.STRONG,
            sampleRate
        )
        assertTrue("Slight (0.6x) < Moderate (1.0x)", brightSlight < brightMod)
        assertTrue("Moderate (1.0x) < Strong (1.4x)", brightMod < brightStrong)
        assertTrue("Brightness Strong 12kHz peak should stay balanced (<= +3.5 dB instead of +5.5 dB)", brightStrong <= 3.5f)

        // 4. Boost Soft Sounds softened compressor thresholds, ratios, and increased makeup gain specs
        assertEquals(-24f, com.example.model.SoftSoundIntensity.SLIGHT.thresholdDb, 0.01f)
        assertEquals(1.8f, com.example.model.SoftSoundIntensity.SLIGHT.ratio, 0.01f)
        assertEquals(3.5f, com.example.model.SoftSoundIntensity.SLIGHT.makeupGainDb, 0.01f)

        assertEquals(-28f, com.example.model.SoftSoundIntensity.MODERATE.thresholdDb, 0.01f)
        assertEquals(2.2f, com.example.model.SoftSoundIntensity.MODERATE.ratio, 0.01f)
        assertEquals(6.0f, com.example.model.SoftSoundIntensity.MODERATE.makeupGainDb, 0.01f)

        assertEquals(-32f, com.example.model.SoftSoundIntensity.STRONG.thresholdDb, 0.01f)
        assertEquals(2.6f, com.example.model.SoftSoundIntensity.STRONG.ratio, 0.01f)
        assertEquals(8.5f, com.example.model.SoftSoundIntensity.STRONG.makeupGainDb, 0.01f)

        // 5. Turning Accommodations ON increases perceived loudness on normal -18 dBFS music instead of making it quieter,
        //    and Accommodations Output Gain (+0..+10 dB) dials in extra clean loudness
        val accOffBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                accommodationsEnabled = false
            )
        }
        val accModerateBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                accommodationsEnabled = true,
                accommodationsToneProfile = com.example.model.AccommodationToneProfile.BALANCED_TONE,
                accommodationsSoftIntensity = com.example.model.SoftSoundIntensity.MODERATE,
                accommodationsApplyToMedia = true,
                accommodationsOutputGainDb = 0f
            )
        }
        val accBoostedBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                accommodationsEnabled = true,
                accommodationsToneProfile = com.example.model.AccommodationToneProfile.BALANCED_TONE,
                accommodationsSoftIntensity = com.example.model.SoftSoundIntensity.MODERATE,
                accommodationsApplyToMedia = true,
                accommodationsOutputGainDb = 6.0f
            )
        }
        var rmsOff = 0.0
        var rmsMod = 0.0
        var rmsBoosted = 0.0
        for (i in 0 until 4410) {
            // Typical -18 dBFS 1 kHz track level
            val s = 0.125 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val (oOff, _) = accOffBank.processStereoSample(s, s)
            val (oMod, _) = accModerateBank.processStereoSample(s, s)
            val (oBoost, _) = accBoostedBank.processStereoSample(s, s)
            if (i > 2205) {
                rmsOff += oOff * oOff
                rmsMod += oMod * oMod
                rmsBoosted += oBoost * oBoost
            }
        }
        rmsOff = kotlin.math.sqrt(rmsOff / 2205)
        rmsMod = kotlin.math.sqrt(rmsMod / 2205)
        rmsBoosted = kotlin.math.sqrt(rmsBoosted / 2205)
        assertTrue("Turning Accommodations ON must increase loudness on -18 dBFS audio (rmsMod=$rmsMod vs rmsOff=$rmsOff)", rmsMod > rmsOff * 1.15)
        assertTrue("Accommodations Output Gain (+6 dB) should increase output further cleanly", rmsBoosted > rmsMod * 1.45)
    }

    @Test
    fun iphone_loudness_maximizer_filters_sub_bass_boosts_presence_and_brickwalls_without_clipping() {
        val sampleRate = 44100

        // 1. Safe Sub-Bass Filter: 115 Hz 24dB/oct HPF cuts 50 Hz strongly on Phone Speaker, keeps 50 Hz near 0 dB on Headphones
        val spkSub50Hz = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            freqHz = 50.0,
            target = com.example.model.OutputTarget.SPEAKER,
            sampleRate = sampleRate
        )
        val hpSub50Hz = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            freqHz = 50.0,
            target = com.example.model.OutputTarget.HEADPHONES,
            sampleRate = sampleRate
        )
        assertTrue("Speaker 115Hz 24dB/oct HPF should cut 50Hz by >14 dB", spkSub50Hz <= -14.0f)
        assertTrue("Headphones should keep 50Hz near 0 dB", abs(hpSub50Hz) < 0.8f)

        // 2. Presence & Fletcher-Munson Loudness Curve: 350Hz -2.5 dB, 1.5kHz +2.0 dB, 3kHz +3.5 dB, 8kHz +2.5 dB
        val resp350 = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            350.0,
            com.example.model.OutputTarget.HEADPHONES,
            sampleRate
        )
        val resp1500 = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            1500.0,
            com.example.model.OutputTarget.HEADPHONES,
            sampleRate
        )
        val resp3000 = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            3000.0,
            com.example.model.OutputTarget.HEADPHONES,
            sampleRate
        )
        val resp8000 = com.example.audio.IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
            8000.0,
            com.example.model.OutputTarget.HEADPHONES,
            sampleRate
        )
        assertEquals(-2.5f, resp350, 0.5f)
        assertEquals(2.0f, resp1500, 0.7f)
        assertEquals(3.5f, resp3000, 0.7f)
        assertEquals(2.5f, resp8000, 0.6f)

        // 3. Dynamic audio (-24 dBFS RMS bed + loud 0.85 transient peak) with +100% (+9 dB) boost
        // triggers Zero-Clipping Brickwall Limiter & Anti-Clip Light while capping peaks <= 0.99
        val chain = com.example.audio.IphoneLoudnessMaximizerChain(sampleRate).apply {
            configure(
                isEnabled = true,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                boostPercent = 1.0f // +100% (+9.0 dB)
            )
        }
        for (i in 0 until 4410) {
            val bed = 0.06 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            chain.processStereoSample(bed, bed)
        }
        var maxOutputAbs = 0.0
        var antiClipTriggered = false
        var minBrickwallGr = 0f
        for (i in 0 until 150) {
            val transientPeak = 0.85 * kotlin.math.sin(2.0 * Math.PI * 3000.0 * i / sampleRate)
            val (oL, oR) = chain.processStereoSample(transientPeak, transientPeak)
            maxOutputAbs = maxOf(maxOutputAbs, abs(oL), abs(oR))
            if (chain.isAntiClipActive) antiClipTriggered = true
            minBrickwallGr = minOf(minBrickwallGr, chain.latestBrickwallGrDb)
        }
        assertTrue("Hot +9dB boosted transient must never clip above 0.99", maxOutputAbs <= 0.99)
        assertTrue("Anti-Clip indicator should engage on hot boosted transient", antiClipTriggered)
        assertTrue("Brickwall limiter should apply negative gain reduction", minBrickwallGr < -1.0f)

        // 4. Integrated end-of-chain BiquadEqFilterBank boosts quiet 1kHz signal loudly and triggers anti-clip on hot input
        val bank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                loudnessMaximizerEnabled = true,
                loudnessBoostPercent = 1.0f,
                outputTarget = com.example.model.OutputTarget.SPEAKER
            )
        }
        var quietOutRms = 0.0
        for (i in 0 until 4410) {
            val s = 0.03 * kotlin.math.sin(2.0 * Math.PI * 1500.0 * i / sampleRate)
            val (oL, _) = bank.processStereoSample(s, s)
            if (i > 2205) quietOutRms += oL * oL
        }
        quietOutRms = kotlin.math.sqrt(quietOutRms / 2205)
        val quietInRms = 0.03 / kotlin.math.sqrt(2.0)
        assertTrue("Loudness Maximizer +100% must boost quiet 1.5kHz passage strongly", quietOutRms > quietInRms * 2.5)
    }

    @Test
    fun ten_band_and_three_band_eq_are_mutually_exclusive_with_zero_double_processing() {
        val sampleRate = 44100

        // Feed a 1 kHz sine wave with non-zero settings in BOTH 10-band (1 kHz = +12 dB) and 3-band (Mids = -6 dB).
        // 1) When 3-Band EQ is active (tenBandEnabled = false, threeBandEnabled = true):
        //    All 10-band filters MUST be flat-bypassed to 0 dB, so output is attenuated (~-6 dB), NOT boosted.
        val threeBandActiveBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = listOf(12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f),
                preampDb = 0f,
                isBypassed = false,
                tenBandEnabled = false,
                threeBandEnabled = true,
                threeBandBassDb = 0f,
                threeBandMidsDb = -6f,
                threeBandTrebleDb = 0f
            )
        }

        // 2) When 10-Band EQ is active (tenBandEnabled = true, threeBandEnabled = false):
        //    All 3-band filters MUST be flat-bypassed to 0 dB, so output receives full +12 dB at 1 kHz without -6 dB attenuation.
        val tenBandActiveBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 12f, 0f, 0f, 0f, 0f),
                preampDb = 0f,
                isBypassed = false,
                tenBandEnabled = true,
                threeBandEnabled = false,
                threeBandBassDb = 7f,
                threeBandMidsDb = -6f,
                threeBandTrebleDb = 7f
            )
        }

        // 3) Safety guard if both booleans are passed true: strictly zero double-processing (prefers 10-Band only).
        val bothPassedTrueBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 12f, 0f, 0f, 0f, 0f),
                preampDb = 0f,
                isBypassed = false,
                tenBandEnabled = true,
                threeBandEnabled = true,
                threeBandBassDb = 7f,
                threeBandMidsDb = -6f,
                threeBandTrebleDb = 7f
            )
        }

        var rmsThreeOnly = 0.0
        var rmsTenOnly = 0.0
        var rmsBothTrue = 0.0
        for (i in 0 until 4410) {
            val s = 0.1 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val (oThree, _) = threeBandActiveBank.processStereoSample(s, s)
            val (oTen, _) = tenBandActiveBank.processStereoSample(s, s)
            val (oBoth, _) = bothPassedTrueBank.processStereoSample(s, s)
            if (i > 2205) {
                rmsThreeOnly += oThree * oThree
                rmsTenOnly += oTen * oTen
                rmsBothTrue += oBoth * oBoth
            }
        }
        rmsThreeOnly = kotlin.math.sqrt(rmsThreeOnly / 2205)
        rmsTenOnly = kotlin.math.sqrt(rmsTenOnly / 2205)
        rmsBothTrue = kotlin.math.sqrt(rmsBothTrue / 2205)
        val inRms = 0.1 / kotlin.math.sqrt(2.0)

        assertTrue(
            "When 3-Band (-6dB Mids) is active, 10-band +12dB must be flat-bypassed (rmsThreeOnly=$rmsThreeOnly < inRms=$inRms)",
            rmsThreeOnly < inRms * 0.65
        )
        assertTrue(
            "When 10-Band (+12dB @ 1kHz) is active, 3-band -6dB Mids must be flat-bypassed (rmsTenOnly=$rmsTenOnly > inRms=$inRms)",
            rmsTenOnly > inRms * 2.5
        )
        assertEquals(
            "Passing both flags true must match 10-band-only output without double processing",
            rmsTenOnly,
            rmsBothTrue,
            1e-4
        )
    }

    @Test
    fun audio_mix_stem_shaper_separates_mid_vocals_and_side_ambience_across_modes() {
        val sampleRate = 44100

        // 1. Standard mode is bit-transparent
        val std = com.example.audio.AudioMixStemShaper(sampleRate).apply {
            mode = com.example.model.AudioMixMode.STANDARD
        }
        val (sL, sR) = std.processStereoSample(0.35, -0.15)
        assertEquals(0.35, sL, 1e-6)
        assertEquals(-0.15, sR, 1e-6)

        // Feed combined Mid vocal (2kHz centered) + Side ambient wash (1.2kHz out-of-phase L/R)
        fun measureMidAndSide(mode: com.example.model.AudioMixMode): Pair<Double, Double> {
            val shaper = com.example.audio.AudioMixStemShaper(sampleRate).apply { this.mode = mode }
            var midEnergy = 0.0
            var sideEnergy = 0.0
            for (i in 0 until 4410) {
                val midVocal = 0.30 * kotlin.math.sin(2.0 * Math.PI * 2000.0 * i / sampleRate)
                val sideAmbience = 0.25 * kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / sampleRate)
                val inL = midVocal + sideAmbience
                val inR = midVocal - sideAmbience
                val (oL, oR) = shaper.processStereoSample(inL, inR)
                if (i > 2205) {
                    val outMid = 0.5 * (oL + oR)
                    val outSide = 0.5 * (oL - oR)
                    midEnergy += outMid * outMid
                    sideEnergy += outSide * outSide
                }
            }
            return kotlin.math.sqrt(midEnergy / 2205) to kotlin.math.sqrt(sideEnergy / 2205)
        }

        val (stdMid, stdSide) = measureMidAndSide(com.example.model.AudioMixMode.STANDARD)
        val (inFrameMid, inFrameSide) = measureMidAndSide(com.example.model.AudioMixMode.IN_FRAME)
        val (studioMid, studioSide) = measureMidAndSide(com.example.model.AudioMixMode.STUDIO)
        val (cinemaMid, cinemaSide) = measureMidAndSide(com.example.model.AudioMixMode.CINEMATIC)

        // In-Frame focuses center vocals while attenuating off-axis Side wash
        assertTrue("In-Frame must preserve/focus Mid vocals ($inFrameMid vs $stdMid)", inFrameMid > stdMid * 1.1)
        assertTrue("In-Frame must attenuate Side wash ($inFrameSide vs $stdSide)", inFrameSide < stdSide * 0.5)

        // Studio aggressively gates/strips Side room reflections while anchoring dry Mid vocals
        assertTrue("Studio must preserve/focus dry Mid vocals ($studioMid vs $stdMid)", studioMid > stdMid * 1.1)
        assertTrue("Studio must strip Side room echo ($studioSide vs $inFrameSide)", studioSide < inFrameSide * 0.75)

        // Cinematic keeps anchored dialogue while expanding Side score above Standard
        assertTrue("Cinematic must anchor Mid dialogue ($cinemaMid vs $stdMid)", cinemaMid > stdMid * 1.05)
        assertTrue("Cinematic must expand Side score ($cinemaSide vs $stdSide)", cinemaSide > stdSide * 1.2)
    }

    @Test
    fun silicon_dsp_core_runs_5_hardware_stages_at_48khz() {
        val sampleRate = 48000

        // 1. Stage 1: Exact 5 ms lookahead ring buffer = 240 samples at 48 kHz
        val lookaheadCore = com.example.audio.SiliconDspCore(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.HEADPHONES)
        }
        assertEquals(240, lookaheadCore.lookaheadSamples)
        for (i in 0 until 240) {
            val (oL, oR) = lookaheadCore.processStereoSample(0.25, 0.25)
            // First 240 samples read from zero-initialized lookahead buffer
            assertEquals("Sample $i before 5ms lookahead must be zero-buffered", 0.0, oL, 1e-6)
            assertEquals("Sample $i before 5ms lookahead must be zero-buffered", 0.0, oR, 1e-6)
        }
        var postLookaheadEnergy = 0.0
        for (i in 0 until 240) {
            val (oL, _) = lookaheadCore.processStereoSample(0.25, 0.25)
            postLookaheadEnergy += abs(oL)
        }
        assertTrue("Signal must emerge after exact 240-sample 5ms lookahead", postLookaheadEnergy > 10.0)

        // 2. Stage 2 & Stage 3: Phone Speaker heavy <92Hz bass drives spring-mass-damper excursion,
        //    modulates SVF cutoff upward from 68Hz toward 122Hz, warms dual-tau voice coil (>25°C),
        //    and boosts Adaptive Class-H rail above 3.7V (Headphones stay at 24Hz SVF).
        val speakerCore = com.example.audio.SiliconDspCore(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.SPEAKER)
        }
        val headphoneCore = com.example.audio.SiliconDspCore(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.HEADPHONES)
        }
        var maxSpeakerSvfHz = 0f
        var maxExcursionPct = 0f
        var maxRailV = 0f
        for (i in 0 until 24000) { // 0.5 seconds at 48 kHz
            val s = 0.85 * kotlin.math.sin(2.0 * Math.PI * 92.0 * i / sampleRate)
            speakerCore.processStereoSample(s, s)
            headphoneCore.processStereoSample(s, s)
            maxSpeakerSvfHz = maxOf(maxSpeakerSvfHz, speakerCore.latestSvfCutoffHz)
            maxExcursionPct = maxOf(maxExcursionPct, speakerCore.latestExcursionPercent)
            maxRailV = maxOf(maxRailV, speakerCore.latestClassHRailV)
        }
        assertTrue("92Hz bass must drive membrane excursion ($maxExcursionPct%)", maxExcursionPct > 45f)
        assertTrue("Phone Speaker SVF cutoff must modulate > 85Hz ($maxSpeakerSvfHz Hz)", maxSpeakerSvfHz > 85f)
        assertTrue("Headphones SVF cutoff must settle near 24Hz (${headphoneCore.latestSvfCutoffHz} Hz)", headphoneCore.latestSvfCutoffHz < 30f)
        assertTrue("Dual-tau voice-coil temperature must rise above 25°C (${speakerCore.latestCoilTempC}°C)", speakerCore.latestCoilTempC > 28f)
        assertTrue("Adaptive Class-H rail must track upward from 3.7V ($maxRailV V)", maxRailV > 8.0f)

        // 3. Stage 3 & Stage 5: Soft-knee ceiling 0.992 + Micro-Air + 4x Catmull-Rom -0.2 dBTP brickwall on hot peaks
        val tpLimitLinear = Math.pow(10.0, -0.2 / 20.0) // ~0.9772
        val hotCore = com.example.audio.SiliconDspCore(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.HEADPHONES)
        }
        var maxHotOut = 0.0
        var minTpGrDb = 0f
        for (i in 0 until 4800) {
            // Hot 3.5 kHz + 14.5 kHz Micro-Air signal creating inter-sample overshoot above -0.2 dBTP
            val hot = 0.90 * kotlin.math.sin(2.0 * Math.PI * 3500.0 * i / sampleRate + 0.35) +
                0.70 * kotlin.math.sin(2.0 * Math.PI * 14500.0 * i / sampleRate + 0.7)
            val (oL, oR) = hotCore.processStereoSample(hot, hot)
            maxHotOut = maxOf(maxHotOut, abs(oL), abs(oR))
            minTpGrDb = minOf(minTpGrDb, hotCore.latestTruePeakGrDb)
        }
        assertTrue("4x True-Peak limiter must enforce <= -0.2 dBTP ($tpLimitLinear), actual=$maxHotOut", maxHotOut <= tpLimitLinear + 1e-4)
        assertTrue("True-Peak reduction telemetry must report negative dB ($minTpGrDb dB)", minTpGrDb < -0.35f)

        // 4. End-to-end BiquadEqFilterBank integration with siliconDspEnabled = true
        val bank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 6f,
                isBypassed = false,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                siliconDspEnabled = true
            )
        }
        var integratedPeak = 0.0
        for (i in 0 until 2400) {
            val s = 0.75 * kotlin.math.sin(2.0 * Math.PI * 100.0 * i / sampleRate)
            val (oL, oR) = bank.processStereoSample(s, s)
            integratedPeak = maxOf(integratedPeak, abs(oL), abs(oR))
        }
        assertTrue("Integrated Silicon DSP Core output must remain bounded <= 0.99 ($integratedPeak)", integratedPeak <= 0.99)
        assertTrue("Integrated BiquadEqFilterBank must expose positive excursion telemetry", bank.latestSiliconExcursionPct > 5f)
    }

    @Test
    fun default_speaker_preset_is_warm_iphone_balance() {
        val speakerProfile = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.SPEAKER)
        assertEquals(EqPresetsCatalog.WARM_IPHONE_BALANCE_ID, speakerProfile.selectedPresetId)
        assertEquals(EqPresetsCatalog.WARM_IPHONE_BALANCE_PRESET.gainsDb, speakerProfile.bandGainsDb)
        assertEquals(0.35f, speakerProfile.trebleSmoothnessPercent, 0.001f)

        val headphoneProfile = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.HEADPHONES)
        assertEquals(0.15f, headphoneProfile.trebleSmoothnessPercent, 0.001f)
    }

    @Test
    fun treble_gain_staging_budget_caps_stacked_high_boosts_to_3db() {
        val allocation = com.example.model.TrebleGainStagingBudget.allocate(
            accommodationsActive = true,
            accommodationsProfile = com.example.model.AccommodationToneProfile.BRIGHTNESS,
            accommodationsIntensity = com.example.model.SoftSoundIntensity.STRONG,
            spatialActive = true,
            spatialHeightPresence = 1.0f,
            siliconDspActive = true
        )
        assertTrue("Stacking Accommodations + Spatial + Silicon Air must engage >4kHz cap", allocation.isCapEngaged)
        assertTrue("Raw combined HF boost should exceed +3.0 dB (${allocation.rawCombinedHfDb})", allocation.rawCombinedHfDb > 3.0f)
        assertTrue(
            "Capped combined HF boost must not exceed +3.0 dB (${allocation.cappedCombinedHfDb})",
            allocation.cappedCombinedHfDb <= 3.001f
        )

        val bank = BiquadEqFilterBank(48000).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                accommodationsEnabled = true,
                accommodationsToneProfile = com.example.model.AccommodationToneProfile.BRIGHTNESS,
                accommodationsSoftIntensity = com.example.model.SoftSoundIntensity.STRONG,
                accommodationsApplyToMedia = true,
                spatialMode = com.example.model.SpatialMode.ATMOS_CINEMA,
                heightPresence = 1.0f,
                siliconDspEnabled = true
            )
        }
        assertTrue("BiquadEqFilterBank must report active >4kHz treble cap", bank.isTrebleCapActive)
        assertTrue("Reported capped boost must stay <= +3.0 dB", bank.latestTrebleCappedBoostDb <= 3.001f)
    }

    @Test
    fun treble_smoothness_processor_warms_160hz_and_attenuates_fatigue_zone() {
        val sampleRate = 48000
        val neutral160 = com.example.audio.TrebleSmoothnessProcessor.computeResponseDbAt(160.0, 0f, sampleRate)
        val warm160 = com.example.audio.TrebleSmoothnessProcessor.computeResponseDbAt(160.0, 1.0f, sampleRate)
        val warm5500 = com.example.audio.TrebleSmoothnessProcessor.computeResponseDbAt(5500.0, 1.0f, sampleRate)
        val warm9500 = com.example.audio.TrebleSmoothnessProcessor.computeResponseDbAt(9500.0, 1.0f, sampleRate)

        assertEquals(0f, neutral160, 0.001f)
        assertTrue("100% Treble Smoothness must add ~+2.0 dB warm body at 160Hz (got $warm160)", warm160 in 1.4f..2.6f)
        assertTrue("100% Treble Smoothness must attenuate 5.5kHz fatigue zone < -3.5 dB (got $warm5500)", warm5500 < -3.5f)
        assertTrue("100% Treble Smoothness must roll off 9.5kHz highs < -3.0 dB (got $warm9500)", warm9500 < -3.0f)

        val processor = com.example.audio.TrebleSmoothnessProcessor(sampleRate).apply {
            configure(isEnabled = true, smoothnessPercent = 1.0f)
        }
        var rms6k = 0.0
        for (i in 0 until 4800) {
            val s = 0.25 * kotlin.math.sin(2.0 * Math.PI * 6000.0 * i / sampleRate)
            val (oL, _) = processor.processStereoSample(s, s)
            if (i > 2400) rms6k += oL * oL
        }
        rms6k = kotlin.math.sqrt(rms6k / 2400)
        val inRms = 0.25 / kotlin.math.sqrt(2.0)
        assertTrue("Active 100% Treble Smoothness must audibly tame 6kHz sibilance/harshness ($rms6k < $inRms)", rms6k < inRms * 0.65)
    }

    @Test
    fun orientation_aware_speaker_stage_switches_portrait_vocal_focus_and_landscape_xtc() {
        val sampleRate = 48000

        val portraitBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                speakerDspEnabled = true,
                virtualBassEnabled = false,
                landscapeStereoStage = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                phoneOrientationMode = com.example.model.PhoneOrientationMode.AUTO_DETECT,
                deviceIsLandscape = false
            )
        }
        val landscapeBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                speakerDspEnabled = true,
                virtualBassEnabled = false,
                landscapeStereoStage = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                phoneOrientationMode = com.example.model.PhoneOrientationMode.AUTO_DETECT,
                deviceIsLandscape = true
            )
        }
        val manualLandscapeBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                speakerDspEnabled = true,
                landscapeStereoStage = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                phoneOrientationMode = com.example.model.PhoneOrientationMode.LANDSCAPE,
                deviceIsLandscape = false
            )
        }

        assertEquals(false, portraitBank.latestResolvedOrientationLandscape)
        assertEquals(true, landscapeBank.latestResolvedOrientationLandscape)
        assertEquals(true, manualLandscapeBank.latestResolvedOrientationLandscape)

        // Portrait focuses center vocals cleanly while equalizing L/R bass weight (<180Hz) vs Landscape XTC separation
        var portraitSideEnergy = 0.0
        var landscapeSideEnergy = 0.0
        var portraitVocalMid = 0.0
        var landscapeVocalMid = 0.0
        for (i in 0 until 4800) {
            val sigL = 0.20 * kotlin.math.sin(2.0 * Math.PI * 1900.0 * i / sampleRate)
            val sigR = 0.04 * kotlin.math.sin(2.0 * Math.PI * 1900.0 * i / sampleRate)
            val (pL, pR) = portraitBank.processStereoSample(sigL, sigR)
            val (lL, lR) = landscapeBank.processStereoSample(sigL, sigR)

            if (i > 2400) {
                portraitVocalMid += abs(0.5 * (pL + pR))
                landscapeVocalMid += abs(0.5 * (lL + lR))
                portraitSideEnergy += abs(0.5 * (pL - pR))
                landscapeSideEnergy += abs(0.5 * (lL - lR))
            }
        }

        val portraitBassBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                speakerDspEnabled = true,
                virtualBassEnabled = false,
                landscapeStereoStage = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                phoneOrientationMode = com.example.model.PhoneOrientationMode.AUTO_DETECT,
                deviceIsLandscape = false
            )
        }
        var rawBassDiff = 0.0
        var portraitBassDiff = 0.0
        for (i in 0 until 4800) {
            val bassL = 0.16 * kotlin.math.sin(2.0 * Math.PI * 95.0 * i / sampleRate)
            val bassR = -0.16 * kotlin.math.sin(2.0 * Math.PI * 95.0 * i / sampleRate)
            val (pbL, pbR) = portraitBassBank.processStereoSample(bassL, bassR)
            if (i > 2400) {
                rawBassDiff += abs(bassL - bassR)
                portraitBassDiff += abs(pbL - pbR)
            }
        }
        assertTrue(
            "Portrait must equalize L/R <180Hz bass weight cleanly (outputDiff=$portraitBassDiff vs rawDiff=$rawBassDiff)",
            portraitBassDiff < rawBassDiff * 0.20
        )
        assertTrue(
            "Portrait must focus center vocals stronger than Landscape ($portraitVocalMid vs $landscapeVocalMid)",
            portraitVocalMid > landscapeVocalMid * 1.05
        )
        assertTrue(
            "Landscape XTC must deliver wider >180Hz stereo separation than Portrait ($landscapeSideEnergy vs $portraitSideEnergy)",
            landscapeSideEnergy > portraitSideEnergy * 1.35
        )
    }

    @Test
    fun ios_volume_step_contour_and_high_volume_deharsh_guard_behave_accurately() {
        val sampleRate = 48000

        // 1. Below 40% volume (e.g. 15%): lifts 115Hz bass (<= +3dB) & 2.8kHz presence (<= +2dB)
        val low115 = com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(115.0, 0.15f, sampleRate)
        val low2800 = com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(2800.0, 0.15f, sampleRate)
        assertTrue("Low volume (<40%) must lift 115Hz bass > +1.2 dB (got $low115)", low115 > 1.2f)
        assertTrue("Low volume (<40%) must lift 2.8kHz presence > +0.8 dB (got $low2800)", low2800 > 0.8f)

        // 2. 40%-80% sweet spot (e.g. 60%): strictly flat 0 dB
        assertEquals(0f, com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(115.0, 0.60f, sampleRate), 0.001f)
        assertEquals(0f, com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(6500.0, 0.60f, sampleRate), 0.001f)

        // 3. Above 80% volume (e.g. 100%): flattens <100Hz sub-bass, boosts 1-2kHz midrange (+2dB), and cuts 5-9kHz sibilance (-3.5dB @ 6.5kHz)
        val highSub50 = com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(50.0, 1.0f, sampleRate)
        val highMid1500 = com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(1500.0, 1.0f, sampleRate)
        val highDeHarsh6500 = com.example.audio.IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(6500.0, 1.0f, sampleRate)
        assertTrue("High volume (>80%) must flatten sub-bass at 50Hz (< -2.0 dB, got $highSub50)", highSub50 < -2.0f)
        assertTrue("High volume (>80%) must boost 1.5kHz projection (> +1.4 dB, got $highMid1500)", highMid1500 > 1.4f)
        assertTrue("High-Volume De-Harshing Guard (>80%) must attenuate 6.5kHz (< -2.5 dB, got $highDeHarsh6500)", highDeHarsh6500 < -2.5f)

        val bank95 = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                masterVolumePercent = 0.95f
            )
        }
        assertTrue("BiquadEqFilterBank must report High-Volume De-Harsh Guard active at 95%", bank95.isHighVolumeDeHarshActive)
    }

    @Test
    fun pro_max_body_warmth_boosts_125hz_and_adds_second_order_harmonics() {
        val sampleRate = 48000
        val body125 = com.example.audio.PhoneSpeakerDspChain.computeBodyWarmthResponseDbAt(125.0, sampleRate)
        assertEquals(2.5f, body125, 0.2f)

        val noWarmth = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = false,
                isBodyWarmthEnabled = false
            )
        }
        val withWarmth = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = false,
                isBodyWarmthEnabled = true
            )
        }

        // Feed pure 125Hz sine wave and verify boosted fundamental + 250Hz 2nd-order harmonic energy
        var rmsNo = 0.0
        var rmsYes = 0.0
        var correlation250Yes = 0.0
        var correlation250No = 0.0
        for (i in 0 until 4800) {
            val s = 0.20 * kotlin.math.sin(2.0 * Math.PI * 125.0 * i / sampleRate)
            val (oNo, _) = noWarmth.processStereoSample(s, s)
            val (oYes, _) = withWarmth.processStereoSample(s, s)
            if (i > 2400) {
                rmsNo += oNo * oNo
                rmsYes += oYes * oYes
                val ref250 = kotlin.math.cos(2.0 * Math.PI * 250.0 * i / sampleRate)
                correlation250No += oNo * ref250
                correlation250Yes += oYes * ref250
            }
        }
        rmsNo = kotlin.math.sqrt(rmsNo / 2400)
        rmsYes = kotlin.math.sqrt(rmsYes / 2400)
        assertTrue("125Hz Body Warmth must noticeably boost 125Hz chamber warmth ($rmsYes vs $rmsNo)", rmsYes > rmsNo * 1.18)
        assertTrue("2nd-order saturation must enrich 250Hz even harmonic ($correlation250Yes vs $correlation250No)", abs(correlation250Yes) > abs(correlation250No) * 1.5)
    }

    @Test
    fun adaptive_multiband_lr4_crossovers_sum_flat_and_compress_bands_independently() {
        val sampleRate = 48000

        // 1. LR4 3-band split (<220Hz, 220Hz-3.8kHz, >3.8kHz) must reconstruct multi-tone audio cleanly without crossover dip
        val splitter = com.example.audio.AdaptiveMultibandToneBalancer(sampleRate).apply {
            configure(isEnabled = true)
        }
        var sumSqIn = 0.0
        var sumSqOut = 0.0
        for (i in 0 until 4800) {
            val s = 0.03 * (
                kotlin.math.sin(2.0 * Math.PI * 100.0 * i / sampleRate) +
                    kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sampleRate) +
                    kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / sampleRate) +
                    kotlin.math.sin(2.0 * Math.PI * 3800.0 * i / sampleRate) +
                    kotlin.math.sin(2.0 * Math.PI * 8000.0 * i / sampleRate)
                )
            val (low, mid, high) = splitter.splitBandsStereo(s, s)
            val recon = low.first + mid.first + high.first
            if (i > 2400) {
                sumSqIn += s * s
                sumSqOut += recon * recon
            }
        }
        val rmsIn = kotlin.math.sqrt(sumSqIn / 2400)
        val rmsOut = kotlin.math.sqrt(sumSqOut / 2400)
        val ratioDb = 20.0 * kotlin.math.log10(rmsOut / rmsIn)
        assertTrue("LR4 3-band sum must stay flat within +-0.35 dB across crossovers (got $ratioDb dB)", abs(ratioDb) < 0.35)

        // 2. Heavy 80Hz bass compresses Low band without ducking a simultaneous 1.2kHz Mid vocal tone
        val balancerLowTest = com.example.audio.AdaptiveMultibandToneBalancer(sampleRate).apply {
            configure(isEnabled = true)
        }
        var minLowGr = 0f
        var midGrDuringBass = 0f
        for (i in 0 until 4800) {
            val heavyBass = 0.65 * kotlin.math.sin(2.0 * Math.PI * 80.0 * i / sampleRate)
            val vocal = 0.05 * kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / sampleRate)
            balancerLowTest.processStereoSample(heavyBass + vocal, heavyBass + vocal)
            if (i > 2400) {
                minLowGr = minOf(minLowGr, balancerLowTest.latestLowGrDb)
                midGrDuringBass = minOf(midGrDuringBass, balancerLowTest.latestMidGrDb)
            }
        }
        assertTrue("Heavy 80Hz bass must compress Low band ($minLowGr dB)", minLowGr < -2.0f)
        assertTrue("Heavy 80Hz bass must NOT duck 1.2kHz vocal Mid band ($midGrDuringBass dB)", abs(midGrDuringBass) < 0.1f)

        // 3. Loud 6kHz transient compresses High band without pumping a simultaneous 1.2kHz Mid vocal tone
        val balancerHighTest = com.example.audio.AdaptiveMultibandToneBalancer(sampleRate).apply {
            configure(isEnabled = true)
        }
        var minHighGr = 0f
        var midGrDuringHigh = 0f
        for (i in 0 until 4800) {
            val snareHigh = 0.60 * kotlin.math.sin(2.0 * Math.PI * 6000.0 * i / sampleRate)
            val vocal = 0.05 * kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / sampleRate)
            balancerHighTest.processStereoSample(snareHigh + vocal, snareHigh + vocal)
            if (i > 2400) {
                minHighGr = minOf(minHighGr, balancerHighTest.latestHighGrDb)
                midGrDuringHigh = minOf(midGrDuringHigh, balancerHighTest.latestMidGrDb)
            }
        }
        assertTrue("Loud 6kHz snare/treble must compress High band ($minHighGr dB)", minHighGr < -2.0f)
        assertTrue("Loud 6kHz transient must NOT pump 1.2kHz vocal Mid band ($midGrDuringHigh dB)", abs(midGrDuringHigh) < 0.1f)
    }

    @Test
    fun speaker_phase_alignment_allpass_preserves_flat_magnitude_and_aligns_1400hz_phase_on_speaker_only() {
        val sampleRate = 48000

        // 1. Allpass magnitude response must be 0.0 dB across the spectrum (zero impact on 4.5k/7.0k de-harsh or +3dB HF cap)
        val allpass = com.example.audio.StereoBiquad().apply {
            setAllPass(
                sampleRate = sampleRate,
                freqHz = com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ,
                q = com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_Q
            )
        }
        listOf(100.0, 500.0, 1400.0, 3000.0, 4500.0, 7000.0, 12000.0).forEach { freq ->
            assertEquals("1.4kHz Allpass must keep strictly 0.0 dB magnitude at $freq Hz", 0.0, allpass.magnitudeDbAt(sampleRate, freq), 1e-4)
        }

        // Phase at center frequency 1.4 kHz must shift by ~pi radians (180 degrees)
        val phaseAtCenter = abs(allpass.phaseRadAt(sampleRate, 1400.0))
        assertTrue("1.4kHz Allpass should shift phase near 180 deg (~3.14 rad, got $phaseAtCenter)", abs(phaseAtCenter - Math.PI) < 0.08)

        // 2. SpeakerPhaseAlignmentFilter engages only when Speaker DSP is active in Phone Speaker Mode
        val phaseFilter = com.example.audio.SpeakerPhaseAlignmentFilter(sampleRate)
        phaseFilter.configure(isEnabled = true, outputTarget = com.example.model.OutputTarget.HEADPHONES, speakerDspEnabled = false)
        assertEquals(false, phaseFilter.isActive)

        val (hpL, hpR) = phaseFilter.processStereoSample(0.37, -0.21)
        assertEquals(0.37, hpL, 1e-6)
        assertEquals(-0.21, hpR, 1e-6)

        phaseFilter.configure(isEnabled = true, outputTarget = com.example.model.OutputTarget.SPEAKER, speakerDspEnabled = true)
        assertEquals(true, phaseFilter.isActive)

        // Verify integrated BiquadEqFilterBank exposes phaseAlignmentActive accurately and keeps treble cap <= +3.0 dB
        val speakerBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                speakerDspEnabled = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                accommodationsEnabled = true,
                accommodationsToneProfile = com.example.model.AccommodationToneProfile.BRIGHTNESS,
                accommodationsSoftIntensity = com.example.model.SoftSoundIntensity.STRONG,
                accommodationsApplyToMedia = true,
                spatialMode = com.example.model.SpatialMode.ATMOS_CINEMA,
                heightPresence = 1.0f,
                siliconDspEnabled = true,
                adaptiveMultibandEnabled = true,
                speakerPhaseAlignmentEnabled = true
            )
        }
        assertTrue("Phase alignment must report active in Phone Speaker mode", speakerBank.isPhaseAlignmentActive)
        assertTrue("Combined >4kHz treble cap must remain <= +3.0 dB", speakerBank.latestTrebleCappedBoostDb <= 3.001f)
    }

    @Test
    fun sound_check_dynamic_crest_controller_preserves_punch_and_tracks_crest_db() {
        val sampleRate = 48000
        val dynamicNormalizer = com.example.audio.SoundCheckNormalizer(sampleRate).apply { enabled = true }

        // Quiet bed (-26 dBFS) punctuated by sharp percussive transients (0.45 peak)
        var maxOutputAbs = 0.0
        for (i in 0 until sampleRate) {
            val isTransient = (i % 4800) in 0 until 240
            val amp = if (isTransient) 0.45 else 0.05
            val s = amp * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val (oL, oR) = dynamicNormalizer.processStereoSample(s, s)
            maxOutputAbs = maxOf(maxOutputAbs, abs(oL), abs(oR))
        }

        assertTrue("Dynamic Crest Controller should report positive crest factor > 3 dB (got ${dynamicNormalizer.latestCrestDb})", dynamicNormalizer.latestCrestDb > 3.0f)
        assertTrue("Soft-crest guard must keep hot normalized transients <= 0.96 (max=$maxOutputAbs)", maxOutputAbs <= 0.961)
    }
}
