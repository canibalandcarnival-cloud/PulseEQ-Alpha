package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLabTelemetry
import com.example.model.OutputTarget
import com.example.model.SiliconDspSpecs
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
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiliconDspCard(
    siliconDspEnabled: Boolean,
    outputTarget: OutputTarget,
    isMasterBypassed: Boolean,
    abCompareHeld: Boolean,
    telemetry: AudioLabTelemetry,
    trebleSmoothnessPercent: Float = 0.35f,
    onToggleSiliconDsp: () -> Unit,
    onTrebleSmoothnessChange: (Float) -> Unit = {},
    onAbCompareHeld: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { onAbCompareHeld(false) }
    }

    val active = siliconDspEnabled && !isMasterBypassed && !abCompareHeld
    val isSpeaker = outputTarget == OutputTarget.SPEAKER

    val borderColor by animateColorAsState(
        targetValue = when {
            abCompareHeld -> WarmAmber.copy(alpha = 0.72f)
            active -> AnalyzerCyan.copy(alpha = 0.68f)
            else -> AppleGlassBorder
        },
        animationSpec = tween(180),
        label = "siliconDspBorder"
    )

    val excursionFraction by animateFloatAsState(
        targetValue = if (active) (telemetry.siliconExcursionPct / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(55),
        label = "siliconExcursion"
    )
    val tempFraction by animateFloatAsState(
        targetValue = ((telemetry.siliconCoilTempC - SiliconDspSpecs.AMBIENT_TEMP_C.toFloat()) /
            (SiliconDspSpecs.MAX_TEMP_C.toFloat() - SiliconDspSpecs.AMBIENT_TEMP_C.toFloat())).coerceIn(0f, 1f),
        animationSpec = tween(75),
        label = "siliconTemp"
    )
    val railFraction by animateFloatAsState(
        targetValue = if (active) {
            ((telemetry.siliconClassHRailV - SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat()) /
                (SiliconDspSpecs.CLASS_H_MAX_VOLTS.toFloat() - SiliconDspSpecs.CLASS_H_MIN_VOLTS.toFloat())).coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = tween(55),
        label = "siliconRail"
    )
    val tpGrAbs = abs(telemetry.siliconTruePeakGrDb).coerceIn(0f, 12f)
    val tpFraction by animateFloatAsState(
        targetValue = if (active) (tpGrAbs / 12f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(55),
        label = "siliconTruePeak"
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
        // Header: Apple / Cirrus Logic Silicon DSP Core + Master Toggle
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
                        .background(if (active) AnalyzerCyanSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (active) AnalyzerCyan.copy(alpha = 0.6f) else AppleGlassBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "Apple / Cirrus Logic Silicon DSP",
                        tint = if (active) AnalyzerCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Apple / Cirrus Logic Silicon DSP",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            abCompareHeld -> "A/B Hold Active • Loudness-Matched Original Bypass"
                            !active -> "CS35L 48 kHz 64-Bit Silicon Post-EQ Core bypassed"
                            isSpeaker -> "64-Bit Double • 5ms Lookahead • CS35L Excursion/Thermal • -0.2 dBTP"
                            outputTarget == OutputTarget.HEADPHONES -> "64-Bit Double • +5.5 dB 3.5mm DAC • 24Hz SVF • 4x -0.2 dBTP"
                            else -> "64-Bit Double • 5ms Lookahead • 24Hz SVF • Class-H • -0.2 dBTP"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = when {
                            abCompareHeld -> WarmAmber
                            active -> AnalyzerCyan
                            else -> TextMuted
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (siliconDspEnabled) AnalyzerCyanSoft else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (siliconDspEnabled) AnalyzerCyan else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleSiliconDsp)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("silicon_dsp_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (siliconDspEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (siliconDspEnabled) AnalyzerCyan else TextSecondary
                )
            }
        }

        // Loudness-Matched Hold-to-Compare (A/B) Press-and-Hold Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (abCompareHeld) WarmAmber.copy(alpha = 0.22f) else StudioBackground.copy(alpha = 0.62f)
                )
                .border(
                    width = 1.dp,
                    color = if (abCompareHeld) WarmAmber else AppleGlassBorder.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp)
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
                .testTag("silicon_ab_compare_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CompareArrows,
                    contentDescription = "Hold to Compare A/B",
                    tint = if (abCompareHeld) WarmAmber else AppleBlue,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = if (abCompareHeld) "Auditioning A: Original (Loudness Matched)" else "Hold to Compare (A/B)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (abCompareHeld) WarmAmber else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Press & hold to bypass EQ + Silicon Core at matched perceived LUFS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = if (abCompareHeld) "A: ORIG" else "B: DSP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (abCompareHeld) WarmAmber else RouteEmerald
            )
        }

        // 4 Live ~60fps Hardware Telemetry Gauges (2x2 Grid)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val svfLabel = if (isSpeaker) {
                    "${telemetry.siliconSvfCutoffHz.toInt().coerceIn(68, 122)}Hz SVF"
                } else {
                    "24Hz Fixed"
                }
                SiliconTelemetryGaugeTile(
                    title = "Membrane Excursion",
                    valueText = String.format(Locale.US, "%.0f%% Xmax", if (active) telemetry.siliconExcursionPct else 0f),
                    subBadge = svfLabel,
                    fraction = excursionFraction,
                    icon = Icons.Rounded.GraphicEq,
                    accentColor = if (telemetry.siliconExcursionPct > 80f) SignalCoral else AnalyzerCyan,
                    isActive = active,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("silicon_gauge_excursion")
                )

                val coilTemp = telemetry.siliconCoilTempC.coerceIn(25f, 95f)
                val tempAccent = when {
                    coilTemp >= 72f -> SignalCoral
                    coilTemp >= 52f -> WarmAmber
                    else -> RouteEmerald
                }
                SiliconTelemetryGaugeTile(
                    title = "Voice-Coil Temp",
                    valueText = String.format(Locale.US, "%.1f°C", coilTemp),
                    subBadge = "τ 1.8s / 18s",
                    fraction = tempFraction,
                    icon = Icons.Rounded.Thermostat,
                    accentColor = tempAccent,
                    isActive = active,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("silicon_gauge_temp")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val railVolts = if (active) telemetry.siliconClassHRailV.coerceIn(3.7f, 11.5f) else 3.7f
                SiliconTelemetryGaugeTile(
                    title = "Adaptive Class-H Rail",
                    valueText = String.format(Locale.US, "%.2f V", railVolts),
                    subBadge = "3.7V–11.5V",
                    fraction = railFraction,
                    icon = Icons.Rounded.Bolt,
                    accentColor = AppleBlue,
                    isActive = active,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("silicon_gauge_rail")
                )

                SiliconTelemetryGaugeTile(
                    title = "True-Peak Reduction",
                    valueText = if (active && tpGrAbs > 0.05f) {
                        String.format(Locale.US, "-%.1f dB", tpGrAbs)
                    } else {
                        "0.0 dB"
                    },
                    subBadge = "-0.2 dBTP",
                    fraction = tpFraction,
                    icon = Icons.Rounded.Shield,
                    accentColor = if (tpGrAbs > 0.25f) SignalCoral else RouteEmerald,
                    isActive = active,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("silicon_gauge_truepeak")
                )
            }
        }

        // iPhone Warmth / Treble Smoothness (De-Harsh) Slider
        val smoothnessClamped = trebleSmoothnessPercent.coerceIn(0f, 1f)
        val smoothnessPctInt = (smoothnessClamped * 100f).toInt()
        val warmBodyGain = smoothnessClamped * 2.0f
        val deHarshCut = smoothnessClamped * 4.5f
        val smoothnessActive = !isMasterBypassed && !abCompareHeld && smoothnessClamped > 0.005f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.65f))
                .border(
                    width = 1.dp,
                    color = if (smoothnessActive) WarmAmber.copy(alpha = 0.62f) else AppleGlassBorder.copy(alpha = 0.65f),
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
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "iPhone Warmth and Treble Smoothness",
                        tint = if (smoothnessActive) WarmAmber else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "iPhone Warmth / Treble Smoothness",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "+%.1fdB @ 160Hz • -%.1fdB De-Harsh (3.5–10kHz)",
                                warmBodyGain,
                                deHarshCut
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (smoothnessActive) WarmAmber else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (smoothnessActive) WarmAmber.copy(alpha = 0.18f) else StudioSurfaceElevated
                        )
                        .border(
                            width = 1.dp,
                            color = if (smoothnessActive) WarmAmber.copy(alpha = 0.68f) else StudioBorder,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("treble_smoothness_badge")
                ) {
                    Text(
                        text = "$smoothnessPctInt%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (smoothnessActive) WarmAmber else TextSecondary
                    )
                }
            }

            Slider(
                value = smoothnessClamped,
                onValueChange = onTrebleSmoothnessChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = WarmAmber,
                    activeTrackColor = WarmAmber,
                    inactiveTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("treble_smoothness_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0% Natural",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted
                )
                Text(
                    text = if (telemetry.trebleGainStagedCapActive && !isMasterBypassed && !abCompareHeld) {
                        String.format(
                            Locale.US,
                            "HF Cap Active: +%.1f dB (≤ +3.0 dB)",
                            telemetry.trebleCappedBoostDb
                        )
                    } else {
                        "Gain-Staged >4kHz ≤ +3.0 dB"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (telemetry.trebleGainStagedCapActive && !isMasterBypassed && !abCompareHeld) {
                        RouteEmerald
                    } else {
                        TextSecondary
                    },
                    modifier = Modifier.testTag("treble_cap_status_badge")
                )
                Text(
                    text = "100% Silky Warm",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = WarmAmber
                )
            }
        }

        // 5-Stage Hardware Pipeline Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SiliconStageChip(
                text = "1. 5ms Lookahead (240smpl)",
                isActive = active,
                highlightColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            SiliconStageChip(
                text = if (isSpeaker) "2. CS35L f₀=92Hz SVF 86–120Hz" else "2. CS35L Thermal • 24Hz SVF",
                isActive = active,
                highlightColor = RouteEmerald,
                bgColor = RouteEmeraldSoft
            )
            SiliconStageChip(
                text = "3. Class-H + 4x tanh (-3dBFS)",
                isActive = active,
                highlightColor = AppleBlue,
                bgColor = AppleBlueSoft
            )
            SiliconStageChip(
                text = "4. 19.2kHz LPF + Gain-Staged Air",
                isActive = active,
                highlightColor = WarmAmber,
                bgColor = WarmAmber.copy(alpha = 0.18f)
            )
            SiliconStageChip(
                text = "5. 4x Catmull-Rom -0.2 dBTP",
                isActive = active,
                highlightColor = SignalCoral,
                bgColor = SignalCoralSoft
            )
        }
    }
}

@Composable
private fun SiliconTelemetryGaugeTile(
    title: String,
    valueText: String,
    subBadge: String,
    fraction: Float,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBackground.copy(alpha = 0.68f))
            .border(
                width = 1.dp,
                color = if (isActive) accentColor.copy(alpha = 0.42f) else StudioBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) accentColor else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subBadge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isActive) accentColor else TextMuted,
                maxLines = 1
            )
        }

        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) TextPrimary else TextMuted,
            maxLines = 1
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(StudioSurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) accentColor else TextMuted.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun SiliconStageChip(
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
