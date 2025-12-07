package wtf.anurag.hojo.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import wtf.anurag.hojo.data.model.TaskItem
import wtf.anurag.hojo.data.model.TaskStatus

@Singleton
class TaskRepository
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val fileManagerRepository: FileManagerRepository,
        private val connectivityManager: EpaperConnectivityManager,
        private val connectivityRepository: ConnectivityRepository
) {
    private val TAG = "TaskRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private var currentUploadJob: Job? = null

    // Helper to update a specific task in the list
    private fun updateTask(taskId: String, transform: (TaskItem) -> TaskItem) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
    }

    init {
        // Watch for connectivity changes to resume queue
        scope.launch {
            connectivityManager.isConnected.collect { isConnected ->
                if (isConnected) {
                    processQueue()
                }
            }
        }
    }

    fun addTask(uri: Uri, fileName: String, targetPath: String) {
        val newTask =
                TaskItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        fileName = fileName,
                        targetPath = targetPath,
                        status = TaskStatus.QUEUED
                )
        _tasks.value = _tasks.value + newTask
        Log.d(TAG, "Added task: ${newTask.id} (${newTask.fileName})")
        processQueue()
    }

    fun cancelTask(taskId: String) {
        // If it's the current running task, cancel the job
        val currentTask = _tasks.value.find { it.id == taskId }

        if (currentTask?.status == TaskStatus.UPLOADING) {
            currentUploadJob?.cancel()
            currentUploadJob = null
            Log.d(TAG, "Cancelled running task: $taskId")
        }

        updateTask(taskId) { it.copy(status = TaskStatus.CANCELLED) }

        // After cancelling, try processing next in queue
        scope.launch {
            delay(500) // Give a moment for cleanup
            processQueue()
        }
    }

    fun clearHistory() {
        _tasks.value =
                _tasks.value.filter {
                    it.status == TaskStatus.QUEUED || it.status == TaskStatus.UPLOADING
                }
    }

    fun retryTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELLED) {
            updateTask(taskId) {
                it.copy(
                    status = TaskStatus.QUEUED,
                    error = null,
                    progress = 0f,
                    bytesTransferred = 0,
                    speedBytesPerSecond = 0
                )
            }
            Log.d(TAG, "Retrying task: $taskId")
            processQueue()
        }
    }

    private fun processQueue() {
        if (currentUploadJob?.isActive == true) return

        val nextTask =
                _tasks.value.filter { it.status == TaskStatus.QUEUED }.minByOrNull { it.timestamp }

        if (nextTask != null) {
            startUpload(nextTask)
        }
    }

    private fun startUpload(task: TaskItem) {
        Log.d(TAG, "Starting upload for task: ${task.id}")
        updateTask(task.id) { it.copy(status = TaskStatus.UPLOADING) }

        val intent =
                android.content.Intent(context, wtf.anurag.hojo.service.UploadService::class.java)
        intent.action = "START_UPLOAD"
        intent.putExtra("TASK_ID", task.id)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        currentUploadJob =
                scope.launch {
                    var tempFile: File? = null
                    try {
                        // 1. Prepare Network
                        if (!connectivityManager.isConnected.value) {
                            val connected = connectivityManager.connectToDevice()
                            if (!connected) {
                                throw Exception("Device not connected")
                            }
                        }

                        val baseUrl = connectivityRepository.deviceBaseUrl.value

                        // 2. Prepare File
                        val inputStream: InputStream? =
                                context.contentResolver.openInputStream(task.uri)
                        if (inputStream == null) {
                            throw Exception("Could not open file stream")
                        }

                        tempFile =
                                File(context.cacheDir, "upload_${System.currentTimeMillis()}.tmp")
                        val totalBytes = inputStream.available().toLong() // Estimate

                        FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }

                        // 3. Upload
                        var startTime = System.currentTimeMillis()
                        var lastUpdateTime = 0L
                        var lastBytesWritten = 0L

                        fileManagerRepository.uploadFile(
                                baseUrl = baseUrl,
                                file = tempFile,
                                targetPath = task.targetPath
                        ) { bytesWritten, total ->
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 500) {
                                val timeDeltaSec = (now - startTime) / 1000.0
                                val speed =
                                        if (timeDeltaSec > 0) (bytesWritten / timeDeltaSec).toLong()
                                        else 0L

                                updateTask(task.id) {
                                    it.copy(
                                            progress =
                                                    if (total > 0) bytesWritten.toFloat() / total
                                                    else 0f,
                                            bytesTransferred = bytesWritten,
                                            totalBytes = total,
                                            speedBytesPerSecond = speed
                                    )
                                }
                                lastUpdateTime = now
                                lastBytesWritten = bytesWritten
                            }
                        }

                        updateTask(task.id) {
                            it.copy(
                                    status = TaskStatus.COMPLETED,
                                    progress = 1f,
                                    bytesTransferred = it.totalBytes // Ensure full bytes shown
                            )
                        }
                        Log.d(TAG, "Upload completed for task: ${task.id}")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Log.d(TAG, "Upload cancelled for task: ${task.id}")
                            // Status already updated in cancelTask, but double check
                            updateTask(task.id) { it.copy(status = TaskStatus.CANCELLED) }
                        } else {
                            Log.e(TAG, "Upload failed for task: ${task.id}", e)
                            updateTask(task.id) {
                                it.copy(status = TaskStatus.FAILED, error = e.message)
                            }
                        }
                    } finally {
                        tempFile?.delete()
                        currentUploadJob = null
                        // Process next item
                        processQueue()
                    }
                }
    }
}
