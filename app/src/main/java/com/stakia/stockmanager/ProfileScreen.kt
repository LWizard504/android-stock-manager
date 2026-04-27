package com.stakia.stockmanager

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
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenDrawer: () -> Unit) {
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = pureBlack)
            )
        },
        containerColor = pureBlack
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
                    Text("ACTUALIZAR NODO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
