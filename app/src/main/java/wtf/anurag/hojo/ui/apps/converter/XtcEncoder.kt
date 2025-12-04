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
 *
 * Implements XTC/XTG/XTH format specification v1.0 for ESP32 E-Paper Display Devices.
 *
 * Supports:
 * - XTG: 1-bit monochrome (for high-contrast content)
 * - XTH: 2-bit 4-level grayscale (for antialiased fonts)
 */
object XtcEncoder {

    private const val PAGE_WIDTH = 480
    private const val PAGE_HEIGHT = 800
    private const val FONT_SCALE_FACTOR = 1.4f // 140% font scaling

    // Header sizes
    private const val XTG_HEADER_SIZE = 22
    private const val XTH_HEADER_SIZE = 22

    // XTC structure sizes (per spec)
    private const val XTC_HEADER_SIZE = 48
    private const val XTC_METADATA_SIZE = 256
    private const val XTC_CHAPTER_ENTRY_SIZE = 88
    private const val XTC_INDEX_ENTRY_SIZE = 16

    /**
     * Color mode for encoding.
     */
    enum class ColorMode {
        MONOCHROME,  // 1-bit XTG
        GRAYSCALE_4  // 2-bit XTH (4 levels)
    }

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
     * Renders spanned content into encoded pages (XTG or XTH based on colorMode).
     */
    fun renderPages(
        spanned: Spanned,
        textPaint: TextPaint,
        settings: ConverterSettings,
        onProgress: (current: Int, total: Int) -> Unit,
        colorMode: ColorMode = ColorMode.GRAYSCALE_4
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

            // Encode based on color mode
            val pageData = when (colorMode) {
                ColorMode.MONOCHROME -> {
                    if (settings.enableDithering) {
                        floydSteinbergDither(bitmap, settings.ditherStrength)
                    } else {
                        threshold(bitmap)
                    }
                    encodeXtg(bitmap)
                }
                ColorMode.GRAYSCALE_4 -> {
                    // For 4-level grayscale, we quantize to 4 levels
                    // Optionally apply dithering for better gradients
                    if (settings.enableDithering) {
                        floydSteinbergDither4Level(bitmap, settings.ditherStrength)
                    } else {
                        quantizeTo4Levels(bitmap)
                    }
                    encodeXth(bitmap)
                }
            }

            pages.add(pageData)
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
     * Quantizes bitmap to 4 grayscale levels without dithering.
     * Maps to XTH LUT levels: 0=White, 1=DarkGrey, 2=LightGrey, 3=Black
     */
    fun quantizeTo4Levels(bitmap: Bitmap) {
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

            // Quantize to 4 levels (0, 85, 170, 255)
            val quantized = when {
                gray < 64 -> 0      // Black
                gray < 128 -> 85    // Dark grey
                gray < 192 -> 170   // Light grey
                else -> 255         // White
            }
            pixels[i] = Color.rgb(quantized, quantized, quantized)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Floyd-Steinberg dithering for 1-bit output.
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
     * Floyd-Steinberg dithering for 4-level grayscale output.
     * Quantizes to 4 levels while diffusing error for smoother gradients.
     */
    fun floydSteinbergDither4Level(bitmap: Bitmap, strength: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val factor = strength / 100f
        val gray = FloatArray(width * height)

        // Convert to grayscale
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b)
        }

        // 4-level quantization values
        val levels = floatArrayOf(0f, 85f, 170f, 255f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = gray[idx].coerceIn(0f, 255f)

                // Find nearest level
                val newPixel = levels.minByOrNull { kotlin.math.abs(it - oldPixel) } ?: 0f

                gray[idx] = newPixel
                val error = (oldPixel - newPixel) * factor

                // Diffuse error to neighbors
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
     * Encodes a bitmap to XTG format (1-bit monochrome).
     *
     * XTG Format (per spec):
     * - Header: 22 bytes
     * - Data: 1 bit per pixel, MSB first, row-major order
     * - Pixel values: 0=Black, 1=White
     */
    fun encodeXtg(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = ceil(width / 8.0).toInt()
        val dataSize = bytesPerRow * height

        val buffer = ByteBuffer.allocate(XTG_HEADER_SIZE + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header (22 bytes)
        buffer.put(0x58.toByte()) // X
        buffer.put(0x54.toByte()) // T
        buffer.put(0x47.toByte()) // G
        buffer.put(0x00.toByte())
        buffer.putShort(width.toShort())
        buffer.putShort(height.toShort())
        buffer.put(0.toByte()) // colorMode = monochrome
        buffer.put(0.toByte()) // compression = none
        buffer.putInt(dataSize)
        buffer.putLong(0L) // md5 (optional)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val dataArray = ByteArray(dataSize)

        // Row-major, MSB first, 0=Black, 1=White
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
     * Encodes a bitmap to XTH format (2-bit 4-level grayscale).
     *
     * XTH Format (per spec):
     * - Header: 22 bytes
     * - Data: Two bit planes, vertical scan order (column-major, right to left)
     * - LUT mapping (IMPORTANT - values 1 and 2 are swapped!):
     *   - 0 (00): White
     *   - 1 (01): Dark Grey
     *   - 2 (10): Light Grey
     *   - 3 (11): Black
     *
     * Bit planes:
     * - Bit1 (first plane): sent via command 0x24
     * - Bit2 (second plane): sent via command 0x26
     * - Pixel value = (bit1 << 1) | bit2
     */
    fun encodeXth(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        // Each column has height pixels, packed 8 per byte
        val bytesPerColumn = (height + 7) / 8
        val planeSize = width * bytesPerColumn
        val dataSize = planeSize * 2  // Two bit planes

        val buffer = ByteBuffer.allocate(XTH_HEADER_SIZE + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header (22 bytes)
        buffer.put(0x58.toByte()) // X
        buffer.put(0x54.toByte()) // T
        buffer.put(0x48.toByte()) // H
        buffer.put(0x00.toByte())
        buffer.putShort(width.toShort())
        buffer.putShort(height.toShort())
        buffer.put(0.toByte()) // colorMode
        buffer.put(0.toByte()) // compression = none
        buffer.putInt(dataSize)
        buffer.putLong(0L) // md5 (optional)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val plane1 = ByteArray(planeSize)  // Bit1 plane
        val plane2 = ByteArray(planeSize)  // Bit2 plane

        // Convert grayscale to 4-level pixel values with swapped LUT mapping
        // Input gray -> XTH pixel value
        // 255 (white)      -> 0 (00)
        // 170 (light grey) -> 2 (10) - NOTE: swapped!
        // 85  (dark grey)  -> 1 (01) - NOTE: swapped!
        // 0   (black)      -> 3 (11)
        fun grayToPixelValue(gray: Int): Int {
            return when {
                gray > 212 -> 0  // White
                gray > 127 -> 2  // Light grey (LUT level 2)
                gray > 42 -> 1   // Dark grey (LUT level 1)
                else -> 3        // Black
            }
        }

        // Vertical scan order: columns right to left, 8 vertical pixels per byte
        var byteIndex = 0
        for (x in (width - 1) downTo 0) {  // Right to left
            for (yGroup in 0 until bytesPerColumn) {
                var byte1 = 0
                var byte2 = 0

                for (bitPos in 0 until 8) {
                    val y = yGroup * 8 + bitPos
                    if (y < height) {
                        val pixel = pixels[y * width + x]
                        val gray = pixel and 0xFF
                        val pixelValue = grayToPixelValue(gray)

                        // pixelValue = (bit1 << 1) | bit2
                        val bit1 = (pixelValue shr 1) and 1
                        val bit2 = pixelValue and 1

                        // MSB = topmost pixel in group (bit 7 = first pixel)
                        val shift = 7 - bitPos
                        byte1 = byte1 or (bit1 shl shift)
                        byte2 = byte2 or (bit2 shl shift)
                    }
                }

                plane1[byteIndex] = byte1.toByte()
                plane2[byteIndex] = byte2.toByte()
                byteIndex++
            }
        }

        buffer.put(plane1)
        buffer.put(plane2)
        return buffer.array()
    }

    /**
     * Packs multiple XTG/XTH pages into an XTC file.
     */
    fun packXtc(title: String, author: String, pages: List<ByteArray>): ByteArray {
        if (pages.isEmpty()) {
            throw IllegalStateException("No pages rendered")
        }

        val pageCount = pages.size
        val chapters = emptyList<ChapterInfo>()

        val chaptersSize = chapters.size * XTC_CHAPTER_ENTRY_SIZE
        val indexSize = pageCount * XTC_INDEX_ENTRY_SIZE
        val totalDataSize = pages.sumOf { it.size }

        val metadataOffset = XTC_HEADER_SIZE
        val chaptersOffset = metadataOffset + XTC_METADATA_SIZE
        val indexOffset = chaptersOffset + chaptersSize
        val dataOffset = indexOffset + indexSize
        val totalFileSize = dataOffset + totalDataSize

        val buffer = ByteBuffer.allocate(totalFileSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // === Header (48 bytes) ===
        buffer.put(0x58.toByte()) // X
        buffer.put(0x54.toByte()) // T
        buffer.put(0x43.toByte()) // C
        buffer.put(0x00.toByte())
        buffer.putShort(0x0100.toShort()) // version
        buffer.putShort(pageCount.toShort())
        buffer.put(0.toByte()) // readDirection = L→R
        buffer.put(1.toByte()) // hasMetadata
        buffer.put(0.toByte()) // hasThumbnails
        buffer.put(if (chapters.isNotEmpty()) 1.toByte() else 0.toByte())
        buffer.putInt(1) // currentPage (1-based)
        buffer.putLong(metadataOffset.toLong())
        buffer.putLong(indexOffset.toLong())
        buffer.putLong(dataOffset.toLong())
        buffer.putLong(0L) // thumbOffset

        // === Metadata (256 bytes) ===
        val metaStart = buffer.position()

        val sanitizedTitle = title.take(127)
        buffer.put(sanitizedTitle.toByteArray(Charsets.UTF_8))
        buffer.position(metaStart + 128)

        val sanitizedAuthor = author.take(63)
        buffer.put(sanitizedAuthor.toByteArray(Charsets.UTF_8))
        buffer.position(metaStart + 192)

        // publisher (32 bytes) - skip
        buffer.position(metaStart + 224)

        // language (16 bytes) - skip
        buffer.position(metaStart + 240)

        buffer.putInt((System.currentTimeMillis() / 1000).toInt()) // createTime
        buffer.putShort(0) // coverPage
        buffer.putShort(chapters.size.toShort()) // chapterCount
        buffer.putLong(0L) // reserved

        buffer.position(metaStart + XTC_METADATA_SIZE)

        // === Chapters (88 bytes each) ===
        for (chapter in chapters) {
            val chapterStart = buffer.position()
            val chapterName = chapter.name.take(79)
            buffer.put(chapterName.toByteArray(Charsets.UTF_8))
            buffer.position(chapterStart + 80)
            buffer.putShort(chapter.startPage.toShort())
            buffer.putShort(chapter.endPage.toShort())
            buffer.putInt(0) // reserved
        }

        // === Index Table (16 bytes per page) ===
        var currentAbsoluteOffset = dataOffset.toLong()
        for (page in pages) {
            buffer.putLong(currentAbsoluteOffset)
            buffer.putInt(page.size)
            buffer.putShort(PAGE_WIDTH.toShort())
            buffer.putShort(PAGE_HEIGHT.toShort())
            currentAbsoluteOffset += page.size
        }

        // === Data Area ===
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