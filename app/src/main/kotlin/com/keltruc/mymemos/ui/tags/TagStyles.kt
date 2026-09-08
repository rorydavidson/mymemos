package com.keltruc.mymemos.ui.tags

import androidx.compose.runtime.compositionLocalOf
import com.keltruc.mymemos.model.TagStyle

/** Tag emoji and colour, available to any composable that renders a tag. */
val LocalTagStyles = compositionLocalOf<Map<String, TagStyle>> { emptyMap() }

/** "💼 work" when the tag has an emoji, else "#work". Parent styles apply to child tags. */
fun Map<String, TagStyle>.label(tag: String): String {
    val style = styleFor(tag)
    return if (style?.emoji != null) "${style.emoji} $tag" else "#$tag"
}

fun Map<String, TagStyle>.styleFor(tag: String): TagStyle? {
    var t = tag
    while (true) {
        this[t]?.let { return it }
        val cut = t.lastIndexOf('/')
        if (cut < 0) return null
        t = t.substring(0, cut)
    }
}
