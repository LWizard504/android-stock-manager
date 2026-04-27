package com.stakia.stockmanager

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val role: String? = null,
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
    val id: String = UUID.randomUUID().toString(),
    val sender_id: String = "",
    val content: String = "",
    val created_at: String = java.time.Instant.now().toString(),
    val recipient_id: String? = null,
    val group_id: String? = null,
    val file_url: String? = null,
    val file_type: String? = null, // image, audio, video, file, sticker
    val reply_to_id: String? = null,
    val deleted_for_users: List<String>? = emptyList()
)

fun getConversationId(uid1: String, uid2: String): String {
    return listOf(uid1, uid2).sorted().joinToString("--")
}
