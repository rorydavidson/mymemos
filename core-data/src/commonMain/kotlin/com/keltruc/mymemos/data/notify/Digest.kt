package com.keltruc.mymemos.data.notify

import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.model.Memo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/**
 * The Sunday evening summary of the week, worked out entirely from the local database.
 *
 * Counting and choosing is here; the sentence is [Labels], because "3 memos" and "1 memo" are
 * not a plural rule that holds outside English and the platform already owns that.
 */
object Digest {

    /** What the week came to. */
    data class Summary(
        val written: Int,
        val tasksClosed: Int,
        val streak: Int,
        /** A few old memos worth reading again, already reduced to one line each. */
        val revisit: List<String>,
    ) {
        val isEmpty: Boolean get() = written == 0 && tasksClosed == 0 && revisit.isEmpty()
    }

    /** Supplies the wording. Implemented per platform, against its own string resources. */
    interface Labels {
        /** "3 memos written, 5 tasks closed" */
        fun written(count: Int): String
        fun tasksClosed(count: Int): String
        /** "a 12 day streak" */
        fun streak(days: Int): String
        /** The line introducing [Summary.revisit]. */
        fun worthALookAgain(): String
        fun nothingThisWeek(): String
    }

    fun summarise(
        all: List<Memo>,
        activeDays: Set<LocalDate>,
        today: LocalDate,
        zone: TimeZone,
        random: Random = Random.Default,
    ): Summary {
        val weekAgo = today.minus(7, DateTimeUnit.DAY)
        val written = all.count { it.createTime.toLocalDateTime(zone).date > weekAgo }
        // Touched this week, not written this week: ticking a task off an old memo counts, and
        // that is most of them.
        val tasksClosed = all
            .filter { it.updateTime.toLocalDateTime(zone).date > weekAgo }
            .sumOf { memo -> memo.content.lines().count { it.trimStart().startsWith("- [x]", ignoreCase = true) } }

        val monthAgo = today.minus(30, DateTimeUnit.DAY)
        val revisit = all
            .filter { it.createTime.toLocalDateTime(zone).date < monthAgo && !it.isLocked }
            .shuffled(random)
            .take(3)
            .mapNotNull { memo -> MemoTitle.of(memo.displayContent)?.take(60) }

        return Summary(written, tasksClosed, streak(activeDays, today), revisit)
    }

    /**
     * Consecutive days up to and including today with something written on them.
     *
     * Today not having a memo yet does not break the streak: the digest goes out on Sunday
     * evening, and a run counted from yesterday is the honest answer at that point.
     */
    fun streak(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minus(1, DateTimeUnit.DAY)
        var count = 0
        while (day in activeDays) {
            count++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    fun text(summary: Summary, labels: Labels): String {
        if (summary.isEmpty) return labels.nothingThisWeek()
        return buildString {
            append(labels.written(summary.written))
            append(", ").append(labels.tasksClosed(summary.tasksClosed))
            if (summary.streak > 1) append(", ").append(labels.streak(summary.streak))
            append(".")
            if (summary.revisit.isNotEmpty()) {
                append("\n\n").append(labels.worthALookAgain())
                summary.revisit.forEach { append("\n• ").append(it) }
            }
        }
    }
}
