package com.keltruc.mymemos.web.store

import com.keltruc.mymemos.web.store.IndexedDbPersistence.Companion.KV
import com.keltruc.mymemos.web.store.IndexedDbPersistence.Companion.MEMOS
import com.keltruc.mymemos.web.store.IndexedDbPersistence.Companion.OPS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The web client's database: everything held in memory for reading, written through to
 * [Persistence] for the next page load.
 *
 * [write] stands in for Room's transaction. Its block is not suspending, and JavaScript runs
 * one thing at a time, so nothing can observe a half-applied change in memory; what the
 * block did is then committed to IndexedDB as one transaction. Listeners hear about every
 * write, which is how the UI learns to re-read.
 */
class WebStore(private val persistence: Persistence) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flush = Mutex()
    private val listeners = mutableListOf<() -> Unit>()

    private val memos = LinkedHashMap<String, MemoRecord>()
    private val ops = LinkedHashMap<Long, OpRecord>()
    var accounts: List<AccountRecord> = emptyList()
        private set
    var activeAccountId: Int? = null
        private set
    var prefs: Prefs = Prefs()
        private set
    var templates: List<TemplateRecord> = emptyList()
        private set
    private val shortcuts = HashMap<Int, List<ShortcutRecord>>()
    private var nextOpId = 1L

    suspend fun load() {
        memos.clear()
        ops.clear()
        shortcuts.clear()
        for ((_, v) in persistence.loadAll(MEMOS)) runCatching { json.decodeFromString(MemoRecord.serializer(), v) }.onSuccess { memos[it.localId] = it }
        for ((_, v) in persistence.loadAll(OPS)) runCatching { json.decodeFromString(OpRecord.serializer(), v) }.onSuccess { ops[it.id] = it }
        nextOpId = (ops.keys.maxOrNull() ?: 0L) + 1
        val kv = persistence.loadAll(KV).toMap()
        accounts = kv[K_ACCOUNTS]?.let { runCatching { json.decodeFromString(ListSerializer(AccountRecord.serializer()), it) }.getOrNull() }.orEmpty()
        activeAccountId = kv[K_ACTIVE]?.toIntOrNull()?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id
        prefs = kv[K_PREFS]?.let { runCatching { json.decodeFromString(Prefs.serializer(), it) }.getOrNull() } ?: Prefs()
        templates = kv[K_TEMPLATES]?.let { runCatching { json.decodeFromString(ListSerializer(TemplateRecord.serializer()), it) }.getOrNull() }.orEmpty()
        for ((k, v) in kv) {
            if (!k.startsWith(K_SHORTCUTS)) continue
            val id = k.removePrefix(K_SHORTCUTS).toIntOrNull() ?: continue
            shortcuts[id] = runCatching { json.decodeFromString(ListSerializer(ShortcutRecord.serializer()), v) }.getOrNull().orEmpty()
        }
    }

    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    // ---- reads ---------------------------------------------------------------------------

    val activeAccount: AccountRecord? get() = accounts.firstOrNull { it.id == activeAccountId }

    fun account(id: Int): AccountRecord? = accounts.firstOrNull { it.id == id }

    fun memo(localId: String): MemoRecord? = memos[localId]

    fun memosFor(accountId: Int): List<MemoRecord> = memos.values.filter { it.accountId == accountId }

    fun memoByRemoteName(accountId: Int, remoteName: String): MemoRecord? =
        memos.values.firstOrNull { it.accountId == accountId && it.remoteName == remoteName }

    fun opsFor(accountId: Int): List<OpRecord> = ops.values.filter { it.accountId == accountId }.sortedBy { it.id }

    fun queued(accountId: Int): List<OpRecord> = opsFor(accountId).filter { !it.failed }

    fun opsForMemo(memoLocalId: String): List<OpRecord> = ops.values.filter { it.memoLocalId == memoLocalId }

    fun countForMemo(memoLocalId: String): Int = ops.values.count { it.memoLocalId == memoLocalId }

    fun latestOfType(memoLocalId: String, type: String): OpRecord? =
        ops.values.filter { it.memoLocalId == memoLocalId && it.type == type }.maxByOrNull { it.id }

    fun shortcutsFor(accountId: Int): List<ShortcutRecord> = shortcuts[accountId].orEmpty()

    // ---- blobs ---------------------------------------------------------------------------

    suspend fun readBlob(key: String): ByteArray? = persistence.readBlob(key)
    suspend fun writeBlob(key: String, bytes: ByteArray) = persistence.writeBlob(key, bytes)
    suspend fun deleteBlob(key: String) = runCatching { persistence.deleteBlob(key) }.let { }

    // ---- writes --------------------------------------------------------------------------

    /** Applies [block] in memory at once, then commits it to IndexedDB as one transaction. */
    suspend fun <R> write(block: Writer.() -> R): R {
        val writer = Writer()
        val result = writer.block()
        if (!writer.changes.isEmpty) flush.withLock { persistence.commit(writer.changes) }
        listeners.toList().forEach { runCatching { it() } }
        return result
    }

    /** Wipes this browser's copy of everything, for signing the last account out. */
    suspend fun clearAll() {
        persistence.clearAll()
        memos.clear(); ops.clear(); shortcuts.clear()
        accounts = emptyList(); activeAccountId = null; templates = emptyList()
        // Remembered servers and view preferences outlive the accounts, as in the apps.
        write { putPrefs(prefs.copy(folded = emptyList(), firedReminders = emptyList())) }
    }

    inner class Writer internal constructor() {
        internal val changes = Changes()

        fun upsertMemo(memo: MemoRecord) {
            memos[memo.localId] = memo
            changes.put(MEMOS, memo.localId, json.encodeToString(MemoRecord.serializer(), memo))
        }

        fun deleteMemo(localId: String) {
            memos.remove(localId)
            changes.delete(MEMOS, localId)
        }

        fun insertOp(op: OpRecord): OpRecord {
            val withId = op.copy(id = nextOpId++)
            ops[withId.id] = withId
            changes.put(OPS, withId.id.toString(), json.encodeToString(OpRecord.serializer(), withId))
            return withId
        }

        fun updateOp(op: OpRecord) {
            ops[op.id] = op
            changes.put(OPS, op.id.toString(), json.encodeToString(OpRecord.serializer(), op))
        }

        fun deleteOp(id: Long) {
            ops.remove(id)
            changes.delete(OPS, id.toString())
        }

        fun deleteOpsForMemo(memoLocalId: String, type: String? = null) {
            ops.values.filter { it.memoLocalId == memoLocalId && (type == null || it.type == type) }.map { it.id }.forEach(::deleteOp)
        }

        fun retryFailed(accountId: Int) {
            ops.values.filter { it.accountId == accountId && it.failed }.forEach { updateOp(it.copy(failed = false, attempts = 0, lastError = null)) }
        }

        fun putAccounts(list: List<AccountRecord>, active: Int?) {
            accounts = list
            activeAccountId = active
            changes.put(KV, K_ACCOUNTS, json.encodeToString(ListSerializer(AccountRecord.serializer()), list))
            if (active == null) changes.delete(KV, K_ACTIVE) else changes.put(KV, K_ACTIVE, active.toString())
        }

        fun updateAccount(account: AccountRecord) =
            putAccounts(accounts.map { if (it.id == account.id) account else it }, activeAccountId)

        fun putPrefs(next: Prefs) {
            prefs = next
            changes.put(KV, K_PREFS, json.encodeToString(Prefs.serializer(), next))
        }

        fun putTemplates(list: List<TemplateRecord>) {
            templates = list
            changes.put(KV, K_TEMPLATES, json.encodeToString(ListSerializer(TemplateRecord.serializer()), list))
        }

        fun putShortcuts(accountId: Int, list: List<ShortcutRecord>) {
            shortcuts[accountId] = list
            changes.put(KV, K_SHORTCUTS + accountId, json.encodeToString(ListSerializer(ShortcutRecord.serializer()), list))
        }

        fun deleteAllForAccount(accountId: Int) {
            memos.values.filter { it.accountId == accountId }.map { it.localId }.forEach(::deleteMemo)
            ops.values.filter { it.accountId == accountId }.map { it.id }.forEach(::deleteOp)
            shortcuts.remove(accountId)
            changes.delete(KV, K_SHORTCUTS + accountId)
        }
    }

    private companion object {
        const val K_ACCOUNTS = "accounts"
        const val K_ACTIVE = "activeAccount"
        const val K_PREFS = "prefs"
        const val K_TEMPLATES = "templates"
        const val K_SHORTCUTS = "shortcuts:"
    }
}
