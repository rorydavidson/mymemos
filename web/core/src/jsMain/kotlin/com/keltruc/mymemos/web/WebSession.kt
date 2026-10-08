package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.account.AvatarSource
import com.keltruc.mymemos.data.config.ConfigCodec
import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.notify.Digest
import com.keltruc.mymemos.data.notify.Schedule
import com.keltruc.mymemos.data.repository.Templates
import com.keltruc.mymemos.data.text.ColourTag
import com.keltruc.mymemos.data.text.DueDateParser
import com.keltruc.mymemos.data.text.EmojiCatalogue
import com.keltruc.mymemos.data.text.MarkdownContinuation
import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.data.text.TaskLine
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.model.AppConfig
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.RecurringTemplate
import com.keltruc.mymemos.model.Reminder
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.TagStyle
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.dto.ColorDto
import com.keltruc.mymemos.network.dto.CreatePersonalAccessTokenRequestDto
import com.keltruc.mymemos.network.dto.CustomProfileDto
import com.keltruc.mymemos.network.dto.InstanceGeneralSettingDto
import com.keltruc.mymemos.network.dto.InstanceSettingDto
import com.keltruc.mymemos.network.dto.MemoShareDto
import com.keltruc.mymemos.network.dto.NotificationWriteDto
import com.keltruc.mymemos.network.dto.PasswordCredentialsDto
import com.keltruc.mymemos.network.dto.ShortcutDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import com.keltruc.mymemos.network.dto.TagMetadataDto
import com.keltruc.mymemos.network.dto.TagsSettingDto
import com.keltruc.mymemos.network.dto.UserGeneralSettingDto
import com.keltruc.mymemos.network.dto.UserSettingDto
import com.keltruc.mymemos.network.dto.UserWebhookDto
import com.keltruc.mymemos.network.dto.UserWriteDto
import com.keltruc.mymemos.web.store.AccountRecord
import com.keltruc.mymemos.web.store.IndexedDbPersistence
import com.keltruc.mymemos.web.store.MemoRecord
import com.keltruc.mymemos.web.store.Persistence
import com.keltruc.mymemos.web.store.ShortcutRecord
import com.keltruc.mymemos.web.store.TemplateRecord
import com.keltruc.mymemos.web.store.WebStore
import com.keltruc.mymemos.web.store.parseEpochMs
import com.keltruc.mymemos.web.store.toModel
import com.keltruc.mymemos.web.store.toRecord
import com.keltruc.mymemos.web.sync.ApiRegistry
import com.keltruc.mymemos.web.sync.SyncScheduler
import com.keltruc.mymemos.web.sync.WebMemos
import com.keltruc.mymemos.web.sync.WebSyncEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Opens the browser's database and returns a session over it. The page's way in. */
@JsExport
fun openSession(): Promise<WebSession> = MainScope().promise {
    WebSession.create(IndexedDbPersistence.open("mymemos"))
}

/**
 * Everything the web UI can ask of the data layer, and nothing else: the web counterpart of
 * MemosSession in apple-shared, and kept deliberately close to it in shape and naming, so a
 * feature added to one has an obvious place in the other.
 *
 * Every call returns a Promise. Reads come from the local store, so they work offline; the
 * calls that are online-only by nature (account settings, admin, shares, shortcuts) say so
 * by rejecting when the server cannot be reached.
 */
