package wtf.anurag.hojo.ui.apps.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import wtf.anurag.hojo.data.model.TaskItem
import wtf.anurag.hojo.data.model.TaskStatus
import wtf.anurag.hojo.ui.viewmodels.TasksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksApp(onBack: () -> Unit, viewModel: TasksViewModel = hiltViewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                        contract =
                                androidx.activity.result.contract.ActivityResultContracts
                                        .RequestPermission()
                ) {}

        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (
                    androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ongoing", "History")

    val ongoingTasks by viewModel.ongoingTasks.collectAsState()
    val historyTasks by viewModel.historyTasks.collectAsState()

    Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                            title = { Text("Tasks") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors =
                                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.background
                                    )
                    )
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(title) }
                            )
                        }
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTabIndex == 0) {
                TaskList(
                    tasks = ongoingTasks,
                    onCancel = { viewModel.cancelTask(it) },
                    onRetry = { viewModel.retryTask(it) }
                )
            } else {
                Column {
                    if (historyTasks.isNotEmpty()) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { viewModel.clearHistory() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear History")
                            }
                        }
                    }
                    TaskList(
                            tasks = historyTasks,
                            onCancel = {
                                // History items usually can't be cancelled
                            },
                            onRetry = { viewModel.retryTask(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskList(tasks: List<TaskItem>, onCancel: (String) -> Unit, onRetry: (String) -> Unit) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                    text = "No tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { items(tasks) { task -> TaskItemCard(task = task, onCancel = onCancel, onRetry = onRetry) } }
    }
}

@Composable
fun TaskItemCard(task: TaskItem, onCancel: (String) -> Unit, onRetry: (String) -> Unit) {
    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
            modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                ) {
                    Icon(
                            imageVector = getStatusIcon(task.status),
                            contentDescription = null,
                            tint = getStatusColor(task.status),
                            modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(text = task.fileName, style = MaterialTheme.typography.titleMedium)
                        Text(
                                text = formatStatus(task),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (task.status == TaskStatus.QUEUED || task.status == TaskStatus.UPLOADING) {
                    IconButton(onClick = { onCancel(task.id) }) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                    }
                } else if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELLED) {
                    IconButton(onClick = { onRetry(task.id) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                }
            }

            if (task.status == TaskStatus.UPLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                            text =
                                    formatBytes(task.bytesTransferred) +
                                            " / " +
                                            formatBytes(task.totalBytes),
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            text = formatBytes(task.speedBytesPerSecond) + "/s",
                            style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (task.status == TaskStatus.FAILED && task.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "Error: ${task.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun getStatusIcon(status: TaskStatus): ImageVector {
    return when (status) {
        TaskStatus.QUEUED -> Icons.Default.Pending
        TaskStatus.UPLOADING -> Icons.Default.Upload
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle
        TaskStatus.FAILED -> Icons.Default.Error
        TaskStatus.CANCELLED -> Icons.Default.Cancel
    }
}

@Composable
fun getStatusColor(status: TaskStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        TaskStatus.QUEUED -> MaterialTheme.colorScheme.primary
        TaskStatus.UPLOADING -> MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

fun formatStatus(task: TaskItem): String {
    val date = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(task.timestamp))
    return when (task.status) {
        TaskStatus.QUEUED -> "Queued • $date"
        TaskStatus.UPLOADING -> "Uploading..."
        TaskStatus.COMPLETED -> "Completed • $date"
        TaskStatus.FAILED -> "Failed • $date"
        TaskStatus.CANCELLED -> "Cancelled • $date"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
