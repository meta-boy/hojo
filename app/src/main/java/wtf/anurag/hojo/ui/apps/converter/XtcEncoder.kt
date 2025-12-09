package wtf.anurag.hojo.ui.apps.converter

import android.graphics.Bitmap
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * XTC/XTH/XTG Encoder for ESP32 E-Paper Displays.
 *
 * Implements the XTC format specification for converting images to e-paper display formats:
 * - XTG: 1-bit monochrome (row-major, MSB first)
 * - XTH: 2-bit 4-level grayscale (vertical bitplanes, column-major right-to-left)
 * - XTC: Multi-page container with metadata
 *
 * Based on the xtctool Python implementation.
 */
object XtcEncoder {

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════

    // Magic numbers (little-endian)
    private const val MAGIC_XTC = 0x00435458  // "XTC\0"
    private const val MAGIC_XTH = 0x00485458  // "XTH\0"
    private const val MAGIC_XTG = 0x00475458  // "XTG\0"

    // Header sizes
    private const val XTC_HEADER_SIZE = 48
    private const val XTC_METADATA_SIZE = 256
    private const val XTC_CHAPTER_SIZE = 96
    private const val XTC_INDEX_ENTRY_SIZE = 16
    private const val XTH_HEADER_SIZE = 22
    private const val XTG_HEADER_SIZE = 22

    // XTC Version
    private const val XTC_VERSION: Short = 0x0100

    // Reading directions
    const val DIRECTION_LTR = 0  // Left to Right
    const val DIRECTION_RTL = 1  // Right to Left
    const val DIRECTION_TTB = 2  // Top to Bottom

    // ═══════════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * XTC file metadata.
     */
    data class XtcMetadata(
        val title: String = "",
        val author: String = "",
        val publisher: String = "",
        val language: String = "en-US",
        val createTime: Long = System.currentTimeMillis() / 1000,
        val coverPage: Int = 0xFFFF,  // 0xFFFF = no cover
    )

    /**
     * Chapter information for XTC container.
     */
    data class XtcChapter(
        val name: String,
        val startPage: Int,  // 0-based
        val endPage: Int,    // 0-based, inclusive
    )

    /**
     * Configuration for image encoding.
     */
    data class EncoderConfig(
        val width: Int = 480,
        val height: Int = 800,
        val enableDithering: Boolean = true,
        val ditherStrength: Float = 0.8f,
        // XTH specific
        val xthThreshold1: Int = 85,
        val xthThreshold2: Int = 170,
        val xthThreshold3: Int = 255,
        val xthInvert: Boolean = false,
        // XTG specific
        val xtgThreshold: Int = 128,
        val xtgInvert: Boolean = false,
    )

