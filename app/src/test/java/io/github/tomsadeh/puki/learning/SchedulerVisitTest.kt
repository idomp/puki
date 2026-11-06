package io.github.tomsadeh.puki.learning

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.ui.big.letters.FinalPartnersGame
import io.github.tomsadeh.puki.ui.big.letters.LetterDetectiveGame
import io.github.tomsadeh.puki.ui.big.letters.LetterGame
import io.github.tomsadeh.puki.ui.big.letters.LetterPhase
import io.github.tomsadeh.puki.ui.big.letters.ListenFindGame
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.big.nikud.JoinPhase
import io.github.tomsadeh.puki.ui.big.nikud.JoinWordGame
import io.github.tomsadeh.puki.ui.big.nikud.ReadSignGame
import io.github.tomsadeh.puki.ui.big.nikud.SoundShellsGame
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.random.Random

/** Runs the same persistence path used by both ViewModel wrappers, with each real reading game. */
@RunWith(Parameterized::class)
class SchedulerVisitTest(private val kind: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun games() = listOf("listen", "detective", "finals", "shells", "join", "read")
            .map { arrayOf(it) }
    }

    private val initial = SchedulerMemory(session = 4,
        records = mapOf("previous" to ItemRecord(seen = 2, lastSession = 3, misses = 1)))

    private class Driver(
        val start: () -> Unit,
        val step: () -> Unit,
        val replay: () -> Unit,
        val finished: () -> Boolean
    )

    private inner class Playing(memory: SchedulerMemory, store: OrderedSchedulerStore) {
        private val random = Random(17)
        val scheduler = RoundScheduler(memory, random)
        val visit = SchedulerVisit(scheduler, kind, store)
        var completed: SchedulerMemory? = null
        private val events = RoundEvents(onFinish = { _, memory ->
            completed = memory
            visit.complete(memory)
        })

        private fun act(action: () -> Unit) { visit.update(action) }

        private fun letters(game: LetterGame, start: () -> Unit) = Driver(
            start = { act(start) },
            step = {
                if (game.state.phase == LetterPhase.COMPARE) {
                    act { game.next() }
                } else if (!game.state.sessionComplete) {
                    val answer = game.state.shown?.let { BigLetters.partnerOf(it.id)!! }
                        ?: BigLetters.all.first { it.voiceKey in game.state.cue.keys }.id
                    act { game.onAnswer(answer) }
                    act { game.next() }
                }
            },
            replay = { act { game.replay() } },
            finished = { game.state.sessionComplete }
        )

        val driver = when (kind) {
            "listen" -> ListenFindGame(scheduler, events).let { letters(it, it::start) }
            "detective" -> LetterDetectiveGame(scheduler, random, events).let { letters(it, it::start) }
            "finals" -> FinalPartnersGame(scheduler, events).let { letters(it, it::start) }
            "shells" -> SoundShellsGame(scheduler, random, events).let { game ->
                Driver(
                    start = { act { game.start() } },
                    step = {
                        if (!game.state.sessionComplete) {
                            val answer = game.state.tiles.first { it.clipKey in game.state.cue.keys }.id
                            act { game.onAnswer(answer) }
                            act { game.next() }
                        }
                    },
                    replay = { act { game.replay() } },
                    finished = { game.state.sessionComplete }
                )
            }
            "join" -> JoinWordGame(scheduler, random, events).let { game ->
                Driver(
                    start = { act { game.start() } },
                    step = {
                        when (game.state.phase) {
                            JoinPhase.UNITS -> act {
                                game.onUnitTap(game.state.units.indexOfFirst { !it.done })
                            }
                            JoinPhase.JOINED -> act { game.onJoined() }
                            JoinPhase.PICTURE -> act { game.onAnswer(game.state.wordId) }
                            JoinPhase.SOLVED -> act { game.next() }
                            else -> Unit
                        }
                    },
                    replay = { act { game.replay() } },
                    finished = { game.state.sessionComplete }
                )
            }
            "read" -> ReadSignGame(scheduler, random, events).let { game ->
                Driver(
                    start = { act { game.start() } },
                    step = {
                        if (!game.state.sessionComplete) {
                            act { game.onAnswer(scheduler.current!!) }
                            act { game.next() }
                        }
                    },
                    replay = { act { game.replay() } },
                    finished = { game.state.sessionComplete }
                )
            }
            else -> error("Unknown game: $kind")
        }

        fun finish() {
            repeat(100) {
                if (driver.finished()) return
                driver.step()
            }
            error("Session did not complete: $kind")
        }
    }

    @Test
    fun unfinishedPresentationsSurviveWithoutClearingTheOwnerOrAwardingMastery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = OrderedSchedulerStore(InMemorySchedulerStore(), scope)
            val playing = Playing(initial, store)
            playing.driver.start()
            val first = store.load(kind)
            assertEquals(playing.scheduler.checkpoint(), first)
            assertEquals(1, first.covered.size)
            assertEquals(first.lastItem, first.opening)
            // No save-on-exit call: each presentation must already have submitted its checkpoint.
            repeat(6) {
                playing.driver.step()
                val saved = store.load(kind)
                assertEquals(playing.scheduler.checkpoint(), saved)
                assertEquals(initial.session, saved.session)
                assertEquals(initial.records, saved.records)
                assertFalse(playing.driver.finished())
            }
            val saved = store.load(kind)
            assertTrue(saved.presentations.isNotEmpty())
            assertTrue(saved.covered.size > 1 || kind == "detective")
            assertNotNull(saved.opening)
            // Reconstruct from durable data as a new process would, with no disposal callback.
            val restored = SchedulerMemory.decode(saved.encode())
            val reopened = Playing(restored, store)
            reopened.driver.start()
            val next = store.load(kind)
            assertNotEquals(saved.opening, next.opening)
            assertEquals(initial.records, next.records)
            assertEquals(initial.session, next.session)
            assertTrue(next.presentations.entries.containsAll(saved.presentations.entries))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun completionSurvivesStatePublicationReplayNextAndDisposal() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = OrderedSchedulerStore(InMemorySchedulerStore(), scope)
            val playing = Playing(initial, store)
            playing.driver.start()
            playing.finish()
            val completed = playing.completed!!
            assertEquals(initial.session + 1, completed.session)
            assertTrue(completed.records.size > initial.records.size)
            assertNotEquals(completed, playing.scheduler.checkpoint())
            assertEquals(completed, store.load(kind))
            playing.driver.replay()
            playing.driver.step()
            playing.visit.save()
            assertEquals(completed, store.load(kind))

            // A new unfinished visit preserves mastery earned by the completed one.
            val next = Playing(store.load(kind), store)
            next.driver.start()
            assertEquals(completed.session, store.load(kind).session)
            assertEquals(completed.records, store.load(kind).records)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun delayedCheckpointsAndCompletionKeepSubmissionOrder() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val backing = InMemorySchedulerStore()
        val writes = mutableListOf<SchedulerMemory>()
        val delayed = object : SchedulerStore {
            override suspend fun load(key: String) = backing.load(key)
            override suspend fun save(key: String, memory: SchedulerMemory) {
                release.await()
                writes.add(memory)
                backing.save(key, memory)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = OrderedSchedulerStore(delayed, scope)
            val playing = Playing(initial, store)
            playing.driver.start()
            val first = playing.scheduler.checkpoint()
            playing.finish()
            playing.visit.save()
            val loaded = async { store.load(kind) }
            yield()
            assertFalse(loaded.isCompleted)
            release.complete(Unit)
            assertEquals(playing.completed, withTimeout(5000) { loaded.await() })
            assertEquals(first, writes.first())
            assertEquals(playing.completed, writes.last())
            assertTrue(writes.size > 2)
            assertTrue(writes.dropLast(1).all { it.session == initial.session && it.records == initial.records })
        } finally {
            scope.cancel()
        }
    }
}
