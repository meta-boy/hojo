package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import wtf.anurag.hojo.data.ConnectivityRepository
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.ui.apps.converter.ConverterSettings
import wtf.anurag.hojo.ui.apps.converter.HtmlConverter
import wtf.anurag.hojo.ui.apps.converter.XtcEncoder
import javax.inject.Inject

@HiltViewModel
class QuickLinkViewModel @Inject constructor(
    application: Application,
    private val repository: FileManagerRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val okHttpClient: OkHttpClient
) : AndroidViewModel(application) {

    private val _quickLinkVisible = MutableStateFlow(false)
    val quickLinkVisible: StateFlow<Boolean> = _quickLinkVisible.asStateFlow()

    private val _quickLinkUrl = MutableStateFlow("")
    val quickLinkUrl: StateFlow<String> = _quickLinkUrl.asStateFlow()

    private val _converting = MutableStateFlow(false)
    val converting: StateFlow<Boolean> = _converting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setQuickLinkVisible(visible: Boolean) {
        _quickLinkVisible.value = visible
    }

    fun setQuickLinkUrl(url: String) {
        _quickLinkUrl.value = url
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleConvertAndUpload() {
        if (_quickLinkUrl.value.isBlank()) return
        _converting.value = true
        _errorMessage.value = null // Clear previous errors

        viewModelScope.launch {
            try {
                val baseUrl = connectivityRepository.deviceBaseUrl.value

                // 1. Unbind to access internet
                connectivityRepository.unbindNetwork()

                // 2. Fetch HTML and Parse
                val html = withContext(Dispatchers.IO) { URL(_quickLinkUrl.value).readText() }

                // Parse using Jsoup to get title
                val doc = Jsoup.parse(html)
                val title = doc.title().ifEmpty { "Quick Link" }

                // 3. Convert HTML directly to XTC format
                val converter = HtmlConverter(okHttpClient)
                val result = withContext(Dispatchers.Default) {
                    converter.convertHtml(
                        html = html,
                        title = title,
                        baseUrl = _quickLinkUrl.value,
                        settings = ConverterSettings(colorMode = XtcEncoder.ColorMode.MONOCHROME)
                    )
                }

                // Save to temp file
                val fileName = "${title.take(20).replace(Regex("[^a-zA-Z0-9]"), "_")}.xtc"
                val tempFile = File(getApplication<Application>().cacheDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(tempFile).use { it.write(result.data) } }

                // 4. Rebind to Epaper
                connectivityRepository.bindToEpaperNetwork()

                // 5. Upload
                // Check books folder
                val list = repository.fetchList(baseUrl, "/")
                if (list.none { it.name == "books" && it.type == "dir" }) {
                    repository.createFolder(baseUrl, "/books")
                }

                // Upload the generated XTC file using the repository API
                repository.uploadFile(baseUrl, tempFile, "/books/$fileName")

                _quickLinkVisible.value = false
                _quickLinkUrl.value = ""
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value =
                        when {
                            e.message?.contains("failed to connect", ignoreCase = true) == true ->
                                    "Failed to connect. Please check your internet connection."
                            e.message?.contains("No pages rendered", ignoreCase = true) == true ->
                                    "Failed to convert page. The content may be empty or unsupported."
                            else -> "Error: ${e.message ?: "Unknown error occurred"}"
                        }
            } finally {
                _converting.value = false
                // Ensure rebind
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectivityRepository.bindToEpaperNetwork()
                }
            }
        }
    }
}
