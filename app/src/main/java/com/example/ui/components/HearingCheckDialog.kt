package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.EqPresetsCatalog
import com.example.model.HearingCheckUiState
import com.example.ui.theme.AnalyzerCyan
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
import java.util.Locale

@Composable
fun HearingCheckDialog(
    state: HearingCheckUiState,
    onHearItTap: () -> Unit,
    onSkipStep: () -> Unit,
    onCancel: () -> Unit,
    onApplyCurve: (List<Float>) -> Unit
) {
    if (!state.isActive) return

    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(StudioSurface)
                .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Hearing,
                        contentDescription = null,
                        tint = SignalCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.isCompleted) "Your Custom Audiogram EQ" else "60-Second Hearing Check",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Hearing Check",
                        tint = TextSecondary
                    )
                }
            }

            LinearProgressIndicator(
                progress = { state.progressFraction.coerceIn(0f, 1f) },
                color = if (state.isCompleted) RouteEmerald else SignalCoral,
                trackColor = StudioSurfaceHighlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            if (!state.isCompleted) {
                val freq = state.frequenciesHz.getOrElse(state.currentStepIndex) { 1000 }
                val freqLabel = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioBackground)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TONE ${state.currentStepIndex + 1} OF ${state.frequenciesHz.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnalyzerCyan
                    )
                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = String.format(Locale.US, "Rising Pure Tone: %.0f dBFS", state.currentLevelDb),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Tap 'I Hear It' as soon as the tone becomes audible.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                        color = TextMuted
                    )
                }

                Button(
                    onClick = onHearItTap,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalCoral,
                        contentColor = StudioBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("hearing_check_hear_btn")
                ) {
                    Text(
                        text = "I Hear It",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onSkipStep,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Can't Hear / Skip", color = TextSecondary, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextMuted, fontSize = 11.5.sp)
                    }
                }
            } else {
                val curve = state.generatedEqCurveDb ?: List(10) { 0f }

                // Preview generated 10-band compensation EQ curve
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioBackground)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GENERATED 10-BAND COMPENSATION CURVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = RouteEmerald
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        drawLine(
                            color = GridLineZero,
                            start = Offset(0f, h * 0.5f),
                            end = Offset(w, h * 0.5f),
                            strokeWidth = 1.dp.toPx()
                        )
                        val pts = curve.mapIndexed { idx, db ->
                            val x = (idx / 9f) * w
                            val norm = (db - EqPresetsCatalog.MIN_GAIN_DB) /
                                (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)
                            Offset(x, h * (1f - norm.coerceIn(0f, 1f)))
                        }
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 0 until pts.size - 1) {
                                val p0 = pts[i]
                                val p1 = pts[i + 1]
                                val midX = (p0.x + p1.x) * 0.5f
                                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = RouteEmerald,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        pts.forEach { pt ->
                            drawCircle(color = SignalCoral, radius = 3.2.dp.toPx(), center = pt)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EqPresetsCatalog.BANDS.forEachIndexed { idx, band ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%+.0f", curve.getOrElse(idx) { 0f }),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    color = RouteEmerald
                                )
                                Text(
                                    text = band.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onApplyCurve(curve) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RouteEmerald,
                        contentColor = StudioBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hearing_check_apply_btn")
                ) {
                    Text(
                        text = "Apply Audiogram EQ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
