package shared

import kotlinx.cinterop.ExperimentalForeignApi
import com.keltruc.mymemos.data.zip.ByteSink
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.closeFile
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeData
import platform.Foundation.writeToFile

/** Plain file operations on Foundation, for export, import, backup and restore. */
@OptIn(ExperimentalForeignApi::class)
internal object AppleFiles {
    private val manager get() = NSFileManager.defaultManager

    fun exists(path: String): Boolean = manager.fileExistsAtPath(path)

    fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    fun write(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, true)

    fun delete(path: String) {
        manager.removeItemAtPath(path, null)
    }

    fun makeDirectory(path: String) {
        manager.createDirectoryAtPath(path, true, null, null)
    }

    /** File names directly inside [path], not recursing, without the hidden ones. */
    @Suppress("UNCHECKED_CAST")
    fun list(path: String): List<String> =
        (manager.contentsOfDirectoryAtPath(path, null) as? List<String>).orEmpty().filter { !it.startsWith(".") }

    fun modifiedEpochMs(path: String): Long? {
        val date = manager.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate ?: return null
        return (date.timeIntervalSince1970 * 1000).toLong()
    }

    /** A sink that appends to a file as it goes, so an export is never held whole in memory. */
    fun sink(path: String): ByteSink {
        manager.createFileAtPath(path, null, null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: error("cannot write $path")
        return object : ByteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                val chunk = if (offset == 0 && length == bytes.size) bytes else bytes.copyOfRange(offset, offset + length)
                handle.writeData(chunk.toNSData())
            }

            override fun close() = handle.closeFile()
        }
    }
}
