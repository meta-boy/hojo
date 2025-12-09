package wtf.anurag.hojo.ui.apps.converter

import android.text.Html
import java.io.File
import java.io.InputStream
import java.net.URI
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup

/**
 * Converts EPUB files to XTC format for e-paper display.
 */
class NativeConverter {

    /**
     * Converts an EPUB to XTC format, writing directly to the output file.
     * Uses streaming to avoid OOM for large files.
     */
    fun convertToFile(
        inputStream: InputStream,
        outputFile: File,
        settings: ConverterSettings,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        val book = EpubReader().readEpub(inputStream)

        val title = book.metadata.titles.firstOrNull() ?: "Untitled"
        val author = book.metadata.authors.firstOrNull()?.toString() ?: "Unknown"

        val writer = XtcEncoder.XtcStreamWriter(outputFile, metadata = XtcEncoder.XtcMetadata(title = title, author = author))
        writer.start()

        try {
            val spineReferences = book.spine.spineReferences
            val textPaint = XtcRenderer.createTextPaint(settings)

            // Pass 1: Calculate total pages
            var totalBookPages = 0
            val chapterPagesMap = mutableMapOf<String, Int>()

            for (ref in spineReferences) {
                if (ref.resource.id == "toc") continue
                
                try {
                    val htmlContent = String(ref.resource.data, charset("UTF-8"))
                    val doc = Jsoup.parse(htmlContent)
                    val bodyHtml = doc.body().html()
                    
                    val imageGetter = Html.ImageGetter { source ->
                        try {
                            var resolvedHref = URI(ref.resource.href).resolve(source).path
                            if (resolvedHref.startsWith("/")) resolvedHref = resolvedHref.substring(1)
                            val imageResource = book.resources.getByHref(resolvedHref) ?: book.resources.getByHref(source)
                            if (imageResource != null) {
                                val availableWidth = 480 - (settings.margin * 2)
                                return@ImageGetter XtcRenderer.createScaledDrawable(imageResource.data, availableWidth)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                        null
                    }

                    val spanned = XtcRenderer.htmlToSpanned(bodyHtml, imageGetter, textPaint, settings)
                    val breaks = XtcRenderer.calculatePageBreaks(spanned, textPaint, settings)
                    val chapterPages = breaks.size
                    
                    chapterPagesMap[ref.resource.href] = chapterPages
                    totalBookPages += chapterPages
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Pass 2: Render pages
            var currentGlobalPage = 1

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
                            return@ImageGetter XtcRenderer.createScaledDrawable(bytes, availableWidth)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    null
                }

                // Convert HTML to Spanned
                val spanned = XtcRenderer.htmlToSpanned(bodyHtml, imageGetter, textPaint, settings)

                // Render pages for this chapter and write each page immediately
                val pageInfo = XtcRenderer.PageInfo(currentGlobalPage, totalBookPages)
                
                XtcRenderer.renderPages(
                    spanned, 
                    textPaint, 
                    settings, 
                    title,
                    pageInfo
                ) { bitmap ->
                    // Encode and write
                    val config = XtcEncoder.EncoderConfig(
                        width = bitmap.width,
                        height = bitmap.height,
                        enableDithering = settings.enableDithering,
                        ditherStrength = settings.ditherStrength / 100f
                    )

                    val frame = if (settings.colorMode == ConverterSettings.ColorMode.MONOCHROME) {
                        XtcEncoder.encodeXtg(bitmap, config)
                    } else {
                        XtcEncoder.encodeXth(bitmap, config)
                    }

                    writer.writePage(frame)
                    bitmap.recycle()

                    onProgress(currentGlobalPage, totalBookPages)
                    currentGlobalPage++
                }
            }

            writer.finish()
            
        } catch (e: Exception) {
            writer.close()
            outputFile.delete()
            throw e
        }
    }
}
