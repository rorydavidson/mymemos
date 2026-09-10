package shared

import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncScheduler
import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.model.Memo
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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

    /**
     * The timeline, grouped the way the phone groups it: days this week, then whole weeks,
     * then whole months. The grouping is shared code; only the words above each group are
     * written here.
     */
    suspend fun timeline(): List<TimelineSection> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val all = memos.observeTimeline(account.id, byModified = false).first()
        return group(all)
    }

    /** Offline full-text search, straight off the local FTS index. */
    suspend fun search(query: String): List<TimelineSection> {
        val account = db.accountDao().getActive() ?: return emptyList()
        if (query.isBlank()) return timeline()
        return group(memos.search(account.id, query).first())
    }

    suspend fun tags(): List<String> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.observeTags(account.id).first()
    }

    suspend fun memo(localId: String): MemoDetail? {
        val memo = memos.observeMemo(localId).first() ?: return null
        return MemoDetail(
            row = memo.toRow(),
            created = memo.createTime.friendly(),
            updated = memo.updateTime.friendly(),
            visibility = memo.visibility.name.lowercase().replaceFirstChar { it.uppercase() },
            wordCount = memo.displayContent.split(Regex("\\s+")).count { it.isNotBlank() },
        )
    }

    private fun group(all: List<Memo>): List<TimelineSection> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val labels = MacTimelineLabels()
        return TimelineGrouping.group(
            memos = all,
            today = today,
            zone = zone,
            firstDayOfWeek = MacTimelineLabels.firstDayOfWeek(),
        ).map { group ->
            TimelineSection(
                key = group.key,
                label = labels.label(group.bucket, today),
                memos = group.memos.map { it.toRow() },
            )
        }
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
        localId = localId,
        title = MemoTitle.of(displayContent) ?: firstLine(),
        body = if (isLocked) "" else displayContent,
        tags = tags,
        pinned = pinned,
        locked = isLocked,
        hasTasks = hasTaskList,
        hasOpenTasks = hasIncompleteTasks,
        attachmentCount = attachments.size,
        colourHex = colour?.hex?.toLong() ?: -1L,
        timeLabel = createTime.timeOfDay(),
        dateLabel = createTime.friendly(),
    )

    /** A memo with no heading still needs something to show in a list. */
    private fun Memo.firstLine(): String =
        displayContent.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80).orEmpty()

    private fun Instant.timeOfDay(): String {
        val local = toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.pad()}:${local.minute.pad()}"
    }

    private fun Instant.friendly(): String {
        val local = toLocalDateTime(TimeZone.currentSystemDefault())
        val month = local.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "${local.day} $month ${local.year} at ${local.hour.pad()}:${local.minute.pad()}"
    }

    private fun Int.pad() = toString().padStart(2, '0')
}

/** One header and the memos beneath it. */
data class TimelineSection(val key: String, val label: String, val memos: List<MemoRow>)

/** Everything the detail pane shows beyond the row itself. */
data class MemoDetail(
    val row: MemoRow,
    val created: String,
    val updated: String,
    val visibility: String,
    val wordCount: Int,
)

/** The memo password is not persisted on macOS yet; see MacStack. */
private class InMemoryPassword : com.keltruc.mymemos.data.crypto.RememberedPassword {
    override fun read(): CharArray? = null
    override fun write(password: CharArray) = Unit
    override fun forget() = Unit
    override fun isRemembered() = false
}

/** One row on screen. A plain class so Swift sees plain properties. */
data class MemoRow(
    val localId: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val pinned: Boolean,
    val locked: Boolean,
    val hasTasks: Boolean,
    val hasOpenTasks: Boolean,
    val attachmentCount: Int,
    /** The memo's tint as 0xRRGGBB, or -1 for none. */
    val colourHex: Long,
    val timeLabel: String,
    val dateLabel: String,
)
