package com.example.cemo.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cemo.data.auth.AuthField
import com.example.cemo.data.auth.GoogleAuthUiClient
import com.example.cemo.data.auth.SignInResult
import com.example.cemo.data.auth.SignInState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(SignInState())
    val state = _state.asStateFlow()

    fun onSignInResult(result: SignInResult) {
        _state.update {
            it.copy(
                isLoading = false,
                isSignInSuccessful = result.data != null,
                signInError = result.errorMessage,
                errorField = result.errorField,
                verificationSent = result.verificationSent
            )
        }
    }

    fun signInWithEmail(client: GoogleAuthUiClient, email: String, pass: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank() || pass.isBlank()) {
            setError("Enter your email and password", AuthField.BOTH)
            return
        }
        if (!isValidEmail(trimmed)) {
            setError("Enter a valid email address", AuthField.EMAIL)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, signInError = null) }
            onSignInResult(client.signInWithEmail(trimmed, pass))
        }
    }

    fun signUpWithEmail(
        client: GoogleAuthUiClient,
        email: String,
        pass: String,
        firstName: String,
        lastName: String,
        displayName: String
    ) {
        val trimmed = email.trim()
        if (firstName.isBlank() || lastName.isBlank()) {
            setError("Enter your first and last name", AuthField.NAME)
            return
        }
        if (trimmed.isBlank() || pass.isBlank()) {
            setError("Enter your email and password", AuthField.BOTH)
            return
        }
        if (!isValidEmail(trimmed)) {
            setError("Enter a valid email address", AuthField.EMAIL)
            return
        }
        if (pass.length < 6) {
            setError("Use at least 6 characters", AuthField.PASSWORD)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, signInError = null) }
            onSignInResult(client.signUpWithEmail(trimmed, pass, firstName.trim(), lastName.trim(), displayName.trim()))
        }
    }

    fun sendPasswordReset(client: GoogleAuthUiClient, email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank() || !isValidEmail(trimmed)) {
            setError("Enter your email to reset", AuthField.EMAIL)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, signInError = null) }
            val result = client.sendPasswordReset(trimmed)
            if (result.errorMessage != null) {
                _state.update {
                    it.copy(isLoading = false, signInError = result.errorMessage, errorField = result.errorField)
                }
            } else {
                _state.update { it.copy(isLoading = false, resetEmailSent = true) }
            }
        }
    }

    fun signInWithGoogle(client: GoogleAuthUiClient) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, signInError = null) }
            onSignInResult(client.signIn())
        }
    }

    /** Clears the error after it has been shown so the same error can surface again. */
    fun errorShown() {
        _state.update { it.copy(signInError = null) }
    }

    /** Resets the verification flag once the UI has reacted to it. */
    fun verificationShown() {
        _state.update { it.copy(verificationSent = false) }
    }

    /** Resets the reset-email flag once the UI has reacted to it. */
    fun resetSentShown() {
        _state.update { it.copy(resetEmailSent = false) }
    }

    fun resetState() {
        _state.update { SignInState() }
    }

    private fun setError(message: String, field: AuthField) {
        _state.update { it.copy(signInError = message, errorField = field, isLoading = false) }
    }

    private fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
