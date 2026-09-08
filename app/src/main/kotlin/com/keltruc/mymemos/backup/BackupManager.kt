package com.keltruc.mymemos.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.keltruc.mymemos.MainActivity
import com.keltruc.mymemos.data.export.BackupCrypto
import com.keltruc.mymemos.database.MyMemosDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Encrypted backup of the local database, attachment files and settings. Credentials are
 * deliberately left out: they are bound to this device's Keystore and would not work
 * elsewhere, so a restored app asks you to sign in again.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MyMemosDatabase,
) {
    private val dbFile get() = context.getDatabasePath(MyMemosDatabase.NAME)
    private val attachmentsDir get() = File(context.filesDir, "attachments")
    private val settingsFile get() = File(context.filesDir, "datastore/settings.preferences_pb")

    suspend fun backup(target: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file so the copy is complete on its own.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        context.contentResolver.openOutputStream(target, "wt")!!.use { raw ->
            ZipOutputStream(BackupCrypto.encrypting(raw, password).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("db/${MyMemosDatabase.NAME}"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                attachmentsDir.listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry("attachments/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                if (settingsFile.exists()) {
                    zip.putNextEntry(ZipEntry("settings/settings.preferences_pb"))
                    settingsFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Unpacks into a staging dir first, so a wrong password never touches live data. */
    suspend fun restore(source: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var sawDb = false
        context.contentResolver.openInputStream(source)!!.use { raw ->
            ZipInputStream(BackupCrypto.decrypting(raw, password).buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val out = File(staging, entry.name)
                    // Prefix must end in a separator, otherwise "../restore-x/evil" would pass.
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) throw BackupCrypto.WrongPasswordOrCorrupt()
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    if (entry.name == "db/${MyMemosDatabase.NAME}") sawDb = true
                    entry = zip.nextEntry
                }
            }
        }
        if (!sawDb) throw BackupCrypto.WrongPasswordOrCorrupt()

        db.close()
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal")).forEach { it.delete() }
        File(staging, "db/${MyMemosDatabase.NAME}").copyTo(dbFile, overwrite = true)
        attachmentsDir.deleteRecursively()
        File(staging, "attachments").takeIf { it.exists() }?.copyRecursively(attachmentsDir, overwrite = true)
        File(staging, "settings/settings.preferences_pb").takeIf { it.exists() }?.let {
            settingsFile.parentFile?.mkdirs()
            it.copyTo(settingsFile, overwrite = true)
        }
        staging.deleteRecursively()
        restart()
    }

    /** Room cannot reopen a swapped file underneath itself; a clean process is the honest way. */
    private fun restart() {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}
