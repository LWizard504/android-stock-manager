package com.stakia.stockmanager

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
                    latestVersion = versionFromServer
                    showDialog = true
                } else if (manualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ya tienes la última versión (v$currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                }
                isSearchingManual.value = false
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Actualización Disponible") },
                text = { Text("Hay una nueva versión disponible (v$latestVersion). ¿Deseas actualizar ahora?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        onUpdateFound() // Avisamos a la UI para que cambie a la pantalla de descarga
                    }) {
                        Text("Actualizar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Más tarde")
                    }
                }
            )
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
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (file.exists()) file.delete()

            val response = client.get(APK_URL) {
                onDownload { bytesSentTotal, contentLength ->
                    if (contentLength > 0) {
                        downloadProgress = bytesSentTotal.toFloat() / contentLength
                        val downloadedMB = bytesSentTotal / (1024 * 1024)
                        val totalMB = contentLength / (1024 * 1024)
                        downloadInfo = "Descargando: $downloadedMB MB / $totalMB MB"
                    }
                }
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    file.appendBytes(packet.readBytes())
                }
            }

            downloadInfo = "Descarga completada. Iniciando instalación..."
            installApk(context, file)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error en la descarga: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            client.close()
            isDownloading = false
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al iniciar instalación", Toast.LENGTH_LONG).show()
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
