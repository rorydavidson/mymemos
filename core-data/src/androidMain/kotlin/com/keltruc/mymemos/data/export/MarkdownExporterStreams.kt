package com.keltruc.mymemos.data.export

import com.keltruc.mymemos.data.zip.ByteSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** The Android way in: a content resolver's OutputStream, written on the IO dispatcher. */
suspend fun MarkdownExporter.export(accountId: Long, out: OutputStream): Int = withContext(Dispatchers.IO) {
    val buffered = out.buffered()
    export(
        accountId,
        object : ByteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) = buffered.write(bytes, offset, length)
            override fun close() = buffered.flush()
        },
    )
}
