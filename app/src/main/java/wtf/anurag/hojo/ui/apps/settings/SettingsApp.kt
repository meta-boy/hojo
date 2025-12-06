package wtf.anurag.hojo.ui.apps.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import wtf.anurag.hojo.data.repository.ThemeMode
import wtf.anurag.hojo.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsApp(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName

    if (showThemeDialog) {
        AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Select Theme") },
                text = {
                    Column(modifier = Modifier.selectableGroup()) {
                        ThemeMode.entries.forEach { mode ->
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(56.dp)
                                                    .selectable(
                                                            selected = (mode == themeMode),
                                                            onClick = {
                                                                viewModel.setTheme(mode)
                                                                showThemeDialog = false
                                                            },
                                                            role = Role.RadioButton
                                                    )
                                                    .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                        selected = (mode == themeMode),
                                        onClick = null // null recommended for accessibility with
                                        // selectable
                                        )
                                Text(
                                        text =
                                                when (mode) {
                                                    ThemeMode.SYSTEM -> "System Default"
                                                    ThemeMode.LIGHT -> "Light"
                                                    ThemeMode.DARK -> "Dark"
                                                },
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
                }
        )
    }

    Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                        title = { Text("Settings") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        },
                        colors =
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            // Theme Section (Placeholder for now)
            SettingsSection(title = "Appearance") {
                SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle =
                                when (themeMode) {
                                    ThemeMode.SYSTEM -> "System Default"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                        onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = versionName,
                        onClick = {}
                )
                SettingsItem(
                        icon = Icons.Default.Link,
                        title = "GitHub Repository",
                        subtitle = "View source code",
                        onClick = {
                            val intent =
                                    Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/meta-boy/hojo")
                                    )
                            context.startActivity(intent)
                        }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun SettingsItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
        )
        Column {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
