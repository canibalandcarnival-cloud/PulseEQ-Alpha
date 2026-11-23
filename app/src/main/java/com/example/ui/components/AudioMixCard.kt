package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioMixMode
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleIndigo
import com.example.ui.theme.AppleIndigoSoft
import com.example.ui.theme.AppleMint
import com.example.ui.theme.AppleMintSoft
import com.example.ui.theme.ApplePurple
import com.example.ui.theme.ApplePurpleSoft
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AudioMixCard(
    audioMixMode: AudioMixMode,
    isBypassed: Boolean,
    onSelectMode: (AudioMixMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = audioMixMode != AudioMixMode.STANDARD && !isBypassed
    val modeAccent = when (audioMixMode) {
        AudioMixMode.STANDARD -> TextSecondary
        AudioMixMode.IN_FRAME -> AppleBlue
        AudioMixMode.STUDIO -> AppleMint
        AudioMixMode.CINEMATIC -> ApplePurple
    }
    val modeBg = when (audioMixMode) {
        AudioMixMode.STANDARD -> AppleGlassTile
        AudioMixMode.IN_FRAME -> AppleIndigoSoft
        AudioMixMode.STUDIO -> AppleMintSoft
        AudioMixMode.CINEMATIC -> ApplePurpleSoft
    }

    val borderColor by animateColorAsState(
        targetValue = if (active) modeAccent.copy(alpha = 0.7f) else AppleGlassBorder,
        animationSpec = tween(220),
        label = "audioMixBorder"
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
                        .background(if (active) modeBg else AppleGlassTile)
                        .border(
                            1.dp,
                            if (active) modeAccent.copy(alpha = 0.65f) else AppleGlassBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = audioMixIcon(audioMixMode),
                        contentDescription = "iOS 27 Audio Mix",
                        tint = if (active) modeAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "iOS 27 Audio Mix",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Real-time Mid (L+R)/2 vocals vs Side (L-R)/2 ambience",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (active) modeAccent else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) modeBg else StudioSurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (active) modeAccent.copy(alpha = 0.6f) else AppleGlassBorder,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.5.dp)
                        .clip(CircleShape)
                        .background(if (active) modeAccent else TextMuted)
                )
                Text(
                    text = audioMixMode.badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (active) modeAccent else TextSecondary
                )
            }
        }

        // Interactive 4-Mode Selector Bar: Standard | In-Frame | Studio | Cinematic
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioBackground.copy(alpha = 0.62f))
                .border(1.dp, AppleGlassBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AudioMixMode.entries.forEach { mode ->
                val selected = mode == audioMixMode
                val pillColor = when (mode) {
                    AudioMixMode.STANDARD -> AnalyzerCyan
                    AudioMixMode.IN_FRAME -> AppleBlue
                    AudioMixMode.STUDIO -> AppleMint
                    AudioMixMode.CINEMATIC -> ApplePurple
                }
                val pillBg = when (mode) {
                    AudioMixMode.STANDARD -> pillColor.copy(alpha = 0.16f)
                    AudioMixMode.IN_FRAME -> AppleIndigo.copy(alpha = 0.24f)
                    AudioMixMode.STUDIO -> AppleMintSoft
                    AudioMixMode.CINEMATIC -> ApplePurpleSoft
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) pillBg else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (selected) pillColor.copy(alpha = 0.75f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("audio_mix_${mode.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = audioMixIcon(mode),
                            contentDescription = mode.label,
                            tint = if (selected) pillColor else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) TextPrimary else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Stem Routing Detail Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(AppleGlassTile.copy(alpha = 0.68f))
                .border(1.dp, AppleGlassBorder.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = audioMixMode.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (active) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (audioMixMode) {
                    AudioMixMode.STANDARD -> "M 1.0 • S 1.0"
                    AudioMixMode.IN_FRAME -> "M +2.2dB • S 0.36x"
                    AudioMixMode.STUDIO -> "Dry Gate • 105Hz HPF"
                    AudioMixMode.CINEMATIC -> "Voc +2dB • S 1.32x"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (active) modeAccent else SignalCoral.copy(alpha = 0.75f)
            )
        }
    }
}

private fun audioMixIcon(mode: AudioMixMode): ImageVector = when (mode) {
    AudioMixMode.STANDARD -> Icons.Rounded.GraphicEq
    AudioMixMode.IN_FRAME -> Icons.Rounded.CenterFocusStrong
    AudioMixMode.STUDIO -> Icons.Rounded.Mic
    AudioMixMode.CINEMATIC -> Icons.Rounded.Movie
}
