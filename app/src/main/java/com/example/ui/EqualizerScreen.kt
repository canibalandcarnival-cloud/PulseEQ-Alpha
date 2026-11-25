package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ListeningMode
import com.example.model.OutputTarget
import com.example.ui.components.AdaptiveMultibandPhaseCard
import com.example.ui.components.AudioRoutingServiceCard
import com.example.ui.components.AudioVisualizerPanel
import com.example.ui.components.CustomAudioSetupDialog
import com.example.ui.components.EqualizerConsole
import com.example.ui.components.HeadphoneAccommodationsCard
import com.example.ui.components.HearingCheckDialog
import com.example.ui.components.IosAudioLabCard
import com.example.ui.components.LateNightDynamicsCard
import com.example.ui.components.ListeningModeCard
import com.example.ui.components.LoudnessMaximizerCard
import com.example.ui.components.MasterActionBar
import com.example.ui.components.OutputDeviceSelector
import com.example.ui.components.OutputVolumeContourCard
import com.example.ui.components.PresetQuickStrip
import com.example.ui.components.PresetsBrowserSheet
import com.example.ui.components.SiliconDspCard
import com.example.ui.components.SpatialAudioCard
import com.example.ui.components.SpeakerDspCard
import com.example.ui.components.StudioHiResMasteringCard
import com.example.ui.components.ThreeBandEqualizerCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.model.SpatialMode
import com.example.ui.components.AudioMixCard
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleMint
import com.example.ui.theme.AppleMintSoft
import com.example.ui.theme.DynamicIslandBorder
import com.example.ui.theme.DynamicIslandSurface
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.viewmodel.EqualizerUiState
import com.example.viewmodel.EqualizerViewModel

