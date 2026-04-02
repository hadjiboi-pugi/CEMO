package com.example.cemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- COLORS ---
val PrimaryGreen = Color(0xFF00B050)
val BackgroundGreen = Color(0xFFF1F8F5)
val DarkGreenText = Color(0xFF006400)
val MetricRed = Color(0xFFFF7043)
val MetricBlue = Color(0xFF42A5F5)
val ImpactGold = Color(0xFFFFD54F)
val AllocationRed = Color(0xFFE53935)

// --- DATA MODELS ---
data class WasteEntry(
    val id: String = UUID.randomUUID().toString(),
    val weightAdded: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val methanePotential: Double
)

data class DashboardState(
    val currentWeight: Double = 0.0,
    val temperature: Double = 25.0,
    val humidity: Double = 50.0,
    val totalMethane: Double = 0.0,
    val history: List<WasteEntry> = emptyList(),
    val maxCapacity: Double = 20.0 // kg
)

// --- VIEWMODEL ---
class WasteViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        // Initial Mock Data for Thesis Presentation
        val initialHistory = listOf(
            createEntry(7.20, 1711813440000L),
            createEntry(6.80, 1711467840000L),
            createEntry(5.40, 1711208640000L)
        )
        _uiState.update { it.copy(
            history = initialHistory,
            currentWeight = initialHistory.sumOf { entry -> entry.weightAdded } % 20.0,
            totalMethane = initialHistory.sumOf { entry -> entry.methanePotential }
        ) }

        // Simulate sensor fluctuations (Temperature and Humidity)
        viewModelScope.launch {
            while (true) {
                delay(5000)
                _uiState.update { state ->
                    state.copy(
                        temperature = 22.0 + (Math.random() * 8.0),
                        humidity = 40.0 + (Math.random() * 20.0)
                    )
                }
            }
        }
    }

    /**
     * THESIS LOGIC: Methane Emission Estimation Algorithm
     * Formula: CH4 = Weight * EmissionFactor * EnvironmentalModifiers
     */
    private fun calculateMethane(weight: Double, temp: Double, humidity: Double): Double {
        val baseFactor = 0.045 // Standard emission factor
        val tempModifier = (temp / 25.0).coerceIn(0.8, 1.2)
        val moistureModifier = (humidity / 50.0).coerceIn(0.9, 1.1)
        return weight * baseFactor * tempModifier * moistureModifier
    }

    private fun createEntry(weight: Double, timestamp: Long = System.currentTimeMillis()): WasteEntry {
        val methane = calculateMethane(weight, _uiState.value.temperature, _uiState.value.humidity)
        return WasteEntry(weightAdded = weight, timestamp = timestamp, methanePotential = methane)
    }

    fun addWaste(weight: Double) {
        val newEntry = createEntry(weight)
        _uiState.update { state ->
            val newHistory = (listOf(newEntry) + state.history).take(20)
            state.copy(
                history = newHistory,
                currentWeight = (state.currentWeight + weight).coerceAtMost(state.maxCapacity),
                totalMethane = state.totalMethane + newEntry.methanePotential
            )
        }
    }

    fun resetBin() {
        _uiState.update { it.copy(currentWeight = 0.0) }
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainContainer(navController) }
    }
}

// --- SHARED COMPONENTS ---
@Composable
fun AppLogo(size: Dp = 80.dp) {
    Surface(
        modifier = Modifier.size(size),
        color = PrimaryGreen,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
            Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.3f).align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = DarkGreenText,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun InputField(label: String, icon: ImageVector, isPassword: Boolean = false, isNumeric: Boolean = false, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(label, color = Color.LightGray) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            leadingIcon = { Icon(icon, null, tint = Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF5F5F5), focusedContainerColor = Color(0xFFF5F5F5), unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent)
        )
    }
}

