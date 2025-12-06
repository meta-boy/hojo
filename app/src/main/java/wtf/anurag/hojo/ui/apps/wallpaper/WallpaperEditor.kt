package wtf.anurag.hojo.ui.apps.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.DEFAULT_FILTERS
import wtf.anurag.hojo.ui.viewmodels.INK_SCREEN_PRESET
import wtf.anurag.hojo.ui.viewmodels.WallpaperViewModel

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditor(onBack: () -> Unit, viewModel: WallpaperViewModel = hiltViewModel()) {
        val colors = HojoTheme.colors
        val context = LocalContext.current

        // State from ViewModel
        val template by viewModel.template.collectAsState()
        val imageUri by viewModel.imageUri.collectAsState()
        val filters by viewModel.filters.collectAsState()
        val saving by viewModel.saving.collectAsState()
        val bitmap by viewModel.bitmap.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()

        // Local state for cropping
        var croppingUri by remember { mutableStateOf<Uri?>(null) }
        var cropImageView: CropImageView? by remember { mutableStateOf(null) }

        val launcher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                        if (uri != null) {
                                croppingUri = uri
                        }
                }

        val takePictureLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.TakePicturePreview()
                ) { bmp: Bitmap? ->
                        if (bmp != null) {
                                // Create a temp uri
                                val file = File(context.cacheDir, "temp_cam.jpg")
                                FileOutputStream(file).use { out ->
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                }
                                croppingUri = Uri.fromFile(file)
                        }
                }

        if (croppingUri != null) {
                val isPortrait = template == "portrait"
                Scaffold(
                        topBar = {
                                TopAppBar(
                                        title = { Text("Crop Image") },
                                        navigationIcon = {
                                                IconButton(onClick = { croppingUri = null }) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Cancel",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .error
                                                        )
                                                }
                                        },
                                        actions = {
                                                IconButton(
                                                        onClick = {
                                                                cropImageView?.let { view ->
                                                                        val reqWidth =
                                                                                if (isPortrait) 480
                                                                                else 800
                                                                        val reqHeight =
                                                                                if (isPortrait) 800
                                                                                else 480
                                                                        val cropped =
                                                                                view.getCroppedImage(
                                                                                        reqWidth,
                                                                                        reqHeight
                                                                                )
                                                                        if (cropped != null) {
                                                                                val file =
                                                                                        File(
                                                                                                context.cacheDir,
                                                                                                "cropped_temp.jpg"
                                                                                        )
                                                                                FileOutputStream(
                                                                                                file
                                                                                        )
                                                                                        .use { out
                                                                                                ->
                                                                                                cropped.compress(
                                                                                                        Bitmap.CompressFormat
                                                                                                                .JPEG,
                                                                                                        100,
                                                                                                        out
                                                                                                )
                                                                                        }
                                                                                viewModel
                                                                                        .setImageUri(
                                                                                                Uri.fromFile(
                                                                                                        file
                                                                                                )
                                                                                        )
                                                                                croppingUri = null
                                                                        }
                                                                }
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Done",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }
                                        },
                                        colors =
                                                TopAppBarDefaults.topAppBarColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                        titleContentColor =
                                                                MaterialTheme.colorScheme.onSurface
                                                )
                                )
                        }
                ) { paddingValues ->
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .background(Color.Black)
                        ) {
                                AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                                CropImageView(ctx).apply {
                                                        layoutParams =
                                                                FrameLayout.LayoutParams(
                                                                        ViewGroup.LayoutParams
                                                                                .MATCH_PARENT,
                                                                        ViewGroup.LayoutParams
                                                                                .MATCH_PARENT
                                                                )
                                                        setImageUriAsync(croppingUri)
                                                        setAspectRatio(
                                                                if (isPortrait) 3 else 5,
                                                                if (isPortrait) 5 else 3
                                                        )
                                                        setFixedAspectRatio(true)
                                                        guidelines = CropImageView.Guidelines.ON
                                                        cropImageView = this
                                                }
                                        }
                                )
                        }
                }
                return
        }

        if (template == null) {
                Scaffold(
                        topBar = {
                                CenterAlignedTopAppBar(
                                        title = { Text("Wallpaper") },
                                        colors =
                                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                        titleContentColor =
                                                                MaterialTheme.colorScheme.onSurface
                                                )
                                )
                        },
                        containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Text(
                                        text = "Select Orientation",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        // Portrait
                                        Column(
                                                modifier =
                                                        Modifier.clip(MaterialTheme.shapes.medium)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .clickable {
                                                                        viewModel.setTemplate(
                                                                                "portrait"
                                                                        )
                                                                }
                                                                .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.width(60.dp)
                                                                        .height(100.dp)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .padding(bottom = 16.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "Portrait",
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                        text = "3:5",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }

                                        // Landscape
                                        Column(
                                                modifier =
                                                        Modifier.clip(MaterialTheme.shapes.medium)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .clickable {
                                                                        viewModel.setTemplate(
                                                                                "landscape"
                                                                        )
                                                                }
                                                                .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.width(100.dp)
                                                                        .height(60.dp)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .padding(bottom = 16.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "Landscape",
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                        text = "5:3",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                }
                        }
                }
                return
        }

        if (imageUri == null) {
                Scaffold(
                        topBar = {
                                LargeTopAppBar(
                                        title = { Text("Select Image") },
                                        navigationIcon = {
                                                IconButton(
                                                        onClick = { viewModel.setTemplate(null) }
                                                ) {
                                                        Icon(
                                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                                contentDescription = "Back"
                                                        )
                                                }
                                        },
                                        colors =
                                                TopAppBarDefaults.largeTopAppBarColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                        scrolledContainerColor =
                                                                MaterialTheme.colorScheme.surface
                                                )
                                )
                        },
                        containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                // Take Photo
                                Row(
                                        modifier =
                                                Modifier.width(200.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                        )
                                                        .clickable {
                                                                takePictureLauncher.launch(null)
                                                        }
                                                        .padding(
                                                                vertical = 12.dp,
                                                                horizontal = 24.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                ) {
                                        Icon(
                                                Icons.Default.CameraAlt,
                                                null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                                "Take Photo",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelLarge
                                        )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // From Album
                                Row(
                                        modifier =
                                                Modifier.width(200.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                        )
                                                        .clickable { launcher.launch("image/*") }
                                                        .padding(
                                                                vertical = 12.dp,
                                                                horizontal = 24.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                ) {
                                        Icon(
                                                Icons.Default.Image,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                                "From Album",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelLarge
                                        )
                                }
                        }
                }
                return
        }

        // Editor
        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text("Edit") },
                                navigationIcon = {
                                        IconButton(onClick = onBack) {
                                                Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back"
                                                )
                                        }
                                },
                                actions = {
                                        if (saving) {
                                                CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        } else {
                                                Text(
                                                        text = "Save",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        modifier =
                                                                Modifier.clickable {
                                                                                viewModel
                                                                                        .saveAndUpload {
                                                                                                // Success - navigate back
                                                                                                onBack()
                                                                                        }
                                                                        }
                                                                        .padding(horizontal = 16.dp)
                                                )
                                        }
                                },
                                colors =
                                        TopAppBarDefaults.topAppBarColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                titleContentColor =
                                                        MaterialTheme.colorScheme.onSurface
                                        )
                        )
                },
                containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        // Error message
                        errorMessage?.let { error ->
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer
                                                        )
                                                        .padding(12.dp)
                                ) {
                                        Text(
                                                text = error,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }

                        Column(
                                modifier =
                                        Modifier.weight(1f)
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                // Preview
                                Box(
                                        modifier =
                                                Modifier.padding(bottom = 20.dp)
                                                        .border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant
                                                        )
                                        // Shadow simulation
                                        ) {
                                        val width = 300.dp
                                        val height = if (template == "portrait") 500.dp else 180.dp

                                        if (bitmap != null) {
                                                Image(
                                                        bitmap = bitmap!!.asImageBitmap(),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(width, height),
                                                        contentScale = ContentScale.Crop,
                                                        colorFilter =
                                                                ColorFilter.colorMatrix(
                                                                        viewModel
                                                                                .generateColorMatrix(
                                                                                        filters
                                                                                )
                                                                )
                                                )
                                        }
                                }

                                // Controls
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clip(MaterialTheme.shapes.medium)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                        )
                                                        .padding(16.dp)
                                ) {
                                        // Presets
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(bottom = 20.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        text = "Reset",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier =
                                                                Modifier.clickable {
                                                                        viewModel.setFilters(
                                                                                DEFAULT_FILTERS
                                                                        )
                                                                }
                                                )
                                                Box(
                                                        modifier =
                                                                Modifier.clip(
                                                                                MaterialTheme.shapes
                                                                                        .small
                                                                        )
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        )
                                                                        .clickable {
                                                                                viewModel
                                                                                        .setFilters(
                                                                                                INK_SCREEN_PRESET
                                                                                        )
                                                                        }
                                                                        .padding(
                                                                                vertical = 6.dp,
                                                                                horizontal = 12.dp
                                                                        )
                                                ) {
                                                        Text(
                                                                text = "Ink Screen",
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onPrimary
                                                        )
                                                }
                                        }

                                        FilterSlider("Grayscale", filters.grayscale, 0f, 100f) {
                                                viewModel.setFilters(filters.copy(grayscale = it))
                                        }
                                        FilterSlider("Contrast", filters.contrast, 0f, 150f) {
                                                viewModel.setFilters(filters.copy(contrast = it))
                                        }
                                        FilterSlider("Brightness", filters.brightness, 0f, 200f) {
                                                viewModel.setFilters(filters.copy(brightness = it))
                                        }
                                        FilterSlider("Saturation", filters.saturation, 0f, 200f) {
                                                viewModel.setFilters(filters.copy(saturation = it))
                                        }
                                }
                        }
                }
        }
}

@Composable
fun FilterSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = Math.round(value).toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                        )
                }
                Slider(
                        value = value,
                        onValueChange = onChange,
                        valueRange = min..max,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor =
                                                MaterialTheme.colorScheme.surfaceVariant
                                )
                )
        }
}
