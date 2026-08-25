package com.example.cemo.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cemo.AppState
import com.example.cemo.data.auth.GoogleAuthUiClient
import com.example.cemo.ui.findActivity
import com.example.cemo.ui.components.AppLogo
import com.example.cemo.ui.components.PremiumAnimatedBackground
import com.example.cemo.ui.components.TranslatedText
import com.example.cemo.ui.screens.*
import com.example.cemo.ui.theme.*
import com.example.cemo.viewmodel.AuthViewModel
import com.example.cemo.viewmodel.WasteViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack

// ── Sealed class for nav items ────────────────────────────────────────────────
sealed class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Dashboard : NavItem("Dashboard", "Dashboard", Icons.Default.Dashboard)
    object Reports   : NavItem("Reports",   "Reports",   Icons.Default.BarChart)
    // Updated to the standard IoT/Sensors icon
    object Bluetooth : NavItem("Bluetooth", "Connect",   Icons.Default.Sensors)
    object Profile   : NavItem("Profile",   "Profile",   Icons.Default.Person)
    object Settings  : NavItem("Settings",  "Settings",  Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val googleAuthUiClient by remember {
        // CredentialManager needs the real Activity, not the localized wrapper.
        mutableStateOf(GoogleAuthUiClient(context.findActivity() ?: context))
    }
    val authViewModel: AuthViewModel = viewModel()
    val navController = rememberNavController()
    
    val themeMode by AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        com.example.cemo.ThemeMode.LIGHT -> false
        com.example.cemo.ThemeMode.DARK -> true
        com.example.cemo.ThemeMode.SYSTEM -> isSystemDark
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Global Animated Background
        PremiumAnimatedBackground(isDark)
        
        NavHost(
            navController = navController, 
            startDestination = if (googleAuthUiClient.getSignedInUser() != null) "main" else "auth"
        ) {
            composable("auth") {
                AuthScreen(
                    navController = navController,
                    googleAuthUiClient = googleAuthUiClient,
                    viewModel = authViewModel
                )
            }
            composable("main") {
                MainContainer(
                    navController = navController,
                    googleAuthUiClient = googleAuthUiClient
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(
    navController: NavController,
    googleAuthUiClient: GoogleAuthUiClient
) {
    val sharedViewModel: WasteViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var isScrolled by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember(currentScreen) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y < -1f) {
                    isScrolled = true
                } else if (available.y > 1f && consumed.y == 0f) {
                    isScrolled = false
                }
                return Offset.Zero
            }
        }
    }

    // Reset isScrolled when screen changes
    LaunchedEffect(currentScreen) {
        isScrolled = false
    }

    val navItems = listOf(
        NavItem.Dashboard,
        NavItem.Reports,
        NavItem.Bluetooth,
        NavItem.Profile,
        NavItem.Settings
    )

    Scaffold(
        topBar = {
            val titleText = when (currentScreen) {
                "Bluetooth" -> "CONNECT"
                "WasteHistory" -> "WASTE HISTORY"
                else -> currentScreen.uppercase()
            }
            
            // On scroll the bar only takes a fill, no border. cemoSurface() would
            // outline all four sides and box the header in against the screen edge;
            // an app bar separates from content by plane, not by frame.
            val barColor by animateColorAsState(
                targetValue = if (isScrolled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                label = "topBarBackground"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barColor)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentScreen == "WasteHistory") {
                        IconButton(
                            onClick = { currentScreen = "Dashboard" },
                            modifier = Modifier
                                .size(36.dp)
                                .cemoSurface(shape = CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    } else {
                        AppLogo(size = 36.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    TranslatedText(
                        text = titleText,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        bottomBar = {
            if (currentScreen != "WasteHistory") {
                FloatingBottomNavBar(
                    items        = navItems,
                    currentRoute = currentScreen,
                    onItemClick  = { item ->
                        currentScreen = item.route
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            when (currentScreen) {
                "Dashboard" -> DashboardScreen(
                    viewModel = sharedViewModel, 
                    innerPadding = padding,
                    onSeeAllHistory = { currentScreen = "WasteHistory" }
                )
                "WasteHistory" -> WasteHistoryScreen(
                    viewModel = sharedViewModel,
                    innerPadding = padding,
                    onBack = { currentScreen = "Dashboard" }
                )
                "Bluetooth" -> BleDeviceScreen(viewModel = sharedViewModel, innerPadding = padding)
                "Reports"   -> ReportsScreen(viewModel = sharedViewModel, innerPadding = padding)
                "Profile"   -> ProfileScreen(
                    innerPadding = padding, 
                    googleAuthUiClient = googleAuthUiClient,
                    viewModel = sharedViewModel,
                    onLogout = { 
                        scope.launch {
                            googleAuthUiClient.signOut()
                            navController.navigate("auth") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    }
                )
                "Settings"  -> SettingsScreen(
                    innerPadding = padding,
                    googleAuthUiClient = googleAuthUiClient,
                    viewModel = sharedViewModel
                )
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
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cemoSurface(shape = MaterialTheme.shapes.extraLarge)
                .height(80.dp)
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isBluetooth = item.route == "Bluetooth"
                    val isSelected  = currentRoute == item.route

                    if (isBluetooth) {
                        Surface(
                            onClick = { onItemClick(item) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                            shadowElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
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
        modifier = Modifier.size(60.dp)
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
            Spacer(modifier = Modifier.height(4.dp))
            TranslatedText(
                text       = item.label,
                fontSize   = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Bold,
                color      = if (isSelected) selectedColor else unselectedColor,
                maxLines   = 1,
                softWrap   = false,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}
