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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.StudioHiResSpecs
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
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioHiResMasteringCard(
    oversampledEqEnabled: Boolean,
    harmonicSheenPercent: Float,
    noiseFloorGateEnabled: Boolean,
    isMasterBypassed: Boolean,
    abCompareHeld: Boolean,
    telemetry: AudioLabTelemetry,
    onToggleOversampledEq: () -> Unit,
    onHarmonicSheenChange: (Float) -> Unit,
    onToggleNoiseFloorGate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = !isMasterBypassed && !abCompareHeld
    val osActive = active && oversampledEqEnabled
    val sheenClamped = harmonicSheenPercent.coerceIn(0f, 1f)
    val sheenPctInt = (sheenClamped * 100f).toInt()
    val sheenActive = active && sheenClamped > 0.005f
    val gateActive = active && noiseFloorGateEnabled

    val borderColor by animateColorAsState(
        targetValue = when {
            abCompareHeld -> WarmAmber.copy(alpha = 0.72f)
            osActive || sheenActive || gateActive -> AppleMint.copy(alpha = 0.62f)
            else -> AppleGlassBorder
        },
        animationSpec = tween(180),
        label = "studioHiResBorder"
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
        // Header + 48 kHz Unprocessed Context Badge
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
                        .background(if (active) AppleMintSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (active) AppleMint.copy(alpha = 0.6f) else AppleGlassBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = "Studio Hi-Res Mastering",
                        tint = if (active) AppleMint else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Studio Hi-Res Mastering",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            abCompareHeld -> "A/B Hold • Raw Original Comparison"
                            !active -> "Master Bypassed"
                            else -> "48 kHz Playback Buffer • Raw Unprocessed Mic • TPDF Dither"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = when {
                            abCompareHeld -> WarmAmber
                            active -> AppleMint
                            else -> TextMuted
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) AppleMintSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (active) AppleMint.copy(alpha = 0.65f) else StudioBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
                    .testTag("hires_header_badge")
            ) {
                Text(
                    text = "${telemetry.studioSampleRateHz / 1000} kHz • RAW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (active) AppleMint else TextSecondary
                )
            }
        }

        // Toggle 1: 2x Oversampled Upper-Band EQ (Anti-Cramping @ 96 kHz)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.64f))
                .border(
                    width = 1.dp,
                    color = if (osActive) AnalyzerCyan.copy(alpha = 0.55f) else AppleGlassBorder.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "2x Oversampled Upper EQ (96 kHz)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (osActive) {
                        "Nyquist De-Cramping active on 8kHz, 16kHz & 4.7kHz High Shelf"
                    } else {
                        "Standard 1x 48 kHz bilinear shelves"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (osActive) AnalyzerCyan else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (oversampledEqEnabled) AnalyzerCyanSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (oversampledEqEnabled) AnalyzerCyan else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleOversampledEq)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("oversampled_eq_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (oversampledEqEnabled) "2x ON" else "1x OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (oversampledEqEnabled) AnalyzerCyan else TextSecondary
                )
            }
        }

        // Control 2: Studio Clarity / "Analog Harmonic Sheen" & Stereo Focus (0% - 100%)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.64f))
                .border(
                    width = 1.dp,
                    color = if (sheenActive) WarmAmber.copy(alpha = 0.58f) else AppleGlassBorder.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Analog Harmonic Sheen and Stereo Focus",
                        tint = if (sheenActive) WarmAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Analog Harmonic Sheen & Focus",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "2kHz–6kHz pure 2nd-order even harmonics (<0.4% THD) • M/S detail",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (sheenActive) WarmAmber else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sheenActive) WarmAmber.copy(alpha = 0.18f) else StudioSurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (sheenActive) WarmAmber.copy(alpha = 0.68f) else StudioBorder,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("harmonic_sheen_thd_badge")
                ) {
                    val displayedThd = if (sheenActive) {
                        telemetry.harmonicSheenThdPct.takeIf { it > 0.005f }
                            ?: (sheenClamped * 0.28f).coerceAtMost(StudioHiResSpecs.HARMONIC_SHEEN_MAX_THD_PCT)
                    } else {
                        0f
                    }
                    Text(
                        text = String.format(Locale.US, "%d%% • %.2f%% THD", sheenPctInt, displayedThd),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (sheenActive) WarmAmber else TextSecondary
                    )
                }
            }

            Slider(
                value = sheenClamped,
                onValueChange = onHarmonicSheenChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = WarmAmber,
                    activeTrackColor = WarmAmber,
                    inactiveTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("harmonic_sheen_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0% Pure Linear",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted
                )
                Text(
                    text = "25% Mastering Default",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextSecondary
                )
                Text(
                    text = "100% Air & Focus",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = WarmAmber
                )
            }
        }

        // Toggle 3: Zero-Noise Floor Gate (-75 dBFS) + TPDF Dither
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.64f))
                .border(
                    width = 1.dp,
                    color = if (gateActive) RouteEmerald.copy(alpha = 0.55f) else AppleGlassBorder.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeOff,
                    contentDescription = "Zero-Noise Floor Gate and TPDF Dither",
                    tint = if (gateActive) RouteEmerald else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Zero-Noise Floor Gate (-75 dBFS)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusText = when {
                        !gateActive -> "Gate bypassed • Raw noise floor passed"
                        telemetry.noiseFloorGateMuted -> "AUTO-MUTED (< -75 dBFS) • Zero Hiss & Zero Dither"
                        else -> "OPEN (Live Audio) • 24-bit/16-bit TPDF Dithering Active"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (gateActive) RouteEmerald else TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("noise_floor_gate_status")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (noiseFloorGateEnabled) RouteEmeraldSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (noiseFloorGateEnabled) RouteEmerald else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleNoiseFloorGate)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("noise_floor_gate_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (noiseFloorGateEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (noiseFloorGateEnabled) RouteEmerald else TextSecondary
                )
            }
        }

        // Spec Summary Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HiResSpecChip(
                text = "48 kHz 4x Buffer Stability",
                isActive = active,
                accentColor = AppleMint,
                bgColor = AppleMintSoft
            )
            HiResSpecChip(
                text = "AEC / NS / AGC Disabled",
                isActive = active,
                accentColor = AppleBlue,
                bgColor = AppleBlueSoft
            )
            HiResSpecChip(
                text = if (osActive) "96 kHz De-Cramped Treble" else "48 kHz Standard Shelves",
                isActive = osActive,
                accentColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            HiResSpecChip(
                text = "TPDF Bit-Depth Dither",
                isActive = gateActive,
                accentColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
        }
    }
}

@Composable
private fun HiResSpecChip(
    text: String,
    isActive: Boolean,
    accentColor: Color,
    bgColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) bgColor else AppleGlassTile)
            .border(
                width = 1.dp,
                color = if (isActive) accentColor.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (isActive) accentColor else TextMuted
        )
    }
}
