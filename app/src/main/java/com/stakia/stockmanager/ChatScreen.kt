package com.stakia.stockmanager

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.webrtc.*
import java.util.UUID
import java.io.File
import org.json.JSONObject
import com.stakia.stockmanager.SignalingManager
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.ui.draw.alpha

data class ActiveCallData(
    val type: String, // voice or video
    var status: String, // calling, incoming, connected
    val partnerName: String,
    val offer: JsonObject? = null,
    val partnerAvatar: String? = null,
    val partnerId: String = "",
    val isGroup: Boolean = false,
    val groupId: String? = null
)

data class UserPresence(
    val isTyping: Boolean = false,
    val isRecording: Boolean = false,
    val typingIn: String? = null,
    val recordingIn: String? = null,
    val name: String? = null
)

private val neuralJson = Json { 
    ignoreUnknownKeys = true 
    coerceInputValues = true
    encodeDefaults = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit, 
    userProfile: Profile? = null,
    initialChatId: String? = null,
    initialCallType: String? = null,
    initialOffer: String? = null,
    initialSenderName: String? = null,
    initialSenderAvatar: String? = null,
    initialAction: String? = null
) {
    val context = LocalContext.current
    var currentView by remember { mutableStateOf(if (initialChatId != null) "conversation" else "list") }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    var selectedGroup by remember { mutableStateOf<ChatGroup?>(null) }
    var profilesForGroup by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var viewingImageUrl by remember { mutableStateOf<String?>(null) }
    var activeCall by remember { mutableStateOf<ActiveCallData?>(null) }
    var presenceMap by remember { mutableStateOf<Map<String, UserPresence>>(emptyMap()) }
    var onlineUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groups by remember { mutableStateOf<List<ChatGroup>>(emptyList()) }
    
    val status by SupabaseManager.client.auth.sessionStatus.collectAsState(initial = SessionStatus.Initializing)
    val currentUser = (status as? SessionStatus.Authenticated)?.session?.user
    val currentUserId = currentUser?.id ?: ""
    
    val scope = rememberCoroutineScope()
    val rtcManager = remember { WebRTCManager(context) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val pureBlack = Color(0xFF000000)
    val cardBg = Color(0xFF080808)
    val accentYellow = Color(0xFFEAB308)

    DisposableEffect(Unit) {
        onDispose {
            SignalingManager.disconnect()
            rtcManager.stopAll()
        }
    }


    LaunchedEffect(currentUserId, groups) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        
        SignalingManager.disconnect() // Limpiar conexión previa
        println("Signaling: Connecting to Render API for $currentUserId")
        val token = SupabaseManager.client.auth.currentSessionOrNull()?.accessToken ?: ""
        SignalingManager.connect(
            token = token,
            userId = currentUserId,
            groupIds = groups.map { it.id },
            onSignal = { data ->
                scope.launch(Dispatchers.Main) {
                    try {
                        val fromObj = data.optJSONObject("from")
                        val fromId = fromObj?.optString("id") ?: data.optString("from") ?: ""
                        
                        if (fromId == currentUserId || fromId.isEmpty()) return@launch

                        val isGroup = data.optBoolean("isGroup", false)
                        val groupId = if (data.has("groupId")) data.optString("groupId") else null
                        val type = data.optString("type", "voice")
                        val fromName = fromObj?.optString("name", "Neural Node") ?: "Neural Node"
                        val fromAvatar = fromObj?.optString("avatar", null)
                        
                        if (data.has("offer")) {
                            println("Signaling: Received Offer from $fromId (Group: $isGroup)")
                            val offerObj = data.optJSONObject("offer") ?: return@launch
                            val offerJson = neuralJson.parseToJsonElement(offerObj.toString()).jsonObject
                            
                            if (activeCall == null) {
                                activeCall = ActiveCallData(type, "incoming", fromName, offerJson, fromAvatar, fromId, isGroup, groupId)
                            } else {
                                rtcManager.createPeerConnection(
                                    remoteUserId = fromId,
                                    onIceCandidate = { candidate ->
                                        scope.launch {
                                            val candObj = JSONObject().apply {
                                                put("sdpMLineIndex", candidate.sdpMLineIndex)
                                                put("sdpMid", candidate.sdpMid)
                                                put("candidate", candidate.sdp)
                                            }
                                            SignalingManager.sendSignal(fromId, currentUserId, "Me", null, type, null, null, candObj, isGroup, groupId)
                                        }
                                    },
                                    onTrackAdded = { }
                                )
                                val sdp = SessionDescription(SessionDescription.Type.OFFER, offerObj.getString("sdp"))
                                rtcManager.setRemoteDescription(fromId, sdp)
                                rtcManager.processQueuedIceCandidates(fromId)
                                val answer = rtcManager.createAnswer(fromId)
                                if (answer != null) {
                                    rtcManager.setLocalDescription(fromId, answer)
                                    val answerObj = JSONObject().apply {
                                        put("type", "answer")
                                        put("sdp", answer.description)
                                    }
                                    SignalingManager.sendSignal(fromId, currentUserId, "Me", null, type, null, answerObj, null, isGroup, groupId)
                                }
                            }
                        } else if (data.has("answer")) {
                            println("Signaling: Received Answer from $fromId")
                            val answerObj = data.optJSONObject("answer") ?: return@launch
                            val sdpStr = answerObj.getString("sdp")
                            val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                            rtcManager.setRemoteDescription(fromId, sdp)
                            rtcManager.processQueuedIceCandidates(fromId)
                            activeCall = activeCall?.copy(status = "connected")
                        } else if (data.has("candidate")) {
                            val candidateObj = data.optJSONObject("candidate") ?: return@launch
                            val sdpMid = candidateObj.optString("sdpMid")
                            val sdpMLineIndex = candidateObj.optInt("sdpMLineIndex")
                            val sdp = candidateObj.getString("candidate")
                            rtcManager.addIceCandidate(fromId, IceCandidate(sdpMid, sdpMLineIndex, sdp))
                        }
                    } catch (e: Exception) { 
                        println("Signaling Error: ${e.message}")
                    }

                }
            },
            onHangup = {
                scope.launch(Dispatchers.Main) {
                    println("Signaling: Hangup received")
                    rtcManager.stopAll()
                    activeCall = null
                }
            },
            onTyping = { fromId, name, isTyping, to ->
                scope.launch(Dispatchers.Main) {
                    presenceMap = presenceMap + (fromId to (presenceMap[fromId]?.copy(isTyping = isTyping, typingIn = to, name = name) ?: UserPresence(isTyping = isTyping, typingIn = to, name = name)))
                }
            },
            onRecording = { fromId, name, isRecording, to ->
                scope.launch(Dispatchers.Main) {
                    presenceMap = presenceMap + (fromId to (presenceMap[fromId]?.copy(isRecording = isRecording, recordingIn = to, name = name) ?: UserPresence(isRecording = isRecording, recordingIn = to, name = name)))
                }
            },
            onPresenceChange = { users ->
                scope.launch(Dispatchers.Main) {
                    onlineUsers = users
                }
            },
            onUserOnline = { userId ->
                scope.launch(Dispatchers.Main) {
                    onlineUsers = onlineUsers + userId
                }
            },
            onUserOffline = { userId ->
                scope.launch(Dispatchers.Main) {
                    onlineUsers = onlineUsers - userId
                }
            }
        )
    }


    LaunchedEffect(Unit) {
        try {
            groups = SupabaseManager.client.from("chat_groups").select {
                filter {
                    eq("chat_group_members.user_id", currentUserId)
                }
            }.decodeList<ChatGroup>()
            
            // Handle initial call if provided
            if (initialCallType != null && initialOffer != null && initialChatId != null) {
                val offerObj = neuralJson.parseToJsonElement(initialOffer).jsonObject
                activeCall = ActiveCallData(
                    type = initialCallType,
                    status = "incoming",
                    partnerName = initialSenderName ?: "Neural Node",
                    offer = offerObj,
                    partnerAvatar = initialSenderAvatar,
                    partnerId = initialChatId,
                    isGroup = initialChatId.startsWith("group:"), // Hypothetical or check logic
                    groupId = if (initialChatId.startsWith("group:")) initialChatId.replace("group:", "") else null
                )
                
                if (initialAction == "accept") {
                    // Trigger auto-accept logic
                    scope.launch {
                        delay(500) // Small delay to let RTC init
                        activeCall?.let { call ->
                            val offerSdp = call.offer?.get("sdp")?.jsonPrimitive?.content
                            if (offerSdp != null) {
                                rtcManager.setRemoteDescription(call.partnerId, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
                                rtcManager.processQueuedIceCandidates(call.partnerId)
                                val answer = rtcManager.createAnswer(call.partnerId)
                                if (answer != null) {
                                    rtcManager.setLocalDescription(call.partnerId, answer)
                                    val answerObj = org.json.JSONObject().apply {
                                        put("type", "answer")
                                        put("sdp", answer.description)
                                    }
                                    SignalingManager.sendSignal(call.partnerId, currentUserId, "Me", null, call.type, null, answerObj, null, call.isGroup, call.groupId)
                                    activeCall = activeCall?.copy(status = "connected")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(targetState = currentView, label = "view_transition") { view: String ->
            when (view) {
                "list" -> ChatListScreen(
                    onProfileClick = { selectedProfile = it; currentView = "conversation" },
                    onGroupClick = { selectedGroup = it; currentView = "conversation" },
                    onOpenDrawer = onOpenDrawer,
                    pureBlack = pureBlack,
                    accentYellow = accentYellow,
                    onlineUsers = onlineUsers,
                    groups = groups,
                    initialChatId = initialChatId,
                    onCreateGroup = {
                        scope.launch {
                            try {
                                profilesForGroup = SupabaseManager.client.from("profiles").select {
                                    filter { Profile::id neq currentUserId }
                                }.decodeList<Profile>()
                                currentView = "create_group"
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                )
                "create_group" -> CreateGroupScreen(
                    profiles = profilesForGroup,
                    onBack = { currentView = "list" },
                    onCreate = { name: String, memberIds: List<String> ->
                        scope.launch {
                            try {
                                // 1. Create Group
                                val newGroup = SupabaseManager.client.from("chat_groups").insert(
                                    buildJsonObject {
                                        put("name", name)
                                        put("created_by", currentUserId)
                                    }
                                ) { select() }.decodeSingle<ChatGroup>()
                                
                                // 2. Add Members
                                val membersToInsert = (memberIds + currentUserId).map { uid ->
                                    buildJsonObject {
                                        put("group_id", newGroup.id)
                                        put("user_id", uid)
                                    }
                                }
                                SupabaseManager.client.from("chat_group_members").insert(membersToInsert)
                                
                                withContext(Dispatchers.Main) {
                                    groups = groups + newGroup
                                    selectedGroup = newGroup
                                    selectedProfile = null
                                    currentView = "conversation"
                                }
                            } catch (e: Exception) { 
                                e.printStackTrace() 
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to create group", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    pureBlack = pureBlack,
                    cardBg = cardBg,
                    accentYellow = accentYellow
                )
                "conversation" -> ConversationScreen(
                    currentUserId = currentUserId,
                    userProfile = userProfile,
                    profile = selectedProfile,
                    group = selectedGroup,
                    onBack = { currentView = "list" },
                    onStartCall = { type ->
                        val targetName = selectedProfile?.full_name ?: selectedGroup?.name ?: "Neural Node"
                        val targetId = selectedProfile?.id ?: selectedGroup?.id ?: ""
                        val isGroup = selectedGroup != null
                        activeCall = ActiveCallData(type, "calling", targetName, null, selectedProfile?.avatar_url ?: selectedGroup?.icon_url, targetId)
                        
                        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasCamera || !hasAudio) {
                            callPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
                        }

                        scope.launch {
                            rtcManager.startLocalStreaming(type == "video")
                            
                            val targets = if (isGroup) {
                                SupabaseManager.client.from("chat_group_members")
                                    .select { filter { eq("group_id", targetId); neq("user_id", currentUserId) } }
                                    .decodeList<JsonObject>()
                                    .map { it["user_id"]?.jsonPrimitive?.content ?: "" }
                                    .filter { it.isNotEmpty() }
                            } else listOf(targetId)

                            targets.forEach { peerId ->
                                rtcManager.createPeerConnection(
                                    remoteUserId = peerId,
                                    onIceCandidate = { candidate ->
                                        scope.launch {
                                            val candObj = JSONObject().apply {
                                                put("sdpMLineIndex", candidate.sdpMLineIndex)
                                                put("sdpMid", candidate.sdpMid)
                                                put("candidate", candidate.sdp)
                                            }
                                            SignalingManager.sendSignal(peerId, currentUserId, "Me", null, type, null, null, candObj, isGroup, targetId)
                                        }
                                    },
                                    onTrackAdded = { 
                                        scope.launch(Dispatchers.Main) {
                                            activeCall = activeCall?.copy(status = "connected")
                                        }
                                    }
                                )
                                val offer = rtcManager.createOffer(peerId)
                                if (offer != null) {
                                    rtcManager.setLocalDescription(peerId, offer)
                                    val offerObj = JSONObject().apply {
                                        put("type", "offer")
                                        put("sdp", offer.description)
                                    }
                                    SignalingManager.sendSignal(peerId, currentUserId, "Me", null, type, offerObj, null, null, isGroup, targetId)
                                }
                            }
                        }
                    },
                    pureBlack = pureBlack,
                    cardBg = cardBg,
                    accentYellow = accentYellow,
                    presence = if (selectedProfile != null) {
                        presenceMap[selectedProfile?.id ?: ""]?.let { 
                            if (it.typingIn == currentUserId || it.recordingIn == currentUserId) it else UserPresence()
                        } ?: UserPresence()
                    } else {
                        presenceMap.values.find { it.typingIn == selectedGroup?.id || it.recordingIn == selectedGroup?.id } ?: UserPresence()
                    },
                    onTypingUpdate = { typing ->
                        val target = selectedProfile?.id ?: selectedGroup?.id ?: ""
                        val name = currentUser?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Neural Node"
                        if (target.isNotEmpty()) SignalingManager.sendTyping(target, currentUserId, name, typing)
                    },
                    onRecordingUpdate = { recording ->
                        val target = selectedProfile?.id ?: selectedGroup?.id ?: ""
                        val name = currentUser?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Neural Node"
                        if (target.isNotEmpty()) SignalingManager.sendRecording(target, currentUserId, name, recording)
                    },
                    onViewImage = { viewingImageUrl = it },
                    onGroupsUpdate = { groups = it }
                )
            }
        }

        activeCall?.let { call ->
            CallOverlay(
                callData = call,
                rtcManager = rtcManager,
                onHangup = { 
                    rtcManager.stopAll()
                    SignalingManager.sendHangup(call.partnerId, currentUserId)
                    activeCall = null 
                },
                onAccept = {
                    val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!hasCamera || !hasAudio) {
                        callPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
                    }
                    
                    activeCall = call.copy(status = "connected")
                    scope.launch {
                        rtcManager.startLocalStreaming(call.type == "video")
                        rtcManager.createPeerConnection(
                            remoteUserId = call.partnerId,
                            onIceCandidate = { candidate ->
                                scope.launch {
                                    val candObj = JSONObject().apply {
                                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                                        put("sdpMid", candidate.sdpMid)
                                        put("candidate", candidate.sdp)
                                    }
                                    SignalingManager.sendSignal(call.partnerId, currentUserId, "Me", null, call.type, null, null, candObj)
                                }
                            },
                            onTrackAdded = {
                                scope.launch(Dispatchers.Main) {
                                    activeCall = activeCall?.copy(status = "connected")
                                }
                            }
                        )
                        
                        val offerSdp = call.offer?.get("sdp")?.jsonPrimitive?.content
                        if (offerSdp != null) {
                            rtcManager.setRemoteDescription(call.partnerId, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
                            rtcManager.processQueuedIceCandidates(call.partnerId)
                            val answer = rtcManager.createAnswer(call.partnerId)
                            if (answer != null) {
                                rtcManager.setLocalDescription(call.partnerId, answer)
                                val answerObj = JSONObject().apply {
                                    put("type", "answer")
                                    put("sdp", answer.description)
                                }
                                SignalingManager.sendSignal(call.partnerId, currentUserId, "Me", null, call.type, null, answerObj, null, call.isGroup, call.groupId)
                            }
                        }
                    }
                }
            )
        }

        viewingImageUrl?.let { url ->
            FullImageOverlay(url = url, onDismiss = { viewingImageUrl = null })
        }
    }
}

@Composable
fun ChatListScreen(
    onProfileClick: (Profile) -> Unit,
    onGroupClick: (ChatGroup) -> Unit,
    onOpenDrawer: () -> Unit,
    pureBlack: Color,
    accentYellow: Color,
    onlineUsers: Set<String>,
    groups: List<ChatGroup>,
    initialChatId: String? = null,
    onCreateGroup: () -> Unit
) {
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        try {
            profiles = SupabaseManager.client.from("profiles").select {
                filter { Profile::id neq SupabaseManager.client.auth.currentUserOrNull()?.id }
            }.decodeList<Profile>()
            
            // Si venimos de una notificación, buscamos el perfil y lo seleccionamos automáticamente
            if (initialChatId != null) {
                profiles.find { it.id == initialChatId }?.let {
                    onProfileClick(it)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(pureBlack).statusBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("CHAT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                }
            }
        },
        containerColor = pureBlack
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            if (groups.isNotEmpty()) {
                item { SectionHeader("GROUPS", groups.size.toString()); Spacer(modifier = Modifier.height(12.dp)) }
                items(groups) { group -> GroupItem(group) { onGroupClick(group) }; Spacer(modifier = Modifier.height(8.dp)) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
            item { SectionHeader("USERS", profiles.size.toString()); Spacer(modifier = Modifier.height(12.dp)) }
            items(profiles) { profile -> UserItem(profile, onlineUsers.contains(profile.id)) { onProfileClick(profile) } }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = onCreateGroup,
            containerColor = accentYellow,
            contentColor = Color.Black,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.GroupAdd, contentDescription = "Create Group")
        }
    }
}

@Composable
fun ConversationScreen(
    currentUserId: String,
    userProfile: Profile?,
    profile: Profile?,
    group: ChatGroup?,
    onBack: () -> Unit,
    onStartCall: (String) -> Unit,
    pureBlack: Color,
    cardBg: Color,
    accentYellow: Color,
    presence: UserPresence,
    onTypingUpdate: (Boolean) -> Unit,
    onRecordingUpdate: (Boolean) -> Unit,
    onViewImage: (String) -> Unit,
    onGroupsUpdate: (List<ChatGroup>) -> Unit
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    val audioFile = remember { mutableStateOf<File?>(null) }
    val mediaRecorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedMessages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var linkPreview by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> attachment = uri }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    val channelId = if (profile != null) "neural-user-sync:$currentUserId" else "neural-group-sync:${group?.id}"
    
    fun handleSendMessage(content: String, fileUrl: String? = null, fileType: String? = null, replyTo: String? = null) {
        if (content.isBlank() && fileUrl == null) return
        
        val tempId = java.util.UUID.randomUUID().toString()
        val newMessage = ChatMessage(
            id = tempId,
            sender_id = currentUserId,
            content = content,
            created_at = java.time.Instant.now().toString(),
            file_url = fileUrl,
            file_type = fileType,
            recipient_id = if (profile != null) profile.id else null,
            group_id = if (group != null) group.id else null,
            sender_name = userProfile?.first_name ?: "Me",
            sender_avatar = userProfile?.avatar_url,
            reply_to_id = replyTo
        )
        
        messages = messages + newMessage
        
        SignalingManager.sendMessage(
            to = profile?.id ?: group?.id ?: "",
            content = content,
            senderName = userProfile?.first_name ?: "Neural User",
            senderAvatar = userProfile?.avatar_url,
            isGroup = group != null,
            tempId = tempId,
            fileUrl = fileUrl,
            fileType = fileType,
            replyTo = replyTo
        )
    }

    LaunchedEffect(currentUserId, profile?.id, group?.id) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        
        try {
            val typeStr = if (profile != null) "dm" else "group"
            val targetId = profile?.id ?: group?.id ?: ""
            val response = SupabaseManager.client.postgrest.rpc(
                "get_chat_messages",
                buildJsonObject {
                    put("p_type", typeStr)
                    put("p_id", targetId)
                }
            )
            messages = response.decodeList<ChatMessage>()
                .filter { msg -> msg.deleted_for_users?.contains(currentUserId) != true }
                .sortedBy { it.created_at }
        } catch (e: Exception) { e.printStackTrace() }

        // Membership Auto-Sync Listener
        val membershipChannel = SupabaseManager.client.channel("membership-auto-sync-$currentUserId")
        scope.launch { 
            membershipChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_group_members"
            }.collect { 
                val updatedGroups = SupabaseManager.client.from("chat_groups").select {
                    filter { eq("chat_group_members.user_id", currentUserId) }
                }.decodeList<ChatGroup>()
                onGroupsUpdate(updatedGroups)
            }
        }
        scope.launch { 
            membershipChannel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                table = "chat_group_members"
            }.collect { 
                val updatedGroups = SupabaseManager.client.from("chat_groups").select {
                    filter { eq("chat_group_members.user_id", currentUserId) }
                }.decodeList<ChatGroup>()
                onGroupsUpdate(updatedGroups)
            }
        }
        membershipChannel.subscribe()
    }

    // Signaling Socket Listener for New Messages
    DisposableEffect(currentUserId, profile?.id, group?.id) {
        SignalingManager.onNewMessageListener = { data ->
            scope.launch(Dispatchers.Main) {
                try {
                    val msg = neuralJson.decodeFromString<ChatMessage>(data.toString())
                    if (msg.deleted_for_users?.contains(currentUserId) == true) return@launch
                    
                    if (!messages.any { it.id == msg.id || (it.id.length < 30 && it.content == msg.content && it.sender_id == msg.sender_id) }) {
                        val isForThisChat = if (profile != null) {
                            (msg.sender_id == profile.id && msg.recipient_id == currentUserId) ||
                            (msg.sender_id == currentUserId && msg.recipient_id == profile.id)
                        } else if (group != null) {
                            msg.group_id == group.id
                        } else false
                        
                        if (isForThisChat) {
                            messages = messages + msg
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        onDispose {
            SignalingManager.onNewMessageListener = null
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        }
    }

    // Auto-scroll to bottom on load and new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Handle highlight duration
    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(2000)
            highlightedMessageId = null
        }
    }

    LaunchedEffect(messageText) {
        val urlRegex = Regex("(https?://\\S+)")
        val match = urlRegex.find(messageText)
        linkPreview = match?.value
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(pureBlack).statusBarsPadding().padding(16.dp)) {
                if (selectedMessages.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedMessages = emptySet() }) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.White) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${selectedMessages.size} selected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { 
                            val msg = messages.find { it.id in selectedMessages }
                            replyingToMessage = msg
                            selectedMessages = emptySet()
                        }) { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = Color.White) }
                        IconButton(onClick = {
                            val msgToShare = messages.filter { it.id in selectedMessages }.joinToString("\n") { it.content }
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, msgToShare)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                            selectedMessages = emptySet()
                        }) { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    selectedMessages.forEach { msgId ->
                                        val msg = messages.find { it.id == msgId } ?: return@forEach
                                        if (msg.sender_id == currentUserId) {
                                            // Delete for everyone
                                            SupabaseManager.client.from("chat_messages").delete { filter { eq("id", msgId) } }
                                        } else {
                                            // Delete for me (append current user to deleted_for_users)
                                            val newList = (msg.deleted_for_users ?: emptyList()) + currentUserId
                                            SupabaseManager.client.from("chat_messages").update(
                                                buildJsonObject { put("deleted_for_users", JsonArray(newList.map { JsonPrimitive(it) })) }
                                            ) { filter { eq("id", msgId) } }
                                        }
                                    }
                                    messages = messages.filter { it.id !in selectedMessages }
                                    selectedMessages = emptySet()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF111111)).clickable { 
                            (profile?.avatar_url ?: group?.icon_url)?.let { onViewImage(it) }
                        }, contentAlignment = Alignment.Center) {
                            if (profile?.avatar_url != null || group?.icon_url != null) {
                                AsyncImage(model = profile?.avatar_url ?: group?.icon_url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text((profile?.full_name ?: group?.name ?: "U").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(profile?.full_name ?: group?.name ?: "Unknown", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            val presenceName = presence.name ?: "System"
                            if (presence.isRecording) Text("$presenceName is recording audio...", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            else if (presence.isTyping) Text("$presenceName is typing...", color = accentYellow, fontSize = 10.sp)
                            else Text("Secure connection", color = Color.Gray, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onStartCall("voice") }) { Icon(Icons.Default.Phone, contentDescription = null, tint = accentYellow) }
                        IconButton(onClick = { onStartCall("video") }) { Icon(Icons.Default.Videocam, contentDescription = null, tint = accentYellow) }
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray) }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(cardBg)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat history", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            try {
                                                // Batch update for performance
                                                val msgIds = messages.map { it.id }
                                                if (msgIds.isNotEmpty()) {
                                                    // This is a simplified approach, in a real scenario you'd want a RPC or more complex update
                                                    messages.forEach { msg ->
                                                        val newList = (msg.deleted_for_users ?: emptyList()) + currentUserId
                                                        SupabaseManager.client.from("chat_messages").update(
                                                            buildJsonObject { put("deleted_for_users", JsonArray(newList.map { JsonPrimitive(it) })) }
                                                        ) { filter { eq("id", msg.id) } }
                                                    }
                                                }
                                                messages = emptyList()
                                                Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                replyingToMessage?.let { replyMsg ->
                    Box(modifier = Modifier.fillMaxWidth().background(cardBg.copy(alpha = 0.5f)).padding(16.dp).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(4.dp).height(40.dp).background(accentYellow, RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Replying to message", color = accentYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(replyMsg.content, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyingToMessage = null }) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray) }
                        }
                    }
                }
                linkPreview?.let { url ->
                    Box(modifier = Modifier.fillMaxWidth().background(cardBg.copy(alpha = 0.5f)).padding(16.dp).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = accentYellow, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Link preview", color = accentYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(url, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { linkPreview = null }) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray) }
                        }
                    }
                }
                if (attachment != null) {
                    Box(modifier = Modifier.fillMaxWidth().background(cardBg).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = accentYellow, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp)); Text("Image selected", color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    if (isRecording) {
                        Box(modifier = Modifier.weight(1f).background(accentYellow.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).border(1.dp, accentYellow.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = accentYellow, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp)); Text("REC ${recordingTime}s", color = accentYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f)); Text("CANCEL", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { 
                                    isRecording = false; 
                                    try { mediaRecorder.value?.stop() } catch (e: Exception) {}
                                    mediaRecorder.value?.release(); mediaRecorder.value = null
                                })
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).background(cardBg, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { filePicker.launch("image/*") }) { Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                                TextField(value = messageText, onValueChange = { 
                                    messageText = it
                                    onTypingUpdate(it.isNotBlank())
                                }, placeholder = { Text("Type a message...", color = Color.DarkGray, fontSize = 13.sp) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val canSend = (messageText.isNotBlank() || attachment != null) && !isSending
                    Box(modifier = Modifier.size(48.dp).background(if (isRecording) accentYellow else Color(0xFF111111), RoundedCornerShape(12.dp)).border(1.dp, if (isRecording) accentYellow else Color(0xFF1A1A1A), RoundedCornerShape(12.dp)).alpha(if (isSending) 0.5f else 1f).clickable(enabled = !isSending) {
                        if (canSend) {
                            isSending = true
                            scope.launch {
                                try {
                                    var url: String? = null
                                    if (attachment != null) {
                                        val b = context.contentResolver.openInputStream(attachment!!)?.readBytes()
                                        if (b != null) { 
                                            val n = "img_${System.currentTimeMillis()}.jpg"
                                            SupabaseManager.client.storage.from("chat_attachments").upload(n, b)
                                            url = SupabaseManager.client.storage.from("chat_attachments").publicUrl(n) 
                                        }
                                    }
                                    
                                    handleSendMessage(
                                        content = messageText,
                                        fileUrl = url,
                                        fileType = if (url != null) "image" else null,
                                        replyTo = replyingToMessage?.id
                                    )
                                    
                                    messageText = ""; attachment = null; replyingToMessage = null; linkPreview = null
                                } catch (e: Exception) { e.printStackTrace() }
                                finally { isSending = false }
                            }
                        } else if (isRecording) {
                            isRecording = false
                            onRecordingUpdate(false)
                            try { mediaRecorder.value?.stop() } catch (e: Exception) {}
                            mediaRecorder.value?.release(); mediaRecorder.value = null
                            scope.launch {
                                val b = audioFile.value?.readBytes()
                                if (b != null) {
                                    val n = "audio_${System.currentTimeMillis()}.m4a"
                                    SupabaseManager.client.storage.from("chat_attachments").upload(n, b)
                                    val url = SupabaseManager.client.storage.from("chat_attachments").publicUrl(n)
                                    
                                    handleSendMessage(
                                        content = "[Audio Note]",
                                        fileUrl = url,
                                        fileType = "audio"
                                    )
                                }
                            }
                        } else {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                            audioFile.value = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                            try {
                                mediaRecorder.value = (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) android.media.MediaRecorder(context) else @Suppress("DEPRECATION") android.media.MediaRecorder()).apply { 
                                    setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(audioFile.value!!.absolutePath)
                                    prepare()
                                    start() 
                                }
                                isRecording = true
                                onRecordingUpdate(true)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (canSend) Icons.AutoMirrored.Filled.Send else if (isRecording) Icons.Default.Check else Icons.Default.Mic, contentDescription = null, tint = if (isRecording) Color.Black else if (canSend) accentYellow else Color.Gray)
                    }
                }
            }
        },
        containerColor = pureBlack,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                NeuralChatBubble(
                    msg = msg,
                    isMe = msg.sender_id == currentUserId,
                    accentColor = accentYellow,
                    cardBg = cardBg,
                    isSelected = msg.id in selectedMessages,
                    isHighlighted = msg.id == highlightedMessageId,
                    replyMessage = messages.find { it.id == msg.reply_to_id },
                    onLongClick = { selectedMessages = selectedMessages + msg.id },
                    onClick = {
                        if (selectedMessages.isNotEmpty()) {
                            if (msg.id in selectedMessages) selectedMessages = selectedMessages - msg.id
                            else selectedMessages = selectedMessages + msg.id
                        }
                    },
                    onViewImage = onViewImage,
                    onReplyClick = { replyId: String ->
                        scope.launch {
                            val index = messages.indexOfFirst { it.id == replyId }
                            if (index != -1) {
                                listState.animateScrollToItem(index)
                                highlightedMessageId = replyId
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NeuralAudioPlayer(url: String, accentColor: Color, cardBg: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(url) {
        mediaPlayer.apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener { duration = it.duration }
            setOnCompletionListener { isPlaying = false; progress = 0f }
        }
        onDispose { mediaPlayer.release() }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer.isPlaying) {
                progress = if (duration > 0) mediaPlayer.currentPosition.toFloat() / duration else 0f
                delay(100)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                } else {
                    mediaPlayer.start()
                }
                isPlaying = !isPlaying
            },
            modifier = Modifier.size(32.dp).background(accentColor, CircleShape)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Visualizer-like wave (static for now)
            Row(modifier = Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(15) { i ->
                    val h = remember { (4..16).random().dp }
                    Box(modifier = Modifier.width(2.dp).height(h).background(if (progress > i/15f) accentColor else Color.DarkGray, RoundedCornerShape(1.dp)))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = accentColor,
                trackColor = Color.DarkGray
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            if (isPlaying) formatTime(mediaPlayer.currentPosition) else if (duration > 0) formatTime(duration) else "0:00",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NeuralChatBubble(
    msg: ChatMessage,
    isMe: Boolean,
    accentColor: Color,
    cardBg: Color,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    replyMessage: ChatMessage? = null,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onViewImage: (String) -> Unit = {},
    onReplyClick: (String) -> Unit = {}
) {
    val highlightColor by animateColorAsState(if (isHighlighted) accentColor.copy(alpha = 0.3f) else Color.Transparent)
    
    Column(modifier = Modifier.fillMaxWidth().background(highlightColor).padding(vertical = 2.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        val isSticker = msg.file_type == "sticker"
        Box(modifier = Modifier
            .widthIn(max = if (isSticker) 120.dp else 280.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMe) 16.dp else 4.dp, bottomEnd = if (isMe) 4.dp else 16.dp))
            .background(if (isSticker) Color.Transparent else (if (isSelected) accentColor.copy(alpha = 0.2f) else (if (isMe) accentColor.copy(alpha = 0.1f) else cardBg)))
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .border(1.dp, if (isSticker) Color.Transparent else (if (isSelected) accentColor else (if (isMe) accentColor.copy(alpha = 0.3f) else Color(0xFF1A1A1A))), RoundedCornerShape(16.dp))
            .padding(if (isSticker) 0.dp else 12.dp)
        ) {
            Column {
                if (replyMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .clickable { onReplyClick(replyMessage.id) }
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(3.dp).height(30.dp).background(accentColor, RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Replying to", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(replyMessage.content, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                
                if (msg.file_type == "image" && !msg.file_url.isNullOrBlank()) {
                    AsyncImage(
                        model = msg.file_url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onViewImage(msg.file_url) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (msg.file_type == "audio" && !msg.file_url.isNullOrBlank()) {
                    NeuralAudioPlayer(msg.file_url, accentColor, cardBg)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!msg.content.isNullOrBlank() && msg.content != "[Audio Note]" && msg.content != "[image]" && msg.content != "[video]") {
                    Text(msg.content, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    profiles: List<Profile>,
    onBack: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
    pureBlack: Color,
    cardBg: Color,
    accentYellow: Color
) {
    var groupName by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CREATE GROUP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) } },
                actions = {
                    TextButton(
                        onClick = { if (groupName.isNotBlank() && selectedIds.isNotEmpty()) onCreate(groupName, selectedIds.toList()) },
                        enabled = groupName.isNotBlank() && selectedIds.isNotEmpty()
                    ) {
                        Text("CREATE", color = if (groupName.isNotBlank() && selectedIds.isNotEmpty()) accentYellow else Color.Gray, fontWeight = FontWeight.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pureBlack)
            )
        },
        containerColor = pureBlack
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentYellow,
                    unfocusedBorderColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("SELECT MEMBERS", selectedIds.size.toString())
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(profiles) { profile ->
                    val isSelected = profile.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (isSelected) selectedIds - profile.id else selectedIds + profile.id
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) accentYellow else Color(0xFF111111)), contentAlignment = Alignment.Center) {
                            if (profile.avatar_url != null) {
                                AsyncImage(model = profile.avatar_url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(profile.full_name?.take(1) ?: "U", color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(profile.full_name ?: "Unknown", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { 
                                selectedIds = if (isSelected) selectedIds - profile.id else selectedIds + profile.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = accentYellow, uncheckedColor = Color.Gray)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullImageOverlay(url: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
fun SectionHeader(title: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.weight(1f)); Box(modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(count, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun GroupItem(group: ChatGroup, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { 
            if (!group.icon_url.isNullOrBlank()) {
                AsyncImage(model = group.icon_url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) 
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp); Text(group.last_message ?: "No messages yet", color = Color.Gray, fontSize = 12.sp) }
        Text(group.last_message_at?.takeLast(5) ?: "", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun UserItem(profile: Profile, isOnline: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { 
                if (!profile.avatar_url.isNullOrBlank()) {
                    AsyncImage(model = profile.avatar_url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    val displayName = profile.full_name ?: profile.first_name ?: "U"
                    Text(displayName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Box(modifier = Modifier.size(12.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Color.Black).padding(2.dp)) { Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(if (isOnline) Color(0xFF22C55E) else Color.Gray)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { 
            Text(profile.full_name ?: profile.first_name ?: "Unknown User", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(profile.last_message ?: "Secure tunnel ready", color = Color.Gray, fontSize = 12.sp) 
        }
        Text(profile.last_message_at?.takeLast(5) ?: "", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun CallOverlay(callData: ActiveCallData, rtcManager: WebRTCManager, onHangup: () -> Unit, onAccept: () -> Unit) {
    val pureBlack = Color(0xFF000000)
    val accentYellow = Color(0xFFEAB308)
    
    val infiniteTransition = rememberInfiniteTransition(label = "sonar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_alpha"
    )
    
    Box(modifier = Modifier.fillMaxSize().background(pureBlack.copy(alpha = 0.95f))) {
        
        if (callData.type == "video" && callData.status == "connected") {
            val participants = rtcManager.remoteViews.values.toList()
            val rowCount = if (participants.size > 2) 2 else 1
            val colCount = if (participants.size > 1) 2 else 1
            
            Column(modifier = Modifier.fillMaxSize()) {
                participants.chunked(colCount).forEach { rowParticipants ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        rowParticipants.forEach { view ->
                            AndroidView(
                                factory = { view },
                                modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, Color.DarkGray)
                            )
                        }
                        // Fill empty slots in row
                        if (rowParticipants.size < colCount) {
                            Spacer(modifier = Modifier.weight((colCount - rowParticipants.size).toFloat()))
                        }
                    }
                }
            }
            
            // Local View (PiP)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 24.dp)
                    .width(100.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, accentYellow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                AndroidView(factory = { rtcManager.localView }, modifier = Modifier.fillMaxSize())
            }
        }
        
        if (callData.type == "voice" || callData.status != "connected") {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                    if (callData.status == "calling" || callData.status == "incoming") {
                        Box(modifier = Modifier.size(120.dp * scale).clip(CircleShape).border(2.dp, accentYellow.copy(alpha = alpha), CircleShape))
                        Box(modifier = Modifier.size(120.dp * (scale * 0.7f)).clip(CircleShape).border(2.dp, accentYellow.copy(alpha = alpha * 0.5f), CircleShape))
                    }
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color(0xFF111111))
                            .border(2.dp, accentYellow.copy(alpha = 0.3f), RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (callData.partnerAvatar != null) {
                            AsyncImage(
                                model = callData.partnerAvatar,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                callData.partnerName.take(2).uppercase(),
                                color = accentYellow,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(callData.partnerName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when (callData.status) {
                        "calling" -> "CONNECTING..."
                        "incoming" -> "INCOMING CALL"
                        else -> "SECURE CHAT"
                    },
                    color = accentYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        var isMuted by remember { mutableStateOf(false) }
        var isVideoOff by remember { mutableStateOf(false) }
        var isSpeakerphoneOn by remember { mutableStateOf(callData.type == "video") }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de Mute
            FloatingActionButton(
                onClick = { 
                    isMuted = !isMuted
                    rtcManager.setAudioEnabled(!isMuted)
                },
                containerColor = if (isMuted) Color(0xFFEF4444) else Color(0xFF222222),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute")
            }

            // Botón de Altavoz (Privacidad)
            FloatingActionButton(
                onClick = { 
                    isSpeakerphoneOn = !isSpeakerphoneOn
                    rtcManager.toggleSpeakerphone(isSpeakerphoneOn)
                },
                containerColor = if (isSpeakerphoneOn) accentYellow else Color(0xFF222222),
                contentColor = if (isSpeakerphoneOn) Color.Black else Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Speaker")
            }

            // Botón de Cámara (Solo si es videollamada)
            if (callData.type == "video") {
                FloatingActionButton(
                    onClick = { 
                        isVideoOff = !isVideoOff
                        rtcManager.setVideoEnabled(!isVideoOff)
                    },
                    containerColor = if (isVideoOff) Color(0xFFEF4444) else Color(0xFF222222),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(if (isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "Camera")
                }
            }

            // Botón de Colgar
            FloatingActionButton(onClick = onHangup, containerColor = Color(0xFFEF4444), contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(36.dp))
            }

            // Botón de Aceptar (Solo si es entrante)
            if (callData.status == "incoming") {
                FloatingActionButton(onClick = onAccept, containerColor = Color(0xFF22C55E), contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.Call, contentDescription = "Accept Call", modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
