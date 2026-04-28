package com.stakia.stockmanager

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.util.Collections

object SignalingManager {
    private var socket: Socket? = null
    private const val SERVER_URL = "https://api-stockm-call-service.onrender.com"

    var onNewMessageListener: ((JSONObject) -> Unit)? = null

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun connect(
        token: String,
        userId: String, 
        groupIds: List<String> = emptyList(),
        onSignal: (JSONObject) -> Unit, 
        onHangup: (JSONObject) -> Unit,
        onTyping: (String, String, Boolean, String) -> Unit = { _, _, _, _ -> },
        onRecording: (String, String, Boolean, String) -> Unit = { _, _, _, _ -> },
        onPresenceChange: (Set<String>) -> Unit = { _ -> },
        onUserOnline: (String) -> Unit = { _ -> },
        onUserOffline: (String) -> Unit = { _ -> },
        onNewMessage: (JSONObject) -> Unit = { _ -> }
    ) {
        try {
            val opts = IO.Options()
            opts.forceNew = true
            opts.reconnection = true
            opts.auth = Collections.singletonMap("token", token)
            
            socket = IO.socket(SERVER_URL, opts)
            
            socket?.on("new-message") { args ->
                val data = args[0] as JSONObject
                onNewMessage(data)
                onNewMessageListener?.invoke(data)
            }
            
            socket?.on(Socket.EVENT_CONNECT) {
                val data = JSONObject()
                data.put("userId", userId)
                val groupsArr = org.json.JSONArray()
                groupIds.forEach { groupsArr.put(it) }
                data.put("groups", groupsArr)
                socket?.emit("register", data)
            }
            
            socket?.on("signal") { args ->
                val data = args[0] as JSONObject
                onSignal(data)
            }
            
            socket?.on("hangup") { args ->
                val data = args[0] as JSONObject
                onHangup(data)
            }

            socket?.on("typing") { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val fromObj = data.optJSONObject("from") ?: return@on
                val fromId = fromObj.optString("id")
                val name = fromObj.optString("name", "Neural Node")
                val isTyping = data.optBoolean("isTyping", false)
                val to = data.optString("to", "")
                if (fromId != null) onTyping(fromId, name, isTyping, to)
            }

            socket?.on("recording") { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val fromObj = data.optJSONObject("from") ?: return@on
                val fromId = fromObj.optString("id")
                val name = fromObj.optString("name", "Neural Node")
                val isRecording = data.optBoolean("isRecording", false)
                val to = data.optString("to", "")
                if (fromId != null) onRecording(fromId, name, isRecording, to)
            }

            socket?.on("user-online") { args ->
                val userId = args[0] as String
                onUserOnline(userId)
            }

            socket?.on("user-offline") { args ->
                val userId = args[0] as String
                onUserOffline(userId)
            }

            socket?.on("online-users") { args ->
                val usersArray = args[0] as org.json.JSONArray
                val usersSet = mutableSetOf<String>()
                for (i in 0 until usersArray.length()) {
                    usersSet.add(usersArray.getString(i))
                }
                onPresenceChange(usersSet)
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                onPresenceChange(emptySet())
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) {
                // Potential to show a toast or notification in the UI
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendSignal(
        to: String, 
        fromId: String, 
        fromName: String, 
        fromAvatar: String? = null, 
        type: String, 
        offer: JSONObject? = null, 
        answer: JSONObject? = null, 
        candidate: JSONObject? = null, 
        isGroup: Boolean = false, 
        groupId: String? = null
    ) {
        val fromObj = JSONObject().apply {
            put("id", fromId)
            put("name", fromName)
            put("avatar", fromAvatar)
        }

        val payload = JSONObject()
        payload.put("from", fromObj)
        payload.put("type", type)
        if (offer != null) payload.put("offer", offer)
        if (answer != null) payload.put("answer", answer)
        if (candidate != null) payload.put("candidate", candidate)
        
        val message = JSONObject()
        message.put("to", to)
        message.put("from", fromObj) // Match PWA: root from is an object
        message.put("type", type)
        message.put("payload", payload)
        message.put("isGroup", isGroup)
        if (groupId != null) message.put("groupId", groupId)
        
        socket?.emit("signal", message)
    }

    fun sendHangup(to: String, from: String, isGroup: Boolean = false, groupId: String? = null) {
        val data = JSONObject()
        data.put("to", to)
        data.put("from", from)
        data.put("isGroup", isGroup)
        if (groupId != null) data.put("groupId", groupId)
        socket?.emit("hangup", data)
    }

    fun sendTyping(to: String, fromId: String, fromName: String, isTyping: Boolean) {
        val data = JSONObject()
        data.put("to", to)
        data.put("from", JSONObject().apply { 
            put("id", fromId)
            put("name", fromName)
        })
        data.put("isTyping", isTyping)
        socket?.emit("typing", data)
    }

    fun sendRecording(to: String, fromId: String, fromName: String, isRecording: Boolean) {
        val data = JSONObject()
        data.put("to", to)
        data.put("from", JSONObject().apply { 
            put("id", fromId)
            put("name", fromName)
        })
        data.put("isRecording", isRecording)
        socket?.emit("recording", data)
    }

    fun sendMessage(
        to: String, 
        content: String, 
        senderName: String, 
        senderAvatar: String?, 
        isGroup: Boolean = false,
        tempId: String = java.util.UUID.randomUUID().toString(),
        fileUrl: String? = null,
        fileType: String? = null,
        replyTo: String? = null
    ) {
        val data = JSONObject().apply {
            put("to", to)
            put("content", content)
            put("senderName", senderName)
            put("senderAvatar", senderAvatar)
            put("isGroup", isGroup)
            put("tempId", tempId)
            put("fileUrl", fileUrl)
            put("fileType", fileType)
            put("replyTo", replyTo)
        }
        socket?.emit("send-message", data)
    }
}
