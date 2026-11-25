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

        // 4. Hybrid Dual-Speaker Mode: keeps bass (<180Hz) tight & centered while widening >180Hz via 2nd-Order -22% 0.18ms XTC
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
            "Hybrid Dual-Speaker Landscape 2nd-Order XTC (-22% 0.18ms) should widen >180Hz L/R separation ($sideEnergyOn vs $sideEnergyOff)",
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
    fun spatial_studio_early_reflection_ir_is_strictly_18ms_and_rapidly_decays() {
        val sampleRate = 48000
        val (irL, irR) = com.example.audio.AppleStudioMicroAmbienceConvolver.generateStudioMicroAmbienceIr(sampleRate)
        val expectedSamples = (sampleRate * 0.018).roundToInt()
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
        assertTrue("Early 18ms studio IR must have non-zero early reflections", earlyPeak > 0.01f)
        assertTrue("18ms IR tail should decay rapidly for zero bathroom reverb", tailPeak < earlyPeak * 0.16f)
    }

    @Test
    fun spatial_asar_locks_dry_sub_bass_and_vocal_center_and_engages_714_hrtf_or_2nd_order_xtc() {
        val sampleRate = 48000
        val engine = com.example.audio.SpatialAudioEngine(sampleRate)

        // 1. OFF mode is bit-transparent
        engine.configure(
            mode = com.example.model.SpatialMode.OFF,
            target = com.example.model.OutputTarget.HEADPHONES,
            vocalFocus = 0.8f
        )
        val (offL, offR) = engine.processStereoSample(0.4, -0.2)
        assertEquals(0.4, offL, 1e-6)
        assertEquals(-0.2, offR, 1e-6)

        // 2. Dry Center Sub-Bass (<150Hz) and Center Vocal Formant (1–3.5kHz) stay strictly mono with zero reverb
        engine.configure(
            mode = com.example.model.SpatialMode.ATMOS_MUSIC,
            target = com.example.model.OutputTarget.HEADPHONES,
            vocalFocus = 1.0f
        )
        var maxStereoDiffAt80Hz = 0.0
        for (i in 0 until 2000) {
            val s = 0.4 * kotlin.math.sin(2.0 * Math.PI * 80.0 * i / sampleRate)
            val (l, r) = engine.processStereoSample(s, s)
            if (i > 1000) {
                maxStereoDiffAt80Hz = maxOf(maxStereoDiffAt80Hz, abs(l - r))
            }
        }
        assertTrue("Mono 80Hz sub-bass should remain strictly dry & centered", maxStereoDiffAt80Hz < 1e-4)

        var maxStereoDiffAt2k = 0.0
        for (i in 0 until 2000) {
            val s = 0.3 * kotlin.math.sin(2.0 * Math.PI * 2000.0 * i / sampleRate)
            val (l, r) = engine.processStereoSample(s, s)
            if (i > 1000) {
                maxStereoDiffAt2k = maxOf(maxStereoDiffAt2k, abs(l - r))
            }
        }
        assertTrue("Mono 2kHz center vocal formant must remain strictly dry & centered", maxStereoDiffAt2k < 1e-3)

        // 3. Higher Vocal Focus increases dry center 2kHz intimacy
        val lowFocusEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_MUSIC,
                target = com.example.model.OutputTarget.HEADPHONES,
                vocalFocus = 0.0f
            )
        }
        val highFocusEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_MUSIC,
                target = com.example.model.OutputTarget.HEADPHONES,
                vocalFocus = 1.0f
            )
        }
        var lowFocusRms = 0.0
        var highFocusRms = 0.0
        for (i in 0 until 2000) {
            val v = 0.3 * kotlin.math.sin(2.0 * Math.PI * 1900.0 * i / sampleRate)
            val (ll, _) = lowFocusEngine.processStereoSample(v, v)
            val (hl, _) = highFocusEngine.processStereoSample(v, v)
            if (i > 1000) {
                lowFocusRms += ll * ll
                highFocusRms += hl * hl
            }
        }
        assertTrue("100% Vocal Focus should elevate intimate center vocal clarity over 0%", highFocusRms > lowFocusRms * 1.15)

        // 4. Built-In Speaker 2nd-Order XTC (-26% @ 0.17ms 4.5kHz LPF + +12% @ 0.34ms) vs Headphone 7.1.4 HRTF
        val hrtfEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_CINEMA,
                target = com.example.model.OutputTarget.HEADPHONES,
                vocalFocus = 0.7f
            )
        }
        val xtcEngine = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_CINEMA,
                target = com.example.model.OutputTarget.SPEAKER,
                vocalFocus = 0.7f
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
        assertTrue("Speaker 2nd-Order XTC should differ distinctly from Headphone 7.1.4 HRTF", diffHrtfVsXtc > 10.0)
        assertTrue("Speaker 2nd-Order XTC should project wide L/R separation", stereoWidthXtc > 50.0)

        // 5. Verify 2nd-order XTC impulse timing: primary LPF'd -26% crossfeed around 0.17ms (~8-14 samples) and +12% bounce at 0.34ms (~16 samples)
        val xtc = com.example.audio.AcousticCrosstalkCanceller(sampleRate)
        val xtcImp = DoubleArray(32)
        for (n in 0 until 32) {
            val (l, _) = xtc.processSideChannel(if (n == 0) 1.0 else 0.0, intensityScale = 1.0)
            xtcImp[n] = l
        }
        val primaryArea = (7..15).sumOf { xtcImp[it] }
        val secondarySample = xtcImp[16]
        assertTrue("2nd-order XTC must emit ~26% LPF-smoothed crossfeed around 0.17ms (area=$primaryArea)", primaryArea in 0.18..0.30)
        assertTrue("2nd-order XTC must emit +12% secondary bounce at 0.34ms (got $secondarySample)", secondarySample > 0.04)
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

        // 1. Linear Independent Low band low-shelf (250 Hz, -7..+7 dB) boosts 100 Hz while keeping 10 kHz near unity
        val bassBoostAt100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 100.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = false
        )
        val bassBoostAt10kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 10000.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = false
        )
        assertTrue("Low Band +7dB should boost 100Hz clearly", bassBoostAt100Hz > 5.0f)
        assertTrue("Low Band +7dB should leave 10kHz near 0 dB", abs(bassBoostAt10kHz) < 0.5f)

        // 2. Linear Independent Mid band peaking (1084 Hz, Q=0.35, -7..+7 dB)
        val midsCutAt1084Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 1084.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = false
        )
        assertTrue("Mid Band -6dB should attenuate 1084Hz", midsCutAt1084Hz < -4.0f)

        // 3. Linear Independent High band high-shelf (4.7 kHz, -7..+7 dB) boosts 10kHz while leaving 100Hz near unity
        val trebleBoostAt10kHz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 10000.0,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 7.0f,
            sampleRate = sampleRate,
            smartCurveEnabled = false
        )
        val trebleBoostAt100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 100.0,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 7.0f,
            sampleRate = sampleRate,
            smartCurveEnabled = false
        )
        assertTrue("High Band +7dB should boost 10kHz clearly", trebleBoostAt10kHz > 4.5f)
        assertTrue("High Band +7dB in Linear Independent must leave 100Hz near 0 dB", abs(trebleBoostAt100Hz) < 0.5f)

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
        val sampleRate = 48000

        // 1. Balanced Tone @ Moderate (1.0x): 1kHz +1.5dB, 3kHz +2.5dB, 8kHz +3.2dB, 12kHz +2.5dB
        val bal1k = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            1000.0,
            com.example.model.AccommodationToneProfile.BALANCED_TONE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val bal3k = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            3000.0,
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
        assertTrue("Balanced 1kHz tilt should be around +1.5 dB ($bal1k)", bal1k in 1.2f..2.1f)
        assertTrue("Balanced 3kHz tilt with 3.2kHz -1.0dB MediaTek smoothness dip should be around +1.6 dB ($bal3k)", bal3k in 1.2f..2.2f)
        assertTrue("Balanced 8kHz tilt should be around +3.2 dB ($bal8k)", bal8k in 2.8f..4.2f)

        val raw3200 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            3200.0,
            com.example.model.AccommodationToneProfile.BALANCED_TONE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate,
            isHeadphoneOrBluetooth = false
        )
        val mediatek3200 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            3200.0,
            com.example.model.AccommodationToneProfile.BALANCED_TONE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate,
            isHeadphoneOrBluetooth = true
        )
        assertEquals("MediaTek wired/BT smoothness dip at 3.2kHz must be -1.0 dB", -1.0f, mediatek3200 - raw3200, 0.15f)

        // 2. Vocal Range @ Moderate (1.0x): 110Hz HPF cuts 50Hz rumble, 280Hz -2dB, 1.5kHz +3dB, 3kHz +3.8dB - 1.0dB MediaTek dip, 6kHz +1.5dB
        val vocal50 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            50.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val vocal280 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            280.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val vocal3k = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            3000.0,
            com.example.model.AccommodationToneProfile.VOCAL_RANGE,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        assertTrue("Vocal Range 110Hz HPF must strongly attenuate 50Hz rumble ($vocal50)", vocal50 < -10f)
        assertEquals(-2.0f, vocal280, 0.6f)
        assertTrue("Vocal Range 3kHz speech boost with 3.2kHz MediaTek dip should be clean & strong ($vocal3k)", vocal3k in 2.4f..3.6f)

        // 3. Brightness: Neutral bass/mids (0 dB < 1 kHz) and scaling Off < Slight < Moderate < Strong at 14kHz
        val brightLow250 = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            250.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val brightOff = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            14000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.OFF,
            sampleRate
        )
        val brightSlight = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            14000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.SLIGHT,
            sampleRate
        )
        val brightMod = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            14000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.MODERATE,
            sampleRate
        )
        val brightStrong = com.example.audio.HeadphoneAccommodationsProcessor.computeResponseDbAt(
            14000.0,
            com.example.model.AccommodationToneProfile.BRIGHTNESS,
            com.example.model.SoftSoundIntensity.STRONG,
            sampleRate
        )
        assertEquals("Brightness must remain 0 dB neutral below 1 kHz", 0.0f, brightLow250, 0.15f)
        assertTrue("Off < Slight < Moderate < Strong", brightOff < brightSlight && brightSlight < brightMod && brightMod < brightStrong)

        // 4. Parallel Soft-Sound Upward Lift specs: Off 0%/0dB, Slight 18%/+2.0dB, Moderate 32%/+3.8dB, Strong 48%/+5.5dB
        assertEquals(0.0f, com.example.model.SoftSoundIntensity.OFF.parallelMix, 0.001f)
        assertEquals(0.0f, com.example.model.SoftSoundIntensity.OFF.clarityGainDb, 0.01f)
        assertEquals(0.18f, com.example.model.SoftSoundIntensity.SLIGHT.parallelMix, 0.001f)
        assertEquals(2.0f, com.example.model.SoftSoundIntensity.SLIGHT.clarityGainDb, 0.01f)
        assertEquals(0.32f, com.example.model.SoftSoundIntensity.MODERATE.parallelMix, 0.001f)
        assertEquals(3.8f, com.example.model.SoftSoundIntensity.MODERATE.clarityGainDb, 0.01f)
        assertEquals(0.48f, com.example.model.SoftSoundIntensity.STRONG.parallelMix, 0.001f)
        assertEquals(5.5f, com.example.model.SoftSoundIntensity.STRONG.clarityGainDb, 0.01f)
        assertEquals(-38.0f, com.example.model.SoftSoundIntensity.STRONG.thresholdDb, 0.01f)
        assertEquals(3.5f, com.example.model.SoftSoundIntensity.STRONG.ratio, 0.01f)

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
        for (i in 0 until 4800) {
            // Typical -18 dBFS 1 kHz track level
            val s = 0.125 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val (oOff, _) = accOffBank.processStereoSample(s, s)
            val (oMod, _) = accModerateBank.processStereoSample(s, s)
            val (oBoost, _) = accBoostedBank.processStereoSample(s, s)
            if (i > 2400) {
                rmsOff += oOff * oOff
                rmsMod += oMod * oMod
                rmsBoosted += oBoost * oBoost
            }
        }
        rmsOff = kotlin.math.sqrt(rmsOff / 2400)
        rmsMod = kotlin.math.sqrt(rmsMod / 2400)
        rmsBoosted = kotlin.math.sqrt(rmsBoosted / 2400)
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
        //    modulates SVF cutoff upward from 86Hz toward 120Hz, warms dual-tau voice coil (>25°C),
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

        // Phase at center frequency 1.45 kHz must shift by ~pi radians (180 degrees)
        val phaseAtCenter = abs(allpass.phaseRadAt(sampleRate, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ))
        assertTrue("1.45kHz Allpass should shift phase near 180 deg (~3.14 rad, got $phaseAtCenter)", abs(phaseAtCenter - Math.PI) < 0.08)

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

    @Test
    fun studio_hires_specs_enforce_48khz_and_playback_buffer_stability() {
        assertEquals(48000, com.example.model.StudioHiResSpecs.SAMPLE_RATE_HZ)
        assertTrue(
            "Playback buffer multiplier should prioritize glitch-free stability (>= 4x)",
            com.example.model.StudioHiResSpecs.PLAYBACK_BUFFER_MULTIPLIER >= 4
        )
        val profile = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.HEADPHONES)
        assertEquals(true, profile.oversampledEqEnabled)
        assertEquals(0.25f, profile.harmonicSheenPercent, 0.001f)
        assertEquals(true, profile.noiseFloorGateEnabled)
    }

    @Test
    fun oversampled_upper_eq_reduces_nyquist_cramping_and_preserves_mutual_exclusion() {
        val sampleRate = 48000

        // 1. 1x 48kHz biquad vs 2x 96kHz oversampled peaking EQ at 16kHz (+6dB): check 19kHz anti-cramping extension
        val oneX16k = com.example.audio.StereoBiquad().apply {
            setPeaking(sampleRate, 16000.0, 1.414, 6.0)
        }
        val mag1xAt19k = oneX16k.magnitudeDbAt(sampleRate, 19000.0)
        val mag2xAt19k = com.example.audio.OversampledUpperEqStage.compute2xTenBandUpperResponseDbAt(
            freqHz = 19000.0,
            gain8kDb = 0.0,
            gain16kDb = 6.0,
            baseSampleRate = sampleRate
        )
        assertTrue(
            "2x oversampled 16kHz EQ should preserve more open 19kHz response ($mag2xAt19k dB) than 1x cramped biquad ($mag1xAt19k dB)",
            mag2xAt19k > mag1xAt19k + 0.5
        )

        // 2. 1x 48kHz High Shelf vs 2x 96kHz High Shelf (4.7kHz +6dB): verify 2x matches ideal analog slope far closer than cramped 1x
        val oneXShelf = com.example.audio.StereoBiquad().apply {
            setHighShelf(sampleRate, 4700.0, 0.7071, 6.0)
        }
        val probeHz = 12000.0
        val shelf1xAtProbe = oneXShelf.magnitudeDbAt(sampleRate, probeHz)
        val shelf2xAtProbe = com.example.audio.OversampledUpperEqStage.compute2xThreeBandHighShelfDbAt(
            freqHz = probeHz,
            trebleDb = 6.0,
            baseSampleRate = sampleRate
        )
        // Compute 8x oversampled reference (virtually zero bilinear cramping) as continuous-time analog ground truth
        val analogShelfRef = com.example.audio.StereoBiquad().apply {
            setHighShelf(sampleRate * 8, 4700.0, 0.7071, 6.0)
        }.magnitudeDbAt(sampleRate * 8, probeHz)
        val err1x = kotlin.math.abs(shelf1xAtProbe - analogShelfRef)
        val err2x = kotlin.math.abs(shelf2xAtProbe - analogShelfRef)
        assertTrue(
            "2x oversampled 4.7kHz high shelf error ($err2x dB) must be significantly smaller than cramped 1x error ($err1x dB) vs analog ($analogShelfRef dB)",
            err2x < err1x * 0.45
        )

        // 3. Strict mutual exclusion in BiquadEqFilterBank with oversampledEqEnabled = true
        val threeBandActive = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 12f },
                preampDb = 0f,
                isBypassed = false,
                tenBandEnabled = false,
                threeBandEnabled = true,
                threeBandBassDb = 0f,
                threeBandMidsDb = 0f,
                threeBandTrebleDb = -6f,
                oversampledEqEnabled = true
            )
        }
        val tenBandActive = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 10f),
                preampDb = 0f,
                isBypassed = false,
                tenBandEnabled = true,
                threeBandEnabled = false,
                threeBandBassDb = 0f,
                threeBandMidsDb = 0f,
                threeBandTrebleDb = -6f,
                oversampledEqEnabled = true
            )
        }

        var rms3 = 0.0
        var rms10 = 0.0
        for (i in 0 until 4800) {
            val s = 0.08 * kotlin.math.sin(2.0 * Math.PI * 16000.0 * i / sampleRate)
            val (o3, _) = threeBandActive.processStereoSample(s, s)
            val (o10, _) = tenBandActive.processStereoSample(s, s)
            if (i > 2400) {
                rms3 += o3 * o3
                rms10 += o10 * o10
            }
        }
        rms3 = kotlin.math.sqrt(rms3 / 2400)
        rms10 = kotlin.math.sqrt(rms10 / 2400)
        val inRms = 0.08 / kotlin.math.sqrt(2.0)

        assertTrue("When 3-Band -6dB Treble is active at 2x, 10-band +12dB must stay bypassed ($rms3 < $inRms)", rms3 < inRms * 0.7)
        assertTrue("When 10-Band +10dB @ 16kHz is active at 2x, 3-band -6dB Treble must stay bypassed ($rms10 > $inRms)", rms10 > inRms * 1.8)
    }

    @Test
    fun harmonic_sheen_adds_clean_even_harmonics_under_0_4_percent_thd() {
        val sampleRate = 48000
        val sheen = com.example.audio.HarmonicSheenAndStereoFocusProcessor(sampleRate).apply {
            configure(isEnabled = true, percent = 1.0f)
        }

        var corr2ndCos = 0.0
        var corr2ndSin = 0.0
        var corr3rdCos = 0.0
        var corr3rdSin = 0.0
        for (i in 0 until 9600) {
            val sig = 0.35 * kotlin.math.sin(2.0 * Math.PI * 3000.0 * i / sampleRate)
            val (oL, _) = sheen.processStereoSample(sig, sig)
            if (i > 4800) {
                corr2ndCos += oL * kotlin.math.cos(2.0 * Math.PI * 6000.0 * i / sampleRate)
                corr2ndSin += oL * kotlin.math.sin(2.0 * Math.PI * 6000.0 * i / sampleRate)
                corr3rdCos += oL * kotlin.math.cos(2.0 * Math.PI * 9000.0 * i / sampleRate)
                corr3rdSin += oL * kotlin.math.sin(2.0 * Math.PI * 9000.0 * i / sampleRate)
            }
        }
        val amp2nd = kotlin.math.hypot(corr2ndCos, corr2ndSin) / 2400.0
        val amp3rd = kotlin.math.hypot(corr3rdCos, corr3rdSin) / 2400.0

        assertTrue("2nd-order even harmonic @ 6kHz must be present ($amp2nd)", amp2nd > 0.0001)
        assertTrue("3rd-order odd harmonic harshness @ 9kHz must stay negligible ($amp3rd vs $amp2nd)", amp3rd < amp2nd * 0.1)
        assertTrue(
            "Analog Harmonic Sheen THD must stay strictly below 0.40% (got ${sheen.latestThdPercent}%)",
            sheen.latestThdPercent in 0.01f..com.example.model.StudioHiResSpecs.HARMONIC_SHEEN_MAX_THD_PCT
        )
    }

    @Test
    fun zero_noise_floor_gate_auto_mutes_below_minus_75dbfs_and_dithers_live_signal() {
        val sampleRate = 48000
        val gate = com.example.audio.ZeroNoiseFloorGateAndDitherer(sampleRate).apply {
            enabled = true
        }

        // 1. Live -20 dBFS signal opens gate and stays unmuted with active TPDF dithering
        var maxLivePcm = 0
        for (i in 0 until 2400) {
            val live = 0.1 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            val (oL, _) = gate.processStereoSample(live, live)
            maxLivePcm = maxOf(maxLivePcm, abs(gate.quantize16BitPcm(oL, isLeft = true).toInt()))
        }
        assertEquals(false, gate.isAutoMuted)
        assertTrue("Live -20 dBFS audio must pass cleanly through open gate", maxLivePcm > 2500)

        // 2. Ultra-quiet noise below -75 dBFS (-84 dBFS ~ 6.3e-5) closes gate after 120ms hold + 180ms release -> exact 0.0
        val hissAmp = Math.pow(10.0, -84.0 / 20.0)
        var lastL = 1.0
        var lastR = 1.0
        var lastPcm: Short = 1
        for (i in 0 until (sampleRate * 0.6).toInt()) {
            val hiss = hissAmp * kotlin.math.sin(2.0 * Math.PI * 3000.0 * i / sampleRate)
            val (oL, oR) = gate.processStereoSample(hiss, -hiss)
            lastL = oL
            lastR = oR
            lastPcm = gate.quantize16BitPcm(oL, isLeft = true)
        }
        assertEquals(true, gate.isAutoMuted)
        assertEquals(0.0, lastL, 0.0)
        assertEquals(0.0, lastR, 0.0)
        assertEquals(0.toShort(), lastPcm)
    }

    @Test
    fun listening_mode_off_and_noise_cancellation_apply_neutral_and_isolation_focus_curves() {
        val sampleRate = 48000
        val processor = com.example.audio.ListeningModeProcessor(sampleRate)

        // 1. Off: Pure bit-transparent music and zero ambient mic pass-through
        processor.configure(
            mode = com.example.model.ListeningMode.OFF,
            outputTarget = com.example.model.OutputTarget.HEADPHONES,
            isBypassed = false,
            ambientPassThroughPercent = 0.8f
        )
        val (offOutL, offOutR) = processor.processStereoSampleWithAmbient(0.32, -0.18, 0.4)
        assertEquals(0.32, offOutL, 1e-6)
        assertEquals(-0.18, offOutR, 1e-6)

        // 2. Noise Cancellation (Active Isolation Focus Curve: 110 Hz +2.0 dB, 320 Hz -2.0 dB, 2.2 kHz +1.5 dB)
        val resp110 = com.example.audio.ListeningModeProcessor.computeMusicResponseDbAt(
            freqHz = 110.0,
            mode = com.example.model.ListeningMode.NOISE_CANCELLATION,
            sampleRate = sampleRate
        )
        val resp320 = com.example.audio.ListeningModeProcessor.computeMusicResponseDbAt(
            freqHz = 320.0,
            mode = com.example.model.ListeningMode.NOISE_CANCELLATION,
            sampleRate = sampleRate
        )
        val resp2200 = com.example.audio.ListeningModeProcessor.computeMusicResponseDbAt(
            freqHz = 2200.0,
            mode = com.example.model.ListeningMode.NOISE_CANCELLATION,
            sampleRate = sampleRate
        )
        assertTrue("ANC 110 Hz warmth should boost > +1.3 dB (got $resp110)", resp110 > 1.3f)
        assertTrue("ANC 320 Hz mud cut should dip < -1.3 dB (got $resp320)", resp320 < -1.3f)
        assertTrue("ANC 2.2 kHz vocal clarity should boost > +1.0 dB (got $resp2200)", resp2200 > 1.0f)

        // Ensure ANC strictly mutes ambient mic pass-through to 0.0 on music
        processor.reset()
        processor.configure(
            mode = com.example.model.ListeningMode.NOISE_CANCELLATION,
            outputTarget = com.example.model.OutputTarget.HEADPHONES,
            isBypassed = false,
            ambientPassThroughPercent = 1.0f
        )
        var maxAmbientLeak = 0.0
        for (i in 0 until 2400) {
            val mic = 0.4 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / sampleRate)
            maxAmbientLeak = maxOf(maxAmbientLeak, abs(processor.processAmbientMicSample(mic)))
        }
        assertEquals(0.0, maxAmbientLeak, 0.0)
    }

    @Test
    fun listening_mode_transparency_filters_wind_rumble_boosts_vocals_and_scales_with_level() {
        val sampleRate = 48000
        val highPassCheck = com.example.audio.ListeningModeProcessor(sampleRate).apply {
            configure(
                mode = com.example.model.ListeningMode.TRANSPARENCY,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                isBypassed = false,
                ambientPassThroughPercent = 1.0f
            )
        }
        val lowSliderCheck = com.example.audio.ListeningModeProcessor(sampleRate).apply {
            configure(
                mode = com.example.model.ListeningMode.TRANSPARENCY,
                outputTarget = com.example.model.OutputTarget.BLUETOOTH,
                isBypassed = false,
                ambientPassThroughPercent = 0.25f
            )
        }

        // Feed 60 Hz wind/rumble vs 2.5 kHz voice into 100% Transparency
        var windRms = 0.0
        var vocalFullRms = 0.0
        var vocalQuarterRms = 0.0
        for (i in 0 until sampleRate) {
            val wind = 0.3 * kotlin.math.sin(2.0 * Math.PI * 60.0 * i / sampleRate)
            val windOut = highPassCheck.processAmbientMicSample(wind)
            if (i > sampleRate / 2) windRms += windOut * windOut
        }
        windRms = kotlin.math.sqrt(windRms / (sampleRate / 2))

        highPassCheck.reset()
        for (i in 0 until sampleRate) {
            val voice = 0.3 * kotlin.math.sin(2.0 * Math.PI * 2500.0 * i / sampleRate)
            val out100 = highPassCheck.processAmbientMicSample(voice)
            val out25 = lowSliderCheck.processAmbientMicSample(voice)
            if (i > sampleRate / 2) {
                vocalFullRms += out100 * out100
                vocalQuarterRms += out25 * out25
            }
        }
        vocalFullRms = kotlin.math.sqrt(vocalFullRms / (sampleRate / 2))
        vocalQuarterRms = kotlin.math.sqrt(vocalQuarterRms / (sampleRate / 2))

        assertTrue("220 Hz HPF must strongly attenuate 60 Hz wind/rumble relative to 2.5 kHz vocals ($windRms vs $vocalFullRms)", windRms < vocalFullRms * 0.2)
        assertTrue("2.5 kHz +1.5 dB vocal presence should pass clearly above unity input RMS", vocalFullRms > (0.3 / kotlin.math.sqrt(2.0)) * 1.1)
        assertTrue("100% Ambient Pass-Through slider must scale strongly above 25% ($vocalFullRms vs $vocalQuarterRms)", vocalFullRms > vocalQuarterRms * 3.0)
    }

    @Test
    fun listening_mode_adaptive_blends_25pct_quiet_and_ducks_on_loud_rumble_with_120hz_warmth() {
        val sampleRate = 48000
        val adaptive = com.example.audio.ListeningModeProcessor(sampleRate).apply {
            configure(
                mode = com.example.model.ListeningMode.ADAPTIVE,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                isBypassed = false,
                ambientPassThroughPercent = 1.0f
            )
        }

        // 1. Quiet room (-52 dBFS @ 1.2 kHz): blends ~25% awareness with 0 warmth ratio
        for (i in 0 until sampleRate) {
            val quiet = 0.003 * kotlin.math.sin(2.0 * Math.PI * 1200.0 * i / sampleRate)
            adaptive.processAmbientMicSample(quiet)
        }
        assertEquals(0.25f, adaptive.latestPassThroughGain, 0.03f)
        assertEquals(false, adaptive.isAdaptiveRumbleDuckActive)

        // 2. Loud <400 Hz rumble spike (-10 dBFS @ 120 Hz): ducks ambient pass-through well below 25% & engages +2.0 dB warmth @ 120 Hz
        for (i in 0 until sampleRate) {
            val rumble = 0.35 * kotlin.math.sin(2.0 * Math.PI * 120.0 * i / sampleRate)
            adaptive.processAmbientMicSample(rumble)
        }
        assertTrue(
            "Adaptive loud <400 Hz rumble must duck ambient pass-through well below 0.25 (got ${adaptive.latestPassThroughGain})",
            adaptive.latestPassThroughGain < 0.08f
        )
        assertEquals(true, adaptive.isAdaptiveRumbleDuckActive)
        assertTrue("Adaptive warmth ratio should climb near 1.0 during rumble spike", adaptive.latestAdaptiveWarmthRatio > 0.7f)

        var dry120Rms = 0.0
        var warmed120Rms = 0.0
        for (i in 0 until 4800) {
            val music = 0.1 * kotlin.math.sin(2.0 * Math.PI * 120.0 * i / sampleRate)
            val (outL, _) = adaptive.processMusicSample(music, music)
            if (i > 2400) {
                dry120Rms += music * music
                warmed120Rms += outL * outL
            }
        }
        dry120Rms = kotlin.math.sqrt(dry120Rms / 2400)
        warmed120Rms = kotlin.math.sqrt(warmed120Rms / 2400)
        assertTrue("Adaptive noise spike should apply +2.0 dB warmth boost @ 120 Hz ($warmed120Rms > $dry120Rms)", warmed120Rms > dry120Rms * 1.15)
    }

    @Test
    fun safe_feedback_protection_strictly_mutes_mic_passthrough_on_phone_speaker() {
        val sampleRate = 48000
        val guard = com.example.audio.ListeningModeProcessor(sampleRate)

        // Speaker + Transparency MUST strictly output 0.0 ambient pass-through
        guard.configure(
            mode = com.example.model.ListeningMode.TRANSPARENCY,
            outputTarget = com.example.model.OutputTarget.SPEAKER,
            isBypassed = false,
            ambientPassThroughPercent = 1.0f,
            conversationSpeechActive = true
        )
        assertEquals(true, guard.isFeedbackProtectionEngaged)
        var maxSpeakerOut = 0.0
        for (i in 0 until 4800) {
            val mic = 0.5 * kotlin.math.sin(2.0 * Math.PI * 1500.0 * i / sampleRate)
            maxSpeakerOut = maxOf(maxSpeakerOut, abs(guard.processAmbientMicSample(mic)))
        }
        assertEquals("Phone Speaker MUST mute all ambient mic pass-through to 0.0", 0.0, maxSpeakerOut, 0.0)
        assertEquals(0f, guard.latestPassThroughGain, 0f)

        // Switching to Headphones unlocks pass-through immediately
        guard.configure(
            mode = com.example.model.ListeningMode.TRANSPARENCY,
            outputTarget = com.example.model.OutputTarget.HEADPHONES,
            isBypassed = false,
            ambientPassThroughPercent = 0.8f
        )
        assertEquals(false, guard.isFeedbackProtectionEngaged)
        var maxHpOut = 0.0
        for (i in 0 until 4800) {
            val mic = 0.3 * kotlin.math.sin(2.0 * Math.PI * 1500.0 * i / sampleRate)
            maxHpOut = maxOf(maxHpOut, abs(guard.processAmbientMicSample(mic)))
        }
        assertTrue("Switching to Headphones must unlock live ambient mic pass-through ($maxHpOut)", maxHpOut > 0.1)
    }

    @Test
    fun biquad_filter_bank_applies_anc_isolation_focus_and_conversation_ducking() {
        val sampleRate = 48000
        val bank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 0f,
                isBypassed = false,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                siliconDspEnabled = false,
                adaptiveMultibandEnabled = false,
                listeningMode = com.example.model.ListeningMode.NOISE_CANCELLATION
            )
        }
        var in110Rms = 0.0
        var out110Rms = 0.0
        for (i in 0 until 9600) {
            val s = 0.12 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate)
            val (oL, _) = bank.processStereoSample(s, s)
            if (i > 4800) {
                in110Rms += s * s
                out110Rms += oL * oL
            }
        }
        assertTrue("ANC Isolation Focus should lift 110 Hz music warmth ($out110Rms > $in110Rms)", out110Rms > in110Rms * 1.25)

        // Check Conversation Awareness -75% duck gain scaling inside BiquadEqFilterBank
        bank.conversationDuckLinear = com.example.model.ListeningModeSpecs.CONVERSATION_DUCK_GAIN.toDouble()
        var duckedRms = 0.0
        for (i in 0 until 9600) {
            val s = 0.12 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate)
            val (oL, _) = bank.processStereoSample(s, s)
            if (i > 4800) {
                duckedRms += oL * oL
            }
        }
        assertTrue("Conversation Awareness 0.25x ducking must lower music energy strongly", duckedRms < out110Rms * 0.1)
    }

    @Test
    fun dynamics_processing_mbc_spec_matches_3_band_configuration() {
        assertEquals(10, com.example.audio.EqualizerAudioEngine.PRE_EQ_BAND_COUNT)
        assertEquals(3, com.example.audio.EqualizerAudioEngine.MBC_BAND_COUNT)
        val cutoffs = com.example.audio.EqualizerAudioEngine.MBC_UPPER_CUTOFFS_HZ
        assertEquals(com.example.audio.EqualizerAudioEngine.MBC_BAND_COUNT, cutoffs.size)
        for (i in 1 until cutoffs.size) {
            assertTrue("MBC cutoffs must be strictly ascending", cutoffs[i] > cutoffs[i - 1])
        }
        assertTrue("Top MBC cutoff must not exceed 20 kHz (${cutoffs.last()})", cutoffs.last() <= 20000f)
    }

    @Test
    fun full_dsp_chain_remains_finite_and_bounded_across_all_listening_modes() {
        val sampleRate = 48000
        val bank = BiquadEqFilterBank(sampleRate)
        val listeningModes = com.example.model.ListeningMode.entries
        val targets = com.example.model.OutputTarget.entries

        for (target in targets) {
            for (mode in listeningModes) {
                bank.updateParameters(
                    gainsDb = listOf(4f, 3f, 2f, -1f, 0f, 1.5f, 3f, 2f, 1f, 1.5f),
                    preampDb = 2f,
                    isBypassed = false,
                    lateNightEnabled = true,
                    speakerDspEnabled = (target == com.example.model.OutputTarget.SPEAKER),
                    virtualBassEnabled = (target == com.example.model.OutputTarget.SPEAKER),
                    landscapeStereoStage = (target == com.example.model.OutputTarget.SPEAKER),
                    spatialMode = com.example.model.SpatialMode.ATMOS_MUSIC,
                    soundCheckEnabled = true,
                    voiceIsolationEnabled = false,
                    accommodationsEnabled = true,
                    loudnessMaximizerEnabled = true,
                    siliconDspEnabled = true,
                    trebleSmoothnessPercent = 0.5f,
                    masterVolumePercent = 0.85f,
                    proMaxBodyWarmthEnabled = true,
                    adaptiveMultibandEnabled = true,
                    speakerPhaseAlignmentEnabled = true,
                    oversampledEqEnabled = true,
                    harmonicSheenPercent = 0.6f,
                    noiseFloorGateEnabled = true,
                    listeningMode = mode,
                    ambientPassThroughPercent = 0.75f
                )
                for (i in 0 until 1200) {
                    val inL = 0.35 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate) +
                        0.15 * kotlin.math.sin(2.0 * Math.PI * 2400.0 * i / sampleRate)
                    val inR = 0.35 * kotlin.math.sin(2.0 * Math.PI * 110.0 * i / sampleRate) -
                        0.15 * kotlin.math.sin(2.0 * Math.PI * 2400.0 * i / sampleRate)
                    val (outL, outR) = bank.processStereoSample(inL, inR)
                    assertTrue("Left sample must be finite & bounded for $target/$mode", outL.isFinite() && outL in -1.2..1.2)
                    assertTrue("Right sample must be finite & bounded for $target/$mode", outR.isFinite() && outR in -1.2..1.2)
                }
            }
        }
    }

    @Test
    fun iphone_18_pro_max_porous_cavity_and_cinema_landscape_dsp_specs() {
        val sampleRate = 48000

        // 1. Verify exact Porous-Cavity (128Hz +3.2dB, Q=1.25), 86Hz HPF, 350Hz (-3.5dB, Q=1.5), and 4.5k/7k guard
        assertEquals(86.0, com.example.model.FlagshipSpeakerSpecs.SUB_BASS_HPF_HZ, 1e-4)
        assertEquals(128.0, com.example.model.FlagshipSpeakerSpecs.CHAMBER_BASS_HZ, 1e-4)
        assertEquals(1.25, com.example.model.FlagshipSpeakerSpecs.CHAMBER_BASS_Q, 1e-4)
        assertEquals(3.2, com.example.model.FlagshipSpeakerSpecs.CHAMBER_BASS_DB, 1e-4)
        assertEquals(350.0, com.example.model.FlagshipSpeakerSpecs.BOXINESS_CUT_HZ, 1e-4)
        assertEquals(1.5, com.example.model.FlagshipSpeakerSpecs.BOXINESS_CUT_Q, 1e-4)
        assertEquals(-3.5, com.example.model.FlagshipSpeakerSpecs.BOXINESS_CUT_DB, 1e-4)
        assertEquals(6.0f, com.example.model.AdaptiveMultibandPhaseSpecs.BAND_ATTACK_MS, 1e-4f)
        assertEquals(85.0f, com.example.model.AdaptiveMultibandPhaseSpecs.BAND_RELEASE_MS, 1e-4f)
        assertEquals(1450.0, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ, 1e-4)
        assertEquals(0.00014, com.example.model.AdaptiveMultibandPhaseSpecs.TREBLE_MICRO_ARRIVAL_OFFSET_SEC, 1e-7)

        // Preserved +8.5 dB / +9.0 dB Loudness Maximizer & +3.0 dB max treble cap
        assertTrue(com.example.model.LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB >= 8.5f)
        assertEquals(3.0f, com.example.model.TrebleGainStagingSpecs.MAX_COMBINED_HF_BOOST_DB, 1e-4f)

        val resp128 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            128.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        val resp350 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            350.0,
            com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
            sampleRate
        )
        assertTrue("128Hz Porous-Cavity Compliance Shelf must boost (> +2.0 dB, got $resp128)", resp128 > 2.0f)
        assertTrue("350Hz Chassis Boxiness Scoop must cut (< -2.8 dB, got $resp350)", resp350 < -2.8f)

        // Apple Sibilance & Harshness Guard at 4.5kHz and 7.0kHz must stay active across ALL 3 profiles
        com.example.model.SpeakerTuningProfile.entries.forEach { profile ->
            val r4500 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(4500.0, profile, sampleRate)
            val r7000 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(7000.0, profile, sampleRate)
            assertTrue("$profile must keep 4.5kHz guard active (< -1.8 dB, got $r4500)", r4500 < -1.8f)
            assertTrue("$profile must keep 7.0kHz guard active (< -2.5 dB, got $r7000)", r7000 < -2.5f)
        }

        // 2. Cinema Landscape engages wide 2nd-order transaural XTC even without orientation flag
        val sigChain = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = true,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC
            )
        }
        val cinemaChain = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = true,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.CINEMA_LANDSCAPE
            )
        }
        var sigSide = 0.0
        var cinemaSide = 0.0
        for (i in 0 until 2400) {
            val l = 0.08 * kotlin.math.sin(2.0 * Math.PI * 800.0 * i / sampleRate)
            val r = 0.01 * kotlin.math.sin(2.0 * Math.PI * 800.0 * i / sampleRate)
            val (sL, sR) = sigChain.processStereoSample(l, r)
            val (cL, cR) = cinemaChain.processStereoSample(l, r)
            if (i > 1200) {
                sigSide += abs(sL - sR)
                cinemaSide += abs(cL - cR)
            }
        }
        assertTrue("Cinema Landscape must engage wide transaural XTC ($cinemaSide vs $sigSide)", cinemaSide > sigSide * 1.15)

        // 3. SpeakerPhaseAlignmentFilter 0.14ms (~6.72 samples @ 48kHz) micro-arrival offset on >2.2kHz upper treble
        val phaseFilter = com.example.audio.SpeakerPhaseAlignmentFilter(sampleRate).apply {
            configure(
                isEnabled = true,
                outputTarget = com.example.model.OutputTarget.SPEAKER,
                speakerDspEnabled = true
            )
        }
        val apOnlyHigh = com.example.audio.StereoBiquad().apply {
            setAllPass(sampleRate, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_Q)
        }
        val apOnlyLow = com.example.audio.StereoBiquad().apply {
            setAllPass(sampleRate, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_HZ, com.example.model.AdaptiveMultibandPhaseSpecs.PHASE_ALIGN_ALLPASS_Q)
        }
        val phaseFilterLow = com.example.audio.SpeakerPhaseAlignmentFilter(sampleRate).apply {
            configure(isEnabled = true, outputTarget = com.example.model.OutputTarget.SPEAKER, speakerDspEnabled = true)
        }
        val totalLen = 256
        val pulseCenter = 60
        var wFullHigh = 0.0
        var sFullHigh = 0.0
        var wApHigh = 0.0
        var sApHigh = 0.0
        var wFullLow = 0.0
        var sFullLow = 0.0
        var wApLow = 0.0
        var sApLow = 0.0
        for (i in 0 until totalLen) {
            val env = kotlin.math.exp(-((i - pulseCenter).toDouble() * (i - pulseCenter).toDouble()) / 18.0)
            val hfBurst = env * kotlin.math.sin(2.0 * Math.PI * 6000.0 * i / sampleRate)
            val lfBurst = env * kotlin.math.sin(2.0 * Math.PI * 500.0 * i / sampleRate)

            val fullH = abs(phaseFilter.processStereoSample(hfBurst, hfBurst).first)
            val baseH = abs(apOnlyHigh.processStereo(hfBurst, hfBurst).first)
            val fullL = abs(phaseFilterLow.processStereoSample(lfBurst, lfBurst).first)
            val baseL = abs(apOnlyLow.processStereo(lfBurst, lfBurst).first)

            wFullHigh += i * fullH
            sFullHigh += fullH
            wApHigh += i * baseH
            sApHigh += baseH
            wFullLow += i * fullL
            sFullLow += fullL
            wApLow += i * baseL
            sApLow += baseL
        }
        val highDelaySamples = (wFullHigh / sFullHigh) - (wApHigh / sApHigh)
        val lowDelaySamples = (wFullLow / sFullLow) - (wApLow / sApLow)
        assertTrue(
            "Upper treble (>2.2kHz) must include ~0.14ms (~6.72 samples) micro-arrival offset (highDelay=$highDelaySamples, lowDelay=$lowDelaySamples)",
            highDelaySamples in 6.5..10.5 && sFullHigh > sApHigh * 0.92
        )
    }

    @Test
    fun headphone_accommodations_parallel_upward_lift_boosts_quiet_detail_without_squashing_peaks() {
        val sampleRate = 48000
        val offProc = com.example.audio.HeadphoneAccommodationsProcessor(sampleRate).apply {
            configure(
                isEnabled = true,
                profile = com.example.model.AccommodationToneProfile.BRIGHTNESS,
                intensity = com.example.model.SoftSoundIntensity.OFF
            )
        }
        val strongProc = com.example.audio.HeadphoneAccommodationsProcessor(sampleRate).apply {
            configure(
                isEnabled = true,
                profile = com.example.model.AccommodationToneProfile.BRIGHTNESS,
                intensity = com.example.model.SoftSoundIntensity.STRONG
            )
        }

        // 1. Quiet 2.5 kHz whisper/detail (-46 dBFS, >1.2kHz HPF, below -38 dB threshold) -> Strong 48% lift boosts strongly
        var quietOffSq = 0.0
        var quietStrongSq = 0.0
        for (i in 0 until 4800) {
            val s = 0.005 * kotlin.math.sin(2.0 * Math.PI * 2500.0 * i / sampleRate)
            val (oOff, _) = offProc.processStereoSample(s, s)
            val (oStrong, _) = strongProc.processStereoSample(s, s)
            if (i >= 2400) {
                quietOffSq += oOff * oOff
                quietStrongSq += oStrong * oStrong
            }
        }
        val quietOffRms = kotlin.math.sqrt(quietOffSq / 2400)
        val quietStrongRms = kotlin.math.sqrt(quietStrongSq / 2400)
        val upwardLiftDb = 20.0 * kotlin.math.log10((quietStrongRms / quietOffRms).coerceAtLeast(1e-6))
        assertTrue(
            "Parallel 48% +5.5dB clarity lift must boost quiet >1.2kHz details by >= +3.5 dB (got $upwardLiftDb dB)",
            upwardLiftDb >= 3.5
        )

        // 2. Loud 2.5 kHz transient (-4 dBFS) must NOT be crushed downward by parallel compressor
        var loudOffSq = 0.0
        var loudStrongSq = 0.0
        for (i in 0 until 4800) {
            val s = 0.63 * kotlin.math.sin(2.0 * Math.PI * 2500.0 * i / sampleRate)
            val (oOff, _) = offProc.processStereoSample(s, s)
            val (oStrong, _) = strongProc.processStereoSample(s, s)
            if (i >= 2400) {
                loudOffSq += oOff * oOff
                loudStrongSq += oStrong * oStrong
            }
        }
        val loudOffRms = kotlin.math.sqrt(loudOffSq / 2400)
        val loudStrongRms = kotlin.math.sqrt(loudStrongSq / 2400)
        assertTrue(
            "Loud peaks must never drop volume when Strong Soft-Sound Lift is engaged (loudStrong=$loudStrongRms vs loudOff=$loudOffRms)",
            loudStrongRms >= loudOffRms * 0.98
        )
    }

    @Test
    fun custom_audio_setup_3_step_wizard_picks_combined_profile_and_intensity() {
        val engine = com.example.audio.CustomAudioSetupAuditionEngine()
        engine.open(com.example.model.SoftSoundIntensity.OFF)
        assertTrue(engine.state.value.isOpen)
        assertEquals(0, engine.state.value.stepIndex)

        // Step 1: Choose Vocal Focus -> advances to Step 2
        engine.chooseStep1Speech(preferVocalFocus = true)
        assertEquals(1, engine.state.value.stepIndex)
        assertTrue(engine.state.value.pickedVocalInStep1)
        assertEquals(com.example.model.AccommodationToneProfile.VOCAL_RANGE, engine.state.value.chosenProfile)

        // Step 2: Choose Balanced Tone (preserves Vocal Focus from Step 1) -> advances to Step 3
        engine.chooseStep2Music(com.example.model.AccommodationToneProfile.BALANCED_TONE)
        assertEquals(2, engine.state.value.stepIndex)
        assertEquals(com.example.model.AccommodationToneProfile.VOCAL_RANGE, engine.state.value.chosenProfile)

        // Step 3: Choose Strong Soft-Sound boost -> completes wizard with VOCAL_RANGE + STRONG
        engine.chooseStep3Sensitivity(com.example.model.SoftSoundIntensity.STRONG)
        assertTrue(engine.state.value.isCompleted)
        assertEquals(com.example.model.SoftSoundIntensity.STRONG, engine.state.value.softIntensity)

        // Restart and test Brightness path: Step 1 Standard + Step 2 Brightness + Step 3 Slight
        engine.restartWizard()
        assertEquals(0, engine.state.value.stepIndex)
        engine.chooseStep1Speech(preferVocalFocus = false)
        engine.chooseStep2Music(com.example.model.AccommodationToneProfile.BRIGHTNESS)
        engine.chooseStep3Sensitivity(com.example.model.SoftSoundIntensity.SLIGHT)
        assertTrue(engine.state.value.isCompleted)
        assertEquals(com.example.model.AccommodationToneProfile.BRIGHTNESS, engine.state.value.chosenProfile)
        assertEquals(com.example.model.SoftSoundIntensity.SLIGHT, engine.state.value.softIntensity)
        engine.close()
    }

    @Test
    fun three_band_ios27_smart_curve_applies_cross_band_compensation_and_7db_limits() {
        val sampleRate = 48000

        // 1. Official -7.0 dB to +7.0 dB bounds clamp excess inputs cleanly
        assertEquals(-7f, com.example.model.ThreeBandEqRanges.BASS_MIN_DB, 1e-4f)
        assertEquals(7f, com.example.model.ThreeBandEqRanges.BASS_MAX_DB, 1e-4f)
        val clamped100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 60.0,
            bassDb = 12.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        val exact7dB100Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 60.0,
            bassDb = 7.0f,
            midsDb = 0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        assertEquals("Excess gain above +7.0 dB must clamp to +7.0 dB", exact7dB100Hz, clamped100Hz, 0.05f)

        // 2. Mid > 0 (+6 dB): boosts vocal presence (600Hz-4.7kHz), trims <250Hz mud (-0.4 dB/dB -> -2.4 dB), lifts >3.3kHz (+0.5 dB/dB -> +3.0 dB)
        val midPosBass = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 80.0,
            bassDb = 0f,
            midsDb = 6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        val midPosVocal = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 1700.0,
            bassDb = 0f,
            midsDb = 6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        val midPosHigh = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 9000.0,
            bassDb = 0f,
            midsDb = 6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        assertTrue("Mid +6dB must trim <250Hz mud (< -1.6 dB, got $midPosBass)", midPosBass < -1.6f)
        assertTrue("Mid +6dB must boost 1.7kHz vocal presence (> +5.0 dB, got $midPosVocal)", midPosVocal > 5.0f)
        assertTrue("Mid +6dB must add >3.3kHz crisp clarity (> +2.5 dB, got $midPosHigh)", midPosHigh > 2.5f)

        // 3. Mid < 0 (-6 dB): cuts boxy 300Hz-2.6kHz and adds V-curve warmth (+0.7 dB/dB <250Hz, +0.5 dB/dB >3.3kHz)
        val midNegBass = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 80.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        val midNegBoxy = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 880.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        val midNegHigh = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 9000.0,
            bassDb = 0f,
            midsDb = -6.0f,
            trebleDb = 0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        assertTrue("Mid -6dB must add V-curve bass warmth (> +3.0 dB, got $midNegBass)", midNegBass > 3.0f)
        assertTrue("Mid -6dB must cut boxy 880Hz midrange (< -4.5 dB, got $midNegBoxy)", midNegBoxy < -4.5f)
        assertTrue("Mid -6dB must add >3.3kHz V-curve clarity (> +2.2 dB, got $midNegHigh)", midNegHigh > 2.2f)

        // 4. High > 0 (+6 dB): boosts >4.7kHz and applies subtle inverse warmth tilt (-0.15 dB/dB <2.5kHz -> ~ -0.9 dB)
        val highPosTilt200Hz = com.example.audio.ThreeBandToneFilter.computeCombinedResponseDbAt(
            freqHz = 200.0,
            bassDb = 0f,
            midsDb = 0f,
            trebleDb = 6.0f,
            sampleRate = sampleRate,
            smartCurveEnabled = true
        )
        assertTrue(
            "High +6dB in iOS 27 Smart Curve must apply ~ -0.9 dB inverse warmth tilt below 2.5kHz (got $highPosTilt200Hz)",
            highPosTilt200Hz in -1.25f..-0.55f
        )
    }

    @Test
    fun infinix_hot_60_5g_compensator_enables_dual_stereo_2nd_order_xtc_and_tweeter_guard() {
        val sampleRate = 48000
        val initial = com.example.model.OutputEqProfile(
            target = com.example.model.OutputTarget.SPEAKER,
            landscapeStereoStage = false,
            speakerTuningProfile = com.example.model.SpeakerTuningProfile.CINEMA_LANDSCAPE,
            proMaxBodyWarmthEnabled = false,
            trebleSmoothnessPercent = 0f,
            loudnessMaximizerEnabled = false,
            loudnessBoostPercent = 0f,
            tenBandEnabled = true,
            threeBandEnabled = false
        )
        val calibrated = initial.withInfinixHot60Compensator()

        assertTrue(calibrated.isInfinixHot60CompensatorActive())
        assertTrue(calibrated.speakerDspEnabled)
        assertTrue("Infinix Hot 60 5G Compensator must enable dual-speaker stereo & landscape XTC", calibrated.landscapeStereoStage)
        assertEquals(0.22, com.example.model.FlagshipSpeakerSpecs.XTC_CROSSFEED_GAIN, 1e-6)
        assertEquals(0.00018, com.example.model.FlagshipSpeakerSpecs.XTC_DELAY_SEC, 1e-7)
        assertEquals(com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC, calibrated.speakerTuningProfile)
        assertTrue("Pro Max Body Warmth (125Hz) must be ON", calibrated.proMaxBodyWarmthEnabled)
        assertEquals(0.50f, calibrated.trebleSmoothnessPercent, 1e-4f)
        assertTrue(calibrated.loudnessMaximizerEnabled)
        assertEquals(1.0f, calibrated.loudnessBoostPercent, 1e-4f)
        assertEquals(9.5f, com.example.model.LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB, 1e-4f)
        assertTrue(!calibrated.tenBandEnabled)
        assertTrue(calibrated.threeBandEnabled)
        assertEquals(2.5f, calibrated.threeBandBassDb, 1e-4f)
        assertEquals(0.0f, calibrated.threeBandMidsDb, 1e-4f)
        assertEquals(-2.0f, calibrated.threeBandTrebleDb, 1e-4f)

        // 1. Verify top-earpiece tweeter De-Harsh Guard (4.5kHz <= -2.5dB, 7.0kHz <= -3.0dB) & warm 130Hz bass punch (>= +3.0dB)
        val resp130 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            130.0,
            calibrated.speakerTuningProfile,
            sampleRate,
            isBodyWarmthEnabled = calibrated.proMaxBodyWarmthEnabled
        )
        val resp4500 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            4500.0,
            calibrated.speakerTuningProfile,
            sampleRate,
            isBodyWarmthEnabled = calibrated.proMaxBodyWarmthEnabled
        )
        val resp7000 = com.example.audio.PhoneSpeakerDspChain.computeVcurveResponseDbAt(
            7000.0,
            calibrated.speakerTuningProfile,
            sampleRate,
            isBodyWarmthEnabled = calibrated.proMaxBodyWarmthEnabled
        )
        assertTrue("Bottom speaker must deliver warm 130Hz bass punch (>= +3.0 dB, got $resp130)", resp130 >= 3.0f)
        assertTrue("Top earpiece 4.5kHz De-Harsh Guard must be active (<= -2.5 dB, got $resp4500)", resp4500 <= -2.5f)
        assertTrue("Top earpiece 7.0kHz De-Harsh Guard must be active (<= -3.0 dB, got $resp7000)", resp7000 <= -3.0f)

        // 2. Verify DSP bank keeps <180Hz bass centered mono while preserving full L/R stereo & engaging 2nd-order 0.18ms XTC
        val dualStereoBank = com.example.audio.BiquadEqFilterBank(sampleRate)
        dualStereoBank.updateParameters(
            gainsDb = calibrated.bandGainsDb,
            preampDb = calibrated.preampDb,
            isBypassed = calibrated.isBypassed,
            speakerDspEnabled = calibrated.speakerDspEnabled,
            virtualBassEnabled = calibrated.virtualBassEnabled,
            landscapeStereoStage = calibrated.landscapeStereoStage,
            speakerTuningProfile = calibrated.speakerTuningProfile,
            outputTarget = calibrated.target,
            tenBandEnabled = calibrated.tenBandEnabled,
            threeBandEnabled = calibrated.threeBandEnabled,
            threeBandBassDb = calibrated.threeBandBassDb,
            threeBandMidsDb = calibrated.threeBandMidsDb,
            threeBandTrebleDb = calibrated.threeBandTrebleDb,
            threeBandSmartCurveEnabled = calibrated.threeBandSmartCurveEnabled,
            loudnessMaximizerEnabled = calibrated.loudnessMaximizerEnabled,
            loudnessBoostPercent = calibrated.loudnessBoostPercent,
            trebleSmoothnessPercent = calibrated.trebleSmoothnessPercent,
            proMaxBodyWarmthEnabled = calibrated.proMaxBodyWarmthEnabled,
            deviceIsLandscape = true
        )

        var bassSideSum = 0.0
        var bassMidSum = 0.0
        for (i in 0 until 2400) {
            val l = 0.08 * kotlin.math.sin(2.0 * Math.PI * 100.0 * i / sampleRate)
            val r = 0.02 * kotlin.math.sin(2.0 * Math.PI * 100.0 * i / sampleRate)
            val (outL, outR) = dualStereoBank.processStereoSample(l, r)
            if (i >= 1200) {
                bassSideSum += abs(outL - outR)
                bassMidSum += abs(outL + outR)
            }
        }
        val inputSideMidRatio = (0.08 - 0.02) / (0.08 + 0.02)
        val outputSideMidRatio = bassSideSum / bassMidSum.coerceAtLeast(1e-6)
        assertTrue("Clean mono-bass summing must preserve strong 100Hz energy (midSum=$bassMidSum)", bassMidSum > 100.0)
        assertTrue(
            "Mono-bass <180Hz must stay centered cleanly (in=$inputSideMidRatio, out=$outputSideMidRatio)",
            outputSideMidRatio < 0.18 && outputSideMidRatio < inputSideMidRatio * 0.35
        )

        // Compare true dual-speaker 2nd-order Landscape XTC against non-XTC at 800Hz
        val noXtcBank = com.example.audio.BiquadEqFilterBank(sampleRate)
        noXtcBank.updateParameters(
            gainsDb = calibrated.bandGainsDb,
            preampDb = calibrated.preampDb,
            isBypassed = calibrated.isBypassed,
            speakerDspEnabled = calibrated.speakerDspEnabled,
            virtualBassEnabled = calibrated.virtualBassEnabled,
            landscapeStereoStage = false,
            speakerTuningProfile = calibrated.speakerTuningProfile,
            outputTarget = calibrated.target,
            tenBandEnabled = calibrated.tenBandEnabled,
            threeBandEnabled = calibrated.threeBandEnabled,
            threeBandBassDb = calibrated.threeBandBassDb,
            threeBandMidsDb = calibrated.threeBandMidsDb,
            threeBandTrebleDb = calibrated.threeBandTrebleDb,
            threeBandSmartCurveEnabled = calibrated.threeBandSmartCurveEnabled,
            loudnessMaximizerEnabled = calibrated.loudnessMaximizerEnabled,
            loudnessBoostPercent = calibrated.loudnessBoostPercent,
            trebleSmoothnessPercent = calibrated.trebleSmoothnessPercent,
            proMaxBodyWarmthEnabled = calibrated.proMaxBodyWarmthEnabled,
            deviceIsLandscape = true
        )
        var noXtcSide = 0.0
        var withXtcSide = 0.0
        for (i in 0 until 2400) {
            val l = 0.06 * kotlin.math.sin(2.0 * Math.PI * 800.0 * i / sampleRate)
            val r = 0.01 * kotlin.math.sin(2.0 * Math.PI * 800.0 * i / sampleRate)
            val (nL, nR) = noXtcBank.processStereoSample(l, r)
            val (xL, xR) = dualStereoBank.processStereoSample(l, r)
            if (i >= 1200) {
                noXtcSide += abs(nL - nR)
                withXtcSide += abs(xL - xR)
            }
        }
        assertTrue("Dual-speaker 2nd-order landscape XTC (-22% 0.18ms) must project wider stereo stage ($withXtcSide vs $noXtcSide)", withXtcSide > noXtcSide * 1.10)
    }

    @Test
    fun infinix_hot_60_5g_full_feature_dsp_optimization_verified() {
        val sampleRate = 48000

        // 1. Asymmetrical Top-Earpiece (+1.8dB @ 2.68kHz Left) & Portrait -8% XTC vs Landscape -22% XTC
        val chainNoXtc = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
                isPortraitVocalFocus = false
            )
        }
        var earpieceSq = 0.0
        var bottomSq = 0.0
        for (i in 0 until 4800) {
            val s = 0.08 * kotlin.math.sin(2.0 * Math.PI * 2680.0 * i / sampleRate)
            val (outL, outR) = chainNoXtc.processStereoSample(s, s)
            if (i >= 2400) {
                earpieceSq += outL * outL
                bottomSq += outR * outR
            }
        }
        val earpieceDbAboveBottom = 10.0 * kotlin.math.log10((earpieceSq / bottomSq).coerceAtLeast(1e-6))
        assertTrue(
            "Top earpiece (Left channel) must receive ~+1.8 dB upper-mid presence lift relative to bottom driver (got $earpieceDbAboveBottom dB)",
            earpieceDbAboveBottom in 1.4..2.2
        )
        assertEquals(0.08, com.example.model.FlagshipSpeakerSpecs.PORTRAIT_XTC_CROSSFEED_GAIN, 1e-6)
        assertEquals(0.22, com.example.model.FlagshipSpeakerSpecs.XTC_CROSSFEED_GAIN, 1e-6)

        // Portrait (-8% XTC) vs Landscape (-22% XTC) separation ordering
        val portraitChain = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = false,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
                isPortraitVocalFocus = true
            )
        }
        val landscapeChain = com.example.audio.PhoneSpeakerDspChain(sampleRate).apply {
            configure(
                isSpeakerDspEnabled = true,
                isVirtualBassEnabled = false,
                isLandscapeStereoEnabled = true,
                profile = com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
                isPortraitVocalFocus = false
            )
        }
        var portraitSide = 0.0
        var landscapeSide = 0.0
        for (i in 0 until 4800) {
            val l = 0.06 * kotlin.math.sin(2.0 * Math.PI * 900.0 * i / sampleRate)
            val r = 0.01 * kotlin.math.sin(2.0 * Math.PI * 900.0 * i / sampleRate)
            val (pL, pR) = portraitChain.processStereoSample(l, r)
            val (lL, lR) = landscapeChain.processStereoSample(l, r)
            if (i >= 2400) {
                portraitSide += abs(pL - pR)
                landscapeSide += abs(lL - lR)
            }
        }
        assertTrue(
            "Portrait -8% XTC must stay cohesive & narrower than Landscape -22% XTC ($portraitSide < $landscapeSide)",
            portraitSide < landscapeSide * 0.78
        )

        // 2. Silicon Excursion Filter calibrated to 86Hz-120Hz in Phone Speaker mode
        assertEquals(86.0, com.example.model.SiliconDspSpecs.SVF_MIN_HZ, 1e-4)
        assertEquals(120.0, com.example.model.SiliconDspSpecs.SVF_MAX_HZ, 1e-4)
        val idleSpeakerCore = com.example.audio.SiliconDspCore(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.SPEAKER)
        }
        idleSpeakerCore.processStereoSample(0.0, 0.0)
        assertTrue(
            "Phone Speaker Silicon SVF floor must start at >=86Hz (got ${idleSpeakerCore.latestSvfCutoffHz}Hz)",
            idleSpeakerCore.latestSvfCutoffHz in 85.9f..120.1f
        )

        // 3. Spatial Audio Phone Speaker dry center vocals (1kHz-3.5kHz) vs Headphone HRTF
        val speakerSpatial = com.example.audio.SpatialAudioEngine(sampleRate).apply {
            configure(
                mode = com.example.model.SpatialMode.ATMOS_MUSIC,
                target = com.example.model.OutputTarget.SPEAKER
            )
        }
        var dryVocalsRatioSum = 0.0
        for (i in 0 until 1200) {
            val vocal = 0.05 * kotlin.math.sin(2.0 * Math.PI * 2000.0 * i / sampleRate)
            val (outL, outR) = speakerSpatial.processStereoSample(vocal, vocal)
            if (i >= 800) {
                dryVocalsRatioSum += abs(outL - outR)
            }
        }
        assertEquals("Phone Speaker Spatial center vocals must stay 100% dry & phase-identical L/R", 0.0, dryVocalsRatioSum, 1e-6)

        // 4. Late Night Mode Standard default (-22 dB threshold, 3.5:1 ratio, +4.5 dB makeup)
        assertEquals(-22f, com.example.model.NightIntensity.STANDARD.thresholdDb, 1e-4f)
        assertEquals(3.5f, com.example.model.NightIntensity.STANDARD.ratio, 1e-4f)
        assertEquals(4.5f, com.example.model.NightIntensity.STANDARD.makeupGainDb, 1e-4f)

        // 5. Infinix Hot 60 5G Reference 3-Band EQ curves for Speaker vs Headphones/BT
        val spkRef = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.SPEAKER)
            .withInfinixHot60ThreeBandReference()
        assertTrue(spkRef.isInfinixHot60ThreeBandReferenceActive())
        assertTrue(spkRef.threeBandEnabled && !spkRef.tenBandEnabled)
        assertEquals(1.5f, spkRef.threeBandBassDb, 1e-4f)
        assertEquals(0.5f, spkRef.threeBandMidsDb, 1e-4f)
        assertEquals(-1.0f, spkRef.threeBandTrebleDb, 1e-4f)

        val hpRef = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.HEADPHONES)
            .withInfinixHot60ThreeBandReference()
        assertTrue(hpRef.isInfinixHot60ThreeBandReferenceActive())
        assertEquals(1.5f, hpRef.threeBandBassDb, 1e-4f)
        assertEquals(0.0f, hpRef.threeBandMidsDb, 1e-4f)
        assertEquals(1.0f, hpRef.threeBandTrebleDb, 1e-4f)

        val btRef = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.BLUETOOTH)
            .withInfinixHot60ThreeBandReference()
        assertTrue(btRef.isInfinixHot60ThreeBandReferenceActive())
        assertEquals(1.5f, btRef.threeBandBassDb, 1e-4f)
        assertEquals(0.0f, btRef.threeBandMidsDb, 1e-4f)
        assertEquals(1.0f, btRef.threeBandTrebleDb, 1e-4f)
    }

    @Test
    fun audiophile_64bit_double_precision_and_infinix_mastering_verified() {
        val sampleRate = 48000

        // 1. CirrusSiliconDspEngine typealias & 64-bit Double sub-bass precision (32 Hz)
        val engine: com.example.audio.CirrusSiliconDspEngine = com.example.audio.CirrusSiliconDspEngine(sampleRate).apply {
            configure(isEnabled = true, target = com.example.model.OutputTarget.HEADPHONES)
        }
        assertEquals(com.example.audio.SiliconDspCore(sampleRate).lookaheadSamples, engine.lookaheadSamples)
        val subBassBiquad = com.example.audio.StereoBiquad().apply {
            setPeaking(sampleRate, 32.0, com.example.audio.StereoBiquad.SQRT2, 6.0)
        }
        // Feed 1e-5 (-100 dBFS) 32Hz sub-bass tone for 100ms (4800 samples) to verify 64-bit precision with zero quantization floor
        var maxSubOut = 0.0
        for (i in 0 until 4800) {
            val amp = 1e-5 * kotlin.math.sin(2.0 * Math.PI * 32.0 * i / sampleRate)
            val (oL, _) = subBassBiquad.processStereo(amp, amp)
            if (i >= 2400) maxSubOut = maxOf(maxSubOut, kotlin.math.abs(oL))
        }
        assertTrue("64-bit sub-bass filter must resolve -100 dBFS 32Hz cleanly (~2e-5 peak, got $maxSubOut)", maxSubOut > 1.8e-5)

        // 2. Phase-coherent LR4 crossovers (220Hz & 3.8kHz) sum flat at 0.0 dB
        val lr4 = com.example.audio.AdaptiveMultibandToneBalancer(sampleRate)
        var inSq = 0.0
        var sumSq = 0.0
        for (i in 0 until 4800) {
            val s = 0.3 * kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sampleRate) +
                0.3 * kotlin.math.sin(2.0 * Math.PI * 3800.0 * i / sampleRate)
            val (low, mid, high) = lr4.splitBandsStereo(s, s)
            val summed = low.first + mid.first + high.first
            if (i >= 2400) {
                inSq += s * s
                sumSq += summed * summed
            }
        }
        val lr4SumErrorDb = kotlin.math.abs(10.0 * kotlin.math.log10((sumSq / inSq).coerceAtLeast(1e-12)))
        assertTrue("LR4 3-way split sum must stay flat within 0.05 dB at crossover frequencies (got $lr4SumErrorDb dB)", lr4SumErrorDb < 0.05)

        // 3. Orfanidis Nyquist-Matched Peaking EQ (16 kHz @ 48 kHz) & Vicanek De-Cramped High Shelf (4.7 kHz @ 48 kHz)
        val cramped16k = com.example.audio.StereoBiquad().apply { setPeaking(sampleRate, 16000.0, 1.4142135623730951, 6.0) }
        val orfanidis16kBoost = com.example.audio.StereoBiquad().apply { setOrfanidisPeaking(sampleRate, 16000.0, 1.4142135623730951, 6.0) }
        val orfanidis16kCut = com.example.audio.StereoBiquad().apply { setOrfanidisPeaking(sampleRate, 16000.0, 1.4142135623730951, -6.0) }
        assertEquals(6.0, orfanidis16kBoost.magnitudeDbAt(sampleRate, 16000.0), 0.02)
        assertEquals(-6.0, orfanidis16kCut.magnitudeDbAt(sampleRate, 16000.0), 0.02)
        assertTrue(
            "Orfanidis 16kHz EQ at 19kHz must exceed cramped RBJ (${orfanidis16kBoost.magnitudeDbAt(sampleRate, 19000.0)} > ${cramped16k.magnitudeDbAt(sampleRate, 19000.0)})",
            orfanidis16kBoost.magnitudeDbAt(sampleRate, 19000.0) > cramped16k.magnitudeDbAt(sampleRate, 19000.0) + 1.0
        )

        val crampedShelf = com.example.audio.StereoBiquad().apply { setHighShelf(sampleRate, 4700.0, 0.7071067811865475, 6.0) }
        val deCrampedShelfBoost = com.example.audio.StereoBiquad().apply { setDeCrampedHighShelf(sampleRate, 4700.0, 0.7071067811865475, 6.0) }
        val deCrampedShelfCut = com.example.audio.StereoBiquad().apply { setDeCrampedHighShelf(sampleRate, 4700.0, 0.7071067811865475, -6.0) }
        val analogShelfRef12k = com.example.audio.StereoBiquad().apply {
            setHighShelf(sampleRate * 8, 4700.0, 0.7071067811865475, 6.0)
        }.magnitudeDbAt(sampleRate * 8, 12000.0)
        assertEquals(3.0, deCrampedShelfBoost.magnitudeDbAt(sampleRate, 4700.0), 0.05)
        assertEquals(-3.0, deCrampedShelfCut.magnitudeDbAt(sampleRate, 4700.0), 0.05)
        assertTrue(
            "Vicanek de-cramped 4.7kHz high shelf error at 12kHz must be < 0.08 dB vs analog ($analogShelfRef12k dB)",
            kotlin.math.abs(deCrampedShelfBoost.magnitudeDbAt(sampleRate, 12000.0) - analogShelfRef12k) < 0.08
        )
        assertTrue(
            "Vicanek de-cramped shelf must beat cramped RBJ at 12kHz",
            kotlin.math.abs(deCrampedShelfBoost.magnitudeDbAt(sampleRate, 12000.0) - analogShelfRef12k) <
                kotlin.math.abs(crampedShelf.magnitudeDbAt(sampleRate, 12000.0) - analogShelfRef12k) * 0.25
        )

        // 4. SiliconDspCore 4x Catmull-Rom -0.2 dBTP brickwall limiter runs as final dynamic stage after VolumeContour
        val truePeakCeiling = Math.pow(10.0, com.example.model.SiliconDspSpecs.TRUE_PEAK_CEILING_DBTP / 20.0)
        val finalChainBank = BiquadEqFilterBank(sampleRate).apply {
            updateParameters(
                gainsDb = List(10) { 0f },
                preampDb = 8.0f,
                isBypassed = false,
                outputTarget = com.example.model.OutputTarget.HEADPHONES,
                siliconDspEnabled = true,
                masterVolumePercent = 0.25f, // triggers Low-Vol +3dB contour BEFORE SiliconDspCore
                applyMasterVolumeGain = false
            )
        }
        var maxSample = 0.0
        for (i in 0 until 2400) {
            val hot = 0.90 * kotlin.math.sin(2.0 * Math.PI * 115.0 * i / sampleRate)
            val (oL, oR) = finalChainBank.processStereoSample(hot, hot)
            maxSample = maxOf(maxSample, kotlin.math.abs(oL), kotlin.math.abs(oR))
        }
        assertTrue(
            "4x Catmull-Rom -0.2 dBTP limiter after VolumeContour must catch peaks <= $truePeakCeiling (got $maxSample)",
            maxSample <= truePeakCeiling + 1e-4
        )

        // 5. Infinix Hot 60 5G 3.5mm Headphone DAC +5.5 dB Pre-Amp Headroom defaults
        assertEquals(5.5f, com.example.model.SiliconDspSpecs.HEADPHONE_DAC_PREAMP_HEADROOM_DB, 1e-4f)
        val defaultHp = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.HEADPHONES)
        val defaultSpk = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.SPEAKER)
        val defaultBt = com.example.model.OutputEqProfile(target = com.example.model.OutputTarget.BLUETOOTH)
        assertEquals(5.5f, defaultHp.preampDb, 1e-4f)
        assertEquals(0.0f, defaultSpk.preampDb, 1e-4f)
        assertEquals(0.0f, defaultBt.preampDb, 1e-4f)
    }
}
