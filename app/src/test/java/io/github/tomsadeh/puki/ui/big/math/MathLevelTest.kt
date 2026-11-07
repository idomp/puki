package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.models.AppSettings
import io.github.tomsadeh.puki.data.models.MathLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class MathLevelTest {
    private class Store(var stage: MathStage = MathStage.JOIN_5) : MathProgressStore {
        override suspend fun load(game: MathGameId) = game.normalize(stage)
        override suspend fun save(game: MathGameId, stage: MathStage) { this.stage = stage }
    }

    private fun floorOf(level: MathLevel): suspend () -> MathStage = { level.floor }

    /** Plays six rounds; [missesIn] rounds get wrong answers first (enough to count as missed rounds). */
    private suspend fun FrameGame.play(missesIn: Int) {
        repeat(6) { index ->
            if (index < missesIn) {
                val wrong = state.choices.first { it != state.round!!.missing }
                repeat(5) { onAnswer(wrong) }
            }
            onAnswer(state.round!!.missing); next()
        }
    }

    @Test fun levelsMapToTheirFloors() {
        assertEquals(MathStage.JOIN_5, MathLevel.FROM_START.floor)
        assertEquals(MathStage.MIXED_10, MathLevel.WITHIN_TEN.floor)
        assertEquals(MathStage.PART_5, MathLevel.MAKE_THE_TOTAL.floor)
    }

    @Test fun theDefaultIsFromTheStartAndChangesNothing() = runBlocking {
        assertEquals(MathLevel.FROM_START, AppSettings().mathLevel)
        val store = Store(MathStage.TAKE_5)
        val game = FrameGame(Random(1), store)
        game.start()
        assertEquals(MathStage.TAKE_5, game.state.session.stage)
        val fresh = FrameGame(Random(2), Store(), floor = floorOf(MathLevel.FROM_START))
        fresh.start()
        assertEquals(MathStage.JOIN_5, fresh.state.session.stage)
    }

    @Test fun theFloorLiftsALowerSavedStageWhenASessionStarts() = runBlocking {
        for ((level, expected) in listOf(MathLevel.WITHIN_TEN to MathStage.MIXED_10, MathLevel.MAKE_THE_TOTAL to MathStage.PART_5)) {
            val store = Store(MathStage.TAKE_5)
            val game = FrameGame(Random(3), store, floor = floorOf(level))
            game.start()
            assertEquals(expected, game.state.session.stage)
            assertEquals(MathStage.TAKE_5, store.stage) // nothing is written until a session finishes
        }
    }

    @Test fun aHigherSavedStageIsKept() = runBlocking {
        val game = FrameGame(Random(4), Store(MathStage.PART_10), floor = floorOf(MathLevel.WITHIN_TEN))
        game.start()
        assertEquals(MathStage.PART_10, game.state.session.stage)
    }

    @Test fun theGameCeilingStillWinsForFriends() = runBlocking {
        val friends = FriendsGame(Random(5), Store(), floor = floorOf(MathLevel.MAKE_THE_TOTAL))
        friends.start()
        assertEquals(MathStage.MIXED_10, friends.state.session.stage)
        val picnic = PicnicGame(Random(5), Store(), floor = floorOf(MathLevel.MAKE_THE_TOTAL))
        picnic.start()
        assertEquals(MathStage.PART_5, picnic.state.session.stage)
        assertEquals(MathStage.MIXED_10, nextMathStage(MathGameId.FRIENDS, MathStage.MIXED_10, 6, 6, 6, MathStage.PART_5))
    }

    @Test fun demotionStopsAtTheFloor() = runBlocking {
        val store = Store(MathStage.PART_5)
        val game = FrameGame(Random(6), store, floor = floorOf(MathLevel.MAKE_THE_TOTAL))
        game.start()
        game.play(missesIn = 4)
        assertEquals(MathStage.PART_5, store.stage)
        assertEquals(MathStage.PART_5, game.state.session.nextStage)

        // Above the floor, easing still works down to it and no further.
        assertEquals(MathStage.PART_5, nextMathStage(MathGameId.FRAME, MathStage.PART_10, 6, 4, 4, MathStage.PART_5))
        assertEquals(MathStage.MIXED_10, nextMathStage(MathGameId.FRAME, MathStage.MIXED_10, 6, 6, 6, MathStage.MIXED_10))
        assertEquals(MathStage.ZERO_5, nextMathStage(MathGameId.FRAME, MathStage.MIXED_10, 6, 4, 4))
    }

    @Test fun removingTheFloorKeepsSavedStagesAndDemotesAsBefore() = runBlocking {
        val store = Store(MathStage.MIXED_10)
        FrameGame(Random(7), store, floor = floorOf(MathLevel.WITHIN_TEN)).also { it.start() }
        val game = FrameGame(Random(8), store, floor = floorOf(MathLevel.FROM_START))
        game.start()
        assertEquals(MathStage.MIXED_10, game.state.session.stage)
        game.play(missesIn = 4)
        assertEquals(MathStage.ZERO_5, store.stage)
    }

    @Test fun storedValuesParseAndUnknownMeansFromTheStart() {
        for (level in MathLevel.entries) assertEquals(level, MathLevel.fromStored(level.name))
        assertEquals(MathLevel.FROM_START, MathLevel.fromStored(null))
        assertEquals(MathLevel.FROM_START, MathLevel.fromStored(""))
        assertEquals(MathLevel.FROM_START, MathLevel.fromStored("EXPERT"))
    }
}
