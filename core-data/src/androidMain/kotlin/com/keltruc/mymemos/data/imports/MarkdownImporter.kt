package com.keltruc.mymemos.data.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android way in: content URIs from the document picker, read here and handed to the
 * shared [MarkdownImporting] as bytes with their names and timestamps.
 */
class MarkdownImporter constructor(
    private val context: Context,
    memoRepository: MemoRepository,
) {
    private val importing = MarkdownImporting(memoRepository)

    suspend fun import(accountId: Long, uris: List<Uri>, visibility: Visibility): MarkdownImporting.Result =
        withContext(Dispatchers.IO) {
            val picked = uris.map { uri ->
                val label = displayName(uri) ?: uri.lastPathSegment ?: "file"
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("could not be opened")
                }.getOrElse { return@map MarkdownImporting.Picked(label, ByteArray(0), null) }
                MarkdownImporting.Picked(label, bytes, lastModified(uri))
            }
            importing.import(accountId, picked, visibility)
        }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun lastModified(uri: Uri): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
}
