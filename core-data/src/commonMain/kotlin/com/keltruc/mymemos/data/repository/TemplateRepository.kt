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
        /** Kept here for callers; the rules live in [Templates], where clients without Room reach them. */
        fun expand(body: String, values: TemplateValues): String = Templates.expand(body, values)

        val defaults: List<Pair<String, String>> get() = Templates.defaults
    }
}
