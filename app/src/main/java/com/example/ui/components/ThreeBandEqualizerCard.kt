package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ThreeBandEqRanges
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.FaderCapWhite
import com.example.ui.theme.FaderTrackIdle
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.GridLineZero
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralBright
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.util.Locale
import kotlin.math.abs

@Composable
fun ThreeBandEqualizerCard(
    threeBandEnabled: Boolean,
    bassDb: Float,
    midsDb: Float,
    trebleDb: Float,
    isMasterBypassed: Boolean,
    onBassChange: (Float) -> Unit,
    onMidsChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onToggleEnabled: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectivelyBypassed = isMasterBypassed || !threeBandEnabled

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row: Title, Subtitle, Reset, and ON/OFF Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (effectivelyBypassed) StudioSurfaceElevated else AnalyzerCyanSoft)
                        .border(
                            1.dp,
                            if (effectivelyBypassed) StudioBorder else AnalyzerCyan.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = if (effectivelyBypassed) TextMuted else AnalyzerCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3-Band Tone Equalizer",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "250Hz Shelf • 1.2kHz Mid • 3.3kHz Shelf",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = StudioSurfaceElevated,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(999.dp))
                        .clickable(onClick = onReset)
                        .testTag("three_band_reset_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset 3-band equalizer",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (threeBandEnabled && !isMasterBypassed) {
                        StudioSurfaceHighlight
                    } else {
                        StudioSurfaceElevated
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(
                            width = 1.dp,
                            color = if (threeBandEnabled && !isMasterBypassed) {
                                RouteEmerald.copy(alpha = 0.7f)
                            } else {
                                StudioBorder
                            },
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clickable(onClick = onToggleEnabled)
                        .testTag("three_band_toggle_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (threeBandEnabled && !isMasterBypassed) RouteEmerald else TextMuted
                                )
                        )
                        Text(
                            text = if (threeBandEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (threeBandEnabled && !isMasterBypassed) RouteEmerald else TextMuted
                        )
                    }
                }
            }
        }

        // 3 Clean Graphic Vertical Faders (Bass, Mids, Treble)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.72f))
                .border(1.dp, StudioBorder.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThreeBandFaderColumn(
                title = "Bass",
                subtitle = "< 250 Hz",
                gainDb = bassDb,
                minDb = ThreeBandEqRanges.BASS_MIN_DB,
                maxDb = ThreeBandEqRanges.BASS_MAX_DB,
                maxLabel = "+7",
                minLabel = "-7",
                isBypassed = effectivelyBypassed,
                accentColor = SignalCoral,
                accentGlowColor = SignalCoralBright,
                onGainChange = onBassChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("three_band_bass_slider"),
                semanticsLabel = "Bass Low-Shelf below 250 Hz"
            )

            ThreeBandFaderColumn(
                title = "Mids",
                subtitle = "1.2 kHz",
                gainDb = midsDb,
                minDb = ThreeBandEqRanges.MIDS_MIN_DB,
                maxDb = ThreeBandEqRanges.MIDS_MAX_DB,
                maxLabel = "+3",
                minLabel = "-6",
                isBypassed = effectivelyBypassed,
                accentColor = WarmAmber,
                accentGlowColor = WarmAmber.copy(alpha = 0.95f),
                onGainChange = onMidsChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("three_band_mids_slider"),
                semanticsLabel = "Mids Peaking at 1.2 kHz"
            )

            ThreeBandFaderColumn(
                title = "Treble",
                subtitle = "> 3.3 kHz",
                gainDb = trebleDb,
                minDb = ThreeBandEqRanges.TREBLE_MIN_DB,
                maxDb = ThreeBandEqRanges.TREBLE_MAX_DB,
                maxLabel = "+7",
                minLabel = "-6",
                isBypassed = effectivelyBypassed,
                accentColor = AnalyzerCyan,
                accentGlowColor = AnalyzerCyan,
                onGainChange = onTrebleChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("three_band_treble_slider"),
                semanticsLabel = "Treble High-Shelf above 3.3 kHz"
            )
        }

        Text(
            text = "Bass (-7..+7 dB) • Mids 300Hz–4.7kHz (-6..+3 dB) • Treble (-6..+7 dB) • Double-tap to zero",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ThreeBandFaderColumn(
    title: String,
    subtitle: String,
    gainDb: Float,
    minDb: Float,
    maxDb: Float,
    maxLabel: String,
    minLabel: String,
    isBypassed: Boolean,
    accentColor: Color,
    accentGlowColor: Color,
    onGainChange: (Float) -> Unit,
    semanticsLabel: String,
    modifier: Modifier = Modifier
) {
    val clampedGain = gainDb.coerceIn(minDb, maxDb)
    val animatedGain by animateFloatAsState(
        targetValue = clampedGain,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 920f),
        label = "three_band_fader_$title"
    )
    val latestOnGainChange by rememberUpdatedState(onGainChange)
    val latestGain by rememberUpdatedState(clampedGain)
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var dragGain by remember { mutableFloatStateOf(clampedGain) }

    val formattedValue = remember(clampedGain) {
        when {
            abs(clampedGain) < 0.05f -> "0.0 dB"
            clampedGain > 0f -> String.format(Locale.US, "+%.1f dB", clampedGain)
            else -> String.format(Locale.US, "%.1f dB", clampedGain)
        }
    }

    val valueColor = when {
        isBypassed -> TextMuted
        abs(clampedGain) < 0.05f -> TextSecondary
        else -> accentGlowColor
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StudioSurface.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .semantics {
                contentDescription = semanticsLabel
                stateDescription = formattedValue
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Live dB pill readout
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(StudioSurfaceElevated)
                .border(
                    width = 1.dp,
                    color = if (!isBypassed && abs(clampedGain) >= 0.05f) {
                        accentColor.copy(alpha = 0.45f)
                    } else {
                        StudioBorder
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = valueColor,
                maxLines = 1
            )
        }

        // Slider Track + Scale Ticks
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left range labels aligned proportionally with the track (Max, 0 dB, Min)
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
                val zeroFractionFromTop = (1f - ((0f - minDb) / (maxDb - minDb))).coerceIn(0f, 1f)
                val zeroBias = (zeroFractionFromTop * 2f - 1f).coerceIn(-0.9f, 0.9f)
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextSecondary,
                    modifier = Modifier.align(androidx.compose.ui.BiasAlignment(1f, zeroBias))
                )
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            // Interactive Fader Groove & Cap
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(title, minDb, maxDb) {
                        detectTapGestures(
                            onDoubleTap = { latestOnGainChange(0f) },
                            onTap = { offset ->
                                val capPad = 11.dp.toPx()
                                val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                                val norm = 1f - ((offset.y - capPad) / usable).coerceIn(0f, 1f)
                                val newDb = minDb + norm * (maxDb - minDb)
                                latestOnGainChange(newDb)
                            }
                        )
                    }
                    .pointerInput(title, minDb, maxDb) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val capPad = 11.dp.toPx()
                                val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                                val norm = 1f - ((offset.y - capPad) / usable).coerceIn(0f, 1f)
                                dragGain = (minDb + norm * (maxDb - minDb)).coerceIn(minDb, maxDb)
                                latestOnGainChange(dragGain)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val capPad = 11.dp.toPx()
                                val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                                val span = maxDb - minDb
                                dragGain = (dragGain - (dragAmount / usable) * span).coerceIn(minDb, maxDb)
                                latestOnGainChange(dragGain)
                            },
                            onDragEnd = {
                                dragGain = latestGain
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val capRadius = 11.dp.toPx()
                    val trackW = 7.dp.toPx()
                    val usableTop = capRadius
                    val usableBottom = h - capRadius
                    val usableH = (usableBottom - usableTop).coerceAtLeast(1f)

                    val zeroNorm = ((0f - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    val zeroY = usableTop + usableH * (1f - zeroNorm)

                    // Subtle tick marks + crisp 0 dB horizontal reference across the fader well
                    drawLine(
                        color = GridLineSubtle,
                        start = Offset(cx - 14.dp.toPx(), usableTop),
                        end = Offset(cx + 14.dp.toPx(), usableTop),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    drawLine(
                        color = GridLineZero,
                        start = Offset(cx - 18.dp.toPx(), zeroY),
                        end = Offset(cx + 18.dp.toPx(), zeroY),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    drawLine(
                        color = GridLineSubtle,
                        start = Offset(cx - 14.dp.toPx(), usableBottom),
                        end = Offset(cx + 14.dp.toPx(), usableBottom),
                        strokeWidth = 0.8.dp.toPx()
                    )

                    // Recessed vertical groove
                    drawRoundRect(
                        color = FaderTrackIdle,
                        topLeft = Offset(cx - trackW * 0.5f, usableTop),
                        size = Size(trackW, usableH),
                        cornerRadius = CornerRadius(trackW * 0.5f, trackW * 0.5f)
                    )

                    val norm = ((animatedGain - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    val knobY = usableTop + usableH * (1f - norm)

                    // Active fill growing outward from true 0 dB line
                    val activeTop = minOf(zeroY, knobY)
                    val activeHeight = abs(knobY - zeroY)
                    if (activeHeight > 1f) {
                        val activeCol = if (isBypassed) TextMuted.copy(alpha = 0.45f) else accentColor
                        val glowCol = if (isBypassed) TextMuted.copy(alpha = 0.35f) else accentGlowColor
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(glowCol, activeCol),
                                startY = activeTop,
                                endY = activeTop + activeHeight
                            ),
                            topLeft = Offset(cx - trackW * 0.5f, activeTop),
                            size = Size(trackW, activeHeight),
                            cornerRadius = CornerRadius(trackW * 0.5f, trackW * 0.5f)
                        )
                    }

                    // Halo + Machined Circular Fader Cap
                    drawCircle(
                        color = if (isBypassed) Color.Transparent else accentColor.copy(alpha = 0.24f),
                        radius = capRadius + 3.dp.toPx(),
                        center = Offset(cx, knobY)
                    )
                    drawCircle(
                        color = if (isBypassed) Color(0xFF7B8396) else FaderCapWhite,
                        radius = capRadius,
                        center = Offset(cx, knobY)
                    )
                    drawCircle(
                        color = StudioBackground.copy(alpha = 0.35f),
                        radius = capRadius,
                        center = Offset(cx, knobY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = if (isBypassed) StudioSurface else accentColor,
                        radius = 3.dp.toPx(),
                        center = Offset(cx, knobY)
                    )
                }
            }
        }

        // Bottom Label + Frequency Band Subtitle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (isBypassed) TextMuted else accentGlowColor.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}
