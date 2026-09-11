package com.keltruc.mymemos.data.export

import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.zip.ByteSink
import com.keltruc.mymemos.data.zip.ZipWriter
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.model.Memo
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Writes every memo as a Markdown file with YAML front matter into a zip, plus any
 * attachment files held locally. Readable by Obsidian and friends. Where the zip goes is the
 * platform's business: a content URI on Android, a file the user chose on a Mac or a phone.
 */
class MarkdownExporter constructor(
    private val memoDao: MemoDao,
    private val attachmentStore: AttachmentStore,
) {
    suspend fun export(accountId: Long, sink: ByteSink): Int {
        val memos = memoDao.allForExport(accountId).map { it.toModel() }
        val zip = ZipWriter(sink)
        val usedNames = mutableSetOf<String>()
        for (memo in memos) {
            val name = uniqueName(fileNameFor(memo), usedNames)
            zip.entry("memos/$name", render(memo).encodeToByteArray(), memo.updateTime.toEpochMilliseconds())
            for (a in memo.attachments) {
                if (!attachmentStore.exists(a.localId)) continue
                zip.entry(attachmentPath(a.localId, a.filename), attachmentStore.readBytes(a.localId), a.createTime.toEpochMilliseconds())
            }
        }
        zip.close()
        return memos.size
    }

    companion object {
        /**
         * Zip entry for an attachment. The filename comes from the server, so it is reduced to a
         * bare name first: a value like "../../.bashrc" would otherwise write outside the export
         * when unpacked.
         */
        fun attachmentPath(localId: String, filename: String): String {
            val bare = filename.replace('\\', '/').substringAfterLast('/').trim().trimStart('.')
            return "attachments/$localId-${bare.ifEmpty { "file" }}"
        }

        fun fileNameFor(memo: Memo): String {
            val day = memo.createTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            val slug = memo.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                .replace(Regex("[#*`\\[\\]()]"), "").trim()
                .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
            return if (slug.isEmpty()) "$day.md" else "$day-$slug.md"
        }

        private fun uniqueName(base: String, used: MutableSet<String>): String {
            var name = base
            var n = 2
            while (!used.add(name)) name = base.removeSuffix(".md") + "-$n.md".also { n++ }
            return name
        }

        fun render(memo: Memo): String = buildString {
            appendLine("---")
            appendLine("created: ${memo.createTime}")
            appendLine("updated: ${memo.updateTime}")
            appendLine("visibility: ${memo.visibility.name.lowercase()}")
            if (memo.pinned) appendLine("pinned: true")
            if (memo.state.name != "NORMAL") appendLine("archived: true")
            if (memo.tags.isNotEmpty()) appendLine("tags: [${memo.tags.joinToString(", ")}]")
            memo.remoteName?.let { appendLine("memos_id: $it") }
            memo.location?.let { appendLine("location: [${it.latitude}, ${it.longitude}]${if (it.placeholder.isNotEmpty()) " # ${it.placeholder}" else ""}") }
            if (memo.attachments.isNotEmpty()) {
                appendLine("attachments:")
                memo.attachments.forEach { appendLine("  - ${attachmentPath(it.localId, it.filename)}") }
            }
            appendLine("---")
            appendLine()
            append(memo.content.trimEnd())
            appendLine()
        }
    }
}
