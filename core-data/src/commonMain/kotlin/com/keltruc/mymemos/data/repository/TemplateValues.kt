package com.keltruc.mymemos.data.repository

/**
 * Today, already written out. A template says {{weekday}}; only the platform knows whether
 * that is "Thursday" or "Iau", so the words are supplied rather than formatted here.
 */
interface TemplateValues {
    val date: String
    val isoDate: String
    val time: String
    val weekday: String
    val month: String
    val year: String
}

/** What a template's placeholders become, and the templates a new device starts with. */
object Templates {
    /**
     * Expands {{date}}, {{time}}, {{weekday}}, {{year}}, {{month}} at insert time.
     *
     * The substitution is here; writing today's date out in the reader's language is
     * [TemplateValues], because month and weekday names come from the platform.
     */
    fun expand(body: String, values: TemplateValues): String =
        body
            .replace("{{date}}", values.date)
            .replace("{{isodate}}", values.isoDate)
            .replace("{{time}}", values.time)
            .replace("{{weekday}}", values.weekday)
            .replace("{{month}}", values.month)
            .replace("{{year}}", values.year)

    val defaults = listOf(
        "Daily journal" to "# {{weekday}} {{date}}\n\n**Grateful for**\n- \n\n**Today**\n- [ ] \n\n**Notes**\n\n#journal",
        "Meeting" to "# Meeting: \n{{date}} {{time}}\n\n**Attendees**\n- \n\n**Decisions**\n- \n\n**Actions**\n- [ ] \n\n#meeting",
        "Weekly review" to "# Week of {{date}}\n\n**Went well**\n- \n\n**Could improve**\n- \n\n**Next week**\n- [ ] \n\n#review",
    )
}
