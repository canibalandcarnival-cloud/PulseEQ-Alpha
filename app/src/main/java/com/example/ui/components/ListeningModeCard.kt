package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeOff
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioLabTelemetry
import com.example.model.ListeningMode
import com.example.model.ListeningModeSpecs
import com.example.model.OutputTarget
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AnalyzerCyanSoft
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBorderBright
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleIndigo
import com.example.ui.theme.AppleIndigoSoft
import com.example.ui.theme.AppleMint
import com.example.ui.theme.AppleMintSoft
import com.example.ui.theme.FaderCapWhite
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningModeCard(
    listeningMode: ListeningMode,
    ambientPassThroughPercent: Float,
    conversationAwarenessEnabled: Boolean,
    isMasterBypassed: Boolean,
    outputTarget: OutputTarget,
    hasAudioPermission: Boolean,
    telemetry: AudioLabTelemetry,
    onSelectMode: (ListeningMode) -> Unit,
    onAmbientPassThroughChange: (Float) -> Unit,
    onToggleConversationAwareness: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSpeakerRoute = outputTarget == OutputTarget.SPEAKER
    val modeAccent = when (listeningMode) {
        ListeningMode.OFF -> TextSecondary
        ListeningMode.TRANSPARENCY -> AnalyzerCyan
        ListeningMode.ADAPTIVE -> AppleBlue
        ListeningMode.NOISE_CANCELLATION -> AppleIndigo
    }
    val effectiveAccent = if (isMasterBypassed) TextMuted else modeAccent

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isMasterBypassed -> AppleGlassBorder
            listeningMode == ListeningMode.OFF && !conversationAwarenessEnabled -> AppleGlassBorder
            else -> effectiveAccent.copy(alpha = 0.65f)
        },
        animationSpec = tween(220),
        label = "listeningModeBorder"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "listeningModeAnim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPulse"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
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
            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Header: Title + iOS 27 Active Mode Status Pill
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (listeningMode == ListeningMode.OFF || isMasterBypassed) {
                                AppleGlassTile
                            } else {
                                effectiveAccent.copy(alpha = 0.2f)
                            }
                        )
                        .border(
                            1.dp,
                            if (listeningMode == ListeningMode.OFF || isMasterBypassed) {
                                AppleGlassBorder
                            } else {
                                effectiveAccent.copy(alpha = 0.6f)
                            },
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ListeningModeAnimatedGlyph(
                        mode = listeningMode,
                        isSelected = listeningMode != ListeningMode.OFF && !isMasterBypassed,
                        tint = if (listeningMode == ListeningMode.OFF || isMasterBypassed) TextSecondary else effectiveAccent,
                        pulsePhase = pulsePhase,
                        iconSizeDp = 18
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Mode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "iOS 27 Control Center • ${listeningMode.subtitle}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Route & Live Status Pill
            val statusLabel = when {
                isMasterBypassed -> "BYPASSED"
                isSpeakerRoute && (listeningMode == ListeningMode.TRANSPARENCY || listeningMode == ListeningMode.ADAPTIVE) ->
                    "MIC GUARDED"
                listeningMode == ListeningMode.OFF -> "DIRECT"
                else -> listeningMode.shortLabel.uppercase(Locale.US)
            }
            val statusColor = when {
                isMasterBypassed -> TextMuted
                isSpeakerRoute && (listeningMode == ListeningMode.TRANSPARENCY || listeningMode == ListeningMode.ADAPTIVE) ->
                    WarmAmber
                listeningMode == ListeningMode.OFF -> TextSecondary
                else -> effectiveAccent
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
                    .testTag("listening_mode_status_badge")
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor
                )
            }
        }

        // Apple Frosted-Glass Control Center Listening Mode Pill Bar
        ControlCenterListeningPillBar(
            selectedMode = listeningMode,
            isMasterBypassed = isMasterBypassed,
            pulsePhase = pulsePhase,
            auraAlpha = auraAlpha,
            onSelectMode = onSelectMode
        )

        // iOS 27 "Conversation Awareness" Pill Switch right below the Listening Mode bar
        ConversationAwarenessPillSwitch(
            enabled = conversationAwarenessEnabled,
            speechDetected = telemetry.speechDetected,
            duckingGainLinear = telemetry.duckingGainLinear,
            isSpeakerRoute = isSpeakerRoute,
            hasAudioPermission = hasAudioPermission,
            onToggle = onToggleConversationAwareness
        )

        // Safe Feedback Protection Banner
        SafeFeedbackProtectionBadge(
            outputTarget = outputTarget,
            listeningMode = listeningMode,
            conversationAwarenessEnabled = conversationAwarenessEnabled,
            feedbackProtectionActive = telemetry.feedbackProtectionActive || isSpeakerRoute,
            liveMicRunning = telemetry.liveMicPassThroughRunning
        )

        // Mic permission helper prompt when Transparency/Adaptive or Conversation Awareness needs RECORD_AUDIO
        AnimatedVisibility(
            visible = !hasAudioPermission &&
                (listeningMode == ListeningMode.TRANSPARENCY || listeningMode == ListeningMode.ADAPTIVE)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarmAmber.copy(alpha = 0.14f))
                    .border(1.dp, WarmAmber.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onRequestAudioPermission)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("listening_mode_grant_mic_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Microphone permission required for ambient pass-through",
                        tint = WarmAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Microphone access required for live ${listeningMode.label} ambient sensing",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = WarmAmber
                )
            }
        }

        // Transparency Mode Controls: Ambient Pass-Through Level (0%–100%) + HPF / Vocal Presence Chips
        AnimatedVisibility(visible = listeningMode == ListeningMode.TRANSPARENCY) {
            val pctInt = (ambientPassThroughPercent.coerceIn(0f, 1f) * 100f).roundToInt()
            val liveBlendPct = (telemetry.listeningPassThroughGain * 100f).roundToInt()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioBackground.copy(alpha = 0.72f))
                    .border(1.dp, AnalyzerCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ambient Pass-Through Level",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isSpeakerRoute) {
                            "$pctInt% (Muted on Speaker)"
                        } else if (telemetry.liveMicPassThroughRunning) {
                            "$pctInt% • Live $liveBlendPct%"
                        } else {
                            "$pctInt%"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSpeakerRoute) WarmAmber else AnalyzerCyan
                    )
                }

                Slider(
                    value = ambientPassThroughPercent.coerceIn(0f, 1f),
                    onValueChange = onAmbientPassThroughChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = AnalyzerCyan,
                        activeTrackColor = AnalyzerCyan,
                        inactiveTrackColor = StudioSurfaceHighlight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("ambient_pass_through_slider")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ListeningSpecChip("220 Hz Wind/Rumble HPF", AnalyzerCyan)
                    ListeningSpecChip("2.5 kHz +1.5 dB Vocal Air", AppleMint)
                    ListeningSpecChip("0 dB Reference Music", TextSecondary)
                }
            }
        }

        // Adaptive (iOS 27 Smart Blend) Real-Time Telemetry Panel
        AnimatedVisibility(visible = listeningMode == ListeningMode.ADAPTIVE) {
            val rumbleDucked = telemetry.adaptiveRumbleDuckActive
            val blendPct = if (isSpeakerRoute) {
                0
            } else if (telemetry.liveMicPassThroughRunning) {
                (telemetry.listeningPassThroughGain * 100f).roundToInt()
            } else {
                25
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioBackground.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        if (rumbleDucked) WarmAmber.copy(alpha = 0.65f) else AppleBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (rumbleDucked) {
                            "Loud <400Hz Rumble Detected • Auto-Ducking"
                        } else {
                            "Quiet Environment • 25% Ambient Awareness"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        color = if (rumbleDucked) WarmAmber else AppleBlue,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSpeakerRoute) "Guard 0%" else "$blendPct% Blend",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSpeakerRoute) WarmAmber else TextPrimary
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ListeningSpecChip(
                        text = String.format(Locale.US, "Room %.0f dBFS", telemetry.ambientNoiseRmsDb),
                        color = AppleBlue
                    )
                    ListeningSpecChip(
                        text = String.format(Locale.US, "<400Hz %.0f dBFS", telemetry.ambientRumbleRmsDb),
                        color = if (rumbleDucked) WarmAmber else TextSecondary
                    )
                    ListeningSpecChip(
                        text = if (rumbleDucked) "120 Hz +2.0 dB Warmth ON" else "Quiet 25% Blend Ready",
                        color = if (rumbleDucked) RouteEmerald else AppleMint
                    )
                }
            }
        }

        // Noise Cancellation (Active Isolation Focus) Curve Summary
        AnimatedVisibility(visible = listeningMode == ListeningMode.NOISE_CANCELLATION) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioBackground.copy(alpha = 0.72f))
                    .border(1.dp, AppleIndigo.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Isolation Focus Curve",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "Mic Muted (0%)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AppleIndigo
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ListeningSpecChip("110 Hz +2.0 dB Warmth", AppleIndigo)
                    ListeningSpecChip("320 Hz -2.0 dB Mud Cut", AnalyzerCyan)
                    ListeningSpecChip("2.2 kHz +1.5 dB Presence", AppleMint)
                }
            }
        }
    }
}

