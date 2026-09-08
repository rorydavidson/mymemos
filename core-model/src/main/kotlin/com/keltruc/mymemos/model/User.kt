package com.keltruc.mymemos.model

enum class UserRole { ADMIN, USER }

data class User(
    /** Server resource name, e.g. `users/rory`. */
    val name: String,
    val username: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
    val description: String,
    val role: UserRole,
)
