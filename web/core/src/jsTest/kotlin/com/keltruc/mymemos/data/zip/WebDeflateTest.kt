package com.keltruc.mymemos.data.zip

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The web's own compressor and decompressor have to agree with each other for any input. */
class WebDeflateTest {
    @Test
    fun roundTripsTextNoiseAndEdges() {
        val inputs = listOf(
            ByteArray(0),
            "a".encodeToByteArray(),
            "abcabcabcabcabcabcabc".encodeToByteArray(),
            ("- [ ] buy milk @tomorrow\n".repeat(400) + "é ü 😀").encodeToByteArray(),
            Random(7).nextBytes(70_000),
            ByteArray(100_000) { (it % 251).toByte() },
        )
        for (bytes in inputs) assertContentEquals(bytes, inflateRaw(deflateRaw(bytes), bytes.size))
    }
}
