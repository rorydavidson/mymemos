package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ListMemoCommentsResponseDto(val memos: List<MemoDto> = emptyList(), val nextPageToken: String = "")

@Serializable
data class ListReactionsResponseDto(val reactions: List<ReactionDto> = emptyList(), val nextPageToken: String = "")

@Serializable
data class UpsertReactionRequestDto(val reaction: ReactionWriteDto)

@Serializable
data class ReactionWriteDto(val contentId: String, val reactionType: String)

@Serializable
data class SetMemoRelationsRequestDto(val relations: List<MemoRelationWriteDto>)

@Serializable
data class MemoRelationWriteDto(val memo: MemoRefDto, val relatedMemo: MemoRefDto, val type: String)

@Serializable
data class MemoShareDto(val name: String = "", val createTime: String? = null, val expireTime: String? = null)

@Serializable
data class ListMemoSharesResponseDto(val memoShares: List<MemoShareDto> = emptyList())

@Serializable
data class ShortcutDto(val name: String = "", val title: String = "", val filter: String = "")

@Serializable
data class ListShortcutsResponseDto(val shortcuts: List<ShortcutDto> = emptyList())

@Serializable
data class UserNotificationDto(
    val name: String = "",
    val sender: String = "",
    val senderUser: UserDto? = null,
    val status: String = "UNREAD",
    val createTime: String? = null,
    val type: String = "",
    val memoComment: NotificationPayloadDto? = null,
    val memoMention: NotificationPayloadDto? = null,
)

@Serializable
data class NotificationPayloadDto(
    val memo: String = "",
    val relatedMemo: String = "",
    val memoSnippet: String = "",
    val relatedMemoSnippet: String = "",
)

@Serializable
data class ListNotificationsResponseDto(val notifications: List<UserNotificationDto> = emptyList(), val nextPageToken: String = "")

@Serializable
data class NotificationWriteDto(val name: String, val status: String)
