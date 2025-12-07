package wtf.anurag.hojo.data.model

import android.net.Uri

enum class TaskStatus {
    QUEUED,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TaskItem(
        val id: String,
        val uri: Uri,
        val fileName: String,
        val targetPath: String,
        val status: TaskStatus = TaskStatus.QUEUED,
        val progress: Float = 0f,
        val speedBytesPerSecond: Long = 0,
        val totalBytes: Long = 0,
        val bytesTransferred: Long = 0,
        val error: String? = null,
        val timestamp: Long = System.currentTimeMillis()
)
