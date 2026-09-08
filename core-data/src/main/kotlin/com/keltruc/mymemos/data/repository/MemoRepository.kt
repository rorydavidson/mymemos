package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.mapper.toEntity
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.dto.MemoDto
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1: read path only. The timeline observes Room; [refresh] pulls from the server
 * and reconciles. Writes and the outbox arrive with the sync engine in phase 2.
 */
@Singleton
class MemoRepository @Inject constructor(
    private val db: MyMemosDatabase,
    private val memoDao: MemoDao,
    private val attachmentDao: AttachmentDao,
    private val accountDao: AccountDao,
    private val registry: ApiClientRegistry,
    private val json: Json,
) {
    fun observeTimeline(accountId: Long, state: MemoState = MemoState.NORMAL): Flow<List<Memo>> =
        memoDao.observeTimeline(accountId, state.name).map { rows -> rows.map { it.toModel() } }

    fun observeMemo(localId: String): Flow<Memo?> =
        memoDao.observeByLocalId(localId).map { it?.toModel() }

    fun search(accountId: Long, query: String): Flow<List<Memo>> {
        // FTS prefix match on each term; quote to stop MATCH syntax leaking in.
        val ftsQuery = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        return memoDao.search(accountId, ftsQuery).map { rows -> rows.map { it.toModel() } }
    }

    fun observeTags(accountId: Long): Flow<List<String>> =
        memoDao.observeTagStrings(accountId).map { joined ->
            joined.flatMap { it.split(MemoEntity.TAG_SEPARATOR) }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }

    /**
     * Pull memos changed since the last sync. First run fetches everything, later runs
     * fetch a delta. Full fetches also drop synced rows the server no longer has.
     */
    suspend fun refresh(account: Account, force: Boolean = false) {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val entity = accountDao.getById(account.id) ?: return
        val since = entity.lastSyncEpochMs?.takeUnless { force }
        val startedAt = System.currentTimeMillis()

        val fetched = mutableListOf<MemoDto>()
        for (state in listOf("NORMAL", "ARCHIVED")) {
            var token: String? = null
            do {
                val page = wrap {
                    api.listMemos(
                        pageSize = 200,
                        pageToken = token,
                        state = state,
                        orderBy = "update_time desc",
                        filter = since?.let { "updated_ts > ${Instant.ofEpochMilli(it).epochSecond}" },
                    )
                }
                fetched += page.memos
                token = page.nextPageToken.ifEmpty { null }
            } while (token != null)
        }

        db.withTransaction {
            for (dto in fetched) {
                val existing = memoDao.getByRemoteName(account.id, dto.name)
                // Local pending edits win until the sync engine merges them (phase 2).
                if (existing != null && existing.syncStatus != "SYNCED") continue
                val memoEntity = dto.toEntity(account.id, existing?.localId)
                memoDao.upsert(memoEntity)
                attachmentDao.deleteForMemo(memoEntity.localId)
                attachmentDao.upsertAll(dto.attachments.map { it.toEntity(memoEntity.localId) })
            }
            if (since == null) {
                memoDao.deleteSyncedNotIn(account.id, fetched.map { it.name }.ifEmpty { listOf("") })
            }
            accountDao.setLastSync(account.id, startedAt)
        }
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw ApiException.from(e, json)
    }
}
