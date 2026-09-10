package com.keltruc.mymemos.data.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Android half of [AttachmentStore]: files in app-private storage. */
class FileAttachmentStore(private val context: Context) : AttachmentStore {

    private val dir: File get() = File(context.filesDir, "attachments").apply { mkdirs() }

    /** Copies a content URI into private storage so it survives the picker's grant expiring. */
    suspend fun stage(uri: Uri): AttachmentStore.Staged = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = uri.lastPathSegment ?: "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { displayName = it }
        }
        val localId = Uuid.random().toString()
        val target = File(dir, localId)
        resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        AttachmentStore.Staged(localId, displayName, mime, target.length())
    }

    fun file(localId: String): File = File(dir, localId)

    override suspend fun exists(localId: String): Boolean = withContext(Dispatchers.IO) { file(localId).exists() }

    override suspend fun readBytes(localId: String): ByteArray = withContext(Dispatchers.IO) { file(localId).readBytes() }

    override suspend fun delete(localId: String) = withContext(Dispatchers.IO) { file(localId).delete(); Unit }
}
