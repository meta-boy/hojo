package wtf.anurag.hojo.data.model

data class UploadProgress(
        val bytesUploaded: Long = 0,
        val totalBytes: Long = 0,
        val transferSpeedBytesPerSecond: Long = 0
) {
    val progressPercentage: Int
        get() = if (totalBytes > 0) ((bytesUploaded * 100) / totalBytes).toInt() else 0

    val transferSpeedFormatted: String
        get() {
            val speedKB = transferSpeedBytesPerSecond / 1024.0
            return if (speedKB < 1024) {
                String.format("%.1f KB/s", speedKB)
            } else {
                String.format("%.1f MB/s", speedKB / 1024.0)
            }
        }

    val isUploading: Boolean
        get() = bytesUploaded > 0 && bytesUploaded < totalBytes
}
