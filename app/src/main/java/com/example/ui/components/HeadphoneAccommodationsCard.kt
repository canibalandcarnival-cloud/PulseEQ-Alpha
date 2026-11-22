package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.HeadphoneAccommodationsProcessor
import com.example.model.AccommodationToneProfile
import com.example.model.AudioLabTelemetry
import com.example.model.EqPresetsCatalog
import com.example.model.OutputEqProfile
import com.example.model.SoftSoundIntensity
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleMint
import com.example.ui.theme.AppleMintSoft
import com.example.ui.theme.GridLineZero
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HeadphoneAccommodationsCard(
    profile: OutputEqProfile,
    telemetry: AudioLabTelemetry,
    hasAudioPermission: Boolean,
    onToggleEnabled: () -> Unit,
    onSelectToneProfile: (AccommodationToneProfile) -> Unit,
    onSelectSoftIntensity: (SoftSoundIntensity) -> Unit,
    onOpenCustomAudioSetup: () -> Unit,
    onToggleApplyToMedia: () -> Unit,
    onToggleApplyToVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = profile.accommodationsEnabled && !profile.isBypassed

    val borderColor by animateColorAsState(
        targetValue = if (active) AppleBlue.copy(alpha = 0.72f) else AppleGlassBorder,
        animationSpec = tween(220),
        label = "accCardBorder"
    )

    val curvePointsDb = remember(
        profile.accommodationsToneProfile,
        profile.accommodationsSoftIntensity
    ) {
        EqPresetsCatalog.BANDS.map { band ->
            HeadphoneAccommodationsProcessor.computeResponseDbAt(
                freqHz = band.frequencyHz.toDouble(),
                profile = profile.accommodationsToneProfile,
                intensity = profile.accommodationsSoftIntensity
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppleGlassTop, AppleGlassBottom)
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header + Subtle Active Indicator Pill + Master Toggle
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
                        .background(if (active) AppleBlueSoft else AppleGlassTile)
                        .border(
                            1.dp,
                            if (active) AppleBlue.copy(alpha = 0.7f) else AppleGlassBorder,
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = "Headphone Accommodations",
                        tint = if (active) AppleBlue else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Headphone Accommodations",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Pre-EQ Stage",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = TextSecondary
                        )
                        if (active) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AppleMintSoft)
                                    .border(1.dp, AppleMint.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .testTag("accommodations_active_pill"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(AppleMint)
                                )
                                Text(
                                    text = "ACTIVE • ${profile.accommodationsToneProfile.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AppleMint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Switch(
                checked = profile.accommodationsEnabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = AppleMint,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceElevated
                ),
                modifier = Modifier.testTag("accommodations_master_toggle")
            )
        }

        // Compact Pre-EQ Tone Contour Preview Strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioBackground.copy(alpha = 0.58f))
                .border(1.dp, AppleGlassBorder.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                val w = size.width
                val h = size.height
                val zeroY = h * 0.62f

                drawLine(
                    color = GridLineZero.copy(alpha = 0.5f),
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 0.9.dp.toPx()
                )

                val pts = curvePointsDb.mapIndexed { idx, db ->
                    val x = (idx / 9f) * w
                    // Scale -10 dB .. +10 dB inside preview strip
                    val y = (zeroY - (db / 10f) * (h * 0.52f)).coerceIn(2f, h - 2f)
                    Offset(x, y)
                }

                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val cx = (p0.x + p1.x) * 0.5f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val strokeCol = if (active) AppleBlue else TextMuted
                drawPath(
                    path = path,
                    color = strokeCol,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Text(
                text = profile.accommodationsToneProfile.detailBadge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = if (active) AppleBlue else TextMuted,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 2. Tune Audio For (3 Official Apple Tone Profiles)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TUNE AUDIO FOR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextSecondary
                )
                Text(
                    text = profile.accommodationsToneProfile.shortSummary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (active) AppleBlue else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleGlassTile)
                    .border(1.dp, AppleGlassBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccommodationToneProfile.entries.forEach { tone ->
                    val selected = tone == profile.accommodationsToneProfile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    if (active) AppleBlue.copy(alpha = 0.24f) else StudioSurfaceElevated
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) {
                                    if (active) AppleBlue else AppleGlassBorder
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectToneProfile(tone) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("acc_tone_${tone.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tone.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) {
                                if (active) TextPrimary else TextSecondary
                            } else {
                                TextMuted
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Boost Soft Sounds (3 Apple Intensity Levels: Slight 0.6x / Moderate 1.0x / Strong 1.4x)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOOST SOFT SOUNDS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextSecondary
                )
                val softSpec = profile.accommodationsSoftIntensity
                Text(
                    text = String.format(
                        Locale.US,
                        "%.0fdB • %.0f:1 • %+.1fdB • %.1fx EQ",
                        softSpec.thresholdDb,
                        softSpec.ratio,
                        softSpec.makeupGainDb,
                        softSpec.eqScale
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (active) AppleMint else TextMuted
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleGlassTile)
                    .border(1.dp, AppleGlassBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SoftSoundIntensity.entries.forEach { level ->
                    val selected = level == profile.accommodationsSoftIntensity
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    if (active) AppleMintSoft else StudioSurfaceElevated
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) {
                                    if (active) AppleMint.copy(alpha = 0.8f) else AppleGlassBorder
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectSoftIntensity(level) }
                            .padding(vertical = 7.dp, horizontal = 6.dp)
                            .testTag("acc_soft_${level.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) {
                                    if (active) AppleMint else TextPrimary
                                } else {
                                    TextMuted
                                }
                            )
                            Text(
                                text = "${level.eqScale}x • +${level.makeupGainDb}dB",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                color = if (selected) TextSecondary else TextMuted.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = active && profile.accommodationsApplyToMedia && telemetry.accommodationsGrDb < -0.1f) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Soft-Sound Dynamics Active • Gain Reduction %.1f dB",
                        telemetry.accommodationsGrDb
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = AppleMint
                )
            }
        }

        // 4. Interactive "Custom Audio Setup" Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(AppleBlueSoft)
                .border(1.dp, AppleBlue.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenCustomAudioSetup)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("custom_audio_setup_btn"),
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
                        .background(AppleBlue.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = "Custom Audio Setup",
                        tint = AppleBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Custom Audio Setup",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Guided A/B comparison • Standard vs Balanced, Vocal & Brightness",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        // 5. Media & Call/Voice Apply Toggles (Bottom of Card)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppleGlassTile.copy(alpha = 0.72f))
                .border(1.dp, AppleGlassBorder.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppleGlassSwitchRow(
                icon = Icons.Rounded.MusicNote,
                title = "Apply to Media",
                subtitle = if (profile.accommodationsApplyToMedia) {
                    "Pre-EQ & soft-sound boost active on music & video"
                } else {
                    "Media playback bypasses Headphone Accommodations"
                },
                checked = profile.accommodationsApplyToMedia,
                accentColor = AppleBlue,
                onToggle = onToggleApplyToMedia,
                testTag = "accommodations_apply_media_toggle"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppleGlassBorder.copy(alpha = 0.4f))
            )

            val voiceSubtitle = when {
                !profile.accommodationsApplyToVoice -> if (hasAudioPermission) {
                    "Off • Tap to tune voice calls & microphone input"
                } else {
                    "Requires Mic permission to process Voice/Mic input"
                }
                !profile.accommodationsEnabled -> "Armed • Enable Headphone Accommodations above"
                telemetry.voiceMicMonitorActive -> String.format(
                    Locale.US,
                    "Voice/Mic Input Active • %.0f dBFS",
                    telemetry.voiceMicLevelDb
                )
                else -> "Voice/Mic conditioning enabled"
            }

            AppleGlassSwitchRow(
                icon = Icons.Rounded.Mic,
                title = "Apply to Voice/Mic Input",
                subtitle = voiceSubtitle,
                checked = profile.accommodationsApplyToVoice,
                accentColor = AppleMint,
                onToggle = onToggleApplyToVoice,
                testTag = "accommodations_apply_voice_toggle"
            )
        }
    }
}

@Composable
private fun AppleGlassSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) accentColor else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (checked) accentColor.copy(alpha = 0.9f) else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AppleMint,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = StudioSurfaceElevated
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
