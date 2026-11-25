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
    onChooseStep1Speech: (Boolean) -> Unit,
    onChooseStep2Music: (com.example.model.AccommodationToneProfile) -> Unit,
    onChooseStep3AndApply: (SoftSoundIntensity) -> Unit,
    onRestart: () -> Unit,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
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
                        val stepTitle = when (state.stepIndex) {
                            0 -> "Step 1 of 3 • Speech Clarity Test"
                            1 -> "Step 2 of 3 • Music Detail & Brightness"
                            else -> "Step 3 of 3 • Soft-Sound Sensitivity"
                        }
                        Text(
                            text = stepTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = AppleBlue
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (state.stepIndex > 0) {
                        IconButton(
                            onClick = onRestart,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("wizard_restart_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Restart Custom Audio Setup",
                                tint = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wizard_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Custom Audio Setup",
                            tint = TextSecondary
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = {
                    when (state.stepIndex) {
                        0 -> 0.34f
                        1 -> 0.67f
                        else -> 1.0f
                    }
                },
                color = if (state.stepIndex >= 2) AppleMint else AppleBlue,
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
                val promptText = when (state.stepIndex) {
                    0 -> "Vocal sample playing. Toggle Standard vs. Vocal Focus to see which makes speech clearer."
                    1 -> "Full-range track playing. Toggle Balanced Tone vs. Brightness for natural music detail."
                    else -> "Quiet detail sample playing (${state.chosenProfile.label}). Pick Slight or Strong boost to apply."
                }
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSecondary
                )
            }

            when (state.stepIndex) {
                // Step 1: Speech Clarity Test (Standard vs. Vocal Focus)
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditionCandidateCard(
                            badge = "1",
                            title = "Standard",
                            subtitle = "Original unprocessed vocal sample without Accommodations",
                            selected = state.activeAuditionSlot == SetupAuditionSlot.STANDARD,
                            accentColor = TextSecondary,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.STANDARD) },
                            testTag = "wizard_step1_standard"
                        )

                        AuditionCandidateCard(
                            badge = "2",
                            title = "Vocal Focus",
                            subtitle = "110Hz HPF • 280Hz -2dB • 1.5kHz +3dB • 3kHz +3.8dB speech clarity",
                            selected = state.activeAuditionSlot != SetupAuditionSlot.STANDARD,
                            accentColor = AppleMint,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_B) },
                            testTag = "wizard_step1_vocal_focus"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onChooseStep1Speech(false) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_choose_standard_btn")
                        ) {
                            Text(
                                text = "Use Standard",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onChooseStep1Speech(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleMint,
                                contentColor = StudioBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_choose_vocal_btn")
                        ) {
                            Text(
                                text = "Use Vocal Focus",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Step 2: Music Detail & Brightness Test (Balanced Tone vs. Brightness)
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditionCandidateCard(
                            badge = "A",
                            title = "Balanced Tone",
                            subtitle = com.example.model.AccommodationToneProfile.BALANCED_TONE.detailBadge,
                            selected = state.activeAuditionSlot != SetupAuditionSlot.OPTION_B,
                            accentColor = AppleBlue,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_A) },
                            testTag = "wizard_step2_balanced"
                        )

                        AuditionCandidateCard(
                            badge = "B",
                            title = "Brightness",
                            subtitle = com.example.model.AccommodationToneProfile.BRIGHTNESS.detailBadge,
                            selected = state.activeAuditionSlot == SetupAuditionSlot.OPTION_B,
                            accentColor = AppleMint,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_B) },
                            testTag = "wizard_step2_brightness"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onChooseStep2Music(com.example.model.AccommodationToneProfile.BALANCED_TONE) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleBlue,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_choose_balanced_btn")
                        ) {
                            Text(
                                text = "Use Balanced",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onChooseStep2Music(com.example.model.AccommodationToneProfile.BRIGHTNESS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleMint,
                                contentColor = StudioBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_choose_brightness_btn")
                        ) {
                            Text(
                                text = "Use Brightness",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Step 3: Soft-Sound Sensitivity (Slight vs. Strong) -> Auto-Applies Winning Combination
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleMintSoft)
                            .border(1.dp, AppleMint.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = AppleMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Selected Tone Curve: ${state.chosenProfile.label}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = state.chosenProfile.detailBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = TextSecondary
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditionCandidateCard(
                            badge = "S",
                            title = "Slight Soft-Sound Boost",
                            subtitle = "18% parallel 1.2kHz upward lift (+2.0 dB clarity gain)",
                            selected = state.activeAuditionSlot != SetupAuditionSlot.OPTION_B,
                            accentColor = AppleBlue,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_A) },
                            testTag = "wizard_step3_slight"
                        )

                        AuditionCandidateCard(
                            badge = "L",
                            title = "Strong Soft-Sound Boost",
                            subtitle = "48% parallel 1.2kHz upward lift (+5.5 dB quiet detail boost)",
                            selected = state.activeAuditionSlot == SetupAuditionSlot.OPTION_B,
                            accentColor = AppleMint,
                            onClick = { onSelectAuditionSlot(SetupAuditionSlot.OPTION_B) },
                            testTag = "wizard_step3_strong"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onChooseStep3AndApply(SoftSoundIntensity.SLIGHT) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleBlue,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_apply_slight_btn")
                        ) {
                            Text(
                                text = "Apply Slight",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onChooseStep3AndApply(SoftSoundIntensity.STRONG) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleMint,
                                contentColor = StudioBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_apply_strong_btn")
                        ) {
                            Text(
                                text = "Apply Strong",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
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
