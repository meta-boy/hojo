package wtf.anurag.hojo.ui.apps.filemanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FileManagerToolbar(onCreateFolder: () -> Unit, onUpload: () -> Unit) {
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
        FilledTonalButton(onClick = onClick, shape = MaterialTheme.shapes.medium) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp)
                )
        }
}
