package com.stakia.stockmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntOfExtra("notification_id", 0)
        val senderId = intent.getStringExtra("sender_id")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        if (action == "ACTION_DECLINE_CALL") {
            Log.d("CallAction", "Call declined from notification - Signaling background")
            
            val targetId = senderId ?: return
            
            // Send hangup signal in background
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val client = OkHttpClient()
                    val token = SupabaseManager.client.auth.currentAccessTokenOrNull()
                    
                    if (token != null) {
                        val json = org.json.JSONObject().apply {
                            put("to", targetId)
                        }
                        
                        val request = Request.Builder()
                            .url("https://api-stockm-call-service.onrender.com/send-hangup")
                            .post(json.toString().toRequestBody("application/json".toMediaType()))
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            Log.d("CallAction", "Hangup signal sent: ${response.isSuccessful}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallAction", "Error sending hangup signal", e)
                }
            }
        }
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return getIntExtra(name, defaultValue)
    }
}
