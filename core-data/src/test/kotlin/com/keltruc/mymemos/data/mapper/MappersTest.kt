package com.keltruc.mymemos.data.mapper

import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.MemoPropertyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun `memo dto round-trips through entity to model`() {
        val dto = MemoDto(
            name = "memos/abc",
            content = "hello #work #home",
            visibility = "PROTECTED",
            tags = listOf("work", "home"),
            pinned = true,
            createTime = "2026-09-08T06:00:00Z",
            updateTime = "2026-09-08T07:30:00Z",
            property = MemoPropertyDto(hasTaskList = true, hasIncompleteTasks = true),
        )
        val entity = dto.toEntity(accountId = 7, existingLocalId = "local-1")
        val model = entity.toModel()

        assertEquals("local-1", model.localId)
        assertEquals("memos/abc", model.remoteName)
        assertEquals(listOf("work", "home"), model.tags)
        assertEquals(Visibility.PROTECTED, model.visibility)
        assertTrue(model.pinned)
        assertTrue(model.hasIncompleteTasks)
        assertEquals(SyncStatus.SYNCED, model.syncStatus)
        assertEquals(entity.updateTimeEpochMs, entity.baseUpdateTimeEpochMs)
        assertNull(model.location)
    }

    @Test
    fun `missing timestamps fall back to epoch rather than crashing`() {
        val entity = MemoDto(name = "memos/x").toEntity(accountId = 1)
        assertEquals(0L, entity.createTimeEpochMs)
    }

    @Test
    fun `empty tag list stays empty after round trip`() {
        val model = MemoDto(name = "memos/x", tags = emptyList()).toEntity(1).toModel()
        assertEquals(emptyList<String>(), model.tags)
    }
}
