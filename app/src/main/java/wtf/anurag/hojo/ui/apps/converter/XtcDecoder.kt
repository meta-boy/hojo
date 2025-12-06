package wtf.anurag.hojo.ui.apps.converter

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for XTC files to extract page bitmaps for preview.
 * Supports XTG (1-bit monochrome) and XTH (2-bit 4-level grayscale) formats.
 * Uses RandomAccessFile to avoid loading entire file into memory.
 */
object XtcDecoder {

    private const val XTC_HEADER_SIZE = 48
    private const val XTC_METADATA_SIZE = 256
    private const val XTC_INDEX_ENTRY_SIZE = 16
    private const val PAGE_HEADER_SIZE = 22

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
     * Uses RandomAccessFile to avoid loading entire file into memory.
     */
    fun readXtcInfo(file: File): XtcFileInfo {
        RandomAccessFile(file, "r").use { raf ->
            // Read XTC header (48 bytes)
            val headerBytes = ByteArray(XTC_HEADER_SIZE)
            raf.readFully(headerBytes)
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic) != "XTC\u0000") {
                throw IllegalArgumentException("Not a valid XTC file")
            }

            buffer.getShort() // version
            var pageCount = buffer.getShort().toInt() and 0xFFFF
            buffer.get() // readDirection
            val hasMetadata = buffer.get().toInt()
            buffer.get() // hasThumbnails
            buffer.get() // chaptersFlag
            buffer.getInt() // currentPage
            val metadataOffset = buffer.getLong().toInt()
            val indexOffset = buffer.getLong().toInt()
            val dataOffset = buffer.getLong().toInt()

            // Prefer computing pageCount from the actual index table size
            if (indexOffset > 0 && dataOffset > indexOffset) {
                val indexSize = dataOffset - indexOffset
                val computed = indexSize / XTC_INDEX_ENTRY_SIZE
                if (computed > 0 && computed < 50000) {
                    pageCount = computed
                }
            }

            var title = ""
            var author = ""

            // Read metadata if available
            if (hasMetadata != 0 && metadataOffset > 0 && metadataOffset < file.length()) {
                try {
                    raf.seek(metadataOffset.toLong())
                    val titleBytes = ByteArray(128)
                    raf.readFully(titleBytes)
                    title = String(titleBytes).trim('\u0000').trim()

                    val authorBytes = ByteArray(64)
                    raf.readFully(authorBytes)
                    author = String(authorBytes).trim('\u0000').trim()
                } catch (e: Exception) {
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
    }

    /**
     * Extracts a specific page from an XTC file as a Bitmap.
     * Uses RandomAccessFile to only read the required page data.
     */
    fun extractPage(file: File, pageIndex: Int): Bitmap? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read XTC header
                val headerBytes = ByteArray(XTC_HEADER_SIZE)
                raf.readFully(headerBytes)
                val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

                val magic = ByteArray(4)
                headerBuffer.get(magic)
                if (String(magic) != "XTC\u0000") return null

                headerBuffer.getShort() // version
                val pageCount = headerBuffer.getShort().toInt() and 0xFFFF
                headerBuffer.get() // readDirection
                headerBuffer.get() // hasMetadata
                headerBuffer.get() // hasThumbnails
                headerBuffer.get() // chaptersFlag
                headerBuffer.getInt() // currentPage
                headerBuffer.getLong() // metadataOffset
                val indexOffset = headerBuffer.getLong().toInt()

                // Validate pageIndex
                if (pageIndex < 0 || pageIndex >= pageCount) return null

                // Seek to index entry for this page
                raf.seek((indexOffset + pageIndex * XTC_INDEX_ENTRY_SIZE).toLong())
                val indexBytes = ByteArray(XTC_INDEX_ENTRY_SIZE)
                raf.readFully(indexBytes)
                val indexBuffer = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN)

                val pageOffset = indexBuffer.getLong()
                val pageSize = indexBuffer.getInt()

                // Validate page offset
                if (pageOffset <= 0 || pageOffset >= file.length()) return null

                // Seek to page data and read page header
                raf.seek(pageOffset)
                val pageHeaderBytes = ByteArray(PAGE_HEADER_SIZE)
                raf.readFully(pageHeaderBytes)
                val pageBuffer = ByteBuffer.wrap(pageHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)

                val pageType = ByteArray(4)
                pageBuffer.get(pageType)
                val pageTypeStr = String(pageType)

                val width = pageBuffer.getShort().toInt() and 0xFFFF
                val height = pageBuffer.getShort().toInt() and 0xFFFF
                pageBuffer.get() // colorMode
                pageBuffer.get() // compression
                val dataSize = pageBuffer.getInt()

                // Read only the page data we need
                val pageData = ByteArray(dataSize)
                raf.readFully(pageData)

                // Decode based on page type
                return when {
                    pageTypeStr.startsWith("XTG") -> decodeXtg(pageData, width, height)
                    pageTypeStr.startsWith("XTH") -> decodeXth(pageData, width, height)
                    else -> null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Decodes XTG (1-bit monochrome) page data to Bitmap.
     */
    private fun decodeXtg(pageData: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val bytesPerRow = (width + 7) / 8

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
    private fun decodeXth(pageData: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val bytesPerColumn = (height + 7) / 8
        val planeSize = width * bytesPerColumn

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
                val byte1 = if (byteIndex < planeSize) pageData[byteIndex].toInt() and 0xFF else 0
                val byte2 = if (byteIndex + planeSize < pageData.size) pageData[byteIndex + planeSize].toInt() and 0xFF else 0

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
