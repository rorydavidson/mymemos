package com.keltruc.mymemos.web.store

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Where [WebStore] keeps its rows between page loads. Values are JSON text so the format is
 * the serializer's business, not IndexedDB's. [commit] must be all or nothing.
 */
interface Persistence {
    suspend fun loadAll(table: String): List<Pair<String, String>>
    suspend fun commit(changes: Changes)
    suspend fun readBlob(key: String): ByteArray?
    suspend fun writeBlob(key: String, bytes: ByteArray)
    suspend fun deleteBlob(key: String)
    suspend fun clearAll()
}

/** Puts and deletes per table, applied in one transaction. A null value is a delete. */
class Changes {
    val tables = LinkedHashMap<String, LinkedHashMap<String, String?>>()

    fun put(table: String, key: String, value: String) {
        tables.getOrPut(table) { LinkedHashMap() }[key] = value
    }

    fun delete(table: String, key: String) {
        tables.getOrPut(table) { LinkedHashMap() }[key] = null
    }

    val isEmpty: Boolean get() = tables.values.all { it.isEmpty() }
}

/** For tests under Node, which has no IndexedDB. */
class MemoryPersistence : Persistence {
    private val tables = HashMap<String, LinkedHashMap<String, String>>()
    private val blobs = HashMap<String, ByteArray>()

    override suspend fun loadAll(table: String): List<Pair<String, String>> = tables[table]?.toList().orEmpty()

    override suspend fun commit(changes: Changes) {
        for ((table, rows) in changes.tables) {
            val t = tables.getOrPut(table) { LinkedHashMap() }
            for ((k, v) in rows) if (v == null) t.remove(k) else t[k] = v
        }
    }

    override suspend fun readBlob(key: String): ByteArray? = blobs[key]
    override suspend fun writeBlob(key: String, bytes: ByteArray) { blobs[key] = bytes }
    override suspend fun deleteBlob(key: String) { blobs.remove(key) }
    override suspend fun clearAll() { tables.clear(); blobs.clear() }
}

/**
 * IndexedDB, through the plain browser API rather than a wrapper library. Every table is an
 * object store with out-of-line string keys and string values; attachment bytes go in
 * `blobs` as Uint8Arrays so they are not inflated into JSON.
 */
class IndexedDbPersistence private constructor(private val db: dynamic) : Persistence {

    override suspend fun loadAll(table: String): List<Pair<String, String>> {
        val tx = db.transaction(table, "readonly")
        val store = tx.objectStore(table)
        val keys: Array<String> = request<Array<String>>(store.getAllKeys()).await()
        val values: Array<String> = request<Array<String>>(store.getAll()).await()
        return keys.indices.map { keys[it] to values[it] }
    }

    override suspend fun commit(changes: Changes) {
        if (changes.isEmpty) return
        val names = changes.tables.keys.toTypedArray()
        val tx = db.transaction(names, "readwrite")
        val done = completion(tx)
        for ((table, rows) in changes.tables) {
            val store = tx.objectStore(table)
            for ((k, v) in rows) if (v == null) store.delete(k) else store.put(v, k)
        }
        done.await()
    }

    override suspend fun readBlob(key: String): ByteArray? {
        val tx = db.transaction(BLOBS, "readonly")
        val value: dynamic = request<dynamic>(tx.objectStore(BLOBS).get(key)).await()
        if (value == null || value == undefined) return null
        val array = value.unsafeCast<Uint8Array>()
        return Int8Array(array.buffer, array.byteOffset, array.length).unsafeCast<ByteArray>().copyOf()
    }

    override suspend fun writeBlob(key: String, bytes: ByteArray) {
        val tx = db.transaction(BLOBS, "readwrite")
        val done = completion(tx)
        val i8 = bytes.unsafeCast<Int8Array>()
        tx.objectStore(BLOBS).put(Uint8Array(i8.buffer, i8.byteOffset, i8.length), key)
        done.await()
    }

    override suspend fun deleteBlob(key: String) {
        val tx = db.transaction(BLOBS, "readwrite")
        val done = completion(tx)
        tx.objectStore(BLOBS).delete(key)
        done.await()
    }

    override suspend fun clearAll() {
        val names = (TABLES + BLOBS).toTypedArray()
        val tx = db.transaction(names, "readwrite")
        val done = completion(tx)
        for (n in names) tx.objectStore(n).clear()
        done.await()
    }

    companion object {
        const val MEMOS = "memos"
        const val OPS = "ops"
        const val KV = "kv"
        const val BLOBS = "blobs"
        private val TABLES = listOf(MEMOS, OPS, KV)
        private const val VERSION = 1

        suspend fun open(name: String): IndexedDbPersistence {
            val req = js("globalThis.indexedDB").open(name, VERSION)
            req.onupgradeneeded = { _: dynamic ->
                val db = req.result
                for (n in TABLES + BLOBS) {
                    if (!(db.objectStoreNames.contains(n) as Boolean)) db.createObjectStore(n)
                }
            }
            val db: dynamic = request<dynamic>(req).await()
            return IndexedDbPersistence(db)
        }

        private fun <T> request(req: dynamic): Promise<T> = Promise { resolve, reject ->
            req.onsuccess = { _: dynamic -> resolve(req.result.unsafeCast<T>()) }
            req.onerror = { _: dynamic -> reject(Error("IndexedDB: ${req.error?.message ?: "request failed"}")) }
        }

        private fun completion(tx: dynamic): Promise<Unit> = Promise { resolve, reject ->
            tx.oncomplete = { _: dynamic -> resolve(Unit) }
            tx.onerror = { _: dynamic -> reject(Error("IndexedDB: ${tx.error?.message ?: "transaction failed"}")) }
            tx.onabort = { _: dynamic -> reject(Error("IndexedDB: ${tx.error?.message ?: "transaction aborted"}")) }
        }
    }
}
