package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Collects what a game reports, and every voice key it asked for. */
class Log {
    var stars = 0
    var slips = 0
    var finished = 0
    var finishedRounds = -1
    var memory: SchedulerMemory? = null
    val events = RoundEvents(
        onStar = { stars++ },
        onSlip = { slips++ },
        onFinish = { rounds, m -> finished++; finishedRounds = rounds; memory = m }
    )
}

private fun idOfClip(key: String) = BigLetters.all.firstOrNull { it.voiceKey == key }?.id

/** The letter the prompt names (what a listening child would know). */
private fun named(state: LetterGameState): String =
    state.cue.keys.mapNotNull(::idOfClip).first()

private fun LetterGameState.correctPosition(id: String) = tiles.indexOfFirst { it.id == id }

class ListenFindGameTest {
    private fun game(seed: Int, log: Log, memory: SchedulerMemory = SchedulerMemory()): ListenFindGame {
        val random = Random(seed)
        return ListenFindGame(RoundScheduler(memory, random), log.events).also { it.start() }
    }

    /** Plays a session; [wrongFirst] says for which rounds a wrong tile is tapped first. Returns the targets. */
    private fun play(g: ListenFindGame, wrongFirst: (Int) -> Boolean = { false }, onRound: (LetterGameState, String) -> Unit = { _, _ -> }): List<String> {
        val targets = mutableListOf<String>()
        while (g.state.phase == LetterPhase.ROUND) {
            val target = named(g.state)
            targets.add(target)
            onRound(g.state, target)
            if (wrongFirst(g.state.roundIndex)) g.onAnswer(g.state.tiles.first { it.id != target }.id)
            g.onAnswer(target)
            g.next()
        }
        return targets
    }

    @Test
    fun aCleanSessionIsEightDifferentLettersAndFinishesOnce() {
        for (seed in 0 until 50) {
            val log = Log()
            val g = game(seed, log)
            val targets = play(g)
            assertEquals(8, targets.size)
            assertEquals("seed $seed", 8, targets.toSet().size)
            assertEquals(8, log.stars)
            assertEquals(0, log.slips)
            assertEquals(1, log.finished)
            assertEquals(8, log.finishedRounds)
            assertEquals(LetterPhase.COMPLETE, g.state.phase)
            assertTrue(g.state.sessionComplete)
            assertEquals(8, log.memory!!.records.size)
        }
    }

    @Test
    fun theStateNeverShowsOrWritesTheTarget() {
        val g = game(5, Log())
        play(g) { state, target ->
            val glyph = BigLetters[target].glyph
            assertEquals(null, state.shown)
            assertEquals(null, state.compare)
            assertTrue(state.tiles.none { it.mark != TileMark.NONE })  // nothing hinted at the start
            assertFalse("glyph in ${state.copy(tiles = emptyList())}", state.copy(tiles = emptyList()).toString().contains(glyph))
            assertTrue(state.cue.keys.none { it.contains(glyph) })
            assertEquals(null, state.feedback)
        }
    }

    @Test
    fun tilesAreThreeThenFourAndNeverConfusableWithTheTarget() {
        for (seed in 0 until 50) {
            val fresh = game(seed, Log())
            play(fresh) { state, target ->
                assertEquals(3, state.tiles.size)
                assertEquals(3, state.tiles.map { it.id }.toSet().size)
                assertTrue(target in state.tiles.map { it.id })
                state.tiles.filter { it.id != target }.forEach { assertFalse("${it.id} vs $target", BigLetters.areConfusable(it.id, target)) }
            }
            val knowing = SchedulerMemory(3, BigLetters.base.take(12).associate { it.id to io.github.tomsadeh.puki.learning.ItemRecord(1, 1, 0) })
            play(game(seed, Log(), knowing)) { state, _ -> assertEquals(4, state.tiles.size) }
        }
    }

