package com.keltruc.mymemos.data.zip

/** CRC-32 as zip uses it (IEEE 802.3, reflected, 0xEDB88320). */
internal object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    fun of(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var c = 0xFFFFFFFF.toInt()
        for (i in offset until offset + length) {
            c = table[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
        }
        return c xor 0xFFFFFFFF.toInt()
    }
}
