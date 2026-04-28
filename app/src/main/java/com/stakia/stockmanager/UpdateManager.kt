package com.stakia.stockmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateManager {
    private const val REPO_URL = "https://raw.githubusercontent.com/LWizard504/android-stock-manager/main/APK"
    private const val VERSION_FILE_URL = "$REPO_URL/version.txt"
    private const val APK_URL = "$REPO_URL/app-release.apk"

    // Estado global para controlar el diálogo
    private var isSearchingManual = mutableStateOf(false)

    @Composable
    fun CheckForUpdates(currentVersion: String, context: Context) {
        var showDialog by remember { mutableStateOf(false) }
        var latestVersion by remember { mutableStateOf("") }
        var triggerCheck by remember { mutableStateOf(0) } // Para forzar re-chequeo manual

        // Observar si se activa la búsqueda manual
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
                text = { Text("Hay una nueva versión disponible (v$latestVersion). ¿Deseas descargarla ahora?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))
                        context.startActivity(intent)
                    }) {
                        Text("Descargar")
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

    // Función para llamar desde el botón de Configuración
    fun forceCheck() {
        isSearchingManual.value = true
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
