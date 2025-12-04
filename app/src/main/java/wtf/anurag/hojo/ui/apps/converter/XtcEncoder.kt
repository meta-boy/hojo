package wtf.anurag.hojo.ui.apps.converter

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * Shared utilities for converting content to XTC format for e-paper display.
 * Used by both NativeConverter (EPUB) and HtmlConverter (HTML).
 */
object XtcEncoder {

    private const val PAGE_WIDTH = 480
    private const val PAGE_HEIGHT = 800
    private const val FONT_SCALE_FACTOR = 1.4f // 140% font scaling

    /**
     * Creates a TextPaint configured for e-paper rendering.
     */
    fun createTextPaint(settings: ConverterSettings): TextPaint {
        return TextPaint().apply {
            isAntiAlias = true
            textSize = 16f * (settings.fontSize / 100f) * FONT_SCALE_FACTOR
            color = Color.BLACK
            if (settings.fontFamily.isNotEmpty()) {
                try {
                    typeface = Typeface.createFromFile(settings.fontFamily)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Converts HTML body content to a Spanned object with proper styling.
     */
    fun htmlToSpanned(
        bodyHtml: String,
        imageGetter: Html.ImageGetter,
        textPaint: TextPaint,
        settings: ConverterSettings
    ): Spanned {
        val spanned = Html.fromHtml(bodyHtml, Html.FROM_HTML_MODE_COMPACT, imageGetter, null)

        return if (textPaint.typeface != null && settings.fontFamily.isNotEmpty()) {
            val ssb = SpannableStringBuilder(spanned)
            ssb.setSpan(
                CustomTypefaceSpan(textPaint.typeface),
                0,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb
        } else {
            spanned
        }
    }

    /**
     * Renders spanned content into XTG-encoded pages.
     */
    fun renderPages(
        spanned: Spanned,
        textPaint: TextPaint,
        settings: ConverterSettings,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()

        val width = PAGE_WIDTH - (settings.margin * 2)
        val layout = StaticLayout.Builder.obtain(
            spanned,
            0,
            spanned.length,
            textPaint,
            width
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, settings.lineHeight)
            .setIncludePad(true)
            .build()

        val contentHeight = PAGE_HEIGHT - (settings.margin * 2)

        var yOffset = 0
        var pageCount = 0

        while (yOffset < layout.height) {
            val proposedBottom = yOffset + contentHeight
            var nextOffset = proposedBottom

            if (proposedBottom < layout.height) {
                val lineIndex = layout.getLineForVertical(proposedBottom)
                val lineTop = layout.getLineTop(lineIndex)

                if (lineTop < proposedBottom && lineTop > yOffset) {
                    nextOffset = lineTop
                }
            }

            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.save()
            // Clip to ensure we don't draw partial lines from the next page
            canvas.clipRect(
                0,
                0,
                PAGE_WIDTH,
                (nextOffset - yOffset + settings.margin).coerceAtMost(PAGE_HEIGHT)
            )
            canvas.translate(
                settings.margin.toFloat(),
                settings.margin.toFloat() - yOffset.toFloat()
            )
            layout.draw(canvas)
            canvas.restore()

            // Dither
            if (settings.enableDithering) {
                floydSteinbergDither(bitmap, settings.ditherStrength)
            } else {
                threshold(bitmap)
            }

            // Encode to XTG
            val xtgData = encodeXtg(bitmap)
            pages.add(xtgData)

            bitmap.recycle()

            yOffset = nextOffset
            pageCount++
            onProgress(pageCount, -1)
        }

        return pages
    }

    /**
     * Simple threshold dithering - converts to pure black/white.
     */
    fun threshold(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (gray < 128) Color.BLACK else Color.WHITE
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Floyd-Steinberg dithering for better grayscale representation.
     */
    fun floydSteinbergDither(bitmap: Bitmap, strength: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val factor = strength / 100f
        val gray = FloatArray(width * height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b)
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = gray[idx]
                val newPixel = if (oldPixel < 128) 0f else 255f

                gray[idx] = newPixel
                val error = (oldPixel - newPixel) * factor

                if (x < width - 1) gray[idx + 1] += error * 7 / 16
                if (y < height - 1) {
                    if (x > 0) gray[idx + width - 1] += error * 3 / 16
                    gray[idx + width] += error * 5 / 16
                    if (x < width - 1) gray[idx + width + 1] += error * 1 / 16
                }
            }
        }

        for (i in pixels.indices) {
            val valGray = gray[i].toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(valGray, valGray, valGray)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Encodes a bitmap to XTG format (single page).
     */
    fun encodeXtg(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = ceil(width / 8.0).toInt()
        val dataSize = bytesPerRow * height

        val buffer = ByteBuffer.allocate(22 + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buffer.put(0x58.toByte()) // X
        buffer.put(0x54.toByte()) // T
        buffer.put(0x47.toByte()) // G
        buffer.put(0x00.toByte())
        buffer.putShort(width.toShort())
        buffer.putShort(height.toShort())
        buffer.put(0.toByte()) // ColorMode
        buffer.put(0.toByte()) // Compression
        buffer.putInt(dataSize)
        buffer.putLong(0L) // MD5 (optional)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val dataArray = ByteArray(dataSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val isWhite = (pixel and 0xFF) > 128

                if (isWhite) {
                    val byteIdx = y * bytesPerRow + (x / 8)
                    val bitIdx = 7 - (x % 8)
                    dataArray[byteIdx] = (dataArray[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                }
            }
        }

        buffer.put(dataArray)
        return buffer.array()
    }

    /**
     * Packs multiple XTG pages into an XTC file.
     */
    fun packXtc(title: String, author: String, pages: List<ByteArray>): ByteArray {
        if (pages.isEmpty()) {
            throw IllegalStateException("No pages rendered")
        }

        val headerSize = 48
        val metadataSize = 256
        val chapterEntrySize = 96
        val indexEntrySize = 16

        val pageCount = pages.size

        val chapters = emptyList<ChapterInfo>()

        val chaptersSize = chapters.size * chapterEntrySize
        val indexSize = pageCount * indexEntrySize
        val totalDataSize = pages.sumOf { it.size }

        val metadataOffset = headerSize
        val chaptersOffset = metadataOffset + metadataSize
        val indexOffset = chaptersOffset + chaptersSize
        val dataOffset = indexOffset + indexSize
        val totalFileSize = dataOffset + totalDataSize

        val buffer = ByteBuffer.allocate(totalFileSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buffer.put(0x58.toByte()) // X
        buffer.put(0x54.toByte()) // T
        buffer.put(0x43.toByte()) // C
        buffer.put(0x00.toByte())
        buffer.putShort(0x0100) // Version 1.0
        buffer.putShort(pageCount.toShort())
        buffer.put(0.toByte()) // L->R
        buffer.put(1.toByte()) // Has Metadata
        buffer.put(0.toByte()) // No Thumbnails
        buffer.put(0.toByte()) // No Chapters
        buffer.putInt(1) // Current Page (1-based)
        buffer.putLong(metadataOffset.toLong())
        buffer.putLong(indexOffset.toLong())
        buffer.putLong(dataOffset.toLong())
        buffer.putLong(0L) // Thumb Offset

        // Metadata
        val sanitizedTitle = title.take(127)
        val sanitizedAuthor = author.take(63)

        val metaStart = buffer.position()
        buffer.put(sanitizedTitle.toByteArray(Charsets.UTF_8))
        buffer.position(metaStart + 128)
        buffer.put(sanitizedAuthor.toByteArray(Charsets.UTF_8))
        buffer.position(metaStart + 192)
        // Publisher... skip
        buffer.position(metaStart + 240)
        buffer.putInt((System.currentTimeMillis() / 1000).toInt()) // Create Time
        buffer.putShort(0) // Cover Page
        buffer.putShort(chapters.size.toShort())
        buffer.position(metaStart + 256)

        // Chapters (Empty)

        // Index Table (Absolute Offsets)
        var currentAbsoluteOffset = dataOffset.toLong()
        for (page in pages) {
            buffer.putLong(currentAbsoluteOffset)
            buffer.putInt(page.size)
            buffer.putShort(PAGE_WIDTH.toShort())
            buffer.putShort(PAGE_HEIGHT.toShort())
            currentAbsoluteOffset += page.size
        }

        // Data
        for (page in pages) {
            buffer.put(page)
        }

        return buffer.array()
    }

    /**
     * Creates a scaled BitmapDrawable from image bytes, fitting within the available width.
     */
    fun createScaledDrawable(bytes: ByteArray, availableWidth: Int): Drawable? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val scale = if (bitmap.width > availableWidth) {
            availableWidth.toFloat() / bitmap.width
        } else {
            1f
        }

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }

        val drawable = BitmapDrawable(Resources.getSystem(), scaledBitmap)
        drawable.setBounds(0, 0, newWidth, newHeight)
        return drawable
    }

    data class ChapterInfo(val name: String, val startPage: Int, val endPage: Int)

    /**
     * Custom TypefaceSpan that properly handles font styling.
     */
    class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) {
            applyCustomTypeface(ds, typeface)
        }

        override fun updateMeasureState(paint: TextPaint) {
            applyCustomTypeface(paint, typeface)
        }

        private fun applyCustomTypeface(paint: Paint, tf: Typeface) {
            val old = paint.typeface
            val oldStyle = old?.style ?: 0

            val fake = oldStyle and tf.style.inv()
            if (fake and Typeface.BOLD != 0) {
                paint.isFakeBoldText = true
            }
            if (fake and Typeface.ITALIC != 0) {
                paint.textSkewX = -0.25f
            }

            paint.typeface = tf
        }
    }
}

