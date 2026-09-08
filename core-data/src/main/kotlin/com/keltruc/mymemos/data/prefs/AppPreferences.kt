package com.keltruc.mymemos.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

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
)

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val sortCompleted = booleanPreferencesKey("sort_completed_tasks")
    private val dynamic = booleanPreferencesKey("dynamic_colour")
    private val tiles = booleanPreferencesKey("map_tiles")
    private val collapsed = stringSetPreferencesKey("collapsed_groups")
    private val compact = booleanPreferencesKey("compact_list")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            sortCompletedTasks = p[sortCompleted] ?: false,
            dynamicColour = p[dynamic] ?: false,
            mapTiles = p[tiles] ?: false,
            collapsedGroups = p[collapsed].orEmpty(),
            compactList = p[compact] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setSortCompletedTasks(enabled: Boolean) {
        context.dataStore.edit { it[sortCompleted] = enabled }
    }

    suspend fun setDynamicColour(enabled: Boolean) {
        context.dataStore.edit { it[dynamic] = enabled }
    }

    suspend fun setMapTiles(enabled: Boolean) {
        context.dataStore.edit { it[tiles] = enabled }
    }

    suspend fun setCompactList(enabled: Boolean) {
        context.dataStore.edit { it[compact] = enabled }
    }

    suspend fun setGroupCollapsed(key: String, collapsedNow: Boolean) {
        context.dataStore.edit { p ->
            val current = p[collapsed].orEmpty()
            p[collapsed] = if (collapsedNow) current + key else current - key
        }
    }

    /** When the last full reconcile of server memo names ran for this account, or 0. */
    suspend fun lastReconcile(accountId: Long): Long =
        context.dataStore.data.first()[longPreferencesKey("last_reconcile_$accountId")] ?: 0L

    suspend fun setLastReconcile(accountId: Long, epochMs: Long) {
        context.dataStore.edit { it[longPreferencesKey("last_reconcile_$accountId")] = epochMs }
    }
}
