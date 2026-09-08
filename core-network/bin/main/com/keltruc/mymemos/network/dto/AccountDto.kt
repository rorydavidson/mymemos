package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserStatsDto(
    val name: String = "",
    val memoTypeStats: MemoTypeStatsDto? = null,
    val tagCount: Map<String, Int> = emptyMap(),
    val memoCreatedTimestamps: List<String> = emptyList(),
    val memoUpdatedTimestamps: List<String> = emptyList(),
    val pinnedMemos: List<String> = emptyList(),
    val totalMemoCount: Int = 0,
)

@Serializable
data class MemoTypeStatsDto(val linkCount: Int = 0, val codeCount: Int = 0, val todoCount: Int = 0, val undoCount: Int = 0)

@Serializable
data class UserWriteDto(
    val name: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val password: String? = null,
    val role: String? = null,
    val state: String? = null,
)

@Serializable
data class ListUsersResponseDto(val users: List<UserDto> = emptyList(), val nextPageToken: String = "")

@Serializable
data class UserGeneralSettingDto(val locale: String = "", val memoVisibility: String = "", val theme: String = "")

@Serializable
data class UserSettingDto(val name: String = "", val generalSetting: UserGeneralSettingDto? = null)

@Serializable
data class PersonalAccessTokenDto(
    val name: String = "",
    val description: String = "",
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val lastUsedAt: String? = null,
)

@Serializable
data class ListPersonalAccessTokensResponseDto(val personalAccessTokens: List<PersonalAccessTokenDto> = emptyList())

@Serializable
data class CreatePersonalAccessTokenRequestDto(val description: String, val expiresInDays: Int? = null)

@Serializable
data class CreatePersonalAccessTokenResponseDto(val personalAccessToken: PersonalAccessTokenDto? = null, val token: String = "")

@Serializable
data class UserWebhookDto(
    val name: String = "",
    val url: String = "",
    val displayName: String = "",
    val createTime: String? = null,
    val updateTime: String? = null,
    val signingSecret: String? = null,
)

@Serializable
data class ListUserWebhooksResponseDto(val webhooks: List<UserWebhookDto> = emptyList())

@Serializable
data class InstanceGeneralSettingDto(
    val disallowUserRegistration: Boolean = false,
    val disallowPasswordAuth: Boolean = false,
    val additionalScript: String = "",
    val additionalStyle: String = "",
    val customProfile: CustomProfileDto? = null,
    val weekStartDayOffset: Int = 0,
    val disallowChangeUsername: Boolean = false,
    val disallowChangeNickname: Boolean = false,
)

@Serializable
data class CustomProfileDto(val title: String = "", val description: String = "", val logoUrl: String = "")

@Serializable
data class InstanceMemoRelatedSettingDto(
    val contentLengthLimit: Int = 0,
    val enableDoubleClickEdit: Boolean = false,
    val reactions: List<String> = emptyList(),
)

@Serializable
data class InstanceSettingDto(
    val name: String = "",
    val generalSetting: InstanceGeneralSettingDto? = null,
    val memoRelatedSetting: InstanceMemoRelatedSettingDto? = null,
)

@Serializable
data class InstanceStatsDto(
    val database: DatabaseStatsDto? = null,
    val localStorageBytes: Long = 0,
    val generatedTime: String? = null,
)

@Serializable
data class DatabaseStatsDto(val driver: String = "", val sizeBytes: Long = 0)
