package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PasswordCredentialsDto(val username: String, val password: String)

@Serializable
data class SignInRequestDto(val passwordCredentials: PasswordCredentialsDto)

@Serializable
data class SignInResponseDto(
    val user: UserDto,
    val accessToken: String = "",
    val accessTokenExpiresAt: String? = null,
)

@Serializable
data class RefreshTokenResponseDto(
    val accessToken: String = "",
    val expiresAt: String? = null,
)

@Serializable
data class GetCurrentUserResponseDto(val user: UserDto)
