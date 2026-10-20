package com.mangaviewer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mangaviewer.app.ui.MangaRoot

class MainActivity : ComponentActivity() {

    /**
     * Android 13 (API 33) 起，下载进度通知必须先拿到 POST_NOTIFICATIONS。
     * 以前没有申请过，导致前台服务的通知被系统丢弃（下载照跑，但看不到进度）。
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞下载 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val container = (application as MangaApp).container
        setContent {
            MangaRoot(container)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
