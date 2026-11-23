package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProMaxHardwareRefinementSpecs
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputVolumeContourCard(
    masterVolumePercent: Float,
    isBypassed: Boolean,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val vol = masterVolumePercent.coerceIn(0f, 1f)
    val volPctInt = (vol * 100f).roundToInt()

    val isLowContour = !isBypassed && vol < ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD
    val isHighContour = !isBypassed && vol > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD

    val accentColor = when {
        isBypassed -> TextMuted
        isHighContour -> SignalCoral
        isLowContour -> AnalyzerCyan
        else -> RouteEmerald
    }

    val softBg = when {
        isBypassed -> AppleGlassTile
        isHighContour -> SignalCoralSoft
        isLowContour -> AnalyzerCyanSoft
        else -> RouteEmeraldSoft
    }

    val borderColor by animateColorAsState(
        targetValue = if (isLowContour || isHighContour) accentColor.copy(alpha = 0.65f) else AppleGlassBorder,
        animationSpec = tween(200),
        label = "volumeContourBorder"
    )

    val lowFactor = if (vol < ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD) {
        ((ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD - vol) / ProMaxHardwareRefinementSpecs.LOW_VOL_THRESHOLD).coerceIn(0f, 1f)
    } else {
        0f
    }
    val highFactor = if (vol > ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) {
        ((vol - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD) / (1.0f - ProMaxHardwareRefinementSpecs.HIGH_VOL_THRESHOLD)).coerceIn(0f, 1f)
    } else {
        0f
    }

    val contourHeadline = when {
        isBypassed -> "Master Bypassed"
        isLowContour -> "Low-Volume Contour (+${String.format("%.1f", 3.0f * lowFactor)}dB @ 115Hz)"
        isHighContour -> "High-Vol Projection + 5–9kHz De-Harsh Guard"
        else -> "Linear Reference Contour (40–80%)"
    }

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
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(softBg)
                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Master Output Volume & iOS Contour",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Master Output & iOS Volume Contour",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contourHeadline,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(softBg)
                    .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("master_volume_percent_badge")
            ) {
                Text(
                    text = "$volPctInt%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }

        Slider(
            value = vol,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioSurfaceElevated
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("master_volume_slider")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VolumeStageChip(
                label = if (isLowContour) {
                    "<40% Lift: 115Hz +${String.format("%.1f", 3.0f * lowFactor)}dB • 2.8k +${String.format("%.1f", 2.0f * lowFactor)}dB"
                } else {
                    "<40% Contour: +115Hz & +2.8kHz"
                },
                active = isLowContour,
                accentColor = AnalyzerCyan,
                bgColor = AnalyzerCyanSoft
            )
            VolumeStageChip(
                label = if (isHighContour) {
                    ">80% Project: Sub ${String.format("%.1f", -3.5f * highFactor)}dB • 1.5k +${String.format("%.1f", 2.0f * highFactor)}dB"
                } else {
                    ">80% Projection: Sub-Flatten + 1–2kHz +2dB"
                },
                active = isHighContour,
                accentColor = WarmAmber,
                bgColor = WarmAmber.copy(alpha = 0.18f)
            )
            VolumeStageChip(
                label = if (isHighContour) {
                    "5–9kHz De-Harsh Guard ACTIVE (6.5kHz ${String.format("%.1f", -3.5f * highFactor)}dB)"
                } else {
                    "5–9kHz High-Vol De-Harsh Guard (Armed >80%)"
                },
                active = isHighContour,
                accentColor = SignalCoral,
                bgColor = SignalCoralSoft,
                modifier = Modifier.testTag("high_vol_deharsh_badge")
            )
            if (!isLowContour && !isHighContour && !isBypassed) {
                VolumeStageChip(
                    label = "40–80% Sweet Spot • Zero Auto-Tilt",
                    active = true,
                    accentColor = AppleBlue,
                    bgColor = AppleBlueSoft
                )
            }
        }
    }
}

@Composable
private fun VolumeStageChip(
    label: String,
    active: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) bgColor else StudioBackground.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = if (active) accentColor.copy(alpha = 0.55f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (active) accentColor else TextMuted
        )
    }
}
