package com.keltruc.mymemos.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs on the JVM and on Kotlin/Native on purpose: the two regex engines disagree about a
 * trailing hyphen inside a character class, and a tag that came out as "follow" on one
 * platform and "follow-up" on the other would quietly fork the tag list between devices.
 */
class TagExtractionTest {
    @Test
    fun hyphensUnderscoresAndSlashesStayInTags() {
        assertEquals(listOf("follow-up", "work/projects", "a_b"), MemoRepository.extractTags("#follow-up then #work/projects and #a_b."))
    }

    @Test
    fun unicodeAndDigitsAreTags() {
        assertEquals(listOf("café", "2026"), MemoRepository.extractTags("#café #2026"))
    }

    @Test
    fun aHashInsideAWordIsNotATag() {
        assertEquals(emptyList(), MemoRepository.extractTags("issue#12 and a/#path"))
    }
}
