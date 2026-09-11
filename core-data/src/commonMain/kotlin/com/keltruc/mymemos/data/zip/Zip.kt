package com.keltruc.mymemos.data.zip

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Enough of the zip format for an export and a backup, and no more: entries stored or
 * deflated, UTF-8 names, no encryption, no zip64. Java has this in the standard library and
 * Apple platforms do not, so it is written once here and only the compressor is platform
 * code. Anything this writes is read by Obsidian, Finder and java.util.zip; anything those
 * write in the same subset is read by [ZipReader].
 */

/** Where bytes go. An OutputStream on Android, an NSFileHandle on Apple platforms. */
interface ByteSink {
    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)
    fun close()
}

/** A sink that keeps everything, for tests and for small archives. */
class ByteArraySink : ByteSink {
    private var buffer = ByteArray(1024)
    private var size = 0

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (size + length > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + length))
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    override fun close() = Unit

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

/** Raw deflate, the "nowrap" form zip entries use: no zlib header, no trailing Adler-32. */
internal expect fun deflateRaw(data: ByteArray): ByteArray

/** The reverse. [expectedSize] is the size the archive recorded, which bounds the output. */
internal expect fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray

/**
 * Writes entries as they are given and the central directory on [close]. Each entry's bytes
 * are held once for the CRC and the compressor; nothing else is buffered, so a large export
 * is a series of small memories rather than one big one.
 */
class ZipWriter(private val sink: ByteSink) {
    private class Written(val name: ByteArray, val method: Int, val crc: Int, val compressed: Int, val size: Int, val time: Int, val date: Int, val offset: Long)

    private val entries = mutableListOf<Written>()
    private var position = 0L
    private var closed = false

    /**
     * Adds one file. Deflate is used when it helps, which it does not for a photo, and text is
     * always worth compressing.
     */
    fun entry(name: String, bytes: ByteArray, modifiedEpochMs: Long? = null) {
        check(!closed) { "zip already closed" }
        val nameBytes = name.encodeToByteArray()
        val deflated = deflateRaw(bytes)
        val useDeflate = deflated.size < bytes.size
        val payload = if (useDeflate) deflated else bytes
        val (time, date) = dosDateTime(modifiedEpochMs)
        val written = Written(nameBytes, if (useDeflate) 8 else 0, Crc32.of(bytes), payload.size, bytes.size, time, date, position)

        val header = ByteBuilder(30 + nameBytes.size)
        header.int(0x04034b50)
        header.short(20)
        header.short(FLAG_UTF8)
        header.short(written.method)
        header.short(time)
        header.short(date)
        header.int(written.crc)
        header.int(written.compressed)
        header.int(written.size)
        header.short(nameBytes.size)
        header.short(0)
        header.bytes(nameBytes)
        emit(header.toByteArray())
        emit(payload)
        entries += written
    }

    fun close() {
        if (closed) return
        closed = true
        val start = position
        for (e in entries) {
            val h = ByteBuilder(46 + e.name.size)
            h.int(0x02014b50)
            h.short(20)
            h.short(20)
            h.short(FLAG_UTF8)
            h.short(e.method)
            h.short(e.time)
            h.short(e.date)
            h.int(e.crc)
            h.int(e.compressed)
            h.int(e.size)
            h.short(e.name.size)
            h.short(0)
            h.short(0)
            h.short(0)
            h.short(0)
            h.int(0)
            h.int(e.offset.toInt())
            h.bytes(e.name)
            emit(h.toByteArray())
        }
        val end = ByteBuilder(22)
        end.int(0x06054b50)
        end.short(0)
        end.short(0)
        end.short(entries.size)
        end.short(entries.size)
        end.int((position - start).toInt())
        end.int(start.toInt())
        end.short(0)
        emit(end.toByteArray())
        sink.close()
    }

    private fun emit(bytes: ByteArray) {
        sink.write(bytes)
        position += bytes.size
    }

    private companion object {
        const val FLAG_UTF8 = 0x0800
    }
}

/** One file inside an archive. [bytes] inflates on demand, so listing an archive costs nothing. */
class ZipEntry internal constructor(
    val name: String,
    val modifiedEpochMs: Long?,
    val size: Int,
    private val method: Int,
    private val crc: Int,
    private val payload: ByteArray,
) {
    val isDirectory: Boolean get() = name.endsWith("/")

    /** Throws [ZipFormatException] when the bytes do not match the checksum the archive recorded. */
    val bytes: ByteArray
        get() {
            val out = when (method) {
                0 -> payload
                8 -> inflateRaw(payload, size)
                else -> throw ZipFormatException("unsupported compression method $method")
            }
            if (Crc32.of(out) != crc) throw ZipFormatException("checksum mismatch in $name")
            return out
        }
}

