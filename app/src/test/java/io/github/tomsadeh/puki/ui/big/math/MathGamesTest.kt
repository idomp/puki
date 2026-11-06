package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MathGamesTest {
    private class Store(var stage: MathStage = MathStage.JOIN_5) : MathProgressStore {
        var writes = 0
        override suspend fun load(game: MathGameId) = stage
        override suspend fun save(game: MathGameId, stage: MathStage) { this.stage = stage; writes++ }
    }

    private fun assertChoices(answer: Int, choices: List<Int>, maximum: Int, zero: Boolean) {
        assertTrue(choices.size in 3..4)
        assertEquals(choices.size, choices.distinct().size)
        assertEquals(1, choices.count { it == answer })
        assertTrue(choices.all { it in (if (zero) 0 else 1)..maximum })
    }

    private fun FriendsGame.question() { advancePhase(); advancePhase() }
    private fun FriendsGame.solve() { question(); onAnswer(state.round!!.result) }
    private fun PicnicGame.solve() { repeat(state.round!!.target) { addItem() }; ready() }

    @Test fun storiesStayWithinStageAndChoicesAreUniqueAcrossSeeds() = runBlocking {
        for (stage in MathStage.entries) for (seed in 0..79) {
            val game = FriendsGame(Random(seed), Store(stage))
            game.start()
            repeat(6) { index ->
                val round = game.state.round!!
                val maximum = if (stage == MathStage.MIXED_10 && index < 2) 5 else stage.maximum
                val minimum = if (stage.allowsZero) 0 else 1
                assertTrue(round.startCount in minimum..maximum)
                assertTrue(round.change in minimum..maximum)
                assertTrue(round.result in minimum..maximum)
                assertChoices(round.result, game.state.choices, maximum, stage.allowsZero)
                when (stage) {
                    MathStage.JOIN_5 -> assertEquals(MathOperation.PLUS, round.direction)
                    MathStage.TAKE_5 -> assertEquals(MathOperation.MINUS, round.direction)
                    MathStage.MIXED_5, MathStage.MIXED_10 -> when (index) {
                        0, 1 -> assertEquals(MathOperation.PLUS, round.direction)
                        2, 3 -> assertEquals(MathOperation.MINUS, round.direction)
                    }
                    else -> Unit
                }
                if (stage == MathStage.MIXED_10 && index >= 2)
                    assertTrue(maxOf(round.startCount, round.change, round.result) > 5)
                game.solve()
                assertEquals(round.equation, game.state.equation)
                assertEquals(index + 1, game.state.session.totalStarsEarned)
                game.next()
            }
            assertTrue(game.state.session.sessionComplete)
        }
    }

    @Test fun zeroStoriesIncludeEmptyStartNoChangeTakingEverythingAndEmptyPlusEmpty() = runBlocking {
        val game = FriendsGame(Random(12), Store(MathStage.ZERO_5))
        game.start()
        val rounds = mutableListOf<FriendsRound>()
        repeat(6) { rounds += game.state.round!!; game.solve(); game.next() }
        assertEquals(0, rounds[2].startCount)
        assertEquals(0, rounds[3].change)
        assertEquals(MathOperation.MINUS, rounds[4].direction)
        assertEquals(rounds[4].startCount, rounds[4].change)
        assertEquals(0, rounds[4].result)
        assertEquals(FriendsRound(0, 0, MathOperation.PLUS), rounds[5])
    }

    @Test fun storyPhasesRetriesAndReplayKeepTheRoundPlayable() = runBlocking {
        val store = Store()
        val game = FriendsGame(Random(1), store)
        val loading = game.state
        game.onAnswer(2); game.advancePhase(); game.replay(); game.next()
        assertEquals(loading, game.state)
        game.start()
        val initial = game.state
        game.start(); game.next(); game.onAnswer(initial.round!!.result)
        assertEquals(initial, game.state)
        assertNull(initial.equation)
        game.replay()
        assertEquals(initial.voice.keys, game.state.voice.keys)
        assertTrue(game.state.voice.token > initial.voice.token)
        game.advancePhase()
        assertEquals(StoryPhase.CHANGE, game.state.phase)
        assertEquals(initial.phaseVoices.getValue(StoryPhase.CHANGE), game.state.voice.lines)
        game.advancePhase()
        assertEquals(StoryPhase.QUESTION, game.state.phase)
        assertEquals(initial.phaseVoices.getValue(StoryPhase.QUESTION), game.state.voice.lines)
        game.onAnswer(-99)
        assertEquals(0, game.state.session.misses)
        val wrong = game.state.choices.first { it != initial.round.result }
        val token = game.state.voice.token
        game.onAnswer(wrong)
        assertEquals(wrong, game.state.feedback!!.chosen)
        assertEquals(MathFeedbackKind.COUNT_TOGETHER, game.state.feedback!!.kind)
        assertFalse(game.state.session.showHint)
        assertNull(game.state.equation)
        assertEquals(StoryPhase.QUESTION, game.state.phase)
        assertTrue(game.state.voice.token > token)
        game.onAnswer(wrong)
        assertTrue(game.state.session.showHint)
        assertEquals(2, game.state.session.misses)
        assertEquals(1, game.state.session.missedRounds)
        game.onAnswer(initial.round.result)
        assertTrue(game.state.roundComplete)
        assertNull(game.state.feedback)
        assertEquals(initial.round.equation, game.state.equation)
        val solved = game.state
        game.onAnswer(initial.round.result); game.advancePhase()
        assertEquals(solved, game.state)
        game.next()
        assertEquals(1, game.state.session.roundIndex)
        assertEquals(0, game.state.session.roundMisses)
        assertEquals(0, store.writes)
    }

    @Test fun subtractionFeedbackCountsOnlyRemainingBirds() = runBlocking {
        val game = FriendsGame(Random(5), Store(MathStage.TAKE_5))
        game.start(); game.question()
        game.onAnswer(game.state.choices.first { it != game.state.round!!.result })
        assertEquals(MathFeedbackKind.COUNT_REMAINING, game.state.feedback!!.kind)
        assertEquals(MathContent.leaveHint, game.state.feedback!!.hint)
    }

    @Test fun picnicOnlyChecksOnReadyAndAllowsTooFewTooManyAndExact() = runBlocking {
        val game = PicnicGame(Random(3), Store())
        game.start()
        val target = game.state.round!!.target
        game.removeItem()
        assertEquals(0, game.state.basketCount)
        game.ready()
        assertEquals(MathFeedbackKind.TOO_FEW, game.state.feedback!!.kind)
        assertEquals(target, game.state.feedback!!.adjustment)
        repeat(game.state.basketLimit + 5) { game.addItem() }
        assertEquals(game.state.basketLimit, game.state.basketCount)
        assertFalse(game.state.roundComplete)
        assertEquals(1, game.state.session.misses)
        assertNull(game.state.feedback)
        game.ready()
        assertEquals(MathFeedbackKind.TOO_MANY, game.state.feedback!!.kind)
        assertEquals(target - game.state.basketCount, game.state.feedback!!.adjustment)
        assertTrue(game.state.session.showHint)
        repeat(game.state.basketCount - target) { game.removeItem() }
        assertFalse(game.state.roundComplete)
        assertEquals(2, game.state.session.misses)
        game.ready()
        assertTrue(game.state.roundComplete)
        assertEquals(1, game.state.session.totalStarsEarned)
        assertNull(game.state.feedback)
        val solved = game.state
        game.ready(); game.addItem(); game.removeItem()
        assertEquals(solved, game.state)
    }

    @Test fun picnicRequestsHaveConcreteWarmupThenOneMoreOrOneLessWithinStage() = runBlocking {
        for (stage in MathStage.entries) for (seed in 0..39) {
            val game = PicnicGame(Random(seed), Store(stage))
            game.start()
            repeat(6) { index ->
                val round = game.state.round!!
                val maximum = if (stage == MathStage.MIXED_10 && index < 2) 5 else stage.maximum
                val minimum = if (stage.allowsZero) 0 else 1
                assertTrue(round.shownAmount in minimum..maximum)
                assertTrue(round.target in minimum..maximum)
                if (index < 3) assertEquals(PicnicRequest.EXACT, round.request)
                else if (stage == MathStage.JOIN_5) assertEquals(PicnicRequest.ONE_MORE, round.request)
                else if (stage == MathStage.TAKE_5) assertEquals(PicnicRequest.ONE_LESS, round.request)
                if (stage == MathStage.MIXED_10 && index >= 2)
                    assertTrue(maxOf(round.shownAmount, round.target) > 5)
                game.solve(); game.next()
            }
            assertTrue(game.state.session.sessionComplete)
            assertEquals(6, game.state.session.totalStarsEarned)
        }
    }

    @Test fun picnicZeroCanBeSubmittedWithoutAddingAndOneLessCanMeanAnEmptyBasket() = runBlocking {
        val game = PicnicGame(Random(4), Store(MathStage.ZERO_5))
        game.start()
        repeat(2) { game.solve(); game.next() }
        assertEquals(PicnicRequest.EXACT, game.state.round!!.request)
        assertEquals(0, game.state.round!!.target)
        game.ready()
        assertTrue(game.state.roundComplete)
        game.next()
        assertEquals(0, game.state.round!!.shownAmount)
        assertEquals(1, game.state.round!!.target)
        game.solve(); game.next()
        assertEquals(PicnicRequest.ONE_LESS, game.state.round!!.request)
        assertEquals(0, game.state.round!!.target)
        game.ready()
        assertTrue(game.state.roundComplete)
    }

    @Test fun frameComplementsCellsChoicesAndEquationsAgree() = runBlocking {
        for (stage in MathStage.entries) for (seed in 0..79) {
            val game = FrameGame(Random(seed), Store(stage))
            game.start()
            repeat(6) { index ->
                val round = game.state.round!!
                assertEquals(if (stage == MathStage.MIXED_10 && index >= 2) 10 else 5, round.capacity)
                assertEquals(round.capacity, round.filled + round.missing)
                assertEquals(round.capacity, game.state.cells.size)
                assertEquals(round.filled, game.state.cells.count { it })
                assertChoices(round.missing, game.state.choices, round.capacity, stage.allowsZero)
                if (stage >= MathStage.MIXED_5 && index >= 3)
                    assertEquals(Equation(round.filled, MathOperation.PLUS, null, round.capacity), game.state.equation)
                else assertNull(game.state.equation)
                game.onAnswer(round.missing)
                assertEquals(Equation(round.filled, MathOperation.PLUS, round.missing, round.capacity), game.state.equation)
                assertTrue(game.state.roundComplete)
                game.next()
            }
            assertEquals(6, game.state.session.totalStarsEarned)
        }
    }

    @Test fun frameIncludesCompletelyEmptyAndFullFramesAtZeroStageAndKeepsHintsGentle() = runBlocking {
        val game = FrameGame(Random(5), Store(MathStage.ZERO_5))
        game.start()
        game.onAnswer(-1)
        assertEquals(0, game.state.session.misses)
        val wrong = game.state.choices.first { it != game.state.round!!.missing }
        game.onAnswer(wrong); game.onAnswer(wrong)
        assertEquals(MathFeedbackKind.COUNT_EMPTY_SPACES, game.state.feedback!!.kind)
        assertTrue(game.state.session.showHint)
        assertFalse(game.state.roundComplete)
        repeat(3) { game.onAnswer(game.state.round!!.missing); game.next() }
        assertEquals(0, game.state.round!!.filled)
        assertEquals(5, game.state.round!!.missing)
        game.onAnswer(5); game.next()
        assertEquals(5, game.state.round!!.filled)
        assertEquals(0, game.state.round!!.missing)
        assertTrue(0 in game.state.choices)
        game.onAnswer(0)
        assertTrue(game.state.roundComplete)
    }

    @Test fun stageRulePromotesAtMostOneMissAndDemotesOnlyAMajorityOfMissedRounds() {
        val stage = MathStage.MIXED_5
        assertEquals(MathStage.ZERO_5, nextMathStage(stage, 6, 0, 0))
        assertEquals(MathStage.ZERO_5, nextMathStage(stage, 6, 1, 1))
        assertEquals(stage, nextMathStage(stage, 6, 2, 2))
        assertEquals(stage, nextMathStage(stage, 6, 20, 1))
        assertEquals(stage, nextMathStage(stage, 6, 3, 3))
        assertEquals(MathStage.TAKE_5, nextMathStage(stage, 6, 4, 4))
        assertEquals(MathStage.TAKE_5, nextMathStage(stage, 6, 10, 6))
        assertEquals(MathStage.JOIN_5, nextMathStage(MathStage.JOIN_5, 6, 6, 6))
        assertEquals(MathStage.MIXED_10, nextMathStage(MathStage.MIXED_10, 6, 0, 0))
    }

    @Test fun onlyFinishedSessionsSaveAndEachGameRemembersItsOwnStage() = runBlocking {
        val store = InMemoryMathProgressStore()
        val game = FriendsGame(Random(6), store)
        game.start()
        assertEquals(MathStage.JOIN_5, store.load(MathGameId.FRIENDS))
        repeat(6) { game.solve(); game.next() }
        assertEquals(MathStage.JOIN_5, game.state.session.stage)
        assertEquals(MathStage.TAKE_5, game.state.session.nextStage)
        assertEquals(MathStage.TAKE_5, store.load(MathGameId.FRIENDS))
        assertEquals(MathStage.JOIN_5, store.load(MathGameId.PICNIC))
        assertEquals(MathStage.JOIN_5, store.load(MathGameId.FRAME))
        val restarted = FriendsGame(Random(7), store)
        restarted.start()
        assertEquals(MathStage.TAKE_5, restarted.state.session.stage)
        val partial = PicnicGame(Random(8), store)
        partial.start(); partial.solve(); partial.next()
        assertEquals(MathStage.JOIN_5, store.load(MathGameId.PICNIC))
    }

    @Test fun finishedSessionsIgnoreFurtherEventsAndPersistExactlyOnce() = runBlocking {
        val store = Store()
        val game = FrameGame(Random(9), store)
        game.start()
        repeat(6) { game.onAnswer(game.state.round!!.missing); game.next() }
        val complete = game.state
        game.next(); game.next(); game.replay(); game.onAnswer(0); game.start()
        assertEquals(complete, game.state)
        assertEquals(1, store.writes)
        assertTrue(complete.voice.keys.isEmpty())
    }

    @Test fun repeatedMissesInOneRoundHoldButMissesInMostRoundsEaseTheNextSession() = runBlocking {
        for (missedRounds in listOf(1, 4)) {
            val store = Store(MathStage.MIXED_5)
            val game = FrameGame(Random(10), store)
            game.start()
            repeat(6) { index ->
                if (index < missedRounds) {
                    val wrong = game.state.choices.first { it != game.state.round!!.missing }
                    repeat(5) { game.onAnswer(wrong) }
                }
                game.onAnswer(game.state.round!!.missing); game.next()
            }
            assertEquals(if (missedRounds == 1) MathStage.MIXED_5 else MathStage.TAKE_5, store.stage)
        }
    }

    @Test fun seededSelectionAndChoicesAreReproducible() = runBlocking {
        for (stage in MathStage.entries) {
            val left = FriendsGame(Random(888), Store(stage))
            val right = FriendsGame(Random(888), Store(stage))
            left.start(); right.start()
            repeat(6) {
                assertEquals(left.state, right.state)
                left.solve(); right.solve(); left.next(); right.next()
            }
        }
    }

    @Test fun replayTokensChangeWithoutChangingTheTaskForPicnicAndFrame() = runBlocking {
        val picnic = PicnicGame(Random(1), Store())
        val frame = FrameGame(Random(1), Store())
        picnic.start(); frame.start()
        val p = picnic.state
        val f = frame.state
        picnic.replay(); frame.replay()
        assertEquals(p.copy(voice = picnic.state.voice), picnic.state)
        assertEquals(f.copy(voice = frame.state.voice), frame.state)
        assertEquals(p.voice.keys, picnic.state.voice.keys)
        assertEquals(f.voice.keys, frame.state.voice.keys)
        assertTrue(picnic.state.voice.token > p.voice.token)
        assertTrue(frame.state.voice.token > f.voice.token)
    }
}
