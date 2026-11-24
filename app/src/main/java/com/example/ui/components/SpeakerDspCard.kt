package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PhoneOrientationMode
import com.example.model.SpeakerTuningProfile
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerDspCard(
    speakerDspEnabled: Boolean,
    virtualBassEnabled: Boolean,
    landscapeStereoStage: Boolean = true,
    speakerTuningProfile: SpeakerTuningProfile = SpeakerTuningProfile.PRO_MAX_MUSIC,
    phoneOrientationMode: PhoneOrientationMode = PhoneOrientationMode.AUTO_DETECT,
    deviceIsLandscape: Boolean = false,
    proMaxBodyWarmthEnabled: Boolean = true,
    isBypassed: Boolean,
    onToggleSpeakerDsp: () -> Unit,
    onToggleVirtualBass: () -> Unit,
    onToggleLandscapeStereo: () -> Unit = {},
    onSelectTuningProfile: (SpeakerTuningProfile) -> Unit = {},
    onSelectOrientationMode: (PhoneOrientationMode) -> Unit = {},
    onToggleBodyWarmth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val active = speakerDspEnabled && !isBypassed
    val isDialogueProfile = speakerTuningProfile == SpeakerTuningProfile.PRO_WARM_DIALOGUE
    val isCinemaProfile = speakerTuningProfile == SpeakerTuningProfile.CINEMA_LANDSCAPE
    val resolvedLandscape = when (phoneOrientationMode) {
        PhoneOrientationMode.AUTO_DETECT -> deviceIsLandscape
        PhoneOrientationMode.PORTRAIT -> false
        PhoneOrientationMode.LANDSCAPE -> true
    }
    val effectiveCinemaOrLandscapeXtc = isCinemaProfile || (landscapeStereoStage && resolvedLandscape)
    val borderColor by animateColorAsState(
        targetValue = if (active) RouteEmerald.copy(alpha = 0.68f) else AppleGlassBorder,
        animationSpec = tween(200),
        label = "speakerDspBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppleGlassTop, AppleGlassBottom)
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: iPhone 18 Pro Max Hybrid Stereo Speaker DSP Master Toggle
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
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) RouteEmeraldSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (active) RouteEmerald.copy(alpha = 0.6f) else AppleGlassBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SpeakerPhone,
                        contentDescription = "iPhone 18 Pro Max Speaker DSP",
                        tint = if (active) RouteEmerald else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "iPhone 18 Pro Max Speaker DSP",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = when {
                            !active -> "Hybrid earpiece + bottom speaker acoustic tuning off"
                            isCinemaProfile -> "86Hz HPF • 128Hz +3.2dB • Cinema -26% Transaural XTC → -0.3dB"
                            isDialogueProfile -> "100Hz HPF • 18 Pro Voice Focus • 4.5k/7k Guard → -0.3dB"
                            else -> "86Hz 24dB/oct HPF • 128Hz +3.2dB Porous-Cavity • 1.45kHz+0.14ms"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) RouteEmerald else TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (speakerDspEnabled) RouteEmeraldSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (speakerDspEnabled) RouteEmerald else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleSpeakerDsp)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("speaker_dsp_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (speakerDspEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (speakerDspEnabled) RouteEmerald else TextSecondary
                )
            }
        }

        // iPhone 18 Pro Max Acoustic Profile Selector:
        // 18 Pro Max Signature | 18 Pro Voice Focus | Cinema Landscape
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "IPHONE 18 PRO MAX ACOUSTIC PROFILE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                ),
                color = TextMuted
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpeakerTuningProfile.entries.forEach { profile ->
                    val selected = speakerTuningProfile == profile
                    val accent = when (profile) {
                        SpeakerTuningProfile.PRO_MAX_MUSIC -> RouteEmerald
                        SpeakerTuningProfile.PRO_WARM_DIALOGUE -> WarmAmber
                        SpeakerTuningProfile.CINEMA_LANDSCAPE -> AppleBlue
                    }
                    val softBg = when (profile) {
                        SpeakerTuningProfile.PRO_MAX_MUSIC -> RouteEmeraldSoft
                        SpeakerTuningProfile.PRO_WARM_DIALOGUE -> WarmAmber.copy(alpha = 0.18f)
                        SpeakerTuningProfile.CINEMA_LANDSCAPE -> AppleBlueSoft
                    }
                    val tag = when (profile) {
                        SpeakerTuningProfile.PRO_MAX_MUSIC -> "speaker_tuning_pro_max_music"
                        SpeakerTuningProfile.PRO_WARM_DIALOGUE -> "speaker_tuning_pro_dialogue"
                        SpeakerTuningProfile.CINEMA_LANDSCAPE -> "speaker_tuning_cinema_landscape"
                    }
                    val icon = when (profile) {
                        SpeakerTuningProfile.PRO_MAX_MUSIC -> Icons.Rounded.GraphicEq
                        SpeakerTuningProfile.PRO_WARM_DIALOGUE -> Icons.Rounded.RecordVoiceOver
                        SpeakerTuningProfile.CINEMA_LANDSCAPE -> Icons.Rounded.SurroundSound
                    }
                    val badgeLabel = when (profile) {
                        SpeakerTuningProfile.PRO_MAX_MUSIC -> "SIGNATURE"
                        SpeakerTuningProfile.PRO_WARM_DIALOGUE -> "VOICE"
                        SpeakerTuningProfile.CINEMA_LANDSCAPE -> "XTC WIDE"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active && selected) softBg else StudioBackground.copy(alpha = 0.62f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (active && selected) accent.copy(alpha = 0.72f) else AppleGlassBorder.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectTuningProfile(profile) }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .testTag(tag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = profile.label,
                                tint = if (active && selected) accent else TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                            Column {
                                Text(
                                    text = profile.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) TextPrimary else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = profile.subtitleTag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (active && selected) accent else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (active && selected) accent.copy(alpha = 0.22f) else StudioSurfaceElevated
                                )
                                .border(
                                    1.dp,
                                    if (active && selected) accent.copy(alpha = 0.5f) else StudioBorder,
                                    RoundedCornerShape(7.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (active && selected) accent else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Orientation-Aware Speaker Stage Selector (Auto-Detect / Portrait / Landscape)
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ORIENTATION-AWARE STAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextMuted
                )
                Text(
                    text = when {
                        !active -> "Stage Off"
                        isCinemaProfile -> "Cinema XTC: -26% 0.17ms + 0.34ms"
                        !landscapeStereoStage -> "Stage Off"
                        resolvedLandscape -> "Landscape: -22% 0.19ms XTC"
                        else -> "Portrait: Center Vocal + Balanced Bass"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (active && effectiveCinemaOrLandscapeXtc) AppleBlue else if (active && landscapeStereoStage) AppleBlue else TextMuted,
                    modifier = Modifier.testTag("resolved_orientation_stage_label")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PhoneOrientationMode.entries.forEach { mode ->
                    val selected = phoneOrientationMode == mode
                    val tag = when (mode) {
                        PhoneOrientationMode.AUTO_DETECT -> "orientation_mode_auto"
                        PhoneOrientationMode.PORTRAIT -> "orientation_mode_portrait"
                        PhoneOrientationMode.LANDSCAPE -> "orientation_mode_landscape"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active && selected) AppleBlueSoft else StudioBackground.copy(alpha = 0.62f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (active && selected) AppleBlue.copy(alpha = 0.72f) else AppleGlassBorder.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectOrientationMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag(tag),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (active && selected) AppleBlue else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Hybrid Dual-Speaker Mode Toggle (enabled by default in Phone Speaker mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.62f))
                .border(
                    width = 1.dp,
                    color = if (active && landscapeStereoStage) AppleBlue.copy(alpha = 0.65f) else AppleGlassBorder.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggleLandscapeStereo)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SurroundSound,
                    contentDescription = "Hybrid Dual-Speaker Mode",
                    tint = if (active && landscapeStereoStage) AppleBlue else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Hybrid Dual-Speaker Mode",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = if (resolvedLandscape) {
                            "Landscape XTC Active • <180Hz centered • -22% 0.19ms wide"
                        } else {
                            "Portrait Focus Active • Center vocal focus + L/R balanced bass"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
            }

            Switch(
                checked = speakerDspEnabled && landscapeStereoStage,
                onCheckedChange = { onToggleLandscapeStereo() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioBackground,
                    checkedTrackColor = AppleBlue,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier.testTag("landscape_stereo_toggle")
            )
        }

        // Pro Max Chassis Body Warmth Toggle (125Hz Chamber Resonance Q=1.6, +2.5dB + 2nd-Order Warmth)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.62f))
                .border(
                    width = 1.dp,
                    color = if (active && proMaxBodyWarmthEnabled) WarmAmber.copy(alpha = 0.65f) else AppleGlassBorder.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggleBodyWarmth)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SpeakerPhone,
                    contentDescription = "Pro Max Body Warmth",
                    tint = if (active && proMaxBodyWarmthEnabled) WarmAmber else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Pro Max Body Warmth (125Hz)",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "125Hz chassis chamber (Q=1.6, +2.5dB) + 2nd-order harmonic warmth",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
            }

            Switch(
                checked = speakerDspEnabled && proMaxBodyWarmthEnabled,
                onCheckedChange = { onToggleBodyWarmth() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioBackground,
                    checkedTrackColor = WarmAmber,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier.testTag("pro_max_body_warmth_toggle")
            )
        }

        // 2nd-Order Harmonic Bass Exciter Toggle (110Hz–220Hz warm Pro Max chamber resonance)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.62f))
                .border(
                    width = 1.dp,
                    color = if (active && virtualBassEnabled) AnalyzerCyan.copy(alpha = 0.55f) else AppleGlassBorder.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggleVirtualBass)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "2nd-Order Harmonic Bass Exciter",
                    tint = if (active && virtualBassEnabled) AnalyzerCyan else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "2nd-Order Harmonic Bass Exciter",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "110Hz–220Hz warm Pro Max chamber resonance without cone rattle",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
            }

            Switch(
                checked = speakerDspEnabled && virtualBassEnabled,
                onCheckedChange = { onToggleVirtualBass() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioBackground,
                    checkedTrackColor = AnalyzerCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier.testTag("virtual_bass_toggle")
            )
        }

        // Flagship Acoustic Telemetry Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DspStageChip(
                text = if (!isDialogueProfile) "86Hz HPF 24dB/oct" else "100Hz HPF 24dB/oct",
                isActive = active,
                highlightColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
            DspStageChip(
                text = "125Hz +2.5dB (Q1.6) Chamber",
                isActive = active && proMaxBodyWarmthEnabled,
                highlightColor = WarmAmber,
                bgColor = WarmAmber.copy(alpha = 0.18f)
            )
            DspStageChip(
                text = if (!isDialogueProfile) "128Hz +3.2dB (Q1.25) • 110–220Hz 2nd" else "1.8kHz +3.2dB Voice Focus",
                isActive = active && (virtualBassEnabled || isDialogueProfile),
                highlightColor = if (!isDialogueProfile) AnalyzerCyan else WarmAmber,
                bgColor = if (!isDialogueProfile) AnalyzerCyanSoft else WarmAmber.copy(alpha = 0.18f)
            )
            DspStageChip(
                text = if (!isDialogueProfile) "350Hz -3.5dB (Q1.5) Scoop" else "350Hz -2.5dB (Q1.5) Scoop",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = "1.45kHz Allpass + 0.14ms (>2.2kHz)",
                isActive = active,
                highlightColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            DspStageChip(
                text = if (!isDialogueProfile) "4.5k -3.0 • 7k -3.5 Guard • 12k +1.0dB" else "4.5k -3.0 • 7k -3.5 Guard • 12k +0.5dB",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = when {
                    isCinemaProfile -> "Cinema Landscape: -26% 2nd-Order XTC"
                    resolvedLandscape -> "Landscape: -22% 0.19ms XTC"
                    else -> "Portrait: Vocal Center + Mono Bass"
                },
                isActive = active && effectiveCinemaOrLandscapeXtc || (active && landscapeStereoStage),
                highlightColor = AppleBlue,
                bgColor = AppleBlueSoft
            )
            DspStageChip(
                text = "3-Band LR4 2.2:1 (6ms / 85ms) → -0.3dB",
                isActive = active,
                highlightColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
        }
    }
}

@Composable
private fun DspStageChip(
    text: String,
    isActive: Boolean,
    highlightColor: Color,
    bgColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) bgColor else AppleGlassTile)
            .border(
                width = 1.dp,
                color = if (isActive) highlightColor.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (isActive) highlightColor else TextMuted
        )
    }
}
