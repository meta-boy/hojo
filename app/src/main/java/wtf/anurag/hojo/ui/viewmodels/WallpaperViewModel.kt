package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import javax.inject.Inject

data class FilterState(
        val grayscale: Float = 0f,
        val contrast: Float = 50f,
        val brightness: Float = 100f,
        val saturation: Float = 100f
)

val DEFAULT_FILTERS = FilterState()
val INK_SCREEN_PRESET =
        FilterState(grayscale = 0f, contrast = 130f, brightness = 100f, saturation = 50f)

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    application: Application,
    private val repository: FileManagerRepository,
    private val connectivityManager: EpaperConnectivityManager
) : AndroidViewModel(application) {

    private val _template = MutableStateFlow<String?>(null)
    val template = _template.asStateFlow()

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri = _imageUri.asStateFlow()

    private val _filters = MutableStateFlow(DEFAULT_FILTERS)
    val filters = _filters.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap = _bitmap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setTemplate(temp: String?) {
        _template.value = temp
    }

    fun setImageUri(uri: Uri?) {
        _imageUri.value = uri
        if (uri != null) {
            loadBitmap(uri)
        }
    }

    fun setFilters(newFilters: FilterState) {
        _filters.value = newFilters
    }

    fun setBitmap(bmp: Bitmap?) {
        _bitmap.value = bmp
    }

    private fun loadBitmap(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    _bitmap.value = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load image: ${e.message}"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveAndUpload(onSuccess: () -> Unit) {
        val baseUrl = connectivityManager.getDeviceBaseUrl()
        val bmp = _bitmap.value ?: return
        val currentFilters = _filters.value

        viewModelScope.launch {
            _saving.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    // Apply filters to bitmap using Android's ColorMatrix
                    val sat = currentFilters.saturation / 100f
                    val brightness = currentFilters.brightness / 100f
                    val contrast = currentFilters.contrast / 50f
                    val gray = currentFilters.grayscale / 100f

                    val colorMatrix = android.graphics.ColorMatrix()

                    // Apply saturation
                    colorMatrix.setSaturation(sat)

                    // Apply brightness (scale)
                    val brightnessMatrix =
                            android.graphics.ColorMatrix(
                                    floatArrayOf(
                                            brightness,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            brightness,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            brightness,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            0f,
                                            1f,
                                            0f
                                    )
                            )
                    colorMatrix.postConcat(brightnessMatrix)

                    // Apply contrast
                    val t = (1.0f - contrast) / 2.0f * 255f
                    val contrastMatrix =
                            android.graphics.ColorMatrix(
                                    floatArrayOf(
                                            contrast,
                                            0f,
                                            0f,
                                            0f,
                                            t,
                                            0f,
                                            contrast,
                                            0f,
                                            0f,
                                            t,
                                            0f,
                                            0f,
                                            contrast,
                                            0f,
                                            t,
                                            0f,
                                            0f,
                                            0f,
                                            1f,
                                            0f
                                    )
                            )
                    colorMatrix.postConcat(contrastMatrix)

                    // Apply grayscale if needed
                    if (gray > 0f) {
                        val graySat = 1f - gray
                        val grayMatrix = android.graphics.ColorMatrix()
                        grayMatrix.setSaturation(graySat)
                        colorMatrix.postConcat(grayMatrix)
                    }

                    // Create filtered bitmap
                    val filteredBitmap =
                            Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(filteredBitmap)
                    val paint = Paint()
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                    canvas.drawBitmap(bmp, 0f, 0f, paint)

                    // Save to temp file as JPEG
                    val timestamp = System.currentTimeMillis()
                    val filename = "wallpaper_$timestamp.jpg"
                    val tempFile = File(getApplication<Application>().cacheDir, filename)
                    FileOutputStream(tempFile).use { out ->
                        filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // Upload to e-paper device
                    val targetPath = "/backgrounds/$filename"
                    repository.uploadFile(baseUrl, tempFile, targetPath)

                    // Clean up
                    filteredBitmap.recycle()
                }
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to save: ${e.message}"
            } finally {
                _saving.value = false
            }
        }
    }

    // Helper to generate color matrix
    fun generateColorMatrix(f: FilterState): ComposeColorMatrix {
        // We'll build a 4x5 color matrix as a FloatArray (20 elements) and return
        // an androidx.compose.ui.graphics.ColorMatrix from it. This avoids using
        // android.graphics.ColorMatrix APIs which are not available on the Compose
        // ColorMatrix type.
        fun identity(): FloatArray =
                floatArrayOf(
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                )

        fun multiply(a: FloatArray, b: FloatArray): FloatArray {
            // a and b are 4x5 matrices stored row-major (4 rows * 5 cols)
            val out = FloatArray(20)
            for (i in 0 until 4) {
                val ai0 = a[i * 5 + 0]
                val ai1 = a[i * 5 + 1]
                val ai2 = a[i * 5 + 2]
                val ai3 = a[i * 5 + 3]
                val ai4 = a[i * 5 + 4]
                for (j in 0 until 4) {
                    // compute sum_k a[i][k] * b[k][j]
                    out[i * 5 + j] =
                            ai0 * b[0 * 5 + j] +
                                    ai1 * b[1 * 5 + j] +
                                    ai2 * b[2 * 5 + j] +
                                    ai3 * b[3 * 5 + j]
                }
                // translation column
                out[i * 5 + 4] =
                        ai0 * b[0 * 5 + 4] +
                                ai1 * b[1 * 5 + 4] +
                                ai2 * b[2 * 5 + 4] +
                                ai3 * b[3 * 5 + 4] +
                                ai4
            }
            return out
        }

        fun saturationMatrix(s: Float): FloatArray {
            val invSat = 1f - s
            val r = 0.213f * invSat
            val g = 0.715f * invSat
            val b = 0.072f * invSat
            return floatArrayOf(
                    r + s,
                    g,
                    b,
                    0f,
                    0f,
                    r,
                    g + s,
                    b,
                    0f,
                    0f,
                    r,
                    g,
                    b + s,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
            )
        }

        fun scaleMatrix(s: Float): FloatArray =
                floatArrayOf(
                        s,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        s,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        s,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                )

        fun contrastMatrix(c: Float): FloatArray {
            val t = (1.0f - c) / 2.0f * 255f
            return floatArrayOf(
                    c,
                    0f,
                    0f,
                    0f,
                    t,
                    0f,
                    c,
                    0f,
                    0f,
                    t,
                    0f,
                    0f,
                    c,
                    0f,
                    t,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
            )
        }

        // Build matrices from filters
        val sat = f.saturation / 100f
        val brightness = f.brightness / 100f
        val contrast = f.contrast / 50f
        val gray = f.grayscale / 100f

        var result = identity()

        // Apply saturation
        result = multiply(result, saturationMatrix(sat))
        // Apply brightness (scale)
        result = multiply(result, scaleMatrix(brightness))
        // Apply contrast
        result = multiply(result, contrastMatrix(contrast))
        // Apply grayscale mix if requested (by mixing saturation down)
        if (gray > 0f) {
            val graySat = 1f - gray
            result = multiply(result, saturationMatrix(graySat))
        }

        return ComposeColorMatrix(result)
    }
}
