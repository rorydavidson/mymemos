package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val name: String = "",
    val role: String = "USER",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val state: String = "NORMAL",
    val createTime: String? = null,
    val updateTime: String? = null,
)

@Serializable
data class InstanceProfileDto(
    val version: String = "",
    val demo: Boolean = false,
    val instanceUrl: String = "",
    val admin: UserDto? = null,
    val commit: String = "",
    val needsSetup: Boolean = false,
)
