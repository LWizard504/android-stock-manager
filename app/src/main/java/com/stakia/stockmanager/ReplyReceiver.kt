package com.stakia.stockmanager

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val replyText = remoteInput.getCharSequence("key_text_reply")?.toString()
            val senderId = intent.getStringExtra("sender_id") ?: ""
            val notificationId = intent.getIntExtra("notification_id", 0)

            if (!replyText.isNullOrBlank() && senderId.isNotBlank()) {
                sendReply(context, senderId, replyText, notificationId)
            }
        }
    }

    private fun sendReply(context: Context, targetId: String, text: String, notificationId: Int) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // Obtenemos el perfil actual para el nombre del remitente
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                val currentUserId = currentUser?.id ?: ""
                
                // Enviamos a través de la API (Bridge) para asegurar persistencia y broadcast
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("to", targetId)
                    put("content", text)
                    put("senderName", "Me (via Node)")
                    put("isGroup", false)
                }

                // Necesitamos el token para la API si tiene seguridad, pero por ahora usamos el endpoint directo
                // El servidor debe estar preparado para recibir este tipo de peticiones rápidas
                val request = Request.Builder()
                    .url("https://api-stockm-call-service.onrender.com/send-direct-message")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute()

                // Actualizar notificación para indicar éxito
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
