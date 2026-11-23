package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqPreset
import com.example.model.EqPresetsCatalog
import com.example.model.PresetGroup
import com.example.ui.theme.AnalyzerCyan
import com.example.ui.theme.AppleGlassBorder
import com.example.ui.theme.AppleGlassBottom
import com.example.ui.theme.AppleGlassTop
import com.example.ui.theme.GridLineZero
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
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

@Composable
fun MasterActionBar(
    isBypassed: Boolean,
    activePresetName: String,
    onToggleBypass: () -> Unit,
    onReset: () -> Unit,
    onOpenPresetsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bypass / Active Toggle
        val bypassBg by animateColorAsState(
            targetValue = if (isBypassed) StudioSurfaceElevated else RouteEmeraldSoft,
            animationSpec = tween(180),
            label = "bypassBg"
        )
        val bypassBorder by animateColorAsState(
            targetValue = if (isBypassed) StudioBorder else RouteEmerald,
            animationSpec = tween(180),
            label = "bypassBorder"
        )

        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bypassBg)
                .border(1.dp, bypassBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleBypass)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("bypass_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (isBypassed) "Enable Equalizer" else "Bypass Equalizer",
                tint = if (isBypassed) TextMuted else RouteEmerald,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isBypassed) "Bypassed" else "EQ On",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = if (isBypassed) TextSecondary else RouteEmerald
            )
        }

        // Preset Picker Trigger (shows active preset + opens all 21 presets sheet)
        Row(
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppleGlassTop, AppleGlassBottom)
                    )
                )
                .border(1.dp, AppleGlassBorder, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenPresetsSheet)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("presets_sheet_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Open EQ Presets",
                    tint = SignalCoral,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = activePresetName,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${EqPresetsCatalog.PRESETS.size} Presets",
                style = MaterialTheme.typography.labelSmall,
                color = AnalyzerCyan
            )
        }

        // Reset to Flat
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioSurfaceElevated)
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("reset_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = "Reset EQ to Flat 0 dB",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Reset",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = TextPrimary
            )
        }
    }
}

@Composable
fun PresetQuickStrip(
    selectedPresetId: String,
    onSelectPreset: (EqPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(EqPresetsCatalog.ALL_WITH_FLAT, key = { it.id }) { preset ->
                val isSelected = preset.id == selectedPresetId
                PresetQuickChip(
                    preset = preset,
                    isSelected = isSelected,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetQuickChip(
    preset: EqPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(106.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    Brush.verticalGradient(listOf(StudioSurfaceHighlight, AppleGlassBottom))
                } else {
                    Brush.verticalGradient(listOf(AppleGlassTop, AppleGlassBottom))
                }
            )
            .border(
                width = 1.dp,
                color = if (isSelected) SignalCoral else AppleGlassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("preset_chip_${preset.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiniEqCurvePreview(
            gainsDb = preset.gainsDb,
            isSelected = isSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (preset.id == EqPresetsCatalog.LATE_NIGHT_ID) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = WarmAmber,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MiniEqCurvePreview(
    gainsDb: List<Float>,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f

        drawLine(
            color = GridLineZero.copy(alpha = 0.65f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 0.8.dp.toPx()
        )

        if (gainsDb.size == 10) {
            val points = gainsDb.mapIndexed { idx, db ->
                val px = (idx / 9f) * w
                val norm = ((db - EqPresetsCatalog.MIN_GAIN_DB) /
                    (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)).coerceIn(0f, 1f)
                val py = h * (1f - norm)
                Offset(px, py)
            }

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) * 0.5f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }

            drawPath(
                path = path,
                color = if (isSelected) SignalCoral else AnalyzerCyan,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsBrowserSheet(
    selectedPresetId: String,
    onSelectPreset: (EqPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedGroup by remember { mutableStateOf(PresetGroup.ALL) }

    val filteredPresets = remember(selectedGroup) {
        if (selectedGroup == PresetGroup.ALL) {
            EqPresetsCatalog.ALL_WITH_FLAT
        } else {
            EqPresetsCatalog.PRESETS.filter { it.group == selectedGroup }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EQ Presets Library",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "21 studio curves from 3 reference sets + Late Night DRC + Flat",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                }
            }

            // Group Filter Pills (All / Image 1 / Image 2 / Image 3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetGroup.entries.forEach { group ->
                    val active = group == selectedGroup
                    val count = when (group) {
                        PresetGroup.ALL -> EqPresetsCatalog.ALL_WITH_FLAT.size
                        else -> EqPresetsCatalog.PRESETS.count { it.group == group }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) SignalCoralSoft else StudioSurfaceElevated,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                width = 1.dp,
                                color = if (active) SignalCoral else StudioBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { selectedGroup = group }
                            .testTag("preset_filter_${group.name.lowercase()}")
                    ) {
                        Text(
                            text = "${group.label} ($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) SignalCoral else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    val isSelected = preset.id == selectedPresetId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) StudioSurfaceHighlight else StudioBackground)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) SignalCoral else StudioBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectPreset(preset)
                                onDismiss()
                            }
                            .padding(12.dp)
                            .testTag("preset_card_${preset.id}"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (preset.id == EqPresetsCatalog.LATE_NIGHT_ID) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = WarmAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(SignalCoral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else if (preset.group != PresetGroup.ALL) {
                                Text(
                                    text = preset.group.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    color = TextMuted
                                )
                            }
                        }

                        // Mini 10-bar + curve preview inspired by the reference images
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSurface)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            PresetMiniSliderGrid(
                                gainsDb = preset.gainsDb,
                                isSelected = isSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetMiniSliderGrid(
    gainsDb: List<Float>,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f

        drawLine(
            color = GridLineZero.copy(alpha = 0.7f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 0.8.dp.toPx()
        )

        val step = w / 10f
        gainsDb.forEachIndexed { idx, db ->
            val cx = step * idx + step * 0.5f
            drawLine(
                color = StudioBorder,
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 1.dp.toPx()
            )

            val norm = ((db - EqPresetsCatalog.MIN_GAIN_DB) /
                (EqPresetsCatalog.MAX_GAIN_DB - EqPresetsCatalog.MIN_GAIN_DB)).coerceIn(0f, 1f)
            val py = h * (1f - norm)
            drawLine(
                color = if (isSelected) SignalCoral else SignalCoral.copy(alpha = 0.75f),
                start = Offset(cx, centerY),
                end = Offset(cx, py),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = TextPrimary,
                radius = 2.4.dp.toPx(),
                center = Offset(cx, py)
            )
        }
    }
}
