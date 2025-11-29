package wtf.anurag.hojo.data

import java.io.File
import java.io.IOException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source

class ProgressRequestBody(
        private val file: File,
        private val contentType: MediaType?,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        var bytesWritten = 0L
        var lastUpdate = 0L

        file.source().use { source ->
            val buffer = sink.buffer
            var read: Long
            while (source.read(buffer, SEGMENT_SIZE).also { read = it } != -1L) {
                bytesWritten += read
                // sink.flush() // Removed to let Okio handle buffering for better performance

                // Throttle progress updates (e.g., every 100ms or 1% change)
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 100 || bytesWritten == totalBytes) {
                    onProgress(bytesWritten, totalBytes)
                    lastUpdate = now
                }

                sink.emitCompleteSegments() // Ensure we don't buffer the whole file
            }
            sink.flush() // Flush only once at the end
        }
    }

    companion object {
        private const val SEGMENT_SIZE = 65536L // 64KB chunks
    }
}