    @Test
    fun aWrongTapSaysWhatHeTappedAndWhatWeWantThenHintsAfterTwo() {
        val log = Log()
        val g = game(1, log)
        val target = named(g.state)
        val wrong = g.state.tiles.filter { it.id != target }
        val before = g.state.cue.token
        val round = g.state.roundIndex

        g.onAnswer(wrong[0].id)
        assertEquals(LetterFeedback(wrong[0].id, target), g.state.feedback)
        assertEquals(listOf(BigLetters[wrong[0].id].voiceKey, LetterVoice.LOOKING_FOR, BigLetters[target].voiceKey), g.state.cue.keys)
        assertNotEquals(before, g.state.cue.token)
        assertEquals(TileMark.WRONG, g.state.tiles.first { it.id == wrong[0].id }.mark)
        assertEquals(TileMark.NONE, g.state.tiles.first { it.id == target }.mark)
        assertEquals(round, g.state.roundIndex)
        assertFalse(g.state.solved)

        g.onAnswer(wrong[0].id)  // the same tile again is not a new miss
        assertEquals(1, g.state.misses)

        g.onAnswer(wrong[1].id)
        assertEquals(TileMark.HINT, g.state.tiles.first { it.id == target }.mark)
        assertEquals(LetterVoice.HINT, g.state.cue.keys.last())
        assertEquals(2, g.state.misses)
        assertEquals(1, log.slips)

        g.next()  // not solved yet: nothing happens
        assertEquals(round, g.state.roundIndex)
        g.onAnswer(target)
        assertTrue(g.state.solved)
        assertEquals(TileMark.CORRECT, g.state.tiles.first { it.id == target }.mark)
        g.onAnswer(wrong[0].id)  // after the find, taps are ignored
        assertEquals(TileMark.CORRECT, g.state.tiles.first { it.id == target }.mark)
        g.next()
        assertEquals(round + 1, g.state.roundIndex)
        assertFalse(g.state.solved)
    }

    @Test
    fun aMissedLetterReturnsTwoOrThreeRoundsLaterWithAnotherPlaceAndOtherNeighbours() {
        for (seed in 0 until 100) {
            val g = game(seed, Log())
            val seenAt = HashMap<Int, LetterGameState>()
            val targets = play(g, wrongFirst = { it == 0 }, onRound = { s, _ -> seenAt[s.roundIndex] = s })
            val missed = targets[0]
            val back = targets.indices.filter { it > 0 && targets[it] == missed }
            assertEquals("seed $seed: $targets", 1, back.size)
            assertTrue("seed $seed: back at ${back[0]}", back[0] == 2 || back[0] == 3)
            val first = seenAt[0]!!
            val again = seenAt[back[0]]!!
            assertNotEquals("seed $seed", first.correctPosition(missed), again.correctPosition(missed))
            assertTrue("seed $seed", (first.tiles.map { it.id }.toSet() - missed).intersect(again.tiles.map { it.id }.toSet()).isEmpty())
        }
    }

    @Test
    fun theRightTileIsInABalancedPlace() {
        for (seed in 0 until 100) {
            val positions = mutableListOf<Int>()
            play(game(seed, Log())) { state, target -> positions.add(state.correctPosition(target)) }
            val counts = (0 until 3).map { p -> positions.count { it == p } }
            assertTrue("seed $seed: $positions", counts.max() - counts.min() <= 1)
        }
        for (seed in 0 until 30) {
            val knowing = SchedulerMemory(3, BigLetters.base.take(12).associate { it.id to io.github.tomsadeh.puki.learning.ItemRecord(1, 1, 0) })
            val positions = mutableListOf<Int>()
            play(game(seed, Log(), knowing)) { state, target -> positions.add(state.correctPosition(target)) }
            assertEquals("seed $seed: $positions", listOf(2, 2, 2, 2), (0 until 4).map { p -> positions.count { it == p } })
        }
    }

    @Test
    fun replayPlaysThePromptAgainWithANewToken() {
        val g = game(2, Log())
        val prompt = g.state.cue.keys
        assertEquals(LetterVoice.LISTEN_INTRO, prompt.first())  // the very first round starts with the intro
        val token = g.state.cue.token
        g.replay()
        assertEquals(prompt.drop(1), g.state.cue.keys)  // replay: the prompt alone
        assertNotEquals(token, g.state.cue.token)
        assertEquals(listOf(BigLetters[named(g.state)].voiceKey, LetterVoice.FIND_IT), g.state.cue.keys)
    }

