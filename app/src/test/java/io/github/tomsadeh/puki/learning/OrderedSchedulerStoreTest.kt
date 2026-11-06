package io.github.tomsadeh.puki.learning

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OrderedSchedulerStoreTest {
    @Test
    fun exitSavesFinishBeforeAnImmediateReentryLoad() = runBlocking {
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backing = InMemorySchedulerStore()
        val store = object : SchedulerStore {
            override suspend fun load(key: String) = backing.load(key)
            override suspend fun save(key: String, memory: SchedulerMemory) {
                writing.complete(Unit)
                release.await()
                backing.save(key, memory)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val ordered = OrderedSchedulerStore(store, scope)
            val first = SchedulerMemory(covered = setOf("a"))
            val completed = first.copy(session = 1, records = mapOf("a" to ItemRecord(1, 1, 0)))
            ordered.save("game", first)
            writing.await()
            ordered.save("game", completed)
            val loaded = async { ordered.load("game") }
            yield()
            assertFalse(loaded.isCompleted)
            release.complete(Unit)
            assertEquals(completed, withTimeout(5000) { loaded.await() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aCancelledVisitCannotCancelItsQueuedSave() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val backing = InMemorySchedulerStore()
        val store = object : SchedulerStore {
            override suspend fun load(key: String) = backing.load(key)
            override suspend fun save(key: String, memory: SchedulerMemory) {
                release.await()
                backing.save(key, memory)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val ordered = OrderedSchedulerStore(store, scope)
            val expected = SchedulerMemory(covered = setOf("a", "b"))
            val visit = async {
                ordered.save("game", expected)
                ordered.load("game")
            }
            yield()
            visit.cancel()
            release.complete(Unit)
            assertEquals(expected, withTimeout(5000) { ordered.load("game") })
        } finally {
            scope.cancel()
        }
    }
}