class ZipFormatException(message: String) : Exception(message)

object ZipReader {
    /** Whether these are the first bytes of a zip, which the extension and the MIME type both lie about. */
    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    /** Every entry, in central-directory order. Directories are included and say so. */
    fun entries(archive: ByteArray): List<ZipEntry> {
        val eocd = findEndOfCentralDirectory(archive)
        val count = archive.u16(eocd + 10)
        var cursor = archive.u32(eocd + 16).toInt()
        val found = ArrayList<ZipEntry>(count)
        repeat(count) {
            if (archive.u32(cursor) != 0x02014b50L) throw ZipFormatException("bad central directory entry")
            val flags = archive.u16(cursor + 8)
            if (flags and 1 != 0) throw ZipFormatException("encrypted entries are not supported")
            val method = archive.u16(cursor + 10)
            val time = archive.u16(cursor + 12)
            val date = archive.u16(cursor + 14)
            val crc = archive.u32(cursor + 16).toInt()
            val compressed = archive.u32(cursor + 20)
            val size = archive.u32(cursor + 24)
            val nameLength = archive.u16(cursor + 28)
            val extraLength = archive.u16(cursor + 30)
            val commentLength = archive.u16(cursor + 32)
            val localOffset = archive.u32(cursor + 42).toInt()
            if (compressed > Int.MAX_VALUE || size > Int.MAX_VALUE) throw ZipFormatException("zip64 archives are not supported")
            val name = archive.decodeToString(cursor + 46, cursor + 46 + nameLength)
            cursor += 46 + nameLength + extraLength + commentLength

            if (archive.u32(localOffset) != 0x04034b50L) throw ZipFormatException("bad local header for $name")
            val localName = archive.u16(localOffset + 26)
            val localExtra = archive.u16(localOffset + 28)
            val dataStart = localOffset + 30 + localName + localExtra
            val dataEnd = dataStart + compressed.toInt()
            if (dataEnd > archive.size) throw ZipFormatException("truncated archive at $name")
            found += ZipEntry(name, epochMs(time, date), size.toInt(), method, crc, archive.copyOfRange(dataStart, dataEnd))
        }
        return found
    }

    private fun findEndOfCentralDirectory(archive: ByteArray): Int {
        if (archive.size < 22) throw ZipFormatException("too short to be a zip")
        // The comment can push the record back by up to 65535 bytes.
        val lowest = maxOf(0, archive.size - 22 - 65535)
        var i = archive.size - 22
        while (i >= lowest) {
            if (archive.u32(i) == 0x06054b50L) return i
            i--
        }
        throw ZipFormatException("no end-of-central-directory record")
    }

    private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
}

// MARK: DOS timestamps, which zip still uses: two seconds of resolution, local time, from 1980.

private fun dosDateTime(epochMs: Long?): Pair<Int, Int> {
    val instant = Instant.fromEpochMilliseconds(epochMs ?: 0)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    if (local.year < 1980 || epochMs == null) return 0 to ((1 shl 5) or 1) // 1980-01-01 00:00, zip's epoch
    val time = (local.hour shl 11) or (local.minute shl 5) or (local.second / 2)
    val date = ((local.year - 1980) shl 9) or (local.month.ordinal + 1 shl 5) or local.day
    return time to date
}

private fun epochMs(time: Int, date: Int): Long? {
    val year = 1980 + (date ushr 9)
    val month = (date ushr 5) and 0xF
    val day = date and 0x1F
    if (month !in 1..12 || day !in 1..31) return null
    return runCatching {
        LocalDateTime(year, month, day, time ushr 11, (time ushr 5) and 0x3F, (time and 0x1F) * 2)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull()
}

private class ByteBuilder(capacity: Int) {
    private val bytes = ByteArray(capacity)
    private var at = 0
    fun short(v: Int) { bytes[at++] = v.toByte(); bytes[at++] = (v ushr 8).toByte() }
    fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
    fun bytes(v: ByteArray) { v.copyInto(bytes, at); at += v.size }
    fun toByteArray(): ByteArray = bytes.copyOf(at)
}
