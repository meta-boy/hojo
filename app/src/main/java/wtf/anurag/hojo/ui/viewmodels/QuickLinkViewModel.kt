package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import wtf.anurag.hojo.data.FileManagerRepository

class QuickLinkViewModel(
        application: Application,
        private val connectivityViewModel: ConnectivityViewModel
) : AndroidViewModel(application) {
    // EpaperConnectivityManager requires API 29+. Only instantiate on supported API levels.
    private val connectivityManager: EpaperConnectivityManager? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    EpaperConnectivityManager(application)
            else null
    private val repository = FileManagerRepository()

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
                val baseUrl = connectivityViewModel.deviceBaseUrl.value

                // 1. Unbind to access internet
                connectivityManager?.unbindNetwork()

                // 2. Fetch HTML and Parse
                val html = withContext(Dispatchers.IO) { URL(_quickLinkUrl.value).readText() }

                // Simple extraction using Jsoup (Readability alternative)
                val doc = Jsoup.parse(html)
                val title = doc.title()
                val content = doc.body().html() // Send full body for now, dotEPUB handles it

                // 3. Send to dotEPUB
                // Multipart request
                // This is tricky without a library that handles multipart easily like OkHttp's
                // MultipartBody
                // But we have OkHttp!
                val client = okhttp3.OkHttpClient()
                val requestBody =
                        okhttp3.MultipartBody.Builder()
                                .setType(okhttp3.MultipartBody.FORM)
                                .addFormDataPart("html", content)
                                .addFormDataPart("title", title)
                                .addFormDataPart("url", _quickLinkUrl.value)
                                .addFormDataPart("lang", "en")
                                .addFormDataPart("format", "epub")
                                .addFormDataPart("links", "0")
                                .addFormDataPart("imgs", "1")
                                .addFormDataPart("flags", "|")
                                .build()

                val request =
                        okhttp3.Request.Builder()
                                .url("https://dotepub.com/api/v1/post")
                                .post(requestBody)
                                .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

                if (!response.isSuccessful) throw Exception("Conversion failed: ${response.code}")

                val bytes = response.body?.bytes() ?: throw Exception("No body")

                // Save to temp file
                val fileName = "${title.take(20).replace(Regex("[^a-zA-Z0-9]"), "_")}.epub"
                val tempFile = File(getApplication<Application>().cacheDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(tempFile).use { it.write(bytes) } }

                // 4. Rebind to Epaper
                connectivityManager?.bindToEpaperNetwork()

                // 5. Upload
                // Check books folder
                val list = repository.fetchList(baseUrl, "/")
                if (list.none { it.name == "books" && it.type == "dir" }) {
                    repository.createFolder(baseUrl, "/books")
                }

                // Upload the generated EPUB file using the repository API (baseUrl, File,
                // targetPath)
                repository.uploadFile(baseUrl, tempFile, "/books/$fileName")

                _quickLinkVisible.value = false
                _quickLinkUrl.value = ""
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value =
                        when {
                            e.message?.contains("failed to connect", ignoreCase = true) == true ->
                                    "Failed to connect. Please check your internet connection."
                            e.message?.contains("Conversion failed", ignoreCase = true) == true ->
                                    "Failed to convert URL. The page may not be accessible."
                            e.message?.contains("No body", ignoreCase = true) == true ->
                                    "Conversion service returned empty response."
                            else -> "Error: ${e.message ?: "Unknown error occurred"}"
                        }
            } finally {
                _converting.value = false
                // Ensure rebind
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectivityManager?.bindToEpaperNetwork()
                }
            }
        }
    }
}
