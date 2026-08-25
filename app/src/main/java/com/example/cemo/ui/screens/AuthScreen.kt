package com.example.cemo.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.cemo.AppState
import com.example.cemo.R
import com.example.cemo.ThemeMode
import com.example.cemo.data.auth.AuthField
import com.example.cemo.data.auth.GoogleAuthUiClient
import com.example.cemo.ui.components.AppLogo
import com.example.cemo.ui.components.PremiumGlassCard
import com.example.cemo.ui.components.TranslatedCompactInputField
import com.example.cemo.ui.components.TranslatedPremiumButton
import com.example.cemo.ui.components.TranslatedText
import com.example.cemo.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    navController: NavController,
    googleAuthUiClient: GoogleAuthUiClient,
    viewModel: AuthViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(key1 = state.isSignInSuccessful) {
        if (state.isSignInSuccessful) {
            Toast.makeText(context, "Sign in successful", Toast.LENGTH_LONG).show()
            navController.navigate("main") {
                popUpTo("auth") { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    var isLoginMode by remember { mutableStateOf(true) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }

    // Clear the offending field so the error surfaces as its resting placeholder.
    LaunchedEffect(key1 = state.signInError) {
        if (state.signInError != null) {
            when (state.errorField) {
                AuthField.EMAIL -> email = ""
                AuthField.PASSWORD, AuthField.BOTH -> {
                    password = ""
                    confirmPassword = ""
                }
                AuthField.NAME -> { /* keep names; blank one already shows its hint */ }
            }
        }
    }

    LaunchedEffect(key1 = state.resetEmailSent) {
        if (state.resetEmailSent) {
            Toast.makeText(
                context,
                "Password reset link sent — check your inbox.",
                Toast.LENGTH_LONG
            ).show()
            viewModel.resetSentShown()
        }
    }

    LaunchedEffect(key1 = state.verificationSent) {
        if (state.verificationSent) {
            Toast.makeText(
                context,
                "Account created. We've sent a verification link to your email — verify it, then log in.",
                Toast.LENGTH_LONG
            ).show()
            isLoginMode = true
            password = ""
            confirmPassword = ""
            viewModel.verificationShown()
        }
    }

    val scrollState = rememberScrollState()
    val themeMode by AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    val isFormValid = if (isLoginMode) {
        email.isNotBlank() && password.isNotBlank()
    } else {
        firstName.isNotBlank() && lastName.isNotBlank() &&
            email.isNotBlank() && password.isNotBlank() && password == confirmPassword
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHeader()
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Glass Card
            PremiumGlassCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tab Toggle
                AuthTabToggle(
                    isLoginMode = isLoginMode,
                    onTabSelected = {
                        isLoginMode = it
                        viewModel.errorShown()
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Inputs — route the error to the field it belongs to.
                val err = state.signInError
                val emailError = err != null && (state.errorField == AuthField.EMAIL || state.errorField == AuthField.BOTH)
                val passwordError = err != null && (state.errorField == AuthField.PASSWORD || state.errorField == AuthField.BOTH)
                val nameError = err != null && state.errorField == AuthField.NAME
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(
                        visible = !isLoginMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TranslatedCompactInputField(
                                        label = stringResource(R.string.first_name),
                                        icon = Icons.Default.Person,
                                        value = firstName,
                                        onValueChange = {
                                            firstName = it
                                            if (err != null) viewModel.errorShown()
                                        },
                                        isError = nameError && firstName.isBlank(),
                                        errorMessage = if (nameError && firstName.isBlank()) "Required" else null
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    TranslatedCompactInputField(
                                        label = stringResource(R.string.last_name),
                                        icon = Icons.Default.Person,
                                        value = lastName,
                                        onValueChange = {
                                            lastName = it
                                            if (err != null) viewModel.errorShown()
                                        },
                                        isError = nameError && lastName.isBlank(),
                                        errorMessage = if (nameError && lastName.isBlank()) "Required" else null
                                    )
                                }
                            }
                            TranslatedCompactInputField(
                                label = stringResource(R.string.display_name),
                                icon = Icons.Default.Badge,
                                value = displayName,
                                onValueChange = {
                                    displayName = it
                                    if (err != null) viewModel.errorShown()
                                }
                            )
                        }
                    }

                    TranslatedCompactInputField(
                        label = stringResource(R.string.email_address),
                        icon = Icons.Default.Email,
                        value = email,
                        onValueChange = {
                            email = it
                            if (err != null) viewModel.errorShown()
                        },
                        isError = emailError,
                        errorMessage = if (state.errorField == AuthField.EMAIL) err else null
                    )

                    TranslatedCompactInputField(
                        label = stringResource(R.string.password),
                        icon = Icons.Default.Lock,
                        value = password,
                        isPassword = true,
                        onValueChange = {
                            password = it
                            if (err != null) viewModel.errorShown()
                        },
                        isError = passwordError,
                        errorMessage = if (state.errorField == AuthField.PASSWORD || state.errorField == AuthField.BOTH) err else null
                    )

                    AnimatedVisibility(
                        visible = !isLoginMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val mismatch = confirmPassword.isNotBlank() && password != confirmPassword
                        TranslatedCompactInputField(
                            label = stringResource(R.string.confirm_password),
                            icon = Icons.Default.Lock,
                            value = confirmPassword,
                            isPassword = true,
                            onValueChange = { confirmPassword = it },
                            isError = mismatch,
                            errorMessage = if (mismatch) "Passwords do not match" else null
                        )
                    }
                }

                // Options Row
                AnimatedVisibility(
                    visible = isLoginMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            )
                            TranslatedText(
                                text = stringResource(R.string.remember_me),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(x = (-4).dp)
                            )
                        }
                        TextButton(
                            onClick = { viewModel.sendPasswordReset(googleAuthUiClient, email) },
                            enabled = !state.isLoading,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            TranslatedText(
                                text = stringResource(R.string.forgot_password),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Button
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    TranslatedPremiumButton(
                        text = if (isLoginMode) stringResource(R.string.sign_in_with_email) else stringResource(R.string.create_account),
                        onClick = {
                            if (isLoginMode) {
                                viewModel.signInWithEmail(googleAuthUiClient, email, password)
                            } else {
                                viewModel.signUpWithEmail(googleAuthUiClient, email, password, firstName, lastName, displayName)
                            }
                        },
                        enabled = isFormValid
                    )
                }

                SocialSignInDivider()

                SocialSignInButton(
                    enabled = !state.isLoading,
                    onClick = { viewModel.signInWithGoogle(googleAuthUiClient) }
                )
            }
        }
    }
}

@Composable
private fun AuthHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(size = 80.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CEMO Monitor",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        TranslatedText(
            text = "SMART WASTE ANALYTICS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AuthTabToggle(
    isLoginMode: Boolean,
    onTabSelected: (Boolean) -> Unit
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabButton(
            text = stringResource(R.string.login),
            isSelected = isLoginMode,
            modifier = Modifier.weight(1f)
        ) {
            onTabSelected(true)
        }
        TabButton(
            text = stringResource(R.string.sign_up),
            isSelected = !isLoginMode,
            modifier = Modifier.weight(1f)
        ) {
            onTabSelected(false)
        }
    }
}

@Composable
private fun SocialSignInDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        TranslatedText(
            text = "OR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    }
}

@Composable
private fun SocialSignInButton(onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google),
            contentDescription = "Google Logo",
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(10.dp))
        TranslatedText(
            text = stringResource(R.string.continue_with_google),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        TranslatedText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