/**
 * Apple Control Center frosted-glass horizontal capsule selector with animated sliding thumb & animated icons.
 */
@Composable
private fun ControlCenterListeningPillBar(
    selectedMode: ListeningMode,
    isMasterBypassed: Boolean,
    pulsePhase: Float,
    auraAlpha: Float,
    onSelectMode: (ListeningMode) -> Unit
) {
    val modes = ListeningMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(41.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        AppleGlassTile,
                        StudioSurfaceElevated.copy(alpha = 0.85f),
                        AppleGlassTile
                    )
                )
            )
            .border(1.dp, AppleGlassBorderBright, RoundedCornerShape(41.dp))
            .padding(5.dp)
            .testTag("listening_mode_pill_bar")
    ) {
        val segmentWidth = maxWidth / modes.size
        val thumbOffsetX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "ccThumbSlide"
        )
        val thumbTint by animateColorAsState(
            targetValue = when {
                isMasterBypassed -> StudioSurfaceHighlight
                selectedMode == ListeningMode.OFF -> FaderCapWhite.copy(alpha = 0.22f)
                selectedMode == ListeningMode.TRANSPARENCY -> FaderCapWhite
                selectedMode == ListeningMode.ADAPTIVE -> FaderCapWhite
                selectedMode == ListeningMode.NOISE_CANCELLATION -> FaderCapWhite
                else -> FaderCapWhite
            },
            animationSpec = tween(220),
            label = "ccThumbTint"
        )

        // Sliding Apple Control Center Capsule Highlight
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(36.dp))
                .background(thumbTint)
                .border(
                    width = 1.dp,
                    color = if (selectedMode == ListeningMode.OFF || isMasterBypassed) {
                        AppleGlassBorderBright
                    } else {
                        FaderCapWhite
                    },
                    shape = RoundedCornerShape(36.dp)
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = mode == selectedMode
                val isLightCapsule = isSelected && mode != ListeningMode.OFF && !isMasterBypassed
                val iconTint = when {
                    isLightCapsule -> StudioBackground
                    isSelected -> TextPrimary
                    else -> TextSecondary
                }
                val labelColor = when {
                    isLightCapsule -> StudioBackground
                    isSelected -> TextPrimary
                    else -> TextSecondary
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(36.dp))
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("listening_mode_${mode.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected && mode != ListeningMode.OFF && !isMasterBypassed) {
                            val ringColor = when (mode) {
                                ListeningMode.TRANSPARENCY -> AnalyzerCyan
                                ListeningMode.ADAPTIVE -> AppleBlue
                                ListeningMode.NOISE_CANCELLATION -> AppleIndigo
                                ListeningMode.OFF -> TextMuted
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .scale(pulsePhase)
                                    .clip(CircleShape)
                                    .background(ringColor.copy(alpha = auraAlpha))
                            )
                        }
                        ListeningModeAnimatedGlyph(
                            mode = mode,
                            isSelected = isSelected && !isMasterBypassed,
                            tint = iconTint,
                            pulsePhase = pulsePhase,
                            iconSizeDp = 20
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = mode.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningModeAnimatedGlyph(
    mode: ListeningMode,
    isSelected: Boolean,
    tint: Color,
    pulsePhase: Float,
    iconSizeDp: Int
) {
    val icon = when (mode) {
        ListeningMode.OFF -> Icons.Rounded.VolumeOff
        ListeningMode.TRANSPARENCY -> Icons.Rounded.Hearing
        ListeningMode.ADAPTIVE -> Icons.Rounded.AutoAwesome
        ListeningMode.NOISE_CANCELLATION -> Icons.Rounded.NoiseControlOff
    }
    val animScale by animateFloatAsState(
        targetValue = if (isSelected && mode != ListeningMode.OFF) {
            if (mode == ListeningMode.NOISE_CANCELLATION) 1.0f else pulsePhase
        } else {
            1.0f
        },
        animationSpec = tween(220),
        label = "glyphScale"
    )

    Icon(
        imageVector = icon,
        contentDescription = "${mode.label} Listening Mode",
        tint = tint,
        modifier = Modifier
            .size(iconSizeDp.dp)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            }
    )
}

/**
 * iOS 27 "Conversation Awareness" pill switch right beneath the Listening Mode bar.
 */
@Composable
private fun ConversationAwarenessPillSwitch(
    enabled: Boolean,
    speechDetected: Boolean,
    duckingGainLinear: Float,
    isSpeakerRoute: Boolean,
    hasAudioPermission: Boolean,
    onToggle: () -> Unit
) {
    val duckPct = ((1f - duckingGainLinear) * 100f).roundToInt().coerceIn(0, 75)
    val accent = when {
        speechDetected -> SignalCoral
        enabled -> AppleMint
        else -> TextSecondary
    }
    val subtitle = when {
        !enabled && !hasAudioPermission ->
            "Tap to grant Mic permission • Auto-ducks -75% on speech"
        !enabled ->
            "Detects speaking (300Hz–3kHz) • Lowers music -75%, restores 2s later"
        isSpeakerRoute && speechDetected ->
            "Speaking detected • Music ducked -$duckPct% (Speaker mic muted)"
        speechDetected ->
            "Voice detected • Music -$duckPct% + External Vocal Clarity ON"
        isSpeakerRoute ->
            "300Hz–3kHz detector active • External mic pass-through guarded on Speaker"
        else ->
            "Active • Ducks -75% + clarifies voices, restores 2s after speech"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (enabled) {
                    accent.copy(alpha = 0.14f)
                } else {
                    AppleGlassTile.copy(alpha = 0.72f)
                }
            )
            .border(
                width = 1.dp,
                color = if (enabled) accent.copy(alpha = 0.6f) else AppleGlassBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("listening_conversation_awareness_toggle"),
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
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (enabled) accent.copy(alpha = 0.22f) else StudioSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = "Conversation Awareness",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conversation Awareness",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (enabled) accent.copy(alpha = 0.95f) else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioBackground,
                checkedTrackColor = accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = StudioSurfaceElevated
            ),
            modifier = Modifier.testTag("listening_conversation_awareness_switch")
        )
    }
}

