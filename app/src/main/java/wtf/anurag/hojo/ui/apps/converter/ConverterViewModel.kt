package wtf.anurag.hojo.ui.apps.converter

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtf.anurag.hojo.data.FileManagerRepository
import javax.inject.Inject

@HiltViewModel
class ConverterViewModel @Inject constructor(
    application: Application,
    private val repository: FileManagerRepository
) : AndroidViewModel(application) {

    private val _status = MutableStateFlow<ConverterStatus>(ConverterStatus.Idle)
    val status: StateFlow<ConverterStatus> = _status.asStateFlow()

    private val _settings = MutableStateFlow(ConverterSettings())
    val settings: StateFlow<ConverterSettings> = _settings.asStateFlow()

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile: StateFlow<Uri?> = _selectedFile.asStateFlow()

    private val _availableFonts = MutableStateFlow<List<File>>(emptyList())
    val availableFonts: StateFlow<List<File>> = _availableFonts.asStateFlow()

    init {
        loadFonts()
    }

    private fun getFontsDir(): File {
        val dir = File(getApplication<Application>().filesDir, "imported_fonts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun loadFonts() {
        viewModelScope.launch(Dispatchers.IO) {
            val fontsDir = getFontsDir()
            if (fontsDir.exists() && fontsDir.isDirectory) {
                val fonts =
                        fontsDir
                                .listFiles { file ->
                                    file.extension.equals("ttf", ignoreCase = true) ||
                                            file.extension.equals("otf", ignoreCase = true)
                                }
                                ?.toList()
                                ?.sortedBy { it.name }
                                ?: emptyList()
                _availableFonts.value = fonts
            }
        }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri)
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fontsDir = getFontsDir()
                    val destFile = File(fontsDir, fileName)
                    FileOutputStream(destFile).use { output -> inputStream.copyTo(output) }
                    loadFonts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectFile(uri: Uri) {
        _selectedFile.value = uri
        _status.value = ConverterStatus.Idle
    }

    fun updateSettings(newSettings: ConverterSettings) {
        _settings.value = newSettings
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor =
                getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            if (result != null) {
                val cut = result.lastIndexOf('/')
                if (cut != -1) {
                    result = result.substring(cut + 1)
                }
            }
        }
        return result ?: "book.epub"
    }

    fun startConversion() {
        val uri = _selectedFile.value ?: return
        _status.value = ConverterStatus.ReadingFile

        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _status.value = ConverterStatus.Error("Failed to open file")
                    return@launch
                }

                _status.value = ConverterStatus.Converting(0, 0)

                val converter = NativeConverter()
                val result =
                    withContext(Dispatchers.Default) {
                        converter.convert(inputStream, _settings.value) { current, total ->
                            _status.value = ConverterStatus.Converting(current, total)
                        }
                    }

                // Save result
                val originalName = getFileName(uri).substringBeforeLast(".")
                val fileName = (originalName + ".xtc").replace(" ", "_")
                val outputFile =
                    withContext(Dispatchers.IO) {
                        val file = File(getApplication<Application>().cacheDir, fileName)
                        FileOutputStream(file).use { it.write(result) }
                        file
                    }

                _status.value = ConverterStatus.Preview(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = ConverterStatus.Error("Conversion failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _status.value = ConverterStatus.Idle
        _selectedFile.value = null
    }

    // Accept baseUrl string so this method doesn't directly depend on ConnectivityViewModel's API level
    fun uploadToEpaper(file: File, baseUrl: String) {
        viewModelScope.launch {
            try {
                _status.value = ConverterStatus.Uploading

                val fileName = file.name
                val targetPath = "/books/$fileName"

                // Ensure /books directory exists
                try {
                    repository.createFolder(baseUrl, "/books")
                } catch (_: Exception) {
                    // Ignore if already exists or fails, try upload anyway
                }

                repository.uploadFile(baseUrl, file, targetPath) { _, _ -> }

                _status.value = ConverterStatus.UploadSuccess
            } catch (e: Exception) {
                _status.value = ConverterStatus.Error("Upload failed: ${e.message}")
            }
        }
    }

    fun saveToDownloads(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _status.value = ConverterStatus.Error("Save to Downloads requires Android 10+")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentValues =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS
                        )
                    }
                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(file).use { input -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Saved to Downloads",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        val currentStatus = _status.value
                        if (currentStatus is ConverterStatus.Preview) {
                            _status.value = currentStatus.copy(isSaved = true)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _status.value = ConverterStatus.Error("Failed to create file in Downloads")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _status.value = ConverterStatus.Error("Save failed: ${e.message}")
                }
            }
        }
    }
}
