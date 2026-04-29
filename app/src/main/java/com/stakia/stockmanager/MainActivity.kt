package com.stakia.stockmanager

import com.stakia.stockmanager.BuildConfig
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import android.widget.Toast
import android.content.Intent
import android.app.PictureInPictureParams
import android.util.Rational
import android.os.Build
import android.app.ActivityManager
import android.content.Context

class MainActivity : ComponentActivity() {
    private var isInPipMode = mutableStateOf(false)
    private var currentActiveCall: ActiveCallData? = null
    
    // States for notification data
    private val initialChatIdState = mutableStateOf<String?>(null)
    private val initialCallTypeState = mutableStateOf<String?>(null)
    private val initialOfferState = mutableStateOf<String?>(null)
    private val initialSenderNameState = mutableStateOf<String?>(null)
    private val initialSenderAvatarState = mutableStateOf<String?>(null)
    private val autoAcceptState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseManager.init(this)
        
        handleIntent(intent)

        setContent {
            MaterialTheme {
                MainContent(
                    initialChatId = initialChatIdState.value,
                    initialCallType = initialCallTypeState.value,
                    initialOffer = initialOfferState.value,
                    initialSenderName = initialSenderNameState.value,
                    initialSenderAvatar = initialSenderAvatarState.value,
                    autoAccept = autoAcceptState.value,
                    onCallStateChanged = { call ->
                        currentActiveCall = call
                    }, 
                    onAutoAcceptProcessed = {
                        autoAcceptState.value = false
                        // Also clear intent data to prevent re-triggering
                        initialOfferState.value = null
                    },
                    isInPip = isInPipMode.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val type = intent.getStringExtra("notification_type")
        initialChatIdState.value = if (type == "chat") intent.getStringExtra("sender_id") else null
        
        if (type == "call") {
            initialCallTypeState.value = intent.getStringExtra("call_type")
            initialOfferState.value = intent.getStringExtra("offer")
            initialSenderNameState.value = intent.getStringExtra("sender_name")
            initialSenderAvatarState.value = intent.getStringExtra("sender_avatar")
            val action = intent.getStringExtra("action")
            autoAcceptState.value = action == "accept"
            
            // Handle explicit decline
            if (action == "decline") {
                val senderId = intent.getStringExtra("sender_id") ?: ""
                val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: ""
                SignalingManager.sendHangup(senderId, currentUserId)
            }
            
            // CRITICAL: Clear intent extras after consumption to prevent residual state
            intent.removeExtra("notification_type")
            intent.removeExtra("action")
            intent.removeExtra("offer")
        } else {
            // Reset auto-accept if the intent is not a call action
            autoAcceptState.value = false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (currentActiveCall?.status == "connected" && currentActiveCall?.type == "video") {
            enterPip()
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    initialScreen: String? = null,
    initialChatId: String? = null,
    initialCallType: String? = null,
    initialOffer: String? = null,
    initialSenderName: String? = null,
    initialSenderAvatar: String? = null,
    onCallStateChanged: (ActiveCallData?) -> Unit = {},
    onAutoAcceptProcessed: () -> Unit = {},
    isInPip: Boolean = false,
    autoAccept: Boolean = false
) {
    val context = LocalContext.current
    var themeSettings by remember { mutableStateOf(ThemeManager.loadSettings(context)) }
    
    CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
        MainContentBody(
            initialScreen = initialScreen,
            initialChatId = initialChatId,
            initialCallType = initialCallType,
            initialOffer = initialOffer,
            initialSenderName = initialSenderName,
            initialSenderAvatar = initialSenderAvatar,
            onCallStateChanged = onCallStateChanged,
            onAutoAcceptProcessed = onAutoAcceptProcessed,
            isInPip = isInPip,
            autoAccept = autoAccept,
            onThemeChange = { 
                themeSettings = it
                ThemeManager.saveSettings(context, it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentBody(
    initialScreen: String? = null,
    initialChatId: String? = null,
    initialCallType: String? = null,
    initialOffer: String? = null,
    initialSenderName: String? = null,
    initialSenderAvatar: String? = null,
    onCallStateChanged: (ActiveCallData?) -> Unit = {},
    onAutoAcceptProcessed: () -> Unit = {},
    isInPip: Boolean = false,
    autoAccept: Boolean = false,
    onThemeChange: (ThemeSettings) -> Unit
) {
    val theme = LocalThemeSettings.current
    val context = LocalContext.current
    var isInitialized by remember { mutableStateOf(false) }
    var currentScreen by remember(initialScreen) { 
        val startScreen = when {
            initialChatId != null -> "chat"
            SupabaseManager.client.auth.currentSessionOrNull() != null -> "dashboard"
            else -> "login"
        }
        mutableStateOf(initialScreen ?: startScreen) 
    }
    
    // Immediate navigation update
    LaunchedEffect(initialChatId, initialOffer) {
        if (initialChatId != null || initialOffer != null) {
            currentScreen = "chat"
        }
        isInitialized = true
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val rtcManager = remember { WebRTCManager(context) }
    
    val activeCallState = remember { mutableStateOf<ActiveCallData?>(null) }
    val onlineUsersState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val presencesState = remember { mutableStateMapOf<String, UserPresence>() }
    
    // Recovery Logic: Load last call if it was active
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
        val partnerId = prefs.getString("last_partner_id", null)
        if (partnerId != null) {
            val status = prefs.getString("last_status", "connected")
            val name = prefs.getString("last_partner_name", "Neural User")
            val type = prefs.getString("last_type", "audio")
            
            // Only restore if it was a connected call (don't restore incoming states to avoid loops)
            if (status == "connected") {
                activeCallState.value = ActiveCallData(
                    partnerId = partnerId,
                    partnerName = name ?: "User",
                    status = status,
                    type = type ?: "audio",
                    isMinimized = true // Restore as minimized bar
                )
            }
        }
    }

    // Persistence Logic: Save call state whenever it changes
    LaunchedEffect(activeCallState.value) {
        val call = activeCallState.value
        onCallStateChanged(call)

        val prefs = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
        if (call != null && call.status == "connected") {
            prefs.edit()
                .putString("last_partner_id", call.partnerId)
                .putString("last_partner_name", call.partnerName)
                .putString("last_status", call.status)
                .putString("last_type", call.type)
                .apply()
        } else if (call == null) {
            prefs.edit().clear().apply()
        }
    }

    val neuralJson = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true } }
    var autoAcceptTriggered by remember { mutableStateOf(false) }
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    
    var permissionRequestTrigger by remember { mutableStateOf(false) }

    fun acceptCall() {
        val call = activeCallState.value ?: return
        activeCallState.value = call.copy(status = "connecting", isMinimized = false)
        
        scope.launch {
            val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasCamera || !hasAudio) {
                permissionRequestTrigger = true
            } else {
                scope.launch {
                    try {
                        val logId = call.logId
                        if (logId != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    SupabaseManager.client.postgrest.from("call_logs").update({
                                        set("status", "connected")
                                    }) { filter { eq("id", logId) } }
                                } catch (_: Exception) {}
                            }
                        }
                        
                        val currentUserId = userProfile?.id ?: SupabaseManager.client.auth.currentUserOrNull()?.id ?: ""
                        val currentUserName = userProfile?.full_name ?: "Neural User"
                        
                        rtcManager.startLocalStreaming(call.type == "video")
                        
                        rtcManager.createPeerConnection(
                            remoteUserId = call.partnerId,
                            onIceCandidate = { candidate ->
                                val candObj = JSONObject().apply {
                                    put("sdpMid", candidate.sdpMid)
                                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                                    put("candidate", candidate.sdp)
                                }
                                SignalingManager.sendSignal(
                                    to = call.partnerId,
                                    fromId = currentUserId,
                                    fromName = currentUserName,
                                    type = "candidate",
                                    candidate = candObj
                                )
                            },
                            onTrackAdded = { }
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
                                
                                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                                audioManager.isMicrophoneMute = false
                                audioManager.isSpeakerphoneOn = call.type == "video"
                                
                                SignalingManager.sendSignal(
                                    to = call.partnerId,
                                    fromId = currentUserId,
                                    fromName = currentUserName,
                                    type = call.type,
                                    answer = answerObj,
                                    isGroup = call.isGroup,
                                    groupId = call.groupId
                                )
                                activeCallState.value = call.copy(status = "connected", connectedAt = System.currentTimeMillis())
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Now correctly inside MainContent composable
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            scope.launch {
                kotlinx.coroutines.delay(500)
                acceptCall()
            }
        } else {
            Toast.makeText(context, "Permisos necesarios para la llamada", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(permissionRequestTrigger) {
        if (permissionRequestTrigger) {
            callPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
            permissionRequestTrigger = false
        }
    }

    // Handle initial call from intent
    LaunchedEffect(initialOffer) {
        if (initialOffer != null && activeCallState.value == null) {
            activeCallState.value = ActiveCallData(
                type = initialCallType ?: "voice",
                status = "incoming",
                partnerName = initialSenderName ?: "Neural Node",
                offer = neuralJson.parseToJsonElement(initialOffer).jsonObject,
                partnerId = initialChatId ?: "",
                isGroup = false,
                logId = null
            )
        }
    }

    // Sync with activity
    LaunchedEffect(activeCallState.value) {
        onCallStateChanged(activeCallState.value)
        // Reset auto-accept trigger when a new incoming call is detected
        if (activeCallState.value?.status == "incoming") {
            autoAcceptTriggered = false
        }
    }

    // Call Service Lifecycle
    LaunchedEffect(activeCallState.value?.status) {
        val call = activeCallState.value
        if (call?.status == "connected") {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra("partnerName", call.partnerName)
                putExtra("isVideo", call.type == "video")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else if (call == null) {
            val intent = Intent(context, CallService::class.java).apply {
                action = "STOP_SERVICE"
            }
            context.startService(intent)
        }
    }

    LaunchedEffect(activeCallState.value, autoAccept) {
        if (autoAccept && activeCallState.value?.status == "incoming" && !autoAcceptTriggered) {
            autoAcceptTriggered = true
            acceptCall()
            onAutoAcceptProcessed()
        }
    }

    // Fetch user profile when currentScreen is not login
    LaunchedEffect(currentScreen) {
        if (currentScreen != "login" && currentScreen != "register") {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                user?.let {
                    val profile = SupabaseManager.client.postgrest.from("profiles").select {
                        filter { eq("id", it.id) }
                    }.decodeSingleOrNull<Profile>()
                    
                    if (profile != null) {
                        if (profile.role != "admin" && profile.role != "superadmin") {
                            SupabaseManager.client.auth.signOut()
                            currentScreen = "login"
                        } else {
                            userProfile = profile
                            // Register FCM Token for notifications
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val token = task.result
                                    scope.launch {
                                        try {
                                            SupabaseManager.client.postgrest.from("profiles").update({
                                                set("fcm_token", token)
                                            }) { filter { eq("id", profile.id) } }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // Global Signaling and Presence
    LaunchedEffect(userProfile) {
        val currentUserId = userProfile?.id ?: return@LaunchedEffect
        
        // Fetch groups for presence
        val groups = try {
            SupabaseManager.client.postgrest.from("chat_groups").select {
                filter { eq("chat_group_members.user_id", currentUserId) }
            }.decodeList<ChatGroup>()
        } catch (_: Exception) { emptyList<ChatGroup>() }

        val token = SupabaseManager.client.auth.currentAccessTokenOrNull() ?: ""
        
        SignalingManager.connect(
            token = token,
            userId = currentUserId,
            groupIds = groups.map { it.id },
            onSignal = { data ->
                val fromObj = data.optJSONObject("from")
                val fromId = fromObj?.optString("id") ?: data.optString("from", "")
                val fromName = fromObj?.optString("name") ?: "Neural Node"
                val payload = data.optJSONObject("payload")
                val type = payload?.optString("type") ?: data.optString("type")
                val offer = payload?.optJSONObject("offer") ?: data.optJSONObject("offer")
                val answer = payload?.optJSONObject("answer") ?: data.optJSONObject("answer")
                val iceCandidate = payload?.optJSONObject("candidate") ?: data.optJSONObject("candidate")
                val isGroup = data.optBoolean("isGroup", false)
                val groupId = data.optString("groupId", null)

                android.util.Log.d("Signaling", "Received signal: type=$type, from=$fromId, hasOffer=${offer!=null}, hasCandidate=${iceCandidate!=null}")
                
                if (offer != null) {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Llamada entrante de $fromName", Toast.LENGTH_SHORT).show()
                    }
                }

                when {
                    offer != null -> {
                        scope.launch {
                            val logId = java.util.UUID.randomUUID().toString()
                            activeCallState.value = ActiveCallData(
                                type = type ?: "voice",
                                status = "incoming",
                                partnerName = fromName,
                                offer = neuralJson.parseToJsonElement(offer.toString()).jsonObject,
                                partnerId = fromId,
                                isGroup = isGroup,
                                groupId = groupId,
                                logId = logId
                            )
                            try {
                                SupabaseManager.client.postgrest.from("call_logs").insert(
                                    CallLog(id = logId, caller_id = fromId, receiver_id = currentUserId, type = type ?: "voice", status = "incoming", caller_name = fromName, receiver_name = userProfile?.full_name ?: "Me", is_group = isGroup, group_id = groupId)
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("Signaling", "Failed to log incoming call", e)
                            }
                        }
                    }
                    answer != null -> {
                        activeCallState.value?.let { call ->
                            if (call.partnerId == fromId) {
                                scope.launch {
                                    val answerSdp = answer.optString("sdp")
                                    if (answerSdp.isNotEmpty()) {
                                        rtcManager.setRemoteDescription(fromId, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
                                        rtcManager.processQueuedIceCandidates(fromId)
                                        activeCallState.value = call.copy(status = "connected")
                                    }
                                }
                            }
                        }
                    }
                    iceCandidate != null -> {
                        val sdpMid = iceCandidate.optString("sdpMid")
                        val sdpMLineIndex = iceCandidate.optInt("sdpMLineIndex")
                        val sdp = iceCandidate.optString("candidate")
                        rtcManager.addIceCandidate(fromId, IceCandidate(sdpMid, sdpMLineIndex, sdp))
                    }
                }
            },
            onHangup = { data ->
                val fromObj = data.optJSONObject("from")
                val fromId = fromObj?.optString("id") ?: data.optString("from", "")
                
                if (activeCallState.value?.partnerId == fromId) {
                    rtcManager.stopAll()
                    activeCallState.value = null
                }
            },
            onPresenceChange = { users ->
                onlineUsersState.value = users
            },
            onTyping = { fromId, name, isTyping, to ->
                if (to == currentUserId || to.isEmpty()) {
                    presencesState[fromId] = presencesState.getOrDefault(fromId, UserPresence()).copy(isTyping = isTyping, name = name)
                }
            },
            onRecording = { fromId, name, isRecording, to ->
                if (to == currentUserId || to.isEmpty()) {
                    presencesState[fromId] = presencesState.getOrDefault(fromId, UserPresence()).copy(isRecording = isRecording, name = name)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isInPip && drawerState.isOpen,
        drawerContent = {
            if (!isInPip) {
                ModalDrawerSheet(
                    drawerContainerColor = if (theme.isDarkMode) Color(0xFF080808) else Color.White,
                    drawerTonalElevation = 0.dp,
                    drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                    modifier = Modifier.width(320.dp)
                ) {
                    DrawerContent(userProfile = userProfile, onNavigate = { screen: String ->
                        currentScreen = screen
                        scope.launch { drawerState.close() }
                    })
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { padding ->
            // Update UI handling
            if (UpdateManager.isDownloading) {
                UpdateScreen()
                // Launch download if just started
                LaunchedEffect(Unit) {
                    UpdateManager.downloadAndInstall(context)
                }
            } else {
                // Check for updates globally
                UpdateManager.CheckForUpdates(
                    currentVersion = BuildConfig.VERSION_NAME, 
                    context = context,
                    onUpdateFound = {
                        // This will trigger isDownloading = true in the next recomposition
                        UpdateManager.isDownloading = true
                    }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                if (!isInPip) {
                    AppBackground(theme.wallpaperType)
                }
                
                Box(modifier = Modifier.padding(if (isInPip) PaddingValues(0.dp) else padding)) {
                    if (!isInPip) {
                        when (currentScreen) {
                            "login" -> LoginScreen(
                                onLoginSuccess = { currentScreen = "dashboard" },
                                onRegisterRequest = { currentScreen = "register" }
                            )
                            "register" -> RegisterScreen(
                                onBack = { currentScreen = "login" },
                                onRegisterSuccess = { currentScreen = "login" }
                            )
                            "dashboard" -> DashboardScreen(onOpenDrawer = { scope.launch { drawerState.open() } }, userProfile = userProfile)
                            "inventory" -> InventoryScreen(onOpenDrawer = { scope.launch { drawerState.open() } }, userProfile = userProfile)
                            "chat" -> ChatScreen(
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                userProfile = userProfile,
                                initialChatId = initialChatId,
                                initialCallType = initialCallType,
                                initialOffer = initialOffer,
                                initialSenderName = initialSenderName,
                                initialSenderAvatar = initialSenderAvatar,
                                activeCall = activeCallState.value,
                                onActiveCallChange = { call -> activeCallState.value = call },
                                onlineUsers = onlineUsersState.value,
                                presences = presencesState,
                                rtcManager = rtcManager
                            )
                            "status" -> StatusScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                            "pricing" -> PricingScreen(onBack = { scope.launch { drawerState.open() } })
                            "profile" -> ProfileScreen(
                                onOpenDrawer = { scope.launch { drawerState.open() } }, 
                                onThemeChange = onThemeChange
                            )
                            "superadmin" -> SuperAdminScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                    }
                }

                // Global Active Call Bar - Only show when minimized or in other screens
                activeCallState.value?.let { call ->
                    val shouldShowBar = call.isMinimized || (currentScreen != "chat" && call.status != "incoming")
                    if (!isInPip && shouldShowBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (currentScreen == "dashboard") 12.dp else 56.dp) 
                                .zIndex(999f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ActiveCallBar(
                                callData = call,
                                onExpand = { activeCallState.value = call.copy(isMinimized = false) },
                                onHangup = {
                                    SignalingManager.sendHangup(call.partnerId, userProfile?.id ?: "")
                                    rtcManager.stopAll()
                                    activeCallState.value = null
                                },
                                onAccept = { acceptCall() }
                            )
                        }
                    }
                }

                // Global Call Overlay
                activeCallState.value?.let { call ->
                    if (!call.isMinimized) {
                        ChatScreenCallOverlay(
                            callData = call,
                        rtcManager = rtcManager,
                        currentUserId = userProfile?.id ?: "",
                        onHangup = {
                            val logId = call.logId
                            val duration = if (call.connectedAt != null) {
                                ((System.currentTimeMillis() - call.connectedAt!!) / 1000).toInt()
                            } else 0
                            
                            if (logId != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        SupabaseManager.client.postgrest.from("call_logs").update({
                                            set("status", if (duration > 0) "completed" else "cancelled")
                                            set("duration", duration)
                                        }) { filter { eq("id", logId) } }
                                    } catch (_: Exception) {}
                                }
                            }
                            SignalingManager.sendHangup(call.partnerId, userProfile?.id ?: "")
                            rtcManager.stopAll()
                            activeCallState.value = null
                        },
                        onAccept = { acceptCall() },
                        onMinimize = { minimized ->
                            activeCallState.value = activeCallState.value?.copy(isMinimized = minimized)
                            if (minimized) {
                                currentScreen = "chat"
                            }
                        }
                    )
                }
            }
        }
    }
}
}
}

@Composable
fun DrawerContent(userProfile: Profile?, onNavigate: (String) -> Unit) {
    val theme = LocalThemeSettings.current
    val isSuperAdmin = userProfile?.role == "superadmin"
    val accentColor = Color(theme.accentColor)
    val textColor = if (theme.isDarkMode) Color.White else Color.Black
    val roleLabel = if (isSuperAdmin) "Super Administrador" else "Administrador"
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxHeight().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
            Spacer(modifier = Modifier.width(12.dp))
            Text("STOCKMANAGER", color = textColor, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(roleLabel.uppercase(), color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text("MENÚ", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        DrawerItem("Inicio", Icons.Default.Dashboard, onClick = { onNavigate("dashboard") }, activeColor = accentColor)
        DrawerItem("Chat", Icons.Default.Chat, onClick = { onNavigate("chat") }, activeColor = accentColor)
        DrawerItem("Estado", Icons.Default.Analytics, onClick = { onNavigate("status") }, activeColor = accentColor)
        DrawerItem("Configuración", Icons.Default.Settings, onClick = { onNavigate("profile") }, activeColor = accentColor)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        DrawerItem("Cerrar Sesión", Icons.AutoMirrored.Filled.Logout, onClick = {
            scope.launch {
                SupabaseManager.client.auth.signOut()
                onNavigate("login")
            }
        }, activeColor = Color(0xFFEF4444))

        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF050505))
                .border(1.dp, Color(0xFF111111), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            userProfile?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111111))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (it.avatar_url != null) {
                            coil.compose.AsyncImage(
                                model = it.avatar_url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(it.full_name ?: "User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(it.role?.uppercase() ?: "ACTIVE", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "VERSION ${BuildConfig.VERSION_NAME}",
            color = Color.DarkGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun ActiveCallBar(
    callData: ActiveCallData, 
    onExpand: () -> Unit,
    onHangup: () -> Unit = {},
    onAccept: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "call_bar")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "alpha"
    )
    
    val barColor = if (callData.status == "incoming") Color(0xFFEAB308) else Color(0xFF22C55E)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .clickable { onExpand() }
            .graphicsLayer { alpha = if (callData.status == "incoming") pulseAlpha else 1f },
        color = barColor,
        tonalElevation = 12.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (callData.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    callData.partnerName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Text(
                    if (callData.status == "incoming") "LLAMADA ENTRANTE" else "EN CURSO",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Action Buttons (Compact)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (callData.status == "incoming") {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(32.dp).background(Color.White, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = onHangup,
                    modifier = Modifier.size(32.dp).background(Color.Red, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Hangup", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, activeColor: Color, onClick: () -> Unit) {
    val theme = LocalThemeSettings.current
    val textColor = if (theme.isDarkMode) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = textColor, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
@Composable
fun AppBackground(type: String) {
    val theme = LocalThemeSettings.current
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val saasYellow = Color(theme.accentColor)
    val bgColor = if (theme.isDarkMode) Color(0xFF000000) else Color(0xFFF5F5F5)
    
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        when (type) {
            "neural_pulse" -> {
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "scale"
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(saasYellow.copy(alpha = 0.15f), Color.Transparent),
                            center = center,
                            radius = 400.dp.toPx() * scale
                        )
                    )
                }
            }
            "digital_grid" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 40.dp.toPx()
                    val color = if (theme.isDarkMode) Color(0xFF111111) else Color(0xFFE0E0E0)
                    for (x in 0..size.width.toInt() step step.toInt()) {
                        drawLine(color, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                    }
                    for (y in 0..size.height.toInt() step step.toInt()) {
                        drawLine(color, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                    }
                }
            }
            "deep_nebula" -> {
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
                    label = "nebula"
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF000000)),
                            start = Offset(offset, offset),
                            end = Offset(offset + size.width, offset + size.height)
                        )
                    )
                }
            }
            "circuit" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, 100f)
                    path.lineTo(200f, 100f)
                    path.lineTo(250f, 150f)
                    path.lineTo(250f, 400f)
                    drawPath(path, Color(0xFF111111), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
            }
            "neural_waves" -> {
                val phase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * Math.PI.toFloat(),
                    animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
                    label = "phase"
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = androidx.compose.ui.graphics.Path()
                    val centerY = size.height / 2f
                    path.moveTo(0f, centerY)
                    for (x in 0..size.width.toInt() step 5) {
                        val y = centerY + Math.sin(x.toDouble() * 0.01 + phase).toFloat() * 100f
                        path.lineTo(x.toFloat(), y)
                    }
                    drawPath(
                        path = path,
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(saasYellow.copy(alpha = 0.2f), Color.Transparent)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
                    )
                }
            }
            "digital_eq" -> {
                val wave by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "eq"
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = 20.dp.toPx()
                    val gap = 10.dp.toPx()
                    val count = (size.width / (barWidth + gap)).toInt()
                    for (i in 0 until count) {
                        val heightFactor = (Math.sin(i.toDouble() * 0.5 + wave * 10).toFloat() + 1f) / 2f
                        val barHeight = size.height * 0.3f * heightFactor
                        drawRect(
                            color = saasYellow.copy(alpha = 0.1f),
                            topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomMenuIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = androidx.compose.ui.graphics.Color.White,
            start = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 7.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(20.dp.toPx(), 7.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = androidx.compose.ui.graphics.Color.White,
            start = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 12.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(16.dp.toPx(), 12.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = androidx.compose.ui.graphics.Color.White,
            start = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 17.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(12.dp.toPx(), 17.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

