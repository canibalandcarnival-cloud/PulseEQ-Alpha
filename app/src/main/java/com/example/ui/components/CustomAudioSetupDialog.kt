package com.example.ui.components

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CustomAudioSetupUiState
import com.example.model.SetupAuditionSlot
import com.example.model.SoftSoundIntensity
import com.example.ui.theme.AppleBlue
import com.example.ui.theme.AppleBlueSoft
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTile
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.AppleMint
import com.example.ui.theme.AppleMintSoft
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CustomAudioSetupDialog(
    state: CustomAudioSetupUiState,
    onSelectAuditionSlot: (SetupAuditionSlot) -> Unit,
    onSelectIntensity: (SoftSoundIntensity) -> Unit,
    onChooseWinner: (SetupAuditionSlot) -> Unit,
    onRestart: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit
) {
    if (!state.isOpen) return

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppleGlassTop, AppleGlassBottom)
                    )
                )
                .border(1.dp, AppleGlassBorder, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppleBlueSoft)
                            .border(1.dp, AppleBlue.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Custom Audio Setup",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = if (state.isCompleted) {
                                "Recommended Profile Ready"
                            } else {
                                "Comparison ${state.stepIndex + 1} of 2 • Looped Sample Playing"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = AppleBlue
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Custom Audio Setup",
                        tint = TextSecondary
                    )
                }
            }

            LinearProgressIndicator(
                progress = {
                    when {
                        state.isCompleted -> 1f
                        state.stepIndex == 0 -> 0.4f
                        else -> 0.8f
                    }
                },
                color = if (state.isCompleted) AppleMint else AppleBlue,
                trackColor = StudioSurfaceHighlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            // Audition Indicator Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioBackground.copy(alpha = 0.6f))
                    .border(1.dp, AppleGlassBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = AppleMint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (state.isCompleted) {
                        "Listening to your match: ${state.chosenProfile.label} (${state.softIntensity.label} Soft Boost)"
                    } else {
                        "Tap Standard, A, or B below while listening to compare speech & treble clarity."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSecondary
                )
            }

            if (!state.isCompleted) {
                // Live A / B / Standard Switcher Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditionCandidateCard(
                        badge = "STD",
                        title = "Standard (Original)",
                        subtitle = "Unprocessed direct signal without Accommodations",
                        selected = state.activeAuditionSlot == SetupAuditionSlot.STANDARD,
                        accentColor = TextSecondary,
                        onClick = { onSelectAuditionSlot(SetupAuditionSlot.STANDARD) },
                        testTag = "wizard_audition_standard"
                    )

                    AuditionCandidateCard(
                        badge = "A",
                        title = "Custom Profile A • ${state.optionA.label}",
                        subtitle = "${state.optionA.shortSummary} (${state.optionA.detailBadge})",
                        selected = state.activeAuditionSlot == SetupAuditionSlot.OPTION_A,
                        accentColor = AppleBlue,
                        onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_A) },
                        testTag = "wizard_audition_a"
                    )

                    AuditionCandidateCard(
                        badge = "B",
                        title = "Custom Profile B • ${state.optionB.label}",
                        subtitle = "${state.optionB.shortSummary} (${state.optionB.detailBadge})",
                        selected = state.activeAuditionSlot == SetupAuditionSlot.OPTION_B,
                        accentColor = AppleMint,
                        onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_B) },
                        testTag = "wizard_audition_b"
                    )
                }

                // Soft Sound Intensity selector inside wizard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleGlassTile)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SoftSoundIntensity.entries.forEach { level ->
                        val selected = level == state.softIntensity
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) StudioSurfaceElevated else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) AppleBlue.copy(alpha = 0.7f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectIntensity(level) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${level.label} (${level.eqScale}x)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) TextPrimary else TextMuted
                            )
                        }
                    }
                }

                // Round Decision Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onChooseWinner(SetupAuditionSlot.OPTION_A) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("wizard_choose_a_btn")
                    ) {
                        Text(
                            text = "Use Profile A",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { onChooseWinner(SetupAuditionSlot.OPTION_B) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleMint,
                            contentColor = StudioBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("wizard_choose_b_btn")
                    ) {
                        Text(
                            text = "Use Profile B",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            } else {
                // Completion Summary + Audition Compare Standard vs Winner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleMintSoft)
                        .border(1.dp, AppleMint.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = AppleMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Best Match: ${state.chosenProfile.label}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${state.chosenProfile.shortSummary} • ${state.chosenProfile.detailBadge}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                    Text(
                        text = "Boost Soft Sounds: ${state.softIntensity.label} (${state.softIntensity.eqScale}x EQ, +${state.softIntensity.makeupGainDb} dB)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = AppleMint
                    )
                }

                // Quick Standard vs Custom toggle right on the final screen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSelectAuditionSlot(SetupAuditionSlot.STANDARD) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (state.activeAuditionSlot == SetupAuditionSlot.STANDARD) "● Standard" else "Hear Standard",
                            color = if (state.activeAuditionSlot == SetupAuditionSlot.STANDARD) TextPrimary else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_A) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (state.activeAuditionSlot != SetupAuditionSlot.STANDARD) "● Custom" else "Hear Custom",
                            color = if (state.activeAuditionSlot != SetupAuditionSlot.STANDARD) AppleMint else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestart,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Restart Wizard",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Button(
                        onClick = onApply,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wizard_apply_btn")
                    ) {
                        Text(
                            text = "Use Custom Settings",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditionCandidateCard(
    badge: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor.copy(alpha = 0.16f) else AppleGlassTile)
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) accentColor else AppleGlassBorder.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (selected) accentColor else StudioSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (selected) StudioBackground else TextSecondary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = if (selected) TextPrimary.copy(alpha = 0.85f) else TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
