package com.keltruc.mymemos.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentDto(
    val name: String = "",
    val createTime: String? = null,
    val filename: String = "",
    val externalLink: String = "",
    val type: String = "",
    val size: Long = 0,
    val memo: String? = null,
)

/** CreateAttachment body. `content` is base64 (proto bytes in JSON). */
@Serializable
data class AttachmentCreateDto(
    val filename: String,
    val type: String,
    val content: String,
    val memo: String? = null,
)

@Serializable
data class ListAttachmentsResponseDto(
    val attachments: List<AttachmentDto> = emptyList(),
    val nextPageToken: String = "",
)

@Serializable
data class SetMemoAttachmentsRequestDto(val attachments: List<AttachmentRefDto>)
