package com.keltruc.mymemos.data.zip

/**
 * The browser compresses only asynchronously (CompressionStream), and ZipWriter is
 * synchronous, so this is a small compressor of its own: greedy LZ77 over a 32 KiB window
 * with one fixed-Huffman block. Not zlib's ratio, but text shrinks well and ZipWriter stores
 * anything it fails to shrink.
 */
internal actual fun deflateRaw(data: ByteArray): ByteArray = Deflater(data).run()

/** Damaged data surfaces as [ZipFormatException], as it does on the other platforms. */
internal actual fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray =
    try {
        Inflater(data, expectedSize).run()
    } catch (e: IllegalStateException) {
        throw ZipFormatException("inflate failed: ${e.message}")
    } catch (e: IndexOutOfBoundsException) {
        throw ZipFormatException("inflate failed: ${e.message}")
    }

/**
 * RFC 1951 inflate, enough to read the zips other tools write: stored, fixed and dynamic
 * Huffman blocks. Output is capped at [expectedSize] so a crafted archive cannot balloon.
 */
private class Inflater(private val input: ByteArray, private val expectedSize: Int) {
    private var pos = 0
    private var bitBuf = 0
    private var bitCount = 0
    private val out = ByteArray(expectedSize)
    private var outPos = 0

    fun run(): ByteArray {
        do {
            val last = bits(1) == 1
            when (bits(2)) {
                0 -> stored()
                1 -> block(FIXED_LIT, FIXED_DIST)
                2 -> dynamic()
                else -> error("bad deflate block type")
            }
        } while (!last)
        check(outPos == expectedSize) { "inflated size $outPos, expected $expectedSize" }
        return out
    }

    private fun bits(n: Int): Int {
        while (bitCount < n) {
            check(pos < input.size) { "deflate data ends early" }
            bitBuf = bitBuf or ((input[pos++].toInt() and 0xff) shl bitCount)
            bitCount += 8
        }
        val v = bitBuf and ((1 shl n) - 1)
        bitBuf = bitBuf ushr n
        bitCount -= n
        return v
    }

    private fun emit(b: Byte) {
        check(outPos < expectedSize) { "deflate data larger than recorded" }
        out[outPos++] = b
    }

    private fun stored() {
        bitBuf = 0
        bitCount = 0
        check(pos + 4 <= input.size) { "deflate data ends early" }
        val len = (input[pos].toInt() and 0xff) or ((input[pos + 1].toInt() and 0xff) shl 8)
        pos += 4
        check(pos + len <= input.size) { "deflate data ends early" }
        repeat(len) { emit(input[pos++]) }
    }

    private fun dynamic() {
        val hlit = bits(5) + 257
        val hdist = bits(5) + 1
        val hclen = bits(4) + 4
        val codeLengths = IntArray(19)
        for (i in 0 until hclen) codeLengths[CL_ORDER[i]] = bits(3)
        val clTable = Huffman(codeLengths)
        val lengths = IntArray(hlit + hdist)
        var i = 0
        while (i < lengths.size) {
            when (val sym = decode(clTable)) {
                in 0..15 -> lengths[i++] = sym
                16 -> { check(i > 0); val prev = lengths[i - 1]; repeat(3 + bits(2)) { lengths[i++] = prev } }
                17 -> repeat(3 + bits(3)) { lengths[i++] = 0 }
                18 -> repeat(11 + bits(7)) { lengths[i++] = 0 }
                else -> error("bad code length symbol")
            }
        }
        block(Huffman(lengths.copyOfRange(0, hlit)), Huffman(lengths.copyOfRange(hlit, lengths.size)))
    }

