package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLabTelemetry
import com.example.model.LoudnessMaximizerSpecs
import com.example.model.OutputTarget
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoudnessMaximizerCard(
    loudnessMaximizerEnabled: Boolean,
    boostPercent: Float,
    outputTarget: OutputTarget,
    isMasterBypassed: Boolean,
    telemetry: AudioLabTelemetry,
    onToggleEnabled: () -> Unit,
    onBoostPercentChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = loudnessMaximizerEnabled && !isMasterBypassed
    val speakerSubHpfActive = active && outputTarget == OutputTarget.SPEAKER
    val clampedBoost = boostPercent.coerceIn(0f, 1f)
    val boostPctInt = (clampedBoost * 100f).roundToInt()
    val makeupDb = clampedBoost * LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB

    val borderColor by animateColorAsState(
        targetValue = when {
            active && telemetry.loudnessAntiClipActive -> WarmAmber.copy(alpha = 0.85f)
            active -> SignalCoral.copy(alpha = 0.7f)
            else -> AppleGlassBorder
        },
        animationSpec = tween(200),
        label = "loudnessCardBorder"
    )

    val pulseTransition = rememberInfiniteTransition(label = "antiClipPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "antiClipAlpha"
    )

    val antiClipLedColor by animateColorAsState(
        targetValue = when {
            !active -> TextMuted
            telemetry.loudnessAntiClipActive -> WarmAmber
            else -> RouteEmerald
        },
        animationSpec = tween(140),
        label = "antiClipLedColor"
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
        // Header: iPhone Loudness Maximizer + Master Toggle
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
                        .background(if (active) SignalCoralSoft else StudioSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (active) SignalCoral.copy(alpha = 0.65f) else StudioBorder,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = "iPhone Loudness Maximizer",
                        tint = if (active) SignalCoral else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "iPhone Loudness Maximizer",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = if (active) {
                            String.format(Locale.US, "Safe Volume Boost +%d%% (+%.1f dB)", boostPctInt, makeupDb)
                        } else {
                            "Final output loudness booster & brickwall limiter"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) SignalCoral else TextMuted
                    )
                }
            }

            Switch(
                checked = loudnessMaximizerEnabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioBackground,
                    checkedTrackColor = SignalCoral,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier.testTag("loudness_maximizer_toggle")
            )
        }

        // iPhone Volume Boost Slider (0%..+100% -> 0..+9 dB clean makeup gain)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground)
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "iPhone Volume Boost",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
                Text(
                    text = String.format(Locale.US, "+%d%%  (+%.1f dB)", boostPctInt, makeupDb),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = if (active) SignalCoral else TextSecondary
                )
            }

            Slider(
                value = clampedBoost,
                onValueChange = onBoostPercentChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = if (active) SignalCoral else TextSecondary,
                    activeTrackColor = if (active) SignalCoral else TextMuted,
                    inactiveTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loudness_boost_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0% (+0.0 dB)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted
                )
                Text(
                    text = String.format(Locale.US, "+50%% (+%.1f dB)", LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB * 0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted,
                    modifier = Modifier.clickable { onBoostPercentChange(0.50f) }
                )
                Text(
                    text = String.format(Locale.US, "+100%% (+%.1f dB)", LoudnessMaximizerSpecs.MAX_MAKEUP_BOOST_DB),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted
                )
            }
        }

        // Live Anti-Clip Protection Indicator Light + Gain Reduction Readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBackground)
                .border(
                    width = 1.dp,
                    color = when {
                        active && telemetry.loudnessAntiClipActive -> WarmAmber.copy(alpha = 0.7f)
                        active -> RouteEmerald.copy(alpha = 0.45f)
                        else -> StudioBorder
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("anti_clip_indicator"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .alpha(if (active && telemetry.loudnessAntiClipActive) pulseAlpha else 1f)
                        .clip(CircleShape)
                        .background(antiClipLedColor)
                        .border(
                            width = 1.dp,
                            color = antiClipLedColor.copy(alpha = 0.55f),
                            shape = CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Anti-Clip Protection",
                    tint = antiClipLedColor,
                    modifier = Modifier.size(15.dp)
                )
                Column {
                    Text(
                        text = when {
                            !active -> "Anti-Clip Protection: Off"
                            telemetry.loudnessAntiClipActive -> "Anti-Clip Limiting Peaks (-0.3 dB Ceiling)"
                            else -> "Anti-Clip Protection: Armed & Clean"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = when {
                            !active -> TextMuted
                            telemetry.loudnessAntiClipActive -> WarmAmber
                            else -> RouteEmerald
                        }
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "RMS Comp %.1f dB  •  Brickwall %.1f dB",
                            telemetry.loudnessRmsGrDb,
                            telemetry.loudnessBrickwallGrDb
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextSecondary
                    )
                }
            }
        }

        // DSP Stage Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MaximizerBadgeChip(
                text = if (speakerSubHpfActive) {
                    "115Hz 24dB/oct HPF • ACTIVE"
                } else {
                    "115Hz 24dB/oct HPF • Speaker Only"
                },
                highlighted = speakerSubHpfActive,
                accent = RouteEmerald,
                accentBg = RouteEmeraldSoft
            )
            MaximizerBadgeChip(
                text = "350Hz -2.5 • 1.5k +2 • 3k +3.5 • 8k +2.5 dB",
                highlighted = active,
                accent = AnalyzerCyan,
                accentBg = AnalyzerCyanSoft
            )
            MaximizerBadgeChip(
                text = "RMS -20dB 4:1 (15dB, 5/150ms)",
                highlighted = active,
                accent = SignalCoral,
                accentBg = SignalCoralSoft
            )
            MaximizerBadgeChip(
                text = "Brickwall -0.3dB 20:1 (0.5/40ms)",
                highlighted = active,
                accent = if (telemetry.loudnessAntiClipActive) WarmAmber else RouteEmerald,
                accentBg = if (telemetry.loudnessAntiClipActive) SignalCoralSoft else RouteEmeraldSoft
            )
        }
    }
}

@Composable
private fun MaximizerBadgeChip(
    text: String,
    highlighted: Boolean,
    accent: Color,
    accentBg: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (highlighted) accentBg else AppleGlassTile)
            .border(
                width = 1.dp,
                color = if (highlighted) accent.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (highlighted) accent else TextMuted
        )
    }
}
