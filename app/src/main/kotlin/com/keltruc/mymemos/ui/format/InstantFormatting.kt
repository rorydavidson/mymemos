package com.keltruc.mymemos.ui.format

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Instant

/**
 * The model layer carries `kotlin.time.Instant` so it can be shared with a macOS build. The UI
 * formats dates with `java.time`, which is what knows how to write a month name in the user's
 * language, so this is where the two meet.
 */
fun Instant.atZone(zone: ZoneId): ZonedDateTime =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(zone)
