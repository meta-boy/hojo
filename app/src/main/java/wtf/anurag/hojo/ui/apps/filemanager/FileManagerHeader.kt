package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.anurag.hojo.ui.theme.HojoTheme

@Composable
fun FileManagerHeader(currentPath: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    val colors = HojoTheme.colors

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(colors.contentBg)
                            .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp
                            ), // Insets handled by Scaffold usually
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(4.dp)) {
            Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(24.dp)
            )
        }

        Row(
                modifier =
                        Modifier.weight(1f)
                                .height(32.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.headerBg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = colors.subText,
                    modifier = Modifier.size(14.dp).padding(end = 6.dp)
            )
            Text(
                    text = currentPath,
                    color = colors.subText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onRefresh).padding(4.dp)) {
            Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
            )
        }
    }
}
