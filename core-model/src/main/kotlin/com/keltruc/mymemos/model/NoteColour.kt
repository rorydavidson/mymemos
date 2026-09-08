package com.keltruc.mymemos.model

/** The sixteen basic web colours, as a memo tint. Rendered as a pastel of each. */
enum class NoteColour(val hex: Long) {
    WHITE(0xFFFFFF), SILVER(0xC0C0C0), GRAY(0x808080), BLACK(0x000000),
    RED(0xFF0000), MAROON(0x800000), YELLOW(0xFFFF00), OLIVE(0x808000),
    LIME(0x00FF00), GREEN(0x008000), AQUA(0x00FFFF), TEAL(0x008080),
    BLUE(0x0000FF), NAVY(0x000080), FUCHSIA(0xFF00FF), PURPLE(0x800080),
}

data class Template(val id: Long, val title: String, val body: String)
