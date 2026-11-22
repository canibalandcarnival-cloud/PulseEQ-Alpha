package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLabTelemetry
import com.example.model.BackgroundSoundPreset
import com.example.model.OutputEqProfile
import com.example.model.SpatialMode
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IosAudioLabCard(
    profile: OutputEqProfile,
    telemetry: AudioLabTelemetry,
    abCompareHeld: Boolean,
    hasAudioPermission: Boolean,
    onToggleSoundCheck: () -> Unit,
    onToggleConversationAwareness: () -> Unit,
    onSelectBackgroundSound: (BackgroundSoundPreset) -> Unit,
    onBackgroundVolumeChange: (Float) -> Unit,
    onStartHearingCheck: () -> Unit,
    onApplySavedAudiogram: (List<Float>) -> Unit,
    onAbCompareHeld: (Boolean) -> Unit,
    onToggleVoiceIsolation: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { onAbCompareHeld(false) }
    }

    val anyLabActive = !profile.isBypassed && (
        profile.soundCheckEnabled ||
            profile.conversationAwarenessEnabled ||
            profile.backgroundSoundPreset != BackgroundSoundPreset.OFF ||
            profile.voiceIsolationEnabled ||
            abCompareHeld
        )

    val borderColor by animateColorAsState(
        targetValue = when {
            abCompareHeld -> WarmAmber.copy(alpha = 0.85f)
            anyLabActive -> AnalyzerCyan.copy(alpha = 0.6f)
            else -> StudioBorder
        },
        animationSpec = tween(200),
        label = "iosLabBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Apple-Style Lossless / Spatial Badge
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
                        .background(if (anyLabActive) AnalyzerCyanSoft else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (anyLabActive) AnalyzerCyan.copy(alpha = 0.6f) else StudioBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "iOS Audio Lab",
                        tint = if (anyLabActive) AnalyzerCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "iOS Audio Lab",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "Sound Check • Auto-Duck • Ambience • Audiogram",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Apple-style Lossless / Spatial status pill
            val badgeText = when {
                abCompareHeld -> "A: ORIGINAL"
                profile.spatialMode != SpatialMode.OFF -> "SPATIAL • LOSSLESS"
                else -> "LOSSLESS 24-BIT"
            }
            val badgeColor = if (abCompareHeld) WarmAmber else TextPrimary
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (abCompareHeld) WarmAmber.copy(alpha = 0.16f) else StudioBackground)
                    .border(
                        1.dp,
                        if (abCompareHeld) WarmAmber else StudioBorder,
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("lossless_status_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (abCompareHeld) WarmAmber else AnalyzerCyan,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = badgeColor
                )
            }
        }

        // 5. Loudness-Matched "Hold to Compare (A/B)" Pill Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (abCompareHeld) WarmAmber.copy(alpha = 0.22f) else StudioBackground
                )
                .border(
                    width = 1.2.dp,
                    color = if (abCompareHeld) WarmAmber else AnalyzerCyan.copy(alpha = 0.55f),
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
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("ab_compare_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CompareArrows,
                    contentDescription = "Hold to Compare A/B",
                    tint = if (abCompareHeld) WarmAmber else AnalyzerCyan,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (abCompareHeld) {
                        "A — ORIGINAL BYPASSED (LOUDNESS MATCHED)"
                    } else {
                        "HOLD TO COMPARE (A/B) • B: PROCESSED"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (abCompareHeld) WarmAmber else TextPrimary
                )
            }
        }

        // 1. Sound Check (Auto-Volume Normalization -> -16 LUFS)
        val scTrimLabel = if (profile.soundCheckEnabled && !profile.isBypassed) {
            String.format(
                Locale.US,
                "Active • %.0f LUFS → -16 LUFS (%+.1f dB)",
                telemetry.inputLufs,
                telemetry.soundCheckTrimDb
            )
        } else {
            "Normalizes quiet & loud tracks smoothly toward -16 LUFS"
        }
        LabFeatureSwitchRow(
            icon = Icons.Rounded.Tune,
            title = "Sound Check (-16 LUFS)",
            subtitle = scTrimLabel,
            checked = profile.soundCheckEnabled,
            accentColor = RouteEmerald,
            onToggle = onToggleSoundCheck,
            testTag = "sound_check_toggle"
        )

        // 2. Conversation Awareness (Smart Mic Auto-Ducking)
        val duckPct = ((1f - telemetry.duckingGainLinear) * 100f).roundToInt().coerceIn(0, 75)
        val convSubtitle = when {
            !profile.conversationAwarenessEnabled -> {
                if (hasAudioPermission) {
                    "Fades music -75% on 300Hz–3kHz speech, restores 2s after"
                } else {
                    "Tap to grant Mic permission & enable smart auto-ducking"
                }
            }
            telemetry.speechDetected -> "Speech detected • Ducking music -$duckPct% (restores in 2s)"
            else -> "Listening on Mic (300Hz–3kHz threshold) • Music 100%"
        }
        LabFeatureSwitchRow(
            icon = Icons.Rounded.RecordVoiceOver,
            title = "Conversation Awareness",
            subtitle = convSubtitle,
            checked = profile.conversationAwarenessEnabled,
            accentColor = if (telemetry.speechDetected) SignalCoral else WarmAmber,
            onToggle = onToggleConversationAwareness,
            testTag = "conversation_awareness_toggle"
        )

        // 6. Voice Isolation Mode (120Hz-6.5kHz Bandpass + -38dB Gate)
        val voiceSubtitle = if (profile.voiceIsolationEnabled && !profile.isBypassed) {
            if (telemetry.voiceGateAttenuationDb < -1f) {
                String.format(
                    Locale.US,
                    "120Hz–6.5kHz Bandpass • Noise Gate %.1f dB",
                    telemetry.voiceGateAttenuationDb
                )
            } else {
                "120Hz–6.5kHz Speech Bandpass • -38 dB Expander Open"
            }
        } else {
            "120Hz–6.5kHz vocal bandpass + -38dB hiss/fan noise gate"
        }
        LabFeatureSwitchRow(
            icon = Icons.Rounded.NoiseAware,
            title = "Voice Isolation Mode",
            subtitle = voiceSubtitle,
            checked = profile.voiceIsolationEnabled,
            accentColor = AnalyzerCyan,
            onToggle = onToggleVoiceIsolation,
            testTag = "voice_isolation_toggle"
        )

        // 3. iOS Background Sounds Mixer (4 Apple Presets + Volume Slider)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground)
                .border(
                    1.dp,
                    if (profile.backgroundSoundPreset != BackgroundSoundPreset.OFF) {
                        RouteEmerald.copy(alpha = 0.5f)
                    } else {
                        StudioBorder
                    },
                    RoundedCornerShape(12.dp)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = "Background Sounds Mixer",
                        tint = if (profile.backgroundSoundPreset != BackgroundSoundPreset.OFF) {
                            RouteEmerald
                        } else {
                            TextSecondary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Background Sounds Mixer",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                }
                Text(
                    text = profile.backgroundSoundPreset.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (profile.backgroundSoundPreset != BackgroundSoundPreset.OFF) {
                        RouteEmerald
                    } else {
                        TextMuted
                    }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BackgroundSoundPreset.entries.forEach { preset ->
                    val selected = preset == profile.backgroundSoundPreset
                    val chipColor = if (preset == BackgroundSoundPreset.OFF) TextSecondary else RouteEmerald
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) chipColor.copy(alpha = 0.16f) else StudioSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (selected) chipColor else StudioBorder,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { onSelectBackgroundSound(preset) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("bg_sound_${preset.id}")
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) chipColor else TextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = profile.backgroundSoundPreset != BackgroundSoundPreset.OFF) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Background Volume (${profile.backgroundSoundPreset.subtitle})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(profile.backgroundSoundVolume * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RouteEmerald
                        )
                    }
                    Slider(
                        value = profile.backgroundSoundVolume.coerceIn(0f, 1f),
                        onValueChange = onBackgroundVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = RouteEmerald,
                            activeTrackColor = RouteEmerald,
                            inactiveTrackColor = StudioSurfaceHighlight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("bg_sound_volume_slider")
                    )
                }
            }
        }

        // 4. Quick Hearing Check (Custom Audiogram EQ Generator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(SignalCoralSoft)
                .border(1.dp, SignalCoral.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .clickable(onClick = onStartHearingCheck)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("hearing_check_start_button"),
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
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SignalCoral.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Hearing,
                        contentDescription = "Hearing Check",
                        tint = SignalCoral,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Take 60-Second Hearing Check",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "500Hz • 1kHz • 2kHz • 4kHz • 8kHz custom audiogram EQ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextSecondary
                    )
                }
            }

            if (profile.audiogramGainsDb != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioBackground)
                        .border(1.dp, SignalCoral.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onApplySavedAudiogram(profile.audiogramGainsDb) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("apply_saved_audiogram_btn")
                ) {
                    Text(
                        text = "Apply Saved",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SignalCoral
                    )
                }
            }
        }
    }
}

@Composable
private fun LabFeatureSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBackground)
            .border(
                width = 1.dp,
                color = if (checked) accentColor.copy(alpha = 0.55f) else StudioBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onToggle)
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
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) accentColor else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (checked) accentColor.copy(alpha = 0.9f) else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioBackground,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = StudioSurfaceElevated
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
