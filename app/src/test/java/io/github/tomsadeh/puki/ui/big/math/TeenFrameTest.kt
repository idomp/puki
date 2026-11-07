package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TeenFrameTest {
    private val stages = MathStage.entries.filter { it >= MathStage.TEEN_COUNT }
    private fun store(stage: MathStage) = InMemoryMathProgressStore().also {
        runBlocking { it.save(MathGameId.FRAME, stage) }
    }

    @Test fun appendedStagesKeepSavedOrderAndEachGamesCeiling() {
        assertEquals(listOf("TEEN_COUNT", "TEEN_CROSS", "TEEN_MAKE", "TEEN_PART"), stages.map { it.name })
        assertEquals(MathStage.TEEN_PART, MathGameId.FRAME.maxStage)
        assertEquals(MathStage.PART_10, MathGameId.FRIENDS.maxStage)
        assertEquals(MathStage.PART_10, MathGameId.PICNIC.maxStage)
        for (stage in stages) {
            assertEquals(20, stage.maximum)
            assertFalse(stage.allowsZero)
            assertEquals(MathStage.PART_10, MathGameId.FRIENDS.normalize(stage))
            assertEquals(MathStage.PART_10, MathGameId.PICNIC.normalize(stage))
            assertEquals(stage, MathGameId.FRAME.normalize(stage))
            assertEquals(MathStage.entries[minOf(stage.ordinal + 1, MathStage.TEEN_PART.ordinal)],
                nextMathStage(MathGameId.FRAME, stage, 6, 1, 1))
            assertEquals(MathStage.entries[stage.ordinal - 1], nextMathStage(MathGameId.FRAME, stage, 6, 4, 4))
        }
    }

    @Test fun catalogsAndWarmupPoolsHaveTheExpectedRangesAndUniqueHistoryKeys() {
        assertEquals(listOf(10, 26, 100, 29), stages.map { frameCatalog(it).size })
        val catalogs = MathStage.entries.flatMap { frameCatalog(it) }.toSet()
        assertEquals(catalogs.size, catalogs.map { it.historyKey }.toSet().size)
        assertEquals("5:2", FrameRound(5, 2).historyKey)
        assertEquals("part:10:8:3", FrameRound(10, 3, 8).historyKey)
        for (stage in stages) for (index in 0..5) {
            val pool = framePool(stage, index)
            assertTrue(pool.isNotEmpty())
            for (round in pool) {
                assertEquals(20, round.capacity)
                assertTrue(round.answer > 0)
                when (stage) {
                    MathStage.TEEN_COUNT -> {
                        assertTrue(round.target in 11..if (index < 2) 14 else 20)
                        assertEquals(round.target, round.filled)
                        assertEquals(round.target, round.answer)
                    }
                    MathStage.TEEN_CROSS -> {
                        assertTrue(round.filled in 6..9 && round.target in 11..18)
                        assertTrue(round.missing in 1..if (index < 3) 5 else 9)
                        assertEquals(round.target, round.answer)
                    }
                    MathStage.TEEN_MAKE -> {
                        assertTrue(round.target in 11..20 && round.missing in 1..10)
                        assertTrue(round.filled in (if (index < 3) 10 else maxOf(1, round.target - 10)) until round.target)
                        assertEquals(round.missing, round.answer)
                    }
                    MathStage.TEEN_PART -> {
                        assertTrue(round.target in 11..18 && round.filled in 6..9)
                        assertTrue(round.answer in 1..10)
                        assertEquals(round.target - round.filled, round.answer)
                    }
                    else -> error("Unexpected stage")
                }
            }
        }
        assertTrue(framePool(MathStage.TEEN_MAKE, 3).any { it.filled < 10 })
        assertTrue(framePool(MathStage.TEEN_CROSS, 3).any { it.missing == 9 })
        assertTrue(frameCatalog(MathStage.TEEN_PART).any { it.answer == 10 })
    }

    @Test fun sixRoundSessionsHaveNoRepeatsAndChoicesStayInTheCorrectWindow() = runBlocking {
        var onesDistractors = 0
        for (stage in stages) for (seed in 0..99) {
            val game = FrameGame(Random(seed), store(stage))
            game.start()
            val rounds = mutableSetOf<FrameRound>()
            repeat(6) { index ->
                val state = game.state
                val round = state.round!!
                assertTrue("$stage $seed $index", rounds.add(round))
                assertTrue(round in framePool(stage, index))
                assertEquals(round.filled, state.cells.count { it })
                assertEquals(20, state.cells.size)
                assertEquals(4, state.choices.size)
                assertEquals(4, state.choices.distinct().size)
                assertTrue(round.answer in state.choices)
                val totalQuestion = stage == MathStage.TEEN_COUNT || stage == MathStage.TEEN_CROSS
                val ones = state.choices.filter { it < 11 }
                if (totalQuestion) {
                    assertTrue(ones.size <= 1)
                    if (ones.isNotEmpty()) { assertEquals(listOf(round.target % 10), ones); onesDistractors++ }
                    val totals = state.choices.filter { it >= 11 }
                    assertTrue(totals.all { it in 11..20 })
                    assertTrue(totals.max() - totals.min() <= 3)
                } else assertTrue(state.choices.all { it in 1..10 })
                game.onAnswer(round.answer); game.next()
            }
            assertTrue(game.state.session.sessionComplete)
            assertEquals(6, game.state.session.totalStarsEarned)
        }
        assertTrue(onesDistractors > 100)
    }

    @Test fun promptsRetriesReplayCorrectNumberAndEquationsFollowEachQuestion() = runBlocking {
        for (stage in stages) {
            val game = FrameGame(Random(17), store(stage))
            game.start()
            repeat(6) {
                val initial = game.state
                val round = initial.round!!
                val expected = when (stage) {
                    MathStage.TEEN_COUNT -> listOf(MathContent.teenHowMany)
                    MathStage.TEEN_CROSS -> listOf(MathContent.frameFilled[round.filled], MathContent.teenArrive, MathContent.teenHowManyNow)
                    MathStage.TEEN_MAKE -> listOf(MathContent.teenTarget, MathContent.howManyMissing)
                    else -> listOf(MathContent.frameFilled[round.filled], MathContent.teenPart)
                }
                assertEquals(expected, initial.voice.lines)
                if (stage == MathStage.TEEN_PART) assertEquals(Equation(round.filled, MathOperation.PLUS, null, round.target), initial.equation)
                else assertNull(initial.equation)
                game.onAnswer(-1); game.next()
                assertEquals(initial, game.state)
                game.replay()
                assertEquals(initial.copy(voice = game.state.voice), game.state)
                val wrong = initial.choices.first { it != round.answer }
                repeat(2) { game.onAnswer(wrong) }
                assertEquals(listOf(MathContent.teenHint, expected.last()), game.state.voice.lines)
                assertTrue(game.state.session.showHint)
                assertFalse(game.state.roundComplete)
                assertEquals(initial.choices, game.state.choices)
                assertEquals(round, game.state.round)
                game.replay()
                assertEquals(expected, game.state.voice.lines)
                game.onAnswer(round.answer)
                assertEquals(listOf(MathContent.exact, MathContent.teenNumbers.getValue(round.target)), game.state.voice.lines)
                if (stage == MathStage.TEEN_COUNT) assertNull(game.state.equation)
                else assertEquals(Equation(round.filled, MathOperation.PLUS, round.missing, round.target), game.state.equation)
                val solved = game.state
                game.onAnswer(round.answer)
                assertEquals(solved, game.state)
                game.replay()
                assertEquals(expected, game.state.voice.lines)
                game.next()
            }
        }
    }

    @Test fun recentAndAbandonedRoundsAreSavedAcrossVisitsWithoutKeyCollisions() = runBlocking {
        for (stage in stages) for (seed in 0..29) {
            val history = InMemoryMathHistoryStore()
            val game = FrameGame(Random(seed), store(stage), history)
            game.start()
            val before = mutableListOf<String>()
            repeat(6) {
                before += game.state.round!!.historyKey
                game.onAnswer(game.state.round!!.answer); game.next()
            }
            assertEquals(before, history.load(MathGameId.FRAME))
            val partial = FrameGame(Random(seed), store(stage), history); partial.start()
            val round = partial.state.round!!
            // Count warmups only have four totals, so the least-recent fallback can reuse a total.
            val pool = framePool(stage, 0).map { it.historyKey }
            if (pool.any { it !in before }) assertTrue(round.historyKey !in before)
            assertEquals(before + round.historyKey, history.load(MathGameId.FRAME))
            val next = FrameGame(Random(seed), store(stage), history); next.start()
            assertNotEquals(round, next.state.round)
            assertTrue(history.load(MathGameId.FRIENDS).isEmpty())
        }
    }

    @Test fun teenVoiceNamesAndGenderedHintAreAuthoredAndSettingsChipExists() {
        assertEquals((11..20).toSet(), MathContent.teenNumbers.keys)
        assertEquals(listOf("אחת-עשרה", "שתים-עשרה", "שלוש-עשרה", "ארבע-עשרה", "חמש-עשרה", "שש-עשרה", "שבע-עשרה", "שמונה-עשרה", "תשע-עשרה", "עשרים"),
            MathContent.teenNumbers.values.map { it.text.he })
        assertEquals("יש עשרה במסגרת המלאה. בואי נספור הלאה מעשר.", MathContent.teenHint.text.heGirl)
        assertEquals("יש עשרה במסגרת המלאה. בוא נספור הלאה מעשר.", MathContent.teenHint.text.he)
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "scripts/voice_packs/math.py").isFile }
        val settings = File(root, "app/src/main/java/io/github/tomsadeh/puki/ui/settings/SettingsScreen.kt").readText()
        assertTrue(settings.contains("MathLevel.TEN_AND_MORE to BilingualText(\"עשר ועוד\", \"Ten and more\")"))
    }
}
