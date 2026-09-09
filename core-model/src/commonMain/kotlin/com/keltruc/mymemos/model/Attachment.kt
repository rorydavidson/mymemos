package com.keltruc.mymemos.model

import kotlin.time.Instant

data class Attachment(
    val localId: String,
    /** Server resource name, e.g. `attachments/AbCdEf`. Null until uploaded. */
    val remoteName: String?,
    val memoLocalId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val externalLink: String?,
    /** Absolute path of a locally held copy, if any. */
    val localPath: String?,
    val createTime: Instant,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
