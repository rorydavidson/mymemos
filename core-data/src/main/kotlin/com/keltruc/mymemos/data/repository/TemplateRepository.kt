package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.dao.TemplateDao
import com.keltruc.mymemos.database.entity.TemplateEntity
import com.keltruc.mymemos.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TemplateRepository constructor(private val dao: TemplateDao) {
    val templates: Flow<List<Template>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() > 0) return
        defaults.forEachIndexed { i, (title, body) -> dao.upsert(TemplateEntity(title = title, body = body, sortOrder = i)) }
    }

    suspend fun save(id: Long?, title: String, body: String) {
        dao.upsert(TemplateEntity(id = id ?: 0, title = title, body = body))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        /** Expands {{date}}, {{time}}, {{weekday}}, {{year}}, {{month}} at insert time. */
        fun expand(body: String, date: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now(), locale: Locale = Locale.getDefault()): String =
            body
                .replace("{{date}}", date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)))
                .replace("{{isodate}}", date.toString())
                .replace("{{time}}", time.format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{{weekday}}", date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                .replace("{{month}}", date.month.getDisplayName(TextStyle.FULL, locale))
                .replace("{{year}}", date.year.toString())

        val defaults = listOf(
            "Daily journal" to "# {{weekday}} {{date}}\n\n**Grateful for**\n- \n\n**Today**\n- [ ] \n\n**Notes**\n\n#journal",
            "Meeting" to "# Meeting: \n{{date}} {{time}}\n\n**Attendees**\n- \n\n**Decisions**\n- \n\n**Actions**\n- [ ] \n\n#meeting",
            "Weekly review" to "# Week of {{date}}\n\n**Went well**\n- \n\n**Could improve**\n- \n\n**Next week**\n- [ ] \n\n#review",
        )
    }
}
