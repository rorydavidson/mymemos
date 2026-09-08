package com.keltruc.mymemos.model

import java.time.Instant

data class Reaction(
    val localId: String,
    val remoteName: String?,
    val creator: String,
    val reactionType: String,
    val createTime: Instant,
)

/** Outgoing reference from a memo to [relatedRemoteName]. */
data class Reference(
    val relatedRemoteName: String,
    val relatedSnippet: String,
)

data class Shortcut(
    val name: String,
    val title: String,
    val filter: String,
)

data class MemoShare(
    val name: String,
    val url: String,
    val createTime: Instant,
    val expireTime: Instant?,
) {
    val token: String get() = name.substringAfterLast('/')
}