// --- SCREENS ---
@Composable
fun LoginScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundGreen).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo()
        Spacer(modifier = Modifier.height(16.dp))
        Text("CEMO Monitor", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
        Text("Smart Waste Analytics", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                InputField("Username", Icons.Default.PersonOutline, value = username) { username = it }
                Spacer(modifier = Modifier.height(16.dp))
                InputField("Password", Icons.Default.Lock, isPassword = true, value = password) { password = it }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { navController.navigate("main") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(PrimaryGreen)
                ) { Text("LOGIN", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Register Account", modifier = Modifier.clickable { navController.navigate("register") }.align(Alignment.CenterHorizontally), color = PrimaryGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundGreen).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
        Spacer(modifier = Modifier.height(32.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                InputField("Full Name", Icons.Default.PersonOutline, value = "") {}
                Spacer(modifier = Modifier.height(16.dp))
                InputField("Email Address", Icons.Default.Email, value = "") {}
                Spacer(modifier = Modifier.height(16.dp))
                InputField("Password", Icons.Default.Lock, isPassword = true, value = "") {}
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.navigate("main") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(PrimaryGreen)) {
                    Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(navController: NavController) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Dashboard") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.75f), drawerContainerColor = PrimaryGreen) {
                Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(32.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("CEMO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    DrawerItem("Dashboard", Icons.Default.Dashboard, currentScreen == "Dashboard") { currentScreen = "Dashboard"; scope.launch { drawerState.close() } }
                    DrawerItem("Reports", Icons.Default.BarChart, currentScreen == "Reports") { currentScreen = "Reports"; scope.launch { drawerState.close() } }
                    DrawerItem("Profile", Icons.Default.PersonOutline, currentScreen == "Profile") { currentScreen = "Profile"; scope.launch { drawerState.close() } }
                    Spacer(modifier = Modifier.weight(1f))
                    DrawerItem("Logout", Icons.AutoMirrored.Filled.Logout, false) { navController.navigate("login") }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen, fontWeight = FontWeight.Bold, color = DarkGreenText) },
                    actions = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(Color.White)
                )
            },
            containerColor = BackgroundGreen
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    "Dashboard" -> DashboardScreen()
                    "Reports" -> ReportsScreen()
                    "Profile" -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = if (isSelected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSelected) PrimaryGreen else Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = if (isSelected) PrimaryGreen else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DashboardScreen(viewModel: WasteViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddWasteDialog(onDismiss = { showAddDialog = false }, onAdd = { weight -> 
            viewModel.addWaste(weight)
            showAddDialog = false
        })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            val progress = (state.currentWeight / state.maxCapacity).toFloat()
                            CircularProgressIndicator(progress = { progress }, color = if (progress > 0.8f) AllocationRed else PrimaryGreen, strokeWidth = 8.dp, trackColor = Color(0xFFEEEEEE))
                            Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = DarkGreenText)
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Text("CURRENT LOAD", fontSize = 10.sp, color = Color.Gray)
                            Text("${String.format(Locale.US, "%.2f", state.currentWeight)} kg", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
                            Text("Methane Yield: ${String.format(Locale.US, "%.3f", state.totalMethane)} kg", fontSize = 12.sp, color = PrimaryGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(PrimaryGreen)) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Waste")
                        }
                        OutlinedButton(onClick = { viewModel.resetBin() }, modifier = Modifier.weight(1f)) {
                            Text("Empty Bin")
                        }
                    }
                }
            }
        }
        item { SectionTitle("Environmental Metrics") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricSmallCard("Temp", "${String.format("%.1f", state.temperature)}°C", Icons.Default.Thermostat, MetricRed, Modifier.weight(1f))
                MetricSmallCard("Humidity", "${String.format("%.1f", state.humidity)}%", Icons.Default.WaterDrop, MetricBlue, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Recent Activity") }
        items(state.history) { entry ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = PrimaryGreen)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(sdf.format(Date(entry.timestamp)), fontSize = 12.sp, color = Color.Gray)
                        Text("Weight: ${entry.weightAdded}kg | CH₄: ${String.format("%.3f", entry.methanePotential)}kg", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddWasteDialog(onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var weightStr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record New Waste") },
        text = {
            Column {
                Text("Enter the weight of organic waste added (kg)")
                Spacer(Modifier.height(8.dp))
                InputField("Weight", Icons.Default.Scale, isNumeric = true, value = weightStr) { weightStr = it }
            }
        },
        confirmButton = {
            Button(onClick = { weightStr.toDoubleOrNull()?.let { onAdd(it) } }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MetricSmallCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ReportsScreen(viewModel: WasteViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Waste Generation Trend")
        Card(modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(Color.White)) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
                val values = if (state.history.isEmpty()) listOf(0.1f) else state.history.take(7).map { (it.weightAdded / 10.0).toFloat().coerceIn(0.1f, 1.0f) }.reversed()
                values.forEach { h ->
                    Box(modifier = Modifier.width(30.dp).fillMaxHeight(h).background(PrimaryGreen, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                }
            }
        }
        SectionTitle("Thesis Impact Analytics")
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ImpactMetricRow("Total CH₄ Avoided", "${String.format("%.3f", state.totalMethane)} kg", ImpactGold)
                ImpactMetricRow("CO₂ Eq. Reduced", "${String.format("%.2f", state.totalMethane * 25)} kg", MetricRed)
                ImpactMetricRow("Compost Potential", "${String.format("%.2f", state.history.sumOf { it.weightAdded } * 0.45)} kg", DarkGreenText)
            }
        }
    }
}

@Composable
fun ImpactMetricRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ProfileScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(modifier = Modifier.size(100.dp), color = Color(0xFFE8F5E9), shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) { Text("Y", fontSize = 40.sp, color = PrimaryGreen) }
            }
            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.background(PrimaryGreen, CircleShape).padding(8.dp).size(20.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        InputField("Full Name", Icons.Default.Person, value = "Yhaj Uranza") {}
        Spacer(modifier = Modifier.height(16.dp))
        InputField("Email Address", Icons.Default.Email, value = "yhaj.uranza@email.com") {}
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(PrimaryGreen)) { Text("Edit Profile") }
    }
}
