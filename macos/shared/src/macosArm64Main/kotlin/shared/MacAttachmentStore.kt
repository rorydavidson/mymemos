package shared

import com.keltruc.mymemos.data.attachments.AttachmentStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.uuid.Uuid

/**
 * Local copies of attachment files, in the app's own Application Support directory, named by
 * attachment localId. The same arrangement the Android side uses, so the sync engine's
 * expectations hold: it asks whether a file is there and for its bytes, nothing more.
 */
@OptIn(ExperimentalForeignApi::class)
internal class MacAttachmentStore(supportDirectory: String) : AttachmentStore {

    private val dir = "$supportDirectory/attachments".also {
        NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)
    }

    fun path(localId: String): String = "$dir/$localId"

    override suspend fun exists(localId: String): Boolean = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.fileExistsAtPath(path(localId))
    }

    override suspend fun readBytes(localId: String): ByteArray = withContext(Dispatchers.Default) {
        NSData.dataWithContentsOfFile(path(localId))?.toByteArray() ?: ByteArray(0)
    }

    override suspend fun delete(localId: String) {
        withContext(Dispatchers.Default) {
            NSFileManager.defaultManager.removeItemAtPath(path(localId), null)
            Unit
        }
    }

    /** Copies a file the user picked into the store, ready to be recorded against a memo. */
    suspend fun stage(sourcePath: String, filename: String, mimeType: String): AttachmentStore.Staged? =
        withContext(Dispatchers.Default) {
            val data = NSData.dataWithContentsOfFile(sourcePath) ?: return@withContext null
            val localId = Uuid.random().toString()
            if (!data.writeToFile(path(localId), true)) return@withContext null
            AttachmentStore.Staged(localId, filename, mimeType, data.length.toLong())
        }

    /** Keeps a downloaded copy so the attachment is there next time without the network. */
    suspend fun store(localId: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        bytes.toNSData().writeToFile(path(localId), true)
        Unit
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}
