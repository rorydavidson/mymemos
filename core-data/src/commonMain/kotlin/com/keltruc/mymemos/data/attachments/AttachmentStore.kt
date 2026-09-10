package com.keltruc.mymemos.data.attachments

/**
 * Local copies of attachment files, named by attachment localId.
 *
 * Getting a file into the store is a platform job: a content URI on Android, a file URL on a
 * Mac. What the sync engine and the repositories need afterwards is only this.
 */
interface AttachmentStore {

    /** A file already copied into the store, ready to be recorded against a memo. */
    data class Staged(
        val localId: String,
        val filename: String,
        val mimeType: String,
        val size: Long,
    )

    suspend fun exists(localId: String): Boolean

    suspend fun readBytes(localId: String): ByteArray

    suspend fun delete(localId: String)
}
