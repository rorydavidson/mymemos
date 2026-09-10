package com.keltruc.mymemos.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    /** Move ticked task lines below the unticked ones whenever a memo is saved. */
    val sortCompletedTasks: Boolean = false,
    /** Use Android 12+ wallpaper colours instead of the app palette. */
    val dynamicColour: Boolean = false,
    /** Fetch OpenStreetMap tiles for memo locations, which tells openstreetmap.org roughly where you are. */
    val mapTiles: Boolean = false,
    /** Timeline headers the user has folded away, by [TimelineGrouping.Group.key]. */
    val collapsedGroups: Set<String> = emptySet(),
    /** Show the timeline as one-line rows instead of cards. */
    val compactList: Boolean = false,
    /**
     * Servers signed into before, newest first. Kept so that a sign-out, wanted or not, does not
     * mean retyping the address. Only the address: no username, password or token is stored here.
     */
    val knownServers: List<String> = emptyList(),
    /**
     * Order the timeline by when memos were last changed rather than when they were written. The
     * dates shown on cards and at the top of the detail screen follow the same choice, so what you
     * are sorting on is always the date you can see.
     */
    val sortByModified: Boolean = false,
)

/** Where the file lives is the platform's business; what is in it is not. */
class AppPreferences(private val dataStore: DataStore<Preferences>) {
    private val sortCompleted = booleanPreferencesKey("sort_completed_tasks")
    private val dynamic = booleanPreferencesKey("dynamic_colour")
    private val tiles = booleanPreferencesKey("map_tiles")
    private val collapsed = stringSetPreferencesKey("collapsed_groups")
    private val compact = booleanPreferencesKey("compact_list")
    private val byModified = booleanPreferencesKey("sort_by_modified")
    // Newline separated because a URL cannot contain one, and a Set would lose the ordering.
    private val servers = stringPreferencesKey("known_servers")

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            sortCompletedTasks = p[sortCompleted] ?: false,
            dynamicColour = p[dynamic] ?: false,
            mapTiles = p[tiles] ?: false,
            collapsedGroups = p[collapsed].orEmpty(),
            compactList = p[compact] ?: false,
            sortByModified = p[byModified] ?: false,
            knownServers = p[servers].orEmpty().lines().filter { it.isNotBlank() },
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setSortCompletedTasks(enabled: Boolean) {
        dataStore.edit { it[sortCompleted] = enabled }
    }

    suspend fun setDynamicColour(enabled: Boolean) {
        dataStore.edit { it[dynamic] = enabled }
    }

    suspend fun setMapTiles(enabled: Boolean) {
        dataStore.edit { it[tiles] = enabled }
    }

    suspend fun setCompactList(enabled: Boolean) {
        dataStore.edit { it[compact] = enabled }
    }

    suspend fun setSortByModified(enabled: Boolean) {
        dataStore.edit { it[byModified] = enabled }
    }

    /** Records a server that was signed into, newest first, keeping the most recent few. */
    suspend fun rememberServer(url: String) {
        if (url.isBlank()) return
        dataStore.edit { p ->
            val existing = p[servers].orEmpty().lines().filter { it.isNotBlank() && it != url }
            p[servers] = (listOf(url) + existing).take(MAX_REMEMBERED_SERVERS).joinToString("\n")
        }
    }

    suspend fun forgetServer(url: String) {
        dataStore.edit { p ->
            p[servers] = p[servers].orEmpty().lines().filter { it.isNotBlank() && it != url }.joinToString("\n")
        }
    }

    suspend fun setGroupCollapsed(key: String, collapsedNow: Boolean) {
        dataStore.edit { p ->
            val current = p[collapsed].orEmpty()
            p[collapsed] = if (collapsedNow) current + key else current - key
        }
    }

    private companion object {
        const val MAX_REMEMBERED_SERVERS = 5
    }

    /** When the last full reconcile of server memo names ran for this account, or 0. */
    suspend fun lastReconcile(accountId: Long): Long =
        dataStore.data.first()[longPreferencesKey("last_reconcile_$accountId")] ?: 0L

    suspend fun setLastReconcile(accountId: Long, epochMs: Long) {
        dataStore.edit { it[longPreferencesKey("last_reconcile_$accountId")] = epochMs }
    }
}
