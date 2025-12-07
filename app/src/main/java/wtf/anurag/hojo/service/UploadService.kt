package wtf.anurag.hojo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.os.PowerManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.launch
import wtf.anurag.hojo.MainActivity
import wtf.anurag.hojo.R
import wtf.anurag.hojo.data.TaskRepository
import wtf.anurag.hojo.data.model.TaskStatus

@AndroidEntryPoint
class UploadService : LifecycleService() {

    @Inject lateinit var taskRepository: TaskRepository

    private val CHANNEL_ID = "upload_channel"
    private val NOTIFICATION_ID = 1

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Setup locks
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hojo::UploadWakeLock")

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Hojo::UploadWifiLock")

        // Find existing non-deprecated way to observe or just observe the flow
        lifecycleScope.launch {
            taskRepository.tasks.collect { tasks ->
                val activeTask = tasks.find { it.status == TaskStatus.UPLOADING }
                if (activeTask != null) {
                    acquireLocks()
                    val notification = buildNotification(activeTask)
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == "START_UPLOAD") {
            val taskId = intent.getStringExtra("TASK_ID")
            val task = taskRepository.tasks.value.find { it.id == taskId }
            if (task != null && task.status == TaskStatus.UPLOADING) {
                // Immediately start foreground to satisfy the promise
                startForeground(NOTIFICATION_ID, buildNotification(task))
            }
        } else if (intent?.action == "CANCEL_TASK") {
            val taskId = intent.getStringExtra("TASK_ID")
            if (taskId != null) {
                taskRepository.cancelTask(taskId)
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(task: wtf.anurag.hojo.data.model.TaskItem): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading ${task.fileName}")
            .setContentText(formatProgress(task.progress, task.speedBytesPerSecond))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, (task.progress * 100).toInt(), false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(getPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                getCancelIntent(task.id)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(CHANNEL_ID, "Uploads", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getCancelIntent(taskId: String): PendingIntent {
        val intent =
                Intent(this, UploadService::class.java).apply {
                    action = "CANCEL_TASK"
                    putExtra("TASK_ID", taskId)
                }
        return PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatProgress(progress: Float, speed: Long): String {
        val percent = (progress * 100).toInt()
        val speedStr = formatBytes(speed) + "/s"
        return "$percent% • $speedStr"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
