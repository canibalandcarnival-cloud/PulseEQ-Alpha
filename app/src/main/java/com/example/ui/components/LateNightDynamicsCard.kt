package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NightIntensity
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmOrange
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LateNightDynamicsCard(
    lateNightEnabled: Boolean,
    isBypassed: Boolean,
    nightIntensity: NightIntensity,
    gainReductionDb: Float,
    onToggleLateNight: () -> Unit,
    onSelectIntensity: (NightIntensity) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = lateNightEnabled && !isBypassed

    val borderColor by animateColorAsState(
        targetValue = if (active) WarmAmber.copy(alpha = 0.75f) else AppleGlassBorder,
        animationSpec = tween(200),
        label = "lateNightBorder"
    )
    val toggleBg by animateColorAsState(
        targetValue = if (lateNightEnabled) WarmAmber.copy(alpha = 0.18f) else StudioSurfaceElevated,
        animationSpec = tween(200),
        label = "lateNightToggleBg"
    )

    // Smooth fast-attack / natural-release GR meter needle
    val targetGrMag = if (active) abs(gainReductionDb).coerceIn(0f, 18f) else 0f
    val animatedGrMag by animateFloatAsState(
        targetValue = targetGrMag,
        animationSpec = tween(durationMillis = if (targetGrMag > 0.1f) 70 else 180),
        label = "grMeter"
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
        // Header: Moon Icon Toggle + Compressor Specs
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
                        .background(if (active) WarmAmber.copy(alpha = 0.16f) else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (active) WarmAmber.copy(alpha = 0.6f) else StudioBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Late Night Mode",
                        tint = if (active) WarmAmber else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Late Night Mode",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "${nightIntensity.thresholdDb.toInt()}dB • 4:1 • 20dB knee • +${nightIntensity.makeupGainDb.toInt()}dB",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) WarmAmber else TextMuted
                    )
                }
            }

            // Dedicated Moon Toggle Button
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(toggleBg)
                    .border(
                        width = 1.dp,
                        color = if (lateNightEnabled) WarmAmber else StudioBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggleLateNight)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("late_night_toggle_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = if (lateNightEnabled) "Disable Late Night Mode" else "Enable Late Night Mode",
                    tint = if (lateNightEnabled) WarmAmber else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (lateNightEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (lateNightEnabled) WarmAmber else TextSecondary
                )
            }
        }

        // Real-Time Gain Reduction Meter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBackground)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (active && animatedGrMag > 0.2f) WarmAmber else TextMuted)
                    )
                    Text(
                        text = "GAIN REDUCTION",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextSecondary
                    )
                }

                Text(
                    text = if (active) {
                        if (animatedGrMag < 0.05f) "0.0 dB" else String.format(Locale.US, "-%.1f dB", animatedGrMag)
                    } else {
                        "STANDBY"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active && animatedGrMag > 0.2f) WarmAmber else TextMuted,
                    modifier = Modifier.testTag("gain_reduction_readout")
                )
            }

            // Segmented LED Attenuation Bar (0 dB right-to-left or left-to-right 0..-18 dB)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .testTag("gain_reduction_meter")
            ) {
                val w = size.width
                val h = size.height
                val segments = 24
                val gap = 2.dp.toPx()
                val segW = ((w - gap * (segments - 1)) / segments).coerceAtLeast(2f)
                val activeRatio = (animatedGrMag / 18f).coerceIn(0f, 1f)
                val activeSegments = (activeRatio * segments).roundToInt()

                for (i in 0 until segments) {
                    // Grow from right (0 dB) toward left (-18 dB) like classic hardware GR meters
                    val segIndexFromRight = segments - 1 - i
                    val isLit = active && segIndexFromRight < activeSegments
                    val frac = segIndexFromRight / segments.toFloat()
                    val litColor = when {
                        frac > 0.66f -> SignalCoral
                        frac > 0.33f -> WarmOrange
                        else -> WarmAmber
                    }

                    drawRoundRect(
                        color = if (isLit) litColor else GridLineSubtle,
                        topLeft = Offset(i * (segW + gap), 1.dp.toPx()),
                        size = Size(segW, h - 2.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("-18", "-12", "-6", "-3", "0 dB").forEach { tick ->
                    Text(
                        text = tick,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = TextMuted
                    )
                }
            }
        }

        // Night Intensity Slider (Low / Medium / High)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Night Intensity",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    color = TextPrimary
                )
                Text(
                    text = nightIntensity.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) WarmAmber else TextSecondary
                )
            }

            Slider(
                value = nightIntensity.ordinal.toFloat(),
                onValueChange = { raw ->
                    val idx = raw.roundToInt().coerceIn(0, NightIntensity.entries.lastIndex)
                    onSelectIntensity(NightIntensity.entries[idx])
                },
                valueRange = 0f..2f,
                steps = 1,
                colors = SliderDefaults.colors(
                    thumbColor = if (lateNightEnabled) WarmAmber else TextSecondary,
                    activeTrackColor = if (lateNightEnabled) WarmAmber else TextMuted,
                    inactiveTrackColor = StudioSurfaceHighlight,
                    activeTickColor = StudioBackground,
                    inactiveTickColor = TextMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .testTag("night_intensity_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NightIntensity.entries.forEach { level ->
                    val selected = level == nightIntensity
                    Text(
                        text = "${level.label} (${level.thresholdDb.toInt()}dB / +${level.makeupGainDb.toInt()}dB)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (selected) (if (lateNightEnabled) WarmAmber else TextPrimary) else TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectIntensity(level) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
