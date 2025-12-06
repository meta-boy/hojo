package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import wtf.anurag.hojo.data.ConnectivityRepository
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.data.model.FileItem
import wtf.anurag.hojo.data.model.StorageStatus
import wtf.anurag.hojo.data.model.UploadProgress
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    application: Application,
    private val repository: FileManagerRepository,
    private val connectivityRepository: ConnectivityRepository
) : AndroidViewModel(application) {

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _storageInfo = MutableStateFlow<StorageStatus?>(null)
    @Suppress("unused") val storageInfo: StateFlow<StorageStatus?> = _storageInfo.asStateFlow()

    // Error message to surface to UI when fetches fail
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Upload progress tracking
    private val _uploadProgress = MutableStateFlow<UploadProgress?>(null)
    val uploadProgress: StateFlow<UploadProgress?> = _uploadProgress.asStateFlow()

    // Modal State
    private val _modalVisible = MutableStateFlow(false)
    val modalVisible: StateFlow<Boolean> = _modalVisible.asStateFlow()

    private val _modalMode = MutableStateFlow("create") // "create" or "rename"
    val modalMode: StateFlow<String> = _modalMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private var selectedItem: FileItem? = null

    init {
        loadFiles()
        loadStatus()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                val list = repository.fetchList(baseUrl, _currentPath.value)
                _files.value = list
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStatus() {
        viewModelScope.launch {
            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                val status = repository.fetchStatus(baseUrl)
                _storageInfo.value = status
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load status: ${e.message}"
            }
        }
    }

    fun handleNavigate(item: FileItem) {
        if (item.type == "dir") {
            val newPath =
                    if (_currentPath.value == "/") "/${item.name}"
                    else "${_currentPath.value}/${item.name}"
            _currentPath.value = newPath
            loadFiles()
        }
    }

    fun handleGoBack(): Boolean {
        return if (_currentPath.value != "/") {
            val parts = _currentPath.value.split("/").filter { it.isNotEmpty() }
            val newPath = if (parts.size <= 1) "/" else "/" + parts.dropLast(1).joinToString("/")
            _currentPath.value = newPath
            loadFiles()
            true
        } else {
            false
        }
    }

    fun handleCreateFolder() {
        _modalMode.value = "create"
        _inputText.value = ""
        _modalVisible.value = true
    }

    fun handleRename(item: FileItem) {
        _modalMode.value = "rename"
        selectedItem = item
        _inputText.value = item.name
        _modalVisible.value = true
    }

    fun handleDelete(item: FileItem) {
        viewModelScope.launch {
            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                val itemPath =
                        if (_currentPath.value == "/") "/${item.name}"
                        else "${_currentPath.value}/${item.name}"
                repository.deleteItem(baseUrl, itemPath)
                loadFiles()
                loadStatus()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to delete item: ${e.message}"
            }
        }
    }

    fun handleUpload(uri: Uri, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _uploadProgress.value = null

            var startTime = 0L
            var lastUpdateTime = 0L
            var lastBytesWritten = 0L

            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                val targetPath =
                        if (_currentPath.value == "/") "/$name" else "${_currentPath.value}/$name"

                // Copy URI contents to a temp file in the app cache so repository can upload it
                val app = getApplication<Application>()
                val tempFile = File(app.cacheDir, name)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                        ?: throw IllegalArgumentException("Unable to open URI: $uri")

                repository.uploadFile(baseUrl, tempFile, targetPath) { bytesWritten, totalBytes ->
                    val currentTime = System.currentTimeMillis()

                    // Initialize start time on first callback
                    if (startTime == 0L) {
                        startTime = currentTime
                        lastUpdateTime = currentTime
                        lastBytesWritten = 0L
                    }

                    // Calculate transfer speed (update every 100ms to avoid too frequent updates)
                    val timeSinceLastUpdate = currentTime - lastUpdateTime
                    val transferSpeed =
                            if (timeSinceLastUpdate > 100) {
                                val bytesSinceLastUpdate = bytesWritten - lastBytesWritten
                                val speedBytesPerSecond =
                                        (bytesSinceLastUpdate * 1000) / timeSinceLastUpdate
                                lastUpdateTime = currentTime
                                lastBytesWritten = bytesWritten
                                speedBytesPerSecond
                            } else {
                                _uploadProgress.value?.transferSpeedBytesPerSecond ?: 0L
                            }

                    _uploadProgress.value =
                            UploadProgress(
                                    bytesUploaded = bytesWritten,
                                    totalBytes = totalBytes,
                                    transferSpeedBytesPerSecond = transferSpeed
                            )
                }

                loadFiles()
                loadStatus()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to upload file: ${e.message}"
            } finally {
                _isLoading.value = false
                // Clear upload progress after a short delay
                kotlinx.coroutines.delay(1000)
                _uploadProgress.value = null
            }
        }
    }

    fun handleModalSubmit() {
        if (_inputText.value.isBlank()) return
        viewModelScope.launch {
            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                if (_modalMode.value == "create") {
                    val newPath =
                            if (_currentPath.value == "/") "/${_inputText.value}"
                            else "${_currentPath.value}/${_inputText.value}"
                    repository.createFolder(baseUrl, newPath)
                } else if (_modalMode.value == "rename" && selectedItem != null) {
                    val oldPath =
                            if (_currentPath.value == "/") "/${selectedItem!!.name}"
                            else "${_currentPath.value}/${selectedItem!!.name}"
                    val newPath =
                            if (_currentPath.value == "/") "/${_inputText.value}"
                            else "${_currentPath.value}/${_inputText.value}"
                    repository.renameItem(baseUrl, oldPath, newPath)
                }
                _modalVisible.value = false
                loadFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to ${_modalMode.value} item: ${e.message}"
            }
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun setModalVisible(visible: Boolean) {
        _modalVisible.value = visible
    }

    fun handleDownload(item: FileItem) {
        if (item.type == "dir") {
            android.widget.Toast.makeText(
                getApplication(),
                "Cannot download folders",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        android.widget.Toast.makeText(
            getApplication(),
            "Downloading ${item.name}...",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value
                val remotePath =
                        if (_currentPath.value == "/") "/${item.name}"
                        else "${_currentPath.value}/${item.name}"

                // Download to a temp file first
                val app = getApplication<Application>()
                val tempFile = File(app.cacheDir, item.name)
                repository.downloadFile(baseUrl, remotePath, tempFile)

                // Save to Downloads using MediaStore (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val resolver = app.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { output ->
                                FileInputStream(tempFile).use { input -> input.copyTo(output) }
                            }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    app,
                                    "Saved to Downloads",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            throw Exception("Failed to create file in Downloads")
                        }
                    }
                } else {
                    // For older Android versions, save directly to Downloads folder
                    withContext(Dispatchers.IO) {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val destFile = File(downloadsDir, item.name)
                        tempFile.copyTo(destFile, overwrite = true)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                app,
                                "Saved to Downloads",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                // Clean up temp file
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Download failed: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
