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

        fun gainsKey(target: OutputTarget) = stringPreferencesKey("gains_${target.id}")
        fun preampKey(target: OutputTarget) = floatPreferencesKey("preamp_${target.id}")
        fun bypassedKey(target: OutputTarget) = booleanPreferencesKey("bypassed_${target.id}")
        fun presetKey(target: OutputTarget) = stringPreferencesKey("preset_${target.id}")
        fun lateNightKey(target: OutputTarget) = booleanPreferencesKey("late_night_${target.id}")
        fun nightIntensityKey(target: OutputTarget) = stringPreferencesKey("night_intensity_${target.id}")
        fun speakerDspKey(target: OutputTarget) = booleanPreferencesKey("speaker_dsp_${target.id}")
        fun virtualBassKey(target: OutputTarget) = booleanPreferencesKey("virtual_bass_${target.id}")
        fun spatialModeKey(target: OutputTarget) = stringPreferencesKey("spatial_mode_${target.id}")
        fun spatialDepthKey(target: OutputTarget) = floatPreferencesKey("spatial_depth_${target.id}")
        fun heightPresenceKey(target: OutputTarget) = floatPreferencesKey("height_presence_${target.id}")
        fun vocalAnchorKey(target: OutputTarget) = booleanPreferencesKey("vocal_anchor_${target.id}")
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

            val spatialMode = com.example.model.SpatialMode.entries.firstOrNull {
                it.name == prefs[Keys.spatialModeKey(target)]
            } ?: com.example.model.SpatialMode.OFF

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
                spatialMode = spatialMode,
                spatialDepth = (prefs[Keys.spatialDepthKey(target)] ?: com.example.model.SpatialMode.ATMOS_MUSIC.defaultDepth)
                    .coerceIn(0f, 1f),
                heightPresence = (prefs[Keys.heightPresenceKey(target)] ?: com.example.model.SpatialMode.ATMOS_MUSIC.defaultHeight)
                    .coerceIn(0f, 1f),
                vocalAnchor = prefs[Keys.vocalAnchorKey(target)] ?: true
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
            prefs[Keys.spatialModeKey(profile.target)] = profile.spatialMode.name
            prefs[Keys.spatialDepthKey(profile.target)] = profile.spatialDepth
            prefs[Keys.heightPresenceKey(profile.target)] = profile.heightPresence
            prefs[Keys.vocalAnchorKey(profile.target)] = profile.vocalAnchor
        }
    }
}