    @Test
    fun finalFormsWaitUntilTheLettersAreKnown() {
        for (seed in 0 until 100) {
            val targets = play(game(seed, Log()))
            assertTrue("seed $seed", targets.none { BigLetters[it].isFinal })
        }
        val known = SchedulerMemory(5, BigLetters.base.associate { it.id to io.github.tomsadeh.puki.learning.ItemRecord(2, 4, 0) })
        var sessionsWithFinals = 0
        for (seed in 0 until 100) {
            val finals = play(game(seed, Log(), known)).filter { BigLetters[it].isFinal }
            assertTrue("seed $seed", finals.size <= ListenFindGame.MAX_FINALS)
            if (finals.isNotEmpty()) sessionsWithFinals++
        }
        assertTrue("finals show up once the letters are known ($sessionsWithFinals)", sessionsWithFinals > 20)
        // only the finals whose ordinary letter is known
        val partly = SchedulerMemory(5, BigLetters.base.associate { it.id to io.github.tomsadeh.puki.learning.ItemRecord(2, 4, if (it.id == "mem") 1 else 0) })
        for (seed in 0 until 100) {
            assertTrue(play(game(seed, Log(), partly)).none { it == "mem_final" })
        }
    }

    @Test
    fun overManySessionsAllTwentySevenAreAsked() {
        var memory = SchedulerMemory()
        val asked = mutableSetOf<String>()
        for (n in 0 until 40) {
            val log = Log()
            asked += play(game(n, log, memory))
            memory = log.memory!!
        }
        assertEquals(BigLetters.all.map { it.id }.toSet(), asked)
    }

    @Test
    fun aMissedLetterIsMoreLikelyInTheNextSession() {
        var back = 0
        for (seed in 0 until 100) {
            val log = Log()
            val first = play(game(seed, log), wrongFirst = { it == 0 })
            val missed = first[0]
            val second = play(game(seed + 500, Log(), log.memory!!))
            if (missed in second) back++
        }
        assertTrue("back $back of 100", back > 40)  // a clean letter comes back about 1 time in 4
    }

    @Test
    fun allBaseLettersAreCoveredWithinThreeReconstructedVisits() {
        for (seed in 0 until 100) {
            var saved = SchedulerMemory().encode()
            val asked = mutableListOf<String>()
            repeat(3) { visit ->
                val log = Log()
                asked += play(game(seed + visit, log, SchedulerMemory.decode(saved)))
                saved = log.memory!!.encode()
            }
            assertTrue("seed $seed: $asked", asked.containsAll(BigLetters.base.map { it.id }))
        }
    }

    @Test
    fun exactUnlockThresholdsAndFinalCapsSurviveCoverageHistory() {
        val baseIds = BigLetters.base.map { it.id }
        val learningOrder = (BigLetters.finals.map { it.baseId!! } + baseIds).distinct()
        for (known in listOf(9, 10, 13, 14)) for (seed in 0 until 30) {
            val memory = SchedulerMemory(
                records = learningOrder.take(known).associateWith { io.github.tomsadeh.puki.learning.ItemRecord(1, 1, 0) },
                covered = baseIds.toSet()
            )
            val asked = play(game(seed, Log(), memory)) { state, _ ->
                assertEquals(if (known < 10) 3 else 4, state.tiles.size)
            }
            assertEquals(if (known < 14) 0 else 2, asked.count { BigLetters[it].isFinal })
        }
    }

    @Test
    fun anUnfinishedVisitRestartsAtRoundZeroWithoutAwardingMastery() {
        val scheduler = RoundScheduler(random = Random(8))
        val first = ListenFindGame(scheduler, Log().events).also { it.start() }
        val opening = named(first.state)
        first.onAnswer(opening)
        first.next()
        val second = named(first.state)
        val saved = SchedulerMemory.decode(scheduler.checkpoint().encode())
        val next = game(8, Log(), saved)
        assertEquals(0, next.state.roundIndex)
        assertEquals(0, next.state.starsEarned)
        assertTrue(saved.records.isEmpty())
        assertFalse(named(next.state) in setOf(opening, second))
        val tiles = next.state.tiles
        next.replay()
        assertEquals(tiles, next.state.tiles)
    }
}

class LetterDetectiveGameTest {
    private fun game(seed: Int, log: Log, memory: SchedulerMemory = SchedulerMemory()): LetterDetectiveGame {
        val random = Random(seed)
        return LetterDetectiveGame(RoundScheduler(memory, random), random, log.events).also { it.start() }
    }

