package com.keltruc.mymemos.data.imports

import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.zip.ZipReader
import com.keltruc.mymemos.model.Visibility

/**
 * Turns Markdown files, or zips of them, into memos. Dates come from the file rather than the
 * clock so imported notes land where they belong on the timeline, and folders inside a zip become
 * nested tags.
 *
 * Memos v0.30 has no bulk create, so this is a loop of ordinary creates through the outbox. A file
 * that cannot be read does not stop the rest: the result says what landed and what did not.
 * Reading the files the user picked is the platform's job; they arrive here as bytes.
 */
class MarkdownImporting constructor(
    private val memoRepository: MemoRepository,
) {
    data class Result(val imported: Int, val skipped: Int, val duplicates: Int, val failures: List<String>)

    /** One thing the user picked: a Markdown file or a zip of them, already read. */
    class Picked(val name: String, val bytes: ByteArray, val modifiedEpochMs: Long?)

    private class Source(val name: String, val text: String, val modifiedEpochMs: Long?, val folderTag: String?)

    suspend fun import(accountId: Long, picked: List<Picked>, visibility: Visibility): Result {
        var imported = 0
        var skipped = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        // Memos already recognised in this run, so a zip holding the same note twice adds it once.
        val seen = mutableSetOf<String>()

        for (file in picked) {
            val sources = runCatching { read(file) }.getOrElse {
                failures += "${file.name}: ${it.message ?: "could not be read"}"
                continue
            }
            if (sources.isEmpty()) skipped++
            for (source in sources) {
                runCatching { create(accountId, source, visibility, seen) }
                    .onSuccess { if (it) imported++ else duplicates++ }
                    .onFailure { failures += "${source.name}: ${it.message ?: "could not be imported"}" }
            }
        }
        return Result(imported, skipped, duplicates, failures)
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

    /** The extension and the reported type both lie often enough to be worth checking the bytes. */
    private fun read(file: Picked): List<Source> =
        if (ZipReader.looksLikeZip(file.bytes)) {
            ZipReader.entries(file.bytes)
                .filter { !it.isDirectory && it.name.substringAfterLast('/').endsWith(".md", ignoreCase = true) }
                .map { entry ->
                    Source(
                        name = entry.name,
                        text = entry.bytes.decodeToString(),
                        modifiedEpochMs = entry.modifiedEpochMs,
                        folderTag = MarkdownImport.tagForPath(entry.name),
                    )
                }
        } else {
            listOf(Source(file.name, file.bytes.decodeToString(), file.modifiedEpochMs, folderTag = null))
        }
}
