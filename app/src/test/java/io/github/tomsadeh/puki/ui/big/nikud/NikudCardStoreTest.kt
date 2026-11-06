package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
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
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class NikudCardStoreTest {
    @Test
    fun finishingACardAndImmediatelyReenteringWaitsForTheSave() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val backing = InMemoryNikudCardStore()
        val store = object : NikudCardStore {
            override suspend fun seen() = backing.seen()
            override suspend fun markSeen(card: Int) {
                release.await()
                backing.markSeen(card)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val ordered = OrderedNikudCardStore(store, scope)
            val card = MeetMarkCard(1, false, ordered::markSeen).also { it.start() }
            card.onDone()
            val reentry = async { ordered.seen() }
            yield()
            assertFalse(reentry.isCompleted)
            release.complete(Unit)
            val seen = withTimeout(5000) { reentry.await() }
            assertEquals(setOf(1), seen)
            val game = SoundShellsGame(RoundScheduler(), Random(1), RoundEvents(), seen).also { it.start() }
            assertNull(game.state.offerCard)
        } finally {
            scope.cancel()
        }
    }
}
