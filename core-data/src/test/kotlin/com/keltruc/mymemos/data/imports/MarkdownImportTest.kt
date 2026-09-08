package com.keltruc.mymemos.data.imports

import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.data.export.MarkdownExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MarkdownImportTest {

    @Test
    fun `a file with no front matter is all body`() {
        val parsed = MarkdownImport.parse("# Shopping\n- milk")
        assertEquals("# Shopping\n- milk", parsed.body)
        assertNull(parsed.created)
        assertEquals(emptyList<String>(), parsed.tags)
    }

    @Test
    fun `front matter is read and removed from the body`() {
        val parsed = MarkdownImport.parse(
            """
            ---
            created: 2026-09-08T09:00:00Z
            updated: 2026-09-08T10:00:00Z
            visibility: private
            pinned: true
            tags: [home, work]
            ---

            # Shopping
            - milk
            """.trimIndent(),
        )
        assertEquals("# Shopping\n- milk", parsed.body)
        assertEquals(Instant.parse("2026-09-08T09:00:00Z"), parsed.created)
        assertEquals(Instant.parse("2026-09-08T10:00:00Z"), parsed.updated)
        assertEquals(listOf("home", "work"), parsed.tags)
        assertTrue(parsed.pinned)
        assertEquals("PRIVATE", parsed.visibility)
    }

    @Test
    fun `tags may be written as a yaml list`() {
        val parsed = MarkdownImport.parse("---\ntags:\n  - home\n  - work\n---\nbody")
        assertEquals(listOf("home", "work"), parsed.tags)
    }

    @Test
    fun `other tools' date keys and formats are accepted`() {
        val zone = ZoneId.systemDefault()
        assertEquals(
            LocalDate.of(2026, 9, 8).atStartOfDay(zone).toInstant(),
            MarkdownImport.parse("---\ndate: 2026-09-08\n---\nx").created,
        )
        assertEquals(
            LocalDate.of(2026, 9, 8).atTime(14, 30).atZone(zone).toInstant(),
            MarkdownImport.parse("---\ncreated_at: 2026-09-08 14:30\n---\nx").created,
        )
        assertEquals(
            Instant.parse("2026-09-08T09:00:00Z"),
            MarkdownImport.parse("---\nmodified: 2026-09-08T09:00:00Z\n---\nx").updated,
        )
    }

    @Test
    fun `front matter that makes no sense is treated as absent, not fatal`() {
        val parsed = MarkdownImport.parse("---\ncreated: last Tuesday\n: : :\n---\nbody")
        assertNull(parsed.created)
        assertEquals("body", parsed.body)
    }

    @Test
    fun `folders in a zip become one nested tag`() {
        assertEquals("work/projects", MarkdownImport.tagForPath("work/projects/notes.md"))
        assertEquals("work", MarkdownImport.tagForPath("work/notes.md"))
        assertNull(MarkdownImport.tagForPath("notes.md"))
    }

    @Test
    fun `folder names are made safe to use as a tag`() {
        assertEquals("my-work/big-ideas", MarkdownImport.tagForPath("my work/big ideas/notes.md"))
        // A traversing entry name must not climb out into a tag of its own.
        assertEquals("work", MarkdownImport.tagForPath("../work/notes.md"))
        assertNull(MarkdownImport.tagForPath("../../notes.md"))
    }

    @Test
    fun `tags are appended only when the body does not already carry them`() {
        assertEquals("body\n\n#work", MarkdownImport.withTags("body", listOf("work")))
        assertEquals("body #work", MarkdownImport.withTags("body #work", listOf("work")))
        assertEquals("#work", MarkdownImport.withTags("", listOf("work")))
        // "#work" must not count as already carrying "#work/projects".
        assertEquals("body #work\n\n#work/projects", MarkdownImport.withTags("body #work", listOf("work/projects")))
    }

    @Test
    fun `the memos id is read so a re-import can recognise its own export`() {
        assertEquals("memos/abc", MarkdownImport.parse("---\nmemos_id: memos/abc\n---\nbody").remoteName)
        assertNull(MarkdownImport.parse("---\ncreated: 2026-09-08\n---\nbody").remoteName)
        assertNull(MarkdownImport.parse("no front matter").remoteName)
    }

    @Test
    fun `an exported memo comes back with its dates and tags intact`() {
        val memo = Memo(
            localId = "l", accountId = 1, remoteName = "memos/abc", creator = null,
            content = "# Shopping list\n- [ ] milk #home", visibility = Visibility.PRIVATE, state = MemoState.NORMAL,
            pinned = true, tags = listOf("home"), createTime = Instant.parse("2026-09-08T09:00:00Z"),
            updateTime = Instant.parse("2026-09-08T10:00:00Z"), snippet = "", hasTaskList = true,
            hasIncompleteTasks = true, hasLink = false, hasCode = false, location = null,
            attachments = emptyList(), syncStatus = SyncStatus.SYNCED,
        )
        val parsed = MarkdownImport.parse(MarkdownExporter.render(memo))
        assertEquals(memo.content, parsed.body)
        assertEquals(memo.createTime, parsed.created)
        assertEquals(memo.updateTime, parsed.updated)
        assertEquals(listOf("home"), parsed.tags)
        assertTrue(parsed.pinned)
        // The id is what stops a second import of the same file making a second memo.
        assertEquals(memo.remoteName, parsed.remoteName)
    }
}
