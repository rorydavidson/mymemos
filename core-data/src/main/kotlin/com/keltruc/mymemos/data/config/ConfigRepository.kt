package com.keltruc.mymemos.data.config

import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.model.AppConfig
import com.keltruc.mymemos.model.TagStyle
import com.keltruc.mymemos.model.Visibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the hidden config memo. Writes go through the normal outbox, so they
 * sync and merge like any memo; the JSON block is rewritten whole, last writer wins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ConfigRepository @Inject constructor(
    private val memoDao: MemoDao,
    private val memoRepository: MemoRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: com.keltruc.mymemos.data.repository.AccountSettingsRepository,
) {
    private val writes = Mutex()

    val config: Flow<AppConfig> = accountRepository.activeAccount.filterNotNull().flatMapLatest { acc ->
        memoDao.observeConfigMemo(acc.id, acc.userResourceName).map { it?.let { m -> ConfigCodec.decode(m.content) } ?: AppConfig() }
    }

    suspend fun current(): AppConfig = config.first()

    suspend fun setTagStyle(tag: String, style: TagStyle) {
        update { it.copy(tagStyles = if (style.emoji == null && style.colour == null) it.tagStyles - tag else it.tagStyles + (tag to style)) }
        // Mirror the colour to the server's own per-tag setting so the web UI matches. Best effort.
        val account = accountRepository.activeAccountOrNull() ?: return
        runCatching { settingsRepository.setTagColour(account, tag, style.colour) }
    }

    /** Applies [change] to the latest config and stores it, creating the memo on first use. */
    suspend fun update(change: (AppConfig) -> AppConfig) = writes.withLock {
        val account = accountRepository.activeAccount.filterNotNull().first()
        val existing = memoDao.observeConfigMemo(account.id, account.userResourceName).first()
        val next = change(existing?.let { ConfigCodec.decode(it.content) } ?: AppConfig())
        val text = ConfigCodec.encode(next)
        if (existing == null) {
            memoRepository.create(account.id, text, Visibility.PRIVATE)
        } else if (existing.content != text) {
            memoRepository.updateContent(existing.localId, text)
        }
    }
}
