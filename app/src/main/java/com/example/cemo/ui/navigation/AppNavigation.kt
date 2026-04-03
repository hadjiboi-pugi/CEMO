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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch

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
    // ── Single shared ViewModel for ALL screens ───────────────────────────────
    val sharedViewModel: WasteViewModel = viewModel()

    val drawerState   = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope         = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Dashboard") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier             = Modifier.fillMaxWidth(0.75f),
                drawerContainerColor = MaterialTheme.colorScheme.primary
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxHeight()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Assuming AppLogo also accepts sizing, keeping it at 32.dp or larger
                        AppLogo(40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "CEMO",
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp // Slightly larger for app title hierarchy
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    DrawerItem("Dashboard", Icons.Default.Dashboard, currentScreen == "Dashboard") {
                        currentScreen = "Dashboard"; scope.launch { drawerState.close() }
                    }
                    DrawerItem("Bluetooth", Icons.Default.Bluetooth, currentScreen == "Bluetooth") {
                        currentScreen = "Bluetooth"; scope.launch { drawerState.close() }
                    }
                    DrawerItem("Reports", Icons.Default.BarChart, currentScreen == "Reports") {
                        currentScreen = "Reports"; scope.launch { drawerState.close() }
                    }
                    DrawerItem("Profile", Icons.Default.PersonOutline, currentScreen == "Profile") {
                        currentScreen = "Profile"; scope.launch { drawerState.close() }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    DrawerItem("Logout", Icons.AutoMirrored.Filled.Logout, false) {
                        navController.navigate("login")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen, fontWeight = FontWeight.Bold) },
                    // UX FIX: Moved Hamburger menu to the leading edge (NavigationIcon)
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu", // UX FIX: Added Accessibility
                                modifier = Modifier.size(32.dp) // UX FIX: Increased icon size
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor         = MaterialTheme.colorScheme.surface,
                        titleContentColor      = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    "Dashboard" -> DashboardScreen(viewModel = sharedViewModel)
                    "Bluetooth" -> BleDeviceScreen(viewModel = sharedViewModel)
                    "Reports"   -> ReportsScreen()
                    "Profile"   -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick  = onClick,
        color    = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp) // Good implicit touch target size maintained
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label, // UX FIX: Added Accessibility
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp) // UX FIX: Increased icon size for better visibility
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp // Ensured text matches the visual weight of the new icon size
            )
        }
    }
}