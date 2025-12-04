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

@RequiresApi(Build.VERSION_CODES.Q)
@Suppress("DEPRECATION")
@Composable
fun ConverterApp(onBack: () -> Unit, connectivityViewModel: ConnectivityViewModel = viewModel()) {
         val viewModel: ConverterViewModel = hiltViewModel()

        val status by viewModel.status.collectAsState()
        val settings by viewModel.settings.collectAsState()
        val selectedFile by viewModel.selectedFile.collectAsState()
        val deviceBaseUrl by connectivityViewModel.deviceBaseUrl.collectAsState()

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

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(HojoTheme.colors.windowBg)
                                .statusBarsPadding()
        ) {
                // Header
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        IconButton(onClick = onBack) {
                                Icon(
                                        Icons.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = HojoTheme.colors.text
                                )
                        }
                        Text(
                                text = "EPUB Converter",
                                color = HojoTheme.colors.text,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                        )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(16.dp)
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
                                                                        HojoTheme.colors.primary
                                                        )
                                        ) { Text("Select EPUB File") }
                                } else {
                                        Text(
                                                text =
                                                        "Selected: ${selectedFile?.path?.split("/")?.last()}",
                                                color = HojoTheme.colors.text,
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
                                                                        HojoTheme.colors.primary
                                                        )
                                        ) { Text("Convert to XTC") }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Status Display
                                when (val s = status) {
                                        is ConverterStatus.ReadingFile ->
                                                Text(
                                                        "Reading file...",
                                                        color = HojoTheme.colors.subText
                                                )
                                        is ConverterStatus.Converting -> {
                                                LinearProgressIndicator(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = HojoTheme.colors.primary
                                                )
                                                Text(
                                                        "Converting: Page ${s.progress}" +
                                                                if (s.total > 0) " / ${s.total}"
                                                                else "",
                                                        color = HojoTheme.colors.subText,
                                                        modifier = Modifier.padding(top = 8.dp)
                                                )
                                        }
                                        is ConverterStatus.Uploading -> {
                                                CircularProgressIndicator(
                                                        color = HojoTheme.colors.primary
                                                )
                                                Text(
                                                        "Uploading to e-paper...",
                                                        color = HojoTheme.colors.subText,
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

                        // Success Dialog
                        if (status is ConverterStatus.Success) {
                                val successStatus = status as ConverterStatus.Success
                                AlertDialog(
                                        onDismissRequest = { /* Prevent dismissal without action */
                                        },
                                        title = { Text("Conversion Complete") },
                                        text = {
                                                Text(
                                                        "File converted successfully. What would you like to do?"
                                                )
                                        },
                                        confirmButton = {
                                                Button(
                                                        onClick = {
                                                                viewModel.uploadToEpaper(
                                                                        successStatus.outputFile,
                                                                        deviceBaseUrl
                                                                )
                                                        },
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor =
                                                                                HojoTheme.colors
                                                                                        .primary
                                                                )
                                                ) { Text("Upload") }
                                        },
                                        dismissButton = {
                                                Row {
                                                        TextButton(
                                                                onClick = {
                                                                        viewModel.saveToDownloads(
                                                                                successStatus
                                                                                        .outputFile
                                                                        )
                                                                },
                                                                enabled = !successStatus.isSaved
                                                        ) {
                                                                Text(
                                                                        if (successStatus.isSaved)
                                                                                "Saved"
                                                                        else "Save to Downloads"
                                                                )
                                                        }
                                                        TextButton(onClick = { onBack() }) {
                                                                Text("Exit")
                                                        }
                                                }
                                        }
                                )
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = HojoTheme.colors.text
                )

                // Font Selection
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = HojoTheme.colors.text
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
                                modifier = Modifier.background(HojoTheme.colors.windowBg)
                        ) {
                                DropdownMenuItem(
                                        text = {
                                                Text("Default Font", color = HojoTheme.colors.text)
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
                                                                color = HojoTheme.colors.text
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
                                        containerColor = HojoTheme.colors.primary
                                )
                ) { Text("Import Font") }

                // Font Size
                Text("Font Size: ${settings.fontSize}%", color = HojoTheme.colors.subText)
                Slider(
                        value = settings.fontSize.toFloat(),
                        onValueChange = { onUpdate(settings.copy(fontSize = it.toInt())) },
                        valueRange = 50f..200f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = HojoTheme.colors.primary,
                                        activeTrackColor = HojoTheme.colors.primary
                                )
                )

                // Line Height
                Text(
                        "Line Height: ${String.format(Locale.getDefault(), "%.1f", settings.lineHeight)}",
                        color = HojoTheme.colors.subText
                )
                Slider(
                        value = settings.lineHeight,
                        onValueChange = { onUpdate(settings.copy(lineHeight = it)) },
                        valueRange = 1.0f..2.5f,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = HojoTheme.colors.primary,
                                        activeTrackColor = HojoTheme.colors.primary
                                )
                )

                // Dithering
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                                checked = settings.enableDithering,
                                onCheckedChange = { onUpdate(settings.copy(enableDithering = it)) },
                                colors =
                                        CheckboxDefaults.colors(
                                                checkedColor = HojoTheme.colors.primary
                                        )
                        )
                        Text("Enable Dithering", color = HojoTheme.colors.text)
                }

                // Color Mode
                var colorModeExpanded by remember { mutableStateOf(false) }
                Text(
                        "Color Mode",
                        color = HojoTheme.colors.subText,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                                onClick = { colorModeExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = HojoTheme.colors.text
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
                                modifier = Modifier.background(HojoTheme.colors.windowBg)
                        ) {
                                DropdownMenuItem(
                                        text = {
                                                Text("Grayscale (4-level)", color = HojoTheme.colors.text)
                                        },
                                        onClick = {
                                                onUpdate(settings.copy(colorMode = XtcEncoder.ColorMode.GRAYSCALE_4))
                                                colorModeExpanded = false
                                        }
                                )
                                DropdownMenuItem(
                                        text = {
                                                Text("Monochrome (1-bit)", color = HojoTheme.colors.text)
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
