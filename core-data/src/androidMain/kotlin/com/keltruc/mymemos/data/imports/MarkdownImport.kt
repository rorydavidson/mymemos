package com.keltruc.mymemos.data.imports

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/**
 * Reads one Markdown file into the pieces a memo is made of. Deliberately forgiving: files come
 * from Obsidian, from this app's own export, or from whatever the user had lying about, so a
 * front matter block it cannot make sense of is treated as absent rather than as an error.
 */
object MarkdownImport {

    data class Parsed(
        val body: String,
        val created: Instant?,
        val updated: Instant?,
        val tags: List<String>,
        val pinned: Boolean,
        val visibility: String?,
        /** The `memos_id` this app's own export writes, so a re-import can recognise its own work. */
        val remoteName: String?,
    )

    private val fence = Regex("\\A---\\r?\\n(.*?)\\r?\\n---[ \\t]*\\r?\\n?", RegexOption.DOT_MATCHES_ALL)
    private val entry = Regex("^([A-Za-z_][A-Za-z0-9_-]*):[ \\t]*(.*)$")
    private val listItem = Regex("^[ \\t]*-[ \\t]+(.*)$")
    // The app's own tag syntax; anything outside it would not survive a round trip through a memo.
    private val illegalInTag = Regex("[^\\p{L}\\p{N}_/-]+")

    /** Keys different tools use for the same two dates. */
    private val createdKeys = setOf("created", "created_at", "date", "createtime", "create_time")
    private val updatedKeys = setOf("updated", "updated_at", "modified", "last_modified", "updatetime", "update_time")

    fun parse(fileContent: String): Parsed {
        val match = fence.find(fileContent)
            ?: return Parsed(fileContent.trim(), null, null, emptyList(), pinned = false, visibility = null, remoteName = null)
        val fields = readFields(match.groupValues[1])
        return Parsed(
            body = fileContent.substring(match.range.last + 1).trim(),
            created = fields.single(createdKeys)?.let(::parseInstant),
            updated = fields.single(updatedKeys)?.let(::parseInstant),
            tags = (fields.list("tags") + fields.single(setOf("tags")).orEmpty().splitInline()).mapNotNull(::sanitiseTag).distinct(),
            pinned = fields.single(setOf("pinned"))?.lowercase() == "true",
            visibility = fields.single(setOf("visibility"))?.uppercase()?.takeIf { it in setOf("PRIVATE", "PROTECTED", "PUBLIC") },
            remoteName = fields.single(setOf("memos_id", "memos-id"))?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * The tag a file's folders inside a zip should carry: "work/projects/notes.md" becomes
     * "work/projects". Files at the root of the zip get none.
     */
    fun tagForPath(entryPath: String): String? {
        val folders = entryPath.replace('\\', '/').split('/').dropLast(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .mapNotNull(::sanitiseTag)
        return folders.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    /** Appends any tags not already written in the body, so they reach the server as real tags. */
    fun withTags(body: String, tags: List<String>): String {
        val missing = tags.filter { tag -> !Regex("(?<![\\w/])#${Regex.escape(tag)}(?![\\w/])").containsMatchIn(body) }
        if (missing.isEmpty()) return body
        val line = missing.joinToString(" ") { "#$it" }
        return if (body.isBlank()) line else body.trimEnd() + "\n\n" + line
    }

    private fun sanitiseTag(raw: String): String? =
        raw.trim().trim('"', '\'').replace(' ', '-').replace(illegalInTag, "").trim('/', '-')
            .takeIf { it.isNotEmpty() }

    private fun String.splitInline(): List<String> =
        removeSurrounding("[", "]").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private class Fields(val singles: Map<String, String>, val lists: Map<String, List<String>>) {
        fun single(keys: Set<String>): String? = keys.firstNotNullOfOrNull { singles[it]?.takeIf(String::isNotEmpty) }
        fun list(key: String): List<String> = lists[key].orEmpty()
    }

    /**
     * Enough of YAML for front matter and no more: "key: value" lines, plus the dash-list form
     * that follows a key with nothing after the colon. Indented blocks and anchors are ignored.
     */
    private fun readFields(block: String): Fields {
        val singles = mutableMapOf<String, String>()
        val lists = mutableMapOf<String, MutableList<String>>()
        var openList: String? = null
        for (line in block.lines()) {
            val item = listItem.matchEntire(line)
            if (item != null && openList != null) {
                lists.getOrPut(openList!!) { mutableListOf() } += item.groupValues[1].trim()
                continue
            }
            val field = entry.matchEntire(line.trim()) ?: continue
            val (key, value) = field.destructured
            val name = key.lowercase()
            // A comment after the value, as MarkdownExporter writes on the location line.
            singles[name] = value.substringBefore(" #").trim()
            openList = if (singles[name].isNullOrEmpty()) name else null
        }
        return Fields(singles, lists)
    }

    /**
     * Front matter comes from whatever wrote it, so this tries the shapes seen in the wild in
     * turn. The parsing stays on `java.time`, which is lenient in ways worth keeping; only the
     * result crosses into the model's `kotlin.time.Instant`.
     */
    private fun parseInstant(raw: String): Instant? = parseJavaInstant(raw)?.let {
        Instant.fromEpochMilliseconds(it.toEpochMilli())
    }

    private fun parseJavaInstant(raw: String): java.time.Instant? {
        val text = raw.trim().trim('"', '\'')
        runCatching { return java.time.Instant.parse(text) }
        runCatching { return java.time.OffsetDateTime.parse(text).toInstant() }
        runCatching { return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant() }
        runCatching { return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant() }
        runCatching { return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.systemDefault()).toInstant() }
        runCatching { return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant() }
        return null
    }
}
