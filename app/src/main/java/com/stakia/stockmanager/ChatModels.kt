package com.stakia.stockmanager

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.json.JsonObject

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val role: String? = null,
    val tenant_id: String? = null,
    val last_message: String? = null,
    val last_message_at: String? = null
)

@Serializable
data class ChatGroup(
    val id: String,
    val name: String,
    val icon_url: String? = null,
    val last_message: String? = null,
    val last_message_at: String? = null
)

@Serializable
data class ChatMessage(
    val id: String? = null,
    val sender_id: String? = null,
    val content: String? = null,
    val created_at: String? = null,
    val recipient_id: String? = null,
    val group_id: String? = null,
    val file_url: String? = null,
    val file_type: String? = null,
    val reply_to_id: String? = null,
    val reply_to_content: String? = null,
    val reply_to_sender: String? = null,
    val sender_name: String? = null,
    val sender_avatar: String? = null,
    val deleted_for_users: List<String>? = emptyList()
)


@Serializable
data class CallLog(
    val id: String = UUID.randomUUID().toString(),
    val caller_id: String,
    val receiver_id: String,
    val type: String, // voice, video
    val status: String, // missed, completed, cancelled
    val duration: Int = 0,
    val created_at: String = java.time.Instant.now().toString(),
    val caller_name: String? = null,
    val receiver_name: String? = null,
    val caller_avatar: String? = null,
    val receiver_avatar: String? = null,
    val is_group: Boolean = false,
    val group_id: String? = null
)

fun getConversationId(uid1: String, uid2: String): String {
    return listOf(uid1, uid2).sorted().joinToString("--")
}

data class ActiveCallData(
    val type: String, // voice or video
    var status: String, // calling, incoming, connected
    val partnerName: String,
    val offer: JsonObject? = null,
    val partnerAvatar: String? = null,
    val partnerId: String = "",
    val isGroup: Boolean = false,
    val groupId: String? = null,
    var logId: String? = null,
    var isMinimized: Boolean = false,
    var connectedAt: Long? = null
)

data class UserPresence(
    val isTyping: Boolean = false,
    val isRecording: Boolean = false,
    val typingIn: String? = null,
    val recordingIn: String? = null,
    val name: String? = null
)
