package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.dao.TemplateDao
import com.keltruc.mymemos.database.entity.TemplateEntity
import com.keltruc.mymemos.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
}
