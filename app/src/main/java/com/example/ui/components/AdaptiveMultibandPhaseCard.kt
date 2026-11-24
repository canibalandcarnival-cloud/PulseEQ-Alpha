package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
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
import com.example.model.AudioLabTelemetry
import com.example.model.OutputTarget
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
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveMultibandPhaseCard(
    adaptiveMultibandEnabled: Boolean,
    speakerPhaseAlignmentEnabled: Boolean,
    soundCheckEnabled: Boolean,
    speakerDspEnabled: Boolean,
    outputTarget: OutputTarget,
    isMasterBypassed: Boolean,
    abCompareHeld: Boolean,
    telemetry: AudioLabTelemetry,
    onToggleAdaptiveMultiband: () -> Unit,
    onToggleSpeakerPhaseAlignment: () -> Unit,
    onToggleSoundCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val multibandActive = adaptiveMultibandEnabled && !isMasterBypassed && !abCompareHeld
    val isSpeaker = outputTarget == OutputTarget.SPEAKER
    val phaseEffective = speakerPhaseAlignmentEnabled && isSpeaker && speakerDspEnabled && !isMasterBypassed && !abCompareHeld
    val soundCheckActive = soundCheckEnabled && !isMasterBypassed && !abCompareHeld

    val borderColor by animateColorAsState(
        targetValue = when {
            multibandActive || phaseEffective -> AppleMint.copy(alpha = 0.65f)
            soundCheckActive -> AppleBlue.copy(alpha = 0.55f)
            else -> AppleGlassBorder
        },
        animationSpec = tween(180),
        label = "adaptiveMultibandBorder"
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
        // Header + 3-Band Dynamic Tone Balancer Master Toggle
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
                        .background(if (multibandActive) AppleMintSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (multibandActive) AppleMint.copy(alpha = 0.6f) else AppleGlassBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = "Adaptive Multi-Band Mastering",
                        tint = if (multibandActive) AppleMint else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Adaptive Multi-Band & Phase Stage",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "LR4 220Hz / 3.8kHz Balancer • 1.45kHz+0.14ms • Sound Check",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (multibandActive) AppleMint else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (adaptiveMultibandEnabled) AppleMintSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (adaptiveMultibandEnabled) AppleMint else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleAdaptiveMultiband)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("adaptive_multiband_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (adaptiveMultibandEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (adaptiveMultibandEnabled) AppleMint else TextSecondary
                )
            }
        }

        // 1. 3-Band Dynamic Tone Balancer Meters (LR4 Crossovers: <220Hz, 220Hz–3.8kHz, >3.8kHz)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.62f))
                .border(
                    width = 1.dp,
                    color = if (multibandActive) AppleMint.copy(alpha = 0.45f) else AppleGlassBorder.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3-Band Dynamic Tone Balancer (LR4 24dB/oct)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "2.2:1 • 6ms / 85ms",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (multibandActive) AnalyzerCyan else TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MultibandReductionTile(
                    bandLabel = "Low",
                    rangeLabel = "< 220 Hz",
                    grDb = if (multibandActive) telemetry.multibandLowGrDb else 0f,
                    accentColor = SignalCoral,
                    isActive = multibandActive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("multiband_low_meter")
                )
                MultibandReductionTile(
                    bandLabel = "Mid Vocals",
                    rangeLabel = "220–3.8k",
                    grDb = if (multibandActive) telemetry.multibandMidGrDb else 0f,
                    accentColor = AppleMint,
                    isActive = multibandActive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("multiband_mid_meter")
                )
                MultibandReductionTile(
                    bandLabel = "High",
                    rangeLabel = "> 3.8 kHz",
                    grDb = if (multibandActive) telemetry.multibandHighGrDb else 0f,
                    accentColor = AnalyzerCyan,
                    isActive = multibandActive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("multiband_high_meter")
                )
            }
        }

        // 2. Earpiece-to-Main-Driver Phase Alignment (1.4 kHz Q = 0.707 Allpass)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (phaseEffective) AnalyzerCyanSoft.copy(alpha = 0.25f) else StudioBackground.copy(alpha = 0.58f)
                )
                .border(
                    width = 1.dp,
                    color = if (phaseEffective) AnalyzerCyan.copy(alpha = 0.58f) else AppleGlassBorder.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onToggleSpeakerPhaseAlignment)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("speaker_phase_alignment_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SurroundSound,
                    contentDescription = "Earpiece to Main Driver Phase Alignment",
                    tint = if (phaseEffective) AnalyzerCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Earpiece-to-Main Phase Alignment",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !isSpeaker -> "Allpass 1.45kHz (Q=0.707) + 0.14ms >2.2kHz • Auto-bypassed on ${outputTarget.label}"
                            !speakerDspEnabled -> "Enable Phone Speaker DSP to engage 1.45 kHz + 0.14ms alignment"
                            speakerPhaseAlignmentEnabled -> "Allpass 1.45kHz (Q=0.707) + 0.14ms (>2.2kHz) • Locks center vocals"
                            else -> "1.45 kHz + 0.14ms alignment disabled"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (phaseEffective) AnalyzerCyan else TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = speakerPhaseAlignmentEnabled,
                onCheckedChange = { onToggleSpeakerPhaseAlignment() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioBackground,
                    checkedTrackColor = AnalyzerCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                )
            )
        }

        // 3. Apple Music "Sound Check" Dynamic Crest Controller (-16 LUFS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (soundCheckActive) AppleBlueSoft.copy(alpha = 0.25f) else StudioBackground.copy(alpha = 0.58f)
                )
                .border(
                    width = 1.dp,
                    color = if (soundCheckActive) AppleBlue.copy(alpha = 0.6f) else AppleGlassBorder.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(onClick = onToggleSoundCheck)
                    .testTag("adaptive_sound_check_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Apple Music Sound Check Dynamic Crest Controller",
                        tint = if (soundCheckActive) AppleBlue else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Apple Music Sound Check (-16 LUFS)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (soundCheckEnabled) {
                                "Dynamic Crest Controller • Preserves punch without squashing"
                            } else {
                                "Real-time LUFS + Crest normalization off"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (soundCheckActive) AppleBlue else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = soundCheckEnabled,
                    onCheckedChange = { onToggleSoundCheck() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioBackground,
                        checkedTrackColor = AppleBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = StudioSurfaceElevated
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundCheckTelemetryChip(
                    label = "Input LUFS",
                    value = String.format(Locale.US, "%.1f", telemetry.inputLufs),
                    accent = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                SoundCheckTelemetryChip(
                    label = "Trim",
                    value = String.format(Locale.US, "%+.1f dB", if (soundCheckActive) telemetry.soundCheckTrimDb else 0f),
                    accent = if (soundCheckActive) AppleBlue else TextMuted,
                    modifier = Modifier.weight(1f)
                )
                SoundCheckTelemetryChip(
                    label = "Crest Punch",
                    value = String.format(Locale.US, "%.1f dB", telemetry.soundCheckCrestDb),
                    accent = if (soundCheckActive && telemetry.soundCheckCrestDb > 3.0f) RouteEmerald else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Guardrail Compliance Strip: De-Harsh & +3.0dB Cap Respected
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdaptiveComplianceChip(
                text = "4.5k (-3.0dB) & 7.0k (-3.5dB) De-Harsh Preserved",
                active = multibandActive || phaseEffective,
                color = WarmAmber,
                bg = WarmAmber.copy(alpha = 0.16f)
            )
            AdaptiveComplianceChip(
                text = if (telemetry.trebleGainStagedCapActive && !isMasterBypassed && !abCompareHeld) {
                    String.format(Locale.US, ">4kHz Cap Active: +%.1fdB ≤ +3.0dB", telemetry.trebleCappedBoostDb)
                } else {
                    ">4kHz Treble Cap ≤ +3.0 dB Respected"
                },
                active = true,
                color = RouteEmerald,
                bg = RouteEmeraldSoft
            )
        }
    }
}

@Composable
private fun MultibandReductionTile(
    bandLabel: String,
    rangeLabel: String,
    grDb: Float,
    accentColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val grAbs = abs(grDb).coerceIn(0f, 12f)
    val fraction by animateFloatAsState(
        targetValue = if (isActive) (grAbs / 12f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(55),
        label = "multibandGr_$bandLabel"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurfaceElevated.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = if (isActive && grAbs > 0.2f) accentColor.copy(alpha = 0.55f) else StudioBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = bandLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) TextPrimary else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = rangeLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = TextSecondary,
            maxLines = 1
        )
        Text(
            text = if (isActive && grAbs > 0.05f) {
                String.format(Locale.US, "-%.1f dB", grAbs)
            } else {
                "0.0 dB"
            },
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) accentColor else TextMuted,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(StudioBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) accentColor else TextMuted.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun SoundCheckTelemetryChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(StudioSurfaceElevated.copy(alpha = 0.5f))
            .border(1.dp, StudioBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = TextMuted,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun AdaptiveComplianceChip(
    text: String,
    active: Boolean,
    color: Color,
    bg: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) bg else AppleGlassTile)
            .border(
                width = 1.dp,
                color = if (active) color.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = if (active) color else TextMuted,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (active) color else TextMuted
        )
    }
}
