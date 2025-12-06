package wtf.anurag.hojo.ui.apps.converter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.annotation.RequiresApi
import android.os.Build
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.ConnectivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.Q)
@Suppress("DEPRECATION")
@Composable
fun ConverterApp(onBack: () -> Unit, connectivityViewModel: ConnectivityViewModel = viewModel()) {
         val viewModel: ConverterViewModel = hiltViewModel()

        val status by viewModel.status.collectAsState()
        val settings by viewModel.settings.collectAsState()
        val selectedFile by viewModel.selectedFile.collectAsState()
        val deviceBaseUrl by connectivityViewModel.deviceBaseUrl.collectAsState()

        // Show preview screen when Preview status is active
        if (status is ConverterStatus.Preview) {
                val previewStatus = status as ConverterStatus.Preview
                XtcPreviewScreen(
                        file = previewStatus.outputFile,
                        onBack = {
                                viewModel.reset()
                                onBack()
                        },
                        onUpload = {
                                viewModel.uploadToEpaper(
                                        previewStatus.outputFile,
                                        deviceBaseUrl
                                )
                        },
                        onSaveToDownloads = {
                                viewModel.saveToDownloads(previewStatus.outputFile)
                        },
                        isSaved = previewStatus.isSaved
                )
                return
        }

        val filePickerLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri -> uri?.let { viewModel.selectFile(it) } }
                )

        val fontPickerLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri -> uri?.let { viewModel.importFont(it) } }
                )

        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("EPUB Converter") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                                // File Selection
                                if (selectedFile == null) {
                                        Button(
                                                onClick = {
                                                        filePickerLauncher.launch(
                                                                arrayOf("application/epub+zip")
                                                        )
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme.primary
                                                        )
                                        ) { Text("Select EPUB File") }
                                } else {
                                        Text(
                                                text =
                                                        "Selected: ${selectedFile?.path?.split("/")?.last()}",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        // Settings
                                        val availableFonts by
                                                viewModel.availableFonts.collectAsState()
                                        SettingsSection(
                                                settings,
                                                availableFonts,
                                                onUpdate = { viewModel.updateSettings(it) },
                                                onImportFont = {
                                                        fontPickerLauncher.launch(
                                                                arrayOf(
                                                                        "font/ttf",
                                                                        "font/otf",
                                                                        "application/x-font-ttf",
                                                                        "application/x-font-otf"
                                                                )
                                                        )
                                                }
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                                onClick = { viewModel.startConversion() },
                                                enabled = status is ConverterStatus.Idle,
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme.primary
                                                        )
                                        ) { Text("Convert to XTC") }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Status Display
                                when (val s = status) {
                                        is ConverterStatus.ReadingFile ->
                                                Text(
                                                        "Reading file...",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        is ConverterStatus.Converting -> {
                                                LinearProgressIndicator(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                        "Converting: Page ${s.progress}" +
                                                                if (s.total > 0) " / ${s.total}"
                                                                else "",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 8.dp)
                                                )
                                        }
                                        is ConverterStatus.Uploading -> {
                                                CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                        "Uploading to e-paper...",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 8.dp)
                                                )
                                        }
                                        is ConverterStatus.UploadSuccess -> {
                                                Text(
                                                        "Upload Successful!",
                                                        color = Color.Green,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Button(
                                                        onClick = { viewModel.reset() },
                                                        modifier = Modifier.padding(top = 8.dp)
                                                ) { Text("Convert Another") }
                                        }
                                        is ConverterStatus.Error ->
                                                Text("Error: ${s.message}", color = Color.Red)
                                        else -> {}
                                }
                        }
                }
        }
}

@Composable
fun SettingsSection(
        settings: ConverterSettings,
        availableFonts: List<java.io.File>,
        onUpdate: (ConverterSettings) -> Unit,
        onImportFont: () -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                )

                // Font Selection
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                        ) {
                                Text(
                                        text =
                                                if (settings.fontFamily.isNotEmpty())
                                                        settings.fontFamily.split("/").last()
                                                else "Default Font"
                                )
                        }
                        DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                                DropdownMenuItem(
                                        text = {
                                                Text("Default Font", color = MaterialTheme.colorScheme.onSurface)
                                        },
                                        onClick = {
                                                onUpdate(settings.copy(fontFamily = ""))
                                                expanded = false
                                        }
                                )
                                availableFonts.forEach { font ->
                                        DropdownMenuItem(
                                                text = {
                                                        Text(
                                                                font.name,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                },
                                                onClick = {
                                                        onUpdate(
                                                                settings.copy(
                                                                        fontFamily =
                                                                                font.absolutePath
                                                                )
                                                        )
                                                        expanded = false
                                                }
                                        )
                                }
                        }
                }

                Button(
                        onClick = onImportFont,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) { Text("Import Font") }

                // Font Size
                Text("Font Size: ${settings.fontSize}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                        value = settings.fontSize.toFloat(),
                        onValueChange = { onUpdate(settings.copy(fontSize = it.toInt())) },
                        valueRange = 50f..200f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                )

                // Line Height
                Text(
                        "Line Height: ${String.format(Locale.getDefault(), "%.1f", settings.lineHeight)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                        value = settings.lineHeight,
                        onValueChange = { onUpdate(settings.copy(lineHeight = it)) },
                        valueRange = 1.0f..2.5f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                )

                // Dithering
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                                checked = settings.enableDithering,
                                onCheckedChange = { onUpdate(settings.copy(enableDithering = it)) },
                                colors =
                                        CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                        )
                        )
                        Text("Enable Dithering", color = MaterialTheme.colorScheme.onSurface)
                }

                // Color Mode
                var colorModeExpanded by remember { mutableStateOf(false) }
                Text(
                        "Color Mode",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                                onClick = { colorModeExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                        ) {
                                Text(
                                        text = when (settings.colorMode) {
                                                XtcEncoder.ColorMode.MONOCHROME -> "Monochrome (1-bit)"
                                                XtcEncoder.ColorMode.GRAYSCALE_4 -> "Grayscale (4-level)"
                                        }
                                )
                        }
                        DropdownMenu(
                                expanded = colorModeExpanded,
                                onDismissRequest = { colorModeExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                                DropdownMenuItem(
                                        text = {
                                                Text("Grayscale (4-level)", color = MaterialTheme.colorScheme.onSurface)
                                        },
                                        onClick = {
                                                onUpdate(settings.copy(colorMode = XtcEncoder.ColorMode.GRAYSCALE_4))
                                                colorModeExpanded = false
                                        }
                                )
                                DropdownMenuItem(
                                        text = {
                                                Text("Monochrome (1-bit)", color = MaterialTheme.colorScheme.onSurface)
                                        },
                                        onClick = {
                                                onUpdate(settings.copy(colorMode = XtcEncoder.ColorMode.MONOCHROME))
                                                colorModeExpanded = false
                                        }
                                )
                        }
                }
        }
}
