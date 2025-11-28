package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.data.model.FileItem
import wtf.anurag.hojo.data.model.StorageStatus
import java.io.File
import java.io.FileOutputStream

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileManagerRepository()
    val BASE_URL = "http://192.168.3.3"

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _storageInfo = MutableStateFlow<StorageStatus?>(null)
    @Suppress("unused")
    val storageInfo: StateFlow<StorageStatus?> = _storageInfo.asStateFlow()

    // Error message to surface to UI when fetches fail
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
                val list = repository.fetchList(BASE_URL, _currentPath.value)
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
                val status = repository.fetchStatus(BASE_URL)
                _storageInfo.value = status
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load status: ${e.message}"
            }
        }
    }

    fun handleNavigate(item: FileItem) {
        if (item.type == "dir") {
            val newPath = if (_currentPath.value == "/") "/${item.name}" else "${_currentPath.value}/${item.name}"
            _currentPath.value = newPath
            loadFiles()
        }
    }

    fun handleGoBack() {
        if (_currentPath.value != "/") {
            val parts = _currentPath.value.split("/").filter { it.isNotEmpty() }
            val newPath = if (parts.size <= 1) "/" else "/" + parts.dropLast(1).joinToString("/")
            _currentPath.value = newPath
            loadFiles()
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
                val itemPath = if (_currentPath.value == "/") "/${item.name}" else "${_currentPath.value}/${item.name}"
                repository.deleteItem(BASE_URL, itemPath)
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
            try {
                val targetPath = if (_currentPath.value == "/") "/$name" else "${_currentPath.value}/$name"

                // Copy URI contents to a temp file in the app cache so repository can upload it
                val app = getApplication<Application>()
                val tempFile = File(app.cacheDir, name)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Unable to open URI: $uri")

                repository.uploadFile(BASE_URL, tempFile, targetPath)
                loadFiles()
                loadStatus()
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to upload file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun handleModalSubmit() {
        if (_inputText.value.isBlank()) return
        viewModelScope.launch {
            try {
                if (_modalMode.value == "create") {
                    val newPath = if (_currentPath.value == "/") "/${_inputText.value}" else "${_currentPath.value}/${_inputText.value}"
                    repository.createFolder(BASE_URL, newPath)
                } else if (_modalMode.value == "rename" && selectedItem != null) {
                    val oldPath = if (_currentPath.value == "/") "/${selectedItem!!.name}" else "${_currentPath.value}/${selectedItem!!.name}"
                    val newPath = if (_currentPath.value == "/") "/${_inputText.value}" else "${_currentPath.value}/${_inputText.value}"
                    repository.renameItem(BASE_URL, oldPath, newPath)
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
}
