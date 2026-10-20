package com.mangaviewer.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mangaviewer.app.MainActivity
import com.mangaviewer.app.MangaApp
import com.mangaviewer.app.R
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_QUEUED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_RUNNING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 下载前台服务：让 app 切后台 / 锁屏时下载不被系统回收。
 *
 * 服务本身不下载，只把 [com.mangaviewer.app.data.repo.DownloadRepository] 的进度投影到一条
 * 常驻通知上；下载仍由仓库的协程池执行。没有任何排队/进行中任务时自动 stopSelf。
 *
 * 注意：启动顺序是 onStartCommand -> 复位残留任务 -> 开始下载 -> 再 startForeground，
 * 这样 onCreate 里不会因为"任务表还是空的"而立刻把自己停掉。
 */
class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var watchJob: Job? = null
    private var started = false

    private val container by lazy { (application as MangaApp).container }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            container.downloads.pauseAll()
            stopSelfSafe()
            return START_NOT_STICKY
        }
        Log.i(TAG, "onStartCommand")
        startWatch()
        if (intent?.action != ACTION_STOP) {
            scope.launch {
                // 冷启动 / 被动重启：把残留的 RUNNING、QUEUED 复位后再重新排队继续。
                container.downloads.resumeInterrupted()
                container.downloads.startAll()
            }
        }
        return START_STICKY
    }

    private fun startWatch() {
        if (watchJob != null) return
        startForegroundCompat("准备下载…")
        watchJob = scope.launch {
            Log.i(TAG, "watch started")
            var idleTicks = 0
            container.downloads.observeAll().collectLatest { list ->
                val active = list.filter {
                    it.status == DOWNLOAD_STATUS_RUNNING || it.status == DOWNLOAD_STATUS_QUEUED
                }
                Log.d(TAG, "tick size=" + list.size + " active=" + active.size + " idle=" + idleTicks)
                if (active.isEmpty()) {
                    // enqueue 和 runTask 置 RUNNING 之间有几十毫秒窗口，这里看到空队列不代表没任务。
                    // 连续 [IDLE_TICKS_BEFORE_STOP] 次空才收工，且收工前再确认一次数据库真实状态。
                    idleTicks++
                    if (idleTicks >= IDLE_TICKS_BEFORE_STOP) {
                        val stillActive = container.downloads.hasActiveTasks()
                        Log.i(TAG, "idle check stillActive=" + stillActive)
                        if (!stillActive) stopSelfSafe()
                    }
                    return@collectLatest
                }
                idleTicks = 0
                val done = active.sumOf { it.donePages }
                val total = active.sumOf { it.totalPages }
                val progress = if (total > 0) (done * 100 / total) else 0
                val title =
                    if (active.size == 1) active.first().title else "${active.size} 个任务下载中"
                startForegroundCompat("$title · 已完成 $done/$total 页")
                updateNotification("$title · 已完成 $done/$total 页", progress)
            }
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(text: String) {
        if (started) {
            updateNotification(text, -1)
            return
        }
        val notification = build(text, -1)
        Log.i(TAG, "startForeground type=dataSync")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            started = true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
        }
    }

    private fun updateNotification(text: String, progress: Int) {
        if (!started) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, build(text, progress)) }
    }

    private fun build(text: String, progress: Int): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this,
            101,
            Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("漫画下载")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(0, "全部暂停", pause)
        if (progress >= 0) builder.setProgress(100, progress.coerceIn(0, 100), false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun stopSelfSafe() {
        started = false
        watchJob?.cancel()
        watchJob = null
        // 只回收服务，不要 stopForeground：收工时通知随后会随进程结束消失。
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示漫画下载进度"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIFICATION_ID = 0x4d41
        private const val IDLE_TICKS_BEFORE_STOP = 3

        const val ACTION_START = "com.mangaviewer.app.download.START"
        const val ACTION_STOP = "com.mangaviewer.app.download.STOP"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "start failed", it) }
            Log.i(TAG, "requested start")
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
