package shared

import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.crypto.RememberedPassword
import com.keltruc.mymemos.data.account.AvatarSource
import com.keltruc.mymemos.data.mapper.remoteUrl
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.TemplateRepository
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
import com.keltruc.mymemos.data.notify.Digest
import com.keltruc.mymemos.data.notify.Schedule
import com.keltruc.mymemos.model.Reminder
import com.keltruc.mymemos.model.RecurringTemplate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.repository.ShareRepository
import com.keltruc.mymemos.data.repository.ShortcutRepository
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.TagStyle
import com.keltruc.mymemos.model.UserRole
import com.keltruc.mymemos.model.InstanceGeneral
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

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
    private val db = AppleStack.database
    private val registry = AppleStack.registry

    private val engine = SyncEngine(
        db, db.accountDao(), db.memoDao(), db.attachmentDao(), db.pendingOpDao(),
        db.relationDao(), db.reactionDao(), db.shortcutDao(), registry, AppleStack.attachments,
        AppleStack.widgets, AppleStack.preferences, json,
    )

    private val scheduler = SyncScheduler(engine, db.accountDao(), AppleStack.background)

    private val memos = MemoRepository(
        db, db.memoDao(), db.attachmentDao(), db.pendingOpDao(), db.relationDao(),
        db.reactionDao(), registry, AppleStack.attachments, engine, scheduler,
        AppleStack.preferences, AppleStack.widgets, json,
    )

    private val passwords = PasswordSession(KeychainPassword())

    private val accounts = AccountRepository(
        db.accountDao(), db.memoDao(), db.attachmentDao(), AppleStack.attachments,
        passwords,
        registry, json,
    )

    private val settings = AccountSettingsRepository(registry, db.accountDao(), json)

    /** Reminders, recurring templates and the digest switch all live in the config memo. */
    private val config = ConfigRepository(db.memoDao(), memos, accounts, settings)

    private val templates = TemplateRepository(db.templateDao())

    private val shares = ShareRepository(registry, json)

    private val shortcuts = ShortcutRepository(db, db.shortcutDao(), db.memoDao(), registry, engine, json)

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
        val byModified = AppleStack.preferences.settings.first().sortByModified
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
            bodyLineOffset = if (memo.isLocked) 0 else memo.displayContent.lines().size - MemoTitle.withoutTitleLine(memo.displayContent).lines().size,
            archived = memo.state == MemoState.ARCHIVED,
            onServer = memo.remoteName != null,
        )
    }

    // MARK: account

    /** Revokes this device's token on the server and forgets the account locally. */
    suspend fun signOut() {
        val account = db.accountDao().getActive() ?: return
        accounts.activeAccount.first()?.let { accounts.signOut(it) }
        AppleStack.attachments.delete(account.userResourceName)
        passwords.forget()
    }

    /** Order the timeline by when memos were last changed rather than when they were written. */
    suspend fun sortByModified(): Boolean = AppleStack.preferences.settings.first().sortByModified

    suspend fun setSortByModified(enabled: Boolean) = AppleStack.preferences.setSortByModified(enabled)

    /**
     * One line per memo instead of a card, for scanning a long timeline rather than reading
     * it. Local to this Mac: it is a view preference, not something to follow you about.
     */
    suspend fun compactList(): Boolean = AppleStack.preferences.settings.first().compactList

    suspend fun setCompactList(enabled: Boolean) = AppleStack.preferences.setCompactList(enabled)

    // MARK: tasks

    /**
     * Every unticked task across the account's memos, grouped by the memo it lives in and
     * ordered by when it is due, with undated ones last. The parsing is shared with the phone,
     * so a completion can never mean a different day than it shows.
     */
    suspend fun openTasks(): List<TaskGroup> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val labels = AppleDueDateLabels()

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
                AppleStack.cachedAvatar(source.url)?.let { return it }

                val client = if (source.sameOrigin) {
                    registry.client(account.serverUrl, account.userResourceName)
                } else {
                    anonymous
                }
                runCatching { client.get(source.url).readRawBytes() }
                    .onFailure { println("W/Avatar: could not fetch ${source.url}: $it") }
                    .getOrNull()
                    ?.also { AppleStack.cacheAvatar(source.url, it) }
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
    suspend fun mapTilesEnabled(): Boolean = AppleStack.preferences.settings.first().mapTiles

    suspend fun setMapTiles(enabled: Boolean) = AppleStack.preferences.setMapTiles(enabled)

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
        val store = AppleStack.attachments
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
        val staged = AppleStack.attachments.stage(sourcePath, filename, mimeType) ?: return false
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
        val labels = AppleTimelineLabels()
        return TimelineGrouping.group(
            memos = all,
            today = today,
            zone = zone,
            firstDayOfWeek = AppleTimelineLabels.firstDayOfWeek(),
        ).map { group ->
            TimelineSection(
                key = group.key,
                label = labels.label(group.bucket, today),
                memos = group.memos.map { it.toRow() },
            )
        }
    }

    // MARK: templates

    /**
     * The saved templates, seeding the three defaults the first time anyone looks. Seeding on
     * read rather than at startup keeps it off the launch path, and the check is a COUNT.
     */
    suspend fun templates(): List<TemplateRow> {
        templates.seedDefaultsIfEmpty()
        return templates.templates.first().map { TemplateRow(it.id, it.title, it.body) }
    }

    suspend fun saveTemplate(id: Long, title: String, body: String) =
        templates.save(id.takeIf { it > 0 }, title, body)

    suspend fun deleteTemplate(id: Long) = templates.delete(id)

    /** A template's body with today's date and time written into it, ready to edit. */
    fun expandTemplate(body: String): String =
        TemplateRepository.expand(body, AppleTemplateValues())

    // MARK: reminders and recurring templates
    //
    // Both live in the config memo, so they sync: a reminder set on the phone arrives here on
    // the next pull, and one set here reaches the phone the same way.

    suspend fun reminders(): List<ReminderRow> {
        val zone = TimeZone.currentSystemDefault()
        return config.current().reminders
            .sortedBy { it.atEpochMs }
            .map { reminder ->
                val memo = memos.observeMemoOnce(localIdFor(reminder.memoRemoteName))
                ReminderRow(
                    id = reminder.id,
                    memoRemoteName = reminder.memoRemoteName,
                    memoLocalId = memo?.localId.orEmpty(),
                    memoTitle = memo?.let { m ->
                        if (m.isLocked) "Locked memo" else MemoTitle.of(m.displayContent) ?: m.firstLine()
                    }.orEmpty(),
                    note = reminder.note,
                    atEpochMs = reminder.atEpochMs,
                    whenLabel = Instant.fromEpochMilliseconds(reminder.atEpochMs).friendlyWithTime(zone),
                    overdue = reminder.atEpochMs <= Clock.System.now().toEpochMilliseconds(),
                )
            }
    }

    /**
     * Sets a one-off reminder on a memo. Returns false for a memo the server has not seen yet:
     * reminders travel by the memo's server name, and a memo still in the outbox has none.
     */
    suspend fun addReminder(memoLocalId: String, atEpochMs: Long, note: String): Boolean {
        val remoteName = memos.observeMemoOnce(memoLocalId)?.remoteName ?: return false
        val reminder = Reminder(
            id = Uuid.random().toString(),
            memoRemoteName = remoteName,
            atEpochMs = atEpochMs,
            note = note,
        )
        config.update { it.copy(reminders = it.reminders + reminder) }
        return true
    }

    suspend fun removeReminder(id: String) =
        config.update { c -> c.copy(reminders = c.reminders.filterNot { it.id == id }) }

    suspend fun recurring(): List<RecurringRow> {
        val enabledByTitle = config.current().recurring.associateBy { it.templateTitle }
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        return templates().map { template ->
            val existing = enabledByTitle[template.title]
            val hour = existing?.hour?.toInt() ?: 8
            val minute = existing?.minute?.toInt() ?: 0
            RecurringRow(
                templateId = template.id,
                templateTitle = template.title,
                hour = hour,
                minute = minute,
                enabled = existing?.enabled == true,
                nextLabel = if (existing?.enabled == true) {
                    Schedule.nextDaily(hour, minute, now, zone).friendlyWithTime(zone)
                } else {
                    ""
                },
            )
        }
    }

    suspend fun setRecurring(templateTitle: String, hour: Int, minute: Int, enabled: Boolean) {
        config.update { c ->
            val rest = c.recurring.filterNot { it.templateTitle == templateTitle }
            c.copy(recurring = rest + RecurringTemplate(templateTitle, hour, minute, enabled))
        }
    }

    suspend fun weeklyDigest(): Boolean = config.current().weeklyDigest

    suspend fun setWeeklyDigest(enabled: Boolean) = config.update { it.copy(weeklyDigest = enabled) }

    /**
     * Runs any recurring template whose time has passed without one being written today, and
     * returns the titles it created.
     *
     * This is the honest shape of the feature on a Mac: the system will deliver a notification
     * scheduled earlier even with the app closed, but nothing can write a memo while the app is
     * not running. So the alarm tells you, and the next launch catches up.
     */
    suspend fun runDueRecurring(): List<String> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val now = Clock.System.now()
        val saved = templates.templates.first().associateBy { it.title }
        val created = mutableListOf<String>()

        for (entry in config.current().recurring.filter { it.enabled }) {
            val template = saved[entry.templateTitle] ?: continue
            // Not yet due today: the next launch after the time will pick it up.
            val dueToday = today.atTime(LocalTime(entry.hour.toInt(), entry.minute.toInt())).toInstant(zone)
            if (dueToday > now) continue

            val body = TemplateRepository.expand(template.body, AppleTemplateValues())
            val writtenToday = memos.observeCreatedOn(account.id, today, zone).first()
                .map { Schedule.firstLine(it.content) }
            if (!Schedule.shouldCreate(Schedule.firstLine(body), writtenToday)) continue

            memos.create(account.id, body, Visibility.PRIVATE)
            created += template.title
        }
        return created
    }

    /**
     * The hour the digest goes out, so the app can set a weekly alarm on the same time this
     * label describes.
     */
    val digestHour: Int get() = DIGEST_HOUR

    /**
     * The most recent Sunday evening that has already gone: the digest this Mac owes, if it
     * has not shown one since.
     *
     * The app needs this because the notification it scheduled a week ago carries a body
     * written a week ago. The alarm is what survives being closed; the words are worked out
     * when there is something to work them out from.
     */
    suspend fun lastDigestDueEpochMs(): Long {
        val zone = TimeZone.currentSystemDefault()
        val next = Schedule.nextWeekly(DayOfWeek.SUNDAY, DIGEST_HOUR, 0, Clock.System.now(), zone)
        return (next - 7.days).toEpochMilliseconds()
    }

    /** When the digest next goes out, or empty when it is switched off. */
    suspend fun digestNextLabel(): String {
        if (!config.current().weeklyDigest) return ""
        val zone = TimeZone.currentSystemDefault()
        return Schedule.nextWeekly(DayOfWeek.SUNDAY, DIGEST_HOUR, 0, Clock.System.now(), zone)
            .friendlyWithTime(zone)
    }

    /** The digest itself, worked out from the local database by the same code the phone uses. */
    suspend fun digestText(): String? {
        val account = db.accountDao().getActive() ?: return null
        val zone = TimeZone.currentSystemDefault()
        val summary = Digest.summarise(
            all = memos.allForDigest(account.id),
            activeDays = memos.observeActiveDays(account.id, zone).first(),
            today = Clock.System.todayIn(zone),
            zone = zone,
        )
        return Digest.text(summary, AppleDigestLabels())
    }

    /** A reminder's memo, if this Mac has a local copy of it yet. */
    private suspend fun localIdFor(remoteName: String): String =
        accounts.activeAccountOrNull()?.let { memos.ensureLocal(it, remoteName) }.orEmpty()

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


    // MARK: archive, colour and undo

    /** Archived memos, grouped like the timeline. */
    suspend fun archived(): List<TimelineSection> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val byModified = AppleStack.preferences.settings.first().sortByModified
        return group(memos.observeTimeline(account.id, byModified = byModified, state = MemoState.ARCHIVED).first())
    }

    suspend fun setArchived(localId: String, archived: Boolean) =
        memos.setState(localId, if (archived) MemoState.ARCHIVED else MemoState.NORMAL)

    suspend fun isArchived(localId: String): Boolean =
        memos.observeMemoOnce(localId)?.state == MemoState.ARCHIVED

    /** Takes back a delete that has not reached the server yet. */
    suspend fun undoDelete(localId: String): Boolean = memos.undoDelete(localId)

    /** The sixteen colours a memo can be tinted, as name and 0xRRGGBB. */
    fun colours(): List<ColourOption> = NoteColour.entries.map { ColourOption(it.name, it.hex) }

    suspend fun setColour(localId: String, name: String?) =
        memos.setColour(localId, name?.let { n -> NoteColour.entries.firstOrNull { it.name == n } })

    // MARK: comments and reactions

    /** The nine reactions the Android app offers first. Any emoji works; these are the shortcuts. */
    val quickReactions: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🎉", "👀", "🔥", "✅")

    /**
     * A memo's comments, refreshed from the server when it can be reached and read from the
     * database either way. Comments are not part of the memo list, so they arrive on demand.
     */
    suspend fun comments(localId: String): List<CommentRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        val parent = memos.observeMemoOnce(localId)?.remoteName ?: return emptyList()
        runCatching { memos.refreshComments(account, parent) }
        return memos.observeComments(account.id, parent).first().map { comment ->
            CommentRow(
                localId = comment.localId,
                creator = comment.creator?.substringAfterLast('/').orEmpty(),
                mine = comment.creator == null || comment.creator == account.userResourceName,
                body = comment.displayContent,
                dateLabel = comment.createTime.friendly(),
                pending = comment.remoteName == null,
            )
        }
    }

    /** False for a memo the server has not seen yet: a comment needs a parent name. */
    suspend fun addComment(localId: String, text: String): Boolean {
        val account = db.accountDao().getActive() ?: return false
        val parent = memos.observeMemoOnce(localId)?.remoteName ?: return false
        memos.addComment(account.id, parent, text, Visibility.PRIVATE)
        return true
    }

    suspend fun reactions(localId: String): List<ReactionRow> {
        val me = db.accountDao().getActive()?.userResourceName
        return memos.observeReactions(localId).first()
            .groupBy { it.reactionType }
            .map { (type, list) -> ReactionRow(type, list.size, list.any { it.creator == me }) }
            .sortedByDescending { it.count }
    }

    suspend fun toggleReaction(localId: String, type: String) {
        val me = db.accountDao().getActive()?.userResourceName ?: return
        memos.toggleReaction(localId, me, type)
    }

    // MARK: references

    /** Memos this one points at. */
    suspend fun references(localId: String): List<ReferenceRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.observeReferences(localId).first().map { ref ->
            ReferenceRow(
                remoteName = ref.relatedRemoteName,
                snippet = ref.relatedSnippet.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                localId = db.memoDao().getByRemoteName(account.id, ref.relatedRemoteName)?.localId,
            )
        }
    }

    /** Memos that point at this one. */
    suspend fun backlinks(localId: String): List<MemoRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val remoteName = memos.observeMemoOnce(localId)?.remoteName ?: return emptyList()
        return memos.observeBacklinks(account.id, remoteName).first().map { it.toRow() }
    }

    /** Synced, top-level memos matching [query], for the reference picker. */
    suspend fun referenceCandidates(query: String): List<MemoRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.pickReferenceCandidates(account.id, query).map { it.toRow() }
    }

    suspend fun addReference(localId: String, targetLocalId: String) {
        val target = memos.observeMemoOnce(targetLocalId) ?: return
        memos.addReference(localId, target)
    }

    suspend fun removeReference(localId: String, remoteName: String) = memos.removeReference(localId, remoteName)

    /** Every memo that references or is referenced, and the edges between them. */
    suspend fun referenceGraph(): GraphData {
        val account = db.accountDao().getActive() ?: return GraphData(emptyList(), emptyList())
        val (nodes, edges) = memos.referenceGraph(account.id)
        return GraphData(nodes.map { it.toRow() }, edges.map { GraphEdge(it.first, it.second) })
    }

    /** Opens a memo by its server name, pulling it if this device does not hold it yet. */
    suspend fun localIdForRemote(remoteName: String): String? =
        accounts.activeAccountOrNull()?.let { memos.ensureLocal(it, remoteName) }

    // MARK: share links

    suspend fun shares(localId: String): List<ShareRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        val remoteName = memos.observeMemoOnce(localId)?.remoteName ?: return emptyList()
        val zone = TimeZone.currentSystemDefault()
        return shares.list(account, remoteName).map {
            ShareRow(it.name, it.url, it.createTime.friendly(), it.expireTime?.friendlyWithTime(zone))
        }
    }

    suspend fun createShare(localId: String, expiresInDays: Int): ShareRow? {
        val account = accounts.activeAccountOrNull() ?: return null
        val remoteName = memos.observeMemoOnce(localId)?.remoteName ?: return null
        val expires = if (expiresInDays > 0) Clock.System.now() + expiresInDays.days else null
        val share = shares.create(account, remoteName, expires)
        return ShareRow(share.name, share.url, share.createTime.friendly(), share.expireTime?.friendlyWithTime(TimeZone.currentSystemDefault()))
    }

    suspend fun revokeShare(localId: String, name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        val remoteName = memos.observeMemoOnce(localId)?.remoteName ?: return
        shares.list(account, remoteName).firstOrNull { it.name == name }?.let { shares.delete(account, it) }
    }

    // MARK: shortcuts

    /** Saved server-side filters. Running one needs the server; the results are shown from the database. */
    suspend fun shortcuts(): List<ShortcutRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return shortcuts.observe(account.id).first().map { ShortcutRow(it.name, it.title, it.filter) }
    }

    suspend fun runShortcut(name: String): List<TimelineSection> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        val shortcut = shortcuts.observe(account.id).first().firstOrNull { it.name == name } ?: return emptyList()
        val names = shortcuts.run(account, shortcut)
        if (names.isEmpty()) return emptyList()
        return group(shortcuts.observeMemos(account.id, names).first())
    }

    suspend fun saveShortcut(name: String?, title: String, filter: String) {
        val account = accounts.activeAccountOrNull() ?: return
        if (name == null) shortcuts.create(account, title, filter)
        else shortcuts.update(account, com.keltruc.mymemos.model.Shortcut(name, title, filter))
    }

    suspend fun deleteShortcut(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        shortcuts.observe(account.id).first().firstOrNull { it.name == name }?.let { shortcuts.delete(account, it) }
    }

    // MARK: places

    suspend fun setLocation(localId: String, latitude: Double, longitude: Double, placeName: String) =
        memos.setLocation(localId, Location(placeName, latitude, longitude))

    suspend fun clearLocation(localId: String) = memos.setLocation(localId, null)

    /** Located memos ordered by distance from where the device says it is. */
    suspend fun nearby(latitude: Double, longitude: Double): List<NearbyRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.memosWithLocation(account.id).mapNotNull { memo ->
            val place = memo.location ?: return@mapNotNull null
            NearbyRow(memo.toRow(), place.placeholder, distanceMetres(latitude, longitude, place.latitude, place.longitude))
        }.sortedBy { it.metres }
    }

    private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    // MARK: sync state

    suspend fun syncState(): SyncStatusRow {
        val account = db.accountDao().getActive() ?: return SyncStatusRow(0, 0, 0, null, false, "")
        val state = memos.observeSyncState(account.id).first()
        return SyncStatusRow(
            pending = state.pendingCount,
            failed = state.failedCount,
            conflicts = state.conflictCount,
            lastError = state.lastError,
            authExpired = state.authExpired,
            lastSuccessLabel = state.lastSuccess?.friendlyWithTime(TimeZone.currentSystemDefault()).orEmpty(),
        )
    }

    suspend fun failedOps(): List<FailedOpRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return memos.observeFailedOps(account.id).first().map { op ->
            val memo = memos.observeMemoOnce(op.memoLocalId)
            FailedOpRow(
                id = op.id,
                memoLocalId = op.memoLocalId,
                memoTitle = memo?.let { m -> if (m.isLocked) "Locked memo" else MemoTitle.of(m.displayContent) ?: m.firstLine() }.orEmpty(),
                type = op.type.lowercase().replace('_', ' '),
                error = op.error.orEmpty(),
                attempts = op.attempts,
            )
        }
    }

    suspend fun retryFailed() {
        val account = db.accountDao().getActive() ?: return
        memos.retryFailed(account.id)
    }

    /** Conflict copies the merge could not settle, kept rather than lost. */
    suspend fun conflicts(): List<MemoRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        return db.memoDao().observeConflicts(account.id).first().map { it.toModel().toRow() }
    }

    suspend fun keepConflictCopy(localId: String) = memos.resolveConflict(localId)

    /** Signs in again with the password when the device's token has lapsed. */
    suspend fun reauthenticate(password: String): Boolean {
        val account = accounts.activeAccountOrNull() ?: return false
        return runCatching { accounts.reauthenticate(account, password) }.isSuccess
    }

    // MARK: accounts and servers

    suspend fun accounts(): List<AccountRow> {
        val active = db.accountDao().getActive()?.id
        return accounts.accounts.first().map {
            AccountRow(
                id = it.id,
                serverUrl = it.serverUrl,
                username = it.username,
                displayName = it.displayName.ifEmpty { it.username },
                active = it.id == active,
                admin = it.role == UserRole.ADMIN,
            )
        }
    }

    suspend fun switchAccount(id: Long) {
        accounts.switchTo(id)
        signedInAs()
    }

    suspend fun isAdmin(): Boolean = db.accountDao().getActive()?.role == UserRole.ADMIN.name

    /** Servers signed into before, newest first. Only the address is kept. */
    suspend fun knownServers(): List<String> = AppleStack.preferences.settings.first().knownServers

    suspend fun rememberServer(url: String) = AppleStack.preferences.rememberServer(url)

    suspend fun forgetServer(url: String) = AppleStack.preferences.forgetServer(url)

    /** Move ticked task lines below the unticked ones whenever a memo is saved. */
    suspend fun sortCompletedTasks(): Boolean = AppleStack.preferences.settings.first().sortCompletedTasks

    suspend fun setSortCompletedTasks(enabled: Boolean) = AppleStack.preferences.setSortCompletedTasks(enabled)

    // MARK: tag styles

    suspend fun tagStyles(): List<TagStyleRow> =
        config.current().tagStyles.map { (tag, style) -> TagStyleRow(tag, style.emoji, style.colour?.name, style.colour?.hex ?: -1L) }

    suspend fun setTagStyle(tag: String, emoji: String?, colourName: String?) {
        val colour = colourName?.let { n -> NoteColour.entries.firstOrNull { it.name == n } }
        config.setTagStyle(tag, TagStyle(emoji?.takeIf { it.isNotBlank() }, colour))
    }

    // MARK: editor completions

    /** Tags starting with [prefix], most used first, for the editor's popup. */
    suspend fun tagSuggestions(prefix: String): List<String> =
        tags().filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }.take(8)

    /**
     * Completions for an `@` date, from the same parser the tasks screen reads, so a completion
     * can never mean a different day than it shows.
     */
    fun dateSuggestions(prefix: String): List<DateSuggestionRow> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val labels = AppleDueDateLabels()
        return DueDateParser.suggest(prefix, today).map {
            DateSuggestionRow(token = it.token, hint = if (it.showsDate) labels.hint(it.date) else "")
        }
    }

    /** The memos written on one day, for the day-by-day review. [isoDate] is yyyy-MM-dd. */
    suspend fun memosOn(isoDate: String): List<MemoRow> {
        val account = db.accountDao().getActive() ?: return emptyList()
        val day = runCatching { kotlinx.datetime.LocalDate.parse(isoDate) }.getOrNull() ?: return emptyList()
        return memos.observeCreatedOn(account.id, day, TimeZone.currentSystemDefault()).first().map { it.toRow() }
    }

    /** Ticks or unticks a task line, which edits the memo's text. */
    suspend fun toggleTask(localId: String, lineIndex: Int, checked: Boolean) {
        val memo = memos.observeMemoOnce(localId) ?: return
        if (memo.isLocked) return
        TaskLine.toggle(memo.content, lineIndex, checked)?.let { memos.updateContent(localId, it) }
    }

    // MARK: the account on the server

    suspend fun profile(): ProfileRow? {
        val account = accounts.activeAccountOrNull() ?: return null
        val user = settings.profile(account)
        return ProfileRow(user.name, user.username, user.displayName, user.email, about = user.description, admin = user.role == UserRole.ADMIN)
    }

    suspend fun updateProfile(displayName: String, description: String, email: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.updateProfile(account, displayName, description, email)
        signedInAs()
    }

    suspend fun changePassword(newPassword: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.changePassword(account, newPassword)
    }

    suspend fun defaultVisibility(): String {
        val account = accounts.activeAccountOrNull() ?: return "PRIVATE"
        return settings.preferences(account).defaultVisibility.name
    }

    suspend fun setDefaultVisibility(visibility: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.setDefaultVisibility(account, Visibility.valueOf(visibility))
    }

    suspend fun tokens(): List<TokenRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        val own = settings.ownTokenName(account)
        val zone = TimeZone.currentSystemDefault()
        return settings.tokens(account).map {
            TokenRow(
                name = it.name,
                label = it.description,
                createdLabel = it.createdAt.friendly(),
                expiresLabel = it.expiresAt?.friendlyWithTime(zone) ?: "Never",
                lastUsedLabel = it.lastUsedAt?.friendlyWithTime(zone) ?: "Never",
                thisDevice = it.name == own,
            )
        }
    }

    /** Returns the new token's value, which the server shows exactly once. */
    suspend fun createToken(description: String, expiresInDays: Int): String {
        val account = accounts.activeAccountOrNull() ?: return ""
        return settings.createToken(account, description, expiresInDays.takeIf { it > 0 })
    }

    suspend fun deleteToken(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.tokens(account).firstOrNull { it.name == name }?.let { settings.deleteToken(account, it) }
    }

    suspend fun webhooks(): List<WebhookRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        return settings.webhooks(account).map { WebhookRow(it.name, it.displayName, it.url, it.createTime.friendly()) }
    }

    suspend fun createWebhook(displayName: String, url: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.createWebhook(account, displayName, url)
    }

    suspend fun deleteWebhook(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.webhooks(account).firstOrNull { it.name == name }?.let { settings.deleteWebhook(account, it) }
    }

    suspend fun notifications(): List<NotificationRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        return settings.notifications(account).map {
            NotificationRow(
                name = it.name,
                sender = it.senderUsername,
                unread = it.unread,
                dateLabel = it.createTime.friendlyWithTime(TimeZone.currentSystemDefault()),
                type = it.type.lowercase().replace('_', ' '),
                memoRemoteName = it.memoRemoteName,
                memoSnippet = it.memoSnippet,
                relatedSnippet = it.relatedSnippet,
            )
        }
    }

    suspend fun unreadNotifications(): Int {
        val account = accounts.activeAccountOrNull() ?: return 0
        runCatching { settings.refreshUnreadCount(account) }
        return settings.unreadNotifications.value
    }

    suspend fun markNotificationRead(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.notifications(account).firstOrNull { it.name == name }?.let { settings.markRead(account, it) }
    }

    suspend fun deleteNotification(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.notifications(account).firstOrNull { it.name == name }?.let { settings.deleteNotification(account, it) }
    }

    suspend fun stats(): StatsRow? {
        val account = accounts.activeAccountOrNull() ?: return null
        val stats = settings.stats(account)
        return StatsRow(
            totalMemos = stats.totalMemos,
            links = stats.links,
            code = stats.code,
            todos = stats.todos,
            undone = stats.undone,
            tagCounts = stats.tagCounts.entries.sortedByDescending { it.value }.map { TagCount(it.key, it.value) },
            activeDays = stats.createdTimes.map { it.toLocalDateTime(TimeZone.currentSystemDefault()).date }.toSet().size,
        )
    }

    // MARK: administration

    suspend fun users(): List<UserRow> {
        val account = accounts.activeAccountOrNull() ?: return emptyList()
        return settings.users(account).map { UserRow(it.name, it.username, it.displayName, it.email, it.role == UserRole.ADMIN) }
    }

    suspend fun createUser(username: String, password: String, admin: Boolean) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.createUser(account, username, password, if (admin) "ADMIN" else "USER")
    }

    suspend fun setUserArchived(name: String, archived: Boolean) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.users(account).firstOrNull { it.name == name }?.let { settings.setUserArchived(account, it, archived) }
    }

    suspend fun deleteUser(name: String) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.users(account).firstOrNull { it.name == name }?.let { settings.deleteUser(account, it) }
    }

    suspend fun instanceGeneral(): InstanceRow? {
        val account = accounts.activeAccountOrNull() ?: return null
        val g = settings.instanceGeneral(account)
        return InstanceRow(g.title, g.description, g.disallowRegistration, g.disallowPasswordAuth, g.disallowChangeUsername, g.disallowChangeNickname, g.weekStartDayOffset)
    }

    suspend fun updateInstanceGeneral(row: InstanceRow) {
        val account = accounts.activeAccountOrNull() ?: return
        settings.updateInstanceGeneral(
            account,
            InstanceGeneral(row.title, row.about, row.disallowRegistration, row.disallowPasswordAuth, row.disallowChangeUsername, row.disallowChangeNickname, row.weekStartDayOffset),
        )
    }

    suspend fun instanceStats(): InstanceStatsRow? {
        val account = accounts.activeAccountOrNull() ?: return null
        val s = settings.instanceStats(account)
        return InstanceStatsRow(s.databaseDriver, s.databaseBytes, s.localStorageBytes)
    }

    private companion object {
        /** Sunday evening, late enough that the week is genuinely over. */
        const val DIGEST_HOUR = 18
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

    private fun Instant.friendlyWithTime(zone: TimeZone): String {
        val local = toLocalDateTime(zone)
        val day = AppleTimelineLabels().label(
            TimelineGrouping.Bucket.Day(local.date),
            Clock.System.todayIn(zone),
        )
        return "$day at ${local.hour.pad()}:${local.minute.pad()}"
    }

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
data class TemplateRow(val id: Long, val title: String, val body: String)

/** A one-off reminder, with its memo already looked up so the list can name it. */
data class ReminderRow(
    val id: String,
    val memoRemoteName: String,
    val memoLocalId: String,
    val memoTitle: String,
    val note: String,
    val atEpochMs: Long,
    val whenLabel: String,
    val overdue: Boolean,
)

/**
 * A template and whether it runs itself daily. Every template gets a row, switched off unless
 * the config says otherwise, so turning one on is a switch rather than a separate thing to add.
 */
data class RecurringRow(
    val templateId: Long,
    val templateTitle: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    /** When it next runs, already written out. Empty when it is switched off. */
    val nextLabel: String,
)

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
    /** How many lines [bodyBelowTitle] dropped from the front, so a task's line index maps back. */
    val bodyLineOffset: Int,
    val archived: Boolean,
    /** False while the memo is still in the outbox: comments, shares and reminders need a server name. */
    val onServer: Boolean,
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

// MARK: - Rows for the Phase D surface

data class ColourOption(val name: String, val hex: Long)

data class CommentRow(
    val localId: String,
    val creator: String,
    val mine: Boolean,
    val body: String,
    val dateLabel: String,
    /** Not on the server yet. */
    val pending: Boolean,
)

data class ReactionRow(val type: String, val count: Int, val mine: Boolean)

/** A memo this one points at; [localId] is null until this device has pulled it. */
data class ReferenceRow(val remoteName: String, val snippet: String, val localId: String?)

data class GraphEdge(val from: String, val to: String)

data class GraphData(val nodes: List<MemoRow>, val edges: List<GraphEdge>)

data class ShareRow(val name: String, val url: String, val createdLabel: String, val expiresLabel: String?)

data class ShortcutRow(val name: String, val title: String, val filter: String)

data class NearbyRow(val row: MemoRow, val placeName: String, val metres: Double)

data class SyncStatusRow(
    val pending: Int,
    val failed: Int,
    val conflicts: Int,
    val lastError: String?,
    val authExpired: Boolean,
    val lastSuccessLabel: String,
)

data class FailedOpRow(
    val id: Long,
    val memoLocalId: String,
    val memoTitle: String,
    val type: String,
    val error: String,
    val attempts: Int,
)

data class AccountRow(
    val id: Long,
    val serverUrl: String,
    val username: String,
    val displayName: String,
    val active: Boolean,
    val admin: Boolean,
)

data class TagStyleRow(val tag: String, val emoji: String?, val colourName: String?, val colourHex: Long)

data class DateSuggestionRow(val token: String, val hint: String)

// `description` is avoided as a property name throughout: Kotlin/Native exports it under
// another name because NSObject already has one, and Swift then reads the object dump.
data class ProfileRow(
    val name: String,
    val username: String,
    val displayName: String,
    val email: String,
    val about: String,
    val admin: Boolean,
)

data class TokenRow(
    val name: String,
    val label: String,
    val createdLabel: String,
    val expiresLabel: String,
    val lastUsedLabel: String,
    val thisDevice: Boolean,
)

data class WebhookRow(val name: String, val displayName: String, val url: String, val createdLabel: String)

data class NotificationRow(
    val name: String,
    val sender: String,
    val unread: Boolean,
    val dateLabel: String,
    val type: String,
    val memoRemoteName: String,
    val memoSnippet: String,
    val relatedSnippet: String,
)

data class TagCount(val tag: String, val count: Int)

data class StatsRow(
    val totalMemos: Int,
    val links: Int,
    val code: Int,
    val todos: Int,
    val undone: Int,
    val tagCounts: List<TagCount>,
    val activeDays: Int,
)

data class UserRow(val name: String, val username: String, val displayName: String, val email: String, val admin: Boolean)

data class InstanceRow(
    val title: String,
    val about: String,
    val disallowRegistration: Boolean,
    val disallowPasswordAuth: Boolean,
    val disallowChangeUsername: Boolean,
    val disallowChangeNickname: Boolean,
    val weekStartDayOffset: Int,
)

data class InstanceStatsRow(val databaseDriver: String, val databaseBytes: Long, val localStorageBytes: Long)
