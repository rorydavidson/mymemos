package com.keltruc.mymemos.data.text

/**
 * Which `#tags` a memo carries. Lives apart from MemoRepository so clients that cannot load
 * Room, the web one, read tags exactly as the apps do.
 */
object Tags {
    private val tagRegex = Regex("(?<![\\w/])#([\\p{L}\\p{N}_/\\-]+)")

    fun extract(content: String): List<String> =
        tagRegex.findAll(content).map { it.groupValues[1].trimEnd('/', '-') }
            .filter { it.isNotEmpty() && !ColourTag.isColourTag(it) }.distinct().toList()
}
