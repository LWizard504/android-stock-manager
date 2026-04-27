package com.stakia.stockmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Token logic will be handled by MainActivity/SignalingManager 
        // to save it to Supabase when user is logged in
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM", "Message received: ${remoteMessage.data}")

        val rawTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Nueva notificación"
        val senderName = remoteMessage.data["sender_name"]
        val title = if (senderName != null) "Nueva notificación de: $senderName" else rawTitle
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "Has recibido un mensaje"
        val type = remoteMessage.data["type"]
        val senderId = remoteMessage.data["sender_id"]
        val callType = remoteMessage.data["call_type"]
        val offer = remoteMessage.data["offer"]
        val senderAvatar = remoteMessage.data["sender_avatar"]

        showNotification(title, message, type, senderId, callType, offer, senderName, senderAvatar)
    }

    private fun showNotification(title: String, message: String, type: String?, senderId: String?, callType: String? = null, offer: String? = null, senderName: String? = null, senderAvatar: String? = null) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (type == "call") "stock_manager_calls_v3" else "stock_manager_notifications"
        val notificationId = if (type == "call") 1001 else System.currentTimeMillis().toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (type == "call") NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, if (type == "call") "Incoming Calls" else "Notifications", importance).apply {
                description = "Unified Communications Channel"
                if (type == "call") {
                    setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build())
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", type)
            putExtra("sender_id", senderId)
            putExtra("call_type", callType)
            putExtra("offer", offer)
            putExtra("sender_name", senderName)
            putExtra("sender_avatar", senderAvatar)
            if (type == "call") putExtra("is_incoming_call", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = androidx.core.app.Person.Builder()
            .setName(senderName ?: "Neural Node")
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(if (type == "call") android.R.drawable.stat_sys_phone_call else android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (type == "call") NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setSilent(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (type == "call") {
            // Accept Action
            val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = "ACTION_ACCEPT_CALL"
                putExtra("notification_id", notificationId)
                putExtra("sender_id", senderId)
                putExtra("call_type", callType)
                putExtra("offer", offer)
                putExtra("sender_name", senderName)
                putExtra("sender_avatar", senderAvatar)
            }
            val acceptPendingIntent = PendingIntent.getBroadcast(this, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            
            // Decline Action
            val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = "ACTION_DECLINE_CALL"
                putExtra("notification_id", notificationId)
                putExtra("sender_id", senderId)
            }
            val declinePendingIntent = PendingIntent.getBroadcast(this, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // High Priority Professional CallStyle
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(person, declinePendingIntent, acceptPendingIntent)
                    .setVerificationText("Secure Protocol")
            )
            builder.setOngoing(true)
            builder.setFullScreenIntent(pendingIntent, true)
            builder.setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI)
            builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
        } else {
            builder.setContentIntent(pendingIntent)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            // Quick Reply could be added here if needed
        }

        notificationManager.notify(notificationId, builder.build())
    }
}
