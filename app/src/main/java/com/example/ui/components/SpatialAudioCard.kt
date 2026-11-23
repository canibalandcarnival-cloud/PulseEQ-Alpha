package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OutputTarget
import com.example.model.SpatialMode
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
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpatialAudioCard(
    spatialMode: SpatialMode,
    outputTarget: OutputTarget,
    spatialDepth: Float,
    heightPresence: Float,
    vocalAnchor: Boolean,
    isBypassed: Boolean,
    onSelectMode: (SpatialMode) -> Unit,
    onDepthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onToggleVocalAnchor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = spatialMode != SpatialMode.OFF && !isBypassed
    val isSpeakerXtc = outputTarget == OutputTarget.SPEAKER
    val accentColor = when {
        !active -> TextSecondary
        isSpeakerXtc -> RouteEmerald
        spatialMode == SpatialMode.ATMOS_CINEMA -> SignalCoral
        else -> AnalyzerCyan
    }

    val borderColor by animateColorAsState(
        targetValue = if (active) accentColor.copy(alpha = 0.65f) else AppleGlassBorder,
        animationSpec = tween(220),
        label = "spatialBorder"
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
        // Header Row
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
                        .background(if (active) accentColor.copy(alpha = 0.16f) else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (active) accentColor.copy(alpha = 0.6f) else StudioBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.SurroundSound else Icons.Rounded.SpatialAudioOff,
                        contentDescription = "Spatial Audio Atmos",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Spatial Audio • Atmos",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !active -> "7.1.2 HRTF binaural & speaker XTC standby"
                            isSpeakerXtc -> "Speaker Mode: 0.18ms Acoustic Crosstalk Cancel"
                            spatialMode == SpatialMode.ATMOS_CINEMA -> "7.1.2 Wide HRTF • +45° Height • 25ms Studio IR"
                            else -> "7.1.2 Studio HRTF • Pinna Curve • 25ms IR"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) accentColor else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) accentColor.copy(alpha = 0.16f) else StudioSurfaceElevated)
                    .border(
                        1.dp,
                        if (active) accentColor.copy(alpha = 0.55f) else StudioBorder,
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isSpeakerXtc && active) "XTC 0.18ms" else spatialMode.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (active) accentColor else TextMuted
                )
            }
        }

        // Apple Music Style 3-Way Mode Selector: Off | Atmos Music | Atmos Cinema
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground)
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpatialMode.entries.forEach { mode ->
                val selected = mode == spatialMode
                val pillAccent = when (mode) {
                    SpatialMode.OFF -> TextSecondary
                    SpatialMode.ATMOS_MUSIC -> if (isSpeakerXtc) RouteEmerald else AnalyzerCyan
                    SpatialMode.ATMOS_CINEMA -> if (isSpeakerXtc) RouteEmerald else SignalCoral
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) pillAccent.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) pillAccent.copy(alpha = 0.7f) else Color.Transparent,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 6.dp, vertical = 7.dp)
                        .testTag("spatial_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) pillAccent else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = if (selected) pillAccent.copy(alpha = 0.85f) else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 7.1.2 Virtual Speaker Array / Speaker XTC Radar & Controls when active (or previewed)
        AnimatedVisibility(visible = spatialMode != SpatialMode.OFF) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpatialSoundstageRadar(
                    spatialMode = spatialMode,
                    isSpeakerXtc = isSpeakerXtc,
                    isBypassed = isBypassed,
                    spatialDepth = spatialDepth,
                    heightPresence = heightPresence,
                    vocalAnchor = vocalAnchor
                )

                // Spatial Depth Slider (0-100%)
                SpatialPercentSlider(
                    title = if (isSpeakerXtc) "XTC Soundstage Width" else "Spatial Depth",
                    percent = (spatialDepth * 100f).roundToInt(),
                    value = spatialDepth,
                    accentColor = accentColor,
                    enabled = !isBypassed,
                    onValueChange = onDepthChange,
                    testTag = "spatial_depth_slider"
                )

                // Height Presence Slider (0-100%)
                SpatialPercentSlider(
                    title = "Height Presence (2.5kHz+ / Pinna)",
                    percent = (heightPresence * 100f).roundToInt(),
                    value = heightPresence,
                    accentColor = if (isSpeakerXtc) RouteEmerald else AnalyzerCyan,
                    enabled = !isBypassed,
                    onValueChange = onHeightChange,
                    testTag = "height_presence_slider"
                )

                // Vocal Anchor Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBackground)
                        .border(
                            width = 1.dp,
                            color = if (active && vocalAnchor) WarmAmber.copy(alpha = 0.55f) else StudioBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onToggleVocalAnchor)
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
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Vocal Anchor",
                            tint = if (active && vocalAnchor) WarmAmber else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Vocal Anchor",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                color = TextPrimary
                            )
                            Text(
                                text = "Locks Center (0°) vocals at 100% clarity while surround expands",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = vocalAnchor,
                        onCheckedChange = { onToggleVocalAnchor() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioBackground,
                            checkedTrackColor = WarmAmber,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = StudioSurfaceElevated
                        ),
                        modifier = Modifier.testTag("vocal_anchor_toggle")
                    )
                }

                // Engine Telemetry Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpatialStageChip(
                        text = if (isSpeakerXtc) "0.18ms Crosstalk Cancel" else "7.1.2 HRTF Array",
                        isActive = active,
                        color = accentColor
                    )
                    SpatialStageChip(
                        text = "<120Hz Mono Bass Lock",
                        isActive = active,
                        color = WarmAmber
                    )
                    SpatialStageChip(
                        text = "Pinna 7.2kHz -3.5dB • 12kHz +3dB",
                        isActive = active && !isSpeakerXtc,
                        color = AnalyzerCyan
                    )
                    SpatialStageChip(
                        text = "25ms Studio IR (3.5kHz LPF • 10%)",
                        isActive = active,
                        color = RouteEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun SpatialSoundstageRadar(
    spatialMode: SpatialMode,
    isSpeakerXtc: Boolean,
    isBypassed: Boolean,
    spatialDepth: Float,
    heightPresence: Float,
    vocalAnchor: Boolean
) {
    val active = spatialMode != SpatialMode.OFF && !isBypassed
    val animDepth by animateFloatAsState(
        targetValue = if (active) spatialDepth else 0f,
        animationSpec = tween(240),
        label = "radarDepth"
    )
    val animHeight by animateFloatAsState(
        targetValue = if (active) heightPresence else 0f,
        animationSpec = tween(240),
        label = "radarHeight"
    )

    val surroundColor = if (isSpeakerXtc) RouteEmerald else AnalyzerCyan
    val heightColor = if (spatialMode == SpatialMode.ATMOS_CINEMA) SignalCoral else AnalyzerCyan

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBackground)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .testTag("spatial_soundstage_radar")
        ) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.54f
            val maxR = minOf(size.width * 0.42f, size.height * 0.44f)

            // Concentric studio field rings
            drawCircle(
                color = StudioBorder.copy(alpha = 0.7f),
                radius = maxR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )
            drawCircle(
                color = StudioBorder.copy(alpha = 0.45f),
                radius = maxR * 0.56f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            // Ambient 25ms Studio halo
            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            surroundColor.copy(alpha = 0.14f + 0.14f * animDepth),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR * (0.75f + 0.4f * animDepth)
                    ),
                    radius = maxR * 1.1f,
                    center = Offset(cx, cy)
                )
            }

            // Listener head marker at origin
            drawCircle(
                color = StudioSurfaceHighlight,
                radius = 9.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = if (active) TextPrimary else TextMuted,
                radius = 9.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Center Front (0°, 0°) Vocal + Mono Bass Anchor
            val centerColor = if (active && vocalAnchor) WarmAmber else TextSecondary
            val cPos = Offset(cx, cy - maxR * 0.78f)
            drawLine(
                color = centerColor.copy(alpha = 0.45f),
                start = Offset(cx, cy),
                end = cPos,
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(color = centerColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = cPos)
            drawCircle(color = centerColor, radius = 4.5.dp.toPx(), center = cPos)

            if (isSpeakerXtc) {
                // Acoustic Crosstalk Cancellation expanding arcs left & right
                val spread = maxR * (0.48f + 0.55f * animDepth)
                listOf(-1f, 1f).forEach { dir ->
                    val spkPos = Offset(cx + dir * spread, cy - maxR * 0.12f)
                    drawLine(
                        color = RouteEmerald.copy(alpha = 0.5f),
                        start = Offset(cx, cy),
                        end = spkPos,
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = RouteEmerald.copy(alpha = 0.22f),
                        radius = 12.dp.toPx(),
                        center = spkPos
                    )
                    drawCircle(
                        color = if (active) RouteEmerald else TextMuted,
                        radius = 5.dp.toPx(),
                        center = spkPos
                    )
                }
            } else {
                // Ls & Rs (±105° azimuth, 0° elevation)
                val surrRadius = maxR * (0.68f + 0.30f * animDepth)
                listOf(-105.0, 105.0).forEach { azDeg ->
                    val rad = Math.toRadians(azDeg)
                    val px = cx + (sin(rad) * surrRadius).toFloat()
                    val py = cy - (cos(rad) * surrRadius).toFloat()
                    drawLine(
                        color = surroundColor.copy(alpha = 0.38f + 0.3f * animDepth),
                        start = Offset(cx, cy),
                        end = Offset(px, py),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    drawCircle(
                        color = surroundColor.copy(alpha = 0.22f * animDepth),
                        radius = (8f + 6f * animDepth).dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = if (active) surroundColor else TextMuted,
                        radius = 4.5.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Ltf & Rtf (±65° azimuth, +45° elevation)
                val heightRadius = maxR * (0.50f + 0.28f * animHeight)
                listOf(-65.0, 65.0).forEach { azDeg ->
                    val rad = Math.toRadians(azDeg)
                    val px = cx + (sin(rad) * heightRadius).toFloat()
                    val py = cy - (cos(rad) * heightRadius).toFloat()
                    drawCircle(
                        color = heightColor.copy(alpha = 0.20f + 0.25f * animHeight),
                        radius = (7f + 7f * animHeight).dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = if (active) heightColor else TextMuted,
                        radius = 4.dp.toPx(),
                        center = Offset(px, py),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Corner legend labels inside radar box
        Text(
            text = if (isSpeakerXtc) "XTC WIDE" else "C 0° • Ls/Rs ±105°",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = TextMuted,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = if (isSpeakerXtc) "0.18ms INV" else "Ltf/Rtf ±65° +45°",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (active) accentTextColor(isSpeakerXtc, spatialMode) else TextMuted,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

private fun accentTextColor(isSpeakerXtc: Boolean, spatialMode: SpatialMode): Color = when {
    isSpeakerXtc -> RouteEmerald
    spatialMode == SpatialMode.ATMOS_CINEMA -> SignalCoral
    else -> AnalyzerCyan
}

@Composable
private fun SpatialPercentSlider(
    title: String,
    percent: Int,
    value: Float,
    accentColor: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = TextPrimary
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) accentColor else TextMuted
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor else TextSecondary,
                activeTrackColor = if (enabled) accentColor else TextMuted,
                inactiveTrackColor = StudioSurfaceHighlight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun SpatialStageChip(
    text: String,
    isActive: Boolean,
    color: Color
) {
    val bg = when {
        !isActive -> AppleGlassTile
        color == AnalyzerCyan -> AnalyzerCyanSoft
        color == RouteEmerald -> RouteEmeraldSoft
        color == SignalCoral -> SignalCoralSoft
        else -> color.copy(alpha = 0.16f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isActive) color.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = if (isActive) color else TextMuted
        )
    }
}
