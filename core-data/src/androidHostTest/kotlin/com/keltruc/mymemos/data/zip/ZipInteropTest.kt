package com.keltruc.mymemos.data.zip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The codec against the standard library, in both directions, since that is what Android writes and reads. */
class ZipInteropTest {

    @Test
    fun javaReadsWhatTheCodecWrites() {
        val sink = ByteArraySink()
        val zip = ZipWriter(sink)
        val text = "hello ".repeat(500).encodeToByteArray()
        zip.entry("memos/one.md", text)
        zip.entry("attachments/id-photo.jpg", ByteArray(300) { it.toByte() })
        zip.close()

        val seen = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(sink.toByteArray())).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                seen[entry.name] = input.readBytes()
                entry = input.nextEntry
            }
        }
        assertEquals(setOf("memos/one.md", "attachments/id-photo.jpg"), seen.keys)
        assertArrayEquals(text, seen["memos/one.md"])
    }

    @Test
    fun codecReadsWhatJavaWrites() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("folder/"))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("folder/note.md"))
            zip.write("# From java.util.zip\n".encodeToByteArray())
            zip.closeEntry()
        }
        val entries = ZipReader.entries(out.toByteArray())
        assertEquals(listOf("folder/", "folder/note.md"), entries.map { it.name })
        assertEquals(true, entries[0].isDirectory)
        assertEquals("# From java.util.zip\n", entries[1].bytes.decodeToString())
    }
}
