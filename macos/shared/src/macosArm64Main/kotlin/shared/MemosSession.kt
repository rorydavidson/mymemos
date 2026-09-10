package shared

import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncScheduler
import com.keltruc.mymemos.model.Memo
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * What the macOS app talks to. Deliberately small and concrete: Swift gets suspend functions
 * as `async` for free, but only off a plain class, and it cannot extend a generic Kotlin type,
 * so everything crossing over is a plain class or a list of them.
 *
 * Behind it is the real data layer: the same sync engine, outbox, three-way merge and Room
 * database the Android app uses, none of it reimplemented.
 */
class MemosSession {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val db = MacStack.database
    private val registry = MacStack.registry

    private val engine = SyncEngine(
        db, db.accountDao(), db.memoDao(), db.attachmentDao(), db.pendingOpDao(),
        db.relationDao(), db.reactionDao(), db.shortcutDao(), registry, MacStack.attachments,
        MacStack.widgets, MacStack.preferences, json,
    )

    private val scheduler = SyncScheduler(engine, db.accountDao(), MacStack.background)

    private val memos = MemoRepository(
        db, db.memoDao(), db.attachmentDao(), db.pendingOpDao(), db.relationDao(),
        db.reactionDao(), registry, MacStack.attachments, engine, scheduler,
        MacStack.preferences, MacStack.widgets, json,
    )

    private val accounts = AccountRepository(
        db.accountDao(), db.memoDao(), db.attachmentDao(), MacStack.attachments,
        com.keltruc.mymemos.data.crypto.PasswordSession(InMemoryPassword()),
        registry, json,
    )

    var serverVersion: String = ""
        private set
    var displayName: String = ""
        private set

    /** Confirms the address really is a Memos server before anyone types a password at it. */
    suspend fun probe(serverUrl: String): String {
        val version = registry.anonymousApi(serverUrl).getInstanceProfile().version
        serverVersion = version
        return version
    }

    suspend fun signIn(serverUrl: String, username: String, password: String) {
        val account = accounts.signInWithPassword(serverUrl, username, password)
        displayName = account.displayName.ifEmpty { account.username }
    }

    /** Pulls from the server into the local database, exactly as the phone does. */
    suspend fun sync(): String {
        val account = db.accountDao().getActive() ?: return "not signed in"
        return when (engine.sync(account.id, fullPull = true)) {
            SyncEngine.Outcome.Success -> "ok"
            is SyncEngine.Outcome.AuthFailed -> "sign in again"
            is SyncEngine.Outcome.Retry -> "will retry"
        }
    }

    /** Reads from the local database, which is what the timeline shows. */
    suspend fun memos(): List<MemoRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.observeTimeline(account.id, byModified = false).first().map { it.toRow() }
    }

    suspend fun signedInAs(): String? = db.accountDao().getActive()?.let {
        serverVersion = it.serverVersion
        displayName = it.displayName.ifEmpty { it.username }
        displayName
    }

    /**
     * Whether credentials can actually be stored. The Keychain refuses an application it
     * cannot identify, so an unsigned build can sign in happily and then forget everything on
     * quit. Better to say so than to look broken later.
     */
    fun credentialStoreAvailable(): Boolean {
        val probe = "probe"
        Keychain.write(probe, "ok")
        val read = Keychain.read(probe)
        Keychain.delete(probe)
        return read == "ok"
    }

    private fun Memo.toRow(): MemoRow = MemoRow(
        name = localId,
        content = content,
        pinned = pinned,
        locked = isLocked,
        createdLabel = label(),
    )

    private fun Memo.label(): String {
        val local = createTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = local.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "${local.day} $month ${local.year}, ${local.hour.pad()}:${local.minute.pad()}"
    }

    private fun Int.pad() = toString().padStart(2, '0')
}

/** The memo password is not persisted on macOS yet; see MacStack. */
private class InMemoryPassword : com.keltruc.mymemos.data.crypto.RememberedPassword {
    override fun read(): CharArray? = null
    override fun write(password: CharArray) = Unit
    override fun forget() = Unit
    override fun isRemembered() = false
}

/** One row on screen. A plain class so Swift sees plain properties. */
data class MemoRow(
    val name: String,
    val content: String,
    val pinned: Boolean,
    val locked: Boolean,
    val createdLabel: String,
)
