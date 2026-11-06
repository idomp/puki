package io.github.tomsadeh.puki.learning

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/** Saves survive a game owner's disposal, and later loads wait for every earlier save. */
class OrderedSchedulerStore(private val store: SchedulerStore, scope: CoroutineScope) {
    private val queue = StoreQueue(scope)

    suspend fun load(key: String): SchedulerMemory = queue.read { store.load(key) }

    fun save(key: String, memory: SchedulerMemory) = queue.write { store.save(key, memory) }
}

/** Orders reads and writes independently of the screen that requested them. */
internal class StoreQueue(private val scope: CoroutineScope) {
    private var tail: Job? = null

    private fun <T> enqueue(action: suspend () -> T): Deferred<T> = synchronized(this) {
        val previous = tail
        scope.async {
            previous?.join()
            action()
        }.also { tail = it }
    }

    suspend fun <T> read(action: suspend () -> T): T = enqueue(action).await()

    fun write(action: suspend () -> Unit) {
        enqueue { runCatching { action() } }
    }
}
