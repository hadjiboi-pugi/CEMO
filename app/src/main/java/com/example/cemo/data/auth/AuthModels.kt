package com.example.cemo.data.auth

import com.example.cemo.data.model.User

/** Which input an auth error belongs to, so it can be shown on the right field. */
enum class AuthField { NAME, EMAIL, PASSWORD, BOTH }

data class SignInResult(
    val data: User?,
    val errorMessage: String?,
    val verificationSent: Boolean = false,
    val errorField: AuthField = AuthField.BOTH
)

data class SignInState(
    val isSignInSuccessful: Boolean = false,
    val signInError: String? = null,
    val isLoading: Boolean = false,
    val verificationSent: Boolean = false,
    val resetEmailSent: Boolean = false,
    val errorField: AuthField = AuthField.BOTH
)
