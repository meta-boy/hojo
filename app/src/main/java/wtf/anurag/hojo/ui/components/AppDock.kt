package wtf.anurag.hojo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
                        DockAction("Tasks", "Tasks", "Manage uploads", Icons.Default.List),
                        DockAction(
                                "Converter",
                                "EPUB Converter",
                                "Convert books to XTC",
                                Icons.Default.Description
                        ),
                        DockAction(
                                "Settings",
                                "Settings",
                                "App preferences",
                                Icons.Default.Settings
                        )
                )

        Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                        text = "QUICK ACTIONS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )

                LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                ) {
                        items(actions) { action ->
                                Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .secondaryContainer,
                                                        contentColor =
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer
                                                ),
                                        modifier =
                                                Modifier.fillMaxWidth().clickable {
                                                        onAction(action.id)
                                                }
                                ) {
                                        Column(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalArrangement = Arrangement.SpaceBetween,
                                                horizontalAlignment = Alignment.Start
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .clip(
                                                                                MaterialTheme.shapes
                                                                                        .medium
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surface
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector = action.icon,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSecondaryContainer,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                Spacer(modifier = Modifier.height(32.dp))

                                                Text(
                                                        text = action.label,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer,
                                                        modifier =
                                                                Modifier.padding(
                                                                        bottom = 4.dp
                                                                )
                                                )
                                                Text(
                                                        text = action.subLabel,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSecondaryContainer,
                                                        minLines = 2,
                                                        maxLines = 2
                                                )
                                        }
                                }
                        }
                }
        }
}