@Composable
private fun SafeFeedbackProtectionBadge(
    outputTarget: OutputTarget,
    listeningMode: ListeningMode,
    conversationAwarenessEnabled: Boolean,
    feedbackProtectionActive: Boolean,
    liveMicRunning: Boolean
) {
    val isSpeaker = outputTarget == OutputTarget.SPEAKER
    val needsMicPassThrough = listeningMode == ListeningMode.TRANSPARENCY ||
        listeningMode == ListeningMode.ADAPTIVE ||
        conversationAwarenessEnabled

    val badgeColor = when {
        isSpeaker && needsMicPassThrough -> WarmAmber
        isSpeaker -> RouteEmerald
        liveMicRunning -> AnalyzerCyan
        else -> RouteEmerald
    }

    val badgeIcon = when {
        isSpeaker -> Icons.Rounded.Security
        else -> Icons.Rounded.Headphones
    }

    val badgeTitle = when {
        isSpeaker && needsMicPassThrough ->
            "Safe Feedback Protection Engaged — Mic Pass-Through Muted on Phone Speaker"
        isSpeaker ->
            "Safe Feedback Protection Ready — Switch to Headphones or Bluetooth to unlock live pass-through"
        liveMicRunning ->
            "Live Ambient Pass-Through Unlocked on ${outputTarget.shortLabel} (${ListeningModeSpecs.TRANSPARENCY_HPF_HZ.toInt()} Hz HPF Active)"
        else ->
            "Live Ambient Pass-Through Unlocked on ${outputTarget.shortLabel}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(1.dp, badgeColor.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("safe_feedback_protection_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = badgeIcon,
            contentDescription = if (feedbackProtectionActive) {
                "Safe Feedback Protection active"
            } else {
                "Headphones ambient pass-through unlocked"
            },
            tint = badgeColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = badgeTitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isSpeaker && needsMicPassThrough) WarmAmber else TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ListeningSpecChip(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}
