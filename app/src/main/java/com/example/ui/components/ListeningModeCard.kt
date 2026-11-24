package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLabTelemetry
import com.example.model.ListeningMode
import com.example.model.OutputTarget
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleIndigo
import com.example.ui.theme.AppleIndigoSoft
import com.example.ui.theme.DynamicIslandBorder
import com.example.ui.theme.DynamicIslandSurface
import com.example.ui.theme.FaderCapWhite
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * iOS 27 AirPods-Style Listening Mode Capsule Card for Headphones & Bluetooth targets.
 * Features Transparency, Adaptive, Noise Cancellation, and an Off bypass toggle.
 */
@Composable
fun ListeningModeCard(
    listeningMode: ListeningMode,
    outputTarget: OutputTarget,
    isMasterBypassed: Boolean,
    abCompareHeld: Boolean,
    telemetry: AudioLabTelemetry,
    onSelectListeningMode: (ListeningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveActive = !isMasterBypassed && !abCompareHeld && listeningMode != ListeningMode.OFF
    val accentColor = when {
        !effectiveActive -> TextSecondary
        listeningMode == ListeningMode.TRANSPARENCY -> RouteEmerald
        listeningMode == ListeningMode.ADAPTIVE -> AnalyzerCyan
        listeningMode == ListeningMode.NOISE_CANCELLATION -> AppleBlue
        else -> TextSecondary
    }
    val accentBg = when {
        !effectiveActive -> StudioSurfaceElevated
        listeningMode == ListeningMode.TRANSPARENCY -> RouteEmeraldSoft
        listeningMode == ListeningMode.ADAPTIVE -> AnalyzerCyanSoft
        listeningMode == ListeningMode.NOISE_CANCELLATION -> AppleBlueSoft
        else -> StudioSurfaceElevated
    }
    val borderColor by animateColorAsState(
        targetValue = if (effectiveActive) accentColor.copy(alpha = 0.58f) else AppleGlassBorder,
        animationSpec = tween(180),
        label = "listeningModeBorder"
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
            .padding(14.dp)
            .testTag("listening_mode_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row: "Listening Mode" + Output Badge + "Off" Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (effectiveActive) accentBg else AppleGlassTile)
                        .border(
                            width = 1.dp,
                            color = if (effectiveActive) accentColor.copy(alpha = 0.6f) else AppleGlassBorder,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (outputTarget == OutputTarget.BLUETOOTH) {
                            Icons.Rounded.BluetoothAudio
                        } else {
                            Icons.Rounded.Headphones
                        },
                        contentDescription = "Listening Mode for ${outputTarget.label}",
                        tint = if (effectiveActive) accentColor else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Listening Mode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val isoMetric = telemetry.listeningModeIsolationDb
                    val statusSub = when {
                        abCompareHeld -> "A/B Compare Held • Original Bypassed"
                        isMasterBypassed -> "Master EQ Bypassed"
                        listeningMode == ListeningMode.OFF -> "${outputTarget.shortLabel} • Direct Passive Mode"
                        listeningMode == ListeningMode.TRANSPARENCY -> String.format(
                            Locale.US,
                            "%s • +%.1f dB Open Air",
                            outputTarget.shortLabel,
                            kotlin.math.abs(isoMetric).coerceAtLeast(1.6f)
                        )
                        listeningMode == ListeningMode.ADAPTIVE -> String.format(
                            Locale.US,
                            "%s • %.0f dB Dynamic Blend",
                            outputTarget.shortLabel,
                            isoMetric.takeIf { it < -1f } ?: -10f
                        )
                        listeningMode == ListeningMode.NOISE_CANCELLATION -> String.format(
                            Locale.US,
                            "%s • %.0f dB Active ANC",
                            outputTarget.shortLabel,
                            isoMetric.takeIf { it < -1f } ?: -28f
                        )
                        else -> outputTarget.label
                    }
                    Text(
                        text = statusSub,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (effectiveActive) accentColor else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("listening_mode_status")
                    )
                }
            }

            // "Off" pill button
            val isOff = listeningMode == ListeningMode.OFF
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isOff) StudioSurfaceElevated else AppleGlassTile)
                    .border(
                        width = 1.dp,
                        color = if (isOff) AppleGlassBorder else StudioBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onSelectListeningMode(ListeningMode.OFF) }
                    )
                    .semantics {
                        selected = isOff
                        contentDescription = "Listening Mode Off"
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("listening_mode_off_chip"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Off",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isOff) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isOff) TextPrimary else TextSecondary
                )
            }
        }

        // iOS 27 Segmented Pill Capsule: Transparency | ○ | Adaptive | ○ | Noise Cancellation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(DynamicIslandSurface)
                .border(1.dp, DynamicIslandBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ListeningModeCapsuleSegment(
                    mode = ListeningMode.TRANSPARENCY,
                    isSelected = !isMasterBypassed && listeningMode == ListeningMode.TRANSPARENCY,
                    onClick = { onSelectListeningMode(ListeningMode.TRANSPARENCY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("listening_mode_transparency")
                )

                CapsuleDetentDot()

                ListeningModeCapsuleSegment(
                    mode = ListeningMode.ADAPTIVE,
                    isSelected = !isMasterBypassed && listeningMode == ListeningMode.ADAPTIVE,
                    onClick = { onSelectListeningMode(ListeningMode.ADAPTIVE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("listening_mode_adaptive")
                )

                CapsuleDetentDot()

                ListeningModeCapsuleSegment(
                    mode = ListeningMode.NOISE_CANCELLATION,
                    isSelected = !isMasterBypassed && listeningMode == ListeningMode.NOISE_CANCELLATION,
                    onClick = { onSelectListeningMode(ListeningMode.NOISE_CANCELLATION) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("listening_mode_noise_cancellation")
                )
            }
        }

        // Centered Labels Directly Beneath Each Capsule Segment (matching iOS 27 layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ListeningModeLabelCell(
                label = "Transparency",
                isSelected = !isMasterBypassed && listeningMode == ListeningMode.TRANSPARENCY,
                modifier = Modifier.weight(1f)
            )
            ListeningModeLabelCell(
                label = "Adaptive",
                isSelected = !isMasterBypassed && listeningMode == ListeningMode.ADAPTIVE,
                modifier = Modifier.weight(1f)
            )
            ListeningModeLabelCell(
                label = "Noise\nCancellation",
                isSelected = !isMasterBypassed && listeningMode == ListeningMode.NOISE_CANCELLATION,
                modifier = Modifier.weight(1f)
            )
        }

        // Contour Telemetry Strip
        val dspSpecText = when {
            isMasterBypassed -> "Master EQ Bypassed • Passive passthrough"
            abCompareHeld -> "A/B Hold • Comparing raw uncolored headphones signal"
            listeningMode == ListeningMode.TRANSPARENCY ->
                "Open-Ear Contour: 85Hz -1.8dB occlusion relief • 2.2kHz +1.6dB openness • +8% side air"
            listeningMode == ListeningMode.ADAPTIVE ->
                "Dynamic Control: 95Hz -2.5dB rumble cut • 1.5kHz +1.0dB speech lock • 4.2kHz peak guard"
            listeningMode == ListeningMode.NOISE_CANCELLATION ->
                "Active ANC Contour: 48Hz HPF + 75Hz -4.5dB hum dip • 125Hz +2.0dB seal • 1.8kHz focus"
            else -> "Direct Output • Passive seal only (0.0 dB contour)"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.6f))
                .border(1.dp, AppleGlassBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = dspSpecText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = if (effectiveActive) TextSecondary else TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CapsuleDetentDot() {
    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(CircleShape)
            .border(1.5.dp, TextMuted.copy(alpha = 0.48f), CircleShape)
    )
}

@Composable
private fun ListeningModeCapsuleSegment(
    mode: ListeningMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg by animateColorAsState(
        targetValue = if (isSelected) FaderCapWhite else Color.Transparent,
        animationSpec = tween(180),
        label = "pillBg_${mode.id}"
    )
    val glyphColor by animateColorAsState(
        targetValue = if (isSelected) DynamicIslandSurface else TextPrimary,
        animationSpec = tween(180),
        label = "glyphColor_${mode.id}"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(pillBg)
            .clickable(
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                selected = isSelected
                contentDescription = mode.label
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(width = 30.dp, height = 26.dp)
        ) {
            when (mode) {
                ListeningMode.TRANSPARENCY -> drawTransparencyGlyph(glyphColor)
                ListeningMode.ADAPTIVE -> drawAdaptiveGlyph(glyphColor)
                ListeningMode.NOISE_CANCELLATION -> drawNoiseCancellationGlyph(glyphColor)
                ListeningMode.OFF -> Unit
            }
        }
    }
}

@Composable
private fun ListeningModeLabelCell(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 13.sp
        ),
        color = if (isSelected) TextPrimary else TextSecondary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private fun DrawScope.drawPersonSilhouette(
    color: Color,
    centerX: Float,
    headY: Float,
    headRadius: Float,
    shoulderBottomY: Float,
    shoulderHalfWidth: Float
) {
    // Head
    drawCircle(
        color = color,
        radius = headRadius,
        center = Offset(centerX, headY)
    )
    // Shoulders dome
    val shoulderTopY = headY + headRadius * 1.45f
    val path = Path().apply {
        moveTo(centerX - shoulderHalfWidth, shoulderBottomY)
        cubicTo(
            centerX - shoulderHalfWidth, shoulderTopY,
            centerX + shoulderHalfWidth, shoulderTopY,
            centerX + shoulderHalfWidth, shoulderBottomY
        )
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawTransparencyGlyph(color: Color) {
    val cx = size.width * 0.5f
    val headY = size.height * 0.54f
    val headR = size.minDimension * 0.145f
    drawPersonSilhouette(
        color = color,
        centerX = cx,
        headY = headY,
        headRadius = headR,
        shoulderBottomY = size.height * 0.90f,
        shoulderHalfWidth = size.width * 0.25f
    )
    // Radiating dotted halo waves around head/shoulders (iOS Transparency icon)
    val arcCenter = Offset(cx, headY + headR * 0.35f)
    val ringR = size.minDimension * 0.42f
    val anglesDeg = listOf(-165f, -140f, -115f, -90f, -65f, -40f, -15f)
    val dotR = size.minDimension * 0.048f
    for (deg in anglesDeg) {
        val rad = deg * PI / 180.0
        val dx = arcCenter.x + (ringR * cos(rad)).toFloat()
        val dy = arcCenter.y + (ringR * sin(rad)).toFloat()
        drawCircle(color = color, radius = dotR, center = Offset(dx, dy))
    }
}

private fun DrawScope.drawAdaptiveGlyph(color: Color) {
    val cx = size.width * 0.44f
    val headY = size.height * 0.50f
    val headR = size.minDimension * 0.155f
    drawPersonSilhouette(
        color = color,
        centerX = cx,
        headY = headY,
        headRadius = headR,
        shoulderBottomY = size.height * 0.88f,
        shoulderHalfWidth = size.width * 0.27f
    )
    // Two 4-point sparkle stars at upper right (iOS Adaptive Audio icon)
    drawFourPointSparkle(
        color = color,
        center = Offset(size.width * 0.72f, size.height * 0.26f),
        radius = size.minDimension * 0.15f
    )
    drawFourPointSparkle(
        color = color,
        center = Offset(size.width * 0.84f, size.height * 0.48f),
        radius = size.minDimension * 0.095f
    )
}

private fun DrawScope.drawFourPointSparkle(
    color: Color,
    center: Offset,
    radius: Float
) {
    val inner = radius * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + inner * 0.4f, center.y - inner * 0.4f, center.x + radius, center.y)
        quadraticTo(center.x + inner * 0.4f, center.y + inner * 0.4f, center.x, center.y + radius)
        quadraticTo(center.x - inner * 0.4f, center.y + inner * 0.4f, center.x - radius, center.y)
        quadraticTo(center.x - inner * 0.4f, center.y - inner * 0.4f, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawNoiseCancellationGlyph(color: Color) {
    val cx = size.width * 0.5f
    val headY = size.height * 0.52f
    val headR = size.minDimension * 0.145f
    drawPersonSilhouette(
        color = color,
        centerX = cx,
        headY = headY,
        headRadius = headR,
        shoulderBottomY = size.height * 0.88f,
        shoulderHalfWidth = size.width * 0.23f
    )
    // Acoustic shield halo ring enclosing listener (iOS ANC icon)
    val ringR = size.minDimension * 0.43f
    drawArc(
        color = color,
        startAngle = 140f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(cx - ringR, headY + headR * 0.15f - ringR),
        size = Size(ringR * 2f, ringR * 2f),
        style = Stroke(width = size.minDimension * 0.085f, cap = StrokeCap.Round)
    )
}
