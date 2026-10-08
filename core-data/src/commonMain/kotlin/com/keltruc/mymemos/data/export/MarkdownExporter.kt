package com.keltruc.mymemos.data.export

import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.zip.ByteSink
import com.keltruc.mymemos.data.zip.ZipWriter
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.model.Memo

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

    /** The format itself lives in [MarkdownFormat], where clients without Room can reach it. */
    companion object {
        fun attachmentPath(localId: String, filename: String): String = MarkdownFormat.attachmentPath(localId, filename)

        fun fileNameFor(memo: Memo): String = MarkdownFormat.fileNameFor(memo)

        private fun uniqueName(base: String, used: MutableSet<String>): String = MarkdownFormat.uniqueName(base, used)

        fun render(memo: Memo): String = MarkdownFormat.render(memo)
    }
}
