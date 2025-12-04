package wtf.anurag.hojo.ui.apps.converter

import android.text.Html
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Converts HTML content directly to XTC format for e-paper display.
 * This bypasses the need for EPUB conversion via external services.
 */
class HtmlConverter(private val okHttpClient: OkHttpClient? = null) {

    data class HtmlConversionResult(
        val title: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HtmlConversionResult
            return title == other.title && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    fun convertHtml(
        html: String,
        title: String,
        baseUrl: String? = null,
        settings: ConverterSettings = ConverterSettings(),
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): HtmlConversionResult {
        val textPaint = XtcEncoder.createTextPaint(settings)

        // Parse and clean HTML using Jsoup
        val doc = Jsoup.parse(html)
        val bodyHtml = doc.body().html()

        // Image Getter for loading images from URLs
        val imageGetter = Html.ImageGetter { source ->
            try {
                val imageUrl = if (source.startsWith("http://") || source.startsWith("https://")) {
                    source
                } else if (baseUrl != null) {
                    // Resolve relative URL
                    URL(URL(baseUrl), source).toString()
                } else {
                    null
                }

                if (imageUrl != null) {
                    val bytes = if (okHttpClient != null) {
                        val request = Request.Builder().url(imageUrl).build()
                        okHttpClient.newCall(request).execute().use { response ->
                            response.body?.bytes()
                        }
                    } else {
                        URL(imageUrl).readBytes()
                    }

                    if (bytes != null) {
                        val availableWidth = 480 - (settings.margin * 2)
                        return@ImageGetter XtcEncoder.createScaledDrawable(bytes, availableWidth)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }

        // Convert HTML to Spanned
        val spanned = XtcEncoder.htmlToSpanned(bodyHtml, imageGetter, textPaint, settings)

        // Render pages - pass colorMode from settings
        val pages = XtcEncoder.renderPages(spanned, textPaint, settings, onProgress, settings.colorMode)

        // Pack to XTC
        val xtcData = XtcEncoder.packXtc(title, "Quick Link", pages)
        return HtmlConversionResult(title, xtcData)
    }
}
