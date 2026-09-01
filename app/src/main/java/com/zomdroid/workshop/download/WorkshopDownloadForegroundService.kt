/* Foreground execution and notification for the persistent Workshop queue. */
package com.zomdroid.workshop.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zomdroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Runs and observes the persistent queue while Android gives the app foreground priority. */
class WorkshopDownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationHandler = Handler(Looper.getMainLooper())
    private lateinit var manager: DownloadCenterManager
    private var serviceStarted = false
    private var notificationScheduled = false
    private var latestTasks: List<DownloadCenterTask> = emptyList()
    private val scheduledNotification = Runnable {
        notificationScheduled = false
        if (serviceStarted) publishTaskNotification(latestTasks)
    }

    override fun onCreate() {
        super.onCreate()
        manager = DownloadCenterManagerProvider.get(this)
        createNotificationChannel()
        serviceScope.launch {
            manager.tasks.collect { tasks ->
                if (!serviceStarted) return@collect
                latestTasks = tasks
                if (notificationScheduled) return@collect
                notificationScheduled = true
                notificationHandler.postDelayed(scheduledNotification, NOTIFICATION_UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    private fun publishTaskNotification(tasks: List<DownloadCenterTask>) {
        val active = tasks.filter { it.state.isActive() }
                if (active.isEmpty()) {
                    tasks.lastOrNull { it.state == DownloadCenterTaskState.Failed }
                        ?.let(::publishFailureNotification)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    publishNotification(active)
                }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        serviceStarted = true
        when (intent?.action) {
            ACTION_PAUSE -> intent.taskId()?.let(manager::pause)
            ACTION_RESUME -> intent.taskId()?.let(manager::resume)
            ACTION_RETRY -> intent.taskId()?.let(manager::retry)
            ACTION_CANCEL -> intent.taskId()?.let(manager::cancel)
        }
        manager.start()
        return START_STICKY
    }

    override fun onDestroy() {
        notificationHandler.removeCallbacks(scheduledNotification)
        notificationScheduled = false
        manager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishNotification(active: List<DownloadCenterTask>) {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(active))
        } catch (_: SecurityException) {
            // Android 13+ can deny notification display; the foreground service still runs.
        }
    }

    private fun publishFailureNotification(task: DownloadCenterTask) {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val message = task.errorMessage ?: getString(R.string.workshop_download_notification_failed)
        try {
            notificationManager.notify(
                FAILURE_NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_workshop_download)
                    .setContentTitle(getString(R.string.workshop_download_notification_failed_title))
                    .setContentText("${task.publishedFileId}: $message")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        } catch (_: SecurityException) {
            // Notification permission is optional on Android 13+.
        }
    }

    private fun buildNotification(active: List<DownloadCenterTask>): Notification {
        val current = active.firstOrNull { it.state == DownloadCenterTaskState.Running }
            ?: active.firstOrNull()
        val text = when {
            current == null -> getString(R.string.workshop_download_notification_idle)
            current.title.isNullOrBlank() -> getString(
                R.string.workshop_download_notification_item,
                current.publishedFileId,
            )
            else -> current.title
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workshop_download)
            .setContentTitle(getString(R.string.workshop_download_notification_title))
            .setContentText(text)
            .setOngoing(active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val total = current?.totalBytes ?: 0L
        if (current != null && total > 0L) {
            val progress = (current.writtenBytes.coerceAtLeast(0L) * 100L / total)
                .coerceIn(0L, 100L)
                .toInt()
            builder.setProgress(100, progress, false)
        } else if (current != null) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.workshop_download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun DownloadCenterTaskState.isActive(): Boolean =
        this == DownloadCenterTaskState.Queued || this == DownloadCenterTaskState.Running

    private fun Intent.taskId(): String? = getStringExtra(EXTRA_TASK_ID)

    companion object {
        const val ACTION_START = "com.zomdroid.workshop.download.START"
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 500L
        const val ACTION_PAUSE = "com.zomdroid.workshop.download.PAUSE"
        const val ACTION_RESUME = "com.zomdroid.workshop.download.RESUME"
        const val ACTION_RETRY = "com.zomdroid.workshop.download.RETRY"
        const val ACTION_CANCEL = "com.zomdroid.workshop.download.CANCEL"
        const val EXTRA_TASK_ID = "com.zomdroid.workshop.download.TASK_ID"

        private const val CHANNEL_ID = "zomdroid_workshop_downloads"
        private const val NOTIFICATION_ID = 4243
        private const val FAILURE_NOTIFICATION_ID = 4244

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, WorkshopDownloadForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        @JvmStatic
        fun command(context: Context, action: String, taskId: String) {
            val intent = Intent(context, WorkshopDownloadForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}
