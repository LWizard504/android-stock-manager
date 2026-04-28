package com.stakia.stockmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable

@Serializable
data class Tenant(
    val id: String? = null,
    val name: String? = null,
    val subscription_tier: String? = null,
    val created_at: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminScreen(onOpenDrawer: () -> Unit) {
    val saasYellow = Color(0xFFEAB308)
    val pureBlack = Color(0xFF000000)
    
    var totalTenants by remember { mutableLongStateOf(0) }
    var totalUsers by remember { mutableLongStateOf(0) }
    var recentTenants by remember { mutableStateOf(listOf<Tenant>()) }
    val revenueData = listOf(30f, 45f, 35f, 60f, 55f, 80f, 70f, 95f)

    LaunchedEffect(Unit) {
        try {
            val tenantsResponse = SupabaseManager.client.from("tenants").select()
            val tenantsList = tenantsResponse.decodeList<Tenant>()
            totalTenants = tenantsList.size.toLong()
            recentTenants = tenantsList.take(5)
            totalUsers = totalTenants * 12 
        } catch (e: Exception) { }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GLOBAL NETWORK VISION", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
                        Text("SUPERADMIN_TERMINAL", color = saasYellow, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                DashboardCard(title = "CORE INFRASTRUCTURE", accentColor = saasYellow) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KPICard(
                            modifier = Modifier.weight(1f),
                            label = "NODOS ACTIVOS",
                            value = totalTenants.toString(),
                            status = "+2.4%",
                            icon = Icons.Default.Public,
                            accentColor = saasYellow
                        )
                        KPICard(
                            modifier = Modifier.weight(1f),
                            label = "ENTIDADES",
                            value = totalUsers.toString(),
                            status = "STABLE",
                            icon = Icons.Default.People,
                            accentColor = Color.White
                        )
                    }
                }
            }

            item {
                DashboardCard(title = "NETWORK REVENUE (EST.)", accentColor = saasYellow, isVerticalBar = true) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp), contentAlignment = Alignment.BottomCenter) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            revenueData.forEach { heightFactor ->
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight(heightFactor / 100f).clip(RoundedCornerShape(4.dp)).background(
                                        Brush.verticalGradient(listOf(saasYellow.copy(alpha = 0.8f), saasYellow.copy(alpha = 0.1f)))
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                DashboardCard(
                    title = "ÚLTIMOS REGISTROS", 
                    accentColor = saasYellow,
                    rightContent = { Text("VIEW ALL", color = saasYellow, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                        recentTenants.forEach { tenant ->
                            TenantListItem(tenant, saasYellow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarContent(
    profile: Profile?,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    onLogout: () -> Unit,
    saasYellow: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { isOnline = true }
            override fun onLost(network: android.net.Network) { isOnline = false }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Stakia SaaS Core", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            
            // Connection Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF151515), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val color = if (isOnline) Color(0xFF10B981) else Color(0xFFF43F5E)
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isOnline) "LINK" else "VOID",
                    color = color.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Profile Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF151515), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (profile?.avatar_url != null) {
                    AsyncImage(
                        model = profile.avatar_url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initials = (profile?.first_name?.take(1) ?: "U").uppercase()
                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile?.let { "${it.first_name ?: ""} ${it.last_name ?: ""}".trim() } ?: "Cargando...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    profile?.role?.uppercase() ?: "USUARIO",
                    color = saasYellow,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFF111111), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Language
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Translate, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("IDIOMA", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Español", color = Color.White, fontSize = 12.sp)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = Color(0xFF151515), thickness = 1.dp)

        Text("PLATAFORMA CORE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))

        val menuItems = listOf(
            "Dashboard" to Icons.Default.Dashboard,
            "Chat" to Icons.Default.Chat,
            "Status" to Icons.Default.Dns,
            "Settings" to Icons.Default.Settings,
            "Logout" to Icons.Default.Logout
        )

        menuItems.forEach { (name, icon) ->
            SidebarItem(name, icon, name == selectedItem, saasYellow) { onItemSelected(name) }
        }
    }
}

@Composable
fun SidebarItem(name: String, icon: ImageVector, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) accentColor.copy(alpha = 0.05f) else Color.Transparent
    val borderColor = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent
    val contentColor = if (isSelected) accentColor else Color.Gray

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(backgroundColor).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, color = if (isSelected) Color.White else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            if (isSelected) {
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
            }
        }
    }
}


@Composable
fun DashboardCard(title: String, accentColor: Color, rightContent: @Composable (() -> Unit)? = null, isVerticalBar: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF0A0A0A)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(24.dp))) {
        if (isVerticalBar) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().align(Alignment.CenterEnd).background(Brush.verticalGradient(listOf(Color(0xFF8B5E00), Color(0xFFEAB308), Color(0xFF8B5E00)))))
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter).background(Brush.horizontalGradient(listOf(Color(0xFF8B5E00), Color(0xFFEAB308), Color(0xFF8B5E00)))))
        }
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                rightContent?.invoke()
            }
            content()
        }
    }
}

@Composable
fun KPICard(modifier: Modifier = Modifier, label: String, value: String, status: String, icon: ImageVector, accentColor: Color) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A0A0A)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(20.dp)).padding(20.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(status, color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun TenantListItem(tenant: Tenant, accentColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(tenant.name ?: "Unknown", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("id: ${tenant.id?.take(8) ?: "N/A"}...", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Light)
        }
        Box(modifier = Modifier.background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(tenant.subscription_tier ?: "ENTERPRISE", color = accentColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}
