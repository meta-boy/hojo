package wtf.anurag.hojo.ui.apps.converter

import android.text.Html
import java.io.InputStream
import java.net.URI
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup

/**
 * Converts EPUB files to XTC format for e-paper display.
 */
class NativeConverter {

    fun convert(
        inputStream: InputStream,
        settings: ConverterSettings,
        onProgress: (current: Int, total: Int) -> Unit
    ): ByteArray {
        val book = EpubReader().readEpub(inputStream)
        val allPages = mutableListOf<ByteArray>()

        val spineReferences = book.spine.spineReferences
        var pageCount = 0

        val textPaint = XtcEncoder.createTextPaint(settings)

        for (ref in spineReferences) {
            val resource = ref.resource

            // Skip TOC page
            if (resource.id == "toc") {
                continue
            }

            val htmlContent = String(resource.data, charset("UTF-8"))

            // Extract body content using Jsoup to clean up
            val doc = Jsoup.parse(htmlContent)
            val bodyHtml = doc.body().html()

            // Image Getter for loading images from EPUB
            val imageGetter = Html.ImageGetter { source ->
                try {
                    // Resolve relative path
                    var resolvedHref = URI(ref.resource.href).resolve(source).path
                    if (resolvedHref.startsWith("/")) {
                        resolvedHref = resolvedHref.substring(1)
                    }

                    val imageResource = book.resources.getByHref(resolvedHref)
                        ?: book.resources.getByHref(source)

                    if (imageResource != null) {
                        val bytes = imageResource.data
                        val availableWidth = 480 - (settings.margin * 2)
                        return@ImageGetter XtcEncoder.createScaledDrawable(bytes, availableWidth)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                null
            }

            // Convert HTML to Spanned
            val spanned = XtcEncoder.htmlToSpanned(bodyHtml, imageGetter, textPaint, settings)

            // Render pages for this chapter
            val pages = XtcEncoder.renderPages(spanned, textPaint, settings) { current, _ ->
                pageCount++
                onProgress(pageCount, -1)
            }

            allPages.addAll(pages)
        }

        // Pack to XTC
        val title = book.metadata.titles.firstOrNull() ?: "Untitled"
        val author = book.metadata.authors.firstOrNull()?.toString() ?: "Unknown"
        return XtcEncoder.packXtc(title, author, allPages)
    }
}
