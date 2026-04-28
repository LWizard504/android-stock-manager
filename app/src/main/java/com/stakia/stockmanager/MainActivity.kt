package com.stakia.stockmanager

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
import androidx.compose.animation.core.*
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseManager.init(this)
        setContent {
            MaterialTheme {
                MainContent()
            }
        }
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
    initialSenderAvatar: String? = null
) {
    var currentScreen by remember(initialScreen) { 
        mutableStateOf(initialScreen ?: if (SupabaseManager.client.auth.currentSessionOrNull() != null) "dashboard" else "login") 
    }
    var selectedBackground by remember { mutableStateOf("pure_black") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    val activeCallState = remember { mutableStateOf<ActiveCallData?>(null) }
    val onlineUsersState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val rtcManager = remember { WebRTCManager(context) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    
    val neuralJson = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true } }

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
                val fromId = data.optString("from")
                if (activeCallState.value?.partnerId == fromId) {
                    rtcManager.stopAll()
                    activeCallState.value = null
                }
            },
            onPresenceChange = { users ->
                onlineUsersState.value = users
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF080808),
                drawerTonalElevation = 0.dp,
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.width(320.dp)
            ) {
                DrawerContent(userProfile = userProfile, onNavigate = { screen ->
                    currentScreen = screen
                    scope.launch { drawerState.close() }
                })
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground(selectedBackground)
                Box(modifier = Modifier.padding(padding)) {
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
                        rtcManager = rtcManager
                    )
                    "status" -> StatusScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                    "pricing" -> PricingScreen(onBack = { scope.launch { drawerState.open() } })
                    "profile" -> ProfileScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } }, 
                        selectedBg = selectedBackground,
                        onBgChange = { selectedBackground = it }
                    )
                    "superadmin" -> SuperAdminScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                }

                // Global Call Overlay
                activeCallState.value?.let { call ->
                    ChatScreenCallOverlay(
                        callData = call,
                        rtcManager = rtcManager,
                        currentUserId = userProfile?.id ?: "",
                        onHangup = {
                            val logId = call.logId
                            if (logId != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        SupabaseManager.client.postgrest.from("call_logs").update({
                                            set("status", "completed")
                                        }) { filter { eq("id", logId) } }
                                    } catch (_: Exception) {}
                                }
                            }
                            SignalingManager.sendHangup(call.partnerId, userProfile?.id ?: "")
                            rtcManager.stopAll()
                            activeCallState.value = null
                        },
                        onAccept = {
                            callPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
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
                                    
                                    rtcManager.startLocalStreaming(call.type == "video")
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
                                            SignalingManager.sendSignal(
                                                to = call.partnerId,
                                                fromId = userProfile?.id ?: "",
                                                fromName = userProfile?.full_name ?: "Me",
                                                type = call.type,
                                                answer = answerObj,
                                                isGroup = call.isGroup,
                                                groupId = call.groupId
                                            )
                                            activeCallState.value = call.copy(status = "connected")
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
fun DrawerContent(userProfile: Profile?, onNavigate: (String) -> Unit) {
    val isSuperAdmin = userProfile?.role == "superadmin"
    val accentColor = if (isSuperAdmin) Color(0xFFEAB308) else Color(0xFF3B82F6)
    val roleLabel = if (isSuperAdmin) "Super Administrador" else "Administrador"
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxHeight().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
            Spacer(modifier = Modifier.width(12.dp))
            Text("STOCKMANAGER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
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
    }
}

@Composable
fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, activeColor: Color, onClick: () -> Unit) {
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
        Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
@Composable
fun AppBackground(type: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val saasYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)
    
    Box(modifier = Modifier.fillMaxSize().background(pureBlack)) {
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
                    for (x in 0..size.width.toInt() step step.toInt()) {
                        drawLine(Color(0xFF111111), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                    }
                    for (y in 0..size.height.toInt() step step.toInt()) {
                        drawLine(Color(0xFF111111), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
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
