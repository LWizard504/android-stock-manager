package com.stakia.stockmanager

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpdateScreen() {
    val saasYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack),
        contentAlignment = Alignment.Center
    ) {
        // Background Glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(saasYellow.copy(alpha = 0.1f * alpha), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon with Pulse
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
                    .border(2.dp, saasYellow.copy(alpha = alpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = saasYellow,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "ACTUALIZANDO SISTEMA",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            if (UpdateManager.latestVersion.isNotEmpty()) {
                Text(
                    "Versión detectada: v${UpdateManager.latestVersion}",
                    color = saasYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Sincronizando con el servidor central...",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            // Bouncing Animation for Download
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -20f,
                animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                label = "bounce"
            )

            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = saasYellow,
                modifier = Modifier.size(40.dp).offset(y = bounce.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar Container
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        UpdateManager.downloadInfo,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${(UpdateManager.downloadProgress * 100).toInt()}%",
                        color = saasYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Linear Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111111))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(UpdateManager.downloadProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF8B0000), saasYellow)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (UpdateManager.downloadProgress >= 1f) {
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        val file = java.io.File(context.cacheDir, "StockManagerUpdate.apk")
                        UpdateManager.installApk(context, file)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        "Si no se ha descargado/instalado haz clic aquí",
                        color = saasYellow,
                        fontSize = 12.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Por favor, no cierres la aplicación durante el proceso.",
                color = Color.DarkGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
