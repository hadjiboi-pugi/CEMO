package com.example.cemo.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cemo.ui.components.AppLogo
import com.example.cemo.ui.screens.*
import com.example.cemo.viewmodel.WasteViewModel

// ── Sealed class for nav items ────────────────────────────────────────────────
sealed class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Dashboard : NavItem("Dashboard", "Home", Icons.Default.Dashboard)
    object Reports   : NavItem("Reports",   "Reports",   Icons.Default.BarChart)
    // Updated to the standard IoT/Sensors icon
    object Bluetooth : NavItem("Bluetooth", "Connect",   Icons.Default.Sensors)
    object Profile   : NavItem("Profile",   "Profile",   Icons.Default.Person)
    object Logout    : NavItem("Logout",    "Logout",    Icons.AutoMirrored.Filled.Logout)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login")    { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main")     { MainContainer(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(navController: NavController) {
    val sharedViewModel: WasteViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var isScrolled by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                when {
                    consumed.y < 0 -> isScrolled = true  // actually scrolled down
                    consumed.y > 0 -> isScrolled = false // scrolled back to top
                }
                return Offset.Zero
            }
        }
    }

    val navItems = listOf(
        NavItem.Dashboard,
        NavItem.Reports,
        NavItem.Bluetooth,
        NavItem.Profile,
        NavItem.Logout
    )

    Scaffold(
        topBar = {
            Surface(
                color           = MaterialTheme.colorScheme.background,
                tonalElevation  = 0.dp,
                shadowElevation = if (isScrolled) 8.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    AppLogo(size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text       = if (currentScreen == "Bluetooth") "Connect" else currentScreen,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 22.sp,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        bottomBar = {
            FloatingBottomNavBar(
                items        = navItems,
                currentRoute = currentScreen,
                onItemClick  = { item ->
                    if (item.route == "Logout") {
                        navController.navigate("login")
                    } else {
                        currentScreen = item.route
                        isScrolled = false // reset shadow on screen switch
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()) // top only, no bottom clipping
                .nestedScroll(nestedScrollConnection)
        ) {
            when (currentScreen) {
                "Dashboard" -> DashboardScreen(viewModel = sharedViewModel)
                "Bluetooth" -> BleDeviceScreen(viewModel = sharedViewModel)
                "Reports"   -> ReportsScreen()
                "Profile"   -> ProfileScreen()
            }
        }
    }
}

@Composable
fun FloatingBottomNavBar(
    items: List<NavItem>,
    currentRoute: String,
    onItemClick: (NavItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 16.dp,
                    shape        = RoundedCornerShape(32.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    spotColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
                .clip(RoundedCornerShape(32.dp)),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isBluetooth = item.route == "Bluetooth"
                    val isSelected  = currentRoute == item.route

                    if (isBluetooth) {
                        FloatingActionButton(
                            onClick        = { onItemClick(item) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                            elevation      = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp
                            ),
                            shape    = RoundedCornerShape(20.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector        = item.icon,
                                contentDescription = item.label,
                                modifier           = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        NavBarItem(
                            item       = item,
                            isSelected = isSelected,
                            onClick    = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor   = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(56.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = item.icon,
                contentDescription = item.label,
                tint               = if (isSelected) selectedColor else unselectedColor,
                modifier           = Modifier.size(if (isSelected) 26.dp else 24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text       = item.label,
                fontSize   = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color      = if (isSelected) selectedColor else unselectedColor,
                maxLines   = 1
            )
        }
    }
}