@JsExport
class WebSession internal constructor(
    private val store: WebStore,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val registry = ApiRegistry()
    private val scheduler = SyncScheduler(scope) { store.activeAccount?.let { engine.sync(it.id) } }
    private val engine = WebSyncEngine(store) { api(it) }
    private val memos = WebMemos(store, scheduler)
    private val configWrites = Mutex()
    private var unread = 0
    private val objectUrls = HashMap<String, String>()

    /** The memo password for this tab; see [usePassword]. */
    private var password: String? = sessionPassword()

    internal companion object {
        suspend fun create(persistence: Persistence): WebSession {
            val store = WebStore(persistence)
            store.load()
            return WebSession(store, MainScope())
        }
    }

    private fun <T> promised(block: suspend () -> T): Promise<T> = scope.promise { block() }

    private fun api(account: AccountRecord): MemosApi = registry.api(account.serverUrl, account.token)

    private val active: AccountRecord? get() = store.activeAccount

    private fun requireActive(): AccountRecord = active ?: throw IllegalStateException("Not signed in")

    private fun model(localId: String): Memo? = store.memo(localId)?.toModel()

    /** Called after any change to local data, including a sync landing. The page re-reads. */
    fun onChange(listener: () -> Unit) = store.onChange(listener)

    // MARK: account

    /** The origin the page was served from: the default server, since it sits beside Memos. */
    fun defaultServer(): String = js("globalThis.location ? globalThis.location.origin : ''") as String

    fun isSignedIn(): Boolean = active != null

    fun signedInAs(): String? = active?.let { it.displayName.ifEmpty { it.username } }

    fun serverVersion(): String = active?.serverVersion.orEmpty()

    fun isAdmin(): Boolean = active?.role == "ADMIN" || active?.role == "HOST"

    /** Confirms the address really is a Memos server before anyone types a password at it. */
    fun probe(serverUrl: String): Promise<String> = promised {
        wrap { registry.anonymous(serverUrl).getInstanceProfile().version }
    }

    /**
     * Password sign-in yields a short-lived session. As the apps do, this mints a personal
     * access token for this browser at once and uses that, then ends the session so no
     * cookie is left behind. The password itself is never stored.
     */
    fun signIn(serverUrl: String, username: String, password: String): Promise<Unit> = promised {
        val url = MemosApiFactory.normaliseBaseUrl(serverUrl)
        val response = wrap { registry.anonymous(url).signIn(SignInRequestDto(PasswordCredentialsDto(username, password))) }
        val sessionApi = registry.api(url, response.accessToken)
        val minted = runCatching {
            sessionApi.createPersonalAccessToken(
                response.user.name,
                CreatePersonalAccessTokenRequestDto(description = "MyMemos on ${deviceName()}", expiresInDays = TOKEN_LIFETIME_DAYS),
            )
        }.getOrNull()?.takeIf { it.token.isNotEmpty() }
        if (minted != null) runCatching { sessionApi.signOut() }
        registry.evict(url, response.accessToken)
        saveAccount(
            url, response.user.name, response.user.username, response.user.displayName, response.user.avatarUrl, response.user.role,
            token = minted?.token ?: response.accessToken,
            minted = minted?.personalAccessToken?.name,
            method = if (minted != null) "PERSONAL_ACCESS_TOKEN" else "PASSWORD",
        )
        rememberServer(url)
        Unit
    }

    /** Signs in with a token the user made themselves. It is theirs, so sign-out leaves it. */
    fun signInWithToken(serverUrl: String, token: String): Promise<Unit> = promised {
        val url = MemosApiFactory.normaliseBaseUrl(serverUrl)
        val user = try {
            wrap { registry.api(url, token).getCurrentUser().user }
        } finally {
            registry.evict(url, token)
        }
        saveAccount(url, user.name, user.username, user.displayName, user.avatarUrl, user.role, token, null, "PERSONAL_ACCESS_TOKEN")
        rememberServer(url)
        Unit
    }

    /** Signs in again with the password when this browser's token has lapsed. */
    fun reauthenticate(password: String): Promise<Boolean> = promised {
        val account = requireActive()
        runCatching { signIn(account.serverUrl, account.username, password).await2() }.isSuccess
    }

    private suspend fun saveAccount(
        serverUrl: String, userName: String, username: String, displayName: String, avatarUrl: String, role: String,
        token: String, minted: String?, method: String,
    ) {
        val version = runCatching { registry.anonymous(serverUrl).getInstanceProfile().version }.getOrDefault("")
        val existing = store.accounts.firstOrNull { it.serverUrl == serverUrl && it.userResourceName == userName }
        val record = AccountRecord(
            id = existing?.id ?: ((store.accounts.maxOfOrNull { it.id } ?: 0) + 1),
            serverUrl = serverUrl, userResourceName = userName, username = username,
            displayName = displayName.ifEmpty { username }, avatarUrl = avatarUrl, role = role,
            authMethod = method, serverVersion = version,
            lastSyncEpochMs = existing?.lastSyncEpochMs, lastReconcileEpochMs = existing?.lastReconcileEpochMs ?: 0,
            token = token, mintedTokenName = minted,
        )
        existing?.let { registry.evict(it.serverUrl, it.token) }
        store.write { putAccounts(store.accounts.filter { it.id != record.id } + record, record.id) }
        if (store.templates.isEmpty()) seedTemplates()
    }

    /**
     * Revokes this browser's token on the server and forgets the account here. Returns false
     * when the server could not be told, so the page can say the token is still live and
     * needs revoking from Memos' own settings; the local copy goes either way.
     */
    fun signOut(): Promise<Boolean> = promised {
        val account = active ?: return@promised true
        val revoked = account.mintedTokenName?.let { name ->
            runCatching { api(account).deletePersonalAccessToken(name) }.isSuccess
        } ?: true
        registry.evict(account.serverUrl, account.token)
        val blobs = store.memosFor(account.id).flatMap { it.attachments }.map { it.localId }
        val rest = store.accounts.filter { it.id != account.id }
        store.write {
            deleteAllForAccount(account.id)
            putAccounts(rest, rest.firstOrNull()?.id)
        }
        blobs.forEach { store.deleteBlob(it) }
        objectUrls.values.forEach { url -> js("URL.revokeObjectURL(url)") }
        objectUrls.clear()
        if (rest.isEmpty()) {
            store.clearAll()
            forgetPassword()
        }
        revoked
    }

    fun accounts(): Array<AccountRow> = store.accounts.map {
        AccountRow(it.id, it.serverUrl, it.username, it.displayName.ifEmpty { it.username }, it.id == store.activeAccountId, it.role == "ADMIN" || it.role == "HOST")
    }.toTypedArray()

    fun switchAccount(id: Int): Promise<Unit> = promised {
        if (store.account(id) == null) return@promised
        store.write { putAccounts(store.accounts, id) }
        scheduler.syncNow(0)
    }

    /** Servers signed into before, newest first. Only the address is kept. */
    fun knownServers(): Array<String> = store.prefs.knownServers.toTypedArray()

    fun forgetServer(url: String): Promise<Unit> = promised {
        store.write { putPrefs(store.prefs.copy(knownServers = store.prefs.knownServers - url)) }
    }

    private suspend fun rememberServer(url: String) {
        store.write { putPrefs(store.prefs.copy(knownServers = (listOf(url) + store.prefs.knownServers.filter { it != url }).take(8))) }
    }

    // MARK: sync

    /** Push and pull now. [full] also reconciles deletions made elsewhere. */
    fun sync(full: Boolean): Promise<String> = promised {
        val account = active ?: return@promised "not signed in"
        when (engine.sync(account.id, fullPull = full)) {
            WebSyncEngine.Outcome.Success -> "ok"
            is WebSyncEngine.Outcome.AuthFailed -> "sign in again"
            is WebSyncEngine.Outcome.Retry -> "will retry"
        }
    }

    /** Asks for a sync soon, debounced: for reconnecting, the tab coming back, and timers. */
    fun requestSync() = scheduler.syncNow()

    fun syncState(): SyncStatusRow {
        val account = active ?: return SyncStatusRow(0, 0, 0, null, false, "", false)
        val ops = store.opsFor(account.id)
        val state = engine.state
        return SyncStatusRow(
            pending = ops.count { !it.failed },
            failed = ops.count { it.failed },
            conflicts = store.memosFor(account.id).count { it.syncStatus == SyncStatus.CONFLICT.name },
            lastError = state.lastError,
            authExpired = state.authExpired,
            lastSuccessLabel = state.lastSuccess?.friendlyWithTime().orEmpty(),
            running = state.running,
        )
    }

    fun failedOps(): Array<FailedOpRow> {
        val account = active ?: return emptyArray()
        return store.opsFor(account.id).filter { it.failed }.map { op ->
            FailedOpRow(op.id.toDouble(), op.memoLocalId, model(op.memoLocalId)?.listTitle().orEmpty(), op.type.lowercase().replace('_', ' '), op.lastError.orEmpty(), op.attempts)
        }.toTypedArray()
    }

    fun retryFailed(): Promise<Unit> = promised { active?.let { memos.retryFailed(it.id) } }

    fun conflicts(): Array<MemoRow> {
        val account = active ?: return emptyArray()
        return store.memosFor(account.id).filter { it.syncStatus == SyncStatus.CONFLICT.name }.map { it.toModel().toRow() }.toTypedArray()
    }

    fun keepConflictCopy(localId: String): Promise<Unit> = promised { memos.resolveConflict(localId) }

    // MARK: reading

    private fun visible(account: AccountRecord): List<Memo> = store.memosFor(account.id)
        .filter { it.syncStatus != SyncStatus.PENDING_DELETE.name && it.parent == null && !isConfig(it) }
        .map { it.toModel() }

    private fun isConfig(m: MemoRecord) = m.tags.any { it.contains(ConfigCodec.TAG) } || m.content.contains("#${ConfigCodec.TAG}")

    private fun ordered(list: List<Memo>): List<Memo> {
        val byModified = store.prefs.sortByModified
        return list.sortedWith(compareByDescending<Memo> { it.pinned }.thenByDescending { it.timelineTime(byModified).toEpochMilliseconds() })
    }

    /**
     * The timeline, grouped the way the apps group it: days this week, then whole weeks, then
     * whole months. The grouping is shared code; only the words above each group are here.
     */
    fun timeline(): Array<TimelineSection> {
        val account = active ?: return emptyArray()
        return group(ordered(visible(account).filter { it.state == MemoState.NORMAL }))
    }

    fun archived(): Array<TimelineSection> {
        val account = active ?: return emptyArray()
        return group(ordered(visible(account).filter { it.state == MemoState.ARCHIVED }))
    }

    /**
     * Offline search over the local copy. Every word must start a word in the memo, as the
     * apps' FTS prefix query does; `#tag` words match tags, including nested ones.
     */
    fun search(query: String): Array<TimelineSection> {
        val account = active ?: return emptyArray()
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return timeline()
        val (tagWords, textWords) = words.partition { it.startsWith("#") && it.length > 1 }
        val matches = visible(account).filter { memo ->
            val text = memo.displayContent.lowercase()
            tagWords.all { t -> val tag = t.drop(1).lowercase(); memo.tags.any { it.lowercase() == tag || it.lowercase().startsWith("$tag/") } } &&
                textWords.all { w -> wordStart(text, w.lowercase()) }
        }
        return group(matches.sortedByDescending { it.createTime.toEpochMilliseconds() }, pinnedFirst = false)
    }

    private fun wordStart(text: String, word: String): Boolean {
        var i = text.indexOf(word)
        while (i >= 0) {
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
            i = text.indexOf(word, i + 1)
        }
        return false
    }

    private fun group(all: List<Memo>, pinnedFirst: Boolean = true): Array<TimelineSection> {
        val byModified = store.prefs.sortByModified
        val today = today()
        val list = if (pinnedFirst) all else all.map { it.copy(pinned = false) }
        val originals = all.associateBy { it.localId }
        return TimelineGrouping.group(list, today, zone(), WebLabels.firstDayOfWeek(), byModified).map { g ->
            TimelineSection(g.key, WebLabels.timeline.label(g.bucket, today), g.memos.map { (originals[it.localId] ?: it).toRow(byModified) }.toTypedArray())
        }.toTypedArray()
    }

    /** Tags, most used first, over the whole account. */
    fun tags(): Array<String> = tagCounts().map { it.tag }.toTypedArray()

    fun tagCounts(): Array<TagCount> {
        val account = active ?: return emptyArray()
        return store.memosFor(account.id).filter { !isConfig(it) }
            .flatMap { ColourTag.visible(it.tags) }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .map { TagCount(it.key, it.value) }.toTypedArray()
    }

    fun memo(localId: String): MemoDetail? {
        val memo = model(localId) ?: return null
        val record = store.memo(localId)
        return MemoDetail(
            row = memo.toRow(store.prefs.sortByModified),
            content = if (memo.isLocked) "" else memo.displayContent,
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
            colourName = record?.colour,
        )
    }

    /** The memos written on one day, for the day-by-day review. [isoDate] is yyyy-MM-dd. */
    fun memosOn(isoDate: String): Array<MemoRow> {
        val account = active ?: return emptyArray()
        val day = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return emptyArray()
        return createdOn(account, day).map { it.toRow() }.toTypedArray()
    }

    private fun createdOn(account: AccountRecord, day: LocalDate): List<Memo> {
        val from = day.atStartOfDayIn(zone()).toEpochMilliseconds()
        val to = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone()).toEpochMilliseconds()
        return visible(account).filter { it.createTime.toEpochMilliseconds() in from until to }.sortedBy { it.createTime }
    }

    private fun activeDaySet(account: AccountRecord): Set<LocalDate> =
        visible(account).map { it.createTime.toLocalDateTime(zone()).date }.toSet()

    // MARK: writing

    fun create(content: String, visibility: String, pinned: Boolean): Promise<String> = promised {
        memos.create(requireActive().id, content, visibilityOf(visibility), pinned)
    }

    fun updateContent(localId: String, content: String): Promise<Unit> = promised { memos.updateContent(localId, content) }

    fun setPinned(localId: String, pinned: Boolean): Promise<Unit> = promised { memos.setPinned(localId, pinned) }

    fun setVisibility(localId: String, visibility: String): Promise<Unit> = promised { memos.setVisibility(localId, visibilityOf(visibility)) }

    fun setArchived(localId: String, archived: Boolean): Promise<Unit> = promised {
        memos.setState(localId, if (archived) MemoState.ARCHIVED else MemoState.NORMAL)
    }

    /** True when the delete can still be undone for a few seconds. */
    fun deleteMemo(localId: String): Promise<Boolean> = promised { memos.delete(localId) }

    fun undoDelete(localId: String): Promise<Boolean> = promised { memos.undoDelete(localId) }

    fun colours(): Array<ColourOption> = NoteColour.entries.map { ColourOption(it.name, it.hex.toInt()) }.toTypedArray()

    fun setColour(localId: String, name: String?): Promise<Unit> = promised {
        memos.setColour(localId, name?.let { n -> NoteColour.entries.firstOrNull { it.name == n } })
    }

    /** Ticks or unticks a task line, which edits the memo's text. */
    fun toggleTask(localId: String, lineIndex: Int, checked: Boolean): Promise<Unit> = promised {
        val memo = model(localId) ?: return@promised
        if (memo.isLocked) return@promised
        TaskLine.toggle(memo.content, lineIndex, checked)?.let { memos.updateContent(localId, it) }
    }

    /** Return inside a list carries the marker on; null when it does not apply. */
    fun continueList(text: String, cursor: Int): ListContinuation? =
        MarkdownContinuation.continueList(text, cursor)?.let { ListContinuation(it.text, it.cursor) }

    /** Tags starting with [prefix], most used first, for the editor's popup. */
    fun tagSuggestions(prefix: String): Array<String> =
        tags().filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }.take(8).toTypedArray()

    /** Completions for an `@` date, from the same parser the tasks screen reads. */
    fun dateSuggestions(prefix: String): Array<DateSuggestionRow> {
        val today = today()
        return DueDateParser.suggest(prefix, today).map {
            DateSuggestionRow(it.token, if (it.showsDate) WebLabels.dueDates.hint(it.date) else "")
        }.toTypedArray()
    }

    fun sortCompletedTasks(): Boolean = store.prefs.sortCompletedTasks
    fun setSortCompletedTasks(enabled: Boolean): Promise<Unit> = promised { store.write { putPrefs(store.prefs.copy(sortCompletedTasks = enabled)) } }
    fun sortByModified(): Boolean = store.prefs.sortByModified
    fun setSortByModified(enabled: Boolean): Promise<Unit> = promised { store.write { putPrefs(store.prefs.copy(sortByModified = enabled)) } }
    fun compactList(): Boolean = store.prefs.compactList
    fun setCompactList(enabled: Boolean): Promise<Unit> = promised { store.write { putPrefs(store.prefs.copy(compactList = enabled)) } }
    fun mapTilesEnabled(): Boolean = store.prefs.mapTiles
    fun setMapTiles(enabled: Boolean): Promise<Unit> = promised { store.write { putPrefs(store.prefs.copy(mapTiles = enabled)) } }

    /** Group keys the reader folded away; they stay folded next time. */
    fun foldedGroups(): Array<String> = store.prefs.folded.toTypedArray()
    fun setFolded(key: String, folded: Boolean): Promise<Unit> = promised {
        val next = if (folded) (store.prefs.folded + key).distinct() else store.prefs.folded - key
        store.write { putPrefs(store.prefs.copy(folded = next)) }
    }

    // MARK: attachments

    fun attachments(localId: String): Array<AttachmentRow> = store.memo(localId)?.attachments.orEmpty().map {
        AttachmentRow(it.localId, it.filename, it.mimeType, it.sizeBytes.toDouble(), it.mimeType.lowercase() in RASTER_TYPES, it.remoteName != null, it.externalLink?.takeIf(::isWebUrl))
    }.toTypedArray()

    fun attach(localId: String, filename: String, mimeType: String, bytes: Uint8Array): Promise<Unit> = promised {
        memos.addAttachment(localId, filename, mimeType.ifEmpty { "application/octet-stream" }, bytes.toByteArray())
    }

    fun removeAttachment(memoLocalId: String, attachmentLocalId: String): Promise<Unit> = promised {
        memos.removeAttachment(memoLocalId, attachmentLocalId)
    }

    /**
     * A URL the page can put in an `<img>` or a link. Attachments are fetched with the
     * credential (an image tag cannot send one), kept for offline use, and handed over as an
     * object URL. Null when the file is neither here nor reachable.
     */
    fun attachmentUrl(memoLocalId: String, attachmentLocalId: String): Promise<String?> = promised {
        objectUrls[attachmentLocalId]?.let { return@promised it }
        val memo = store.memo(memoLocalId) ?: return@promised null
        val a = memo.attachments.firstOrNull { it.localId == attachmentLocalId } ?: return@promised null
        a.externalLink?.let { return@promised it.takeIf(::isWebUrl) }
        var bytes = store.readBlob(a.localId)
        if (bytes == null) {
            val account = store.account(memo.accountId) ?: return@promised null
            // The name goes into a path sent with the credential; it must be an attachment's.
            val remote = a.remoteName?.takeIf { attachmentName.matches(it) } ?: return@promised null
            bytes = runCatching {
                registry.client(account.serverUrl, account.token).http.get("file/$remote/${encodeUri(a.filename)}").readRawBytes()
            }.getOrNull() ?: return@promised null
            runCatching { store.writeBlob(a.localId, bytes) }
        }
        objectUrl(bytes, displayableType(a.mimeType)).also { objectUrls[attachmentLocalId] = it }
    }

    /** The account's avatar as something an `<img>` can show, or null for none. */
    fun avatarUrl(): Promise<String?> = promised {
        val account = active ?: return@promised null
        when (val source = AvatarSource.of(account.avatarUrl, account.serverUrl)) {
            is AvatarSource.None -> null
            is AvatarSource.Bytes -> objectUrl(source.bytes, IMAGE_FALLBACK)
            // Another site's image is linked, not fetched with the credential.
            is AvatarSource.Url -> if (!source.sameOrigin) source.url else runCatching {
                val bytes = registry.client(account.serverUrl, account.token).http.get(source.url).readRawBytes()
                objectUrl(bytes, IMAGE_FALLBACK)
            }.getOrNull()
        }
    }

    // MARK: locked memos

    fun hasPassword(): Boolean = password != null

    /**
     * Holds the memo password for this tab. [remember] keeps it for the browser session (until
     * the tab closes) rather than on disk: a browser has no Keychain, and the password is what
     * protects text the server never sees.
     */
    fun usePassword(password: String, remember: Boolean) {
        this.password = password
        runCatching { if (remember) sessionStorage()?.setItem(PASSWORD_KEY, password) else sessionStorage()?.removeItem(PASSWORD_KEY) }
    }

    fun forgetPassword() {
        password = null
        runCatching { sessionStorage()?.removeItem(PASSWORD_KEY) }
    }

    fun reveal(localId: String): Promise<Reveal> = promised {
        val memo = model(localId) ?: return@promised Reveal(null, false, false)
        if (!memo.isLocked) return@promised Reveal(memo.displayContent, false, false)
        val pw = password ?: return@promised Reveal(null, needsPassword = true, wrongPassword = false)
        try {
            Reveal(WebCipher.decryptMemo(memo.content, pw), false, false)
        } catch (e: MemoCipher.WrongPassword) {
            Reveal(null, needsPassword = true, wrongPassword = true)
        }
    }

    /** Encrypts the memo; [title] stays readable beside it if given. False without a password. */
    fun lock(localId: String, title: String?): Promise<Boolean> = promised {
        val pw = password ?: return@promised false
        val memo = store.memo(localId) ?: return@promised false
        if (MemoCipher.isEncrypted(memo.content)) return@promised true
        memos.updateContent(localId, MemoCipher.withTitle(WebCipher.encryptMemo(memo.content, pw), title))
        true
    }

    fun setLockedTitle(localId: String, title: String?): Promise<Unit> = promised {
        val memo = store.memo(localId) ?: return@promised
        if (MemoCipher.isEncrypted(memo.content)) memos.updateContent(localId, MemoCipher.withTitle(memo.content, title))
    }

    /** Stores the plain text again. False for a wrong or missing password. */
    fun unlockForGood(localId: String): Promise<Boolean> = promised {
        val pw = password ?: return@promised false
        val memo = store.memo(localId) ?: return@promised false
        if (!MemoCipher.isEncrypted(memo.content)) return@promised true
        val plain = try { WebCipher.decryptMemo(memo.content, pw) } catch (e: MemoCipher.WrongPassword) { return@promised false }
        memos.updateContent(localId, plain)
        true
    }

    /** Edits a locked memo: the new text is encrypted before it is stored. */
    fun saveLocked(localId: String, plain: String): Promise<Boolean> = promised {
        val pw = password ?: return@promised false
        val memo = store.memo(localId) ?: return@promised false
        memos.updateContent(localId, MemoCipher.withTitle(WebCipher.encryptMemo(plain, pw), Memo.lockedTitleOf(memo.content)))
        true
    }

    // MARK: comments, reactions, references

    val quickReactions: Array<String> = arrayOf("👍", "❤️", "😂", "😮", "😢", "🎉", "👀", "🔥", "✅")

    /** A memo's comments, refreshed from the server when it can be reached. */
    fun comments(localId: String): Promise<Array<CommentRow>> = promised {
        val account = active ?: return@promised emptyArray()
        val parent = store.memo(localId)?.remoteName ?: return@promised emptyArray()
        runCatching {
            val fetched = api(account).listMemoComments(parent).memos
            store.write {
                for (dto in fetched) {
                    val existing = store.memoByRemoteName(account.id, dto.name)
                    if (existing != null && existing.syncStatus != SyncStatus.SYNCED.name) continue
                    upsertMemo(dto.toRecord(account.id, existing).copy(parent = parent))
                }
            }
        }
        store.memosFor(account.id)
            .filter { it.parent == parent && it.syncStatus != SyncStatus.PENDING_DELETE.name }
            .sortedBy { it.createTimeEpochMs }
            .map { c ->
                val m = c.toModel()
                CommentRow(c.localId, c.creator?.substringAfterLast('/').orEmpty(), c.creator == null || c.creator == account.userResourceName, m.displayContent, m.createTime.friendly(), c.remoteName == null)
            }.toTypedArray()
    }

    /** False for a memo the server has not seen yet: a comment needs a parent name. */
    fun addComment(localId: String, text: String): Promise<Boolean> = promised {
        val account = active ?: return@promised false
        val parent = store.memo(localId)?.remoteName ?: return@promised false
        memos.addComment(account.id, parent, text, Visibility.PRIVATE)
        true
    }

    fun reactions(localId: String): Array<ReactionRow> {
        val me = active?.userResourceName
        return store.memo(localId)?.reactions.orEmpty().groupBy { it.reactionType }
            .map { (type, list) -> ReactionRow(type, list.size, list.any { it.creator == me }) }
            .sortedByDescending { it.count }.toTypedArray()
    }

    fun toggleReaction(localId: String, type: String): Promise<Unit> = promised {
        val me = active?.userResourceName ?: return@promised
        memos.toggleReaction(localId, me, type)
    }

    fun references(localId: String): Array<ReferenceRow> {
        val account = active ?: return emptyArray()
        return store.memo(localId)?.relations.orEmpty().filter { it.type == "REFERENCE" }.map { ref ->
            ReferenceRow(
                ref.relatedRemoteName,
                ref.relatedSnippet.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                store.memoByRemoteName(account.id, ref.relatedRemoteName)?.localId,
            )
        }.toTypedArray()
    }

    fun backlinks(localId: String): Array<MemoRow> {
        val account = active ?: return emptyArray()
        val name = store.memo(localId)?.remoteName ?: return emptyArray()
        return store.memosFor(account.id)
            .filter { m -> m.syncStatus != SyncStatus.PENDING_DELETE.name && m.relations.any { it.relatedRemoteName == name && it.type == "REFERENCE" } }
            .sortedByDescending { it.createTimeEpochMs }
            .map { it.toModel().toRow() }.toTypedArray()
    }

    /** Synced, top-level memos matching [query], for the reference picker. */
    fun referenceCandidates(query: String): Array<MemoRow> {
        val account = active ?: return emptyArray()
        return store.memosFor(account.id)
            .filter { it.parent == null && it.syncStatus != SyncStatus.PENDING_DELETE.name && it.remoteName != null && it.content.contains(query, ignoreCase = true) }
            .sortedByDescending { it.updateTimeEpochMs }.take(30)
            .map { it.toModel().toRow() }.toTypedArray()
    }

    fun addReference(localId: String, targetLocalId: String): Promise<Unit> = promised {
        store.memo(targetLocalId)?.let { memos.addReference(localId, it) }
    }

    fun removeReference(localId: String, remoteName: String): Promise<Unit> = promised { memos.removeReference(localId, remoteName) }

    /** Every memo that references or is referenced, and the edges between them. */
    fun referenceGraph(): GraphData {
        val account = active ?: return GraphData(emptyArray(), emptyArray())
        val all = store.memosFor(account.id).filter { it.remoteName != null && it.parent == null && it.syncStatus != SyncStatus.PENDING_DELETE.name }
        val byRemote = all.associateBy { it.remoteName }
        val edges = all.flatMap { from -> from.relations.filter { it.type == "REFERENCE" }.mapNotNull { r -> byRemote[r.relatedRemoteName]?.let { GraphEdge(from.localId, it.localId) } } }
        val connected = edges.flatMap { listOf(it.from, it.to) }.toSet()
        return GraphData(all.filter { it.localId in connected }.map { it.toModel().toRow() }.toTypedArray(), edges.toTypedArray())
    }

    /** Opens a memo by its server name, pulling it if this browser does not hold it yet. */
    fun localIdForRemote(remoteName: String): Promise<String?> = promised { ensureLocal(remoteName) }

    private suspend fun ensureLocal(remoteName: String): String? {
        val account = active ?: return null
        store.memoByRemoteName(account.id, remoteName)?.let { return it.localId }
        val dto = runCatching { api(account).getMemo(remoteName) }.getOrNull() ?: return null
        val record = dto.toRecord(account.id, null)
        store.write { upsertMemo(record) }
        return record.localId
    }

    // MARK: share links (online only)

    fun shares(localId: String): Promise<Array<ShareRow>> = promised {
        val account = requireActive()
        val name = store.memo(localId)?.remoteName ?: return@promised emptyArray()
        wrap { api(account).listMemoShares(name) }.memoShares.map { it.toRow(account) }.toTypedArray()
    }

    fun createShare(localId: String, expiresInDays: Int): Promise<ShareRow?> = promised {
        val account = requireActive()
        val name = store.memo(localId)?.remoteName ?: return@promised null
        val expires = if (expiresInDays > 0) (Clock.System.now() + expiresInDays.days).toString() else null
        wrap { api(account).createMemoShare(name, MemoShareDto(expireTime = expires)) }.toRow(account)
    }

    fun revokeShare(name: String): Promise<Unit> = promised { wrap { api(requireActive()).deleteMemoShare(name) } }

    private fun MemoShareDto.toRow(account: AccountRecord) = ShareRow(
        name, "${account.serverUrl.trimEnd('/')}/memos/shares/${name.substringAfterLast('/')}",
        parseEpochMs(createTime).friendly(), expireTime?.let { Instant.parse(it).friendlyWithTime() },
    )

    // MARK: shortcuts

    fun shortcuts(): Array<ShortcutRow> {
        val account = active ?: return emptyArray()
        return store.shortcutsFor(account.id).map { ShortcutRow(it.name, it.title, it.filter) }.toTypedArray()
    }

    /** Runs a saved filter on the server; the results are kept locally and shown from there. */
    fun runShortcut(name: String): Promise<Array<TimelineSection>> = promised {
        val account = requireActive()
        val shortcut = store.shortcutsFor(account.id).firstOrNull { it.name == name } ?: return@promised emptyArray()
        val names = mutableListOf<String>()
        var token: String? = null
        do {
            val page = wrap { api(account).listMemos(pageSize = 200, pageToken = token, filter = shortcut.filter.ifBlank { null }) }
            store.write {
                for (dto in page.memos) {
                    val existing = store.memoByRemoteName(account.id, dto.name)
                    if (existing != null && existing.syncStatus != SyncStatus.SYNCED.name) continue
                    upsertMemo(dto.toRecord(account.id, existing))
                }
            }
            names += page.memos.map { it.name }
            token = page.nextPageToken.ifEmpty { null }
        } while (token != null)
        val wanted = names.toSet()
        group(ordered(visible(account).filter { it.remoteName in wanted }))
    }

    fun saveShortcut(name: String?, title: String, filter: String): Promise<Unit> = promised {
        val account = requireActive()
        val api = api(account)
        wrap {
            if (name == null) api.createShortcut(account.userResourceName, ShortcutDto(title = title, filter = filter))
            else api.updateShortcut(name, ShortcutDto(name = name, title = title, filter = filter))
        }
        refreshShortcuts(account)
    }

    fun deleteShortcut(name: String): Promise<Unit> = promised {
        val account = requireActive()
        wrap { api(account).deleteShortcut(name) }
        refreshShortcuts(account)
    }

    private suspend fun refreshShortcuts(account: AccountRecord) {
        val list = wrap { api(account).listShortcuts(account.userResourceName) }.shortcuts
        store.write { putShortcuts(account.id, list.map { ShortcutRecord(it.name, it.title, it.filter) }) }
    }

    // MARK: places

    fun setLocation(localId: String, latitude: Double, longitude: Double, placeName: String): Promise<Unit> = promised {
        memos.setLocation(localId, Location(placeName, latitude, longitude))
    }

    fun clearLocation(localId: String): Promise<Unit> = promised { memos.setLocation(localId, null) }

    fun locatedMemos(): Array<PlacedMemo> {
        val account = active ?: return emptyArray()
        return visible(account).sortedByDescending { it.createTime }.mapNotNull { memo ->
            val place = memo.location ?: return@mapNotNull null
            PlacedMemo(memo.toRow(), place.placeholder, place.latitude, place.longitude, memo.createTime.toLocalDateTime(zone()).date.toString())
        }.toTypedArray()
    }

    /** Located memos ordered by distance from where the browser says it is. */
    fun nearby(latitude: Double, longitude: Double): Array<NearbyRow> = locatedMemos()
        .map { NearbyRow(it.row, it.placeName, distanceMetres(latitude, longitude, it.latitude, it.longitude)) }
        .sortedBy { it.metres }.toTypedArray()

    private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    // MARK: tasks and review

    /** Every unticked task, grouped by memo, soonest due first and undated last. */
    fun openTasks(): Array<TaskGroup> {
        val account = active ?: return emptyArray()
        val today = today()
        return visible(account)
            .filter { it.state == MemoState.NORMAL && it.hasIncompleteTasks && !it.isLocked }
            .sortedWith(compareByDescending<Memo> { it.pinned }.thenByDescending { it.updateTime })
            .map { memo ->
                val tasks = TaskLine.openTasks(memo.displayContent).map { open ->
                    val due = DueDateParser.parse(open.text, today)?.date
                    Triple(TaskRow(memo.localId, open.lineIndex, open.text, due?.let { WebLabels.dueDates.label(it, today) }, due != null && due < today), due, Unit)
                }
                Triple(memo, tasks, tasks.mapNotNull { it.second }.minOrNull())
            }
            .filter { it.second.isNotEmpty() }
            .sortedWith(compareBy<Triple<Memo, List<Triple<TaskRow, LocalDate?, Unit>>, LocalDate?>> { it.third == null }.thenBy { it.third?.toEpochDays() ?: 0 }.thenBy { it.first.listTitle() })
            .map { (memo, tasks, _) -> TaskGroup(memo.localId, memo.listTitle(), tasks.map { it.first }.toTypedArray()) }
            .toTypedArray()
    }

    /** How many days in a row have something written, counting back from today. */
    fun streak(): Int {
        val account = active ?: return 0
        return Digest.streak(activeDaySet(account), today())
    }

    /** One entry per day that has any writing, oldest first, as yyyy-MM-dd. */
    fun activeDays(): Array<String> {
        val account = active ?: return emptyArray()
        return activeDaySet(account).map { it.toString() }.sorted().toTypedArray()
    }

    /** Memos written on this day in earlier months and years. */
    fun onThisDay(): Array<Throwback> {
        val account = active ?: return emptyArray()
        val today = today()
        val days = activeDaySet(account).filter { it != today && it.day == today.day && it < today }.sortedDescending()
        return days.flatMap { day ->
            val years = today.year - day.year
            val months = years * 12 + (today.month.ordinal - day.month.ordinal)
            createdOn(account, day).map { memo ->
                Throwback(memo.toRow(), if (years >= 1) "$years year${if (years == 1) "" else "s"} ago" else "$months month${if (months == 1) "" else "s"} ago")
            }
        }.toTypedArray()
    }

    // MARK: templates, reminders, recurring, digest

    fun templates(): Array<TemplateRow> = store.templates.sortedBy { it.sortOrder }.map { TemplateRow(it.id, it.title, it.body) }.toTypedArray()

    private suspend fun seedTemplates() {
        store.write { putTemplates(Templates.defaults.mapIndexed { i, (t, b) -> TemplateRecord(i + 1, t, b, i) }) }
    }

    fun saveTemplate(id: Int, title: String, body: String): Promise<Unit> = promised {
        val list = store.templates
        val next = if (id > 0 && list.any { it.id == id }) {
            list.map { if (it.id == id) it.copy(title = title, body = body) else it }
        } else {
            list + TemplateRecord((list.maxOfOrNull { it.id } ?: 0) + 1, title, body, list.size)
        }
        store.write { putTemplates(next) }
    }

    fun deleteTemplate(id: Int): Promise<Unit> = promised { store.write { putTemplates(store.templates.filter { it.id != id }) } }

    /** A template's body with today's date and time written in, ready to edit. */
    fun expandTemplate(body: String): String = Templates.expand(body, WebLabels.templateValues())

    private fun configRecord(account: AccountRecord): MemoRecord? = store.memosFor(account.id)
        .filter { it.tags.any { t -> t.contains(ConfigCodec.TAG) } && (it.creator == null || it.creator == account.userResourceName) && it.syncStatus != SyncStatus.PENDING_DELETE.name }
        .minByOrNull { it.createTimeEpochMs }

    private fun config(): AppConfig = active?.let { configRecord(it) }?.let { ConfigCodec.decode(it.content) } ?: AppConfig()

    /** Applies [change] to the config memo, creating it on first use. Last writer wins, as in the apps. */
    private suspend fun updateConfig(change: (AppConfig) -> AppConfig) = configWrites.withLock {
        val account = requireActive()
        val existing = configRecord(account)
        val text = ConfigCodec.encode(change(existing?.let { ConfigCodec.decode(it.content) } ?: AppConfig()))
        if (existing == null) memos.create(account.id, text, Visibility.PRIVATE)
        else if (existing.content != text) memos.updateContent(existing.localId, text)
    }

    fun reminders(): Array<ReminderRow> {
        val account = active ?: return emptyArray()
        val now = Clock.System.now().toEpochMilliseconds()
        return config().reminders.sortedBy { it.atEpochMs }.map { r ->
            val memo = store.memoByRemoteName(account.id, r.memoRemoteName)?.toModel()
            ReminderRow(r.id, r.memoRemoteName, memo?.localId.orEmpty(), memo?.listTitle().orEmpty(), r.note, r.atEpochMs.toDouble(),
                Instant.fromEpochMilliseconds(r.atEpochMs).friendlyWithTime(), r.atEpochMs <= now)
        }.toTypedArray()
    }

    /** False for a memo the server has not seen yet: reminders travel by server name. */
    fun addReminder(memoLocalId: String, atEpochMs: Double, note: String): Promise<Boolean> = promised {
        val remote = store.memo(memoLocalId)?.remoteName ?: return@promised false
        updateConfig { it.copy(reminders = it.reminders + Reminder(Uuid.random().toString(), remote, atEpochMs.toLong(), note)) }
        true
    }

    fun removeReminder(id: String): Promise<Unit> = promised { updateConfig { c -> c.copy(reminders = c.reminders.filterNot { it.id == id }) } }

    fun recurring(): Array<RecurringRow> {
        val byTitle = config().recurring.associateBy { it.templateTitle }
        val now = Clock.System.now()
        return templates().map { t ->
            val e = byTitle[t.title]
            val hour = e?.hour ?: 8
            val minute = e?.minute ?: 0
            RecurringRow(t.id, t.title, hour, minute, e?.enabled == true,
                if (e?.enabled == true) Schedule.nextDaily(hour, minute, now, zone()).friendlyWithTime() else "")
        }.toTypedArray()
    }

    fun setRecurring(templateTitle: String, hour: Int, minute: Int, enabled: Boolean): Promise<Unit> = promised {
        updateConfig { c -> c.copy(recurring = c.recurring.filterNot { it.templateTitle == templateTitle } + RecurringTemplate(templateTitle, hour, minute, enabled)) }
    }

    fun weeklyDigest(): Boolean = config().weeklyDigest

    fun setWeeklyDigest(enabled: Boolean): Promise<Unit> = promised { updateConfig { it.copy(weeklyDigest = enabled) } }

    fun digestNextLabel(): String =
        if (!config().weeklyDigest) "" else Schedule.nextWeekly(DayOfWeek.SUNDAY, DIGEST_HOUR, 0, Clock.System.now(), zone()).friendlyWithTime()

    fun digestText(): String? {
        val account = active ?: return null
        val all = visible(account).filter { !it.isComment }
        return Digest.text(Digest.summarise(all, activeDaySet(account), today(), zone()), WebLabels.digest)
    }

    /**
     * Runs any recurring template whose time has passed without one written today, and
     * returns the titles it created. A browser runs nothing while the tab is closed, so this
     * is called when the page opens and on a timer while it is open.
     */
    fun runDueRecurring(): Promise<Array<String>> = promised {
        val account = active ?: return@promised emptyArray()
        val today = today()
        val now = Clock.System.now()
        val saved = store.templates.associateBy { it.title }
        val created = mutableListOf<String>()
        for (entry in config().recurring.filter { it.enabled }) {
            val template = saved[entry.templateTitle] ?: continue
            if (today.atTime(LocalTime(entry.hour, entry.minute)).toInstant(zone()) > now) continue
            val body = Templates.expand(template.body, WebLabels.templateValues())
            val writtenToday = createdOn(account, today).map { Schedule.firstLine(it.content) }
            if (!Schedule.shouldCreate(Schedule.firstLine(body), writtenToday)) continue
            memos.create(account.id, body, Visibility.PRIVATE)
            created += template.title
        }
        created.toTypedArray()
    }

    /**
     * Reminders that have come due and the weekly digest if one is owed, each returned once.
     * The page shows them as notifications; reminders reach every signed-in device this way,
     * as they do in the apps, but only while a tab is open.
     */
    fun dueNotices(): Promise<Array<DueNotice>> = promised {
        val account = active ?: return@promised emptyArray()
        val now = Clock.System.now()
        val prefs = store.prefs
        val notices = mutableListOf<DueNotice>()
        val due = config().reminders.filter { it.atEpochMs <= now.toEpochMilliseconds() && it.id !in prefs.firedReminders }
        for (r in due) {
            val memo = store.memoByRemoteName(account.id, r.memoRemoteName)?.toModel()
            notices += DueNotice(r.id, memo?.listTitle() ?: "Reminder", r.note, memo?.localId)
        }
        val lastDue = (Schedule.nextWeekly(DayOfWeek.SUNDAY, DIGEST_HOUR, 0, now, zone()) - 7.days).toEpochMilliseconds()
        val digestOwed = config().weeklyDigest && prefs.lastDigestShownEpochMs < lastDue
        if (digestOwed) digestText()?.let { notices += DueNotice("digest-$lastDue", "Your week", it, null) }
        if (due.isNotEmpty() || digestOwed) {
            store.write {
                putPrefs(store.prefs.copy(
                    firedReminders = (store.prefs.firedReminders + due.map { it.id }).takeLast(200),
                    lastDigestShownEpochMs = if (digestOwed) lastDue else store.prefs.lastDigestShownEpochMs,
                ))
            }
        }
        notices.toTypedArray()
    }

    // MARK: tag styles

    fun emojiCatalogue(): Array<EmojiGroup> = EmojiCatalogue.categories.map { (n, e) -> EmojiGroup(n, e.toTypedArray()) }.toTypedArray()

    fun tagStyles(): Array<TagStyleRow> =
        config().tagStyles.map { (tag, s) -> TagStyleRow(tag, s.emoji, s.colour?.name, s.colour?.hex?.toInt() ?: -1) }.toTypedArray()

    /** Sets a tag's emoji and colour; the colour is mirrored to the server's tag setting too. */
    fun setTagStyle(tag: String, emoji: String?, colourName: String?): Promise<Unit> = promised {
        val colour = colourName?.let { n -> NoteColour.entries.firstOrNull { it.name == n } }
        val style = TagStyle(emoji?.takeIf { it.isNotBlank() }, colour)
        updateConfig { it.copy(tagStyles = if (style.emoji == null && style.colour == null) it.tagStyles - tag else it.tagStyles + (tag to style)) }
        val account = active ?: return@promised
        runCatching {
            val api = api(account)
            val name = "${account.userResourceName}/settings/TAGS"
            val current = runCatching { api.getUserSetting(name).tagsSetting?.tags }.getOrNull().orEmpty().toMutableMap()
            if (colour == null) current.remove(tag) else {
                val hex = colour.hex
                current[tag] = TagMetadataDto(ColorDto(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f))
            }
            api.updateUserSetting(name, UserSettingDto(name = name, tagsSetting = TagsSettingDto(current)), "tags")
        }
    }

    // MARK: the account on the server (online only)

    fun profile(): Promise<ProfileRow> = promised {
        val account = requireActive()
        val u = wrap { api(account).getUser(account.userResourceName) }
        ProfileRow(u.name, u.username, u.displayName, u.email, u.description, u.role == "ADMIN" || u.role == "HOST")
    }

    fun updateProfile(displayName: String, about: String, email: String): Promise<Unit> = promised {
        val account = requireActive()
        val u = wrap {
            api(account).updateUser(account.userResourceName,
                UserWriteDto(name = account.userResourceName, displayName = displayName, description = about, email = email), "display_name,description,email")
        }
        store.write { updateAccount(account.copy(displayName = u.displayName.ifEmpty { u.username }, avatarUrl = u.avatarUrl)) }
    }

    fun changePassword(newPassword: String): Promise<Unit> = promised {
        val account = requireActive()
        wrap { api(account).updateUser(account.userResourceName, UserWriteDto(name = account.userResourceName, password = newPassword), "password") }
        Unit
    }

    fun defaultVisibility(): Promise<String> = promised {
        val account = requireActive()
        val s = runCatching { api(account).getUserSetting("${account.userResourceName}/settings/GENERAL").generalSetting }.getOrNull()
        runCatching { Visibility.valueOf(s?.memoVisibility.orEmpty()) }.getOrDefault(Visibility.PRIVATE).name
    }

    fun setDefaultVisibility(visibility: String): Promise<Unit> = promised {
        val account = requireActive()
        val name = "${account.userResourceName}/settings/GENERAL"
        wrap { api(account).updateUserSetting(name, UserSettingDto(name = name, generalSetting = UserGeneralSettingDto(memoVisibility = visibilityOf(visibility).name)), "general_setting.memo_visibility") }
        Unit
    }

    fun tokens(): Promise<Array<TokenRow>> = promised {
        val account = requireActive()
        wrap { api(account).listPersonalAccessTokens(account.userResourceName) }.personalAccessTokens.map {
            TokenRow(it.name, it.description, parseEpochMs(it.createdAt).friendly(),
                it.expiresAt?.let { e -> parseEpochMs(e).friendly() } ?: "Never",
                it.lastUsedAt?.let { e -> parseEpochMs(e).friendly() } ?: "Never",
                it.name == account.mintedTokenName)
        }.toTypedArray()
    }

    /** Returns the raw token; the server never shows it again. */
    fun createToken(label: String, expiresInDays: Int): Promise<String> = promised {
        val account = requireActive()
        wrap { api(account).createPersonalAccessToken(account.userResourceName, CreatePersonalAccessTokenRequestDto(label, expiresInDays.takeIf { it > 0 })) }.token
    }

    fun deleteToken(name: String): Promise<Unit> = promised { wrap { api(requireActive()).deletePersonalAccessToken(name) } }

    fun webhooks(): Promise<Array<WebhookRow>> = promised {
        val account = requireActive()
        wrap { api(account).listUserWebhooks(account.userResourceName) }.webhooks.map { WebhookRow(it.name, it.displayName, it.url, parseEpochMs(it.createTime).friendly()) }.toTypedArray()
    }

    fun createWebhook(displayName: String, url: String): Promise<Unit> = promised {
        val account = requireActive()
        wrap { api(account).createUserWebhook(account.userResourceName, UserWebhookDto(url = url, displayName = displayName)) }
        Unit
    }

    fun deleteWebhook(name: String): Promise<Unit> = promised { wrap { api(requireActive()).deleteUserWebhook(name) } }

    fun notifications(): Promise<Array<NotificationRow>> = promised {
        val account = requireActive()
        val list = wrap { api(account).listUserNotifications(account.userResourceName) }.notifications.map { n ->
            val payload = n.memoComment ?: n.memoMention
            NotificationRow(n.name, n.senderUser?.username ?: n.sender.substringAfterLast('/'), n.status == "UNREAD",
                parseEpochMs(n.createTime).friendly(), n.type,
                payload?.relatedMemo?.ifEmpty { payload.memo }.orEmpty(), payload?.memoSnippet.orEmpty(), payload?.relatedMemoSnippet.orEmpty())
        }
        unread = list.count { it.unread }
        list.toTypedArray()
    }

    fun unreadNotifications(): Int = unread

    fun refreshUnread(): Promise<Int> = promised {
        runCatching { notifications().await2() }
        unread
    }

    fun markNotificationRead(name: String): Promise<Unit> = promised {
        wrap { api(requireActive()).updateUserNotification(name, NotificationWriteDto(name, "ARCHIVED")) }
        unread = (unread - 1).coerceAtLeast(0)
    }

    fun deleteNotification(name: String): Promise<Unit> = promised { wrap { api(requireActive()).deleteUserNotification(name) } }

    fun stats(): Promise<StatsRow> = promised {
        val account = requireActive()
        val s = wrap { api(account).getUserStats(account.userResourceName) }
        StatsRow(s.totalMemoCount, s.memoTypeStats?.linkCount ?: 0, s.memoTypeStats?.codeCount ?: 0, s.memoTypeStats?.todoCount ?: 0,
            s.memoTypeStats?.undoCount ?: 0, s.tagCount.entries.sortedByDescending { it.value }.map { TagCount(it.key, it.value) }.toTypedArray(),
            s.memoCreatedTimestamps.map { Instant.parse(it).toLocalDateTime(zone()).date }.toSet().size)
    }

    // MARK: admin (online only)

    fun users(): Promise<Array<UserRow>> = promised {
        wrap { api(requireActive()).listUsers() }.users.map {
            UserRow(it.name, it.username, it.displayName, it.email, it.role == "ADMIN" || it.role == "HOST", it.state == "ARCHIVED")
        }.toTypedArray()
    }

    fun createUser(username: String, password: String, admin: Boolean): Promise<Unit> = promised {
        wrap { api(requireActive()).createUser(UserWriteDto(username = username, password = password, role = if (admin) "ADMIN" else "USER")) }
        Unit
    }

    fun setUserArchived(name: String, archived: Boolean): Promise<Unit> = promised {
        wrap { api(requireActive()).updateUser(name, UserWriteDto(name = name, state = if (archived) "ARCHIVED" else "NORMAL"), "state") }
        Unit
    }

    fun deleteUser(name: String): Promise<Unit> = promised { wrap { api(requireActive()).deleteUser(name) } }

    fun instanceGeneral(): Promise<InstanceRow> = promised {
        val g = wrap { api(requireActive()).getInstanceSetting("instance/settings/GENERAL") }.generalSetting ?: InstanceGeneralSettingDto()
        InstanceRow(g.customProfile?.title.orEmpty(), g.customProfile?.description.orEmpty(), g.disallowUserRegistration, g.disallowPasswordAuth,
            g.disallowChangeUsername, g.disallowChangeNickname, g.weekStartDayOffset)
    }

    fun updateInstanceGeneral(row: InstanceRow): Promise<Unit> = promised {
        val api = api(requireActive())
        val current = wrap { api.getInstanceSetting("instance/settings/GENERAL") }.generalSetting ?: InstanceGeneralSettingDto()
        wrap {
            api.updateInstanceSetting(
                "instance/settings/GENERAL",
                InstanceSettingDto(
                    name = "instance/settings/GENERAL",
                    generalSetting = current.copy(
                        disallowUserRegistration = row.disallowRegistration, disallowPasswordAuth = row.disallowPasswordAuth,
                        disallowChangeUsername = row.disallowChangeUsername, disallowChangeNickname = row.disallowChangeNickname,
                        weekStartDayOffset = row.weekStartDayOffset,
                        customProfile = CustomProfileDto(row.title, row.about, current.customProfile?.logoUrl.orEmpty()),
                    ),
                ),
                "general_setting",
            )
        }
        Unit
    }

    fun instanceStats(): Promise<InstanceStatsRow> = promised {
        val s = wrap { api(requireActive()).getInstanceStats() }
        InstanceStatsRow(s.database?.driver.orEmpty(), (s.database?.sizeBytes ?: 0).toDouble(), s.localStorageBytes.toDouble())
    }

    // MARK: your data

    /** Every memo as Markdown with front matter, plus the attachment files held here, zipped. */
    fun exportMarkdown(): Promise<Uint8Array> = promised { DataTransfer(store, memos).export(requireActive()).toUint8() }

    /** Markdown files, or zips of them, as memos. Private unless a file's front matter says otherwise. */
    fun importMarkdown(files: Array<PickedFile>): Promise<ImportResultRow> = promised {
        DataTransfer(store, memos).import(requireActive(), files.toList())
    }

    /**
     * This browser's copy of the active account (memos, outbox, attachments held here,
     * templates and view settings), zipped and encrypted with [password]. The apps' backups
     * are their SQLite databases and cannot be read here, nor these there; the server is the
     * thing all of them share.
     */
    fun backup(password: String): Promise<Uint8Array> = promised {
        WebCipher.encryptBackup(DataTransfer(store, memos).backup(requireActive()), password).toUint8()
    }

    /** Replaces the active account's local copy with a backup. Nothing changes on a wrong password. */
    fun restore(bytes: Uint8Array, password: String): Promise<Unit> = promised {
        val plain = WebCipher.decryptBackup(bytes.toByteArray(), password)
        DataTransfer(store, memos).restore(requireActive(), plain)
        scheduler.syncNow()
    }

    // MARK: helpers

    private fun visibilityOf(name: String) = runCatching { Visibility.valueOf(name.uppercase()) }.getOrDefault(Visibility.PRIVATE)

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw ApiException.from(e, json)
    }

    /**
     * The type an object URL is created with. A blob URL opened as a page runs in this
     * origin, which holds the access token, so only raster images keep their own type: HTML,
     * SVG and anything else the server labels arrive as an opaque download.
     */
    private fun displayableType(mime: String): String =
        mime.lowercase().substringBefore(';').trim().takeIf { it in RASTER_TYPES } ?: "application/octet-stream"

    private fun isWebUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private fun objectUrl(bytes: ByteArray, mime: String): String {
        val u8 = bytes.toUint8()
        return js("URL.createObjectURL(new Blob([u8], { type: mime }))") as String
    }

    private fun encodeUri(s: String): String = js("encodeURIComponent(s)") as String

    private fun deviceName(): String {
        val ua = js("(globalThis.navigator && navigator.userAgent) || ''") as String
        val browser = when {
            "Firefox/" in ua -> "Firefox"
            "Edg/" in ua -> "Edge"
            "Chrome/" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> "a browser"
        }
        val os = when {
            "iPhone" in ua || "iPad" in ua -> "iOS"
            "Android" in ua -> "Android"
            "Mac OS X" in ua -> "macOS"
            "Windows" in ua -> "Windows"
            "Linux" in ua -> "Linux"
            else -> null
        }
        return if (os != null) "$browser on $os (web)" else "$browser (web)"
    }

    private fun sessionStorage(): dynamic = js("(typeof sessionStorage !== 'undefined') ? sessionStorage : null")

    private fun sessionPassword(): String? = runCatching { sessionStorage()?.getItem(PASSWORD_KEY) as String? }.getOrNull()
}

private const val PASSWORD_KEY = "mymemos.memoPassword"
private val RASTER_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "image/bmp")
/** Avatars are decoded by an `<img>`, which sniffs raster formats; never a page-renderable type. */
private const val IMAGE_FALLBACK = "image/png"
private val attachmentName = Regex("^attachments/[A-Za-z0-9_-]+$")
private const val TOKEN_LIFETIME_DAYS = 90
/** Sunday evening, late enough that the week is genuinely over. */
private const val DIGEST_HOUR = 18

private suspend fun <T> Promise<T>.await2(): T = await()
