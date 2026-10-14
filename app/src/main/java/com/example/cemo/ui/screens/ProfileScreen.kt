package com.example.cemo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cemo.AppState
import com.example.cemo.data.auth.GoogleAuthUiClient
import com.example.cemo.data.image.AvatarCompressor
import com.example.cemo.data.model.User
import com.example.cemo.data.model.UserRole
import com.example.cemo.ui.components.*
import com.example.cemo.ui.theme.*
import com.example.cemo.viewmodel.WasteViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    googleAuthUiClient: GoogleAuthUiClient,
    viewModel: WasteViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authUser = remember { googleAuthUiClient.getSignedInUser() }
    val context = LocalContext.current
    val cachedUser by AppState.cachedUser.collectAsState()
    val themeMode by AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        com.example.cemo.ThemeMode.LIGHT -> false
        com.example.cemo.ThemeMode.DARK -> true
        com.example.cemo.ThemeMode.SYSTEM -> isSystemDark
    }

    // Seed from the cached profile so re-entering this screen paints immediately;
    // the Firestore fetch below then refreshes it in place.
    var userData by remember { mutableStateOf(cachedUser ?: authUser) }
    val sdf = remember<SimpleDateFormat> { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    // Editing State
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editDisplayName by remember { mutableStateOf("") }
    var editIsIndividual by remember { mutableStateOf(true) }
    var editEstName by remember { mutableStateOf("") }
    var editEstAddress by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Avatar picker — compresses the chosen image (any format the platform can
    // decode), uploads it to Firebase Storage and stores the resulting download
    // URL on the user record, so the photo follows the account across devices
    // instead of living only on this phone.
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingAvatar = true
                avatarError = null
                val current = userData
                if (current == null || current.uid.isBlank()) {
                    avatarError = "Sign in again to change your photo"
                } else {
                    try {
                        val bytes = AvatarCompressor.compress(context, uri)
                        val downloadUrl = googleAuthUiClient.uploadAvatar(
                            uid = current.uid,
                            bytes = bytes,
                            previousPhotoUrl = current.photoUrl
                        )
                        val updated = current.copy(photoUrl = downloadUrl, updatedAt = Date())
                        googleAuthUiClient.updateProfile(updated)
                        userData = updated
                    } catch (e: Exception) {
                        avatarError = e.message ?: "Photo upload failed"
                    }
                }
                isUploadingAvatar = false
            }
        }
    }
    val onChangeAvatar: () -> Unit = {
        avatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(authUser?.uid) {
        authUser?.uid?.let { uid ->
            googleAuthUiClient.getUserData(uid)?.let { fullUser ->
                userData = fullUser
                editFirstName = fullUser.firstName ?: ""
                editLastName = fullUser.lastName ?: ""
                editDisplayName = fullUser.displayName ?: ""
                editIsIndividual = fullUser.isIndividual
                editEstName = fullUser.establishmentName ?: ""
                editEstAddress = fullUser.establishmentAddress ?: ""
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { TranslatedText("Logout", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) },
            text = { TranslatedText("Are you sure you want to log out?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    TranslatedText("Yes", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    TranslatedText("No", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = {
            viewModel.refresh()
            authUser?.uid?.let { uid ->
                scope.launch {
                    googleAuthUiClient.getUserData(uid)?.let { fullUser ->
                        userData = fullUser
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.xl),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + Space.xxl,
                bottom = innerPadding.calculateBottomPadding() + 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Space.xl)
        ) {
            item {
                HeroProfileHeader(
                    user = userData,
                    isEditing = isEditing,
                    isUploadingAvatar = isUploadingAvatar,
                    avatarError = avatarError,
                    onEditClick = { isEditing = !isEditing },
                    onChangeAvatar = onChangeAvatar
                )
            }

            if (isEditing) {
                item {
                    EditProfileCard(
                        firstName = editFirstName,
                        onFirstNameChange = { editFirstName = it },
                        lastName = editLastName,
                        onLastNameChange = { editLastName = it },
                        displayName = editDisplayName,
                        onDisplayNameChange = { editDisplayName = it },
                        isIndividual = editIsIndividual,
                        onIsIndividualChange = { editIsIndividual = it },
                        establishmentName = editEstName,
                        onEstNameChange = { editEstName = it },
                        establishmentAddress = editEstAddress,
                        onEstAddressChange = { editEstAddress = it },
                        onSave = {
                            scope.launch {
                                val updatedUser = userData?.copy(
                                    firstName = editFirstName,
                                    lastName = editLastName,
                                    displayName = editDisplayName,
                                    isIndividual = editIsIndividual,
                                    establishmentName = if (editIsIndividual) null else editEstName,
                                    establishmentAddress = if (editIsIndividual) null else editEstAddress,
                                    updatedAt = Date()
                                )
                                if (updatedUser != null) {
                                    googleAuthUiClient.updateProfile(updatedUser)
                                    userData = updatedUser
                                    isEditing = false
                                }
                            }
                        },
                        onCancel = { isEditing = false }
                    )
                }
            } else {
                item { IdentityInformationCard(userData, isDark) }
                item { DynamicAccountLayout(userData) }
                item { ActivityTimelineSection(userData, sdf) }
                item { ProfileActionsSection(onLogoutClick = { showLogoutDialog = true }) }
            }
        }
    }
}

@Composable
private fun HeroProfileHeader(
    user: User?,
    isEditing: Boolean,
    isUploadingAvatar: Boolean,
    avatarError: String?,
    onEditClick: () -> Unit,
    onChangeAvatar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, PrimaryGreen.copy(alpha = 0.35f))
            ) {
                if (user?.photoUrl != null) {
                    // Key the cache on the profile version rather than the URL: the
                    // avatar always lives at the same Storage path, so a re-upload
                    // would otherwise risk serving the previous cached image.
                    val avatarCacheKey = "avatar_${user.uid}_${user.updatedAt?.time ?: user.createdAt?.time ?: 0L}"
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(user.photoUrl)
                            .memoryCacheKey(avatarCacheKey)
                            .diskCacheKey(avatarCacheKey)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person, null,
                            modifier = Modifier.size(60.dp),
                            tint = PrimaryGreen.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Camera badge — tap to change the avatar.
            Surface(
                onClick = onChangeAvatar,
                enabled = !isUploadingAvatar,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = PrimaryGreen,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.background)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoCamera, "Change photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (avatarError != null) {
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = avatarError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(Space.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Cemo User",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (user?.role == UserRole.ADMIN) {
                Spacer(Modifier.width(Space.sm))
                Icon(
                    Icons.Default.Verified, "Admin",
                    tint = PrimaryGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (!user?.email.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isEditing) {
            Spacer(modifier = Modifier.height(Space.lg))
            OutlinedButton(
                onClick = onEditClick,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(Size.iconSmall))
                Spacer(Modifier.width(Space.sm))
                Text("Edit Profile", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun IdentityInformationCard(user: User?, isDark: Boolean) {
    PremiumGlassCard {
        IdentityItem(Icons.Outlined.Person, "Display Name", user?.displayName ?: "Not set")
        HorizontalDivider(modifier = Modifier.padding(vertical = Space.lg))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                IdentityItem(Icons.Outlined.Badge, "First Name", user?.firstName ?: "Not set")
            }
            Box(modifier = Modifier.weight(1f)) {
                IdentityItem(Icons.Outlined.Badge, "Last Name", user?.lastName ?: "Not set")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Space.lg))

        IdentityItem(Icons.Outlined.Email, "Email Address", user?.email ?: "Not set")

        Spacer(modifier = Modifier.height(Space.xl))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            GlassBadge(
                user?.role?.name ?: "USER",
                if (user?.role == UserRole.ADMIN) ImpactGold else PrimaryGreen,
                if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
            GlassBadge(
                if (user?.isIndividual == true) "Individual" else "Establishment",
                MetricBlue,
                if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EditProfileCard(
    firstName: String, onFirstNameChange: (String) -> Unit,
    lastName: String, onLastNameChange: (String) -> Unit,
    displayName: String, onDisplayNameChange: (String) -> Unit,
    isIndividual: Boolean, onIsIndividualChange: (Boolean) -> Unit,
    establishmentName: String, onEstNameChange: (String) -> Unit,
    establishmentAddress: String, onEstAddressChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    PremiumGlassCard {
        Text(
            "EDIT ACCOUNT",
            style = MaterialTheme.typography.labelMedium,
            color = PrimaryGreen,
            modifier = Modifier.padding(bottom = Space.xl)
        )

        PremiumInputField("Display Name", Icons.Default.Badge, value = displayName, onValueChange = onDisplayNameChange)
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumInputField("First Name", Icons.Default.Person, value = firstName, onValueChange = onFirstNameChange)
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumInputField("Last Name", Icons.Default.Person, value = lastName, onValueChange = onLastNameChange)
            }
        }

        Spacer(Modifier.height(Space.xl))
        HorizontalDivider()
        Spacer(Modifier.height(Space.xl))

        Text(
            "ACCOUNT TYPE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.md)
        )

        // Segmented toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(Space.xs)
        ) {
            AccountTypeSegment(
                label = "Individual",
                selected = isIndividual,
                onClick = { onIsIndividualChange(true) },
                modifier = Modifier.weight(1f)
            )
            AccountTypeSegment(
                label = "Business",
                selected = !isIndividual,
                onClick = { onIsIndividualChange(false) },
                modifier = Modifier.weight(1f)
            )
        }

        if (!isIndividual) {
            Spacer(Modifier.height(Space.xl))
            PremiumInputField("Establishment Name", Icons.Default.Business, value = establishmentName, onValueChange = onEstNameChange)
            Spacer(Modifier.height(Space.lg))
            PremiumInputField("Establishment Address", Icons.Default.LocationOn, value = establishmentAddress, onValueChange = onEstAddressChange)
        }

        Spacer(Modifier.height(Space.xxl))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
            PremiumButton(
                text = "Save Changes",
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountTypeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.small,
        color = if (selected) PrimaryGreen else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IdentityItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(PrimaryGreen.copy(alpha = 0.1f), MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(Size.icon), tint = PrimaryGreen)
        }
        Spacer(Modifier.width(Space.lg))
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DynamicAccountLayout(user: User?) {
    if (user?.isIndividual == false) {
        PremiumGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PrimaryGreen.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Business, null, tint = PrimaryGreen, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(Space.lg))
                Column {
                    Text(
                        "Establishment",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryGreen
                    )
                    Text(
                        user.establishmentName ?: "Not set",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(Space.card))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = PrimaryGreen.copy(alpha = 0.6f), modifier = Modifier.size(Size.icon))
                Spacer(Modifier.width(Space.md))
                Text(
                    user.establishmentAddress ?: "No address on file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        PremiumGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PrimaryGreen.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonPin, null, tint = PrimaryGreen, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(Space.lg))
                Column {
                    Text("Personal Account", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Not linked to an establishment", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineSection(user: User?, sdf: SimpleDateFormat) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        TimelineCard(
            label = "Created",
            value = user?.createdAt?.let { sdf.format(it) } ?: "—",
            subtitle = user?.createdAt?.let { timeFormat.format(it) },
            icon = Icons.Default.AddCircleOutline,
            modifier = Modifier.weight(1f)
        )
        TimelineCard(
            label = "Updated",
            value = user?.updatedAt?.let { sdf.format(it) } ?: "—",
            subtitle = user?.updatedAt?.let { timeFormat.format(it) },
            icon = Icons.Default.Update,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimelineCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier,
    subtitle: String? = null
) {
    Box(
        modifier = modifier
            .cemoSurface(shape = MaterialTheme.shapes.large)
            .padding(Space.card)
    ) {
        Column {
            Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(Space.lg))
            TranslatedText(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileActionsSection(onLogoutClick: () -> Unit) {
    OutlinedButton(
        onClick = onLogoutClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text("Logout", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
