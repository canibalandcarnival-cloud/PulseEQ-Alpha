package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.OutputTarget
import com.example.ui.components.AudioRoutingServiceCard
import com.example.ui.components.AudioVisualizerPanel
import com.example.ui.components.CustomAudioSetupDialog
import com.example.ui.components.EqualizerConsole
import com.example.ui.components.HeadphoneAccommodationsCard
import com.example.ui.components.HearingCheckDialog
import com.example.ui.components.IosAudioLabCard
import com.example.ui.components.LateNightDynamicsCard
import com.example.ui.components.LoudnessMaximizerCard
import com.example.ui.components.MasterActionBar
import com.example.ui.components.OutputDeviceSelector
import com.example.ui.components.PresetQuickStrip
import com.example.ui.components.PresetsBrowserSheet
import com.example.ui.components.SpatialAudioCard
import com.example.ui.components.SpeakerDspCard
import com.example.ui.components.ThreeBandEqualizerCard
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EqualizerUiState
import com.example.viewmodel.EqualizerViewModel

@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPresetsSheet by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    val conversationMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (granted && !uiState.activeProfile.conversationAwarenessEnabled) {
            viewModel.toggleConversationAwareness()
        }
    }

    val accommodationsVoiceMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (granted && !uiState.activeProfile.accommodationsApplyToVoice) {
            viewModel.toggleAccommodationsVoice()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.ensureServiceStarted()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.ensureServiceStarted()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.ensureServiceStarted()
        }
    }

    Scaffold(
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StudioBackground,
                            StudioBackground,
                            StudioSurface.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
            ) {
                val isWideLayout = maxWidth >= 720.dp

                if (isWideLayout) {
                    WideConsoleLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenPresetsSheet = { showPresetsSheet = true },
                        onRequestAudioPermission = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onToggleConversationWithPermission = {
                            val micGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micGranted || uiState.activeProfile.conversationAwarenessEnabled) {
                                viewModel.toggleConversationAwareness()
                            } else {
                                conversationMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onToggleAccommodationsVoiceWithPermission = {
                            val micGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micGranted || uiState.activeProfile.accommodationsApplyToVoice) {
                                viewModel.toggleAccommodationsVoice()
                            } else {
                                accommodationsVoiceMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                } else {
                    CompactConsoleLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenPresetsSheet = { showPresetsSheet = true },
                        onRequestAudioPermission = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onToggleConversationWithPermission = {
                            val micGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micGranted || uiState.activeProfile.conversationAwarenessEnabled) {
                                viewModel.toggleConversationAwareness()
                            } else {
                                conversationMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onToggleAccommodationsVoiceWithPermission = {
                            val micGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micGranted || uiState.activeProfile.accommodationsApplyToVoice) {
                                viewModel.toggleAccommodationsVoice()
                            } else {
                                accommodationsVoiceMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPresetsSheet) {
        PresetsBrowserSheet(
            selectedPresetId = uiState.activeProfile.selectedPresetId,
            onSelectPreset = viewModel::applyPreset,
            onDismiss = { showPresetsSheet = false }
        )
    }

    HearingCheckDialog(
        state = uiState.hearingCheckState,
        onHearItTap = viewModel::recordHearingCheckTap,
        onSkipStep = viewModel::skipHearingCheckStep,
        onCancel = viewModel::cancelHearingCheck,
        onApplyCurve = viewModel::applyAudiogramCurve
    )

    CustomAudioSetupDialog(
        state = uiState.customAudioSetupState,
        onSelectAuditionSlot = viewModel::selectCustomSetupAudition,
        onSelectIntensity = viewModel::selectCustomSetupIntensity,
        onChooseWinner = viewModel::pickCustomSetupWinner,
        onRestart = viewModel::restartCustomAudioSetup,
        onApply = viewModel::applyCustomAudioSetupResult,
        onClose = viewModel::closeCustomAudioSetup
    )
}

@Composable
private fun CompactConsoleLayout(
    uiState: EqualizerUiState,
    viewModel: EqualizerViewModel,
    onOpenPresetsSheet: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onToggleConversationWithPermission: () -> Unit,
    onToggleAccommodationsVoiceWithPermission: () -> Unit
) {
    val profile = uiState.activeProfile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StudioTopHeader(
            dspStatusLabel = uiState.dspStatusLabel,
            isBypassed = profile.isBypassed,
            selectedTargetLabel = uiState.selectedTarget.label
        )

        OutputDeviceSelector(
            selectedTarget = uiState.selectedTarget,
            connectedDevices = uiState.connectedDevices,
            autoSwitchByDevice = uiState.autoSwitchByDevice,
            onSelectTarget = viewModel::selectOutputTarget,
            onToggleAutoSwitch = viewModel::toggleAutoSwitch
        )

        AudioVisualizerPanel(
            frame = uiState.visualizerFrame,
            bandGainsDb = profile.bandGainsDb,
            preampDb = profile.preampDb,
            isBypassed = profile.isBypassed,
            visualizerMode = uiState.visualizerMode,
            testAudioSource = uiState.testAudioSource,
            hasAudioPermission = uiState.hasAudioPermission,
            onSelectMode = viewModel::setVisualizerMode,
            onCycleTestAudio = viewModel::cycleTestAudioSource,
            onRequestAudioPermission = onRequestAudioPermission,
            threeBandEnabled = profile.threeBandEnabled,
            threeBandBassDb = profile.threeBandBassDb,
            threeBandMidsDb = profile.threeBandMidsDb,
            threeBandTrebleDb = profile.threeBandTrebleDb,
            accommodationsEnabled = profile.accommodationsEnabled,
            accommodationsToneProfile = profile.accommodationsToneProfile,
            accommodationsSoftIntensity = profile.accommodationsSoftIntensity,
            accommodationsApplyToMedia = profile.accommodationsApplyToMedia,
            loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
            outputTarget = uiState.selectedTarget
        )

        MasterActionBar(
            isBypassed = profile.isBypassed,
            activePresetName = uiState.activePresetName,
            onToggleBypass = viewModel::toggleBypass,
            onReset = viewModel::resetToFlat,
            onOpenPresetsSheet = onOpenPresetsSheet
        )

        HeadphoneAccommodationsCard(
            profile = profile,
            telemetry = uiState.audioLabTelemetry,
            hasAudioPermission = uiState.hasAudioPermission,
            onToggleEnabled = viewModel::toggleHeadphoneAccommodations,
            onSelectToneProfile = viewModel::setAccommodationsToneProfile,
            onSelectSoftIntensity = viewModel::setAccommodationsSoftIntensity,
            onOpenCustomAudioSetup = viewModel::openCustomAudioSetup,
            onToggleApplyToMedia = viewModel::toggleAccommodationsMedia,
            onToggleApplyToVoice = onToggleAccommodationsVoiceWithPermission
        )

        EqualizerConsole(
            bandGainsDb = profile.bandGainsDb,
            preampDb = profile.preampDb,
            isBypassed = profile.isBypassed,
            onBandGainChange = viewModel::updateBandGain,
            onPreampChange = viewModel::updatePreamp
        )

        ThreeBandEqualizerCard(
            threeBandEnabled = profile.threeBandEnabled,
            bassDb = profile.threeBandBassDb,
            midsDb = profile.threeBandMidsDb,
            trebleDb = profile.threeBandTrebleDb,
            isMasterBypassed = profile.isBypassed,
            onBassChange = viewModel::updateThreeBandBass,
            onMidsChange = viewModel::updateThreeBandMids,
            onTrebleChange = viewModel::updateThreeBandTreble,
            onToggleEnabled = viewModel::toggleThreeBandEnabled,
            onReset = viewModel::resetThreeBand
        )

        AnimatedVisibility(visible = uiState.selectedTarget == OutputTarget.SPEAKER) {
            SpeakerDspCard(
                speakerDspEnabled = profile.speakerDspEnabled,
                virtualBassEnabled = profile.virtualBassEnabled,
                isBypassed = profile.isBypassed,
                onToggleSpeakerDsp = viewModel::toggleSpeakerDsp,
                onToggleVirtualBass = viewModel::toggleVirtualBass
            )
        }

        LateNightDynamicsCard(
            lateNightEnabled = profile.lateNightEnabled,
            isBypassed = profile.isBypassed,
            nightIntensity = profile.nightIntensity,
            gainReductionDb = uiState.visualizerFrame.gainReductionDb,
            onToggleLateNight = viewModel::toggleLateNightMode,
            onSelectIntensity = viewModel::setNightIntensity
        )

        SpatialAudioCard(
            spatialMode = profile.spatialMode,
            outputTarget = uiState.selectedTarget,
            spatialDepth = profile.spatialDepth,
            heightPresence = profile.heightPresence,
            vocalAnchor = profile.vocalAnchor,
            isBypassed = profile.isBypassed,
            onSelectMode = viewModel::setSpatialMode,
            onDepthChange = viewModel::updateSpatialDepth,
            onHeightChange = viewModel::updateHeightPresence,
            onToggleVocalAnchor = viewModel::toggleVocalAnchor
        )

        IosAudioLabCard(
            profile = profile,
            telemetry = uiState.audioLabTelemetry,
            abCompareHeld = uiState.abCompareHeld,
            hasAudioPermission = uiState.hasAudioPermission,
            onToggleSoundCheck = viewModel::toggleSoundCheck,
            onToggleConversationAwareness = onToggleConversationWithPermission,
            onSelectBackgroundSound = viewModel::selectBackgroundSound,
            onBackgroundVolumeChange = viewModel::updateBackgroundVolume,
            onStartHearingCheck = viewModel::startHearingCheck,
            onApplySavedAudiogram = viewModel::applyAudiogramCurve,
            onAbCompareHeld = viewModel::setAbCompareHeld,
            onToggleVoiceIsolation = viewModel::toggleVoiceIsolation
        )

        LoudnessMaximizerCard(
            loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
            boostPercent = profile.loudnessBoostPercent,
            outputTarget = uiState.selectedTarget,
            isMasterBypassed = profile.isBypassed,
            telemetry = uiState.audioLabTelemetry,
            onToggleEnabled = viewModel::toggleLoudnessMaximizer,
            onBoostPercentChange = viewModel::updateLoudnessBoostPercent
        )

        PresetQuickStrip(
            selectedPresetId = profile.selectedPresetId,
            onSelectPreset = viewModel::applyPreset
        )

        AudioRoutingServiceCard(
            serviceRunning = uiState.serviceRunning,
            globalMixFallbackEnabled = uiState.globalMixFallbackEnabled,
            activeSessions = uiState.activeAudioSessions,
            onToggleService = viewModel::toggleBackgroundService,
            onToggleGlobalMixFallback = viewModel::toggleGlobalMixFallback
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun WideConsoleLayout(
    uiState: EqualizerUiState,
    viewModel: EqualizerViewModel,
    onOpenPresetsSheet: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onToggleConversationWithPermission: () -> Unit,
    onToggleAccommodationsVoiceWithPermission: () -> Unit
) {
    val profile = uiState.activeProfile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioTopHeader(
            dspStatusLabel = uiState.dspStatusLabel,
            isBypassed = profile.isBypassed,
            selectedTargetLabel = uiState.selectedTarget.label
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutputDeviceSelector(
                    selectedTarget = uiState.selectedTarget,
                    connectedDevices = uiState.connectedDevices,
                    autoSwitchByDevice = uiState.autoSwitchByDevice,
                    onSelectTarget = viewModel::selectOutputTarget,
                    onToggleAutoSwitch = viewModel::toggleAutoSwitch
                )

                AudioVisualizerPanel(
                    frame = uiState.visualizerFrame,
                    bandGainsDb = profile.bandGainsDb,
                    preampDb = profile.preampDb,
                    isBypassed = profile.isBypassed,
                    visualizerMode = uiState.visualizerMode,
                    testAudioSource = uiState.testAudioSource,
                    hasAudioPermission = uiState.hasAudioPermission,
                    onSelectMode = viewModel::setVisualizerMode,
                    onCycleTestAudio = viewModel::cycleTestAudioSource,
                    onRequestAudioPermission = onRequestAudioPermission,
                    threeBandEnabled = profile.threeBandEnabled,
                    threeBandBassDb = profile.threeBandBassDb,
                    threeBandMidsDb = profile.threeBandMidsDb,
                    threeBandTrebleDb = profile.threeBandTrebleDb,
                    accommodationsEnabled = profile.accommodationsEnabled,
                    accommodationsToneProfile = profile.accommodationsToneProfile,
                    accommodationsSoftIntensity = profile.accommodationsSoftIntensity,
                    accommodationsApplyToMedia = profile.accommodationsApplyToMedia,
                    loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
                    outputTarget = uiState.selectedTarget
                )

                PresetQuickStrip(
                    selectedPresetId = profile.selectedPresetId,
                    onSelectPreset = viewModel::applyPreset
                )

                AudioRoutingServiceCard(
                    serviceRunning = uiState.serviceRunning,
                    globalMixFallbackEnabled = uiState.globalMixFallbackEnabled,
                    activeSessions = uiState.activeAudioSessions,
                    onToggleService = viewModel::toggleBackgroundService,
                    onToggleGlobalMixFallback = viewModel::toggleGlobalMixFallback
                )
            }

            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MasterActionBar(
                    isBypassed = profile.isBypassed,
                    activePresetName = uiState.activePresetName,
                    onToggleBypass = viewModel::toggleBypass,
                    onReset = viewModel::resetToFlat,
                    onOpenPresetsSheet = onOpenPresetsSheet
                )

                HeadphoneAccommodationsCard(
                    profile = profile,
                    telemetry = uiState.audioLabTelemetry,
                    hasAudioPermission = uiState.hasAudioPermission,
                    onToggleEnabled = viewModel::toggleHeadphoneAccommodations,
                    onSelectToneProfile = viewModel::setAccommodationsToneProfile,
                    onSelectSoftIntensity = viewModel::setAccommodationsSoftIntensity,
                    onOpenCustomAudioSetup = viewModel::openCustomAudioSetup,
                    onToggleApplyToMedia = viewModel::toggleAccommodationsMedia,
                    onToggleApplyToVoice = onToggleAccommodationsVoiceWithPermission
                )

                EqualizerConsole(
                    bandGainsDb = profile.bandGainsDb,
                    preampDb = profile.preampDb,
                    isBypassed = profile.isBypassed,
                    onBandGainChange = viewModel::updateBandGain,
                    onPreampChange = viewModel::updatePreamp
                )

                ThreeBandEqualizerCard(
                    threeBandEnabled = profile.threeBandEnabled,
                    bassDb = profile.threeBandBassDb,
                    midsDb = profile.threeBandMidsDb,
                    trebleDb = profile.threeBandTrebleDb,
                    isMasterBypassed = profile.isBypassed,
                    onBassChange = viewModel::updateThreeBandBass,
                    onMidsChange = viewModel::updateThreeBandMids,
                    onTrebleChange = viewModel::updateThreeBandTreble,
                    onToggleEnabled = viewModel::toggleThreeBandEnabled,
                    onReset = viewModel::resetThreeBand
                )

                AnimatedVisibility(visible = uiState.selectedTarget == OutputTarget.SPEAKER) {
                    SpeakerDspCard(
                        speakerDspEnabled = profile.speakerDspEnabled,
                        virtualBassEnabled = profile.virtualBassEnabled,
                        isBypassed = profile.isBypassed,
                        onToggleSpeakerDsp = viewModel::toggleSpeakerDsp,
                        onToggleVirtualBass = viewModel::toggleVirtualBass
                    )
                }

                LateNightDynamicsCard(
                    lateNightEnabled = profile.lateNightEnabled,
                    isBypassed = profile.isBypassed,
                    nightIntensity = profile.nightIntensity,
                    gainReductionDb = uiState.visualizerFrame.gainReductionDb,
                    onToggleLateNight = viewModel::toggleLateNightMode,
                    onSelectIntensity = viewModel::setNightIntensity
                )

                SpatialAudioCard(
                    spatialMode = profile.spatialMode,
                    outputTarget = uiState.selectedTarget,
                    spatialDepth = profile.spatialDepth,
                    heightPresence = profile.heightPresence,
                    vocalAnchor = profile.vocalAnchor,
                    isBypassed = profile.isBypassed,
                    onSelectMode = viewModel::setSpatialMode,
                    onDepthChange = viewModel::updateSpatialDepth,
                    onHeightChange = viewModel::updateHeightPresence,
                    onToggleVocalAnchor = viewModel::toggleVocalAnchor
                )

                IosAudioLabCard(
                    profile = profile,
                    telemetry = uiState.audioLabTelemetry,
                    abCompareHeld = uiState.abCompareHeld,
                    hasAudioPermission = uiState.hasAudioPermission,
                    onToggleSoundCheck = viewModel::toggleSoundCheck,
                    onToggleConversationAwareness = onToggleConversationWithPermission,
                    onSelectBackgroundSound = viewModel::selectBackgroundSound,
                    onBackgroundVolumeChange = viewModel::updateBackgroundVolume,
                    onStartHearingCheck = viewModel::startHearingCheck,
                    onApplySavedAudiogram = viewModel::applyAudiogramCurve,
                    onAbCompareHeld = viewModel::setAbCompareHeld,
                    onToggleVoiceIsolation = viewModel::toggleVoiceIsolation
                )

                LoudnessMaximizerCard(
                    loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
                    boostPercent = profile.loudnessBoostPercent,
                    outputTarget = uiState.selectedTarget,
                    isMasterBypassed = profile.isBypassed,
                    telemetry = uiState.audioLabTelemetry,
                    onToggleEnabled = viewModel::toggleLoudnessMaximizer,
                    onBoostPercentChange = viewModel::updateLoudnessBoostPercent
                )
            }
        }
    }
}

@Composable
private fun StudioTopHeader(
    dspStatusLabel: String,
    isBypassed: Boolean,
    selectedTargetLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SignalCoralSoft)
                    .border(1.dp, SignalCoral.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "PulseEQ",
                    tint = SignalCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "PulseEQ",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = TextPrimary
                )
                Text(
                    text = "Tuning: $selectedTargetLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(StudioSurfaceElevated)
                .border(1.dp, StudioBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isBypassed) TextMuted else RouteEmerald)
            )
            Text(
                text = dspStatusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isBypassed) TextMuted else AnalyzerCyan
            )
        }
    }
}
