package com.keltruc.mymemos.web.store

import kotlinx.serialization.Serializable

/**
 * What the web client keeps in IndexedDB. These mirror the Room entities in core-database
 * field for field where they can, so the sync engine port reads like the original. The
 * differences: attachments, reactions and relations ride inside their memo rather than in
 * tables of their own, since IndexedDB has no joins and a memo is always read whole.
 */
@Serializable
data class MemoRecord(
    val localId: String,
    val accountId: Int,
    val remoteName: String?,
    val creator: String?,
    val content: String,
    val visibility: String,
    val state: String,
    val pinned: Boolean,
    val tags: List<String>,
    val createTimeEpochMs: Long,
    val updateTimeEpochMs: Long,
    val snippet: String,
    val hasTaskList: Boolean,
    val hasIncompleteTasks: Boolean,
    val hasLink: Boolean,
    val hasCode: Boolean,
    val locationPlaceholder: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val syncStatus: String,
    val baseUpdateTimeEpochMs: Long?,
    val parent: String? = null,
    val colour: String? = null,
    val attachments: List<AttachmentRecord> = emptyList(),
    val reactions: List<ReactionRecord> = emptyList(),
    val relations: List<RelationRecord> = emptyList(),
)

@Serializable
data class AttachmentRecord(
    val localId: String,
    val remoteName: String?,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val externalLink: String? = null,
    val createTimeEpochMs: Long,
)

@Serializable
data class ReactionRecord(
    val localId: String,
    val remoteName: String?,
    val creator: String,
    val reactionType: String,
    val createTimeEpochMs: Long,
)

/** An outgoing reference, as MemoRelationEntity holds it. */
@Serializable
data class RelationRecord(
    val relatedRemoteName: String,
    val relatedSnippet: String,
    val type: String = "REFERENCE",
)

/** One queued write. The types are PendingOpEntity.Type's, so a reader of one knows the other. */
@Serializable
data class OpRecord(
    val id: Long,
    val accountId: Int,
    val memoLocalId: String,
    val type: String,
    val payloadJson: String = "",
    val attempts: Int = 0,
    val lastError: String? = null,
    val failed: Boolean = false,
) {
    companion object {
        const val CREATE = "CREATE"
        const val UPDATE_CONTENT = "UPDATE_CONTENT"
        const val SET_PINNED = "SET_PINNED"
        const val SET_VISIBILITY = "SET_VISIBILITY"
        const val SET_STATE = "SET_STATE"
        const val DELETE = "DELETE"
        const val ADD_ATTACHMENT = "ADD_ATTACHMENT"
        const val REMOVE_ATTACHMENT = "REMOVE_ATTACHMENT"
        const val CREATE_COMMENT = "CREATE_COMMENT"
        const val UPSERT_REACTION = "UPSERT_REACTION"
        const val DELETE_REACTION = "DELETE_REACTION"
        const val SET_RELATIONS = "SET_RELATIONS"
        const val SET_LOCATION = "SET_LOCATION"
    }
}

/**
 * A signed-in account. [token] is the personal access token minted at sign-in (or pasted by
 * the user). A browser has nothing like the Keychain to put it in, so it lives in IndexedDB
 * for this origin; the strict Content-Security-Policy the container serves is what keeps
 * other script away from it.
 */
@Serializable
data class AccountRecord(
    val id: Int,
    val serverUrl: String,
    val userResourceName: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val role: String = "USER",
    val authMethod: String,
    val serverVersion: String = "",
    val lastSyncEpochMs: Long? = null,
    val lastReconcileEpochMs: Long = 0,
    val token: String,
    /** Set when this client minted [token], so sign-out knows to revoke it. */
    val mintedTokenName: String? = null,
)

@Serializable
data class ShortcutRecord(val name: String, val title: String, val filter: String)

/** Templates are per device, as they are in the apps. */
@Serializable
data class TemplateRecord(val id: Int, val title: String, val body: String, val sortOrder: Int = 0)

/** View preferences. Local to this browser, like the apps' DataStore settings. */
@Serializable
data class Prefs(
    val sortByModified: Boolean = false,
    val compactList: Boolean = false,
    val mapTiles: Boolean = false,
    val sortCompletedTasks: Boolean = false,
    val knownServers: List<String> = emptyList(),
    val folded: List<String> = emptyList(),
    val lastDigestShownEpochMs: Long = 0,
    val firedReminders: List<String> = emptyList(),
)
