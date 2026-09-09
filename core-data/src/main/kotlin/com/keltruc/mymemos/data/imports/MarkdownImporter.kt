package com.keltruc.mymemos.data.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Visibility
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns Markdown files, or zips of them, into memos. Dates come from the file rather than the
 * clock so imported notes land where they belong on the timeline, and folders inside a zip become
 * nested tags.
 *
 * Memos v0.30 has no bulk create, so this is a loop of ordinary creates through the outbox. A file
 * that cannot be read does not stop the rest: the result says what landed and what did not.
 */
@Singleton
class MarkdownImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoRepository: MemoRepository,
) {
    data class Result(val imported: Int, val skipped: Int, val duplicates: Int, val failures: List<String>)

    private data class Source(val name: String, val text: String, val modifiedEpochMs: Long?, val folderTag: String?)

    suspend fun import(accountId: Long, uris: List<Uri>, visibility: Visibility): Result = withContext(Dispatchers.IO) {
        var imported = 0
        var skipped = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        // Memos already recognised in this run, so a zip holding the same note twice adds it once.
        val seen = mutableSetOf<String>()

        for (uri in uris) {
            val label = displayName(uri) ?: uri.lastPathSegment ?: "file"
            val sources = runCatching { read(uri, label) }.getOrElse {
                failures += "$label: ${it.message ?: "could not be read"}"
                continue
            }
            if (sources.isEmpty()) skipped++
            for (source in sources) {
                runCatching { create(accountId, source, visibility, seen) }
                    .onSuccess { if (it) imported++ else duplicates++ }
                    .onFailure { failures += "${source.name}: ${it.message ?: "could not be imported"}" }
            }
        }
        Result(imported, skipped, duplicates, failures)
    }

    /** Returns false when the file is a memo this account already has, rather than a new one. */
    private suspend fun create(accountId: Long, source: Source, visibility: Visibility, seen: MutableSet<String>): Boolean {
        val parsed = MarkdownImport.parse(source.text)
        if (parsed.body.isBlank()) return false
        // Re-importing an export of this account would otherwise duplicate every memo in it.
        parsed.remoteName?.let { name ->
            if (!seen.add(name)) return false
            if (memoRepository.findByRemoteName(accountId, name) != null) return false
        }
        val tags = (listOfNotNull(source.folderTag) + parsed.tags).distinct()
        // Front matter first, then the zip entry or file's own timestamp, then the clock.
        val created = parsed.created?.toEpochMilliseconds() ?: source.modifiedEpochMs
        val updated = parsed.updated?.toEpochMilliseconds() ?: created
        memoRepository.create(
            accountId = accountId,
            rawContent = MarkdownImport.withTags(parsed.body, tags),
            visibility = parsed.visibility?.let { runCatching { Visibility.valueOf(it) }.getOrNull() } ?: visibility,
            pinned = parsed.pinned,
            createdAtEpochMs = created,
            updatedAtEpochMs = updated,
        )
        return true
    }

    private fun read(uri: Uri, label: String): List<Source> {
        val open = { context.contentResolver.openInputStream(uri) ?: error("could not be opened") }
        return if (open().use(::looksLikeZip)) open().use { readZip(it) } else listOf(readSingle(open(), label, uri))
    }

    /** The extension and the reported type both lie often enough to be worth checking the bytes. */
    private fun looksLikeZip(input: InputStream): Boolean {
        val magic = ByteArray(4)
        return input.read(magic) == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
            magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
    }

    private fun readSingle(input: InputStream, label: String, uri: Uri): Source =
        Source(label, input.use { it.readBytes().decodeToString() }, lastModified(uri), folderTag = null)

    private fun readZip(input: InputStream): List<Source> = buildList {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.substringAfterLast('/').endsWith(".md", ignoreCase = true)) {
                    zip.closeEntry()
                    continue
                }
                // readBytes on the zip stream stops at the end of the current entry.
                val text = zip.readBytes().decodeToString()
                add(
                    Source(
                        name = entry.name,
                        text = text,
                        modifiedEpochMs = entry.lastModifiedTime?.toMillis() ?: entry.time.takeIf { it > 0 },
                        folderTag = MarkdownImport.tagForPath(entry.name),
                    ),
                )
                zip.closeEntry()
            }
        }
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