@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPresetsSheet by remember { mutableStateOf(false) }
    var pendingListeningMode by remember { mutableStateOf<ListeningMode?>(null) }

    LaunchedEffect(isDeviceLandscape) {
        viewModel.updateDetectedDeviceOrientation(isDeviceLandscape)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    val listeningModeMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        val pending = pendingListeningMode
        pendingListeningMode = null
        if (pending != null) {
            viewModel.setListeningMode(pending)
        }
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

                val onSelectListeningModeWithPermission: (ListeningMode) -> Unit = { mode ->
                    val needsMic = mode == ListeningMode.TRANSPARENCY || mode == ListeningMode.ADAPTIVE
                    val micGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!needsMic || micGranted) {
                        viewModel.setListeningMode(mode)
                    } else {
                        pendingListeningMode = mode
                        listeningModeMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                if (isWideLayout) {
                    WideConsoleLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenPresetsSheet = { showPresetsSheet = true },
                        onRequestAudioPermission = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onSelectListeningModeWithPermission = onSelectListeningModeWithPermission,
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
                        onSelectListeningModeWithPermission = onSelectListeningModeWithPermission,
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
        onChooseStep1Speech = viewModel::chooseWizardStep1Speech,
        onChooseStep2Music = viewModel::chooseWizardStep2Music,
        onChooseStep3AndApply = viewModel::chooseWizardStep3SensitivityAndApply,
        onRestart = viewModel::restartCustomAudioSetup,
        onClose = viewModel::closeCustomAudioSetup
    )
}

@Composable
private fun CompactConsoleLayout(
    uiState: EqualizerUiState,
    viewModel: EqualizerViewModel,
    onOpenPresetsSheet: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onSelectListeningModeWithPermission: (ListeningMode) -> Unit,
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
            selectedTargetLabel = uiState.selectedTarget.label,
            spatialMode = profile.spatialMode,
            abCompareHeld = uiState.abCompareHeld,
            onAbCompareHeld = viewModel::setAbCompareHeld
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
            tenBandEnabled = profile.tenBandEnabled,
            threeBandEnabled = profile.threeBandEnabled,
            threeBandBassDb = profile.threeBandBassDb,
            threeBandMidsDb = profile.threeBandMidsDb,
            threeBandTrebleDb = profile.threeBandTrebleDb,
            threeBandSmartCurveEnabled = profile.threeBandSmartCurveEnabled,
            accommodationsEnabled = profile.accommodationsEnabled,
            accommodationsToneProfile = profile.accommodationsToneProfile,
            accommodationsSoftIntensity = profile.accommodationsSoftIntensity,
            accommodationsApplyToMedia = profile.accommodationsApplyToMedia,
            loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
            outputTarget = uiState.selectedTarget,
            audioMixMode = profile.audioMixMode,
            trebleSmoothnessPercent = profile.trebleSmoothnessPercent,
            spatialMode = profile.spatialMode,
            spatialHeightPresence = profile.heightPresence,
            siliconDspEnabled = profile.siliconDspEnabled,
            masterVolumePercent = profile.masterVolumePercent,
            speakerDspEnabled = profile.speakerDspEnabled,
            proMaxBodyWarmthEnabled = profile.proMaxBodyWarmthEnabled,
            listeningMode = profile.listeningMode,
            adaptiveWarmthActive = uiState.audioLabTelemetry.adaptiveRumbleDuckActive
        )

        MasterActionBar(
            isBypassed = profile.isBypassed,
            activePresetName = uiState.activePresetName,
            onToggleBypass = viewModel::toggleBypass,
            onReset = viewModel::resetToFlat,
            onOpenPresetsSheet = onOpenPresetsSheet
        )

        ListeningModeCard(
            listeningMode = profile.listeningMode,
            ambientPassThroughPercent = profile.ambientPassThroughPercent,
            conversationAwarenessEnabled = profile.conversationAwarenessEnabled,
            isMasterBypassed = profile.isBypassed,
            outputTarget = uiState.selectedTarget,
            hasAudioPermission = uiState.hasAudioPermission,
            telemetry = uiState.audioLabTelemetry,
            onSelectMode = onSelectListeningModeWithPermission,
            onAmbientPassThroughChange = viewModel::updateAmbientPassThrough,
            onToggleConversationAwareness = onToggleConversationWithPermission,
            onRequestAudioPermission = onRequestAudioPermission
        )

        OutputVolumeContourCard(
            masterVolumePercent = profile.masterVolumePercent,
            isBypassed = profile.isBypassed,
            onVolumeChange = viewModel::updateMasterVolume
        )

        AudioMixCard(
            audioMixMode = profile.audioMixMode,
            isBypassed = profile.isBypassed,
            onSelectMode = viewModel::setAudioMixMode
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
            onToggleApplyToVoice = onToggleAccommodationsVoiceWithPermission,
            onToggleAutoEngageHeadphones = viewModel::toggleAccommodationsAutoEngage,
            onOutputGainChange = viewModel::updateAccommodationsOutputGain
        )

        EqModeSegmentedSwitcher(
            tenBandEnabled = profile.tenBandEnabled,
            threeBandEnabled = profile.threeBandEnabled,
            onSelectEqMode = viewModel::selectEqMode
        )

        EqualizerConsole(
            bandGainsDb = profile.bandGainsDb,
            preampDb = profile.preampDb,
            isBypassed = profile.isBypassed,
            tenBandEnabled = profile.tenBandEnabled,
            onToggleEnabled = viewModel::toggleTenBandEnabled,
            onBandGainChange = viewModel::updateBandGain,
            onPreampChange = viewModel::updatePreamp
        )

        ThreeBandEqualizerCard(
            threeBandEnabled = profile.threeBandEnabled,
            bassDb = profile.threeBandBassDb,
            midsDb = profile.threeBandMidsDb,
            trebleDb = profile.threeBandTrebleDb,
            smartCurveEnabled = profile.threeBandSmartCurveEnabled,
            recommendedMode = profile.threeBandRecommendedMode,
            recommendedBannerVisible = profile.threeBandRecommendedBannerVisible,
            isSpeakerTarget = (uiState.selectedTarget == OutputTarget.SPEAKER),
            isMasterBypassed = profile.isBypassed,
            onBassChange = viewModel::updateThreeBandBass,
            onMidsChange = viewModel::updateThreeBandMids,
            onTrebleChange = viewModel::updateThreeBandTreble,
            onToggleEnabled = viewModel::toggleThreeBandEnabled,
            onToggleSmartCurve = viewModel::toggleThreeBandSmartCurve,
            onSelectRecommendedMode = viewModel::setThreeBandRecommendedMode,
            onDismissRecommendedBanner = viewModel::dismissThreeBandRecommendedBanner,
            onReset = viewModel::resetThreeBand
        )

        AnimatedVisibility(visible = uiState.selectedTarget == OutputTarget.SPEAKER) {
            SpeakerDspCard(
                speakerDspEnabled = profile.speakerDspEnabled,
                virtualBassEnabled = profile.virtualBassEnabled,
                landscapeStereoStage = profile.landscapeStereoStage,
                speakerTuningProfile = profile.speakerTuningProfile,
                phoneOrientationMode = profile.phoneOrientationMode,
                deviceIsLandscape = uiState.deviceIsLandscape,
                proMaxBodyWarmthEnabled = profile.proMaxBodyWarmthEnabled,
                isBypassed = profile.isBypassed,
                isInfinixHot60CompensatorActive = profile.isInfinixHot60CompensatorActive(),
                onToggleSpeakerDsp = viewModel::toggleSpeakerDsp,
                onToggleVirtualBass = viewModel::toggleVirtualBass,
                onToggleLandscapeStereo = viewModel::toggleLandscapeStereoStage,
                onSelectTuningProfile = viewModel::selectSpeakerTuningProfile,
                onSelectOrientationMode = viewModel::setPhoneOrientationMode,
                onToggleBodyWarmth = viewModel::toggleProMaxBodyWarmth,
                onApplyInfinixHot60Compensator = viewModel::applyInfinixHot60Compensator
            )
        }

        SiliconDspCard(
            siliconDspEnabled = profile.siliconDspEnabled,
            outputTarget = uiState.selectedTarget,
            isMasterBypassed = profile.isBypassed,
            abCompareHeld = uiState.abCompareHeld,
            telemetry = uiState.audioLabTelemetry,
            trebleSmoothnessPercent = profile.trebleSmoothnessPercent,
            onToggleSiliconDsp = viewModel::toggleSiliconDsp,
            onTrebleSmoothnessChange = viewModel::updateTrebleSmoothness,
            onAbCompareHeld = viewModel::setAbCompareHeld
        )

        AdaptiveMultibandPhaseCard(
            adaptiveMultibandEnabled = profile.adaptiveMultibandEnabled,
            speakerPhaseAlignmentEnabled = profile.speakerPhaseAlignmentEnabled,
            soundCheckEnabled = profile.soundCheckEnabled,
            speakerDspEnabled = profile.speakerDspEnabled,
            outputTarget = uiState.selectedTarget,
            isMasterBypassed = profile.isBypassed,
            abCompareHeld = uiState.abCompareHeld,
            telemetry = uiState.audioLabTelemetry,
            onToggleAdaptiveMultiband = viewModel::toggleAdaptiveMultiband,
            onToggleSpeakerPhaseAlignment = viewModel::toggleSpeakerPhaseAlignment,
            onToggleSoundCheck = viewModel::toggleSoundCheck
        )

        StudioHiResMasteringCard(
            oversampledEqEnabled = profile.oversampledEqEnabled,
            harmonicSheenPercent = profile.harmonicSheenPercent,
            noiseFloorGateEnabled = profile.noiseFloorGateEnabled,
            isMasterBypassed = profile.isBypassed,
            abCompareHeld = uiState.abCompareHeld,
            telemetry = uiState.audioLabTelemetry,
            onToggleOversampledEq = viewModel::toggleOversampledEq,
            onHarmonicSheenChange = viewModel::updateHarmonicSheen,
            onToggleNoiseFloorGate = viewModel::toggleNoiseFloorGate
        )

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
            vocalFocus = profile.vocalFocus,
            isBypassed = profile.isBypassed,
            onSelectMode = viewModel::setSpatialMode,
            onVocalFocusChange = viewModel::updateVocalFocus
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
    onSelectListeningModeWithPermission: (ListeningMode) -> Unit,
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
            selectedTargetLabel = uiState.selectedTarget.label,
            spatialMode = profile.spatialMode,
            abCompareHeld = uiState.abCompareHeld,
            onAbCompareHeld = viewModel::setAbCompareHeld
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
                    tenBandEnabled = profile.tenBandEnabled,
                    threeBandEnabled = profile.threeBandEnabled,
                    threeBandBassDb = profile.threeBandBassDb,
                    threeBandMidsDb = profile.threeBandMidsDb,
                    threeBandTrebleDb = profile.threeBandTrebleDb,
                    threeBandSmartCurveEnabled = profile.threeBandSmartCurveEnabled,
                    accommodationsEnabled = profile.accommodationsEnabled,
                    accommodationsToneProfile = profile.accommodationsToneProfile,
                    accommodationsSoftIntensity = profile.accommodationsSoftIntensity,
                    accommodationsApplyToMedia = profile.accommodationsApplyToMedia,
                    loudnessMaximizerEnabled = profile.loudnessMaximizerEnabled,
                    outputTarget = uiState.selectedTarget,
                    audioMixMode = profile.audioMixMode,
                    trebleSmoothnessPercent = profile.trebleSmoothnessPercent,
                    spatialMode = profile.spatialMode,
                    spatialHeightPresence = profile.heightPresence,
                    siliconDspEnabled = profile.siliconDspEnabled,
                    masterVolumePercent = profile.masterVolumePercent,
                    speakerDspEnabled = profile.speakerDspEnabled,
                    proMaxBodyWarmthEnabled = profile.proMaxBodyWarmthEnabled,
                    listeningMode = profile.listeningMode,
                    adaptiveWarmthActive = uiState.audioLabTelemetry.adaptiveRumbleDuckActive
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

                ListeningModeCard(
                    listeningMode = profile.listeningMode,
                    ambientPassThroughPercent = profile.ambientPassThroughPercent,
                    conversationAwarenessEnabled = profile.conversationAwarenessEnabled,
                    isMasterBypassed = profile.isBypassed,
                    outputTarget = uiState.selectedTarget,
                    hasAudioPermission = uiState.hasAudioPermission,
                    telemetry = uiState.audioLabTelemetry,
                    onSelectMode = onSelectListeningModeWithPermission,
                    onAmbientPassThroughChange = viewModel::updateAmbientPassThrough,
                    onToggleConversationAwareness = onToggleConversationWithPermission,
                    onRequestAudioPermission = onRequestAudioPermission
                )

                OutputVolumeContourCard(
                    masterVolumePercent = profile.masterVolumePercent,
                    isBypassed = profile.isBypassed,
                    onVolumeChange = viewModel::updateMasterVolume
                )

                AudioMixCard(
                    audioMixMode = profile.audioMixMode,
                    isBypassed = profile.isBypassed,
                    onSelectMode = viewModel::setAudioMixMode
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
                    onToggleApplyToVoice = onToggleAccommodationsVoiceWithPermission,
                    onToggleAutoEngageHeadphones = viewModel::toggleAccommodationsAutoEngage,
                    onOutputGainChange = viewModel::updateAccommodationsOutputGain
                )

                EqModeSegmentedSwitcher(
                    tenBandEnabled = profile.tenBandEnabled,
                    threeBandEnabled = profile.threeBandEnabled,
                    onSelectEqMode = viewModel::selectEqMode
                )

                EqualizerConsole(
                    bandGainsDb = profile.bandGainsDb,
                    preampDb = profile.preampDb,
                    isBypassed = profile.isBypassed,
                    tenBandEnabled = profile.tenBandEnabled,
                    onToggleEnabled = viewModel::toggleTenBandEnabled,
                    onBandGainChange = viewModel::updateBandGain,
                    onPreampChange = viewModel::updatePreamp
                )

                ThreeBandEqualizerCard(
                    threeBandEnabled = profile.threeBandEnabled,
                    bassDb = profile.threeBandBassDb,
                    midsDb = profile.threeBandMidsDb,
                    trebleDb = profile.threeBandTrebleDb,
                    smartCurveEnabled = profile.threeBandSmartCurveEnabled,
                    recommendedMode = profile.threeBandRecommendedMode,
                    recommendedBannerVisible = profile.threeBandRecommendedBannerVisible,
                    isSpeakerTarget = (uiState.selectedTarget == OutputTarget.SPEAKER),
                    isMasterBypassed = profile.isBypassed,
                    onBassChange = viewModel::updateThreeBandBass,
                    onMidsChange = viewModel::updateThreeBandMids,
                    onTrebleChange = viewModel::updateThreeBandTreble,
                    onToggleEnabled = viewModel::toggleThreeBandEnabled,
                    onToggleSmartCurve = viewModel::toggleThreeBandSmartCurve,
                    onSelectRecommendedMode = viewModel::setThreeBandRecommendedMode,
                    onDismissRecommendedBanner = viewModel::dismissThreeBandRecommendedBanner,
                    onReset = viewModel::resetThreeBand
                )

                AnimatedVisibility(visible = uiState.selectedTarget == OutputTarget.SPEAKER) {
                    SpeakerDspCard(
                        speakerDspEnabled = profile.speakerDspEnabled,
                        virtualBassEnabled = profile.virtualBassEnabled,
                        landscapeStereoStage = profile.landscapeStereoStage,
                        speakerTuningProfile = profile.speakerTuningProfile,
                        phoneOrientationMode = profile.phoneOrientationMode,
                        deviceIsLandscape = uiState.deviceIsLandscape,
                        proMaxBodyWarmthEnabled = profile.proMaxBodyWarmthEnabled,
                        isBypassed = profile.isBypassed,
                        isInfinixHot60CompensatorActive = profile.isInfinixHot60CompensatorActive(),
                        onToggleSpeakerDsp = viewModel::toggleSpeakerDsp,
                        onToggleVirtualBass = viewModel::toggleVirtualBass,
                        onToggleLandscapeStereo = viewModel::toggleLandscapeStereoStage,
                        onSelectTuningProfile = viewModel::selectSpeakerTuningProfile,
                        onSelectOrientationMode = viewModel::setPhoneOrientationMode,
                        onToggleBodyWarmth = viewModel::toggleProMaxBodyWarmth,
                        onApplyInfinixHot60Compensator = viewModel::applyInfinixHot60Compensator
                    )
                }

                SiliconDspCard(
                    siliconDspEnabled = profile.siliconDspEnabled,
                    outputTarget = uiState.selectedTarget,
                    isMasterBypassed = profile.isBypassed,
                    abCompareHeld = uiState.abCompareHeld,
                    telemetry = uiState.audioLabTelemetry,
                    trebleSmoothnessPercent = profile.trebleSmoothnessPercent,
                    onToggleSiliconDsp = viewModel::toggleSiliconDsp,
                    onTrebleSmoothnessChange = viewModel::updateTrebleSmoothness,
                    onAbCompareHeld = viewModel::setAbCompareHeld
                )

                AdaptiveMultibandPhaseCard(
                    adaptiveMultibandEnabled = profile.adaptiveMultibandEnabled,
                    speakerPhaseAlignmentEnabled = profile.speakerPhaseAlignmentEnabled,
                    soundCheckEnabled = profile.soundCheckEnabled,
                    speakerDspEnabled = profile.speakerDspEnabled,
                    outputTarget = uiState.selectedTarget,
                    isMasterBypassed = profile.isBypassed,
                    abCompareHeld = uiState.abCompareHeld,
                    telemetry = uiState.audioLabTelemetry,
                    onToggleAdaptiveMultiband = viewModel::toggleAdaptiveMultiband,
                    onToggleSpeakerPhaseAlignment = viewModel::toggleSpeakerPhaseAlignment,
                    onToggleSoundCheck = viewModel::toggleSoundCheck
                )

                StudioHiResMasteringCard(
                    oversampledEqEnabled = profile.oversampledEqEnabled,
                    harmonicSheenPercent = profile.harmonicSheenPercent,
                    noiseFloorGateEnabled = profile.noiseFloorGateEnabled,
                    isMasterBypassed = profile.isBypassed,
                    abCompareHeld = uiState.abCompareHeld,
                    telemetry = uiState.audioLabTelemetry,
                    onToggleOversampledEq = viewModel::toggleOversampledEq,
                    onHarmonicSheenChange = viewModel::updateHarmonicSheen,
                    onToggleNoiseFloorGate = viewModel::toggleNoiseFloorGate
                )

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
                    vocalFocus = profile.vocalFocus,
                    isBypassed = profile.isBypassed,
                    onSelectMode = viewModel::setSpatialMode,
                    onVocalFocusChange = viewModel::updateVocalFocus
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioTopHeader(
    dspStatusLabel: String,
    isBypassed: Boolean,
    selectedTargetLabel: String,
    spatialMode: SpatialMode,
    abCompareHeld: Boolean,
    onAbCompareHeld: (Boolean) -> Unit
) {
    DisposableEffect(Unit) {
        onDispose { onAbCompareHeld(false) }
    }

    val spatialActive = spatialMode != SpatialMode.OFF && !isBypassed

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(AppleGlassTop, AppleGlassBottom)
                            )
                        )
                        .border(1.dp, SignalCoral.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "PulseEQ",
                        tint = SignalCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PulseEQ",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "iPhone 18 Pro Max • iOS 27 • $selectedTargetLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Prominent Quick "Hold to Compare (A/B)" Loudness-Matched Pill Button
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (abCompareHeld) {
                            WarmAmber.copy(alpha = 0.24f)
                        } else {
                            AppleGlassTile
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (abCompareHeld) WarmAmber else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onAbCompareHeld(true)
                            waitForUpOrCancellation()
                            onAbCompareHeld(false)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("header_ab_compare_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CompareArrows,
                    contentDescription = "Hold to Compare A/B",
                    tint = if (abCompareHeld) WarmAmber else AnalyzerCyan,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (abCompareHeld) "A: ORIG" else "HOLD A/B",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (abCompareHeld) WarmAmber else TextPrimary
                )
            }
        }

        // iOS 27 Dynamic Island Live Status Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(DynamicIslandSurface)
                .border(1.dp, DynamicIslandBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                abCompareHeld -> WarmAmber
                                isBypassed -> TextMuted
                                else -> RouteEmerald
                            }
                        )
                )
                Text(
                    text = if (abCompareHeld) "A/B Compare • Loudness Matched Bypass" else dspStatusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = when {
                        abCompareHeld -> WarmAmber
                        isBypassed -> TextMuted
                        else -> AnalyzerCyan
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Active Lossless 24-bit Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppleMintSoft)
                        .border(1.dp, AppleMint.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("lossless_header_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Lossless 24-bit Audio",
                        tint = AppleMint,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Lossless 24-bit",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AppleMint
                    )
                }

                // Active Spatial Audio Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (spatialActive) AppleBlueSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (spatialActive) AppleBlue.copy(alpha = 0.65f) else AppleGlassBorder,
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("spatial_header_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SurroundSound,
                        contentDescription = "Spatial Audio Status",
                        tint = if (spatialActive) AppleBlue else TextMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = if (spatialActive) "Spatial" else "Stereo",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (spatialActive) AppleBlue else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun EqModeSegmentedSwitcher(
    tenBandEnabled: Boolean,
    threeBandEnabled: Boolean,
    onSelectEqMode: (Boolean) -> Unit
) {
    val isThreeBand = threeBandEnabled && !tenBandEnabled
    val isTenBand = !isThreeBand

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppleGlassTop, AppleGlassBottom)
                )
            )
            .border(1.dp, AppleGlassBorder, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isTenBand) SignalCoralSoft else StudioBackground.copy(alpha = 0.35f),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(
                    width = 1.dp,
                    color = if (isTenBand) SignalCoral.copy(alpha = 0.72f) else StudioBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(999.dp)
                )
                .clickable { onSelectEqMode(false) }
                .testTag("eq_mode_10band_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isTenBand) SignalCoral else TextMuted)
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    text = if (isTenBand) "10-Band EQ Active" else "Switch to 10-Band",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isTenBand) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isTenBand) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isThreeBand) AnalyzerCyanSoft else StudioBackground.copy(alpha = 0.35f),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(
                    width = 1.dp,
                    color = if (isThreeBand) AnalyzerCyan.copy(alpha = 0.72f) else StudioBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(999.dp)
                )
                .clickable { onSelectEqMode(true) }
                .testTag("eq_mode_3band_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isThreeBand) AnalyzerCyan else TextMuted)
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    text = if (isThreeBand) "3-Band EQ Active" else "Switch to 3-Band",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isThreeBand) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isThreeBand) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