    /** Plays a session. [wrongFirst] picks the rounds that start with a wrong tap. Records every state seen. */
    private fun play(g: LetterDetectiveGame, wrongFirst: (Int) -> Boolean = { false }): List<LetterGameState> {
        val seen = mutableListOf<LetterGameState>()
        while (g.state.phase != LetterPhase.COMPLETE) {
            seen.add(g.state)
            if (g.state.phase == LetterPhase.COMPARE) {
                g.next()
            } else {
                val target = named(g.state)
                if (wrongFirst(g.state.roundIndex)) g.onAnswer(g.state.tiles.first { it.id != target }.id)
                g.onAnswer(target)
                g.next()
            }
        }
        return seen
    }

    @Test
    fun twoPairsEachWithACompareStepThenFourQuestions() {
        for (seed in 0 until 50) {
            val log = Log()
            val g = game(seed, log)
            val seen = play(g)
            val compares = seen.filter { it.phase == LetterPhase.COMPARE }
            val rounds = seen.filter { it.phase == LetterPhase.ROUND }
            assertEquals(2, compares.size)
            assertEquals(8, rounds.size)
            assertEquals((0 until 8).toList(), rounds.map { it.roundIndex })
            assertEquals(LetterPhase.COMPARE, seen.first().phase)
            assertEquals(1, log.finished)
            assertEquals(8, log.stars)
            assertEquals(2, log.memory!!.records.size)  // the two pairs
            assertEquals(compares.map { it.compare!!.line }.toSet().size, 2)
        }
    }

    @Test
    fun theCompareStepCarriesBothLettersAndTheDifferenceLine() {
        val g = game(3, Log())
        val s = g.state
        val c = s.compare!!
        val pair = BigLetters.pairOf(c.a.id, c.b.id)!!
        assertEquals(pair.compareLine, c.line)
        assertEquals(BigLetters[pair.a].glyph, c.a.glyph)
        assertEquals(listOf(LetterVoice.DETECTIVE_INTRO, BigLetters[pair.a].voiceKey, BigLetters[pair.b].voiceKey, pair.compareKey), s.cue.keys)
        assertTrue(s.tiles.isEmpty())
        g.replay()
        assertEquals(listOf(BigLetters[pair.a].voiceKey, BigLetters[pair.b].voiceKey, pair.compareKey), g.state.cue.keys)
        g.onAnswer(c.a.id)  // tapping in the compare step does nothing
        assertEquals(LetterPhase.COMPARE, g.state.phase)
    }

    @Test
    fun questionsOfferThePairAndOneClearlyDifferentLetter() {
        for (seed in 0 until 50) {
            var pairIds: Set<String> = emptySet()
            val asked = mutableMapOf<Set<String>, MutableList<String>>()
            for (s in play(game(seed, Log()))) {
                if (s.phase == LetterPhase.COMPARE) {
                    pairIds = setOf(s.compare!!.a.id, s.compare!!.b.id)
                    continue
                }
                val target = named(s)
                assertTrue(target in pairIds)
                assertEquals(3, s.tiles.size)
                assertTrue(s.tiles.map { it.id }.containsAll(pairIds))
                val other = s.tiles.map { it.id }.first { it !in pairIds }
                pairIds.forEach { assertEquals("seed $seed: $other vs $it", 2, BigLetters.visualDistance(other, it)) }
                asked.getOrPut(pairIds) { mutableListOf() }.add(target)
            }
            for ((_, list) in asked) {
                assertEquals(4, list.size)
                assertEquals("both letters are asked, twice each: $list", 2, list.count { it == list[0] })
            }
        }
    }

    @Test
    fun aMissedLetterComesBackWithinTheSamePair() {
        for (seed in 0 until 50) {
            val states = play(game(seed, Log()), wrongFirst = { it == 0 })
            val rounds = states.filter { it.phase == LetterPhase.ROUND }
            val targets = rounds.map(::named)
            val back = (1 until 4).filter { targets[it] == targets[0] }
            assertTrue("seed $seed: $targets", back.any { it == 2 || it == 3 })
            assertNotEquals(rounds[0].correctPosition(targets[0]), rounds[back.first { it >= 2 }].correctPosition(targets[0]))
        }
    }

