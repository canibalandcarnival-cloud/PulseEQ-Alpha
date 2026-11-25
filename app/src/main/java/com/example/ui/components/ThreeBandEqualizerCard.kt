package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ThreeBandToneFilter
import com.example.model.ThreeBandEqRanges
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.FaderCapBypassed
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
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun ThreeBandEqualizerCard(
    threeBandEnabled: Boolean,
    bassDb: Float,
    midsDb: Float,
    trebleDb: Float,
    smartCurveEnabled: Boolean = true,
    recommendedMode: Boolean = false,
    recommendedBannerVisible: Boolean = false,
    isSpeakerTarget: Boolean = false,
    isMasterBypassed: Boolean,
    onBassChange: (Float) -> Unit,
    onMidsChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleSmartCurve: (Boolean) -> Unit = {},
    onSelectRecommendedMode: (Boolean) -> Unit = {},
    onDismissRecommendedBanner: () -> Unit = {},
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectivelyBypassed = isMasterBypassed || !threeBandEnabled
    val isFlat = abs(bassDb) < 0.05f && abs(midsDb) < 0.05f && abs(trebleDb) < 0.05f
    val isRecommendedSelected = recommendedMode && isFlat
    val bodyAlpha by animateFloatAsState(
        targetValue = if (threeBandEnabled) 1f else 0.40f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "three_band_alpha"
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
            .border(
                1.dp,
                if (threeBandEnabled && !isMasterBypassed) AppleBlue.copy(alpha = 0.50f) else AppleGlassBorder,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row: Title, Reset, and ON/OFF Toggle
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
                        .background(if (effectivelyBypassed) StudioSurfaceElevated else AppleBlueSoft)
                        .border(
                            1.dp,
                            if (effectivelyBypassed) StudioBorder else AppleBlue.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = if (effectivelyBypassed) TextMuted else AppleBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "iOS 27 3-Band Equalizer",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (threeBandEnabled) TextPrimary else TextSecondary
                    )
                    if (!threeBandEnabled) {
                        Text(
                            text = "Disabled (10-Band Active)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextMuted,
                            modifier = Modifier.testTag("three_band_disabled_badge")
                        )
                    } else {
                        Text(
                            text = if (smartCurveEnabled) {
                                "iOS 27 Smart Curve • -7.0 to +7.0 dB"
                            } else {
                                "Linear Independent • -7.0 to +7.0 dB"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (smartCurveEnabled) AppleBlue else TextSecondary
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (threeBandEnabled) {
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

        // Top Pill Switcher: Recommended (Flat) | Custom (iOS 27 AirPods style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(StudioBackground.copy(alpha = 0.72f))
                .border(1.dp, StudioBorder, RoundedCornerShape(999.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (threeBandEnabled && isRecommendedSelected) AppleBlueSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (threeBandEnabled && isRecommendedSelected) AppleBlue.copy(alpha = 0.72f) else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelectRecommendedMode(true) }
                    .testTag("three_band_recommended_pill"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Recommended (Flat)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (threeBandEnabled && isRecommendedSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (threeBandEnabled && isRecommendedSelected) AppleBlue else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (threeBandEnabled && !isRecommendedSelected) AppleBlueSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (threeBandEnabled && !isRecommendedSelected) AppleBlue.copy(alpha = 0.72f) else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelectRecommendedMode(false) }
                    .testTag("three_band_custom_pill"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (threeBandEnabled && !isRecommendedSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (threeBandEnabled && !isRecommendedSelected) AppleBlue else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Subtle iOS-style Info Banner when Headphone Accommodations > Custom Audio Setup resets 3-Band EQ to Recommended
        AnimatedVisibility(visible = recommendedBannerVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleBlueSoft)
                    .border(1.dp, AppleBlue.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("three_band_recommended_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "Recommended Equalizer Notice",
                    tint = AppleBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Equalizer set to Recommended to match your Custom Audio Setup",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismissRecommendedBanner,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("three_band_banner_dismiss_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss Recommended notice",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Apple Smart Tilt vs. Pure Independent Curve Mode Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.65f))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (smartCurveEnabled) AppleBlueSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (smartCurveEnabled) AppleBlue.copy(alpha = 0.72f) else Color.Transparent,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onToggleSmartCurve(true) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("three_band_smart_curve_btn"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (smartCurveEnabled) AppleBlue else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = " iOS 27 Smart Curve",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = if (smartCurveEnabled) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (smartCurveEnabled) AppleBlue else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (!smartCurveEnabled) AnalyzerCyanSoft else Color.Transparent)
                    .border(
                        1.dp,
                        if (!smartCurveEnabled) AnalyzerCyan.copy(alpha = 0.68f) else Color.Transparent,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onToggleSmartCurve(false) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("three_band_linear_curve_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Linear Independent",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = if (!smartCurveEnabled) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (!smartCurveEnabled) AnalyzerCyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Interactive 60fps Frequency Curve + 3 Vertical Faders (Low, Mid, High) — Dimmed & locked when 10-Band EQ is active
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(bodyAlpha)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Smooth 60fps iOS Blue Curve Visualization directly above faders
                ThreeBandInteractiveCurveCanvas(
                    bassDb = if (threeBandEnabled) bassDb else 0f,
                    midsDb = if (threeBandEnabled) midsDb else 0f,
                    trebleDb = if (threeBandEnabled) trebleDb else 0f,
                    smartCurveEnabled = smartCurveEnabled,
                    isSpeakerTarget = isSpeakerTarget,
                    isBypassed = effectivelyBypassed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .testTag("three_band_curve_canvas")
                )

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
                        title = "LOW",
                        subtitle = "20Hz – 250Hz",
                        gainDb = bassDb,
                        minDb = ThreeBandEqRanges.BASS_MIN_DB,
                        maxDb = ThreeBandEqRanges.BASS_MAX_DB,
                        maxLabel = "+7",
                        minLabel = "-7",
                        isBypassed = effectivelyBypassed,
                        isInteractive = threeBandEnabled,
                        accentColor = SignalCoral,
                        accentGlowColor = SignalCoralBright,
                        onGainChange = onBassChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("three_band_bass_slider"),
                        semanticsLabel = "Low Band 20Hz to 250Hz Low Shelf"
                    )

                    ThreeBandFaderColumn(
                        title = "MID",
                        subtitle = "250Hz – 4.7kHz",
                        gainDb = midsDb,
                        minDb = ThreeBandEqRanges.MIDS_MIN_DB,
                        maxDb = ThreeBandEqRanges.MIDS_MAX_DB,
                        maxLabel = "+7",
                        minLabel = "-7",
                        isBypassed = effectivelyBypassed,
                        isInteractive = threeBandEnabled,
                        accentColor = WarmAmber,
                        accentGlowColor = WarmAmber.copy(alpha = 0.95f),
                        onGainChange = onMidsChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("three_band_mids_slider"),
                        semanticsLabel = "Mid Band 250Hz to 4.7kHz"
                    )

                    ThreeBandFaderColumn(
                        title = "HIGH",
                        subtitle = "4.7kHz – 20kHz",
                        gainDb = trebleDb,
                        minDb = ThreeBandEqRanges.TREBLE_MIN_DB,
                        maxDb = ThreeBandEqRanges.TREBLE_MAX_DB,
                        maxLabel = "+7",
                        minLabel = "-7",
                        isBypassed = effectivelyBypassed,
                        isInteractive = threeBandEnabled,
                        accentColor = AppleBlue,
                        accentGlowColor = AppleBlue,
                        onGainChange = onTrebleChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("three_band_treble_slider"),
                        semanticsLabel = "High Band 4.7kHz to 20kHz High Shelf"
                    )
                }

                Text(
                    text = if (!threeBandEnabled) {
                        "3-Band Tone EQ bypassed at 0 dB while 10-Band EQ shapes the audio"
                    } else if (smartCurveEnabled) {
                        "Smart Compensation: Mid + trims <250Hz & lifts >3.3kHz • Mid - adds V-curve • High + adds -0.15dB/dB warmth tilt"
                    } else {
                        "Linear Independent: Pure 250Hz Low / 1084Hz Mid / 4.7kHz High (-7.0 to +7.0 dB) • Double-tap to reset"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!threeBandEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun ThreeBandInteractiveCurveCanvas(
    bassDb: Float,
    midsDb: Float,
    trebleDb: Float,
    smartCurveEnabled: Boolean,
    isSpeakerTarget: Boolean,
    isBypassed: Boolean,
    modifier: Modifier = Modifier
) {
    // 60fps responsive spring interpolation so dragging sliders bends the iOS Blue curve smoothly
    val animBass by animateFloatAsState(
        targetValue = bassDb,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 1100f),
        label = "curve_bass"
    )
    val animMids by animateFloatAsState(
        targetValue = midsDb,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 1100f),
        label = "curve_mids"
    )
    val animTreble by animateFloatAsState(
        targetValue = trebleDb,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 1100f),
        label = "curve_treble"
    )

    val freqs = remember {
        List(56) { idx ->
            val t = idx / 55.0
            20.0 * (20000.0 / 20.0).pow(t)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBackground.copy(alpha = 0.86f))
            .border(
                1.dp,
                if (isBypassed) StudioBorder else AppleBlue.copy(alpha = 0.42f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 2f || h <= 2f) return@Canvas

            val maxRangeDb = 9.0f
            val zeroY = h * 0.5f

            fun dbToY(db: Float): Float {
                val norm = (db / maxRangeDb).coerceIn(-1f, 1f)
                return (zeroY - norm * (h * 0.44f)).coerceIn(2f, h - 2f)
            }

            fun freqToX(freqHz: Double): Float {
                val t = (ln(freqHz / 20.0) / ln(20000.0 / 20.0)).toFloat().coerceIn(0f, 1f)
                return t * w
            }

            // Reference dB lines (+7, 0, -7)
            drawLine(
                color = GridLineSubtle,
                start = Offset(0f, dbToY(7f)),
                end = Offset(w, dbToY(7f)),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = GridLineZero,
                start = Offset(0f, zeroY),
                end = Offset(w, zeroY),
                strokeWidth = 1.1.dp.toPx()
            )
            drawLine(
                color = GridLineSubtle,
                start = Offset(0f, dbToY(-7f)),
                end = Offset(w, dbToY(-7f)),
                strokeWidth = 0.8.dp.toPx()
            )

            // Crossover guides at 250 Hz and 4.7 kHz
            val x250 = freqToX(250.0)
            val x4700 = freqToX(4700.0)
            drawLine(
                color = GridLineSubtle.copy(alpha = 0.65f),
                start = Offset(x250, 0f),
                end = Offset(x250, h),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = GridLineSubtle.copy(alpha = 0.65f),
                start = Offset(x4700, 0f),
                end = Offset(x4700, h),
                strokeWidth = 0.8.dp.toPx()
            )

            val points = freqs.map { f ->
                val respDb = ThreeBandToneFilter.computeCombinedResponseDbAt(
                    freqHz = f,
                    bassDb = animBass,
                    midsDb = animMids,
                    trebleDb = animTreble,
                    isSpeakerTarget = isSpeakerTarget,
                    sampleRate = 48000,
                    smartCurveEnabled = smartCurveEnabled
                )
                Offset(freqToX(f), dbToY(respDb))
            }

            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { idx, pt ->
                if (idx == 0) {
                    linePath.moveTo(pt.x, pt.y)
                    fillPath.moveTo(pt.x, zeroY)
                    fillPath.lineTo(pt.x, pt.y)
                } else {
                    val prev = points[idx - 1]
                    val midX = (prev.x + pt.x) * 0.5f
                    linePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                    fillPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                }
            }
            fillPath.lineTo(points.last().x, zeroY)
            fillPath.close()

            val curveColor = if (isBypassed) TextMuted else AppleBlue
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        curveColor.copy(alpha = 0.28f),
                        curveColor.copy(alpha = 0.04f)
                    )
                )
            )
            drawPath(
                path = linePath,
                color = curveColor.copy(alpha = 0.25f),
                style = Stroke(
                    width = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = linePath,
                color = curveColor,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Subtle overlay frequency landmarks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "20Hz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
            Text(
                text = "250Hz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
            Text(
                text = "1kHz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
            Text(
                text = "4.7kHz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
            Text(
                text = "20kHz",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TextMuted
            )
        }
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
    isInteractive: Boolean,
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
    val latestInteractive by rememberUpdatedState(isInteractive)
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
                    .width(24.dp)
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
                    .pointerInput(title, minDb, maxDb, isInteractive) {
                        if (!latestInteractive) return@pointerInput
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
                    .pointerInput(title, minDb, maxDb, isInteractive) {
                        if (!latestInteractive) return@pointerInput
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

                    drawRoundRect(
                        color = FaderTrackIdle,
                        topLeft = Offset(cx - trackW * 0.5f, usableTop),
                        size = Size(trackW, usableH),
                        cornerRadius = CornerRadius(trackW * 0.5f, trackW * 0.5f)
                    )

                    val norm = ((animatedGain - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    val knobY = usableTop + usableH * (1f - norm)

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

                    drawCircle(
                        color = if (isBypassed) Color.Transparent else accentColor.copy(alpha = 0.24f),
                        radius = capRadius + 3.dp.toPx(),
                        center = Offset(cx, knobY)
                    )
                    drawCircle(
                        color = if (isBypassed) FaderCapBypassed else FaderCapWhite,
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
