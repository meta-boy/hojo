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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.anurag.hojo.ui.theme.HojoTheme

data class DockAction(
        val id: String,
        val label: String,
        val subLabel: String,
        val icon: ImageVector
)

@Composable
fun AppDock(onAction: (String) -> Unit) {
        val colors = HojoTheme.colors
        val actions =
                listOf(
                        DockAction(
                                "File Manager",
                                "File Manager",
                                "Browse and manage files",
                                Icons.Default.Folder
                        ),
                        DockAction(
                                "Quick Link",
                                "Quick Link",
                                "Push URL to device",
                                Icons.Default.Link
                        ),
                        DockAction(
                                "Wallpaper Editor",
                                "Wallpaper",
                                "Customize display",
                                Icons.Default.Image
                        ),
                        DockAction(
                                "Converter",
                                "EPUB Converter",
                                "Convert books to XTC",
                                Icons.Default.Description
                        )
                )

        Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                        text = "QUICK ACTIONS",
                        color = colors.subText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.forEach { action ->
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(colors.headerBg)
                                                        .border(
                                                                1.dp,
                                                                colors.border,
                                                                RoundedCornerShape(20.dp)
                                                        )
                                                        .clickable { onAction(action.id) }
                                                        .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Single icon container (fixed): removed duplicate
                                        // placeholder box that caused
                                        // overlapping rounded shapes
                                        Box(
                                                modifier =
                                                        Modifier.size(48.dp)
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .background(Color(0x0DFFFFFF)),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = action.icon,
                                                        contentDescription = null,
                                                        tint = colors.primary,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = action.label,
                                                        color = colors.text,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(bottom = 2.dp)
                                                )
                                                Text(
                                                        text = action.subLabel,
                                                        color = colors.subText,
                                                        fontSize = 13.sp
                                                )
                                        }

                                        Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = colors.subText,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }
                }
        }
}
