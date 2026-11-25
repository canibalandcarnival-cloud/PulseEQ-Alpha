package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EqPresetsCatalog
import com.example.model.NightIntensity
import com.example.model.OutputEqProfile
import com.example.model.OutputTarget
import com.example.model.VisualizerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.eqDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_eq_prefs")

data class StoredEqPreferences(
    val selectedTarget: OutputTarget,
    val autoSwitchByDevice: Boolean,
    val globalMixFallbackEnabled: Boolean,
    val visualizerMode: VisualizerMode,
    val profiles: Map<OutputTarget, OutputEqProfile>
)

class EqPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_TARGET = stringPreferencesKey("selected_target")
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch_by_device")
        val GLOBAL_MIX_FALLBACK = booleanPreferencesKey("global_mix_fallback")
        val VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        val ACCOMMODATIONS_AUTO_ENGAGE_HEADPHONES = booleanPreferencesKey("acc_auto_engage_headphones")

        fun gainsKey(target: OutputTarget) = stringPreferencesKey("gains_${target.id}")
        fun preampKey(target: OutputTarget) = floatPreferencesKey("preamp_${target.id}")
        fun bypassedKey(target: OutputTarget) = booleanPreferencesKey("bypassed_${target.id}")
        fun presetKey(target: OutputTarget) = stringPreferencesKey("preset_${target.id}")
        fun lateNightKey(target: OutputTarget) = booleanPreferencesKey("late_night_${target.id}")
        fun nightIntensityKey(target: OutputTarget) = stringPreferencesKey("night_intensity_${target.id}")
        fun speakerDspKey(target: OutputTarget) = booleanPreferencesKey("speaker_dsp_${target.id}")
        fun virtualBassKey(target: OutputTarget) = booleanPreferencesKey("virtual_bass_${target.id}")
        fun landscapeStereoStageKey(target: OutputTarget) = booleanPreferencesKey("hybrid_dual_speaker_${target.id}")
        fun speakerTuningProfileKey(target: OutputTarget) = stringPreferencesKey("speaker_tuning_profile_${target.id}")
        fun audioMixModeKey(target: OutputTarget) = stringPreferencesKey("audio_mix_mode_${target.id}")
        fun spatialModeKey(target: OutputTarget) = stringPreferencesKey("spatial_mode_${target.id}")
        fun spatialDepthKey(target: OutputTarget) = floatPreferencesKey("spatial_depth_${target.id}")
        fun heightPresenceKey(target: OutputTarget) = floatPreferencesKey("height_presence_${target.id}")
        fun vocalAnchorKey(target: OutputTarget) = booleanPreferencesKey("vocal_anchor_${target.id}")
        fun vocalFocusKey(target: OutputTarget) = floatPreferencesKey("vocal_focus_${target.id}")
        fun soundCheckKey(target: OutputTarget) = booleanPreferencesKey("sound_check_${target.id}")
        fun conversationAwarenessKey(target: OutputTarget) = booleanPreferencesKey("conv_awareness_${target.id}")
        fun bgSoundPresetKey(target: OutputTarget) = stringPreferencesKey("bg_sound_preset_${target.id}")
        fun bgSoundVolumeKey(target: OutputTarget) = floatPreferencesKey("bg_sound_volume_${target.id}")
        fun voiceIsolationKey(target: OutputTarget) = booleanPreferencesKey("voice_isolation_${target.id}")
        fun audiogramCurveKey(target: OutputTarget) = stringPreferencesKey("audiogram_curve_${target.id}")
        fun tenBandEnabledKey(target: OutputTarget) = booleanPreferencesKey("ten_band_enabled_${target.id}")
        fun threeBandEnabledKey(target: OutputTarget) = booleanPreferencesKey("three_band_enabled_${target.id}")
        fun threeBandBassKey(target: OutputTarget) = floatPreferencesKey("three_band_bass_${target.id}")
        fun threeBandMidsKey(target: OutputTarget) = floatPreferencesKey("three_band_mids_${target.id}")
        fun threeBandTrebleKey(target: OutputTarget) = floatPreferencesKey("three_band_treble_${target.id}")
        fun threeBandSmartCurveKey(target: OutputTarget) = booleanPreferencesKey("three_band_smart_curve_${target.id}")
        fun threeBandRecommendedKey(target: OutputTarget) = booleanPreferencesKey("three_band_recommended_${target.id}")
        fun threeBandRecommendedBannerKey(target: OutputTarget) = booleanPreferencesKey("three_band_rec_banner_${target.id}")
        fun accommodationsEnabledKey(target: OutputTarget) = booleanPreferencesKey("acc_enabled_${target.id}")
        fun accommodationsProfileKey(target: OutputTarget) = stringPreferencesKey("acc_profile_${target.id}")
        fun accommodationsSoftIntensityKey(target: OutputTarget) = stringPreferencesKey("acc_soft_${target.id}")
        fun accommodationsMediaKey(target: OutputTarget) = booleanPreferencesKey("acc_media_${target.id}")
        fun accommodationsVoiceKey(target: OutputTarget) = booleanPreferencesKey("acc_voice_${target.id}")
        fun accommodationsOutputGainKey(target: OutputTarget) = floatPreferencesKey("acc_output_gain_${target.id}")
        fun loudnessMaxEnabledKey(target: OutputTarget) = booleanPreferencesKey("loudness_max_enabled_${target.id}")
        fun loudnessBoostPctKey(target: OutputTarget) = floatPreferencesKey("loudness_boost_pct_${target.id}")
        fun siliconDspKey(target: OutputTarget) = booleanPreferencesKey("silicon_dsp_${target.id}")
        fun trebleSmoothnessKey(target: OutputTarget) = floatPreferencesKey("treble_smoothness_${target.id}")
        fun phoneOrientationKey(target: OutputTarget) = stringPreferencesKey("phone_orientation_${target.id}")
        fun masterVolumeKey(target: OutputTarget) = floatPreferencesKey("master_volume_${target.id}")
        fun proMaxBodyWarmthKey(target: OutputTarget) = booleanPreferencesKey("pro_max_body_warmth_${target.id}")
        fun adaptiveMultibandKey(target: OutputTarget) = booleanPreferencesKey("adaptive_multiband_${target.id}")
        fun speakerPhaseAlignKey(target: OutputTarget) = booleanPreferencesKey("speaker_phase_align_${target.id}")
        fun oversampledEqKey(target: OutputTarget) = booleanPreferencesKey("oversampled_eq_${target.id}")
        fun harmonicSheenKey(target: OutputTarget) = floatPreferencesKey("harmonic_sheen_${target.id}")
        fun noiseFloorGateKey(target: OutputTarget) = booleanPreferencesKey("noise_floor_gate_${target.id}")
        fun listeningModeKey(target: OutputTarget) = stringPreferencesKey("listening_mode_${target.id}")
        fun ambientPassThroughKey(target: OutputTarget) = floatPreferencesKey("ambient_pass_through_${target.id}")
    }

    val preferencesFlow: Flow<StoredEqPreferences> = context.eqDataStore.data.map { prefs ->
        val selectedTarget = OutputTarget.entries.firstOrNull {
            it.id == prefs[Keys.SELECTED_TARGET]
        } ?: OutputTarget.SPEAKER

        val autoSwitch = prefs[Keys.AUTO_SWITCH] ?: true
        val globalMixFallback = prefs[Keys.GLOBAL_MIX_FALLBACK] ?: true
        val visualizerMode = VisualizerMode.entries.firstOrNull {
            it.name == prefs[Keys.VISUALIZER_MODE]
        } ?: VisualizerMode.HYBRID

        val profiles = OutputTarget.entries.associateWith { target ->
            val rawGains = prefs[Keys.gainsKey(target)]
            val parsedGains = rawGains
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull()?.coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB) }
                ?.takeIf { it.size == 10 }
                ?: OutputEqProfile.defaultGainsForTarget(target)

            val storedIntensity = prefs[Keys.nightIntensityKey(target)]
            val intensity = when (storedIntensity) {
                "LOW", "GENTLE" -> NightIntensity.GENTLE
                "HIGH", "HEAVY" -> NightIntensity.HEAVY
                else -> NightIntensity.STANDARD
            }

            val audioMixMode = com.example.model.AudioMixMode.entries.firstOrNull {
                it.name == prefs[Keys.audioMixModeKey(target)]
            } ?: com.example.model.AudioMixMode.STANDARD

            val spatialMode = com.example.model.SpatialMode.entries.firstOrNull {
                it.name == prefs[Keys.spatialModeKey(target)]
            } ?: com.example.model.SpatialMode.OFF

            val bgPreset = com.example.model.BackgroundSoundPreset.entries.firstOrNull {
                it.name == prefs[Keys.bgSoundPresetKey(target)]
            } ?: com.example.model.BackgroundSoundPreset.OFF

            val parsedAudiogram = prefs[Keys.audiogramCurveKey(target)]
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull()?.coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB) }
                ?.takeIf { it.size == 10 }

            val storedBass = (prefs[Keys.threeBandBassKey(target)] ?: 0f)
                .coerceIn(com.example.model.ThreeBandEqRanges.BASS_MIN_DB, com.example.model.ThreeBandEqRanges.BASS_MAX_DB)
            val storedMids = (prefs[Keys.threeBandMidsKey(target)] ?: 0f)
                .coerceIn(com.example.model.ThreeBandEqRanges.MIDS_MIN_DB, com.example.model.ThreeBandEqRanges.MIDS_MAX_DB)
            val storedTreble = (prefs[Keys.threeBandTrebleKey(target)] ?: 0f)
                .coerceIn(com.example.model.ThreeBandEqRanges.TREBLE_MIN_DB, com.example.model.ThreeBandEqRanges.TREBLE_MAX_DB)
            val hasNonZeroThreeBand = kotlin.math.abs(storedBass) > 0.05f ||
                kotlin.math.abs(storedMids) > 0.05f ||
                kotlin.math.abs(storedTreble) > 0.05f

            val rawTenBand = prefs[Keys.tenBandEnabledKey(target)]
            val rawThreeBand = prefs[Keys.threeBandEnabledKey(target)]
            val (resolvedTenBand, resolvedThreeBand) = when {
                rawTenBand != null -> rawTenBand to !rawTenBand
                rawThreeBand == true && hasNonZeroThreeBand -> false to true
                else -> true to false
            }

            OutputEqProfile(
                target = target,
                bandGainsDb = parsedGains,
                preampDb = (prefs[Keys.preampKey(target)] ?: 0f)
                    .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB),
                isBypassed = prefs[Keys.bypassedKey(target)] ?: false,
                selectedPresetId = prefs[Keys.presetKey(target)]
                    ?: OutputEqProfile.defaultPresetIdForTarget(target),
                lateNightEnabled = prefs[Keys.lateNightKey(target)] ?: false,
                nightIntensity = intensity,
                speakerDspEnabled = prefs[Keys.speakerDspKey(target)] ?: (target == OutputTarget.SPEAKER),
                virtualBassEnabled = prefs[Keys.virtualBassKey(target)] ?: (target == OutputTarget.SPEAKER),
                landscapeStereoStage = prefs[Keys.landscapeStereoStageKey(target)] ?: (target == OutputTarget.SPEAKER),
                speakerTuningProfile = com.example.model.SpeakerTuningProfile.entries.firstOrNull {
                    it.name == prefs[Keys.speakerTuningProfileKey(target)]
                } ?: com.example.model.SpeakerTuningProfile.PRO_MAX_MUSIC,
                audioMixMode = audioMixMode,
                spatialMode = spatialMode,
                spatialDepth = (prefs[Keys.spatialDepthKey(target)] ?: com.example.model.SpatialMode.ATMOS_MUSIC.defaultDepth)
                    .coerceIn(0f, 1f),
                heightPresence = (prefs[Keys.heightPresenceKey(target)] ?: com.example.model.SpatialMode.ATMOS_MUSIC.defaultHeight)
                    .coerceIn(0f, 1f),
                vocalAnchor = prefs[Keys.vocalAnchorKey(target)] ?: true,
                vocalFocus = (prefs[Keys.vocalFocusKey(target)] ?: 0.70f).coerceIn(0f, 1f),
                soundCheckEnabled = prefs[Keys.soundCheckKey(target)] ?: false,
                conversationAwarenessEnabled = prefs[Keys.conversationAwarenessKey(target)] ?: false,
                backgroundSoundPreset = bgPreset,
                backgroundSoundVolume = (prefs[Keys.bgSoundVolumeKey(target)] ?: 0.28f).coerceIn(0f, 1f),
                voiceIsolationEnabled = prefs[Keys.voiceIsolationKey(target)] ?: false,
                audiogramGainsDb = parsedAudiogram,
                tenBandEnabled = resolvedTenBand,
                threeBandEnabled = resolvedThreeBand,
                threeBandBassDb = storedBass,
                threeBandMidsDb = storedMids,
                threeBandTrebleDb = storedTreble,
                threeBandSmartCurveEnabled = prefs[Keys.threeBandSmartCurveKey(target)] ?: true,
                threeBandRecommendedMode = prefs[Keys.threeBandRecommendedKey(target)] ?: false,
                threeBandRecommendedBannerVisible = prefs[Keys.threeBandRecommendedBannerKey(target)] ?: false,
                accommodationsEnabled = prefs[Keys.accommodationsEnabledKey(target)] ?: false,
                accommodationsToneProfile = com.example.model.AccommodationToneProfile.entries.firstOrNull {
                    it.name == prefs[Keys.accommodationsProfileKey(target)]
                } ?: com.example.model.AccommodationToneProfile.BALANCED_TONE,
                accommodationsSoftIntensity = com.example.model.SoftSoundIntensity.entries.firstOrNull {
                    it.name == prefs[Keys.accommodationsSoftIntensityKey(target)]
                } ?: com.example.model.SoftSoundIntensity.MODERATE,
                accommodationsApplyToMedia = prefs[Keys.accommodationsMediaKey(target)] ?: true,
                accommodationsApplyToVoice = prefs[Keys.accommodationsVoiceKey(target)] ?: false,
                accommodationsAutoEngageHeadphones = prefs[Keys.ACCOMMODATIONS_AUTO_ENGAGE_HEADPHONES] ?: false,
                accommodationsOutputGainDb = (prefs[Keys.accommodationsOutputGainKey(target)] ?: 0f)
                    .coerceIn(
                        com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
                        com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
                    ),
                loudnessMaximizerEnabled = prefs[Keys.loudnessMaxEnabledKey(target)] ?: false,
                loudnessBoostPercent = (prefs[Keys.loudnessBoostPctKey(target)] ?: 0.50f).coerceIn(0f, 1f),
                siliconDspEnabled = prefs[Keys.siliconDspKey(target)] ?: true,
                trebleSmoothnessPercent = (prefs[Keys.trebleSmoothnessKey(target)]
                    ?: OutputEqProfile.defaultTrebleSmoothnessForTarget(target)).coerceIn(0f, 1f),
                phoneOrientationMode = com.example.model.PhoneOrientationMode.entries.firstOrNull {
                    it.name == prefs[Keys.phoneOrientationKey(target)]
                } ?: com.example.model.PhoneOrientationMode.AUTO_DETECT,
                masterVolumePercent = (prefs[Keys.masterVolumeKey(target)] ?: 0.75f).coerceIn(0f, 1f),
                proMaxBodyWarmthEnabled = prefs[Keys.proMaxBodyWarmthKey(target)] ?: (target == OutputTarget.SPEAKER),
                adaptiveMultibandEnabled = prefs[Keys.adaptiveMultibandKey(target)] ?: true,
                speakerPhaseAlignmentEnabled = prefs[Keys.speakerPhaseAlignKey(target)] ?: (target == OutputTarget.SPEAKER),
                oversampledEqEnabled = prefs[Keys.oversampledEqKey(target)] ?: true,
                harmonicSheenPercent = (prefs[Keys.harmonicSheenKey(target)] ?: 0.25f).coerceIn(0f, 1f),
                noiseFloorGateEnabled = prefs[Keys.noiseFloorGateKey(target)] ?: true,
                listeningMode = com.example.model.ListeningMode.entries.firstOrNull {
                    it.name == prefs[Keys.listeningModeKey(target)]
                } ?: com.example.model.ListeningMode.OFF,
                ambientPassThroughPercent = (prefs[Keys.ambientPassThroughKey(target)] ?: 0.65f).coerceIn(0f, 1f)
            )
        }

        StoredEqPreferences(
            selectedTarget = selectedTarget,
            autoSwitchByDevice = autoSwitch,
            globalMixFallbackEnabled = globalMixFallback,
            visualizerMode = visualizerMode,
            profiles = profiles
        )
    }

    suspend fun saveSelectedTarget(target: OutputTarget) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.SELECTED_TARGET] = target.id
        }
    }

    suspend fun saveAutoSwitch(enabled: Boolean) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.AUTO_SWITCH] = enabled
        }
    }

    suspend fun saveGlobalMixFallback(enabled: Boolean) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_MIX_FALLBACK] = enabled
        }
    }

    suspend fun saveAccommodationsAutoEngageHeadphones(enabled: Boolean) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.ACCOMMODATIONS_AUTO_ENGAGE_HEADPHONES] = enabled
        }
    }

    suspend fun saveVisualizerMode(mode: VisualizerMode) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.VISUALIZER_MODE] = mode.name
        }
    }

    suspend fun saveProfile(profile: OutputEqProfile) {
        context.eqDataStore.edit { prefs ->
            prefs[Keys.gainsKey(profile.target)] = profile.bandGainsDb.joinToString(",")
            prefs[Keys.preampKey(profile.target)] = profile.preampDb
            prefs[Keys.bypassedKey(profile.target)] = profile.isBypassed
            prefs[Keys.presetKey(profile.target)] = profile.selectedPresetId
            prefs[Keys.lateNightKey(profile.target)] = profile.lateNightEnabled
            prefs[Keys.nightIntensityKey(profile.target)] = profile.nightIntensity.name
            prefs[Keys.speakerDspKey(profile.target)] = profile.speakerDspEnabled
            prefs[Keys.virtualBassKey(profile.target)] = profile.virtualBassEnabled
            prefs[Keys.landscapeStereoStageKey(profile.target)] = profile.landscapeStereoStage
            prefs[Keys.speakerTuningProfileKey(profile.target)] = profile.speakerTuningProfile.name
            prefs[Keys.audioMixModeKey(profile.target)] = profile.audioMixMode.name
            prefs[Keys.spatialModeKey(profile.target)] = profile.spatialMode.name
            prefs[Keys.spatialDepthKey(profile.target)] = profile.spatialDepth
            prefs[Keys.heightPresenceKey(profile.target)] = profile.heightPresence
            prefs[Keys.vocalAnchorKey(profile.target)] = profile.vocalAnchor
            prefs[Keys.vocalFocusKey(profile.target)] = profile.vocalFocus.coerceIn(0f, 1f)
            prefs[Keys.soundCheckKey(profile.target)] = profile.soundCheckEnabled
            prefs[Keys.conversationAwarenessKey(profile.target)] = profile.conversationAwarenessEnabled
            prefs[Keys.bgSoundPresetKey(profile.target)] = profile.backgroundSoundPreset.name
            prefs[Keys.bgSoundVolumeKey(profile.target)] = profile.backgroundSoundVolume
            prefs[Keys.voiceIsolationKey(profile.target)] = profile.voiceIsolationEnabled
            val audiogram = profile.audiogramGainsDb
            if (audiogram != null && audiogram.size == 10) {
                prefs[Keys.audiogramCurveKey(profile.target)] = audiogram.joinToString(",")
            } else {
                prefs.remove(Keys.audiogramCurveKey(profile.target))
            }
            val saveThreeBand = profile.threeBandEnabled && !profile.tenBandEnabled
            val saveTenBand = !saveThreeBand
            prefs[Keys.tenBandEnabledKey(profile.target)] = saveTenBand
            prefs[Keys.threeBandEnabledKey(profile.target)] = saveThreeBand
            prefs[Keys.threeBandBassKey(profile.target)] = profile.threeBandBassDb
            prefs[Keys.threeBandMidsKey(profile.target)] = profile.threeBandMidsDb
            prefs[Keys.threeBandTrebleKey(profile.target)] = profile.threeBandTrebleDb
            prefs[Keys.threeBandSmartCurveKey(profile.target)] = profile.threeBandSmartCurveEnabled
            prefs[Keys.threeBandRecommendedKey(profile.target)] = profile.threeBandRecommendedMode
            prefs[Keys.threeBandRecommendedBannerKey(profile.target)] = profile.threeBandRecommendedBannerVisible
            prefs[Keys.accommodationsEnabledKey(profile.target)] = profile.accommodationsEnabled
            prefs[Keys.accommodationsProfileKey(profile.target)] = profile.accommodationsToneProfile.name
            prefs[Keys.accommodationsSoftIntensityKey(profile.target)] = profile.accommodationsSoftIntensity.name
            prefs[Keys.accommodationsMediaKey(profile.target)] = profile.accommodationsApplyToMedia
            prefs[Keys.accommodationsVoiceKey(profile.target)] = profile.accommodationsApplyToVoice
            prefs[Keys.accommodationsOutputGainKey(profile.target)] = profile.accommodationsOutputGainDb
                .coerceIn(
                    com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MIN_DB,
                    com.example.model.HeadphoneAccommodationsSpecs.OUTPUT_GAIN_MAX_DB
                )
            prefs[Keys.loudnessMaxEnabledKey(profile.target)] = profile.loudnessMaximizerEnabled
            prefs[Keys.loudnessBoostPctKey(profile.target)] = profile.loudnessBoostPercent.coerceIn(0f, 1f)
            prefs[Keys.siliconDspKey(profile.target)] = profile.siliconDspEnabled
            prefs[Keys.trebleSmoothnessKey(profile.target)] = profile.trebleSmoothnessPercent.coerceIn(0f, 1f)
            prefs[Keys.phoneOrientationKey(profile.target)] = profile.phoneOrientationMode.name
            prefs[Keys.masterVolumeKey(profile.target)] = profile.masterVolumePercent.coerceIn(0f, 1f)
            prefs[Keys.proMaxBodyWarmthKey(profile.target)] = profile.proMaxBodyWarmthEnabled
            prefs[Keys.adaptiveMultibandKey(profile.target)] = profile.adaptiveMultibandEnabled
            prefs[Keys.speakerPhaseAlignKey(profile.target)] = profile.speakerPhaseAlignmentEnabled
            prefs[Keys.oversampledEqKey(profile.target)] = profile.oversampledEqEnabled
            prefs[Keys.harmonicSheenKey(profile.target)] = profile.harmonicSheenPercent.coerceIn(0f, 1f)
            prefs[Keys.noiseFloorGateKey(profile.target)] = profile.noiseFloorGateEnabled
            prefs[Keys.listeningModeKey(profile.target)] = profile.listeningMode.name
            prefs[Keys.ambientPassThroughKey(profile.target)] = profile.ambientPassThroughPercent.coerceIn(0f, 1f)
        }
    }
}
