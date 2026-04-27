package com.stakia.stockmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onRegisterRequest: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Colors from the provided image
    val pureBlack = Color(0xFF000000)
    val cardBg = Color(0xFF080808)
    val saasRed = Color(0xFFFF0000) // Pure vibrant red from image
    val darkGray = Color(0xFF1A1A1A)
    val textGray = Color(0xFF888888)
    val labelWhite = Color(0xFFE0E0E0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, darkGray, RoundedCornerShape(16.dp))
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Accent Bar (Gradient Red)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF8B0000), saasRed, Color(0xFF8B0000))
                        )
                    )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Logo
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                        append("STAKIA")
                    }
                    withStyle(style = SpanStyle(color = saasRed, fontWeight = FontWeight.Bold)) {
                        append("SOLUTIONS")
                    }
                },
                fontSize = 28.sp,
                letterSpacing = (-1).sp
            )
            
            Text(
                text = "SECURE ENTERPRISE PORTAL",
                color = textGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Form Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Email Field
                Text(
                    "Email Address",
                    color = labelWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("name@company.com", color = Color(0xFF444444), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = pureBlack,
                        unfocusedContainerColor = pureBlack,
                        focusedBorderColor = Color(0xFF333333),
                        unfocusedBorderColor = Color(0xFF222222),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = saasRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "Password",
                        color = labelWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        "Forgot password?",
                        color = saasRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("••••••••", color = Color(0xFF444444), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF444444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = pureBlack,
                        unfocusedContainerColor = pureBlack,
                        focusedBorderColor = Color(0xFF333333),
                        unfocusedBorderColor = Color(0xFF222222),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = saasRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Remember Me
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = saasRed,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "Remember me for 30 days",
                        color = textGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign In Button
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = ""
                                try {
                                    SupabaseManager.client.auth.signInWith(Email) {
                                        this.email = email.trim()
                                        this.password = password.trim()
                                    }
                                    onLoginSuccess()
                                } catch (e: Exception) {
                                    // Mostramos el mensaje real del error (ej. "Invalid login credentials")
                                    errorMessage = e.localizedMessage ?: e.message ?: "Unknown Auth Error"
                                    println("AUTH_DEBUG: $errorMessage")
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = saasRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(
                            "Sign In",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = errorMessage, color = saasRed, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer
                HorizontalDivider(color = darkGray, thickness = 0.5.dp)
                
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Don't have an account? ",
                        color = textGray,
                        fontSize = 13.sp
                    )
                    Text(
                        "Request Access",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onRegisterRequest() }
                    )
                }
            }
        }
    }
}
