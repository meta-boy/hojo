package wtf.anurag.hojo.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.anurag.hojo.data.model.StorageStatus
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.utils.FormatUtils

@Composable
fun StatusWidget(
        isConnected: Boolean,
        isConnecting: Boolean,
        storageStatus: StorageStatus?,
        onConnect: (Boolean) -> Unit
) {
    val colors = HojoTheme.colors

    val usedPercentage =
            if (storageStatus != null && storageStatus.totalBytes > 0) {
                (storageStatus.usedBytes.toFloat() / storageStatus.totalBytes.toFloat()) * 100f
            } else {
                0f
            }

    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.headerBg)
                            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                            .padding(20.dp)
    ) {
        // Header
        Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                )
                Text(
                        text = "Device Status",
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                )
            }
            Box(
                    modifier =
                            Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(
                                            if (isConnected) Color(0x1A22C55E)
                                            else Color(0x1AEF4444)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                        text = if (isConnected) "Online" else "Offline",
                        color = if (isConnected) colors.success else colors.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Connection Status
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                    modifier = Modifier.size(32.dp).padding(end = 12.dp),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector =
                                if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = colors.subText,
                        modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "Connection",
                        color = colors.subText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                        text = if (isConnected) "Connected via WiFi" else "Not Connected",
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                )
            }
            if (!isConnected) {
                Box(
                        modifier =
                                Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(colors.primary)
                                        .clickable(enabled = !isConnecting) { onConnect(false) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                            text = if (isConnecting) "..." else "Connect",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Status
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                    modifier = Modifier.size(32.dp).padding(end = 12.dp),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = colors.subText,
                        modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Storage", color = colors.subText, fontSize = 12.sp)
                    Text(
                            text =
                                    if (storageStatus != null)
                                            "${FormatUtils.formatBytes(storageStatus.usedBytes)} / ${FormatUtils.formatBytes(storageStatus.totalBytes)}"
                                    else "Unknown",
                            color = colors.text,
                            fontSize = 12.sp
                    )
                }

                // Progress Bar
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colors.border)
                ) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth(usedPercentage / 100f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(colors.primary)
                    )
                }
            }
        }

        // Footer Info
        if (storageStatus?.version != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .border(
                                            0.dp,
                                            Color.Transparent
                                    ) // Hack to simulate border top? No, just use a Divider or
                    // Spacer with background
                    ) {
                // Border top simulation
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.border)
                                        .align(Alignment.TopCenter)
                )
                Text(
                        text = "Firmware: ${storageStatus.version}",
                        color = colors.subText,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp)
                )
            }
        }
    }
}
