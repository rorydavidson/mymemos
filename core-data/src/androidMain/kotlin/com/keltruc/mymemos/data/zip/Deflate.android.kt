package com.keltruc.mymemos.data.zip

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

internal actual fun deflateRaw(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}

internal actual fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        return inflateInto(inflater, data, expectedSize)
    } catch (e: java.util.zip.DataFormatException) {
        throw ZipFormatException("damaged deflate stream: ${e.message}")
    } finally {
        inflater.end()
    }
}

private fun inflateInto(inflater: Inflater, data: ByteArray, expectedSize: Int): ByteArray {
    run {
        inflater.setInput(data)
        val out = ByteArray(expectedSize)
        var at = 0
        while (at < expectedSize && !inflater.finished()) {
            val n = inflater.inflate(out, at, expectedSize - at)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            at += n
        }
        if (at != expectedSize) throw ZipFormatException("inflated ${at} bytes, expected $expectedSize")
        return out
    }
}