    /**
     * Encoded frame data with format info.
     */
    data class EncodedFrame(
        val data: ByteArray,
        val format: FrameFormat,
        val width: Int,
        val height: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncodedFrame) return false
            return data.contentEquals(other.data) && format == other.format &&
                    width == other.width && height == other.height
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + format.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }

    enum class FrameFormat {
        XTH,  // 4-level grayscale
        XTG,  // 1-bit monochrome
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API - Image Encoding
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Encode a bitmap to XTH format (4-level grayscale).
     */
    fun encodeXth(
        bitmap: Bitmap,
        config: EncoderConfig = EncoderConfig()
    ): EncodedFrame {
        // Resize if needed
        val resized = resizeBitmap(bitmap, config.width, config.height)

        // Convert to grayscale array
        val grayArray = bitmapToGrayscale(resized)

        // Apply invert if requested (to source before quantization)
        val processedArray = if (config.xthInvert) {
            FloatArray(grayArray.size) { 255f - grayArray[it] }
        } else {
            grayArray
        }

        // Quantize to 4 levels (with or without dithering)
        val quantized = if (config.enableDithering) {
            floydSteinbergDither4Level(
                processedArray,
                config.width,
                config.height,
                config.xthThreshold1.toFloat(),
                config.xthThreshold2.toFloat(),
                config.xthThreshold3.toFloat(),
                config.ditherStrength
            )
        } else {
            quantize4Level(
                processedArray,
                config.width,
                config.height,
                config.xthThreshold1,
                config.xthThreshold2,
                config.xthThreshold3
            )
        }

        // Encode to bitplanes
        val (plane1, plane2) = encodeXthBitplanes(quantized, config.width, config.height)

        // Build XTH file
        val dataSize = plane1.size + plane2.size
        val checksum = plane1.sum() + plane2.sum()

        val buffer = ByteBuffer.allocate(XTH_HEADER_SIZE + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header (22 bytes)
        buffer.putInt(MAGIC_XTH)
        buffer.putShort(config.width.toShort())
        buffer.putShort(config.height.toShort())
        buffer.put(0)  // colorMode
        buffer.put(0)  // compression
        buffer.putInt(dataSize)
        buffer.putLong(checksum and 0xFFFFFFFFFFFFFFFL)  // checksum

        // Data
        buffer.put(plane1)
        buffer.put(plane2)

        if (resized != bitmap) resized.recycle()

        return EncodedFrame(buffer.array(), FrameFormat.XTH, config.width, config.height)
    }

    /**
     * Encode a bitmap to XTG format (1-bit monochrome).
     */
    fun encodeXtg(
        bitmap: Bitmap,
        config: EncoderConfig = EncoderConfig()
    ): EncodedFrame {
        // Resize if needed
        val resized = resizeBitmap(bitmap, config.width, config.height)

        // Convert to grayscale array
        val grayArray = bitmapToGrayscale(resized)

        // Quantize to 2 levels (with or without dithering)
        val quantized = if (config.enableDithering) {
            floydSteinbergDither2Level(
                grayArray,
                config.width,
                config.height,
                config.xtgThreshold.toFloat(),
                config.ditherStrength
            )
        } else {
            quantize2Level(grayArray, config.width, config.height, config.xtgThreshold)
        }

        // Apply invert if requested
        val finalQuantized = if (config.xtgInvert) {
            ByteArray(quantized.size) { (1 - quantized[it]).toByte() }
        } else {
            quantized
        }

        // Encode to bitmap
        val bitmapData = encodeXtgBitmap(finalQuantized, config.width, config.height)

        // Build XTG file
        val dataSize = bitmapData.size
        val checksum = bitmapData.sumOf { it.toInt() and 0xFF }

        val buffer = ByteBuffer.allocate(XTG_HEADER_SIZE + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header (22 bytes)
        buffer.putInt(MAGIC_XTG)
        buffer.putShort(config.width.toShort())
        buffer.putShort(config.height.toShort())
        buffer.put(0)  // colorMode
        buffer.put(0)  // compression
        buffer.putInt(dataSize)
        buffer.putLong(checksum.toLong() and 0xFFFFFFFFFFFFFFFL)  // checksum

        // Data
        buffer.put(bitmapData)

        if (resized != bitmap) resized.recycle()

        return EncodedFrame(buffer.array(), FrameFormat.XTG, config.width, config.height)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API - XTC Container
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pack multiple encoded frames into an XTC container.
     */
    fun packXtc(
        frames: List<EncodedFrame>,
        metadata: XtcMetadata = XtcMetadata(),
        chapters: List<XtcChapter> = emptyList(),
        readingDirection: Int = DIRECTION_LTR
    ): ByteArray {
        require(frames.isNotEmpty()) { "No frames provided" }

        // Verify all frames are same format
        val format = frames.first().format
        require(frames.all { it.format == format }) {
            "All frames must be the same format"
        }

        val pageCount = frames.size
        val width = frames.first().width
        val height = frames.first().height

        // Calculate offsets
        val hasMetadata = metadata.title.isNotEmpty() || metadata.author.isNotEmpty()
        val hasChapters = chapters.isNotEmpty()

        var currentOffset = XTC_HEADER_SIZE
        val metadataOffset = if (hasMetadata) currentOffset.toLong() else 0L
        if (hasMetadata) currentOffset += XTC_METADATA_SIZE

        val chaptersOffset = currentOffset.toLong()
        if (hasChapters) currentOffset += XTC_CHAPTER_SIZE * chapters.size

        val indexOffset = currentOffset.toLong()
        val dataOffset = indexOffset + (XTC_INDEX_ENTRY_SIZE * pageCount)
        val totalDataSize = frames.sumOf { it.data.size }
        val totalFileSize = dataOffset.toInt() + totalDataSize

        val buffer = ByteBuffer.allocate(totalFileSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // === Header (48 bytes) ===
        buffer.putInt(MAGIC_XTC)
        buffer.putShort(XTC_VERSION)
        buffer.putShort(pageCount.toShort())
        buffer.put(readingDirection.toByte())
        buffer.put(if (hasMetadata) 1 else 0)
        buffer.put(0)  // hasThumbnails
        buffer.put(if (hasChapters) 1 else 0)
        buffer.putInt(0)  // currentPage
        buffer.putLong(metadataOffset)
        buffer.putLong(indexOffset)
        buffer.putLong(dataOffset)
        buffer.putLong(0)  // thumbOffset

        // === Metadata (256 bytes) ===
        if (hasMetadata) {
            val metaStart = buffer.position()

            // Title (128 bytes)
            writeFixedString(buffer, metadata.title, 128)

            // Author (64 bytes)
            writeFixedString(buffer, metadata.author, 64)

            // Publisher (32 bytes)
            writeFixedString(buffer, metadata.publisher, 32)

            // Language (16 bytes)
            writeFixedString(buffer, metadata.language, 16)

            // Create time (4 bytes)
            buffer.putInt(metadata.createTime.toInt())

            // Cover page (2 bytes)
            buffer.putShort(metadata.coverPage.toShort())

            // Chapter count (2 bytes)
            buffer.putShort(chapters.size.toShort())

            // Reserved (8 bytes)
            buffer.putLong(0)

            // Ensure we're at correct position
            buffer.position(metaStart + XTC_METADATA_SIZE)
        }

        // === Chapters (96 bytes each) ===
        for (chapter in chapters) {
            val chapterStart = buffer.position()

            // Name (80 bytes)
            writeFixedString(buffer, chapter.name, 80)

            // Start page (2 bytes)
            buffer.putShort(chapter.startPage.toShort())

            // End page (2 bytes)
            buffer.putShort(chapter.endPage.toShort())

            // Reserved (12 bytes)
            buffer.putInt(0)
            buffer.putInt(0)
            buffer.putInt(0)

            buffer.position(chapterStart + XTC_CHAPTER_SIZE)
        }

        // === Index Table (16 bytes per page) ===
        var currentDataOffset = dataOffset
        for (frame in frames) {
            buffer.putLong(currentDataOffset)
            buffer.putInt(frame.data.size)
            buffer.putShort(width.toShort())
            buffer.putShort(height.toShort())
            currentDataOffset += frame.data.size.toLong()
        }

        // === Page Data ===
        for (frame in frames) {
            buffer.put(frame.data)
        }

        return buffer.array()
    }

    /**
     * Streaming XTC writer for large files to avoid OOM.
     */
    class XtcStreamWriter(
        private val outputFile: File,
        private val width: Int = 480,
        private val height: Int = 800,
        private val metadata: XtcMetadata = XtcMetadata(),
        private val chapters: List<XtcChapter> = emptyList(),
        private val readingDirection: Int = DIRECTION_LTR,
        private var estimatedMaxPages: Int = DEFAULT_MAX_PAGES
    ) {
        companion object {
            const val DEFAULT_MAX_PAGES = 10000
        }

        private val pageSizes = mutableListOf<Int>()
        private var raf: RandomAccessFile? = null
        private var reservedIndexSize: Int = 0
        private var dataStartOffset: Long = 0

        fun start() {
            raf = RandomAccessFile(outputFile, "rw")
            raf?.setLength(0)

            val hasMetadata = metadata.title.isNotEmpty() || metadata.author.isNotEmpty()
            val hasChapters = chapters.isNotEmpty()

            // Calculate reserved space
            var headerSpace = XTC_HEADER_SIZE
            if (hasMetadata) headerSpace += XTC_METADATA_SIZE
            if (hasChapters) headerSpace += XTC_CHAPTER_SIZE * chapters.size

            reservedIndexSize = estimatedMaxPages * XTC_INDEX_ENTRY_SIZE
            dataStartOffset = (headerSpace + reservedIndexSize).toLong()

            // Seek to data start - write header/index at the end
            raf?.seek(dataStartOffset)
        }

        fun writePage(frame: EncodedFrame) {
            val file = raf ?: return

            if (pageSizes.size >= estimatedMaxPages) {
                expandIndexSpace(file)
            }

            pageSizes.add(frame.data.size)
            file.write(frame.data)
        }

        private fun expandIndexSpace(file: RandomAccessFile) {
            val oldReservedSize = reservedIndexSize
            val newMaxPages = estimatedMaxPages * 2
            val newReservedSize = newMaxPages * XTC_INDEX_ENTRY_SIZE
            val additionalSpace = newReservedSize - oldReservedSize

            val currentPos = file.filePointer
            val pageDataSize = currentPos - dataStartOffset

            file.setLength(file.length() + additionalSpace)

            val chunkSize = 65536
            val buffer = ByteArray(chunkSize)
            var remaining = pageDataSize
            while (remaining > 0) {
                val readSize = minOf(remaining, chunkSize.toLong()).toInt()
                val srcPos = dataStartOffset + remaining - readSize
                val dstPos = srcPos + additionalSpace

                file.seek(srcPos)
                file.readFully(buffer, 0, readSize)
                file.seek(dstPos)
                file.write(buffer, 0, readSize)

                remaining -= readSize
            }

            reservedIndexSize = newReservedSize
            dataStartOffset += additionalSpace
            estimatedMaxPages = newMaxPages

            file.seek(dataStartOffset + pageDataSize)
        }

        fun finish() {
            val file = raf ?: return

            if (pageSizes.isEmpty()) {
                file.close()
                raf = null
                throw IllegalStateException("No pages written")
            }

            val pageCount = pageSizes.size
            val hasMetadata = metadata.title.isNotEmpty() || metadata.author.isNotEmpty()
            val hasChapters = chapters.isNotEmpty()

            // Calculate actual offsets
            var headerSpace = XTC_HEADER_SIZE
            val metadataOffset = if (hasMetadata) headerSpace.toLong() else 0L
            if (hasMetadata) headerSpace += XTC_METADATA_SIZE

            val chaptersOffset = headerSpace.toLong()
            if (hasChapters) headerSpace += XTC_CHAPTER_SIZE * chapters.size

            val indexOffset = headerSpace.toLong()
            val actualIndexSize = pageCount * XTC_INDEX_ENTRY_SIZE
            val actualDataOffset = indexOffset + actualIndexSize

            // Shift data if we reserved more space than needed
            val unusedSpace = reservedIndexSize - actualIndexSize
            if (unusedSpace > 0) {
                val currentDataStart = dataStartOffset
                val newDataStart = actualDataOffset
                val pageDataSize = file.length() - currentDataStart

                val chunkSize = 65536
                val buffer = ByteArray(chunkSize)
                var offset = 0L
                while (offset < pageDataSize) {
                    val readSize = minOf(pageDataSize - offset, chunkSize.toLong()).toInt()
                    file.seek(currentDataStart + offset)
                    file.readFully(buffer, 0, readSize)
                    file.seek(newDataStart + offset)
                    file.write(buffer, 0, readSize)
                    offset += readSize
                }

                file.setLength(newDataStart + pageDataSize)
            }

            // Write header
            file.seek(0)

            val headerBuffer = ByteBuffer.allocate(XTC_HEADER_SIZE)
            headerBuffer.order(ByteOrder.LITTLE_ENDIAN)
            headerBuffer.putInt(MAGIC_XTC)
            headerBuffer.putShort(XTC_VERSION)
            headerBuffer.putShort(pageCount.toShort())
            headerBuffer.put(readingDirection.toByte())
            headerBuffer.put(if (hasMetadata) 1 else 0)
            headerBuffer.put(0)  // hasThumbnails
            headerBuffer.put(if (hasChapters) 1 else 0)
            headerBuffer.putInt(0)  // currentPage
            headerBuffer.putLong(metadataOffset)
            headerBuffer.putLong(indexOffset)
            headerBuffer.putLong(actualDataOffset)
            headerBuffer.putLong(0)  // thumbOffset
            file.write(headerBuffer.array())

            // Write metadata
            if (hasMetadata) {
                val metaBuffer = ByteBuffer.allocate(XTC_METADATA_SIZE)
                metaBuffer.order(ByteOrder.LITTLE_ENDIAN)

                val titleBytes = metadata.title.take(127).toByteArray(Charsets.UTF_8)
                metaBuffer.put(titleBytes)
                metaBuffer.position(128)

                val authorBytes = metadata.author.take(63).toByteArray(Charsets.UTF_8)
                metaBuffer.put(authorBytes)
                metaBuffer.position(192)

                val publisherBytes = metadata.publisher.take(31).toByteArray(Charsets.UTF_8)
                metaBuffer.put(publisherBytes)
                metaBuffer.position(224)

                val langBytes = metadata.language.take(15).toByteArray(Charsets.UTF_8)
                metaBuffer.put(langBytes)
                metaBuffer.position(240)

                metaBuffer.putInt(metadata.createTime.toInt())
                metaBuffer.putShort(metadata.coverPage.toShort())
                metaBuffer.putShort(chapters.size.toShort())
                metaBuffer.putLong(0)  // reserved

                file.write(metaBuffer.array())
            }

            // Write chapters
            for (chapter in chapters) {
                val chapterBuffer = ByteBuffer.allocate(XTC_CHAPTER_SIZE)
                chapterBuffer.order(ByteOrder.LITTLE_ENDIAN)

                val nameBytes = chapter.name.take(79).toByteArray(Charsets.UTF_8)
                chapterBuffer.put(nameBytes)
                chapterBuffer.position(80)
                chapterBuffer.putShort(chapter.startPage.toShort())
                chapterBuffer.putShort(chapter.endPage.toShort())
                chapterBuffer.putInt(0)
                chapterBuffer.putInt(0)
                chapterBuffer.putInt(0)

                file.write(chapterBuffer.array())
            }

            // Write index table
            var currentOffset = actualDataOffset
            for (pageSize in pageSizes) {
                val entryBuffer = ByteBuffer.allocate(XTC_INDEX_ENTRY_SIZE)
                entryBuffer.order(ByteOrder.LITTLE_ENDIAN)
                entryBuffer.putLong(currentOffset)
                entryBuffer.putInt(pageSize)
                entryBuffer.putShort(width.toShort())
                entryBuffer.putShort(height.toShort())
                file.write(entryBuffer.array())
                currentOffset += pageSize.toLong()
            }

            file.close()
            raf = null
        }

        fun close() {
            raf?.close()
            raf = null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private - Image Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }
    }

    private fun bitmapToGrayscale(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return FloatArray(pixels.size) { i ->
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private - Quantization
    // ═══════════════════════════════════════════════════════════════════════════

    private fun quantize2Level(
        gray: FloatArray,
        width: Int,
        height: Int,
        threshold: Int
    ): ByteArray {
        return ByteArray(gray.size) { i ->
            if (gray[i] < threshold) 0 else 1
        }
    }

    private fun quantize4Level(
        gray: FloatArray,
        width: Int,
        height: Int,
        t1: Int,
        t2: Int,
        t3: Int
    ): ByteArray {
        return ByteArray(gray.size) { i ->
            when {
                gray[i] < t1 -> 0
                gray[i] < t2 -> 1
                gray[i] < t3 -> 2
                else -> 3
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private - Floyd-Steinberg Dithering
    // ═══════════════════════════════════════════════════════════════════════════

    private fun floydSteinbergDither2Level(
        gray: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        strength: Float
    ): ByteArray {
        val working = gray.copyOf()
        val result = ByteArray(width * height)

        val wRight = (7f / 16f) * strength
        val wBottomLeft = (3f / 16f) * strength
        val wBottom = (5f / 16f) * strength
        val wBottomRight = (1f / 16f) * strength

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = working[idx]

                val newVal: Float
                val level: Byte
                if (oldVal < threshold) {
                    level = 0
                    newVal = 0f
                } else {
                    level = 1
                    newVal = 255f
                }

                result[idx] = level
                val error = oldVal - newVal

                if (x + 1 < width) {
                    working[idx + 1] += error * wRight
                }
                if (y + 1 < height) {
                    if (x > 0) {
                        working[idx + width - 1] += error * wBottomLeft
                    }
                    working[idx + width] += error * wBottom
                    if (x + 1 < width) {
                        working[idx + width + 1] += error * wBottomRight
                    }
                }
            }
        }

        return result
    }

    private fun floydSteinbergDither4Level(
        gray: FloatArray,
        width: Int,
        height: Int,
        t1: Float,
        t2: Float,
        t3: Float,
        strength: Float
    ): ByteArray {
        val working = gray.copyOf()
        val result = ByteArray(width * height)

        val wRight = (7f / 16f) * strength
        val wBottomLeft = (3f / 16f) * strength
        val wBottom = (5f / 16f) * strength
        val wBottomRight = (1f / 16f) * strength

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = working[idx].coerceIn(0f, 255f)

                // Find nearest level
                val (_, newVal) = when {
                    oldVal < t1 -> 0.toByte() to 0f
                    oldVal < t2 -> 1.toByte() to 85f
                    oldVal < t3 -> 2.toByte() to 170f
                    else -> 3.toByte() to 255f
                }
                
                // Map to 0, 1, 2, 3
                val level = when {
                    oldVal < t1 -> 0.toByte()
                    oldVal < t2 -> 1.toByte()
                    oldVal < t3 -> 2.toByte()
                    else -> 3.toByte()
                }

                result[idx] = level
                val error = oldVal - newVal

                if (x + 1 < width) {
                    working[idx + 1] += error * wRight
                }
                if (y + 1 < height) {
                    if (x > 0) {
                        working[idx + width - 1] += error * wBottomLeft
                    }
                    working[idx + width] += error * wBottom
                    if (x + 1 < width) {
                        working[idx + width + 1] += error * wBottomRight
                    }
                }
            }
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private - Bitplane Encoding
    // ═══════════════════════════════════════════════════════════════════════════

    private fun encodeXthBitplanes(
        quantized: ByteArray,
        width: Int,
        height: Int
    ): Pair<ByteArray, ByteArray> {
        // Map pixel values to Xteink LUT (swap middle values)
        // 0 -> 0 (white), 1 -> 2 (dark grey), 2 -> 1 (light grey), 3 -> 3 (black)
        val lutMap = intArrayOf(0, 2, 1, 3)

        // Apply LUT mapping and invert
        val mapped = ByteArray(quantized.size) { i ->
            (3 - lutMap[quantized[i].toInt()]).toByte()
        }

        val bytesPerColumn = (height + 7) / 8
        val planeSize = width * bytesPerColumn

        val plane1 = ByteArray(planeSize)
        val plane2 = ByteArray(planeSize)

        var byteIndex = 0

        // Scan columns from right to left
        for (x in (width - 1) downTo 0) {
            for (yGroup in 0 until bytesPerColumn) {
                var byte1 = 0
                var byte2 = 0

                for (i in 0 until 8) {
                    val y = yGroup * 8 + i
                    if (y < height) {
                        val pixelVal = mapped[y * width + x].toInt() and 0x03
                        val bit1 = (pixelVal shr 1) and 1  // High bit
                        val bit2 = pixelVal and 1          // Low bit

                        byte1 = byte1 or (bit1 shl (7 - i))
                        byte2 = byte2 or (bit2 shl (7 - i))
                    }
                }

                plane1[byteIndex] = byte1.toByte()
                plane2[byteIndex] = byte2.toByte()
                byteIndex++
            }
        }

        return plane1 to plane2
    }

    private fun encodeXtgBitmap(quantized: ByteArray, width: Int, height: Int): ByteArray {
        val bytesPerRow = (width + 7) / 8
        val bitmapData = ByteArray(bytesPerRow * height)

        for (y in 0 until height) {
            for (byteIdx in 0 until bytesPerRow) {
                var byteVal = 0
                for (bitIdx in 0 until 8) {
                    val x = byteIdx * 8 + bitIdx
                    if (x < width) {
                        val pixel = quantized[y * width + x].toInt() and 1
                        byteVal = byteVal or (pixel shl (7 - bitIdx))
                    }
                }
                bitmapData[y * bytesPerRow + byteIdx] = byteVal.toByte()
            }
        }

        return bitmapData
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private - Utilities
    // ═══════════════════════════════════════════════════════════════════════════

    private fun writeFixedString(buffer: ByteBuffer, str: String, size: Int) {
        val bytes = str.take(size - 1).toByteArray(Charsets.UTF_8)
        val startPos = buffer.position()
        buffer.put(bytes)
        while (buffer.position() < startPos + size) {
            buffer.put(0)
        }
    }

    private fun ByteArray.sum(): Long {
        var sum = 0L
        for (b in this) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }
}