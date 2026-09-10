package com.keltruc.mymemos.data.text

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Instant

/**
 * The model layer carries `kotlin.time.Instant` so it can be shared with a macOS build, while
 * the parts of this module that format dates for people still use `java.time`, which is the
 * only thing on the JVM that knows a Welsh month name.
 *
 * These are the two places those meet. They go away once the grouping and export code is
 * split into portable date arithmetic and platform-specific formatting.
 */
internal fun Instant.atZone(zone: ZoneId): ZonedDateTime =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(zone)

internal fun Long.toLocalDateIn(zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
