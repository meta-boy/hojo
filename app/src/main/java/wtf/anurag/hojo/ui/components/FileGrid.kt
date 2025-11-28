package wtf.anurag.hojo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.anurag.hojo.data.model.FileItem
import wtf.anurag.hojo.ui.theme.HojoTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
        files: List<FileItem>,
        isLoading: Boolean,
        onNavigate: (FileItem) -> Unit,
        onRename: (FileItem) -> Unit,
        onDelete: (FileItem) -> Unit,
        onDownload: (FileItem) -> Unit,
        errorMessage: String? = null
) {
    val colors = HojoTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        if (errorMessage != null) {
            // Show error prominently in the center
            Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(48.dp)
                )
                Text(
                        text = errorMessage,
                        color = colors.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else if (files.isEmpty() && !isLoading) {
            Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = colors.border,
                        modifier = Modifier.size(48.dp)
                )
                Text(
                        text = "Folder is empty",
                        color = colors.subText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(files) { item ->
                    var showMenu by remember { mutableStateOf(false) }

                    Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .combinedClickable(
                                                    onClick = { onNavigate(item) },
                                                    onLongClick = { showMenu = true }
                                            )
                    ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(colors.headerBg),
                                contentAlignment = Alignment.Center
                        ) {
                            val icon =
                                    when {
                                        item.type == "dir" -> Icons.Default.Folder
                                        item.name.endsWith(".jpg", true) ||
                                                item.name.endsWith(".png", true) ->
                                                Icons.Default.Image
                                        item.name.endsWith(".txt", true) ||
                                                item.name.endsWith(".json", true) ->
                                                Icons.Default.Description
                                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                    }
                            val tint = if (item.type == "dir") colors.primary else colors.text

                            Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                                text = item.name,
                                color = colors.subText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                        )

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showMenu = false
                                        onRename(item)
                                    }
                            )
                            DropdownMenuItem(
                                    text = { Text("Download") },
                                    onClick = {
                                        showMenu = false
                                        onDownload(item)
                                    }
                            )
                            DropdownMenuItem(
                                    text = { Text("Delete", color = colors.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete(item)
                                    }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                    modifier =
                            Modifier.fillMaxSize().background(colors.windowBg.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = colors.primary) }
        }
    }
}