    @Test
    fun mixingUpThePairRepeatsTheDifference() {
        val g = game(7, Log())
        g.next()
        val target = named(g.state)
        val partner = g.state.tiles.map { it.id }.first { BigLetters.pairOf(it, target) != null }
        g.onAnswer(partner)
        val pair = BigLetters.pairOf(partner, target)!!
        assertEquals(listOf(BigLetters[partner].voiceKey, LetterVoice.LOOKING_FOR, BigLetters[target].voiceKey, pair.compareKey), g.state.cue.keys)
    }

    @Test
    fun answerPlacesAreBalancedAcrossTheSession() {
        for (seed in 0 until 100) {
            val positions = play(game(seed, Log())).filter { it.phase == LetterPhase.ROUND }.map { it.correctPosition(named(it)) }
            val counts = (0 until 3).map { p -> positions.count { it == p } }
            assertTrue("seed $seed: $positions", counts.max() - counts.min() <= 1)
        }
    }

    @Test
    fun everyPairComesUpOverSessions() {
        var memory = SchedulerMemory()
        val asked = mutableSetOf<String>()
        val visits = (BigLetters.pairs.size + LetterDetectiveGame.PAIRS_PER_SESSION - 1) / LetterDetectiveGame.PAIRS_PER_SESSION
        for (n in 0 until visits) {
            val log = Log()
            play(game(n, log, memory)).filter { it.phase == LetterPhase.COMPARE }.forEach { asked += BigLetters.pairOf(it.compare!!.a.id, it.compare!!.b.id)!!.id }
            memory = SchedulerMemory.decode(log.memory!!.encode())
        }
        assertEquals(BigLetters.pairs.map { it.id }.toSet(), asked)
    }

    @Test
    fun everyBalancedFourQuestionOrderIsReachable() {
        val orders = mutableSetOf<String>()
        for (seed in 0 until 200) {
            val states = play(game(seed, Log()))
            for (block in states.chunked(5)) {
                val pair = block.first().compare!!
                val order = block.drop(1).map { if (named(it) == pair.a.id) 'A' else 'B' }.joinToString("")
                assertEquals(2, order.count { it == 'A' })
                assertEquals(2, order.count { it == 'B' })
                orders += order
            }
        }
        assertEquals(setOf("AABB", "ABAB", "ABBA", "BAAB", "BABA", "BBAA"), orders)
    }

    @Test
    fun retriesUseScheduledOccurrencesWithoutAnImmediateDuplicate() {
        for (seed in 0 until 100) for (missAt in 0..1) {
            val rounds = play(game(seed, Log()), wrongFirst = { it == missAt })
                .filter { it.phase == LetterPhase.ROUND }.take(4)
            val targets = rounds.map(::named)
            val missed = targets[missAt]
            val retry = (missAt + 2..3).firstOrNull { targets[it] == missed }
            assertTrue("seed $seed miss $missAt: $targets", retry != null)
            assertNotEquals(missed, targets[retry!! - 1])
            if (retry < 3) assertNotEquals(missed, targets[retry + 1])
            assertEquals(2, targets.toSet().size)
        }
    }

    @Test
    fun detectiveLayoutsAreRememberedPerPairAndTarget() {
        val first = RoundScheduler(random = Random(2))
        val g = LetterDetectiveGame(first, Random(3), Log().events).also { it.start() }
        play(g)
        val memory = SchedulerMemory.decode(first.finish().encode())
        assertEquals(4, memory.presentations.size)
        assertTrue(memory.presentations.keys.all { key -> BigLetters.pairs.any { key.startsWith("${it.id}_") } })
    }
}

class FinalPartnersGameTest {
    private fun game(seed: Int, log: Log, memory: SchedulerMemory = SchedulerMemory()): FinalPartnersGame {
        val random = Random(seed)
        return FinalPartnersGame(RoundScheduler(memory, random), log.events).also { it.start() }
    }

    private fun play(g: FinalPartnersGame, wrongFirst: (Int) -> Boolean = { false }, onRound: (LetterGameState) -> Unit = {}): List<String> {
        val shown = mutableListOf<String>()
        while (g.state.phase == LetterPhase.ROUND) {
            val s = g.state
            onRound(s)
            shown.add(s.shown!!.id)
            val partner = BigLetters.partnerOf(s.shown!!.id)!!
            if (wrongFirst(s.roundIndex)) g.onAnswer(s.tiles.first { it.id != partner }.id)
            g.onAnswer(partner)
            g.next()
        }
        return shown
    }

