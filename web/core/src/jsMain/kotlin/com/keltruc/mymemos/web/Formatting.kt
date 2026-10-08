package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.web.WebLabels.toJsDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

// How a memo is written out for a list. Matches MemosSession's helpers of the same names.

internal fun zone(): TimeZone = TimeZone.currentSystemDefault()

internal fun today() = Clock.System.todayIn(zone())

internal fun Memo.toRow(byModified: Boolean = false): MemoRow {
    val shown = timelineTime(byModified)
    return MemoRow(
        localId = localId,
        title = listTitle(),
        lockedTitle = lockedTitle,
        body = if (isLocked) "" else displayContent,
        bodyBelowTitle = if (isLocked) "" else MemoTitle.withoutTitleLine(displayContent),
        tags = tags.toTypedArray(),
        pinned = pinned,
        locked = isLocked,
        hasTasks = hasTaskList,
        hasOpenTasks = hasIncompleteTasks,
        attachmentCount = attachments.size,
        colourHex = colour?.hex?.toInt() ?: -1,
        timeLabel = shown.timeOfDay(),
        dateLabel = shown.friendly(),
        visibility = visibility.name,
        archived = state == MemoState.ARCHIVED,
        pending = syncStatus != SyncStatus.SYNCED,
        conflict = syncStatus == SyncStatus.CONFLICT,
        hasPlace = location != null,
        isoDate = shown.toLocalDateTime(zone()).date.toString(),
    )
}

/** What a list calls this memo. A locked one shows only the title it was given in the clear. */
internal fun Memo.listTitle(): String =
    if (isLocked) lockedTitle ?: "Locked memo" else MemoTitle.of(displayContent) ?: firstLine()

internal fun Memo.firstLine(): String =
    displayContent.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80).orEmpty()

internal fun Instant.timeOfDay(): String {
    val local = toLocalDateTime(zone())
    return "${local.hour.pad()}:${local.minute.pad()}"
}

/** "8 October 2026 at 12:30", in the reader's language as far as the date goes. */
internal fun Instant.friendly(): String {
    val local = toLocalDateTime(zone())
    val opts: dynamic = js("({ day: 'numeric', month: 'long', year: 'numeric' })")
    val day = js("new Intl.DateTimeFormat(undefined, opts)").format(local.date.toJsDate()) as String
    return "$day at ${local.hour.pad()}:${local.minute.pad()}"
}

/** "Today at 18:00", "Sunday 12 October at 18:00". */
internal fun Instant.friendlyWithTime(): String {
    val local = toLocalDateTime(zone())
    val day = WebLabels.timeline.label(TimelineGrouping.Bucket.Day(local.date), today())
    return "$day at ${local.hour.pad()}:${local.minute.pad()}"
}

internal fun Long.friendly(): String = Instant.fromEpochMilliseconds(this).friendly()

private fun Int.pad() = toString().padStart(2, '0')
