package com.stakia.stockmanager

import com.stakia.stockmanager.BuildConfig
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpdateManager {
    private const val REPO_URL = "https://raw.githubusercontent.com/LWizard504/android-stock-manager/main/APK"
    private const val VERSION_FILE_URL = "$REPO_URL/version.txt"
    private const val APK_URL = "$REPO_URL/app-release.apk"

    // Estados para la UI
    var downloadProgress by mutableStateOf(0f)
    var isDownloading by mutableStateOf(false)
    var downloadInfo by mutableStateOf("Preparando descarga...")
    
    var latestVersion by mutableStateOf("")
    private var isSearchingManual = mutableStateOf(false)

    @Composable
    fun CheckForUpdates(currentVersion: String, context: Context, onUpdateFound: () -> Unit) {
        var showDialog by remember { mutableStateOf(false) }
        var latestVersion by remember { mutableStateOf("") }
        var triggerCheck by remember { mutableStateOf(0) }

        val manualCheck by isSearchingManual

        LaunchedEffect(triggerCheck, manualCheck) {
            if (triggerCheck >= 0 || manualCheck) {
                val versionFromServer = fetchLatestVersion()
                if (versionFromServer != null && isNewerVersion(currentVersion, versionFromServer)) {
                    UpdateManager.latestVersion = versionFromServer
                    // Actualización automática: avisamos a la UI inmediatamente
                    onUpdateFound()
                } else if (manualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ya tienes la última versión (v$currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                }
                isSearchingManual.value = false
            }
        }
    }

    fun forceCheck() {
        isSearchingManual.value = true
    }

    suspend fun downloadAndInstall(context: Context) {
        isDownloading = true
        downloadProgress = 0f
        
        val client = HttpClient()
        try {
            // Usar cacheDir es más seguro para FileProvider y no requiere permisos de almacenamiento externo
            val file = File(context.cacheDir, "StockManagerUpdate.apk")
            if (file.exists()) file.delete()

            val response = client.get(APK_URL) {
                onDownload { bytesSentTotal, contentLength ->
                    val total = contentLength ?: 0L
                    if (total > 0L) {
                        downloadProgress = bytesSentTotal.toFloat() / total.toFloat()
                        val downloadedMB = bytesSentTotal / (1024 * 1024)
                        val totalMB = total / (1024 * 1024)
                        downloadInfo = "Descargando: $downloadedMB MB / $totalMB MB"
                    }
                }
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(8192)
                while (!packet.isEmpty) {
                    file.appendBytes(packet.readBytes())
                }
            }

            downloadInfo = "DESCARGA TERMINADA. Abriendo instalador..."
            installApk(context, file)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.util.Log.e("UpdateManager", "Error en descarga", e)
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } finally {
            client.close()
            isDownloading = false
        }
    }

    internal fun installApk(context: Context, file: File) {
        try {
            val authority = "com.stakia.stockmanager.updates.fileprovider"
            if (!file.exists() || file.length() == 0L) {
                android.util.Log.e("UpdateManager", "Archivo inválido o inexistente")
                return
            }

            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            context.startActivity(intent)
            android.util.Log.d("UpdateManager", "Intent de instalación lanzado correctamente")
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Error crítico al instalar", e)
            Toast.makeText(context, "Error al abrir APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            
            // Intento desesperado: si falla el Provider, intentar abrir la carpeta (esto es limitado en Android moderno)
            try {
                val browserIntent = Intent(Intent.ACTION_GET_CONTENT)
                browserIntent.type = "application/vnd.android.package-archive"
                context.startActivity(browserIntent)
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        val client = HttpClient()
        try {
            val response: HttpResponse = client.get(VERSION_FILE_URL)
            if (response.status.value == 200) {
                response.bodyAsText().trim()
            } else null
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentClean = current.replace(Regex("[^0-9.]"), "")
            val latestClean = latest.replace(Regex("[^0-9.]"), "")
            val currentParts = currentClean.split(".").map { it.toInt() }
            val latestParts = latestClean.split(".").map { it.toInt() }
            for (i in 0 until minOf(currentParts.size, latestParts.size)) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            latestParts.size > currentParts.size
        } catch (e: Exception) {
            latest != current
        }
    }
}