    @Test
    fun oneLetterAndThreeCandidatesForItsPartner() {
        for (seed in 0 until 50) {
            val log = Log()
            val shown = play(game(seed, log)) { s ->
                val item = s.shown!!.id
                val partner = BigLetters.partnerOf(item)!!
                assertEquals(3, s.tiles.size)
                assertEquals(3, s.tiles.map { it.id }.toSet().size)
                assertTrue(partner in s.tiles.map { it.id })
                assertFalse(item in s.tiles.map { it.id })
                s.tiles.filter { it.id != partner }.forEach {
                    assertEquals("${it.id} vs $item/$partner", 2, minOf(BigLetters.visualDistance(it.id, partner), BigLetters.visualDistance(it.id, item)))
                }
            }
            assertEquals(8, shown.size)
            assertEquals(8, shown.toSet().size)
            assertEquals(1, log.finished)
            assertEquals(8, log.stars)
        }
    }

    @Test
    fun thePromptNeverNamesThePartnerButFindingItDoes() {
        val g = game(4, Log())
        while (g.state.phase == LetterPhase.ROUND) {
            val s = g.state
            val partner = BigLetters.partnerOf(s.shown!!.id)!!
            assertFalse(BigLetters[partner].voiceKey in s.cue.keys)
            assertEquals(BigLetters[s.shown!!.id].voiceKey, s.cue.keys.first { k -> BigLetters.all.any { it.voiceKey == k } })
            g.onAnswer(partner)
            assertEquals(listOf(BigLetters[partner].voiceKey), g.state.cue.keys)  // now he hears its name
            g.next()
        }
    }

    @Test
    fun aWrongCandidateIsNamedAndTheRoundGoesOn() {
        val g = game(9, Log())
        val shown = g.state.shown!!.id
        val partner = BigLetters.partnerOf(shown)!!
        val wrong = g.state.tiles.first { it.id != partner }.id
        g.onAnswer(wrong)
        assertEquals(listOf(BigLetters[wrong].voiceKey, LetterVoice.PARTNER_NOT_FRIEND), g.state.cue.keys)
        assertEquals(LetterFeedback(wrong, partner), g.state.feedback)
        val other = g.state.tiles.first { it.id != partner && it.id != wrong }.id
        g.onAnswer(other)
        assertEquals(TileMark.HINT, g.state.tiles.first { it.id == partner }.mark)
    }

    @Test
    fun aMissedPairComesBackAndPlacesAreBalanced() {
        for (seed in 0 until 100) {
            val positions = mutableListOf<Int>()
            val shown = play(game(seed, Log()), onRound = { s -> positions.add(s.correctPosition(BigLetters.partnerOf(s.shown!!.id)!!)) })
            assertEquals(8, shown.size)
            val counts = (0 until 3).map { p -> positions.count { it == p } }
            assertTrue("seed $seed: $positions", counts.max() - counts.min() <= 1)
        }
        for (seed in 0 until 100) {
            val shown = play(game(seed, Log()), wrongFirst = { it == 0 })
            val back = shown.indices.filter { it > 0 && shown[it] == shown[0] }
            assertTrue("seed $seed: $shown", back.size == 1 && (back[0] == 2 || back[0] == 3))
        }
    }

    @Test
    fun allTenLettersComeUpOverSessions() {
        var memory = SchedulerMemory()
        val asked = mutableSetOf<String>()
        for (n in 0 until 10) {
            val log = Log()
            asked += play(game(n, log, memory))
            memory = log.memory!!
        }
        assertEquals(BigLetters.finals.flatMap { listOf(it.id, it.baseId!!) }.toSet(), asked)
    }

    @Test
    fun allRelationshipsAppearBeforeReverseDirectionsAcrossVisits() {
        for (seed in 0 until 50) {
            var memory = SchedulerMemory()
            val coverage = mutableListOf<String>()
            repeat(5) { visit ->
                val log = Log()
                val asked = play(game(seed + visit, log, memory))
                assertEquals("seed $seed visit $visit: $asked", 5, asked.take(5).map(BigLetters::baseOf).toSet().size)
                coverage += asked
                memory = SchedulerMemory.decode(log.memory!!.encode())
            }
            coverage.chunked(10).forEach { cycle -> assertEquals("seed $seed: $coverage", cycle.size, cycle.toSet().size) }
        }
    }
}
