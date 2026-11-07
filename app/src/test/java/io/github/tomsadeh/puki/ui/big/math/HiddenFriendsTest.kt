package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HiddenFriendsTest {
    private fun progress(stage: MathStage) = InMemoryMathProgressStore().also {
        runBlocking { it.save(MathGameId.FRIENDS, stage) }
    }

    private fun FriendsGame.question() { advancePhase(); advancePhase() }
    private suspend fun FriendsGame.solve() { question(); onAnswer(state.round!!.answer); next() }

    @Test fun partStagesUseHiddenAdditionWithSmallWarmupsAndTwoLaterStartUnknowns() = runBlocking {
        val laterOrders = mutableSetOf<List<FriendsUnknown>>()
        for (stage in listOf(MathStage.PART_5, MathStage.PART_10)) for (seed in 0..99) {
            val game = FriendsGame(Random(seed), progress(stage)); game.start()
            val rounds = mutableListOf<FriendsRound>()
            repeat(6) { index ->
                val round = game.state.round!!
                rounds += round
                assertEquals(stage, game.state.session.stage)
                assertTrue(round.isHidden)
                assertEquals(MathOperation.PLUS, round.direction)
                assertEquals(round.result, round.startCount + round.change)
                assertEquals(4, game.state.choices.distinct().size)
                assertTrue(round.answer in game.state.choices)
                assertTrue(game.state.choices.all { it in 0..friendsMaximum(stage, index) })
                if (stage == MathStage.PART_5 || index < 2) {
                    assertEquals(FriendsUnknown.CHANGE, round.unknown)
                    assertTrue(round.result in 3..5)
                    assertTrue(round.change in 1..3)
                } else if (round.unknown == FriendsUnknown.CHANGE) {
                    assertTrue(round.result in 6..10)
                    assertTrue(round.change in 1..4)
                } else {
                    assertEquals(FriendsUnknown.START, round.unknown)
                    assertTrue(round.startCount in 2..7)
                    assertTrue(round.change in 1..4)
                    assertTrue(round.result <= 10)
                }
                game.solve()
            }
            assertEquals(6, rounds.distinct().size)
            assertTrue(friendsCatalog(stage).containsAll(rounds))
            if (stage == MathStage.PART_10) {
                val later = rounds.drop(2).map { it.unknown }
                laterOrders += later
                assertEquals(2, later.count { it == FriendsUnknown.START })
                assertEquals(2, later.count { it == FriendsUnknown.CHANGE })
            }
        }
        assertEquals(6, laterOrders.size)
    }

    @Test fun bothUnknownsAnswerTheHiddenPartAndFillTheMatchingOperand() = runBlocking {
        val seen = mutableSetOf<FriendsUnknown>()
        val game = FriendsGame(Random(17), progress(MathStage.PART_10)); game.start()
        repeat(6) {
            val initial = game.state
            val round = initial.round!!
            seen += round.unknown
            val startUnknown = round.unknown == FriendsUnknown.START
            val expected = Equation(if (startUnknown) null else round.startCount, MathOperation.PLUS,
                if (startUnknown) round.change else null, round.result)
            assertEquals(expected, initial.equation)
            assertEquals(if (startUnknown) round.startCount else round.change, round.answer)
            assertEquals(if (startUnknown) round.change else round.startCount, round.visibleCount)
            game.onAnswer(round.answer) // story is still running
            assertEquals(initial, game.state)
            game.question()
            game.onAnswer(-1)
            assertEquals(0, game.state.session.roundMisses)
            val wrong = game.state.choices.first { it != round.answer }
            game.onAnswer(wrong)
            assertEquals(expected, game.state.equation)
            assertFalse(game.state.showHiddenOutlines)
            assertFalse(game.state.roundComplete)
            assertEquals(MathContent.hideHint, game.state.feedback!!.hint)
            game.onAnswer(wrong)
            assertTrue(game.state.showHiddenOutlines)
            assertTrue(game.state.session.showHint)
            assertEquals(initial.round, game.state.round)
            assertEquals(initial.choices, game.state.choices)
            game.onAnswer(round.answer)
            assertEquals(round.equation, game.state.equation)
            assertTrue(game.state.roundComplete)
            assertFalse(game.state.showHiddenOutlines)
            assertNull(game.state.feedback)
            val solved = game.state
            game.onAnswer(round.answer); game.advancePhase()
            assertEquals(solved, game.state)
            game.next()
            assertFalse(game.state.showHiddenOutlines)
        }
        assertEquals(setOf(FriendsUnknown.CHANGE, FriendsUnknown.START), seen)
    }

    @Test fun phasesReplayAndHintsUseTheAuthoredStoryOrderForBothUnknowns() = runBlocking {
        val game = FriendsGame(Random(12), progress(MathStage.PART_10)); game.start()
        var arrivals = 0
        repeat(6) {
            val round = game.state.round!!
            val startUnknown = round.unknown == FriendsUnknown.START
            val ask = if (startUnknown) MathContent.hideAskStart else MathContent.hideAsk
            val first = if (startUnknown) MathContent.hideStart else MathContent.birdStarts[round.startCount]
            val change = if (startUnknown) MathContent.birdArrivals[round.change]
                else MathContent.hideArrivals[arrivals++ % 2]
            val expected = listOf(first, change, MathContent.birdsNow.getValue(round.result), ask)
            assertEquals(listOf(first), game.state.voice.lines)
            game.advancePhase()
            assertEquals(listOf(change), game.state.voice.lines)
            game.advancePhase()
            assertEquals(expected.drop(2), game.state.voice.lines)
            assertEquals(expected, game.state.storyLines)
            val before = game.state
            game.replay()
            assertEquals(expected, game.state.voice.lines)
            assertTrue(game.state.voice.token > before.voice.token)
            assertEquals(before.copy(voice = game.state.voice), game.state)
            game.onAnswer(game.state.choices.first { it != round.answer })
            assertEquals(listOf(MathContent.hideHint, ask), game.state.voice.lines)
            game.replay()
            assertEquals(expected, game.state.voice.lines)
            game.onAnswer(round.answer)
            game.replay()
            assertEquals(expected, game.state.voice.lines)
            assertTrue(game.state.roundComplete)
            game.next()
        }
    }

    @Test fun replayDuringTheOpeningPlaysTheWholeStoryWithoutChangingTheProblem() = runBlocking {
        val game = FriendsGame(Random(3), progress(MathStage.PART_5)); game.start()
        val before = game.state
        game.replay()
        assertEquals(StoryPhase.QUESTION, game.state.phase)
        assertEquals(before.storyLines, game.state.voice.lines)
        assertEquals(before.round, game.state.round)
        assertEquals(before.choices, game.state.choices)
        assertEquals(before.equation, game.state.equation)
    }

    @Test fun hiddenHistoryAvoidsRecentProblemsAndSavesAnAbandonedVisit() = runBlocking {
        for (stage in listOf(MathStage.PART_5, MathStage.PART_10)) for (seed in 0..39) {
            val history = InMemoryMathHistoryStore()
            suspend fun visit(): List<FriendsRound> {
                val game = FriendsGame(Random(seed), progress(stage), history); game.start()
                return List(6) { game.state.round!!.also { game.solve() } }
            }
            val first = visit()
            val second = visit()
            assertEquals(6, second.distinct().size)
            if (stage == MathStage.PART_5) assertTrue(first.intersect(second.take(2).toSet()).isEmpty())
            else assertTrue(first.intersect(second.toSet()).isEmpty())
            assertEquals(12, history.load(MathGameId.FRIENDS).size)
            val partial = FriendsGame(Random(seed), progress(stage), history); partial.start()
            assertEquals(partial.state.round!!.historyKey, history.load(MathGameId.FRIENDS).last())
            assertTrue(history.load(MathGameId.PICNIC).isEmpty())
        }
        val total = FriendsRound(2, 3, MathOperation.PLUS)
        val change = total.copy(unknown = FriendsUnknown.CHANGE)
        val start = total.copy(unknown = FriendsUnknown.START)
        assertEquals("2+3", total.historyKey)
        assertEquals(3, listOf(total, change, start).map { it.historyKey }.distinct().size)
        assertTrue(listOf(total, change, start).none { ',' in it.historyKey })
    }

    @Test fun oldCountingHistoryStillLoadsBesideHiddenHistory() = runBlocking {
        val history = InMemoryMathHistoryStore()
        history.save(MathGameId.FRIENDS, listOf("1+2", "3-1"))
        val game = FriendsGame(Random(2), progress(MathStage.PART_5), history); game.start()
        assertEquals(listOf("1+2", "3-1", game.state.round!!.historyKey), history.load(MathGameId.FRIENDS))
    }
}
