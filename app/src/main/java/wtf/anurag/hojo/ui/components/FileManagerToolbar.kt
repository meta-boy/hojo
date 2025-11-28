package wtf.anurag.hojo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.anurag.hojo.ui.theme.HojoTheme

@Composable
fun FileManagerToolbar(onCreateFolder: () -> Unit, onUpload: () -> Unit) {
    val colors = HojoTheme.colors

    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolbarButton(
                text = "New Folder",
                icon = Icons.Default.CreateNewFolder,
                onClick = onCreateFolder
        )
        ToolbarButton(text = "Upload", icon = Icons.Default.CloudUpload, onClick = onUpload)
    }
}

@Composable
private fun ToolbarButton(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit
) {
    val colors = HojoTheme.colors
    Row(
            modifier =
                    Modifier.clip(RoundedCornerShape(20.dp))
                            .background(colors.headerBg)
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .clickable(onClick = onClick)
                            .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
        )
        Text(text = text, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
