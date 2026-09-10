package shared

import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.crypto.RememberedPassword
import com.keltruc.mymemos.data.account.AvatarSource
import com.keltruc.mymemos.data.mapper.remoteUrl
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncScheduler
import com.keltruc.mymemos.data.text.DueDateParser
import com.keltruc.mymemos.data.text.MarkdownContinuation
import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.data.text.TaskLine
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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

    private val passwords = PasswordSession(KeychainPassword())

    private val accounts = AccountRepository(
        db.accountDao(), db.memoDao(), db.attachmentDao(), MacStack.attachments,
        passwords,
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
        val byModified = MacStack.preferences.settings.first().sortByModified
        return group(memos.observeTimeline(account.id, byModified = byModified).first())
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
            placeName = memo.location?.placeholder,
            latitude = memo.location?.latitude ?: 0.0,
            longitude = memo.location?.longitude ?: 0.0,
            hasPlace = memo.location != null,
            bodyBelowTitle = if (memo.isLocked) "" else MemoTitle.withoutTitleLine(memo.displayContent),
        )
    }

    // MARK: account

    /** Revokes this device's token on the server and forgets the account locally. */
    suspend fun signOut() {
        val account = db.accountDao().getActive() ?: return
        accounts.activeAccount.first()?.let { accounts.signOut(it) }
        MacStack.attachments.delete(account.userResourceName)
        passwords.forget()
    }

    /** Order the timeline by when memos were last changed rather than when they were written. */
    suspend fun sortByModified(): Boolean = MacStack.preferences.settings.first().sortByModified

    suspend fun setSortByModified(enabled: Boolean) = MacStack.preferences.setSortByModified(enabled)

    // MARK: tasks

    /**
     * Every unticked task across the account's memos, grouped by the memo it lives in and
     * ordered by when it is due, with undated ones last. The parsing is shared with the phone,
     * so a completion can never mean a different day than it shows.
     */
    suspend fun openTasks(): List<TaskGroup> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val labels = MacDueDateLabels()

        return memos.observeMemosWithOpenTasks(account.id).first()
            .filter { !it.isLocked }
            .map { memo ->
                val tasks = TaskLine.openTasks(memo.displayContent).map { open ->
                    val due = DueDateParser.parse(open.text, today)?.date
                    TaskRow(
                        memoLocalId = memo.localId,
                        lineIndex = open.lineIndex,
                        text = open.text,
                        dueLabel = due?.let { labels.label(it, today) },
                        overdue = due != null && due < today,
                        dueSort = due?.toEpochDays()?.toLong() ?: Long.MAX_VALUE,
                    )
                }
                TaskGroup(
                    memoLocalId = memo.localId,
                    memoTitle = MemoTitle.of(memo.displayContent) ?: memo.firstLine(),
                    tasks = tasks,
                    earliestDue = tasks.minOfOrNull { it.dueSort } ?: Long.MAX_VALUE,
                )
            }
            .filter { it.tasks.isNotEmpty() }
            .sortedWith(compareBy({ it.earliestDue }, { it.memoTitle }))
    }

    /** Ticks a task off, which edits the memo's own text. */
    suspend fun completeTask(memoLocalId: String, lineIndex: Int) {
        val memo = memos.observeMemoOnce(memoLocalId) ?: return
        TaskLine.toggle(memo.content, lineIndex, checked = true)?.let {
            memos.updateContent(memoLocalId, it)
        }
    }

    // MARK: review

    /** How many days in a row have something written, counting back from today. */
    suspend fun streak(): Int {
        val account = db.accountDao().getActive() ?: return 0
        val zone = TimeZone.currentSystemDefault()
        val days = memos.observeActiveDays(account.id, zone).first()
        var day = Clock.System.todayIn(zone)
        if (day !in days) day = day.minus(1, DateTimeUnit.DAY)
        var count = 0
        while (day in days) {
            count++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /** The last year of writing, as one entry per day that has any. */
    suspend fun activeDays(): List<String> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.observeActiveDays(account.id).first().map { it.toString() }.sorted()
    }

    /**
     * Memos written on this day in earlier months and years, which is what "On this day" shows.
     */
    suspend fun onThisDay(): List<Throwback> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val days = memos.observeActiveDays(account.id, zone).first()
            .filter { it != today && it.day == today.day && it < today }
            .sortedDescending()

        return days.flatMap { day ->
            val years = today.year - day.year
            val months = years * 12 + (today.month.ordinal - day.month.ordinal)
            memos.observeCreatedOn(account.id, day, zone).first().map { memo ->
                Throwback(
                    row = memo.toRow(),
                    whenLabel = if (years >= 1) {
                        "$years year${if (years == 1) "" else "s"} ago"
                    } else {
                        "$months month${if (months == 1) "" else "s"} ago"
                    },
                )
            }
        }
    }

    // MARK: the account's avatar

    /**
     * The account's avatar, or null when there is none to show.
     *
     * A `data:` avatar is decoded here. One on the account's own server is fetched with the
     * credential, like any other file. One pointing somewhere else is fetched without it: an
     * avatar URL is set on the server and would otherwise be a way to collect bearer tokens
     * from whoever renders it.
     */
    suspend fun avatarBytes(): ByteArray? {
        val account = db.accountDao().getActive() ?: return null
        return when (val source = AvatarSource.of(account.avatarUrl, account.serverUrl)) {
            is AvatarSource.None -> null

            is AvatarSource.Bytes -> source.bytes

            is AvatarSource.Url -> {
                // Avatars are full-size uploads rather than thumbnails, so this one is well
                // over a megabyte for a circle drawn at 26 points. Fetch it once and keep it.
                MacStack.cachedAvatar(source.url)?.let { return it }

                val client = if (source.sameOrigin) {
                    registry.client(account.serverUrl, account.userResourceName)
                } else {
                    anonymous
                }
                runCatching { client.get(source.url).readRawBytes() }
                    .onFailure { println("W/Avatar: could not fetch ${source.url}: $it") }
                    .getOrNull()
                    ?.also { MacStack.cacheAvatar(source.url, it) }
            }
        }
    }

    /** No credential attached, for anything that is not the account's own server. */
    private val anonymous by lazy { HttpClient() }

    // MARK: places

    /**
     * Whether map tiles may be drawn at all. Off by default and deliberately so: rendering a
     * memo's location asks openstreetmap.org for the tiles around it, which tells them roughly
     * where you are. With it off nothing is drawn and nothing leaves the device.
     */
    suspend fun mapTilesEnabled(): Boolean = MacStack.preferences.settings.first().mapTiles

    suspend fun setMapTiles(enabled: Boolean) = MacStack.preferences.setMapTiles(enabled)

    /** Every memo that carries a location, newest first, for the journey map. */
    suspend fun locatedMemos(): List<PlacedMemo> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.memosWithLocation(account.id)
            .mapNotNull { memo ->
                val place = memo.location ?: return@mapNotNull null
                PlacedMemo(
                    row = memo.toRow(),
                    placeName = place.placeholder,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    dayKey = memo.createTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                )
            }
    }

    // MARK: attachments

    /** What a memo carries, for the strip under it. */
    suspend fun attachments(localId: String): List<AttachmentRow> {
        // observeMemoOnce reads the memo row alone; only the observing query joins the
        // attachments, and without them this quietly returns an empty strip.
        val memo = memos.observeMemo(localId).first() ?: return emptyList()
        return memo.attachments.map {
            AttachmentRow(
                localId = it.localId,
                filename = it.filename,
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes,
                isImage = it.isImage,
                uploaded = it.remoteName != null,
            )
        }
    }

    /**
     * The bytes of an attachment, from the local copy when there is one and from the server
     * otherwise, keeping what it downloads so the next look needs no network.
     *
     * Going through the account's own authenticated client matters: the file endpoint wants
     * the bearer token, and sending it anywhere other than the account's own server would
     * hand the credential to whoever controls an attachment's link.
     */
    suspend fun attachmentBytes(memoLocalId: String, attachmentLocalId: String): ByteArray? {
        val store = MacStack.attachments
        if (store.exists(attachmentLocalId)) return store.readBytes(attachmentLocalId)

        val account = db.accountDao().getActive() ?: return null
        val memo = memos.observeMemo(memoLocalId).first() ?: return null
        val attachment = memo.attachments.firstOrNull { it.localId == attachmentLocalId } ?: return null
        val url = attachment.remoteUrl(account.serverUrl) ?: return null

        val client = registry.client(account.serverUrl, account.userResourceName)
        val bytes = runCatching { client.get(url).readRawBytes() }.getOrNull() ?: return null
        store.store(attachmentLocalId, bytes)
        return bytes
    }

    /** Records a file the user picked. The upload rides the outbox like every other change. */
    suspend fun attach(memoLocalId: String, sourcePath: String, filename: String, mimeType: String): Boolean {
        val staged = MacStack.attachments.stage(sourcePath, filename, mimeType) ?: return false
        memos.addAttachment(memoLocalId, staged)
        return true
    }

    // MARK: locked memos

    /** True once a memo password is available for this process. */
    val hasPassword: Boolean get() = passwords.current() != null

    val passwordRemembered: Boolean get() = passwords.isRemembered

    /**
     * Holds the memo password for this process, and on this device if asked. One password
     * covers every locked memo, which is what the phone does.
     */
    fun usePassword(password: String, remember: Boolean) {
        passwords.set(password.toCharArray(), remember)
    }

    fun forgetPassword() = passwords.forget()

    /**
     * The readable text of a locked memo, without changing what is stored. The memo stays
     * encrypted on the server and on disk; this is only for showing it.
     */
    suspend fun reveal(localId: String): Reveal {
        val memo = memos.observeMemoOnce(localId) ?: return Reveal(null, needsPassword = false, wrongPassword = false)
        if (!memo.isLocked) return Reveal(memo.displayContent, needsPassword = false, wrongPassword = false)
        val password = passwords.current() ?: return Reveal(null, needsPassword = true, wrongPassword = false)
        return try {
            Reveal(memos.decrypt(memo, password), needsPassword = false, wrongPassword = false)
        } catch (e: MemoCipher.WrongPassword) {
            Reveal(null, needsPassword = true, wrongPassword = true)
        }
    }

    /** Removes the encryption for good, so the server sees the text again. */
    suspend fun unlockForGood(localId: String): Boolean {
        val password = passwords.current() ?: return false
        return try {
            memos.unlock(localId, password)
            true
        } catch (e: MemoCipher.WrongPassword) {
            false
        }
    }

    /** Locks a memo that is currently in the clear. */
    suspend fun lock(localId: String): Boolean {
        val password = passwords.current() ?: return false
        memos.lock(localId, password)
        return true
    }

    // MARK: writing

    /** Saves a new memo locally and pushes it; the outbox handles the network being away. */
    suspend fun create(content: String, visibility: String, pinned: Boolean): String? {
        val account = db.accountDao().getActive() ?: return null
        return memos.create(account.id, content, Visibility.valueOf(visibility), pinned)
    }

    suspend fun updateContent(localId: String, content: String) {
        memos.updateContent(localId, content)
    }

    suspend fun setPinned(localId: String, pinned: Boolean) {
        memos.setPinned(localId, pinned)
    }

    suspend fun setVisibility(localId: String, visibility: String) {
        memos.setVisibility(localId, Visibility.valueOf(visibility))
    }

    /** True when the delete can still be taken back, which is what the phone's Undo relies on. */
    suspend fun delete(localId: String): Boolean = memos.delete(localId)

    /**
     * Continues a list or task after Return, the same way the phone does.
     *
     * Called with the field as it was and as it now is, because the shared logic runs after the
     * newline has already landed rather than instead of it.
     */
    fun continueAfterReturn(
        beforeText: String,
        beforeCursor: Int,
        afterText: String,
        afterCursor: Int,
    ): ListContinuation? =
        MarkdownContinuation.continueAfterReturn(beforeText, beforeCursor, afterText, afterCursor)?.let {
            ListContinuation(text = it.text, cursor = it.cursor)
        }

    /** The raw Markdown, which is what the editor opens. */
    suspend fun rawContent(localId: String): String? = memos.observeMemoOnce(localId)?.displayContent

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
    val placeName: String?,
    val latitude: Double,
    val longitude: Double,
    val hasPlace: Boolean,
    /** The text without the line the title came from, so a view showing both does not repeat it. */
    val bodyBelowTitle: String,
)

