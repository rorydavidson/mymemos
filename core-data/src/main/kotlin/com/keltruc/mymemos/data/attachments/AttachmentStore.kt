package com.keltruc.mymemos.data.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.Uuid

/** Local copies of attachment files, in app-private storage, named by attachment localId. */
class AttachmentStore constructor(private val context: Context) {

    data class Staged(val localId: String, val file: File, val filename: String, val mimeType: String, val size: Long)

    private val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }

    /** Copies a content URI into private storage so it survives the picker's grant expiring. */
    suspend fun stage(uri: Uri): Staged = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = uri.lastPathSegment ?: "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { displayName = it }
        }
        val localId = Uuid.random().toString()
        val target = File(dir, localId)
        resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        Staged(localId, target, displayName, mime, target.length())
    }

    fun file(localId: String): File = File(dir, localId)

    suspend fun delete(localId: String) = withContext(Dispatchers.IO) { file(localId).delete() }
}
