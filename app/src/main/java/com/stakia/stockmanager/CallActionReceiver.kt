package com.stakia.stockmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntOfExtra("notification_id", 0)
        val senderId = intent.getStringExtra("sender_id")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        if (action == "ACTION_ACCEPT_CALL") {
            Log.d("CallAction", "Call accepted from notification")
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("notification_type", "call")
                putExtra("sender_id", senderId)
                putExtra("call_type", intent.getStringExtra("call_type"))
                putExtra("offer", intent.getStringExtra("offer"))
                putExtra("sender_name", intent.getStringExtra("sender_name"))
                putExtra("sender_avatar", intent.getStringExtra("sender_avatar"))
                putExtra("action", "accept")
            }
            context.startActivity(mainIntent)
        } else if (action == "ACTION_DECLINE_CALL") {
            Log.d("CallAction", "Call declined from notification")
            // Here you could send a signal back to Supabase to reject the call
            // For now we just close the notification
        }
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return getIntExtra(name, defaultValue)
    }
}
