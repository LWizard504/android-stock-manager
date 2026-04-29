package com.stakia.stockmanager

import com.stakia.stockmanager.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenDrawer: () -> Unit, onThemeChange: (ThemeSettings) -> Unit) {
    val theme = LocalThemeSettings.current
    val context = LocalContext.current
    val saasYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)
    val scope = rememberCoroutineScope()
    
    var profile by remember { mutableStateOf<Profile?>(null) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = SupabaseManager.client.auth.currentUserOrNull()
        user?.let {
            try {
                val p = SupabaseManager.client.postgrest.from("profiles").select {
                    filter {
                        eq("id", it.id)
                    }
                }.decodeSingle<Profile>()
                profile = p
                firstName = p.first_name ?: ""
                lastName = p.last_name ?: ""
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val onSave: () -> Unit = {
        scope.launch {
            isSaving = true
            try {
                profile?.id?.let { uid ->
                    SupabaseManager.client.postgrest.from("profiles").update({
                        set("first_name", firstName)
                        set("last_name", lastName)
                        set("full_name", "$firstName $lastName")
                    }) {
                        filter {
                            eq("id", uid)
                        }
                    }
                    // Refresh
                    profile = profile?.copy(
                        first_name = firstName, 
                        last_name = lastName,
                        full_name = "$firstName $lastName"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("GLOBAL PROFILE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        CustomMenuIcon()
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar Section
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0A))
                        .border(2.dp, Brush.linearGradient(listOf(saasYellow, Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile?.avatar_url != null) {
                        AsyncImage(
                            model = profile?.avatar_url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initialChar = (profile?.first_name?.take(1) ?: profile?.full_name?.take(1) ?: "U").uppercase()
                        Text(
                            text = initialChar,
                            color = saasYellow,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    uri?.let { 
                        scope.launch {
                            isSaving = true
                            try {
                                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                                if (bytes != null) {
                                    val fileName = "${profile?.id}_${System.currentTimeMillis()}.jpg"
                                    val bucket = SupabaseManager.client.storage.from("avatars")
                                    bucket.upload(fileName, bytes) {
                                        upsert = true
                                    }
                                    val publicUrl = bucket.publicUrl(fileName)
                                    
                                    // Update profile with new URL
                                    SupabaseManager.client.postgrest.from("profiles").update({
                                        set("avatar_url", publicUrl)
                                    }) {
                                        filter { eq("id", profile?.id ?: "") }
                                    }
                                    
                                    profile = profile?.copy(avatar_url = publicUrl)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { launcher.launch("image/*") },
                    color = saasYellow
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info Card
            DashboardCard(title = "IDENTIDAD DE NODO", accentColor = saasYellow) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = saasYellow,
                            unfocusedBorderColor = Color(0xFF1A1A1A)
                        )
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Apellidos") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = saasYellow,
                            unfocusedBorderColor = Color(0xFF1A1A1A)
                        )
                    )
                    
                    Text(
                        "Email: ${profile?.email ?: "Desconocido"}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Personalization Section
            DashboardCard(title = "PERSONALIZACIÓN AVANZADA", accentColor = saasYellow) {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Theme Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("MODO OSCURO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (theme.isDarkMode) "Activado" else "Desactivado", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = theme.isDarkMode,
                            onCheckedChange = { onThemeChange(theme.copy(isDarkMode = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = saasYellow)
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.05f))

                    // Bubble Colors
                    Text("COLOR DE MENSAJES ENVIADOS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFF005C4B, 0xFF1E3A5F, 0xFF3B82F6, 0xFFEF4444, 0xFFEAB308, 0xFF8B5CF6).forEach { color ->
                            ColorCircle(Color(color), theme.sentBubbleColor == color.toInt()) {
                                onThemeChange(theme.copy(sentBubbleColor = color.toInt()))
                            }
                        }
                    }

                    Text("COLOR DE MENSAJES RECIBIDOS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFF202C33, 0xFF242424, 0xFF1A1A1A, 0xFF333333, 0xFF444444, 0xFF555555).forEach { color ->
                            ColorCircle(Color(color), theme.receivedBubbleColor == color.toInt()) {
                                onThemeChange(theme.copy(receivedBubbleColor = color.toInt()))
                            }
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.05f))

                    Text("FONDO DE PANTALLA", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BackgroundOption("Puro", "pure_black", theme.wallpaperType == "pure_black", saasYellow) { onThemeChange(theme.copy(wallpaperType = "pure_black")) }
                        BackgroundOption("Pulso", "neural_pulse", theme.wallpaperType == "neural_pulse", saasYellow) { onThemeChange(theme.copy(wallpaperType = "neural_pulse")) }
                        BackgroundOption("Ondas", "neural_waves", theme.wallpaperType == "neural_waves", saasYellow) { onThemeChange(theme.copy(wallpaperType = "neural_waves")) }
                        BackgroundOption("Ecualizador", "digital_eq", theme.wallpaperType == "digital_eq", saasYellow) { onThemeChange(theme.copy(wallpaperType = "digital_eq")) }
                        BackgroundOption("Rejilla", "digital_grid", theme.wallpaperType == "digital_grid", saasYellow) { onThemeChange(theme.copy(wallpaperType = "digital_grid")) }
                        BackgroundOption("Nebulosa", "deep_nebula", theme.wallpaperType == "deep_nebula", saasYellow) { onThemeChange(theme.copy(wallpaperType = "deep_nebula")) }
                        BackgroundOption("Circuito", "circuit", theme.wallpaperType == "circuit", saasYellow) { onThemeChange(theme.copy(wallpaperType = "circuit")) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Settings Section
            Spacer(modifier = Modifier.height(24.dp))
            DashboardCard(title = "AJUSTES DE APLICACIÓN", accentColor = saasYellow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    SettingItem(
                        title = "Notificaciones Neurales",
                        subtitle = "Alertas de mensajes y llamadas",
                        icon = Icons.Default.Notifications,
                        accentColor = saasYellow,
                        trailing = {
                            var checked by remember { mutableStateOf(true) }
                            Switch(
                                checked = checked,
                                onCheckedChange = { checked = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = saasYellow)
                            )
                        }
                    )
                    
                    SettingItem(
                        title = "Buscar Actualizaciones",
                        subtitle = "Versión actual: ${BuildConfig.VERSION_NAME}",
                        icon = Icons.Default.SystemUpdate,
                        accentColor = saasYellow,
                        onClick = {
                            android.widget.Toast.makeText(context, "Buscando actualizaciones...", android.widget.Toast.LENGTH_SHORT).show()
                            UpdateManager.forceCheck()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onSave() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saasYellow),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text("GUARDAR CAMBIOS", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentColor: Color, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        trailing?.invoke()
    }
}
@Composable
fun BackgroundOption(name: String, id: String, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF0A0A0A))
                .border(1.dp, if (isSelected) accentColor else Color(0xFF1A1A1A), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.Palette,
                contentDescription = null,
                tint = if (isSelected) accentColor else Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
