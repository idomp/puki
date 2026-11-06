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
        val minimum = if (zero) 0 else 1
        assertEquals(minOf(4, maximum - minimum + 1), choices.size)
        assertEquals(choices.size, choices.distinct().size)
        assertEquals(1, choices.count { it == answer })
        assertTrue(choices.all { it in minimum..maximum })
        // Shown in any order, but always the consecutive window around the answer.
        assertTrue(choices.sorted().zipWithNext().all { (a, b) -> b == a + 1 })
    }

    @Test fun choicesAreConsecutiveAroundEveryAnswerIncludingStageBoundaries() {
        for (maximum in 1..10) for (zero in listOf(false, true)) {
            for (answer in (if (zero) 0 else 1)..maximum) for (seed in 0..39) {
                val choices = mathChoices(answer, maximum, zero, Random(seed))
                assertChoices(answer, choices, maximum, zero)
                val minimum = if (zero) 0 else 1
                if (answer in (minimum + 2)..(maximum - 2))
                    assertTrue(choices.sorted().indexOf(answer) in 1..2)
            }
        }
        assertEquals(listOf(0, 1, 2, 3), mathChoices(1, 5, true, Random(0)).sorted())
        assertEquals(listOf(1, 2, 3, 4), mathChoices(1, 5, false, Random(0)).sorted())
        assertEquals(listOf(2, 3, 4, 5), mathChoices(4, 5, false, Random(0)).sorted())
        assertEquals(listOf(7, 8, 9, 10), mathChoices(10, 10, true, Random(0)).sorted())
    }

    @Test fun choicesPutTheAnswerWhereAskedAndShuffleTheOthers() {
        for (slot in 0..3) for (seed in 0..19) {
            val choices = mathChoices(3, 5, false, Random(seed), slot)
            assertEquals(3, choices[slot])
            assertChoices(3, choices, 5, false)
        }
        // Without a slot the answer lands anywhere, and the neighbours are not left ascending.
        val unslotted = (0..99).map { mathChoices(3, 5, false, Random(it)) }
        assertEquals(setOf(0, 1, 2, 3), unslotted.map { it.indexOf(3) }.toSet())
        assertTrue(unslotted.any { it != it.sorted() })
    }

    @Test fun answerSlotsCoverEveryPositionBeforeRepeatingAndNeverRepeatInARow() {
        for (seed in 0..99) {
            val slots = balancedAnswerSlots(4, 6, Random(seed))
            assertEquals(6, slots.size)
            assertEquals(setOf(0, 1, 2, 3), slots.take(4).toSet())
            assertEquals(2, slots.drop(4).distinct().size)
            assertTrue(slots.zipWithNext().all { (a, b) -> a != b })
            val counts = (0..3).map { slot -> slots.count { it == slot } }
            assertTrue(counts.max() - counts.min() <= 1)
        }
    }

    private fun FriendsGame.question() { advancePhase(); advancePhase() }
    private fun FriendsGame.solve() { question(); onAnswer(state.round!!.result) }
    private fun PicnicGame.solve() { repeat(state.round!!.target) { addItem() }; ready() }

    @Test fun storiesStayWithinStageAndChoicesAreUniqueAcrossSeeds() = runBlocking {
        for (stage in MathStage.entries) for (seed in 0..79) {
            val game = FriendsGame(Random(seed), Store(stage))
            game.start()
            val operations = mutableListOf<MathOperation>()
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
                    MathStage.MIXED_5, MathStage.MIXED_10 -> if (index < 2) assertEquals(MathOperation.PLUS, round.direction)
                    else -> Unit
                }
                operations += round.direction
                if (stage == MathStage.MIXED_10 && index >= 2)
                    assertTrue(maxOf(round.startCount, round.change, round.result) > 5)
                game.solve()
                assertEquals(round.equation, game.state.equation)
                assertEquals(index + 1, game.state.session.totalStarsEarned)
                game.next()
            }
            assertTrue(game.state.session.sessionComplete)
            // Both operations stay covered; the later two joins and two leaves come in a shuffled order.
            if (stage == MathStage.MIXED_5 || stage == MathStage.MIXED_10)
                assertEquals(listOf(2, 2), listOf(MathOperation.PLUS, MathOperation.MINUS).map { op -> operations.drop(2).count { it == op } })
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

    // ---- Variety: catalogs, sessions without repeats, answer placement and history across visits.

    private fun counts(catalog: (MathStage) -> Set<Any>) = MathStage.entries.map { catalog(it).size }

    @Test fun catalogsHaveTheMeasuredSizesPerStage() {
        assertEquals(listOf(10, 10, 20, 26, 111), counts(::friendsCatalog))
        assertEquals(listOf(9, 9, 13, 12, 21), counts(::picnicCatalog))
        assertEquals(listOf(4, 4, 4, 6, 17), counts(::frameCatalog))
    }

    private fun friendsSession(stage: MathStage, seed: Int, history: MathHistoryStore = InMemoryMathHistoryStore()) = runBlocking {
        val game = FriendsGame(Random(seed), Store(stage), history)
        game.start()
        List(6) { game.state.round!!.also { game.solve(); game.next() } }
    }

    private fun picnicSession(stage: MathStage, seed: Int, history: MathHistoryStore = InMemoryMathHistoryStore()) = runBlocking {
        val game = PicnicGame(Random(seed), Store(stage), history)
        game.start()
        List(6) { game.state.round!!.also { game.solve(); game.next() } }
    }

    private fun frameSession(stage: MathStage, seed: Int, history: MathHistoryStore = InMemoryMathHistoryStore()) = runBlocking {
        val game = FrameGame(Random(seed), Store(stage), history)
        game.start()
        List(6) { game.state.round!!.also { game.onAnswer(it.missing); game.next() } }
    }

    @Test fun everyStageOffersSixDifferentStoriesAndBasketsAndStaysInItsCatalog() {
        for (stage in MathStage.entries) for (seed in 0..99) {
            val stories = friendsSession(stage, seed)
            assertEquals("$stage $seed", 6, stories.distinct().size)
            assertTrue(friendsCatalog(stage).containsAll(stories))
            val baskets = picnicSession(stage, seed)
            assertEquals("$stage $seed", 6, baskets.distinct().size)
            assertTrue(picnicCatalog(stage).containsAll(baskets))
        }
    }

    @Test fun mixedStagesKeepBothOperationsAndRequestsButShuffleTheirOrder() {
        val operationOrders = mutableSetOf<List<MathOperation>>()
        val requestOrders = mutableSetOf<List<PicnicRequest>>()
        for (stage in listOf(MathStage.MIXED_5, MathStage.MIXED_10)) for (seed in 0..99) {
            val operations = friendsSession(stage, seed).map { it.direction }
            assertEquals(listOf(MathOperation.PLUS, MathOperation.PLUS), operations.take(2))
            assertEquals(2, operations.drop(2).count { it == MathOperation.MINUS })
            operationOrders += operations.drop(2)
            val requests = picnicSession(stage, seed).map { it.request }
            assertEquals(List(3) { PicnicRequest.EXACT }, requests.take(3))
            assertTrue(PicnicRequest.ONE_MORE in requests && PicnicRequest.ONE_LESS in requests)
            requestOrders += requests.drop(3)
        }
        assertEquals(6, operationOrders.size)
        assertTrue(requestOrders.size >= 4)
    }

    @Test fun zeroStagesStillTeachEveryZeroExampleWithoutRepeatingThem() {
        for (seed in 0..99) {
            val stories = friendsSession(MathStage.ZERO_5, seed)
            assertEquals(0, stories[2].startCount)
            assertEquals(0, stories[3].change)
            assertEquals(stories[4].startCount, stories[4].change)
            assertEquals(FriendsRound(0, 0, MathOperation.PLUS), stories[5])
            assertTrue(stories.take(2).all { it.startCount > 0 && it.change > 0 })
            val baskets = picnicSession(MathStage.ZERO_5, seed)
            assertEquals(List(3) { PicnicRequest.EXACT } + PicnicRequest.ONE_MORE + PicnicRequest.ONE_LESS + PicnicRequest.ONE_MORE,
                baskets.map { it.request })
            assertEquals(PicnicRound(PicnicRequest.EXACT, 0), baskets[2])
            assertEquals(PicnicRound(PicnicRequest.ONE_MORE, 0), baskets[3])
            assertEquals(PicnicRound(PicnicRequest.ONE_LESS, 1), baskets[4])
        }
    }

    @Test fun earlyFrameUsesAllFourProblemsBeforeAnyRepeatThenSpreadsTwoRepeats() {
        for (stage in listOf(MathStage.JOIN_5, MathStage.TAKE_5, MathStage.MIXED_5)) for (seed in 0..99) {
            val frames = frameSession(stage, seed)
            assertEquals(setOf(1, 2, 3, 4), frames.take(4).map { it.filled }.toSet())
            assertEquals(2, frames.drop(4).distinct().size)
            assertTrue(frames.zipWithNext().all { (a, b) -> a != b })
            assertTrue(frames.all { it.capacity == 5 })
            // The repeats come back in the order the problems first appeared.
            assertEquals(frames.take(2), frames.drop(4))
        }
    }

    @Test fun zeroFrameShowsAllSixProblemsOnceWithEmptyAndFullAsExamples() {
        for (seed in 0..99) {
            val frames = frameSession(MathStage.ZERO_5, seed)
            assertEquals((0..5).toSet(), frames.map { it.filled }.toSet())
            assertEquals(6, frames.distinct().size)
            assertEquals(0, frames[3].filled)
            assertEquals(5, frames[4].filled)
        }
    }

    @Test fun bigFrameKeepsFiveFrameWarmupsAndNeverRepeatsAFrame() {
        for (seed in 0..99) {
            val frames = frameSession(MathStage.MIXED_10, seed)
            assertEquals(List(2) { 5 } + List(4) { 10 }, frames.map { it.capacity })
            assertEquals(6, frames.distinct().size)
            assertEquals(0, frames[3].filled)
            assertEquals(10, frames[4].filled)
        }
    }

    @Test fun answerPositionVariesAndIsBalancedOverASessionForEveryGame() = runBlocking {
        for (stage in MathStage.entries) for (seed in 0..39) {
            val friends = FriendsGame(Random(seed), Store(stage)); friends.start()
            val frame = FrameGame(Random(seed), Store(stage)); frame.start()
            val friendSlots = mutableListOf<Int>()
            val frameSlots = mutableListOf<Int>()
            repeat(6) {
                friendSlots += friends.state.choices.indexOf(friends.state.round!!.result)
                frameSlots += frame.state.choices.indexOf(frame.state.round!!.missing)
                friends.solve(); friends.next()
                frame.onAnswer(frame.state.round!!.missing); frame.next()
            }
            for (slots in listOf(friendSlots, frameSlots)) {
                assertEquals(setOf(0, 1, 2, 3), slots.take(4).toSet())
                assertTrue(slots.zipWithNext().all { (a, b) -> a != b })
                val counts = (0..3).map { slot -> slots.count { it == slot } }
                assertTrue("$stage $seed $slots", counts.max() - counts.min() <= 1)
            }
        }
    }

    @Test fun shownChoicesAreNotAlwaysAscending() = runBlocking {
        var unsorted = 0
        for (seed in 0..19) {
            val game = FriendsGame(Random(seed), Store(MathStage.MIXED_10)); game.start()
            repeat(6) {
                if (game.state.choices != game.state.choices.sorted()) unsorted++
                game.solve(); game.next()
            }
        }
        assertTrue("only $unsorted of 120 rounds were shuffled", unsorted > 60)
    }

    @Test fun choicesStayPutThroughMistakesReplayAndRereads() = runBlocking {
        val friends = FriendsGame(Random(21), Store(MathStage.MIXED_5)); friends.start()
        val frame = FrameGame(Random(21), Store(MathStage.MIXED_5)); frame.start()
        val friendChoices = friends.state.choices
        val frameChoices = frame.state.choices
        friends.question()
        friends.replay(); frame.replay()
        repeat(2) {
            friends.onAnswer(friends.state.choices.first { it != friends.state.round!!.result })
            frame.onAnswer(frame.state.choices.first { it != frame.state.round!!.missing })
            friends.replay(); frame.replay()
            assertEquals(friendChoices, friends.state.choices)
            assertEquals(frameChoices, frame.state.choices)
        }
        friends.onAnswer(friends.state.round!!.result); frame.onAnswer(frame.state.round!!.missing)
        assertEquals(friendChoices, friends.state.choices)
        assertEquals(frameChoices, frame.state.choices)
    }

    @Test fun anotherVisitOpensWithProblemsTheLastVisitDidNotShow() {
        for (seed in 0..49) {
            // Plenty of room: nothing from the previous visit comes back.
            val friends = InMemoryMathHistoryStore()
            val first = friendsSession(MathStage.MIXED_10, seed, friends)
            val second = friendsSession(MathStage.MIXED_10, seed + 1000, friends)
            assertTrue(first.intersect(second.toSet()).isEmpty())
            // Picnic's exact-quantity opening: the two unseen baskets come before the shown ones.
            val picnic = InMemoryMathHistoryStore()
            val before = picnicSession(MathStage.JOIN_5, seed, picnic)
            val after = picnicSession(MathStage.JOIN_5, seed + 1000, picnic)
            assertTrue(before.take(3).intersect(after.take(2).toSet()).isEmpty())
            // Early frame plays all four every visit; the opening one is the one the last visit ended without.
            val frame = InMemoryMathHistoryStore()
            val previous = frameSession(MathStage.JOIN_5, seed, frame)
            val next = frameSession(MathStage.JOIN_5, seed + 1000, frame)
            assertEquals((1..4).toSet() - previous.takeLast(3).map { it.filled }.toSet(), setOf(next.first().filled))
        }
    }

    @Test fun historyIsSavedAsRoundsStartSoAnAbandonedVisitStillCounts() = runBlocking {
        val history = InMemoryMathHistoryStore()
        val game = PicnicGame(Random(2), Store(), history)
        game.start()
        assertEquals(1, history.load(MathGameId.PICNIC).size)
        game.solve(); game.next()
        assertEquals(2, history.load(MathGameId.PICNIC).size)
        assertTrue(history.load(MathGameId.FRIENDS).isEmpty())
        repeat(5) { game.solve(); game.next() }
        repeat(3) {
            val visit = PicnicGame(Random(it), Store(), history)
            visit.start(); repeat(6) { visit.solve(); visit.next() }
        }
        assertEquals(12, history.load(MathGameId.PICNIC).size)
    }
}
