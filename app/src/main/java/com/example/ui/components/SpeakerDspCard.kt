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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerDspCard(
    speakerDspEnabled: Boolean,
    virtualBassEnabled: Boolean,
    landscapeStereoStage: Boolean = false,
    isBypassed: Boolean,
    onToggleSpeakerDsp: () -> Unit,
    onToggleVirtualBass: () -> Unit,
    onToggleLandscapeStereo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val active = speakerDspEnabled && !isBypassed
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
        // Header: iPhone 18 Pro Max Speaker DSP Master Toggle
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
                        text = if (active) {
                            "95Hz 24dB/oct HPF • RMS -18dB 3.5:1 (+5.5dB) → -0.3dB"
                        } else {
                            "Flagship acoustic enclosure tuning & clarity chain off"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) RouteEmerald else TextMuted
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

        // Virtual Bass Harmonic Exciter Toggle Row (110Hz - 240Hz warm 2nd/3rd harmonics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    contentDescription = "Psychoacoustic Virtual Bass",
                    tint = if (active && virtualBassEnabled) AnalyzerCyan else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Psychoacoustic Virtual Bass",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "110Hz–240Hz warm 2nd/3rd harmonic exciter without cone rattle",
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

        // Landscape Stereo Stage (0.18ms -20% Phase-Inverted Acoustic Crosstalk Cancellation)
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    contentDescription = "Landscape Stereo Stage",
                    tint = if (active && landscapeStereoStage) AppleBlue else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Landscape Stereo Stage",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "-20% phase-inverted crossfeed delayed by 0.18ms (>150Hz)",
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

        // Flagship Telemetry Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DspStageChip(
                text = "95Hz HPF 24dB/oct",
                isActive = active,
                highlightColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
            DspStageChip(
                text = "Virtual Bass 110–240Hz",
                isActive = active && virtualBassEnabled,
                highlightColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            DspStageChip(
                text = "380Hz -3dB Boxiness",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = "1.8k +2 • 3.2k +2.5 • 9.5k +3dB",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = "Landscape -20% 0.18ms XTC",
                isActive = active && landscapeStereoStage,
                highlightColor = AppleBlue,
                bgColor = AppleBlueSoft
            )
            DspStageChip(
                text = "RMS -18dB +5.5dB → -0.3dB",
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
