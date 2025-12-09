package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import wtf.anurag.hojo.data.ConnectivityRepository
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.ui.apps.converter.ConverterSettings
import wtf.anurag.hojo.ui.apps.converter.HtmlConverter
import wtf.anurag.hojo.ui.apps.converter.XtcEncoder
import javax.inject.Inject
import kotlin.coroutines.resume

data class ReadabilityResult(val title: String, val content: String)

@HiltViewModel
class QuickLinkViewModel @Inject constructor(
    application: Application,
    private val repository: FileManagerRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val taskRepository: wtf.anurag.hojo.data.TaskRepository,
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

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    fun setQuickLinkVisible(visible: Boolean) {
        _quickLinkVisible.value = visible
    }

    fun setQuickLinkUrl(url: String) {
        _quickLinkUrl.value = url
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun getReadabilityJsFile(): File {
        val dir = File(getApplication<Application>().filesDir, "readability_lib")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "readability.js")
    }

    private suspend fun ensureReadabilityJsExists(): String {
        return withContext(Dispatchers.IO) {
            val file = getReadabilityJsFile()

            // Check if file exists and has content
            if (!file.exists() || file.length() == 0L) {
                // Download from Mozilla's GitHub
                val url = "https://raw.githubusercontent.com/mozilla/readability/main/Readability.js"
                val content = URL(url).readText()
                FileOutputStream(file).use { it.write(content.toByteArray()) }
            }

            // Read and return the content
            file.readText()
        }
    }

    private suspend fun parseHtmlWithWebView(context: Context, baseUrl: String, rawHtml: String): ReadabilityResult {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true

                // Prevent WebView from rendering to screen or taking focus
                webView.alpha = 0f

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        viewModelScope.launch {
                            try {
                                // Get Readability.js library
                                val readabilityJsLibString = ensureReadabilityJsExists()

                                val jsLogic = """
                                    $readabilityJsLibString
                                    
                                    (function() {
                                        try {
                                            var documentClone = document.cloneNode(true);
                                            var article = new Readability(documentClone).parse();
                                            return JSON.stringify({
                                                title: article.title,
                                                content: article.content
                                            });
                                        } catch(e) {
                                            return JSON.stringify({ error: e.toString() });
                                        }
                                    })();
                                """

                                view?.evaluateJavascript(jsLogic) { result ->
                                    try {
                                        if (result == "null") {
                                            continuation.resume(ReadabilityResult("Error", rawHtml))
                                            return@evaluateJavascript
                                        }

                                        // Remove the extra quotes wrapper that evaluateJavascript adds
                                        val jsonStr = result.substring(1, result.length - 1)
                                            .replace("\\\"", "\"")
                                            .replace("\\\\", "\\")
                                            .replace("\\n", "\n")
                                            .replace("\\r", "\r")
                                            .replace("\\t", "\t")

                                        val json = JSONObject(jsonStr)
                                        val title = json.optString("title", "Quick Link")
                                        val content = json.optString("content", rawHtml)

                                        continuation.resume(ReadabilityResult(title, content))
                                    } catch (e: Exception) {
                                        continuation.resume(ReadabilityResult("Quick Link", rawHtml))
                                    } finally {
                                        webView.destroy()
                                    }
                                }
                            } catch (e: Exception) {
                                continuation.resume(ReadabilityResult("Quick Link", rawHtml))
                                webView.destroy()
                            }
                        }
                    }
                }

                // Load the data
                webView.loadDataWithBaseURL(baseUrl, rawHtml, "text/html", "UTF-8", null)

                // Handle cancellation
                continuation.invokeOnCancellation {
                    webView.destroy()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleConvert() {
        if (_quickLinkUrl.value.isBlank()) return
        _converting.value = true
        _errorMessage.value = null // Clear previous errors

        viewModelScope.launch {
            try {
                // 1. Prepare network for internet access (finds cellular/WiFi with internet)
                val hasInternet = connectivityRepository.prepareNetworkForApiRequest()
                if (!hasInternet) {
                    _errorMessage.value = "No internet connection available. If you're using hotspot mode, please ensure mobile data is enabled."
                    _converting.value = false
                    return@launch
                }

                // 2. Fetch Raw HTML
                val rawHtml = withContext(Dispatchers.IO) { URL(_quickLinkUrl.value).readText() }

                // 3. Parse using WebView with Readability.js (Replacing Jsoup)
                val readabilityResult = parseHtmlWithWebView(
                    context = getApplication(),
                    baseUrl = _quickLinkUrl.value,
                    rawHtml = rawHtml
                )

                val title = readabilityResult.title
                val cleanedHtml = readabilityResult.content // This is the ad-free, reader-view HTML

                // 4. Convert HTML directly to XTC format
                val converter = HtmlConverter(okHttpClient)
                val result = withContext(Dispatchers.Default) {
                    converter.convertHtml(
                        html = cleanedHtml, // Use cleaned HTML for reader view
                        title = title,
                        baseUrl = _quickLinkUrl.value,
                        settings = ConverterSettings(colorMode = ConverterSettings.ColorMode.MONOCHROME)
                    )
                }

                // Save to temp file
                val fileName = "${title.take(20).replace(Regex("[^a-zA-Z0-9]"), "_")}.xtc"
                val tempFile = File(getApplication<Application>().cacheDir, fileName)
                withContext(Dispatchers.IO) { FileOutputStream(tempFile).use { it.write(result.data) } }

                // 5. Rebind to Epaper network for device communication
                connectivityRepository.bindToEpaperNetwork()

                // Show preview
                _previewFile.value = tempFile
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
                            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                                    "No internet connection. If using hotspot, enable mobile data."
                            else -> "Error: ${e.message ?: "Unknown error occurred"}"
                        }
                // Ensure rebind on error
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectivityRepository.bindToEpaperNetwork()
                }
            } finally {
                _converting.value = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun uploadPreviewFile() {
        val file = _previewFile.value ?: return

        viewModelScope.launch {
            try {
                _converting.value = true
                
                // Queue the task
                val targetPath = "/books/${file.name}"
                taskRepository.addTask(android.net.Uri.fromFile(file), file.name, targetPath)

                // Close preview immediately
                _previewFile.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to queue upload: ${e.message}"
            } finally {
                _converting.value = false
            }
        }
    }

    fun closePreview() {
        _previewFile.value = null
    }
}
