package com.keltruc.mymemos.data.export

import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MarkdownExporterTest {
    private val memo = Memo(
        localId = "l", accountId = 1, remoteName = "memos/abc", creator = null,
        content = "# Shopping list\n- [ ] milk #home", visibility = Visibility.PRIVATE, state = MemoState.NORMAL,
        pinned = true, tags = listOf("home"), createTime = Instant.parse("2026-09-08T09:00:00Z"),
        updateTime = Instant.parse("2026-09-08T10:00:00Z"), snippet = "", hasTaskList = true, hasIncompleteTasks = true,
        hasLink = false, hasCode = false, location = null, attachments = emptyList(), syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `file name is date plus slug`() {
        assertEquals("2026-09-08-shopping-list.md", MarkdownExporter.fileNameFor(memo))
    }

    @Test
    fun `attachment path keeps a plain name`() {
        assertEquals("attachments/id1-photo.jpg", MarkdownExporter.attachmentPath("id1", "photo.jpg"))
    }

    @Test
    fun `attachment path strips a traversing server filename`() {
        assertEquals("attachments/id1-bashrc", MarkdownExporter.attachmentPath("id1", "../../.bashrc"))
        assertEquals("attachments/id1-evil.sh", MarkdownExporter.attachmentPath("id1", "..\\..\\evil.sh"))
        assertEquals("attachments/id1-file", MarkdownExporter.attachmentPath("id1", "../"))
    }

    @Test
    fun `front matter carries the metadata and content follows`() {
        val md = MarkdownExporter.render(memo)
        assertTrue(md.startsWith("---\ncreated: 2026-09-08T09:00:00Z\n"))
        assertTrue(md.contains("pinned: true"))
        assertTrue(md.contains("tags: [home]"))
        assertTrue(md.endsWith("---\n\n# Shopping list\n- [ ] milk #home\n"))
    }
}
