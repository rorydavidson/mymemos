package com.keltruc.mymemos.web.sync

import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.web.store.AccountRecord
import com.keltruc.mymemos.web.store.MemoryPersistence
import com.keltruc.mymemos.web.store.WebStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSyncEngineTest {

    private class Rig {
        val server = FakeServer()
        val store = WebStore(MemoryPersistence())
        // Scheduled syncs are ignored; each test syncs when it means to.
        val idle = CoroutineScope(Job())
        val engine = WebSyncEngine(store) { server }
        val memos = WebMemos(store, SyncScheduler(idle) {})

        suspend fun signIn() {
            store.write { putAccounts(listOf(AccountRecord(1, "https://memos.test/", "users/1", "rory", "Rory", authMethod = "PERSONAL_ACCESS_TOKEN", token = "t")), 1) }
        }

        suspend fun sync(full: Boolean = false) = assertEquals(WebSyncEngine.Outcome.Success, engine.sync(1, full))
    }

    private fun rig(block: suspend Rig.() -> Unit) = runTest {
        val r = Rig()
        r.signIn()
        try { r.block() } finally { r.idle.cancel() }
    }

    @Test
    fun aMemoWrittenOfflineReachesTheServer() = rig {
        val id = memos.create(1, "# Shopping\n- [ ] milk #home", Visibility.PRIVATE)
        assertEquals(SyncStatus.PENDING_CREATE.name, store.memo(id)!!.syncStatus)
        assertEquals(listOf("home"), store.memo(id)!!.tags)
        sync()
        val local = store.memo(id)!!
        assertEquals(SyncStatus.SYNCED.name, local.syncStatus)
        assertEquals("# Shopping\n- [ ] milk #home", server.memos.getValue(local.remoteName!!).content)
        assertTrue(store.queued(1).isEmpty())
    }

    @Test
    fun editsOnBothSidesOfDifferentLinesMerge() = rig {
        val id = memos.create(1, "one\ntwo\nthree", Visibility.PRIVATE)
        sync()
        val name = store.memo(id)!!.remoteName!!
        server.editElsewhere(name, "ONE\ntwo\nthree")
        memos.updateContent(id, "one\ntwo\nTHREE")
        sync()
        assertEquals("ONE\ntwo\nTHREE", server.memos.getValue(name).content)
        assertEquals("ONE\ntwo\nTHREE", store.memo(id)!!.content)
    }

    @Test
    fun clashingEditsKeepBothVersions() = rig {
        val id = memos.create(1, "the same line", Visibility.PRIVATE)
        sync()
        val name = store.memo(id)!!.remoteName!!
        server.editElsewhere(name, "their line")
        memos.updateContent(id, "my line")
        sync()
        assertEquals("their line", store.memo(id)!!.content)
        val fork = store.memosFor(1).single { it.localId != id }
        assertTrue(fork.content.startsWith("my line"))
        assertTrue("conflict" in fork.tags)
        // The fork was pushed as a new memo in the same run, so nothing is lost on the server either.
        assertEquals(2, server.memos.size)
    }

    @Test
    fun anEditToAMemoDeletedElsewhereIsRecreated() = rig {
        val id = memos.create(1, "keep me", Visibility.PRIVATE)
        sync()
        server.memos.clear()
        memos.updateContent(id, "keep me, edited")
        sync()
        val local = store.memo(id)!!
        assertEquals(SyncStatus.SYNCED.name, local.syncStatus)
        assertEquals("keep me, edited", server.memos.getValue(local.remoteName!!).content)
    }

    @Test
    fun aFullPullDropsWhatTheServerLostButNotEverything() = rig {
        val a = memos.create(1, "a", Visibility.PRIVATE)
        val b = memos.create(1, "b", Visibility.PRIVATE)
        sync()
        server.memos.remove(store.memo(a)!!.remoteName)
        sync(full = true)
        assertNull(store.memo(a))
        assertNotNull(store.memo(b))
        // An empty answer looks like a broken server, not a wish to lose everything.
        server.memos.clear()
        sync(full = true)
        assertNotNull(store.memo(b))
    }

    @Test
    fun aDeleteCanBeTakenBackUntilItIsSent() = rig {
        val id = memos.create(1, "oops", Visibility.PRIVATE)
        sync()
        assertTrue(memos.delete(id))
        assertTrue(memos.undoDelete(id))
        sync()
        assertEquals(1, server.memos.size)
        assertTrue(memos.delete(id))
        sync()
        assertTrue(server.memos.isEmpty())
        assertNull(store.memo(id))
        assertFalse(memos.undoDelete(id))
    }

    @Test
    fun aMemoNeverSentIsDeletedOutright() = rig {
        val id = memos.create(1, "draft", Visibility.PRIVATE)
        assertFalse(memos.delete(id))
        assertNull(store.memo(id))
        sync()
        assertTrue(server.memos.isEmpty())
    }

    @Test
    fun theStoreSurvivesAReload() = runTest {
        val persistence = MemoryPersistence()
        val store = WebStore(persistence)
        val idle = CoroutineScope(Job())
        val memos = WebMemos(store, SyncScheduler(idle) {})
        store.write { putAccounts(listOf(AccountRecord(1, "https://memos.test/", "users/1", "rory", "Rory", authMethod = "PAT", token = "t")), 1) }
        val id = memos.create(1, "persisted", Visibility.PUBLIC, pinned = true)
        val reloaded = WebStore(persistence).also { it.load() }
        assertEquals("persisted", reloaded.memo(id)!!.content)
        assertEquals(1, reloaded.queued(1).size)
        assertEquals(1, reloaded.activeAccountId)
        idle.cancel()
    }
}
