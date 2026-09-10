package com.keltruc.mymemos.model

enum class AuthMethod { PASSWORD, PERSONAL_ACCESS_TOKEN }

/** One signed-in identity on one server. The app supports several at once. */
data class Account(
    val id: Long,
    val serverUrl: String,
    val userResourceName: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val role: UserRole,
    val authMethod: AuthMethod,
    val serverVersion: String,
)
