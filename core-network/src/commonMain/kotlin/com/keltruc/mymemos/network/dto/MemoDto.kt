package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of `memos.api.v1.Memo` as rendered by grpc-gateway (camelCase, enums as
 * strings, timestamps as RFC 3339). Server omits fields at their proto default, so every
 * field has one here.
 */
@Serializable
data class MemoDto(
    val name: String = "",
    val state: String = "NORMAL",
    val creator: String = "",
    val createTime: String? = null,
    val updateTime: String? = null,
    val content: String = "",
    val visibility: String = "PRIVATE",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val attachments: List<AttachmentDto> = emptyList(),
    val relations: List<MemoRelationDto> = emptyList(),
    val reactions: List<ReactionDto> = emptyList(),
    val property: MemoPropertyDto? = null,
    val parent: String? = null,
    val snippet: String = "",
    val location: LocationDto? = null,
)

@Serializable
data class MemoPropertyDto(
    val hasLink: Boolean = false,
    val hasTaskList: Boolean = false,
    val hasCode: Boolean = false,
    val hasIncompleteTasks: Boolean = false,
    val title: String = "",
)

@Serializable
data class MemoRelationDto(
    val memo: MemoRefDto,
    val relatedMemo: MemoRefDto,
    val type: String,
)

@Serializable
data class MemoRefDto(
    val name: String,
    val snippet: String = "",
)

@Serializable
data class ReactionDto(
    val name: String = "",
    val creator: String = "",
    val contentId: String = "",
    val reactionType: String = "",
    val createTime: String? = null,
)

@Serializable
data class ListMemosResponseDto(
    val memos: List<MemoDto> = emptyList(),
    val nextPageToken: String = "",
)

/** Body for CreateMemo and UpdateMemo. Only send what the caller intends to set. */
@Serializable
data class MemoWriteDto(
    val name: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    val pinned: Boolean? = null,
    val state: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val location: LocationDto? = null,
    val attachments: List<AttachmentRefDto>? = null,
)

@Serializable
data class AttachmentRefDto(val name: String)
