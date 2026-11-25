package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioMixStemShaper
import com.example.audio.HeadphoneAccommodationsProcessor
import com.example.audio.IosVolumeContourAndDeHarshProcessor
import com.example.audio.IphoneLoudnessMaximizerChain
import com.example.audio.ListeningModeProcessor
import com.example.audio.PhoneSpeakerDspChain
import com.example.audio.ThreeBandToneFilter
import com.example.audio.TrebleSmoothnessProcessor
import com.example.model.AccommodationToneProfile
import com.example.model.AudioMixMode
import com.example.model.EqPresetsCatalog
import com.example.model.ListeningMode
import com.example.model.OutputTarget
import com.example.model.SoftSoundIntensity
import com.example.model.SpatialMode
import com.example.model.TestAudioSource
import com.example.model.TrebleGainStagingBudget
import com.example.model.VisualizerFrame
import com.example.model.VisualizerMode
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanDeep
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.GridLineZero
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
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

@Composable
fun AudioVisualizerPanel(
    frame: VisualizerFrame,
    bandGainsDb: List<Float>,
    preampDb: Float,
    isBypassed: Boolean,
    visualizerMode: VisualizerMode,
    testAudioSource: TestAudioSource,
    hasAudioPermission: Boolean,
    onSelectMode: (VisualizerMode) -> Unit,
    onCycleTestAudio: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    tenBandEnabled: Boolean = true,
    threeBandEnabled: Boolean = false,
    threeBandBassDb: Float = 0f,
    threeBandMidsDb: Float = 0f,
    threeBandTrebleDb: Float = 0f,
    threeBandSmartCurveEnabled: Boolean = true,
    accommodationsEnabled: Boolean = false,
    accommodationsToneProfile: AccommodationToneProfile = AccommodationToneProfile.BALANCED_TONE,
    accommodationsSoftIntensity: SoftSoundIntensity = SoftSoundIntensity.MODERATE,
    accommodationsApplyToMedia: Boolean = true,
    loudnessMaximizerEnabled: Boolean = false,
    outputTarget: OutputTarget = OutputTarget.HEADPHONES,
    audioMixMode: AudioMixMode = AudioMixMode.STANDARD,
    trebleSmoothnessPercent: Float = 0f,
    spatialMode: SpatialMode = SpatialMode.OFF,
    spatialHeightPresence: Float = 0.58f,
    siliconDspEnabled: Boolean = true,
    masterVolumePercent: Float = 0.75f,
    speakerDspEnabled: Boolean = false,
    proMaxBodyWarmthEnabled: Boolean = false,
    listeningMode: ListeningMode = ListeningMode.OFF,
    adaptiveWarmthActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeThreeBand = threeBandEnabled && !tenBandEnabled
    val activeTenBand = !activeThreeBand
    val targetCurveGains = remember(
        bandGainsDb,
        preampDb,
        isBypassed,
        activeTenBand,
        activeThreeBand,
        threeBandBassDb,
        threeBandMidsDb,
        threeBandTrebleDb,
        threeBandSmartCurveEnabled,
        accommodationsEnabled,
        accommodationsToneProfile,
        accommodationsSoftIntensity,
        accommodationsApplyToMedia,
        loudnessMaximizerEnabled,
        outputTarget,
        audioMixMode,
        trebleSmoothnessPercent,
        spatialMode,
        spatialHeightPresence,
        siliconDspEnabled,
        masterVolumePercent,
        speakerDspEnabled,
        proMaxBodyWarmthEnabled,
        listeningMode,
        adaptiveWarmthActive
    ) {
        if (isBypassed) {
            List(bandGainsDb.size) { 0f }
        } else {
            val trebleBudget = TrebleGainStagingBudget.allocate(
                accommodationsActive = accommodationsEnabled && accommodationsApplyToMedia,
                accommodationsProfile = accommodationsToneProfile,
                accommodationsIntensity = accommodationsSoftIntensity,
                spatialActive = spatialMode != SpatialMode.OFF,
                spatialHeightPresence = spatialHeightPresence,
                siliconDspActive = siliconDspEnabled
            )
            bandGainsDb.mapIndexed { index, gain ->
                val freqHz = EqPresetsCatalog.BANDS.getOrNull(index)?.frequencyHz?.toDouble() ?: 1000.0
                val tenBandGain = if (activeTenBand) gain else 0f
                val toneOffset = if (activeThreeBand) {
                    ThreeBandToneFilter.computeCombinedResponseDbAt(
                        freqHz = freqHz,
                        bassDb = threeBandBassDb,
                        midsDb = threeBandMidsDb,
                        trebleDb = threeBandTrebleDb,
                        isSpeakerTarget = (outputTarget == OutputTarget.SPEAKER),
                        smartCurveEnabled = threeBandSmartCurveEnabled
                    )
                } else {
                    0f
                }
                val accommodationsOffset = if (accommodationsEnabled && accommodationsApplyToMedia) {
                    HeadphoneAccommodationsProcessor.computeResponseDbAt(
                        freqHz = freqHz,
                        profile = accommodationsToneProfile,
                        intensity = accommodationsSoftIntensity,
                        hfScale = trebleBudget.accHfScale.toDouble()
                    )
                } else {
                    0f
                }
                val smoothnessOffset = if (trebleSmoothnessPercent > 0.005f) {
                    TrebleSmoothnessProcessor.computeResponseDbAt(
                        freqHz = freqHz,
                        smoothnessPercent = trebleSmoothnessPercent
                    )
                } else {
                    0f
                }
                val loudnessOffset = if (loudnessMaximizerEnabled) {
                    IphoneLoudnessMaximizerChain.computePresenceAndHpfResponseDbAt(
                        freqHz = freqHz,
                        target = outputTarget
                    )
                } else {
                    0f
                }
                val audioMixOffset = if (audioMixMode != AudioMixMode.STANDARD) {
                    AudioMixStemShaper.computeFrequencyTiltDbAt(
                        freqHz = freqHz,
                        mode = audioMixMode
                    )
                } else {
                    0f
                }
                val volumeContourOffset = IosVolumeContourAndDeHarshProcessor.computeContourResponseDbAt(
                    freqHz = freqHz,
                    volumePercent = masterVolumePercent
                )
                val bodyWarmthOffset = if (outputTarget == OutputTarget.SPEAKER && speakerDspEnabled && proMaxBodyWarmthEnabled) {
                    PhoneSpeakerDspChain.computeBodyWarmthResponseDbAt(freqHz = freqHz)
                } else {
                    0f
                }
                val listeningModeOffset = if (listeningMode != ListeningMode.OFF) {
                    ListeningModeProcessor.computeMusicResponseDbAt(
                        freqHz = freqHz,
                        mode = listeningMode,
                        adaptiveWarmthRatio = if (adaptiveWarmthActive) 1f else 0f
                    )
                } else {
                    0f
                }
                (tenBandGain + toneOffset + accommodationsOffset + smoothnessOffset + loudnessOffset + audioMixOffset + volumeContourOffset + bodyWarmthOffset + listeningModeOffset + preampDb * 0.5f)
                    .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
            }
        }
    }

    // Animate EQ response curve points for smooth transitions on preset changes
    val animatedCurveGains = targetCurveGains.mapIndexed { index, target ->
        val anim by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(220),
            label = "curve_$index"
        )
        anim
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
            .border(1.dp, AppleGlassBorder, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Controls: Mode Switcher + Test Audio + System Mix Permission
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisualizerMode.entries.forEach { mode ->
                    val selected = mode == visualizerMode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) StudioSurfaceHighlight else StudioSurfaceElevated,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = if (selected) AnalyzerCyan.copy(alpha = 0.7f) else StudioBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectMode(mode) }
                            .testTag("vis_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) AnalyzerCyan else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!hasAudioPermission && testAudioSource == TestAudioSource.OFF) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onRequestAudioPermission)
                            .testTag("enable_system_visualizer_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Enable external audio visualizer",
                                tint = WarmAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Mix",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmAmber
                            )
                        }
                    }
                }

                val isTestPlaying = testAudioSource != TestAudioSource.OFF
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTestPlaying) AnalyzerCyanSoft else StudioSurfaceElevated,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = if (isTestPlaying) AnalyzerCyan else StudioBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onCycleTestAudio)
                        .testTag("test_audio_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isTestPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = "Cycle built-in test audio signal",
                            tint = if (isTestPlaying) AnalyzerCyan else TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = testAudioSource.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTestPlaying) AnalyzerCyan else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Main Analyzer Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground)
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val w = size.width
                val h = size.height
                val centerY = h * 0.5f

                // Reference grid: 0dB line + top/bottom thirds + vertical octave guides
                drawLine(
                    color = GridLineSubtle,
                    start = Offset(0f, h * 0.25f),
                    end = Offset(w, h * 0.25f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = GridLineZero,
                    start = Offset(0f, centerY),
                    end = Offset(w, centerY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                drawLine(
                    color = GridLineSubtle,
                    start = Offset(0f, h * 0.75f),
                    end = Offset(w, h * 0.75f),
                    strokeWidth = 1.dp.toPx()
                )

                val cols = 9
                for (i in 1 until cols) {
                    val gx = (w / cols) * i
                    drawLine(
                        color = GridLineSubtle.copy(alpha = 0.65f),
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // 1. Spectrum FFT Bars
                if (visualizerMode == VisualizerMode.SPECTRUM || visualizerMode == VisualizerMode.HYBRID) {
                    val bins = frame.spectrumBins
                    if (bins.isNotEmpty()) {
                        val barSlot = w / bins.size
                        val barWidth = (barSlot * 0.68f).coerceAtLeast(2.dp.toPx())
                        val barAlpha = if (visualizerMode == VisualizerMode.HYBRID) 0.52f else 0.88f

                        bins.forEachIndexed { idx, mag ->
                            val barH = (mag.coerceIn(0f, 1f) * (h * 0.9f)).coerceAtLeast(2.dp.toPx())
                            val x = idx * barSlot + (barSlot - barWidth) * 0.5f
                            val y = h - barH

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        AnalyzerCyan.copy(alpha = barAlpha),
                                        AnalyzerCyanDeep.copy(alpha = barAlpha * 0.45f)
                                    ),
                                    startY = y,
                                    endY = h
                                ),
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            if (mag > 0.04f) {
                                drawRect(
                                    color = AnalyzerCyan.copy(alpha = (barAlpha + 0.2f).coerceAtMost(1f)),
                                    topLeft = Offset(x, (y - 2.dp.toPx()).coerceAtLeast(0f)),
                                    size = Size(barWidth, 2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // 2. Oscilloscope Waveform Trace
                if (visualizerMode == VisualizerMode.WAVEFORM || visualizerMode == VisualizerMode.HYBRID) {
                    val samples = frame.waveformSamples
                    if (samples.size >= 2) {
                        val wavePath = Path()
                        val stepX = w / (samples.size - 1).toFloat()
                        val ampScale = h * 0.42f

                        samples.forEachIndexed { i, sample ->
                            val x = i * stepX
                            val y = centerY - sample.coerceIn(-1f, 1f) * ampScale
                            if (i == 0) {
                                wavePath.moveTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevY = centerY - samples[i - 1].coerceIn(-1f, 1f) * ampScale
                                val midX = (prevX + x) * 0.5f
                                wavePath.cubicTo(midX, prevY, midX, y, x, y)
                            }
                        }

                        drawPath(
                            path = wavePath,
                            color = if (frame.isSignalLive) AnalyzerCyan else AnalyzerCyan.copy(alpha = 0.25f),
                            style = Stroke(
                                width = if (visualizerMode == VisualizerMode.WAVEFORM) 2.2.dp.toPx() else 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 3. Live EQ Frequency Response Curve Overlay
                if (animatedCurveGains.size == 10) {
                    val points = animatedCurveGains.mapIndexed { idx, db ->
                        val px = (idx / 9f) * w
                        val norm = (db - EqPresetsCatalog.MIN_GAIN_DB) /
                            (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)
                        val py = h * (1f - norm.coerceIn(0f, 1f))
                        Offset(px, py)
                    }

                    val curvePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val ctrlX = (p0.x + p1.x) * 0.5f
                            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(w, centerY)
                        lineTo(0f, centerY)
                        close()
                    }

                    val curveColor = if (isBypassed) TextMuted else SignalCoral
                    drawPath(
                        path = fillPath,
                        color = if (isBypassed) TextMuted.copy(alpha = 0.08f) else SignalCoralSoft
                    )
                    drawPath(
                        path = curvePath,
                        color = curveColor,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Band anchor nodes on curve
                    points.forEach { pt ->
                        drawCircle(
                            color = StudioBackground,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = curveColor,
                            radius = 2.2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            // Live Signal / Peak Telemetry Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (frame.isSignalLive) RouteEmerald else TextMuted)
                )
                Text(
                    text = if (frame.isSignalLive) {
                        String.format(Locale.US, "LIVE %.0f dBFS", frame.peakDb)
                    } else {
                        "EQ RESPONSE CURVE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (frame.isSignalLive) TextPrimary else TextMuted
                )
            }

            if (!frame.isSignalLive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioSurface.copy(alpha = 0.85f))
                        .clickable(onClick = onCycleTestAudio)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = AnalyzerCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Tap to play test audio or start external music",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
