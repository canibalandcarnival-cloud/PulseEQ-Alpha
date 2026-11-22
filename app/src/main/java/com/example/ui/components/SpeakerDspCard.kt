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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerDspCard(
    speakerDspEnabled: Boolean,
    virtualBassEnabled: Boolean,
    isBypassed: Boolean,
    onToggleSpeakerDsp: () -> Unit,
    onToggleVirtualBass: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = speakerDspEnabled && !isBypassed
    val borderColor by animateColorAsState(
        targetValue = if (active) RouteEmerald.copy(alpha = 0.65f) else StudioBorder,
        animationSpec = tween(200),
        label = "speakerDspBorder"
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
        // Header: iPhone Speaker DSP Master Toggle
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
                        .background(if (active) RouteEmeraldSoft else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (active) RouteEmerald.copy(alpha = 0.6f) else StudioBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SpeakerPhone,
                        contentDescription = "iPhone Speaker DSP",
                        tint = if (active) RouteEmerald else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "iPhone Speaker DSP",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = if (active) {
                            "110Hz 24dB/oct HPF • -6dB 12:1 Limiter (+3dB)"
                        } else {
                            "Micro-driver protection & clarity chain off"
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
                        color = if (speakerDspEnabled) RouteEmerald else StudioBorder,
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

        // Virtual Bass Harmonic Exciter Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBackground)
                .border(
                    width = 1.dp,
                    color = if (active && virtualBassEnabled) AnalyzerCyan.copy(alpha = 0.55f) else StudioBorder,
                    shape = RoundedCornerShape(10.dp)
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
                        text = "120Hz–250Hz harmonic exciter for deep perceived punch",
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

        // 4-Stage Telemetry Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DspStageChip(
                text = "110Hz HPF 24dB/oct",
                isActive = active,
                highlightColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
            DspStageChip(
                text = "Virtual Bass 120–250Hz",
                isActive = active && virtualBassEnabled,
                highlightColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            DspStageChip(
                text = "400Hz -3dB • 3.5kHz -1.5dB",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = "2kHz +2dB • 10kHz +2.5dB",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
            DspStageChip(
                text = "Limiter -6dB 12:1 (1ms/80ms) +3dB",
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
    highlightColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) bgColor else StudioSurfaceElevated)
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