    private fun block(lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = decode(lit)
            when {
                sym < 256 -> emit(sym.toByte())
                sym == 256 -> return
                else -> {
                    val li = sym - 257
                    check(li < LEN_BASE.size) { "bad length symbol" }
                    val len = LEN_BASE[li] + bits(LEN_EXTRA[li])
                    val di = decode(dist)
                    check(di < DIST_BASE.size) { "bad distance symbol" }
                    val d = DIST_BASE[di] + bits(DIST_EXTRA[di])
                    check(d <= outPos) { "distance too far back" }
                    repeat(len) { emit(out[outPos - d]) }
                }
            }
        }
    }

    private fun decode(h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..15) {
            code = code or bits(1)
            val count = h.counts[len]
            if (code - first < count) return h.symbols[index + code - first]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        error("bad Huffman code")
    }

    /** Canonical Huffman table as counts per length and symbols in code order. */
    private class Huffman(lengths: IntArray) {
        val counts = IntArray(16)
        val symbols: IntArray

        init {
            for (l in lengths) counts[l]++
            counts[0] = 0
            val offsets = IntArray(16)
            for (l in 1 until 15) offsets[l + 1] = offsets[l] + counts[l]
            symbols = IntArray(lengths.size)
            for ((s, l) in lengths.withIndex()) if (l != 0) symbols[offsets[l]++] = s
        }
    }

    companion object {
        private val CL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

        private val FIXED_LIT = Huffman(IntArray(288) { when (it) { in 0..143 -> 8; in 144..255 -> 9; in 256..279 -> 7; else -> 8 } })
        private val FIXED_DIST = Huffman(IntArray(30) { 5 })
    }
}

private class Deflater(private val input: ByteArray) {
    private val out = ArrayList<Byte>(input.size / 2 + 16)
    private var bitBuf = 0
    private var bitCount = 0

    fun run(): ByteArray {
        bits(1, 1) // final block
        bits(1, 2) // fixed Huffman
        val head = IntArray(1 shl HASH_BITS) { -1 }
        val prev = IntArray(input.size)
        var i = 0
        while (i < input.size) {
            var bestLen = 0
            var bestDist = 0
            if (i + MIN_MATCH <= input.size) {
                val h = hash(i)
                var candidate = head[h]
                var chain = 0
                while (candidate >= 0 && i - candidate <= WINDOW && chain++ < MAX_CHAIN) {
                    var len = 0
                    val limit = minOf(MAX_MATCH, input.size - i)
                    while (len < limit && input[candidate + len] == input[i + len]) len++
                    if (len > bestLen) { bestLen = len; bestDist = i - candidate }
                    if (len == limit) break
                    candidate = prev[candidate]
                }
            }
            if (bestLen >= MIN_MATCH) {
                length(bestLen)
                distance(bestDist)
                repeat(bestLen) { insert(i++, head, prev) }
            } else {
                literal(input[i].toInt() and 0xff)
                insert(i++, head, prev)
            }
        }
        literal(256)
        if (bitCount > 0) out.add(bitBuf.toByte())
        return out.toByteArray()
    }

    private fun hash(i: Int): Int =
        (((input[i].toInt() and 0xff) shl 10) xor ((input[i + 1].toInt() and 0xff) shl 5) xor (input[i + 2].toInt() and 0xff)) and ((1 shl HASH_BITS) - 1)

    private fun insert(i: Int, head: IntArray, prev: IntArray) {
        if (i + MIN_MATCH > input.size) return
        val h = hash(i)
        prev[i] = head[h]
        head[h] = i
    }

    private fun bits(value: Int, n: Int) {
        bitBuf = bitBuf or (value shl bitCount)
        bitCount += n
        while (bitCount >= 8) {
            out.add(bitBuf.toByte())
            bitBuf = bitBuf ushr 8
            bitCount -= 8
        }
    }

    /** Huffman codes go out most significant bit first, unlike everything else. */
    private fun code(code: Int, n: Int) {
        var reversed = 0
        for (b in 0 until n) reversed = reversed or (((code ushr b) and 1) shl (n - 1 - b))
        bits(reversed, n)
    }

    private fun literal(sym: Int) = when {
        sym <= 143 -> code(0x30 + sym, 8)
        sym <= 255 -> code(0x190 + sym - 144, 9)
        sym <= 279 -> code(sym - 256, 7)
        else -> code(0xC0 + sym - 280, 8)
    }

    private fun length(len: Int) {
        var idx = LEN_BASE.size - 1
        while (LEN_BASE[idx] > len) idx--
        literal(257 + idx)
        if (LEN_EXTRA[idx] > 0) bits(len - LEN_BASE[idx], LEN_EXTRA[idx])
    }

    private fun distance(dist: Int) {
        var idx = DIST_BASE.size - 1
        while (DIST_BASE[idx] > dist) idx--
        code(idx, 5)
        if (DIST_EXTRA[idx] > 0) bits(dist - DIST_BASE[idx], DIST_EXTRA[idx])
    }

    private companion object {
        const val MIN_MATCH = 3
        const val MAX_MATCH = 258
        const val WINDOW = 32_768
        const val HASH_BITS = 15
        const val MAX_CHAIN = 64
        val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    }
}
