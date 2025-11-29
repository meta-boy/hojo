package wtf.anurag.hojo.ui.apps.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.DEFAULT_FILTERS
import wtf.anurag.hojo.ui.viewmodels.INK_SCREEN_PRESET
import wtf.anurag.hojo.ui.viewmodels.WallpaperViewModel

@Composable
fun WallpaperEditor(
        onBack: () -> Unit,
        baseUrl: String = "http://192.168.3.3",
        viewModel: WallpaperViewModel = viewModel()
) {
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
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(
                                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                                factory = { ctx ->
                                        CropImageView(ctx).apply {
                                                layoutParams =
                                                        FrameLayout.LayoutParams(
                                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                                ViewGroup.LayoutParams.MATCH_PARENT
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

                        // Crop Controls
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(colors.windowBg)
                                                .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                IconButton(onClick = { croppingUri = null }) {
                                        Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = colors.error
                                        )
                                }
                                Text(
                                        "Crop Image",
                                        color = colors.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                )
                                IconButton(
                                        onClick = {
                                                cropImageView?.let { view ->
                                                        val reqWidth = if (isPortrait) 480 else 800
                                                        val reqHeight = if (isPortrait) 800 else 480
                                                        val cropped =
                                                                view.getCroppedImage(
                                                                        reqWidth,
                                                                        reqHeight
                                                                )
                                                        if (cropped != null) {
                                                                // Save cropped bitmap to file to
                                                                // get URI
                                                                // Or just pass bitmap to ViewModel
                                                                // if it supports it
                                                                // ViewModel currently takes URI and
                                                                // loads bitmap.
                                                                // Let's modify ViewModel to accept
                                                                // Bitmap or just save to temp file
                                                                // here.

                                                                // Saving to temp file
                                                                val file =
                                                                        File(
                                                                                context.cacheDir,
                                                                                "cropped_temp.jpg"
                                                                        )
                                                                FileOutputStream(file).use { out ->
                                                                        cropped.compress(
                                                                                Bitmap.CompressFormat
                                                                                        .JPEG,
                                                                                100,
                                                                                out
                                                                        )
                                                                }
                                                                viewModel.setImageUri(
                                                                        Uri.fromFile(file)
                                                                )
                                                                croppingUri = null
                                                        }
                                                }
                                        }
                                ) {
                                        Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Done",
                                                tint = colors.primary
                                        )
                                }
                        }
                }
                return
        }

        if (template == null) {
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(colors.windowBg)
                                        .statusBarsPadding()
                ) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                        ) {
                                Text(
                                        text = "Wallpaper",
                                        color = colors.text,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Column(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Text(
                                        text = "Select Orientation",
                                        color = colors.subText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        // Portrait
                                        Column(
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(24.dp))
                                                                .background(colors.headerBg)
                                                                .border(
                                                                        1.dp,
                                                                        colors.border,
                                                                        RoundedCornerShape(24.dp)
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
                                                                                colors.primary,
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .padding(bottom = 16.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "Portrait",
                                                        color = colors.text,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 16.sp
                                                )
                                                Text(
                                                        text = "3:5",
                                                        color = colors.subText,
                                                        fontSize = 13.sp
                                                )
                                        }

                                        // Landscape
                                        Column(
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(24.dp))
                                                                .background(colors.headerBg)
                                                                .border(
                                                                        1.dp,
                                                                        colors.border,
                                                                        RoundedCornerShape(24.dp)
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
                                                                                colors.primary,
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .padding(bottom = 16.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text = "Landscape",
                                                        color = colors.text,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 16.sp
                                                )
                                                Text(
                                                        text = "5:3",
                                                        color = colors.subText,
                                                        fontSize = 13.sp
                                                )
                                        }
                                }
                        }
                }
                return
        }

        if (imageUri == null) {
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(colors.windowBg)
                                        .statusBarsPadding()
                ) {
                        // Header
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .border(
                                                        0.dp,
                                                        Color.Transparent
                                                ) // Hack for bottom border?
                                                .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.text,
                                        modifier =
                                                Modifier.size(24.dp).clickable {
                                                        viewModel.setTemplate(null)
                                                }
                                )
                                Text(
                                        text = "Select Image",
                                        color = colors.text,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(24.dp))
                        }
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(1.dp)
                                                .background(colors.border)
                        )

                        Column(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                // Take Photo
                                Row(
                                        modifier =
                                                Modifier.width(200.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(colors.primary)
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
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                                "Take Photo",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 16.sp
                                        )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // From Album
                                Row(
                                        modifier =
                                                Modifier.width(200.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(colors.headerBg)
                                                        .border(
                                                                1.dp,
                                                                colors.border,
                                                                RoundedCornerShape(8.dp)
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
                                                tint = colors.text,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                                "From Album",
                                                color = colors.text,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 16.sp
                                        )
                                }
                        }
                }
                return
        }

        // Editor
        Column(modifier = Modifier.fillMaxSize().background(colors.windowBg).statusBarsPadding()) {
                // Header
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.text,
                                modifier = Modifier.size(24.dp).clickable { onBack() }
                        )
                        Text(
                                text = "Edit",
                                color = colors.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                        )
                        if (saving) {
                                CircularProgressIndicator(
                                        color = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                        } else {
                                Text(
                                        text = "Save",
                                        color = colors.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier =
                                                Modifier.clickable {
                                                        viewModel.saveAndUpload(baseUrl) {
                                                                // Success - navigate back
                                                                onBack()
                                                        }
                                                }
                                )
                        }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))

                // Error message
                errorMessage?.let { error ->
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(Color(0x1AEF4444))
                                                .padding(12.dp)
                        ) { Text(text = error, color = colors.error, fontSize = 14.sp) }
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
                                                .border(1.dp, Color(0xFFDDDDDD))
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
                                                                viewModel.generateColorMatrix(
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
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.contentBg)
                                                .padding(16.dp)
                        ) {
                                // Presets
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = "Reset",
                                                color = colors.primary,
                                                modifier =
                                                        Modifier.clickable {
                                                                viewModel.setFilters(
                                                                        DEFAULT_FILTERS
                                                                )
                                                        }
                                        )
                                        Box(
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(16.dp))
                                                                .background(colors.primary)
                                                                .clickable {
                                                                        viewModel.setFilters(
                                                                                INK_SCREEN_PRESET
                                                                        )
                                                                }
                                                                .padding(
                                                                        vertical = 6.dp,
                                                                        horizontal = 12.dp
                                                                )
                                        ) { Text(text = "Ink Screen", color = Color.White) }
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

@Composable
fun FilterSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
        val colors = HojoTheme.colors
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text(
                                text = label,
                                color = colors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = Math.round(value).toString(),
                                color = colors.subText,
                                fontSize = 12.sp
                        )
                }
                Slider(
                        value = value,
                        onValueChange = onChange,
                        valueRange = min..max,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = colors.primary,
                                        activeTrackColor = colors.primary,
                                        inactiveTrackColor = colors.border
                                )
                )
        }
}
