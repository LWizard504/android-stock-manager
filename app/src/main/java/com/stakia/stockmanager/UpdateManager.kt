package com.stakia.stockmanager

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import androidx.compose.runtime.mutableStateOf

@Serializable
data class AppDownload(
    val version: String,
    val download_url: String,
    val platform: String,
    val is_active: Boolean
)

object UpdateManager {
    var downloadProgress = mutableStateOf(0f)
    var isDownloading = mutableStateOf(false)
    var currentDownloadId = mutableStateOf<Long?>(null)

    suspend fun checkForUpdates(currentVersion: String): AppDownload? {
        return withContext(Dispatchers.IO) {
            try {
                val latest = SupabaseManager.client.postgrest["app_downloads"]
                    .select {
                        filter {
                            eq("platform", "Android")
                            eq("is_active", true)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeSingleOrNull<AppDownload>()

                if (latest != null && isNewerVersion(latest.version, currentVersion)) {
                    latest
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun downloadAndInstall(context: Context, url: String, version: String, scope: kotlinx.coroutines.CoroutineScope) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        
        val request = DownloadManager.Request(uri)
            .setTitle("StockManager Update $version")
            .setDescription("Downloading secure binary update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "stockmanager_$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val id = downloadManager.enqueue(request)
        currentDownloadId.value = id
        isDownloading.value = true
        downloadProgress.value = 0f

        // Polling progress
        scope.launch(Dispatchers.IO) {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    
                    if (downloadedIdx != -1 && totalIdx != -1) {
                        val bytesDownloaded = cursor.getLong(downloadedIdx)
                        val bytesTotal = cursor.getLong(totalIdx)
                        if (bytesTotal > 0) {
                            val prog = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            withContext(Dispatchers.Main) {
                                downloadProgress.value = prog
                            }
                        }
                    }

                    if (statusIdx != -1) {
                        val status = cursor.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                }
                cursor.close()
                delay(500)
            }
        }

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val intentId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (intentId == id) {
                    isDownloading.value = false
                    installApk(ctx, version)
                    ctx.unregisterReceiver(this)
                }
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, version: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "stockmanager_$version.apk")
        // Note: DownloadManager usually saves to public Downloads if requested. 
        // We need to find the actual file path.
        
        // Simpler approach: find the file in public downloads
        val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "stockmanager_$version.apk")
        
        if (publicFile.exists()) {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", publicFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setDataAndType(contentUri, "application/vnd.android.package-archive")
            }
            context.startActivity(installIntent)
        }
    }
}
