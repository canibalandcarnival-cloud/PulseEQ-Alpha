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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedDeviceState
import com.example.model.OutputTarget
import com.example.ui.theme.RouteEmerald
import com.example.ui.theme.RouteEmeraldSoft
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SignalCoralSoft
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OutputDeviceSelector(
    selectedTarget: OutputTarget,
    connectedDevices: ConnectedDeviceState,
    autoSwitchByDevice: Boolean,
    onSelectTarget: (OutputTarget) -> Unit,
    onToggleAutoSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeName = when (connectedDevices.activeRoute) {
                OutputTarget.BLUETOOTH -> connectedDevices.bluetoothName ?: "Bluetooth Output"
                OutputTarget.HEADPHONES -> connectedDevices.headphonesName ?: "Wired / USB Audio"
                OutputTarget.SPEAKER -> "Internal Phone Speaker"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(RouteEmerald)
                )
                Text(
                    text = "ROUTED: $activeName",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (autoSwitchByDevice) RouteEmeraldSoft else StudioSurfaceElevated,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggleAutoSwitch)
                    .testTag("auto_switch_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Auto switch profile on device change",
                        tint = if (autoSwitchByDevice) RouteEmerald else TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (autoSwitchByDevice) "Auto" else "Manual",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (autoSwitchByDevice) RouteEmerald else TextMuted
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutputTarget.entries.forEach { target ->
                val isSelected = target == selectedTarget
                val isHardwareConnected = when (target) {
                    OutputTarget.SPEAKER -> connectedDevices.activeRoute == OutputTarget.SPEAKER
                    OutputTarget.HEADPHONES -> connectedDevices.headphonesConnected
                    OutputTarget.BLUETOOTH -> connectedDevices.bluetoothConnected
                }

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) StudioSurfaceHighlight else StudioSurfaceElevated,
                    animationSpec = tween(180),
                    label = "outputBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) SignalCoral else StudioBorder,
                    animationSpec = tween(180),
                    label = "outputBorder"
                )

                val icon: ImageVector = when (target) {
                    OutputTarget.SPEAKER -> Icons.Rounded.SpeakerPhone
                    OutputTarget.HEADPHONES -> Icons.Rounded.Headphones
                    OutputTarget.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelectTarget(target) }
                        .padding(horizontal = 8.dp, vertical = 9.dp)
                        .testTag("output_tab_${target.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = target.label,
                            tint = if (isSelected) SignalCoral else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (isHardwareConnected) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(RouteEmerald)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = target.shortLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
