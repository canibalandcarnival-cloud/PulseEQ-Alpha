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
import androidx.compose.ui.text.style.TextAlign
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
    vocalFocus: Float,
    isBypassed: Boolean,
    onSelectMode: (SpatialMode) -> Unit,
    onVocalFocusChange: (Float) -> Unit,
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
                        contentDescription = "Apple Spatial Audio Renderer Atmos",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Spatial Audio • Atmos ASAR",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !active -> "7.1.4 diffuse HRTF & 2nd-order speaker XTC standby"
                            isSpeakerXtc -> "18 Pro Max • 2nd-Order XTC + Dry Center Lock"
                            spatialMode == SpatialMode.ATMOS_CINEMA -> "7.1.4 HRTF • ±60°/+50° Height • 18ms Studio IR"
                            else -> "7.1.4 HRTF • Intimate Dry Vocal + Wide Stage"
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
                    text = if (isSpeakerXtc && active) "XTC 2ND-ORDER" else spatialMode.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (active) accentColor else TextMuted
                )
            }
        }

        // Apple Music / Apple TV Authentic 3-Profile Atmos Selector:
        // Off | Atmos Music (Intimate Vocal + Wide Stage) | Atmos Cinema (Maximum 3D Height & Surround)
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
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                            color = if (selected) pillAccent.copy(alpha = 0.88f) else TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ASAR Soundstage Radar, Vocal Focus Slider & Architecture Telemetry
        AnimatedVisibility(visible = spatialMode != SpatialMode.OFF) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpatialSoundstageRadar(
                    spatialMode = spatialMode,
                    isSpeakerXtc = isSpeakerXtc,
                    isBypassed = isBypassed,
                    vocalFocus = vocalFocus
                )

                // Vocal Focus Slider (0% - 100%)
                VocalFocusSliderRow(
                    vocalFocus = vocalFocus,
                    accentColor = WarmAmber,
                    enabled = !isBypassed,
                    onValueChange = onVocalFocusChange
                )

                // ASAR Hardware Telemetry Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpatialStageChip(
                        text = "<150Hz & 1–3.5kHz 100% Dry Lock",
                        isActive = active,
                        color = WarmAmber
                    )
                    if (isSpeakerXtc) {
                        SpatialStageChip(
                            text = "2nd-Order XTC (-26% @ 0.17ms • +12% @ 0.34ms)",
                            isActive = active,
                            color = RouteEmerald
                        )
                        SpatialStageChip(
                            text = "4.5kHz Head-Shadow LPF",
                            isActive = active,
                            color = AnalyzerCyan
                        )
                    } else {
                        SpatialStageChip(
                            text = "7.1.4 HRTF (±110° 0° • ±60° +50° >2.8kHz)",
                            isActive = active,
                            color = accentColor
                        )
                        SpatialStageChip(
                            text = "Pinna 6.8kHz -3dB • 11.8kHz +2.5dB",
                            isActive = active,
                            color = AnalyzerCyan
                        )
                        SpatialStageChip(
                            text = "18ms Studio IR (3.2kHz LPF • 8%)",
                            isActive = active,
                            color = RouteEmerald
                        )
                    }
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
    vocalFocus: Float
) {
    val active = spatialMode != SpatialMode.OFF && !isBypassed
    val targetDepth = spatialMode.defaultDepth
    val targetHeight = spatialMode.defaultHeight
    val animDepth by animateFloatAsState(
        targetValue = if (active) targetDepth else 0f,
        animationSpec = tween(240),
        label = "radarDepth"
    )
    val animHeight by animateFloatAsState(
        targetValue = if (active) targetHeight else 0f,
        animationSpec = tween(240),
        label = "radarHeight"
    )
    val animVocal by animateFloatAsState(
        targetValue = if (active) vocalFocus.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(200),
        label = "radarVocal"
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
                .height(108.dp)
                .testTag("spatial_soundstage_radar")
        ) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.56f
            val maxR = minOf(size.width * 0.42f, size.height * 0.44f)

            // Concentric diffuse studio soundfield rings
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

            // Ambient 18ms Studio early-reflection / XTC field halo
            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            surroundColor.copy(alpha = 0.14f + 0.14f * animDepth),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR * (0.78f + 0.36f * animDepth)
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

            // Center Front (0°, 0°) Dry Vocal (1-3.5kHz) & Sub-Bass (<150Hz) Anchor
            val centerColor = if (active) WarmAmber else TextSecondary
            val cPos = Offset(cx, cy - maxR * 0.78f)
            drawLine(
                color = centerColor.copy(alpha = 0.42f + 0.42f * animVocal),
                start = Offset(cx, cy),
                end = cPos,
                strokeWidth = (1.5f + 1.2f * animVocal).dp.toPx()
            )
            drawCircle(
                color = centerColor.copy(alpha = 0.18f + 0.28f * animVocal),
                radius = (8f + 6f * animVocal).dp.toPx(),
                center = cPos
            )
            drawCircle(color = centerColor, radius = 4.8.dp.toPx(), center = cPos)

            if (isSpeakerXtc) {
                // 2nd-Order Transaural XTC Primary (-26% @ 0.17ms) + Bounce (+12% @ 0.34ms)
                val primarySpread = maxR * (0.50f + 0.42f * animDepth)
                val bounceSpread = maxR * (0.72f + 0.45f * animDepth)
                listOf(-1f, 1f).forEach { dir ->
                    val spkPos = Offset(cx + dir * primarySpread, cy - maxR * 0.12f)
                    val bouncePos = Offset(cx + dir * bounceSpread, cy - maxR * 0.18f)
                    drawLine(
                        color = RouteEmerald.copy(alpha = 0.55f),
                        start = Offset(cx, cy),
                        end = spkPos,
                        strokeWidth = 1.6.dp.toPx()
                    )
                    drawCircle(
                        color = RouteEmerald.copy(alpha = 0.24f),
                        radius = 12.dp.toPx(),
                        center = spkPos
                    )
                    drawCircle(
                        color = if (active) RouteEmerald else TextMuted,
                        radius = 5.dp.toPx(),
                        center = spkPos
                    )
                    drawCircle(
                        color = if (active) AnalyzerCyan.copy(alpha = 0.75f) else TextMuted,
                        radius = 3.5.dp.toPx(),
                        center = bouncePos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            } else {
                // 7.1.4 Surround L / R (±110° azimuth, 0° elevation)
                val surrRadius = maxR * (0.68f + 0.30f * animDepth)
                listOf(-110.0, 110.0).forEach { azDeg ->
                    val rad = Math.toRadians(azDeg)
                    val px = cx + (sin(rad) * surrRadius).toFloat()
                    val py = cy - (cos(rad) * surrRadius).toFloat()
                    drawLine(
                        color = surroundColor.copy(alpha = 0.38f + 0.32f * animDepth),
                        start = Offset(cx, cy),
                        end = Offset(px, py),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = surroundColor.copy(alpha = 0.22f * animDepth),
                        radius = (8f + 6f * animDepth).dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = if (active) surroundColor else TextMuted,
                        radius = 4.6.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // 7.1.4 Top Height L / R (±60° azimuth, +50° elevation, 2.8kHz HPF)
                val heightRadius = maxR * (0.50f + 0.28f * animHeight)
                listOf(-60.0, 60.0).forEach { azDeg ->
                    val rad = Math.toRadians(azDeg)
                    val px = cx + (sin(rad) * heightRadius).toFloat()
                    val py = cy - (cos(rad) * heightRadius).toFloat()
                    drawCircle(
                        color = heightColor.copy(alpha = 0.20f + 0.28f * animHeight),
                        radius = (7f + 7f * animHeight).dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = if (active) heightColor else TextMuted,
                        radius = 4.2.dp.toPx(),
                        center = Offset(px, py),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Corner legend labels inside radar box
        Text(
            text = if (isSpeakerXtc) "XTC SIDE CHANNEL" else "C 0° • Ls/Rs ±110°",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = TextMuted,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = if (isSpeakerXtc) "0.17ms -26% • 0.34ms +12%" else "Ltf/Rtf ±60° +50°",
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
private fun VocalFocusSliderRow(
    vocalFocus: Float,
    accentColor: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    val percent = (vocalFocus.coerceIn(0f, 1f) * 100f).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBackground)
            .border(
                width = 1.dp,
                color = if (enabled && percent > 0) accentColor.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    imageVector = Icons.Rounded.CenterFocusStrong,
                    contentDescription = "Dry Center Vocal Focus",
                    tint = if (enabled) accentColor else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Vocal Focus",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "Locks 1–3.5kHz center vocals & <150Hz bass 100% dry",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
            }
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (enabled) accentColor else TextMuted
            )
        }
        Slider(
            value = vocalFocus.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor else TextSecondary,
                activeTrackColor = if (enabled) accentColor else TextMuted,
                inactiveTrackColor = StudioSurfaceHighlight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("vocal_focus_slider")
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
