package com.example.cemo.data.model

import java.util.Date

enum class UserRole {
    USER,
    ADMIN
}

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val photoUrl: String? = null,
    val role: UserRole = UserRole.USER,
    val isIndividual: Boolean = true,
    val establishmentName: String? = null,
    val establishmentAddress: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)
