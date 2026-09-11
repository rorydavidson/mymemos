package com.keltruc.mymemos.data.zip

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipTest {

    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val sink = ByteArraySink()
        val zip = ZipWriter(sink)
        for ((name, bytes) in files) zip.entry(name, bytes, modifiedEpochMs = 1_757_600_000_000)
        zip.close()
        return sink.toByteArray()
    }

    @Test
    fun roundTripsTextAndBinary() {
        val text = "# Hello\n\n".repeat(200).encodeToByteArray()
        val binary = ByteArray(5000) { (it * 31 + 7).toByte() }
        val entries = ZipReader.entries(archive("memos/a.md" to text, "attachments/x.bin" to binary))
        assertEquals(listOf("memos/a.md", "attachments/x.bin"), entries.map { it.name })
        assertContentEquals(text, entries[0].bytes)
        assertContentEquals(binary, entries[1].bytes)
        assertEquals(1_757_600_000_000 / 2000 * 2000, entries[0].modifiedEpochMs)
    }

    @Test
    fun compressesTextAndLeavesNoiseAlone() {
        val text = "the same line again and again\n".repeat(100).encodeToByteArray()
        val small = archive("t.md" to text)
        assertTrue(small.size < text.size / 2, "text should deflate well: ${small.size} of ${text.size}")
        // xorshift, so there is genuinely nothing for deflate to find.
        var x = 0x9E3779B9u
        val noise = ByteArray(4000) {
            x = x xor (x shl 13); x = x xor (x shr 17); x = x xor (x shl 5)
            (x shr 24).toByte()
        }
        val stored = archive("n.bin" to noise)
        assertTrue(stored.size >= noise.size, "noise should be stored, not grown by deflate")
        assertContentEquals(noise, ZipReader.entries(stored)[0].bytes)
    }

    @Test
    fun emptyFileAndUnicodeName() {
        val entries = ZipReader.entries(archive("notes/café ☕.md" to ByteArray(0)))
        assertEquals("notes/café ☕.md", entries[0].name)
        assertEquals(0, entries[0].bytes.size)
    }

    @Test
    fun recognisesZipBytes() {
        assertTrue(ZipReader.looksLikeZip(archive("a" to "b".encodeToByteArray())))
        assertTrue(!ZipReader.looksLikeZip("# not a zip".encodeToByteArray()))
    }

    @Test
    fun refusesGarbage() {
        assertFailsWith<ZipFormatException> { ZipReader.entries("nothing like a zip at all, but long enough".encodeToByteArray()) }
    }

    @Test
    fun catchesCorruption() {
        val bytes = archive("a.md" to "some text that will be deflated ".repeat(20).encodeToByteArray())
        bytes[40] = (bytes[40].toInt() xor 0x55).toByte()
        assertFailsWith<ZipFormatException> { ZipReader.entries(bytes)[0].bytes }
    }

    @Test
    fun crcMatchesTheStandardVector() {
        assertEquals(0xCBF43926.toInt(), Crc32.of("123456789".encodeToByteArray()))
    }
}
