package com.keltruc.mymemos.data.zip

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.MAX_WBITS
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.uBytefVar
import platform.zlib.z_stream

/**
 * zlib ships with every Apple system, and Kotlin/Native binds it. Negative window bits ask
 * for the raw stream a zip entry holds, without the zlib header and Adler-32 trailer.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun deflateRaw(data: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    val status = deflateInit2_(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -MAX_WBITS, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().toInt())
    if (status != Z_OK) throw ZipFormatException("deflateInit failed: $status")
    try {
        val bound = deflateBound(stream.ptr, data.size.toULong()).toInt()
        val out = ByteArray(maxOf(bound, 16))
        // An empty input still needs a valid empty deflate stream, so pin a one-byte array.
        val input = if (data.isEmpty()) ByteArray(1) else data
        input.usePinned { pinnedIn ->
            out.usePinned { pinnedOut ->
                stream.next_in = pinnedIn.addressOf(0).reinterpret<uBytefVar>()
                stream.avail_in = data.size.toUInt()
                stream.next_out = pinnedOut.addressOf(0).reinterpret<uBytefVar>()
                stream.avail_out = out.size.toUInt()
                val result = deflate(stream.ptr, Z_FINISH)
                if (result != Z_STREAM_END) throw ZipFormatException("deflate failed: $result")
            }
        }
        out.copyOf(stream.total_out.toInt())
    } finally {
        deflateEnd(stream.ptr)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray = memScoped {
    if (expectedSize == 0) return@memScoped ByteArray(0)
    val stream = alloc<z_stream>()
    val status = inflateInit2_(stream.ptr, -MAX_WBITS, ZLIB_VERSION, sizeOf<z_stream>().toInt())
    if (status != Z_OK) throw ZipFormatException("inflateInit failed: $status")
    try {
        val out = ByteArray(expectedSize)
        val input = if (data.isEmpty()) ByteArray(1) else data
        input.usePinned { pinnedIn ->
            out.usePinned { pinnedOut ->
                stream.next_in = pinnedIn.addressOf(0).reinterpret<uBytefVar>()
                stream.avail_in = data.size.toUInt()
                stream.next_out = pinnedOut.addressOf(0).reinterpret<uBytefVar>()
                stream.avail_out = out.size.toUInt()
                val result = inflate(stream.ptr, Z_FINISH)
                if (result != Z_STREAM_END || stream.total_out.toInt() != expectedSize) {
                    throw ZipFormatException("inflate failed: $result, ${stream.total_out} of $expectedSize bytes")
                }
            }
        }
        out
    } finally {
        inflateEnd(stream.ptr)
    }
}
