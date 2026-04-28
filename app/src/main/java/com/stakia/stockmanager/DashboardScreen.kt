package com.stakia.stockmanager

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenDrawer: () -> Unit, userProfile: Profile?) {
    val isSuperAdmin = userProfile?.role == "superadmin"
    val pureBlack = Color(0xFF000000)
    val accentColor = if (isSuperAdmin) Color(0xFFEAB308) else Color(0xFF3B82F6)
    
    var revenue by remember { mutableStateOf("$0.0") }
    var userCount by remember { mutableStateOf("0") }
    var stockCount by remember { mutableStateOf("0") }
    var alertCount by remember { mutableStateOf("0") }
    
    var tenantCount by remember { mutableStateOf("0") }
    var globalUserCount by remember { mutableStateOf("0") }
    
    var chartData by remember { mutableStateOf(listOf<Float>()) }
    var recentItems by remember { mutableStateOf(listOf<Map<String, String>>()) } // List of attribute maps
    var alertDetails by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) } // Pair(Message, IsImportant)
    var showAlertSheet by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<Map<String, String>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val alerts = mutableListOf<Pair<String, Boolean>>()
            if (isSuperAdmin) {
                val tenants = SupabaseManager.client.from("tenants").select().decodeList<JsonObject>()
                tenantCount = tenants.size.toString()
                
                val profiles = SupabaseManager.client.from("profiles").select().decodeList<JsonObject>()
                globalUserCount = profiles.size.toString()
                
                recentItems = tenants.take(5).map { 
                    mapOf(
                        "TITULO" to (it["name"]?.toString()?.replace("\"", "") ?: "Sin nombre"),
                        "ESTADO" to "Suscripción Activa",
                        "ID" to (it["id"]?.toString()?.replace("\"", "") ?: "N/A"),
                        "FECHA" to "Registro Reciente"
                    )
                }
                
                // Alerts for SuperAdmin
                alerts.add("Sistema operando con normalidad" to false)
                alerts.add("Total de nodos en la red: $tenantCount" to false)
                if (tenants.size < 5) alerts.add("Capacidad de red subutilizada" to true)
                
                chartData = listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.8f, 0.7f, 0.9f)
            } else {
                val profiles = SupabaseManager.client.from("profiles").select().decodeList<JsonObject>()
                userCount = profiles.size.toString()
                
                val inventory = SupabaseManager.client.from("inventory").select().decodeList<JsonObject>()
                stockCount = inventory.size.toString()
                
                val lowStockItems = inventory.filter { 
                    (it["stock"]?.toString()?.toIntOrNull() ?: 100) < 10 
                }
                
                // Important Alerts
                lowStockItems.forEach { 
                    alerts.add("STOCK BAJO: ${it["name"]?.toString()?.replace("\"", "")} (${it["stock"]})" to true)
                }
                
                // General Alerts
                alerts.add("Inventario actualizado: $stockCount productos" to false)
                alerts.add("Usuarios activos hoy: $userCount" to false)
                if (userCount.toInt() > 100) alerts.add("¡Gran actividad de usuarios hoy!" to false)
                
                alertCount = alerts.size.toString()
                
                recentItems = profiles.take(5).map { 
                    mapOf(
                        "TITULO" to ("${it["first_name"]?.toString()?.replace("\"", "") ?: ""} ${it["last_name"]?.toString()?.replace("\"", "") ?: ""}".trim()),
                        "ESTADO" to "Nuevo Registro",
                        "EMAIL" to (it["email"]?.toString()?.replace("\"", "") ?: "N/A"),
                        "ROL" to (it["role"]?.toString()?.replace("\"", "") ?: "Usuario")
                    )
                }
                
                revenue = "$${(userCount.toInt() * 45)}.5K"
                chartData = listOf(0.4f, 0.3f, 0.5f, 0.2f, 0.6f, 0.5f, 0.8f)
            }
            alertDetails = alerts
            alertCount = alerts.size.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            chartData = emptyList()
        }
    }
    
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) { 
                    CustomMenuIcon()
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("INICIO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111111))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfile?.avatar_url != null) {
                        AsyncImage(
                            model = userProfile.avatar_url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (userProfile?.first_name?.take(1) ?: "U").uppercase(),
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                val title = if (userProfile == null) "CARGANDO..." else if (isSuperAdmin) "RED GLOBAL" else "RESUMEN GENERAL"
                Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (userProfile == null) {
                        StatCard("Cargando...", "...", "", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("Cargando...", "...", "", Modifier.weight(1f), accentColor = accentColor)
                    } else if (isSuperAdmin) {
                        StatCard("NODOS", tenantCount, "+1", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("USUARIOS TOTALES", globalUserCount, "+3%", Modifier.weight(1f), accentColor = accentColor)
                    } else {
                        StatCard("INGRESOS", revenue, "+12%", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("USUARIOS", userCount, "+5%", Modifier.weight(1f), accentColor = accentColor)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (userProfile == null) {
                        StatCard("Cargando...", "...", "", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("Cargando...", "...", "", Modifier.weight(1f), accentColor = accentColor)
                    } else if (isSuperAdmin) {
                        StatCard("NODOS ACTIVOS", "128", "ESTABLE", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("AVISOS", "4", "BAJA", Modifier.weight(1f), isAlert = false, accentColor = accentColor, onClick = { showAlertSheet = true })
                    } else {
                        StatCard("PRODUCTOS", stockCount, "-2%", Modifier.weight(1f), accentColor = accentColor)
                        StatCard("AVISOS", alertCount, if (alertDetails.any { it.second }) "CRÍTICO" else "ESTABLE", Modifier.weight(1f), isAlert = alertDetails.any { it.second }, accentColor = accentColor, onClick = { showAlertSheet = true })
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            item {
                Text("ACTIVIDAD", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(modifier = Modifier.height(16.dp))
                ActivityChart(accentColor = accentColor, dataPoints = chartData)
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            item {
                val title = if (userProfile == null) "CARGANDO..." else "ÚLTIMOS REGISTROS"
                Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            items(recentItems) { item ->
                RecentNodeItem(item["TITULO"] ?: "", item["ESTADO"] ?: "") {
                    selectedRecord = item
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { /* Sync logic */ },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("SINCRONIZAR DATOS", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (selectedRecord != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedRecord = null },
                containerColor = Color(0xFF0A0A0A),
                scrimColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("DETALLES DEL REGISTRO", color = accentColor, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    selectedRecord?.forEach { (key, value) ->
                        if (key != "TITULO") {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(key, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(value, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showAlertSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAlertSheet = false },
                containerColor = Color(0xFF0A0A0A),
                scrimColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("AVISOS DEL SISTEMA", color = accentColor, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (alertDetails.isEmpty()) {
                        Text("No hay avisos en este momento.", color = Color.Gray, fontSize = 14.sp)
                    } else {
                        alertDetails.forEach { (alert, isImportant) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isImportant) Color.Red else Color.Gray))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(alert, color = if (isImportant) Color.White else Color.Gray, fontSize = 14.sp, fontWeight = if (isImportant) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, change: String, modifier: Modifier, isAlert: Boolean = false, accentColor: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(20.dp)
    ) {
        Column {
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(change, color = if (isAlert) Color(0xFFEF4444) else accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActivityChart(accentColor: Color, dataPoints: List<Float> = emptyList()) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    val animatedPoints = dataPoints.map { point ->
        animateFloatAsState(
            targetValue = point,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "point"
        ).value
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            if (dataPoints.isEmpty()) {
                // Scanning Animation for no data
                val scanX = size.width * scanOffset
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.3f), Color.Transparent),
                        startX = scanX - 100f,
                        endX = scanX + 100f
                    ),
                    size = size
                )
                
                // Static ghost lines
                val step = size.width / 6
                (0..6).forEach { i ->
                    val x = i * step
                    drawLine(Color(0xFF151515), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                }
            } else {
                val step = size.width / (animatedPoints.size - 1)
                val path = androidx.compose.ui.graphics.Path()
                animatedPoints.forEachIndexed { i, p ->
                    val x = i * step
                    val y = size.height * (1f - p)
                    if (i == 0) path.moveTo(x, y) else {
                        // Smooth cubic curve
                        val prevX = (i - 1) * step
                        val prevY = size.height * (1f - animatedPoints[i - 1])
                        path.cubicTo(
                            prevX + step / 2, prevY,
                            x - step / 2, y,
                            x, y
                        )
                    }
                }
                
                // Fill Area
                val fillPath = androidx.compose.ui.graphics.Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
                
                drawPath(
                    path = path,
                    color = accentColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun RecentNodeItem(name: String, status: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF050505))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(status, color = Color.Gray, fontSize = 10.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF1A1A1A))
    }
}
