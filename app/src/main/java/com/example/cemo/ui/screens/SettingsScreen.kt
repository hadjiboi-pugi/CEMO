package com.example.cemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cemo.AppState
import com.example.cemo.ThemeMode
import com.example.cemo.data.auth.GoogleAuthUiClient
import com.example.cemo.ui.components.*
import com.example.cemo.ui.theme.*
import com.example.cemo.viewmodel.WasteViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cemo.R

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.clip
import java.util.Locale
import com.example.cemo.data.notification.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    googleAuthUiClient: GoogleAuthUiClient,
    viewModel: WasteViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val themeMode by AppState.themeMode.collectAsState()
    val currentLanguage by AppState.selectedLanguage.collectAsState()
    val isNotifEnabled by AppState.foregroundNotifEnabled.collectAsState()
    val maxWeightThreshold by AppState.maxWeightThreshold.collectAsState()
    val isWeightAlertEnabled by AppState.weightAlertEnabled.collectAsState()
    val isDemoMode by AppState.isDemoMode.collectAsState()
    
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }
    
    val context = LocalContext.current
    var showTareDialog by remember { mutableStateOf(false) }
    var showWeightThresholdDialog by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    val packageInfo = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 1L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 1L
    }

    if (showWeightThresholdDialog) {
        WeightThresholdDialog(
            currentThreshold = maxWeightThreshold,
            onDismiss = { showWeightThresholdDialog = false },
            onConfirm = { newThreshold ->
                showWeightThresholdDialog = false
                AppState.setMaxWeightThreshold(context, newThreshold)
                NotificationHelper.resetAlertState()
                NotificationHelper.checkWeightThreshold(context, state.currentWeight)
            }
        )
    }

    if (showTareDialog) {
        AlertDialog(
            onDismissRequest = { showTareDialog = false },
            title = { TranslatedText("Confirm Tare", style = MaterialTheme.typography.titleLarge) },
            text = { TranslatedText("Recalibrate the empty baseline? Make sure there is no weight on the scale.") },
            confirmButton = {
                TextButton(onClick = {
                    showTareDialog = false
                    viewModel.tareHardware()
                }) {
                    TranslatedText("Yes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTareDialog = false }) {
                    TranslatedText("No", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (state.isTaring) {
        AlertDialog(
            onDismissRequest = {},
            title = { TranslatedText("Taring...", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    TranslatedText("Please wait while the scale zeros.")
                }
            },
            confirmButton = {},
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 32.dp))
                
                // ── Display Section ───────────────────────────────────────────
                SettingsSection(title = stringResource(R.string.display), icon = Icons.Default.Palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.appearance_mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ThemeSelectionItem(
                            currentTheme = themeMode,
                            onThemeSelected = { newMode ->
                                AppState.setThemeMode(context, newMode)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // ── Language Section ──────────────────────────────────────────
                SettingsSection(title = stringResource(R.string.language), icon = Icons.Default.Language) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.preferred_language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LanguageSelectionItem(
                            currentLanguage = currentLanguage,
                            onLanguageSelected = { lang ->
                                AppState.setSelectedLanguage(context, lang)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Notifications Section ─────────────────────────────────────
                SettingsSection(title = stringResource(R.string.notifications), icon = Icons.Default.Notifications) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsToggleItem(
                            label = stringResource(R.string.foreground_notification),
                            subLabel = stringResource(R.string.foreground_service_desc),
                            initialValue = isNotifEnabled,
                            onToggle = { enabled ->
                                AppState.setForegroundNotifEnabled(context, enabled)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        SettingsToggleItem(
                            label = stringResource(R.string.weight_threshold_alert),
                            subLabel = stringResource(R.string.weight_threshold_alert_desc),
                            initialValue = isWeightAlertEnabled,
                            onToggle = { enabled ->
                                AppState.setWeightAlertEnabled(context, enabled)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Weight Settings Section ───────────────────────────────────
                SettingsSection(title = stringResource(R.string.weight_settings), icon = Icons.Default.Scale) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showWeightThresholdDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                TranslatedText(
                                    stringResource(R.string.max_weight_threshold),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                TranslatedText(
                                    stringResource(R.string.max_weight_threshold_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${String.format(Locale.US, "%.1f", maxWeightThreshold)} kg",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Device Controls Section ───────────────────────────────────
                val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
                val isConnected = bleStatus is com.example.cemo.data.model.BleStatus.Connected

                SettingsSection(title = "Device Controls", icon = Icons.Default.Devices) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Hardware Actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showTareDialog = true },
                                enabled = isConnected,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                            ) {
                                Icon(Icons.Default.Scale, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                TranslatedText("Tare", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (!isConnected) {
                            Text(
                                "Connect to a device to enable hardware controls.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Version & Demo Mode Trigger (Tap 5 times) ─────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime > 2000L) {
                                tapCount = 1
                            } else {
                                tapCount++
                            }
                            lastTapTime = now

                            if (tapCount >= 5) {
                                tapCount = 0
                                val active = AppState.toggleDemoMode()
                                if (active) {
                                    Toast.makeText(context, "Demo Mode Activated: Simulating IoT Connection", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Demo Mode Deactivated", Toast.LENGTH_SHORT).show()
                                }
                            } else if (tapCount in 2..4) {
                                val remaining = 5 - tapCount
                                Toast.makeText(
                                    context,
                                    if (remaining == 1) "Tap 1 more time to enter Demo Mode" else "Tap $remaining more times to enter Demo Mode",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CEMO Monitor",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Version $versionName ($versionCode)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (isDemoMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "DEMO MODE ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 40.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cemoSurface(shape = MaterialTheme.shapes.extraLarge)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.shapes.extraSmall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                TranslatedText(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun ThemeSelectionItem(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                MaterialTheme.shapes.small
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = currentTheme == mode
            Surface(
                onClick = { onThemeSelected(mode) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.extraSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TranslatedText(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSelectionItem(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf("English", "Tagalog")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                MaterialTheme.shapes.small
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        languages.forEach { lang ->
            val isSelected = currentLanguage == lang
            Surface(
                onClick = { onLanguageSelected(lang) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = MaterialTheme.shapes.extraSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TranslatedText(
                        text = lang,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    subLabel: String,
    initialValue: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            TranslatedText(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            TranslatedText(
                subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = initialValue,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun WeightThresholdDialog(
    currentThreshold: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentThreshold.coerceAtMost(50.0))) }
    var isError by remember { mutableStateOf(false) }
    val presets = listOf(5.0, 10.0, 20.0, 30.0, 50.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Scale, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                TranslatedText(
                    stringResource(R.string.set_weight_threshold),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TranslatedText(
                    stringResource(R.string.enter_threshold_kg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            textValue = input
                            val parsed = input.toDoubleOrNull()
                            isError = parsed == null || parsed <= 0.0 || parsed > 50.0
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { TranslatedText(stringResource(R.string.weight_kg)) },
                    trailingIcon = {
                        Text(
                            "kg",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    isError = isError,
                    supportingText = if (isError) {
                        { TranslatedText(stringResource(R.string.invalid_threshold), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )

                // Quick Presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick Presets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = textValue.toDoubleOrNull() == preset
                            SuggestionChip(
                                onClick = {
                                    textValue = String.format(Locale.US, "%.1f", preset)
                                    isError = false
                                },
                                label = {
                                    Text(
                                        "${preset.toInt()} kg",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0 && parsed <= 50.0) {
                        onConfirm(parsed)
                    } else {
                        isError = true
                    }
                },
                enabled = !isError && textValue.toDoubleOrNull() != null
            ) {
                TranslatedText(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                TranslatedText(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
