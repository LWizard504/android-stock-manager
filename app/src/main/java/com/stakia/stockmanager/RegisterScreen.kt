package com.stakia.stockmanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, onRegisterSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var acceptTerms by remember { mutableStateOf(false) }
    var authorizeData by remember { mutableStateOf(false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val pureBlack = Color(0xFF050505)
    val cardBg = Color(0xFF080808)
    val saasRed = Color(0xFFFF0000)
    val darkGray = Color(0xFF1A1A1A)
    val textGray = Color(0xFF444444)
    val labelGray = Color(0xFF888888)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        avatarUri = uri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack)
    ) {
        // Red Top Border
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(saasRed).align(Alignment.TopCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "REGISTRATION",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = (-1).sp
            )
            Text(
                "CREATE YOUR ACCOUNT",
                color = labelGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Avatar Picker
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 32.dp)) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(1.dp, labelGray.copy(alpha = 0.3f), CircleShape)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = textGray, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Text(
                "UPLOAD PROFILE PICTURE",
                color = labelGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Fields
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CustomTextField(value = fullName, onValueChange = { fullName = it }, label = "FULL NAME", placeholder = "John Doe")
                CustomTextField(value = companyName, onValueChange = { companyName = it }, label = "ORGANIZATION / COMPANY", placeholder = "Stakia Corp")
                CustomTextField(value = email, onValueChange = { email = it }, label = "CORPORATE EMAIL", placeholder = "ceo@company.com")
                CustomTextField(value = phone, onValueChange = { phone = it }, label = "PHONE NUMBER", placeholder = "+1 (555) 000-0000")
                
                // Password
                Column {
                    Text("PASSWORD", color = labelGray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("••••••••", color = textGray) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = textGray)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedBorderColor = saasRed,
                            unfocusedBorderColor = darkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Confirm Identity
                Column {
                    Text("CONFIRM IDENTITY", color = labelGray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("••••••••", color = textGray) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = textGray)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedBorderColor = saasRed,
                            unfocusedBorderColor = darkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Checkboxes
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = acceptTerms,
                    onCheckedChange = { acceptTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = saasRed, uncheckedColor = darkGray)
                )
                Text(
                    "I accept the Terms and Conditions and the administrative protocols of the Stakia Solutions network.",
                    color = labelGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = authorizeData,
                    onCheckedChange = { authorizeData = it },
                    colors = CheckboxDefaults.colors(checkedColor = saasRed, uncheckedColor = darkGray)
                )
                Text(
                    "I authorize the processing of my corporate data according to the Privacy Policy.",
                    color = labelGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        scope.launch {
                            isLoading = true
                            errorMessage = ""
                            try {
                                // 1. Create Tenant
                                val tenantId = java.util.UUID.randomUUID().toString()
                                SupabaseManager.client.postgrest.from("tenants").insert(mapOf(
                                    "id" to tenantId,
                                    "name" to companyName,
                                    "subscription_tier" to "Starter"
                                ))

                                // 2. Sign Up
                                SupabaseManager.client.auth.signUpWith(Email) {
                                    this.email = email.trim()
                                    this.password = password.trim()
                                    data = buildJsonObject {
                                        put("full_name", fullName)
                                        put("company_name", companyName)
                                        put("phone", phone)
                                        put("role", "admin")
                                        put("tenant_id", tenantId)
                                    }
                                }
                                
                                val user = SupabaseManager.client.auth.currentUserOrNull()
                                if (user != null && avatarUri != null) {
                                    val bytes = context.contentResolver.openInputStream(avatarUri!!)?.readBytes()
                                    if (bytes != null) {
                                        val fileName = "${user.id}/profile.jpg"
                                        val bucket = SupabaseManager.client.storage.from("avatars")
                                        bucket.upload(fileName, bytes) { upsert = true }
                                        val publicUrl = bucket.publicUrl(fileName)
                                        
                                        SupabaseManager.client.postgrest.from("profiles").update({
                                            set("avatar_url", publicUrl)
                                        }) {
                                            filter { eq("id", user.id) }
                                        }
                                    }
                                }
                                onRegisterSuccess()
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Registration Failed"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saasRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading && acceptTerms && authorizeData
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("CREATE ACCOUNT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }

            if (errorMessage.isNotBlank()) {
                Text(errorMessage, color = saasRed, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Footer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUTHORIZED PERSONNEL ONLY ", color = Color(0xFF333333), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(
                    "LOGIN",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clickable { onBack() }
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val y = size.height - strokeWidth / 2
                            drawLine(
                                color = Color.White,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth
                            )
                        }
                        .padding(bottom = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    val saasRed = Color(0xFFFF0000)
    val darkGray = Color(0xFF1A1A1A)
    val labelGray = Color(0xFF888888)
    val cardBg = Color(0xFF080808)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = labelGray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF444444)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg,
                focusedBorderColor = saasRed,
                unfocusedBorderColor = darkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}
