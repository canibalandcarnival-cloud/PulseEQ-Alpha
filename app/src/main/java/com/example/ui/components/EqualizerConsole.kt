package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqPresetsCatalog
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.FaderCapWhite
import com.example.ui.theme.FaderTrackIdle
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.GridLineZero
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralBright
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

@Composable
fun EqualizerConsole(
    bandGainsDb: List<Float>,
    preampDb: Float,
    isBypassed: Boolean,
    onBandGainChange: (Int, Float) -> Unit,
    onPreampChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Preamp / Master Gain Slider
            VerticalFaderColumn(
                label = "PRE",
                gainDb = preampDb,
                isBypassed = isBypassed,
                accentColor = AnalyzerCyan,
                accentGlowColor = AnalyzerCyan,
                onGainChange = onPreampChange,
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .testTag("preamp_slider"),
                semanticsLabel = "Preamp Master Gain"
            )

            // Scale labels column (+12 dB / 0 dB / -12 dB)
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .padding(vertical = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "+12",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted
                )
                Text(
                    text = "+6",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = TextMuted.copy(alpha = 0.7f)
                )
                Text(
                    text = "0dB",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextSecondary
                )
                Text(
                    text = "-6",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = TextMuted.copy(alpha = 0.7f)
                )
                Text(
                    text = "-12",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextMuted
                )
            }

            // Right: 10-Band Graphic EQ with shared precision dB gridlines
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Background precision gridlines aligned with track padding
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp, bottom = 24.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val capRadiusPx = 10.dp.toPx()
                    val usableTop = capRadiusPx
                    val usableBottom = h - capRadiusPx
                    val usableH = (usableBottom - usableTop).coerceAtLeast(1f)

                    // Draw 9 gridlines: +12, +9, +6, +3, 0, -3, -6, -9, -12 dB
                    for (step in 0..8) {
                        val y = usableTop + usableH * (step / 8f)
                        val isZero = step == 4
                        val isMajor = step == 0 || step == 2 || step == 6 || step == 8
                        drawLine(
                            color = when {
                                isZero -> GridLineZero
                                isMajor -> GridLineSubtle.copy(alpha = 0.95f)
                                else -> GridLineSubtle.copy(alpha = 0.45f)
                            },
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = if (isZero) 1.3.dp.toPx() else 0.8.dp.toPx()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EqPresetsCatalog.BANDS.forEach { band ->
                        val gain = bandGainsDb.getOrElse(band.index) { 0f }
                        VerticalFaderColumn(
                            label = band.shortLabel,
                            gainDb = gain,
                            isBypassed = isBypassed,
                            accentColor = SignalCoral,
                            accentGlowColor = SignalCoralBright,
                            onGainChange = { newGain -> onBandGainChange(band.index, newGain) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("eq_band_${band.index}"),
                            semanticsLabel = "${band.fullLabel} Equalizer Band"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Drag faders (-12 to +12 dB) • Double-tap any fader to zero",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VerticalFaderColumn(
    label: String,
    gainDb: Float,
    isBypassed: Boolean,
    accentColor: Color,
    accentGlowColor: Color,
    onGainChange: (Float) -> Unit,
    semanticsLabel: String,
    modifier: Modifier = Modifier
) {
    val animatedGain by animateFloatAsState(
        targetValue = gainDb,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
        label = "fader_$label"
    )
    val latestOnGainChange by rememberUpdatedState(onGainChange)
    val latestGain by rememberUpdatedState(gainDb)
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var dragGain by remember { mutableFloatStateOf(gainDb) }

    val formattedValue = remember(gainDb) {
        when {
            abs(gainDb) < 0.05f -> "0"
            gainDb > 0f -> if (gainDb % 1f == 0f) "+${gainDb.toInt()}" else String.format(Locale.US, "+%.1f", gainDb)
            else -> if (gainDb % 1f == 0f) "${gainDb.toInt()}" else String.format(Locale.US, "%.1f", gainDb)
        }
    }

    val valueColor = when {
        isBypassed -> TextMuted
        abs(gainDb) < 0.05f -> TextSecondary
        else -> accentGlowColor
    }

    Column(
        modifier = modifier.semantics {
            contentDescription = semanticsLabel
            stateDescription = String.format(Locale.US, "%.1f dB", gainDb)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Live dB value at top
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = valueColor,
                maxLines = 1
            )
        }

        // Interactive Vertical Fader Track
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(label) {
                    detectTapGestures(
                        onDoubleTap = {
                            latestOnGainChange(0f)
                        },
                        onTap = { offset ->
                            val capPad = 10.dp.toPx()
                            val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                            val norm = 1f - ((offset.y - capPad) / usable).coerceIn(0f, 1f)
                            val newDb = EqPresetsCatalog.MIN_GAIN_DB +
                                norm * (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)
                            latestOnGainChange(newDb)
                        }
                    )
                }
                .pointerInput(label) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val capPad = 10.dp.toPx()
                            val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                            val norm = 1f - ((offset.y - capPad) / usable).coerceIn(0f, 1f)
                            dragGain = EqPresetsCatalog.MIN_GAIN_DB +
                                norm * (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)
                            latestOnGainChange(dragGain)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val capPad = 10.dp.toPx()
                            val usable = (trackHeightPx - capPad * 2f).coerceAtLeast(1f)
                            val dbSpan = EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB
                            dragGain = (dragGain - (dragAmount / usable) * dbSpan)
                                .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
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
                val capRadius = 9.5.dp.toPx()
                val trackW = 5.dp.toPx()
                val usableTop = capRadius
                val usableBottom = h - capRadius
                val usableH = (usableBottom - usableTop).coerceAtLeast(1f)
                val centerY = usableTop + usableH * 0.5f

                // Recessed groove track
                drawRoundRect(
                    color = FaderTrackIdle,
                    topLeft = Offset(cx - trackW * 0.5f, usableTop),
                    size = Size(trackW, usableH),
                    cornerRadius = CornerRadius(trackW * 0.5f, trackW * 0.5f)
                )

                val norm = ((animatedGain - EqPresetsCatalog.MIN_GAIN_DB) /
                    (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)).coerceIn(0f, 1f)
                val knobY = usableTop + usableH * (1f - norm)

                // Active level fill growing from 0dB center line (mirrors the reference images!)
                val activeTop = minOf(centerY, knobY)
                val activeHeight = abs(knobY - centerY)
                if (activeHeight > 1f) {
                    val activeColor = if (isBypassed) TextMuted.copy(alpha = 0.45f) else accentColor
                    val glowColor = if (isBypassed) TextMuted.copy(alpha = 0.35f) else accentGlowColor
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(glowColor, activeColor),
                            startY = activeTop,
                            endY = activeTop + activeHeight
                        ),
                        topLeft = Offset(cx - trackW * 0.5f, activeTop),
                        size = Size(trackW, activeHeight),
                        cornerRadius = CornerRadius(trackW * 0.5f, trackW * 0.5f)
                    )
                }

                // Knob drop halo
                drawCircle(
                    color = if (isBypassed) Color.Transparent else accentColor.copy(alpha = 0.22f),
                    radius = capRadius + 2.5.dp.toPx(),
                    center = Offset(cx, knobY)
                )

                // Machined circular white fader cap
                drawCircle(
                    color = if (isBypassed) Color(0xFF7B8396) else FaderCapWhite,
                    radius = capRadius,
                    center = Offset(cx, knobY)
                )
                drawCircle(
                    color = StudioBackground.copy(alpha = 0.35f),
                    radius = capRadius,
                    center = Offset(cx, knobY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = if (isBypassed) StudioSurface else accentColor,
                    radius = 2.6.dp.toPx(),
                    center = Offset(cx, knobY)
                )
            }
        }

        // Bottom Frequency / Channel Label
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
