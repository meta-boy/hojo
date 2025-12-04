package wtf.anurag.hojo.ui.apps.converter

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for XTC files to extract page bitmaps for preview.
 * Supports XTG (1-bit monochrome) and XTH (2-bit 4-level grayscale) formats.
 */
object XtcDecoder {

    data class XtcFileInfo(
        val title: String,
        val author: String,
        val pageCount: Int,
        val totalSize: Long
    )

    data class PageInfo(
        val pageNumber: Int,
        val width: Int,
        val height: Int,
        val colorMode: String,
        val dataSize: Int
    )

    /**
     * Reads XTC file header and extracts metadata.
     */
    fun readXtcInfo(file: File): XtcFileInfo {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read XTC header (48 bytes)
        val magic = ByteArray(4)
        buffer.get(magic)
        if (String(magic) != "XTC\u0000") {
            throw IllegalArgumentException("Not a valid XTC file")
        }

        // version and pageCount are 2-byte shorts in the encoder
        val version = buffer.getShort().toInt() and 0xFFFF
        var pageCount = buffer.getShort().toInt() and 0xFFFF

        // read remaining header fields to get metadata offset
        val readDirection = buffer.get().toInt()
        val hasMetadata = buffer.get().toInt()
        val hasThumbnails = buffer.get().toInt()
        val chaptersFlag = buffer.get().toInt()
        val currentPage = buffer.getInt()
        val metadataOffset = buffer.getLong().toInt()
        val indexOffset = buffer.getLong().toInt()
        val dataOffset = buffer.getLong().toInt()
        val thumbOffset = buffer.getLong().toInt()

        // Prefer computing pageCount from the actual index table size when available
        try {
            if (indexOffset > 0 && dataOffset > indexOffset) {
                val indexSize = dataOffset - indexOffset
                val computed = indexSize / 16
                if (computed > 0 && computed < 50000) {
                    pageCount = computed
                }
            }
        } catch (_: Exception) {
            // ignore and keep header pageCount
        }

        var title = ""
        var author = ""

        // If metadata exists, jump to metadataOffset and read title/author according to encoder layout
        if (hasMetadata != 0 && metadataOffset > 0 && metadataOffset < bytes.size) {
            try {
                buffer.position(metadataOffset)
                val titleBytes = ByteArray(128)
                buffer.get(titleBytes)
                title = String(titleBytes).trim('\u0000').trim()

                val authorBytes = ByteArray(64)
                buffer.get(authorBytes)
                author = String(authorBytes).trim('\u0000').trim()
            } catch (e: Exception) {
                // ignore and leave empty
                e.printStackTrace()
            }
        }

        return XtcFileInfo(
            title = title,
            author = author,
            pageCount = pageCount,
            totalSize = file.length()
        )
    }

    /**
     * Extracts a specific page from an XTC file as a Bitmap.
     */
    fun extractPage(file: File, pageIndex: Int): Bitmap? {
        try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Read header to obtain indexOffset (don't assume fixed positions)
            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic) != "XTC\u0000") return null

            buffer.getShort() // version
            val pageCount = buffer.getShort().toInt() and 0xFFFF
            buffer.get() // readDirection
            buffer.get() // hasMetadata
            buffer.get() // hasThumbnails
            buffer.get() // chaptersFlag
            buffer.getInt() // currentPage
            val metadataOffset = buffer.getLong().toInt()
            val indexOffset = buffer.getLong().toInt()
            val dataOffset = buffer.getLong().toInt()
            val thumbOffset = buffer.getLong().toInt()

            // Validate pageIndex
            if (pageIndex < 0 || pageIndex >= pageCount) return null

            // Read index entry (16 bytes per page) at indexOffset
            val pageIndexOffset = indexOffset
            buffer.position(pageIndexOffset + (pageIndex * 16))

            val pageOffset = buffer.getLong().toInt()
            val pageSize = buffer.getInt()
            val idxWidth = buffer.getShort().toInt() and 0xFFFF
            val idxHeight = buffer.getShort().toInt() and 0xFFFF

            // Jump to page data
            if (pageOffset <= 0 || pageOffset >= bytes.size) return null
            buffer.position(pageOffset)

            // Read page header (22 bytes for XTG/XTH)
            val pageType = ByteArray(4)
            buffer.get(pageType)
            val pageTypeStr = String(pageType)

            val width = buffer.getShort().toInt() and 0xFFFF
            val height = buffer.getShort().toInt() and 0xFFFF
            buffer.get() // colorMode
            buffer.get() // compression
            val dataSize = buffer.getInt()
            buffer.getLong() // md5

            // Decode based on page type
            return when {
                pageTypeStr.startsWith("XTG") -> decodeXtg(buffer, width, height, dataSize)
                pageTypeStr.startsWith("XTH") -> decodeXth(buffer, width, height, dataSize)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Decodes XTG (1-bit monochrome) page data to Bitmap.
     */
    private fun decodeXtg(buffer: ByteBuffer, width: Int, height: Int, dataSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val bytesPerRow = (width + 7) / 8
        val pageData = ByteArray(dataSize)
        buffer.get(pageData)

        // Decode: MSB first, row-major order, 0=Black, 1=White
        for (y in 0 until height) {
            for (x in 0 until width) {
                val byteIdx = y * bytesPerRow + (x / 8)
                val bitIdx = 7 - (x % 8)

                if (byteIdx < pageData.size) {
                    val isWhite = ((pageData[byteIdx].toInt() shr bitIdx) and 1) == 1
                    pixels[y * width + x] = if (isWhite) Color.WHITE else Color.BLACK
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Decodes XTH (2-bit 4-level grayscale) page data to Bitmap.
     */
    private fun decodeXth(buffer: ByteBuffer, width: Int, height: Int, dataSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val bytesPerColumn = (height + 7) / 8
        val planeSize = width * bytesPerColumn

        val plane1 = ByteArray(planeSize)
        val plane2 = ByteArray(planeSize)

        buffer.get(plane1)
        buffer.get(plane2)

        // LUT mapping (with swapped values 1 and 2)
        // 0 (00): White (255)
        // 1 (01): Dark Grey (85)
        // 2 (10): Light Grey (170)
        // 3 (11): Black (0)
        fun pixelValueToGray(pixelValue: Int): Int {
            return when (pixelValue) {
                0 -> 255  // White
                1 -> 85   // Dark Grey
                2 -> 170  // Light Grey
                3 -> 0    // Black
                else -> 255
            }
        }

        // Decode: vertical scan order, columns right to left, 8 vertical pixels per byte
        var byteIndex = 0
        for (x in (width - 1) downTo 0) {  // Right to left
            for (yGroup in 0 until bytesPerColumn) {
                val byte1 = plane1[byteIndex].toInt() and 0xFF
                val byte2 = plane2[byteIndex].toInt() and 0xFF

                for (bitPos in 0 until 8) {
                    val y = yGroup * 8 + bitPos
                    if (y < height) {
                        val shift = 7 - bitPos
                        val bit1 = (byte1 shr shift) and 1
                        val bit2 = (byte2 shr shift) and 1

                        val pixelValue = (bit1 shl 1) or bit2
                        val gray = pixelValueToGray(pixelValue)

                        pixels[y * width + x] = Color.rgb(gray, gray, gray)
                    }
                }

                byteIndex++
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Gets the total number of pages in an XTC file.
     */
    fun getPageCount(file: File): Int {
        return try {
            readXtcInfo(file).pageCount
        } catch (e: Exception) {
            0
        }
    }
}
