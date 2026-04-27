package com.stakia.stockmanager

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BillingCycle {
    MONTHLY, ANNUALLY, LIFETIME
}

data class PricingPlan(
    val name: String,
    val description: String,
    val priceMonthly: Int,
    val priceAnnually: Int,
    val priceLifetime: Int?,
    val features: List<String>,
    val popular: Boolean = false,
    val isContact: Boolean = false
)

@Composable
fun PricingScreen(onBack: () -> Unit) {
    var billingCycle by remember { mutableStateOf(BillingCycle.MONTHLY) }
    
    val plans = listOf(
        PricingPlan(
            name = "Starter",
            description = "Perfect for single location small businesses.",
            priceMonthly = 19,
            priceAnnually = 190,
            priceLifetime = 299,
            features = listOf("1 Admin User", "Up to 500 Products", "Basic Reports", "Standard Support")
        ),
        PricingPlan(
            name = "Pro",
            description = "For growing businesses managing multiple locations.",
            priceMonthly = 49,
            priceAnnually = 490,
            priceLifetime = 899,
            features = listOf("Up to 5 Employees", "Unlimited Products", "Multi-branch Management", "Priority Email Support"),
            popular = true
        ),
        PricingPlan(
            name = "Enterprise",
            description = "Advanced controls and unlimited scalability.",
            priceMonthly = 99,
            priceAnnually = 990,
            priceLifetime = null,
            features = listOf("Unlimited Employees", "Advanced Analytics", "Custom API Integrations", "24/7 Phone Support")
        ),
        PricingPlan(
            name = "Custom",
            description = "Tailored solutions for large-scale enterprise operations.",
            priceMonthly = 0,
            priceAnnually = 0,
            priceLifetime = 0,
            features = listOf("Everything in Enterprise", "Dedicated Account Manager", "Custom Feature Development", "On-premise Deployment"),
            isContact = true
        )
    )

    val pureBlack = Color(0xFF050505)
    val saasRed = Color(0xFFFF0000)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header
        Text(
            "Choose Your Plan",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        
        Text(
            "Select the best plan for your business needs.",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        )

        // Billing Toggle
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0F0F))
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                BillingToggleItem(
                    text = "Monthly",
                    isSelected = billingCycle == BillingCycle.MONTHLY,
                    onClick = { billingCycle = BillingCycle.MONTHLY },
                    modifier = Modifier.weight(1f)
                )
                BillingToggleItem(
                    text = "Annually",
                    subtext = "(Save 20%)",
                    isSelected = billingCycle == BillingCycle.ANNUALLY,
                    onClick = { billingCycle = BillingCycle.ANNUALLY },
                    modifier = Modifier.weight(1.3f)
                )
                BillingToggleItem(
                    text = "Lifetime",
                    isPremium = true,
                    isSelected = billingCycle == BillingCycle.LIFETIME,
                    onClick = { billingCycle = BillingCycle.LIFETIME },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Pricing Cards
        plans.forEach { plan ->
            val price = when (billingCycle) {
                BillingCycle.MONTHLY -> plan.priceMonthly
                BillingCycle.ANNUALLY -> plan.priceAnnually
                BillingCycle.LIFETIME -> plan.priceLifetime
            }

            if (price != null || plan.isContact) {
                PricingCard(
                    plan = plan,
                    billingCycle = billingCycle,
                    price = price ?: 0
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Not sure which plan is right for you? Contact our sales team",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun BillingToggleItem(
    text: String,
    subtext: String? = null,
    isPremium: Boolean = false,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(if (isSelected) (if (isPremium) Color.White else Color(0xFFFF0000)) else Color.Transparent)
    val textColor by animateColorAsState(if (isSelected) (if (isPremium) Color.Black else Color.White) else Color.Gray)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (isPremium && !isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFEAB308), Color(0xFFFDE047))))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("PREMIUM", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (subtext != null) {
                Text(
                    text = subtext,
                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFFFF4444),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isPremium && isSelected) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("PREMIUM", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun PricingCard(
    plan: PricingPlan,
    billingCycle: BillingCycle,
    price: Int
) {
    val saasRed = Color(0xFFFF0000)
    val cardBg = Color(0xFF0A0A0A)
    val borderColor = if (plan.popular) saasRed.copy(alpha = 0.5f) else Color(0xFF1A1A1A)

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(32.dp))
            .padding(24.dp)
    ) {
        if (plan.popular) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-36).dp)
                    .clip(CircleShape)
                    .background(saasRed)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "MOST POPULAR",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        Column {
            Text(
                plan.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                plan.description,
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 24.dp)) {
                if (plan.isContact) {
                    Text("Contact Us", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                } else {
                    Text("$", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Text("$price", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
                    val cycleText = when (billingCycle) {
                        BillingCycle.MONTHLY -> " / mo"
                        BillingCycle.ANNUALLY -> " / yr"
                        BillingCycle.LIFETIME -> " / one time"
                    }
                    Text(cycleText, color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
            }

            Button(
                onClick = { /* TODO: Checkout */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (plan.popular) saasRed else (if (plan.isContact) Color.White else Color(0xFF1A1A1A))
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (plan.isContact) "Contact Sales" else "Get Started",
                    color = if (plan.isContact) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "FEATURES INCLUDED",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            plan.features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = saasRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(feature, color = Color(0xFFCCCCCC), fontSize = 14.sp)
                }
            }
        }
    }
}
