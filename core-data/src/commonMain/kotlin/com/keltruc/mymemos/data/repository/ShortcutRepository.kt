package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.mapper.toEntity
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.transaction
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.ShortcutDao
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.Shortcut
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.dto.ShortcutDto
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Shortcuts are CEL filters the server evaluates, so running one needs a connection. The
 * memos it returns are upserted locally and then shown from Room like everything else.
 */
class ShortcutRepository constructor(
    private val db: MyMemosDatabase,
    private val shortcutDao: ShortcutDao,
    private val memoDao: MemoDao,
    private val registry: ApiClientRegistry,
    private val engine: SyncEngine,
    private val json: Json,
) {
    fun observe(accountId: Long): Flow<List<Shortcut>> = shortcutDao.observe(accountId).map { it.map { s -> s.toModel() } }

    /** Runs the filter on the server and returns the matching memos' server names, newest first. */
    suspend fun run(account: Account, shortcut: Shortcut): List<String> = wrap {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val names = mutableListOf<String>()
        var token: String? = null
        do {
            val page = api.listMemos(pageSize = 200, pageToken = token, filter = shortcut.filter.ifBlank { null })
            db.transaction {
                for (dto in page.memos) {
                    val existing = memoDao.getByRemoteName(account.id, dto.name)
                    if (existing != null && existing.syncStatus != SyncStatus.SYNCED.name) continue
                    val entity = dto.toEntity(account.id, existing?.localId, existing?.colour)
                    memoDao.upsert(entity)
                    engine.reconcileSocial(entity.localId, dto)
                }
            }
            names += page.memos.map { it.name }
            token = page.nextPageToken.ifEmpty { null }
        } while (token != null)
        names
    }

    fun observeMemos(accountId: Long, remoteNames: List<String>): Flow<List<Memo>> =
        memoDao.observeByRemoteNames(accountId, remoteNames).map { rows -> rows.map { it.toModel() } }

    suspend fun create(account: Account, title: String, filter: String) = wrap {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val created = api.createShortcut(account.userResourceName, ShortcutDto(title = title, filter = filter))
        shortcutDao.upsertAll(listOf(created.toEntity(account.id)))
    }

    suspend fun update(account: Account, shortcut: Shortcut) = wrap {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val updated = api.updateShortcut(shortcut.name, ShortcutDto(name = shortcut.name, title = shortcut.title, filter = shortcut.filter))
        shortcutDao.upsertAll(listOf(updated.toEntity(account.id)))
    }

    suspend fun delete(account: Account, shortcut: Shortcut) = wrap {
        val api = registry.api(account.serverUrl, account.userResourceName)
        api.deleteShortcut(shortcut.name)
        shortcutDao.replaceAll(account.id, api.listShortcuts(account.userResourceName).shortcuts.map { it.toEntity(account.id) })
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw ApiException.from(e, json)
    }
}
