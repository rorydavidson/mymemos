package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.export.MarkdownFormat
import com.keltruc.mymemos.data.imports.MarkdownImport
import com.keltruc.mymemos.data.zip.ByteArraySink
import com.keltruc.mymemos.data.zip.ZipEntry
import com.keltruc.mymemos.data.zip.ZipFormatException
import com.keltruc.mymemos.data.zip.ZipReader
import com.keltruc.mymemos.data.zip.ZipWriter
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.web.store.AccountRecord
import com.keltruc.mymemos.web.store.MemoRecord
import com.keltruc.mymemos.web.store.OpRecord
import com.keltruc.mymemos.web.store.Prefs
import com.keltruc.mymemos.web.store.TemplateRecord
import com.keltruc.mymemos.web.store.WebStore
import com.keltruc.mymemos.web.store.toModel
import com.keltruc.mymemos.web.sync.WebMemos
import com.keltruc.mymemos.web.sync.WebSyncEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Export, import, backup and restore: the web's half of "your data". */
internal class DataTransfer(private val store: WebStore, private val memos: WebMemos) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The same files MarkdownExporter writes, through the same shared format. */
    suspend fun export(account: AccountRecord): ByteArray {
        val all = store.memosFor(account.id).filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }.sortedBy { it.createTimeEpochMs }
        val sink = ByteArraySink()
        val zip = ZipWriter(sink)
        val used = mutableSetOf<String>()
        for (record in all) {
            val memo = record.toModel()
            zip.entry("memos/" + MarkdownFormat.uniqueName(MarkdownFormat.fileNameFor(memo), used), MarkdownFormat.render(memo).encodeToByteArray(), record.updateTimeEpochMs)
            for (a in record.attachments) {
                val bytes = store.readBlob(a.localId) ?: continue
                zip.entry(MarkdownFormat.attachmentPath(a.localId, a.filename), bytes, a.createTimeEpochMs)
            }
        }
        zip.close()
        return sink.toByteArray()
    }

    private class Source(val name: String, val text: String, val modifiedEpochMs: Long?, val folderTag: String?)

    /** MarkdownImporting's loop, with the same de-duplication and the same reading of files. */
    suspend fun import(account: AccountRecord, picked: List<PickedFile>): ImportResultRow {
        var imported = 0
        var skipped = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (file in picked) {
            val sources = runCatching { read(file) }.getOrElse {
                failures += "${file.name}: ${it.message ?: "could not be read"}"
                continue
            }
            if (sources.isEmpty()) skipped++
            for (source in sources) {
                runCatching { create(account, source, seen) }
                    .onSuccess { if (it) imported++ else duplicates++ }
                    .onFailure { failures += "${source.name}: ${it.message ?: "could not be imported"}" }
            }
        }
        return ImportResultRow(imported, skipped, duplicates, failures.toTypedArray())
    }

    private suspend fun create(account: AccountRecord, source: Source, seen: MutableSet<String>): Boolean {
        val parsed = MarkdownImport.parse(source.text)
        if (parsed.body.isBlank()) return false
        parsed.remoteName?.let { name ->
            if (!seen.add(name)) return false
            if (store.memoByRemoteName(account.id, name) != null) return false
        }
        val tags = (listOfNotNull(source.folderTag) + parsed.tags).distinct()
        val created = parsed.created?.toEpochMilliseconds() ?: source.modifiedEpochMs
        memos.create(
            account.id,
            MarkdownImport.withTags(parsed.body, tags),
            parsed.visibility?.let { runCatching { Visibility.valueOf(it) }.getOrNull() } ?: Visibility.PRIVATE,
            parsed.pinned,
            created,
            parsed.updated?.toEpochMilliseconds() ?: created,
        )
        return true
    }

    private fun read(file: PickedFile): List<Source> {
        val bytes = file.bytes.toByteArray()
        return if (ZipReader.looksLikeZip(bytes)) {
            checkedEntries(bytes)
                .filter { !it.isDirectory && it.name.substringAfterLast('/').endsWith(".md", ignoreCase = true) }
                .map { Source(it.name, it.bytes.decodeToString(), it.modifiedEpochMs, MarkdownImport.tagForPath(it.name)) }
        } else {
            listOf(Source(file.name, bytes.decodeToString(), file.modifiedEpochMs?.toLong(), null))
        }
    }

    @Serializable
    private class Snapshot(
        val memos: List<MemoRecord>,
        val ops: List<OpRecord>,
        val templates: List<TemplateRecord>,
        val prefs: Prefs,
    )

    /** Credentials are left out: a restored browser signs in again, as a restored app does. */
    suspend fun backup(account: AccountRecord): ByteArray {
        val records = store.memosFor(account.id)
        val snapshot = Snapshot(records, store.opsFor(account.id), store.templates, store.prefs)
        val sink = ByteArraySink()
        val zip = ZipWriter(sink)
        zip.entry(STATE, json.encodeToString(Snapshot.serializer(), snapshot).encodeToByteArray())
        for (a in records.flatMap { it.attachments }) store.readBlob(a.localId)?.let { zip.entry("attachments/${a.localId}", it) }
        zip.close()
        return sink.toByteArray()
    }

    /**
     * Everything is unpacked and checked before anything live is touched. The backup's rows
     * are re-homed onto the active account, so a backup made under one sign-in restores under
     * another for the same user.
     */
    suspend fun restore(account: AccountRecord, plain: ByteArray) {
        val entries = checkedEntries(plain).filter { !it.isDirectory }
        val state = entries.firstOrNull { it.name == STATE } ?: error("Not a MyMemos web backup")
        val snapshot = json.decodeFromString(Snapshot.serializer(), state.bytes.decodeToString())
        val blobs = entries.filter { it.name.startsWith("attachments/") }.map { it.name.removePrefix("attachments/") to it.bytes }
        for ((id, bytes) in blobs) if ('/' !in id) store.writeBlob(id, bytes)
        // Queued ops replay with this account's credential, so only those that act on a memo
        // in the backup survive, and a delete only for the server memo that memo really is: a
        // doctored backup must not be able to aim one at anything else.
        val byId = snapshot.memos.associateBy { it.localId }
        val ops = snapshot.ops.filter { op ->
            val memo = byId[op.memoLocalId] ?: return@filter false
            op.type != OpRecord.DELETE || runCatching {
                json.decodeFromString(WebSyncEngine.DeletePayload.serializer(), op.payloadJson).remoteName == memo.remoteName
            }.getOrDefault(false)
        }
        store.write {
            deleteAllForAccount(account.id)
            snapshot.memos.forEach { upsertMemo(it.copy(accountId = account.id)) }
            ops.forEach { insertOp(it.copy(accountId = account.id)) }
            putTemplates(snapshot.templates)
            putPrefs(snapshot.prefs.copy(knownServers = (store.prefs.knownServers + snapshot.prefs.knownServers).distinct()))
        }
    }

    /**
     * The archive's entries, refused outright when there are implausibly many or they claim
     * more than [MAX_ARCHIVE_BYTES] between them. Each entry is capped on inflate too, but
     * a crafted zip can point thousands of entries at one compressed blob.
     */
    private fun checkedEntries(archive: ByteArray): List<ZipEntry> {
        val entries = ZipReader.entries(archive)
        if (entries.size > MAX_ENTRIES) throw ZipFormatException("too many files in the archive")
        if (entries.sumOf { it.size.toLong() } > MAX_ARCHIVE_BYTES) throw ZipFormatException("archive too large to open here")
        return entries
    }

    private companion object {
        const val STATE = "web/state.json"
        const val MAX_ENTRIES = 20_000
        const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
    }
}
