package com.keltruc.mymemos.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two things that are easy to break and expensive to discover in the wild, checked on every
 * target the database is built for:
 *  - the external-content `memos_fts` table and its MATCH query, including the triggers that
 *    keep it in step, which only work if the SQLite underneath was built with FTS4;
 *  - the auto-migrations, run against a database file shaped exactly as schema version 1
 *    left it, since that is what an install from the first release still has on disk.
 *
 * These live on the macOS target rather than in commonTest because Room's Android builder
 * needs a Context, so the same source cannot compile for both. Android is the target that
 * already had years of use behind it; this is the one that needed proving.
 */
class FtsAndMigrationTest {

    private val paths = mutableListOf<String>()

    private fun dbPath(name: String): String = tempFilePath(name).also { paths += it }

    private fun open(path: String): MyMemosDatabase =
        Room.databaseBuilder<MyMemosDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    @AfterTest
    fun cleanUp() {
        paths.forEach(::deleteFile)
        paths.clear()
    }

    private suspend fun seedAccount(db: MyMemosDatabase): Long = db.accountDao().insert(
        AccountEntity(
            id = 0, serverUrl = "https://memos.example", userResourceName = "users/1",
            username = "rory", displayName = "Rory", avatarUrl = "", role = "USER",
            authMethod = "PERSONAL_ACCESS_TOKEN", serverVersion = "0.30.0",
            lastSyncEpochMs = null, isActive = true,
        ),
    )

    private fun memo(accountId: Long, localId: String, content: String, tags: String = "") = MemoEntity(
        localId = localId, accountId = accountId, remoteName = null, creator = "users/1",
        content = content, visibility = "PRIVATE", state = "NORMAL", pinned = false,
        tagsJoined = tags, createTimeEpochMs = 1_700_000_000_000, updateTimeEpochMs = 1_700_000_000_000,
        snippet = content.take(40), hasTaskList = false, hasIncompleteTasks = false,
        hasLink = false, hasCode = false, locationPlaceholder = null, latitude = null,
        longitude = null, syncStatus = "SYNCED", baseUpdateTimeEpochMs = null,
    )

    @Test
    fun ftsSearchWorksOnThisTarget() = runTest {
        val db = open(dbPath("fts.db"))
        val accountId = seedAccount(db)
        db.memoDao().upsertAll(
            listOf(
                memo(accountId, "a", "Sourdough starter needs feeding on Thursday"),
                memo(accountId, "b", "Ring the plumber about the boiler"),
            ),
        )

        val hits = db.memoDao().search(accountId, "sourdough").first()
        assertEquals(1, hits.size, "FTS4 MATCH should find exactly the sourdough memo")
        assertEquals("a", hits.single().memo.localId)

        // External-content FTS only stays right if Room's sync triggers fire.
        db.memoDao().upsertAll(listOf(memo(accountId, "a", "Ring the plumber about the starter")))
        assertTrue(db.memoDao().search(accountId, "sourdough").first().isEmpty(), "stale FTS row after update")
        assertEquals(2, db.memoDao().search(accountId, "plumber").first().size)

        db.close()
    }

    @Test
    fun autoMigrationsRunFromVersionOne() = runTest {
        val path = dbPath("migrate.db")

        // Build the file exactly as schema version 1 left it, then put a row in it.
        BundledSQLiteDriver().open(path).use { connection ->
            V1_DDL.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 1")
            connection.execSQL(
                """
                INSERT INTO accounts (serverUrl, userResourceName, username, displayName, avatarUrl,
                    role, authMethod, serverVersion, lastSyncEpochMs, isActive)
                VALUES ('https://memos.example', 'users/1', 'rory', 'Rory', '', 'USER',
                    'PERSONAL_ACCESS_TOKEN', '0.30.0', NULL, 1)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO memos (localId, accountId, remoteName, creator, content, visibility, state,
                    pinned, tagsJoined, createTimeEpochMs, updateTimeEpochMs, snippet, hasTaskList,
                    hasIncompleteTasks, hasLink, hasCode, locationPlaceholder, latitude, longitude,
                    syncStatus, baseUpdateTimeEpochMs)
                VALUES ('old-1', 1, NULL, 'users/1', 'Written before the migration', 'PRIVATE', 'NORMAL',
                    0, '', 1700000000000, 1700000000000, 'Written before', 0, 0, 0, 0, NULL, NULL, NULL,
                    'SYNCED', NULL)
                """.trimIndent(),
            )
        }

        // Opening at version 3 must run both auto-migrations rather than throwing.
        val db = open(path)
        val survivor = db.memoDao().getByLocalId("old-1")
        assertEquals("Written before the migration", survivor?.content)
        assertEquals(null, survivor?.parent, "column added by the 1 to 2 migration")
        assertEquals(null, survivor?.colour, "column added by the 2 to 3 migration")

        // The tables the later versions added must exist and be usable.
        assertEquals(emptyList(), db.templateDao().observeAll().first())
        assertEquals(emptyList(), db.shortcutDao().observe(1).first())

        // And the FTS index has to survive a migration, not just a fresh database.
        assertEquals(1, db.memoDao().search(1, "migration").first().size)

        db.close()
    }
}
