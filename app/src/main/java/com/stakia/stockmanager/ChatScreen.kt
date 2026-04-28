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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
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
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.ui.draw.alpha


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
    activeCall: ActiveCallData? = null,
    onActiveCallChange: (ActiveCallData?) -> Unit,
    onlineUsers: Set<String> = emptySet(),
    presences: Map<String, UserPresence> = emptyMap(),
    rtcManager: WebRTCManager
) {
    var currentView by remember { mutableStateOf(if (initialChatId != null) "conversation" else "list") }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    var selectedGroup by remember { mutableStateOf<ChatGroup?>(null) }
    var profilesForGroup by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var viewingImageUrl by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<ChatGroup>>(emptyList()) }
    
    val currentUserId = userProfile?.id ?: ""
    
    val scope = rememberCoroutineScope()
    val pureBlack = Color(0xFF000000)
    val accentYellow = if (userProfile?.role == "superadmin") Color(0xFFEAB308) else Color(0xFF3B82F6)
    val context = LocalContext.current
    
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(context, "Permisos necesarios para llamar", Toast.LENGTH_SHORT).show()
        }
    }

    fun initiateCall(partnerId: String, isVideo: Boolean, partnerName: String, partnerAvatar: String?) {
        scope.launch {
            try {
                // Request permissions first
                callPermissionLauncher.launch(
                    if (isVideo) arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
                    else arrayOf(android.Manifest.permission.RECORD_AUDIO)
                )
                
                rtcManager.startLocalStreaming(isVideo)
                rtcManager.createPeerConnection(
                    remoteUserId = partnerId,
                    onIceCandidate = { candidate: IceCandidate ->
                        val candObj = JSONObject().apply {
                            put("sdpMid", candidate.sdpMid)
                            put("sdpMLineIndex", candidate.sdpMLineIndex)
                            put("candidate", candidate.sdp)
                        }
                        SignalingManager.sendSignal(
                            to = partnerId,
                            fromId = currentUserId,
                            fromName = userProfile?.full_name ?: "Me",
                            fromAvatar = userProfile?.avatar_url,
                            type = "candidate", // Use candidate type for handshaking
                            candidate = candObj
                        )
                    },
                    onTrackAdded = { }
                )
                
                val offer = rtcManager.createOffer(partnerId)
                if (offer != null) {
                    rtcManager.setLocalDescription(partnerId, offer)
                    val offerObj = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", offer.description)
                    }
                    
                    val logId = UUID.randomUUID().toString()
                    try {
                        SupabaseManager.client.postgrest.from("call_logs").insert(
                            CallLog(id = logId, caller_id = currentUserId, receiver_id = partnerId, type = if (isVideo) "video" else "voice", status = "outgoing", caller_name = userProfile?.full_name ?: "Me", receiver_name = partnerName, caller_avatar = userProfile?.avatar_url, receiver_avatar = partnerAvatar)
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ChatScreen", "Error logging call: ${e.message}")
                    }
                    
                    SignalingManager.sendSignal(
                        to = partnerId,
                        fromId = currentUserId,
                        fromName = userProfile?.full_name ?: "Me",
                        fromAvatar = userProfile?.avatar_url,
                        type = if (isVideo) "video" else "voice", // Use voice/video only for offer
                        offer = offerObj
                    )
                    
                    onActiveCallChange(ActiveCallData(
                        type = if (isVideo) "video" else "voice",
                        status = "calling",
                        partnerName = partnerName,
                        partnerId = partnerId,
                        partnerAvatar = partnerAvatar,
                        logId = logId
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Call Error", e)
                Toast.makeText(context, "Error al iniciar llamada: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            // Improved group fetching: Get memberships first
            val memberships = SupabaseManager.client.postgrest.from("chat_group_members").select {
                filter { eq("user_id", currentUserId) }
            }.decodeList<JsonObject>()
            
            val groupIds = memberships.mapNotNull { it["group_id"]?.jsonPrimitive?.contentOrNull }
            
            if (groupIds.isNotEmpty()) {
                val fetchedGroups = SupabaseManager.client.postgrest.from("chat_groups").select {
                    filter {
                        isIn("id", groupIds)
                    }
                }.decodeList<ChatGroup>()
                
                groups = fetchedGroups.map { group ->
                    try {
                        val lastMsg = SupabaseManager.client.postgrest.from("chat_messages").select {
                            filter { eq("group_id", group.id) }
                            order("created_at", Order.DESCENDING)
                            limit(1)
                        }.decodeSingleOrNull<ChatMessage>()
                        group.copy(
                            last_message = lastMsg?.content ?: "No messages yet",
                            last_message_at = lastMsg?.created_at
                        )
                    } catch (e: Exception) { group }
                }
            }
            
            if (initialCallType != null && initialOffer != null && initialChatId != null) {
                val offerObj = neuralJson.parseToJsonElement(initialOffer).jsonObject
                onActiveCallChange(ActiveCallData(
                    type = initialCallType,
                    status = "incoming",
                    partnerName = initialSenderName ?: "Neural Node",
                    offer = offerObj,
                    partnerAvatar = initialSenderAvatar,
                    partnerId = initialChatId,
                    isGroup = initialChatId.startsWith("group:"),
                    groupId = if (initialChatId.startsWith("group:")) initialChatId.replace("group:", "") else null
                ))
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
                    presences = presences,
                    groups = groups,
                    initialChatId = initialChatId,
                    userProfile = userProfile,
                    onInitiateCall = { id, video, name, avatar -> initiateCall(id, video, name, avatar) },
                    onCreateGroup = {
                        scope.launch {
                            try {
                                profilesForGroup = SupabaseManager.client.postgrest.from("profiles").select {
                                    filter { Profile::id neq currentUserId }
                                }.decodeList<Profile>()
                                currentView = "create_group"
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                )
                "conversation" -> {
                    ConversationScreen(
                        profile = selectedProfile,
                        group = selectedGroup,
                        onBack = { selectedProfile = null; selectedGroup = null; currentView = "list" },
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        rtcManager = rtcManager,
                        activeCall = activeCall,
                        onActiveCallChange = onActiveCallChange,
                        onInitiateCall = { id, video, name, avatar -> initiateCall(id, video, name, avatar) },
                        presence = presences[selectedProfile?.id ?: ""]
                    )
                }
                "create_group" -> {
                    CreateGroupScreen(
                        profiles = profilesForGroup,
                        onBack = { currentView = "list" },
                        onGroupCreated = { currentView = "list" },
                        currentUserId = currentUserId
                    )
                }
            }
        }
        
        viewingImageUrl?.let { url ->
            ImagePreview(url = url, onDismiss = { viewingImageUrl = null })
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
    presences: Map<String, UserPresence> = emptyMap(),
    groups: List<ChatGroup>,
    initialChatId: String? = null,
    onCreateGroup: () -> Unit,
    onInitiateCall: (String, Boolean, String, String?) -> Unit,
    userProfile: Profile?
) {
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var callLogs by remember { mutableStateOf<List<CallLog>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("CHATS") }
    val currentUserId = userProfile?.id ?: ""
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        try {
            if (selectedTab == "CHATS") {
                val fetchedProfiles = SupabaseManager.client.postgrest.from("profiles").select {
                    filter { Profile::id neq currentUserId }
                }.decodeList<Profile>()
                
            if (fetchedProfiles.isNotEmpty()) {
                SignalingManager.fetchRecentChats(currentUserId) { lastMsgs ->
                    scope.launch(Dispatchers.Main) {
                        val profilesWithMessages = fetchedProfiles.map { profile ->
                            val lastMsg = lastMsgs[profile.id]
                            profile.copy(
                                last_message = lastMsg?.content ?: "Canal seguro listo",
                                last_message_at = lastMsg?.created_at
                            )
                        }
                        profiles = profilesWithMessages.sortedByDescending { it.last_message_at }
                    }
                }
                
                // Real-time updates for last message in the list
                val channel = SupabaseManager.client.realtime.channel("chat_list")
                val broadcastFlow = channel.broadcastFlow<ChatMessage>("new_message")
                scope.launch {
                    broadcastFlow.collect { msg ->
                        if (msg.recipient_id == currentUserId || msg.sender_id == currentUserId) {
                            val partnerId = if (msg.sender_id == currentUserId) msg.recipient_id else msg.sender_id
                            profiles = profiles.map { p ->
                                if (p.id == partnerId) p.copy(last_message = msg.content, last_message_at = msg.created_at)
                                else p
                            }.sortedByDescending { it.last_message_at }
                        }
                    }
                }
                channel.subscribe()
            }
            } else {
                callLogs = SupabaseManager.client.postgrest.from("call_logs").select {
                    filter {
                        or {
                            eq("caller_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<CallLog>()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(Color.Transparent).statusBarsPadding().padding(16.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrawer) { CustomMenuIcon() }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("MENSAJES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onCreateGroup) { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = accentYellow) }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TabItem("CHATS", selectedTab == "CHATS", accentYellow) { selectedTab = "CHATS" }
                        TabItem("LLAMADAS", selectedTab == "LLAMADAS", accentYellow) { selectedTab = "LLAMADAS" }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                
                if (selectedTab == "CHATS") {
                    if (groups.isNotEmpty()) {
                        item { SectionHeader("GRUPOS", groups.size.toString()) }
                        items(groups) { group -> GroupItem(group, onClick = { onGroupClick(group) }) }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                    
                    item { SectionHeader("CHATS DIRECTOS", profiles.size.toString()) }
                    items(profiles) { profile ->
                        UserItem(
                            profile = profile, 
                            isOnline = onlineUsers.contains(profile.id), 
                            presence = presences[profile.id],
                            onClick = { onProfileClick(profile) }
                        )
                    }
                } else {
                    items(callLogs) { log ->
                        CallLogItem(log, currentUserId, accentYellow, onClick = {
                            val partnerId = if (log.caller_id == currentUserId) log.receiver_id else log.caller_id
                            val partnerName = if (log.caller_id == currentUserId) (log.receiver_name ?: "User") else (log.caller_name ?: "User")
                            val partnerAvatar = if (log.caller_id == currentUserId) log.receiver_avatar else log.caller_avatar
                            onInitiateCall(partnerId, log.type == "video", partnerName, partnerAvatar)
                        })
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
            
            // Premium Action Button
            Button(
                onClick = {
                    if (selectedTab == "CHATS") onCreateGroup()
                    else {
                        selectedTab = "CHATS"
                        Toast.makeText(context, "Selecciona un usuario para llamar", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentYellow),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    if (selectedTab == "CHATS") "CREAR GRUPO NEURAL" else "INICIAR LLAMADA",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun RowScope.TabItem(title: String, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clickable { onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = if (isSelected) Color.White else Color.Gray, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.width(40.dp).height(2.dp).background(if (isSelected) accentColor else Color.Transparent))
    }
}

@Composable
fun SectionHeader(title: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { 
            Text(count, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun GroupItem(group: ChatGroup, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { 
            if (!group.icon_url.isNullOrBlank()) {
                AsyncImage(model = group.icon_url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) 
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { 
            Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(group.last_message ?: "No messages yet", color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
        }
        Text(group.last_message_at?.takeLast(5) ?: "", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun UserItem(profile: Profile, isOnline: Boolean, presence: UserPresence? = null, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { 
                if (!profile.avatar_url.isNullOrBlank()) {
                    AsyncImage(model = profile.avatar_url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    val displayName = profile.full_name ?: profile.first_name ?: "U"
                    Text(displayName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Box(modifier = Modifier.size(12.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Color.Black).padding(2.dp)) { 
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(if (isOnline) Color(0xFF22C55E) else Color.Gray)) 
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { 
            Text(profile.full_name ?: profile.first_name ?: "Unknown User", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            
            if (presence?.isTyping == true) {
                TypingAnimation(Color(0xFF22C55E))
            } else if (presence?.isRecording == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Grabando audio...", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(profile.last_message ?: "Canal seguro listo", color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
            }
        }
        Text(profile.last_message_at?.takeLast(5) ?: "", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun TypingAnimation(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (index == 0) dotAlpha else 1f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Escribiendo...", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CallLogItem(log: CallLog, currentUserId: String, accentColor: Color, onClick: () -> Unit) {
    val isIncoming = log.receiver_id == currentUserId
    val isMissed = log.status == "missed"
    val partnerName = if (isIncoming) (log.caller_name ?: "Unknown") else (log.receiver_name ?: "Unknown")
    val partnerAvatar = if (isIncoming) log.caller_avatar else log.receiver_avatar
    
    val durationText = if (log.duration > 0) {
        val mins = log.duration / 60
        val secs = log.duration % 60
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    } else ""

    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { 
            if (!partnerAvatar.isNullOrBlank()) {
                AsyncImage(model = partnerAvatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(partnerName.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(partnerName, color = if (isMissed && isIncoming) Color(0xFFEF4444) else Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIncoming) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = null,
                    tint = if (isMissed) Color(0xFFEF4444) else Color.Gray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        append(if (isMissed) "Perdida" else if (isIncoming) "Recibida" else "Realizada")
                        if (durationText.isNotEmpty()) append(" • $durationText")
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        Text(log.created_at.takeLast(5), color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun ImagePreview(url: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 24.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    profile: Profile?,
    group: ChatGroup?,
    onBack: () -> Unit,
    currentUserId: String,
    userProfile: Profile?,
    rtcManager: WebRTCManager,
    activeCall: ActiveCallData?,
    onActiveCallChange: (ActiveCallData?) -> Unit,
    onInitiateCall: (String, Boolean, String, String?) -> Unit,
    presence: UserPresence? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val neuralJson = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true } }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    val accentYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)
    
    val chatId = profile?.id ?: "group:${group?.id}"
    val title = profile?.full_name ?: group?.name ?: "Neural Node"
    val avatar = profile?.avatar_url ?: group?.icon_url
    
    // Typing Signal Logic
    LaunchedEffect(text) {
        if (profile != null) {
            SignalingManager.sendTyping(profile.id, currentUserId, userProfile?.full_name ?: "User", text.isNotBlank())
            if (text.isNotBlank()) {
                delay(3000)
                SignalingManager.sendTyping(profile.id, currentUserId, userProfile?.full_name ?: "User", false)
            }
        }
    }

    LaunchedEffect(chatId) {
        if (chatId.isEmpty()) return@LaunchedEffect
        
        try {
            android.util.Log.d("ChatScreen", "Starting bridge fetch for $chatId")
            
            val isGroupChat = group != null
            val targetId = if (isGroupChat) group!!.id else profile!!.id
            
            SignalingManager.fetchHistory(targetId, currentUserId, isGroupChat) { fetched ->
                scope.launch(Dispatchers.Main) {
                    messages = fetched.reversed()
                    Toast.makeText(context, "Neural Link: ${fetched.size} mensajes recuperados (vía Bridge)", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { 
            android.util.Log.e("ChatScreen", "Fatal Bridge Fetch Error", e)
        }

        // Listen to socket messages instead of Supabase Realtime for instant delivery
        SignalingManager.onNewMessageListener = { data ->
            try {
                val msg = neuralJson.decodeFromString<ChatMessage>(data.toString())
                // Only add if it belongs to this conversation and is not from me (already added locally)
                val isRelevant = if (group != null) {
                    msg.group_id == group.id
                } else {
                    (msg.sender_id == profile?.id && msg.recipient_id == currentUserId)
                }
                
                if (isRelevant && msg.sender_id != currentUserId) {
                    messages = (messages + msg).distinctBy { it.id }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Error decoding message", e)
            }
        }
    }

    DisposableEffect(chatId) {
        onDispose {
            SignalingManager.onNewMessageListener = null
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Transparent).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111))) {
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    if (presence?.isTyping == true) {
                        Text("Escribiendo...", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else if (presence?.isRecording == true) {
                        Text("Grabando audio...", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(if (profile != null) "Direct Link" else "Neural Cluster", color = Color.Gray, fontSize = 10.sp)
                    }
                }
                IconButton(
                    onClick = { 
                        if (profile != null) onInitiateCall(profile.id, false, profile.full_name ?: "User", profile.avatar_url)
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A))
                ) { Icon(Icons.Default.Call, contentDescription = null, tint = Color.White) }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { 
                        if (profile != null) onInitiateCall(profile.id, true, profile.full_name ?: "User", profile.avatar_url)
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A))
                ) { Icon(Icons.Default.Videocam, contentDescription = null, tint = accentYellow) }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(msg, currentUserId)
                }
            }

            // Input Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0A0A0A)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Attach */ }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray) }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accentYellow)
                )
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            val isGroup = group != null
                            val targetId = if (isGroup) group!!.id else (profile?.id ?: "")
                            
                            val newMsg = ChatMessage(
                                sender_id = currentUserId,
                                recipient_id = if (isGroup) null else targetId,
                                group_id = if (isGroup) targetId else null,
                                content = text,
                                created_at = java.time.Instant.now().toString(),
                                sender_name = userProfile?.full_name ?: "Neural User"
                            )
                            
                            // Update UI immediately
                            messages = messages + newMsg
                            val messageText = text
                            text = ""
                            
                            scope.launch {
                                try {
                                    // Let the API handle persistence to avoid RLS issues and double-writes
                                    SignalingManager.sendMessage(
                                        to = targetId,
                                        content = messageText,
                                        senderName = userProfile?.full_name ?: "User",
                                        senderAvatar = userProfile?.avatar_url,
                                        isGroup = isGroup
                                    )
                                } catch (e: Exception) { 
                                    android.util.Log.e("ChatScreen", "Send Error", e)
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accentYellow)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage, currentUserId: String) {
    val isMe = msg.sender_id == currentUserId
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bgColor = if (isMe) Color(0xFF1A1A1A) else Color(0xFF0F0F0F)
    val shape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, Color(0xFF222222), shape)
                .padding(12.dp)
        ) {
            Column {
                Text(msg.content ?: "", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(msg.created_at?.takeLast(5) ?: "", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    profiles: List<Profile>,
    onBack: () -> Unit,
    onGroupCreated: () -> Unit,
    currentUserId: String
) {
    var groupName by remember { mutableStateOf("") }
    val selectedProfiles = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accentYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NUEVO GRUPO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.White) } },
                actions = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Nombre del Grupo") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentYellow,
                    unfocusedBorderColor = Color(0xFF1A1A1A)
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("SELECCIONAR MIEMBROS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(profiles) { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            if (selectedProfiles.contains(profile.id)) selectedProfiles.remove(profile.id)
                            else selectedProfiles.add(profile.id)
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(profile.full_name ?: "Desconocido", color = Color.White, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = selectedProfiles.contains(profile.id),
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = accentYellow)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (groupName.isNotBlank() && selectedProfiles.isNotEmpty()) {
                        scope.launch {
                            try {
                                val groupId = UUID.randomUUID().toString()
                                val newGroup = ChatGroup(id = groupId, name = groupName)
                                SupabaseManager.client.postgrest.from("chat_groups").insert(newGroup)
                                
                                // Add all selected members + current user
                                val membersToAdd = (selectedProfiles + currentUserId).distinct()
                                val memberObjects = membersToAdd.map { uid ->
                                    buildJsonObject {
                                        put("group_id", groupId)
                                        put("user_id", uid)
                                    }
                                }
                                SupabaseManager.client.postgrest.from("chat_group_members").insert(memberObjects)
                                
                                onGroupCreated()
                            } catch (e: Exception) { 
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentYellow),
                shape = RoundedCornerShape(16.dp),
                enabled = groupName.isNotBlank() && selectedProfiles.isNotEmpty()
            ) {
                Text("CREAR GRUPO", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChatScreenCallOverlay(
    callData: ActiveCallData,
    rtcManager: WebRTCManager,
    currentUserId: String,
    onHangup: () -> Unit,
    onAccept: () -> Unit,
    onMinimize: (Boolean) -> Unit,
    isInPip: Boolean = false
) {
    var isMuted by remember { mutableStateOf(false) }
    var isVideoOff by remember { mutableStateOf(false) }
    var isSpeakerphoneOn by remember { mutableStateOf(callData.type == "video") }
    val accentYellow = Color(0xFFEAB308)

    if (callData.isMinimized || isInPip) {
        // Minimized PiP View or Android System PiP
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (callData.type == "video" && callData.status == "connected") {
                AndroidView(
                    factory = { 
                        val view = rtcManager.getOrCreateRemoteView(callData.partnerId)
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Audio Call PiP
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(if (isInPip) 60.dp else 80.dp).clip(CircleShape).background(Color(0xFF111111)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!callData.partnerAvatar.isNullOrBlank()) {
                            AsyncImage(model = callData.partnerAvatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }
            }
            
            if (!isInPip) {
                // Expand Button only for internal minimized view
                IconButton(
                    onClick = { onMinimize(false) },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(32.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video Layer
        if (callData.status == "connected" && callData.type == "video") {
            // Remote Video (Full Screen)
            AndroidView(
                factory = { 
                    val view = rtcManager.getOrCreateRemoteView(callData.partnerId)
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Video (PiP)
            if (!isVideoOff) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 100.dp, end = 20.dp)
                        .size(100.dp, 150.dp) // Slightly smaller
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { 
                            val view = rtcManager.localView
                            (view.parent as? android.view.ViewGroup)?.removeView(view)
                            // Important: Set ZOrderMediaOverlay so local video is above remote video
                            view.setZOrderMediaOverlay(true)
                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Audio Call or Calling State
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111111))
                        .border(2.dp, accentYellow.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!callData.partnerAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = callData.partnerAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            callData.partnerName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    callData.partnerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    when (callData.status) {
                        "connected" -> "CONECTADO"
                        "incoming" -> "LLAMADA ENTRANTE"
                        else -> "LLAMANDO..."
                    },
                    color = accentYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        // Top Controls (Minimize)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp)) {
            IconButton(
                onClick = { onMinimize(true) },
                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        // Control Layer (Only visible when NOT in PiP)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (callData.status == "incoming") {
                    Row(
                        modifier = Modifier.padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(40.dp)
                    ) {
                        CallActionButton(
                            onClick = onHangup,
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFEF4444),
                            size = 64.dp
                        )
                        CallActionButton(
                            onClick = onAccept,
                            icon = if (callData.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                            color = Color(0xFF22C55E),
                            size = 64.dp
                        )
                    }
                } else {
                    // Connected/Calling Controls
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallActionButton(
                            onClick = { 
                                isMuted = !isMuted
                                rtcManager.setAudioEnabled(!isMuted)
                            },
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            color = if (isMuted) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                            tint = if (isMuted) Color.Red else Color.White,
                            size = 48.dp
                        )

                        if (callData.type == "video") {
                            CallActionButton(
                                onClick = {
                                    isVideoOff = !isVideoOff
                                    rtcManager.setVideoEnabled(!isVideoOff)
                                },
                                icon = if (isVideoOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                color = if (isVideoOff) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                tint = if (isVideoOff) Color.Red else Color.White,
                                size = 48.dp
                            )
                            
                            CallActionButton(
                                onClick = { rtcManager.switchCamera() },
                                icon = Icons.Default.FlipCameraAndroid,
                                color = Color.Transparent,
                                size = 48.dp
                            )
                        }

                        CallActionButton(
                            onClick = {
                                isSpeakerphoneOn = !isSpeakerphoneOn
                                rtcManager.toggleSpeakerphone(isSpeakerphoneOn)
                            },
                            icon = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            color = if (isSpeakerphoneOn) accentYellow.copy(alpha = 0.2f) else Color.Transparent,
                            tint = if (isSpeakerphoneOn) accentYellow else Color.White,
                            size = 48.dp
                        )

                        CallActionButton(
                            onClick = onHangup,
                            icon = Icons.Default.CallEnd,
                            color = Color(0xFFEF4444),
                            size = 48.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    tint: Color = Color.White,
    size: androidx.compose.ui.unit.Dp = 56.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

