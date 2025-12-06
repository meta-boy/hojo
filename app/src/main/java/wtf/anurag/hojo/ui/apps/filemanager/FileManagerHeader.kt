package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerHeader(currentPath: String, onBack: () -> Unit, onRefresh: () -> Unit) {
        TopAppBar(
                title = {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(32.dp)
                                                .padding(end = 12.dp)
                                                .clip(MaterialTheme.shapes.extraLarge)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                        ) {
                                Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp).padding(end = 6.dp)
                                )
                                Text(
                                        text = currentPath,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                },
                navigationIcon = {
                        IconButton(onClick = onBack) {
                                Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                )
                        }
                },
                actions = {
                        IconButton(onClick = onRefresh) {
                                Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.onSurface
                                )
                        }
                },
                colors =
                        TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
        )
}