package com.stakia.stockmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager
import android.app.NotificationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.google.firebase.messaging.FirebaseMessaging
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private var initialScreenState = mutableStateOf<String?>(null)
    private var initialChatIdState = mutableStateOf<String?>(null)
    private var initialCallTypeState = mutableStateOf<String?>(null)
    private var initialOfferState = mutableStateOf<String?>(null)
    private var initialSenderNameState = mutableStateOf<String?>(null)
    private var initialSenderAvatarState = mutableStateOf<String?>(null)
    private var initialActionState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseManager.init(this)
        
        configureLockScreenFlags()
        handleIntent(intent)
        
        // Request Required Permissions
        askRequiredPermissions()
        
        
        // Setup Firebase Messaging
        setupFirebase()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    AppNavigation(
                        initialScreen = initialScreenState.value,
                        initialChatId = initialChatIdState.value,
                        initialCallType = initialCallTypeState.value,
                        initialOffer = initialOfferState.value,
                        initialSenderName = initialSenderNameState.value,
                        initialSenderAvatar = initialSenderAvatarState.value,
                        initialAction = initialActionState.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val type = intent?.getStringExtra("notification_type")
        val senderId = intent?.getStringExtra("sender_id")
        val callType = intent?.getStringExtra("call_type")
        val offer = intent?.getStringExtra("offer")
        val senderName = intent?.getStringExtra("sender_name")
        val senderAvatar = intent?.getStringExtra("sender_avatar")
        val action = intent?.getStringExtra("action")
        
        if (type == "message" || type == "call") {
            initialScreenState.value = "chat"
            initialChatIdState.value = senderId
            initialCallTypeState.value = callType
            initialOfferState.value = offer
            initialSenderNameState.value = senderName
            initialSenderAvatarState.value = senderAvatar
            initialActionState.value = action

            if (type == "call") {
                // Cancel notification if we are opening the app
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001)
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    fun setupFirebase() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }
            val token = task.result
            saveFcmToken(token)
        }
    }

    fun saveFcmToken(token: String) {
        val user = SupabaseManager.client.auth.currentSessionOrNull()?.user
        if (user != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val profile = SupabaseManager.client.postgrest["profiles"].select {
                        filter { eq("id", user.id) }
                    }.decodeSingle<Profile>()
                    
                    SupabaseManager.client.postgrest["profiles"].update(
                        mapOf("fcm_token" to token, "tenant_id" to profile.tenant_id)
                    ) {
                        filter { eq("id", user.id) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun clearFcmToken() {
        val user = SupabaseManager.client.auth.currentSessionOrNull()?.user
        if (user != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    SupabaseManager.client.postgrest["profiles"].update(
                        mapOf("fcm_token" to null)
                    ) {
                        filter {
                            eq("id", user.id)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun askRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Notifications is already added
        }
        permissions.add(Manifest.permission.CAMERA)
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Handle results if needed
    }
}

@Composable
fun UpdateDownloadScreen(progress: Float, version: String) {
    val saasYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF050505)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Neural Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF111111), RoundedCornerShape(24.dp))
                    .border(1.dp, saasYellow.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = saasYellow,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "UPDATING PROTOCOLS",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 4.sp
            )
            
            Text(
                "VERSION $version",
                color = saasYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Custom Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF111111), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(saasYellow, RoundedCornerShape(2.dp))
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${(progress * 100).toInt()}% COMPLETE",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "ENCRYPTED CHANNEL",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            CircularProgressIndicator(
                color = saasYellow.copy(alpha = 0.5f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                "DO NOT CLOSE THE APPLICATION",
                color = Color.DarkGray,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}


@Composable
fun AppNavigation(
    initialScreen: String? = null, 
    initialChatId: String? = null,
    initialCallType: String? = null,
    initialOffer: String? = null,
    initialSenderName: String? = null,
    initialSenderAvatar: String? = null,
    initialAction: String? = null
) {
    var currentScreen by remember(initialScreen) { 
        mutableStateOf(initialScreen ?: if (SupabaseManager.client.auth.currentSessionOrNull() != null) "dashboard" else "login") 
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val saasYellow = Color(0xFFEAB308)
    
    var userProfile by remember { mutableStateOf<Profile?>(null) }

    // Fetch user profile when currentScreen is not login
    LaunchedEffect(currentScreen) {
        if (currentScreen != "login") {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                user?.let {
                    userProfile = SupabaseManager.client.postgrest.from("profiles").select {
                        filter {
                            eq("id", it.id)
                        }
                    }.decodeSingle<Profile>()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Helper to open drawer
    val onOpenDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    if (currentScreen == "login") {
        LoginScreen(
            onLoginSuccess = {
                currentScreen = "dashboard"
                // Actualizar token inmediatamente después de login
                (context as? MainActivity)?.setupFirebase()
            },
            onRegisterRequest = {
                currentScreen = "register"
            }
        )
    } else if (currentScreen == "register") {
        RegisterScreen(
            onBack = { currentScreen = "login" },
            onRegisterSuccess = {
                currentScreen = "pricing"
                (context as? MainActivity)?.setupFirebase()
            }
        )
    } else if (currentScreen == "pricing") {
        PricingScreen(
            onBack = { currentScreen = "login" }
        )
    } else {
        var updateAvailable by remember { mutableStateOf<AppDownload?>(null) }
    
    LaunchedEffect(Unit) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "1.0.0" }
        
        val update = UpdateManager.checkForUpdates(currentVersion)
        if (update != null) {
            updateAvailable = update
        }
    }

    if (updateAvailable != null) {
        AlertDialog(
            onDismissRequest = { updateAvailable = null },
            containerColor = Color(0xFF080808),
            title = { Text("NEURAL UPDATE AVAILABLE", color = Color.White, fontWeight = FontWeight.Black) },
            text = { 
                Text(
                    "A new binary version (${updateAvailable?.version}) has been detected in the distribution hub. Update now to maintain protocol integrity?",
                    color = Color.Gray
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        val update = updateAvailable
                        if (update != null) {
                            UpdateManager.downloadAndInstall(context, update.download_url, update.version, scope)
                        }
                        updateAvailable = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308), contentColor = Color.Black)
                ) {
                    Text("UPDATE NOW")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateAvailable = null }) {
                    Text("LATER", color = Color.Gray)
                }
            }
        )
    }

    if (UpdateManager.isDownloading.value) {
        UpdateDownloadScreen(
            progress = UpdateManager.downloadProgress.value,
            version = updateAvailable?.version ?: "Latest"
        )
    }

    ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFF050505),
                    drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxHeight().width(310.dp)
                ) {
                    SidebarContent(
                        selectedItem = when(currentScreen) {
                            "dashboard" -> "Dashboard"
                            "chat" -> "Chat"
                            "profile" -> "Settings"
                            else -> "Dashboard"
                        },
                        onItemSelected = { item ->
                            scope.launch { drawerState.close() }
                            currentScreen = when(item) {
                                "Dashboard" -> "dashboard"
                                "Chat" -> "chat"
                                "Status" -> "status"
                                "Settings" -> "profile"
                                else -> {
                                    android.widget.Toast.makeText(context, "Feature Coming Soon", android.widget.Toast.LENGTH_SHORT).show()
                                    currentScreen // Stay on current
                                }
                            }
                        },
                        onLogout = {
                            scope.launch { 
                                try {
                                    (context as? MainActivity)?.clearFcmToken()
                                    delay(500) // Give time for the update
                                    SignalingManager.disconnect()
                                    SupabaseManager.client.auth.signOut()
                                } catch (e: Exception) {}
                                drawerState.close()
                                currentScreen = "login"
                            }
                        },
                        saasYellow = saasYellow,
                        profile = userProfile
                    )
                }
            }
        ) {
            when (currentScreen) {
                "dashboard" -> {
                    when (userProfile?.role) {
                        "superadmin" -> SuperAdminScreen(onOpenDrawer = onOpenDrawer)
                        "manager" -> SuperAdminScreen(onOpenDrawer = onOpenDrawer) // Fallback for missing ManagerScreen
                        "employee" -> SuperAdminScreen(onOpenDrawer = onOpenDrawer) // Fallback for missing POSScreen
                        "it", "global_it" -> SuperAdminScreen(onOpenDrawer = onOpenDrawer) // Fallback for missing ITInventoryScreen
                        else -> SuperAdminScreen(onOpenDrawer = onOpenDrawer)
                    }
                }
                "chat" -> ChatScreen(
                    onOpenDrawer = onOpenDrawer, 
                    userProfile = userProfile,
                    initialChatId = initialChatId,
                    initialCallType = initialCallType,
                    initialOffer = initialOffer,
                    initialSenderName = initialSenderName,
                    initialSenderAvatar = initialSenderAvatar,
                    initialAction = initialAction
                )
                "status" -> StatusScreen(onOpenDrawer = onOpenDrawer)
                "profile" -> ProfileScreen(onOpenDrawer = onOpenDrawer)
                "pricing" -> PricingScreen(onBack = { currentScreen = "dashboard" })
            }
        }
    }
}