/**
 * "Remember on this device" for the memo password, in the Keychain.
 *
 * The password is what protects text the server never sees, so it goes where the operating
 * system guards it rather than anywhere this app controls.
 */
private class KeychainPassword : RememberedPassword {
    override fun read(): CharArray? = Keychain.read(KEY)?.toCharArray()
    override fun write(password: CharArray) = Keychain.write(KEY, password.concatToString())
    override fun forget() = Keychain.delete(KEY)
    override fun isRemembered(): Boolean = Keychain.read(KEY) != null

    private companion object {
        const val KEY = "memo_password"
    }
}

/** An unticked task, and where in its memo it lives. */
data class TaskRow(
    val memoLocalId: String,
    val lineIndex: Int,
    val text: String,
    val dueLabel: String?,
    val overdue: Boolean,
    val dueSort: Long,
)

/** The open tasks of one memo. */
data class TaskGroup(
    val memoLocalId: String,
    val memoTitle: String,
    val tasks: List<TaskRow>,
    val earliestDue: Long,
)

/** A memo that knows where it was written. */
data class PlacedMemo(
    val row: MemoRow,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    /** ISO date, so a day's memos can be grouped into one journey. */
    val dayKey: String,
)

/** A memo resurfaced from the same date in an earlier month or year. */
data class Throwback(val row: MemoRow, val whenLabel: String)

/** One attachment, as the strip under a memo shows it. */
data class AttachmentRow(
    val localId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val uploaded: Boolean,
)

/** The text and where the cursor lands after Return inside a list. */
data class ListContinuation(val text: String, val cursor: Int)

/** What a locked memo looks like when asked to show itself. */
data class Reveal(val text: String?, val needsPassword: Boolean, val wrongPassword: Boolean)

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
