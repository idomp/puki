package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.ItemRecord
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Tally {
    var stars = 0
    var slips = 0
    var finished = 0
    var memory: SchedulerMemory? = null
    val events = RoundEvents(onStar = { stars++ }, onSlip = { slips++ }, onFinish = { _, m -> finished++; memory = m })
}

class SoundShellsGameTest {
    private fun game(seed: Int, t: Tally, memory: SchedulerMemory = SchedulerMemory(), cards: Set<Int> = emptySet()): SoundShellsGame {
        val r = Random(seed)
        return SoundShellsGame(RoundScheduler(memory, r), r, t.events, cards).also { it.start() }
    }

    /** The child hears the prompt: the syllable clip in the cue is the target. */
    private fun targetId(g: SoundShellsGame): String {
        val clip = g.state.cue.keys.first { it in Nikud.SYLLABLE_CLIPS.values }
        return g.state.tiles.first { it.clipKey == clip }.id
    }

    private fun play(g: SoundShellsGame, wrongFirst: (Int) -> Boolean = { false }, onRound: (ShellsState, String) -> Unit = { _, _ -> }): List<String> {
        val targets = mutableListOf<String>()
        while (g.state.phase == NikudPhase.ROUND) {
            val target = targetId(g)
            targets.add(target)
            onRound(g.state, target)
            if (wrongFirst(g.state.roundIndex)) g.onAnswer(g.state.tiles.first { it.id != target }.id)
            g.onAnswer(target)
            g.next()
        }
        return targets
    }

    @Test
    fun stageOneIsEightDifferentConsonantsAllA() {
        for (seed in 0 until 40) {
            val t = Tally()
            val g = game(seed, t)
            assertEquals(1, g.state.stage)
            val targets = play(g) { s, _ ->
                assertEquals(3, s.tiles.size)
                assertEquals("three different consonants", 3, s.tiles.map { Nikud.syllable(it.id).consonant.id }.toSet().size)
                assertTrue(s.tiles.all { Nikud.syllable(it.id).sound == Nikud.Sound.A })
                // one spelling in a round: never kamatz beside patach
                assertEquals(1, s.tiles.map { Nikud.syllable(it.id).mark }.toSet().size)
                assertEquals(3, s.tiles.map { it.clipKey }.toSet().size)
            }
            assertEquals(8, targets.map { Nikud.syllable(it).consonant.id }.toSet().size)
            assertEquals(8, t.stars); assertEquals(0, t.slips); assertEquals(1, t.finished)
        }
    }

    @Test
    fun bothMarksAreBalancedInAShuffledOrder() {
        val orders = mutableSetOf<List<Nikud.Mark>>()
        for (seed in 0 until 100) {
            val marks = mutableListOf<Nikud.Mark>()
            play(game(seed, Tally()), onRound = { s, _ -> marks += Nikud.syllable(s.tiles[0].id).mark })
            assertEquals(4, marks.count { it == Nikud.Mark.PATAH })
            assertEquals(4, marks.count { it == Nikud.Mark.KAMATS })
            orders += marks
        }
        assertTrue("more than the two alternating orders", orders.size > 2)
        assertTrue(orders.any { order -> order.zipWithNext().any { (a, b) -> a == b } })
    }

    @Test
    fun theTargetSyllableIsNeverWrittenOutsideTheBoards() {
        play(game(3, Tally())) { s, target ->
            assertFalse(s.copy(tiles = emptyList()).toString().contains(Nikud.syllable(target).text))
            assertNull(s.foundBare)
        }
    }

    @Test
    fun stageTwoComesAfterTwoSolidSessionsAndKeepsTheSameConsonantInTwoSounds() {
        var memory = SchedulerMemory()
        var sessions = 0
        while (sessions < 5) {
            val t = Tally()
            val g = game(sessions, t, memory)
            if (g.state.stage == 2) break
            play(g)
            memory = t.memory!!
            sessions++
        }
        assertEquals("two clean sessions", 2, sessions)
        for (seed in 0 until 40) {
            val g = game(seed, Tally(), memory)
            assertEquals(2, g.state.stage)
            play(g) { s, target ->
                val t = Nikud.syllable(target)
                val syl = s.tiles.map { Nikud.syllable(it.id) }
                assertEquals(3, syl.size)
                assertEquals(2, syl.count { it.consonant == t.consonant })
                assertEquals(setOf(Nikud.Sound.A, Nikud.Sound.I), syl.filter { it.consonant == t.consonant }.map { it.sound }.toSet())
                assertEquals("no two boards sound the same", 3, s.tiles.map { it.clipKey }.toSet().size)
                assertEquals("one a-spelling at most", 1, syl.filter { it.sound == Nikud.Sound.A }.map { it.mark }.toSet().size)
            }
        }
    }

    @Test
    fun wrongTapPlaysItsSoundThenTheTargetAndTwoMissesHint() {
        val t = Tally()
        val g = game(1, t)
        val target = targetId(g)
        val wrong = g.state.tiles.filter { it.id != target }
        g.onAnswer(wrong[0].id)
        assertEquals(listOf(wrong[0].clipKey, Nikud.syllable(target).clipKey), g.state.cue.keys)
        assertEquals(SyllableFeedback(wrong[0].id, wrong[0].text), g.state.feedback)
        assertEquals(TileMark.WRONG, g.state.tiles.first { it.id == wrong[0].id }.mark)
        g.onAnswer(wrong[0].id)
        assertEquals(1, g.state.misses)
        g.onAnswer(wrong[1].id)
        assertEquals(TileMark.HINT, g.state.tiles.first { it.id == target }.mark)
        assertEquals(1, t.slips)
        g.onAnswer(target)
        assertTrue(g.state.solved)
        assertEquals(Nikud.syllable(target).consonant.bare, g.state.foundBare)
        val round = g.state.roundIndex
        g.next()
        assertEquals(round + 1, g.state.roundIndex)
    }

    @Test
    fun aMissedSyllableReturnsTwoOrThreeRoundsLaterElsewhere() {
        for (seed in 0 until 60) {
            val positions = HashMap<Int, Int>()
            val targets = play(game(seed, Tally()), wrongFirst = { it == 0 }, onRound = { s, target -> positions[s.roundIndex] = s.tiles.indexOfFirst { it.id == target } })
            val first = Nikud.syllable(targets[0]).consonant.id
            val back = targets.indices.filter { it > 0 && Nikud.syllable(targets[it]).consonant.id == first }
            assertEquals("seed $seed", 1, back.size)
            assertTrue(back[0] == 2 || back[0] == 3)
            assertNotEquals(positions[0], positions[back[0]])
        }
    }

    @Test
    fun theMeetTheMarkCardIsOfferedUntilSeen() {
        val knownStage1 = SchedulerMemory(4, SoundShellsGame.STAGE1_ITEMS.associateWith { ItemRecord(2, 3, 0) })
        assertEquals(1, game(1, Tally()).state.offerCard)
        assertNull(game(1, Tally(), cards = setOf(1)).state.offerCard)
        assertEquals(2, game(1, Tally(), knownStage1, cards = setOf(1)).state.offerCard)
    }

    @Test
    fun positionsAreBalanced() {
        for (seed in 0 until 60) {
            val pos = mutableListOf<Int>()
            play(game(seed, Tally())) { s, target -> pos += s.tiles.indexOfFirst { it.id == target } }
            val c = (0 until 3).map { p -> pos.count { it == p } }
            assertTrue("$pos", c.max() - c.min() <= 1)
        }
    }

    @Test
    fun stageTwoCoversAllSixteenItemsInTwoSavedVisitsWithBalancedMarks() {
        for (seed in 0 until 50) {
            var memory = SchedulerMemory(4, SoundShellsGame.STAGE1_ITEMS.associateWith { ItemRecord(2, 3, 0) })
            val asked = mutableListOf<String>()
            repeat(2) { visit ->
                val tally = Tally()
                val marks = mutableListOf<Nikud.Mark>()
                play(game(seed + visit, tally, memory)) { state, target ->
                    val syllable = Nikud.syllable(target)
                    asked += SoundShellsGame.stage2Item(syllable.consonant.id, syllable.sound)
                    marks += state.tiles.map { Nikud.syllable(it.id) }.first { it.sound == Nikud.Sound.A }.mark
                }
                assertEquals(4, marks.count { it == Nikud.Mark.PATAH })
                assertEquals(4, marks.count { it == Nikud.Mark.KAMATS })
                memory = SchedulerMemory.decode(tally.memory!!.encode())
            }
            assertEquals(SoundShellsGame.STAGE2_ITEMS.toSet(), asked.toSet())
        }
    }

    @Test
    fun stageTwoRequiresSixCleanItemsWithTwoCompletedSessions() {
        val ids = SoundShellsGame.STAGE1_ITEMS
        fun memory(solid: Int, seen: Int = 2, misses: Int = 0) = SchedulerMemory(
            records = ids.take(solid).associateWith { ItemRecord(seen, 2, misses) }
        )
        assertEquals(1, game(1, Tally(), memory(5)).state.stage)
        assertEquals(1, game(1, Tally(), memory(6, seen = 1)).state.stage)
        assertEquals(1, game(1, Tally(), memory(6, misses = 1)).state.stage)
        assertEquals(2, game(1, Tally(), memory(6)).state.stage)
    }
}

class JoinWordGameTest {
    private fun game(seed: Int, t: Tally, memory: SchedulerMemory = SchedulerMemory()): JoinWordGame {
        val r = Random(seed)
        return JoinWordGame(RoundScheduler(memory, r), r, t.events).also { it.start() }
    }

    /** Taps every unit in order, joins, and answers right (or wrong first when asked). */
    private fun playRound(g: JoinWordGame, wrongFirst: Boolean = false, onPicture: (JoinState) -> Unit = {}) {
        for (i in g.state.units.indices) g.onUnitTap(i)
        g.onJoined()
        if (g.state.phase == JoinPhase.PICTURE) {
            onPicture(g.state)
            if (wrongFirst) g.onAnswer(g.state.pictures.first { it.id != g.state.wordId }.id)
            g.onAnswer(g.state.wordId)
        }
        g.next()
    }

    @Test
    fun aWordIsJoinedInOrderWithASilentClosingLetter() {
        for (seed in 0 until 30) {
            val g = game(seed, Tally())
            while (g.state.phase == JoinPhase.UNITS) {
                val s = g.state
                val w = Nikud.word(s.wordId)
                assertEquals(w.units.map { it.text }, s.units.map { it.text })
                assertTrue(s.units[0].isNext && s.units.none { it.done })
                if (s.units.size > 1) {  // a later unit does nothing but nudge
                    g.onUnitTap(1)
                    assertEquals(s.nudge + 1, g.state.nudge)
                    assertTrue(g.state.units.none { it.done })
                    assertEquals(s.cue, g.state.cue)
                }
                for (i in w.units.indices) {
                    g.onUnitTap(i)
                    val u = w.units[i]
                    if (i < w.units.lastIndex) {
                        assertEquals(if (u.clipKey == null) emptyList() else listOf(u.clipKey), g.state.cue.keys)
                        assertTrue(g.state.units[i].done && g.state.units[i + 1].isNext)
                    }
                }
                // after the last unit: JOINED, its clip (if any), then the whole word
                assertEquals(JoinPhase.JOINED, g.state.phase)
                assertEquals(listOfNotNull(w.units.last().clipKey, w.wordKey), g.state.cue.keys)
                g.onUnitTap(0)  // nothing happens any more
                assertEquals(JoinPhase.JOINED, g.state.phase)
                g.onJoined()
                if (w.pictureId == null) {
                    assertEquals(JoinPhase.SOLVED, g.state.phase)
                    assertNull(g.state.foundPictureId)
                    assertFalse(g.state.hasPicture)
                } else {
                    assertEquals(JoinPhase.PICTURE, g.state.phase)
                    g.onAnswer(w.id)
                    assertEquals(JoinPhase.SOLVED, g.state.phase)
                    assertEquals(w.pictureId, g.state.foundPictureId)
                }
                g.next()
            }
            assertEquals(JoinPhase.COMPLETE, g.state.phase)
        }
    }

    @Test
    fun dagIsDaletThenALitGimel() {
        val g = (0 until 200).map { game(it, Tally()) }.first { it.state.wordId == "dag" }
        g.onUnitTap(0)
        assertEquals(listOf("voice_big_nikud_dalet_a"), g.state.cue.keys)
        g.onUnitTap(1)
        assertTrue(g.state.units[1].done)
        assertEquals(JoinPhase.JOINED, g.state.phase)
        assertEquals(listOf("voice_big_nikud_word_dag"), g.state.cue.keys)  // the closing letter itself says nothing
    }

    @Test
    fun picturesAreTheWordPlusTwoClearDifferentSoundingNoTwins() {
        for (seed in 0 until 100) {
            val g = game(seed, Tally())
            while (g.state.phase == JoinPhase.UNITS) {
                playRound(g) { s ->
                    val target = Nikud.word(s.wordId)
                    val words = s.pictures.map { Nikud.word(it.id) }
                    assertEquals(3, words.size)
                    assertTrue(target.id in words.map { it.id })
                    val rest = words.filter { it.id != target.id }
                    assertTrue("distractors are clear: ${rest.map { it.id }}", rest.all { it.tier == Nikud.Tier.CLEAR })
                    assertTrue(rest.all { it.firstConsonant != target.firstConsonant })
                    for (a in words) for (b in words) if (a.id != b.id) assertFalse("${a.id}/${b.id}", Nikud.areTwins(a, b))
                }
            }
        }
    }

    @Test
    fun aWrongPictureSaysWhatItShowsThenTheWordAndTheRoundGoesOn() {
        val g = (0 until 100).map { game(it, Tally()) }.first { it.state.hasPicture }
        for (i in g.state.units.indices) g.onUnitTap(i)
        g.onJoined()
        val wordId = g.state.wordId
        val wrong = g.state.pictures.first { it.id != wordId }
        g.onAnswer(wrong.id)
        assertEquals(listOf(Nikud.word(wrong.id).wordKey, Nikud.word(wordId).wordKey), g.state.cue.keys)
        assertEquals(PictureFeedback(wrong.id, Nikud.word(wrong.id).text), g.state.feedback)
        assertEquals(JoinPhase.PICTURE, g.state.phase)
        val other = g.state.pictures.first { it.id != wordId && it.id != wrong.id }
        g.onAnswer(other.id)
        assertEquals(TileMark.HINT, g.state.pictures.first { it.id == wordId }.mark)
        g.onAnswer(wordId)
        assertEquals(JoinPhase.SOLVED, g.state.phase)
    }

    @Test
    fun allTwelveWordsComeUpOverSessionsAndScenePicturesAreNeverDistractors() {
        var memory = SchedulerMemory()
        val asked = mutableSetOf<String>()
        for (n in 0 until 2) {
            val t = Tally()
            val g = game(n, t, memory)
            while (g.state.phase == JoinPhase.UNITS) {
                asked += g.state.wordId
                playRound(g) { s -> s.pictures.filter { it.id != s.wordId }.forEach { assertEquals(Nikud.Tier.CLEAR, Nikud.word(it.id).tier) } }
            }
            memory = SchedulerMemory.decode(t.memory!!.encode())
            assertEquals(8, t.stars)
        }
        assertEquals(Nikud.words.map { it.id }.toSet(), asked)
    }

    @Test
    fun aMissedWordReturnsTwoOrThreeRoundsLater() {
        var checked = 0
        for (seed in 0 until 60) {
            val g = game(seed, Tally())
            val order = mutableListOf<String>()
            while (g.state.phase == JoinPhase.UNITS) {
                order += g.state.wordId
                playRound(g, wrongFirst = order.size == 1 && g.state.hasPicture)
            }
            if (Nikud.word(order[0]).pictureId != null) {
                val back = order.indices.filter { it > 0 && order[it] == order[0] }
                assertTrue("seed $seed: $order", back.size == 1 && (back[0] == 3 || back[0] == 2))
                checked++
            }
        }
        assertTrue(checked > 30)
    }

    @Test
    fun reentryAndAgainCreateFreshJoiningSessions() {
        val scheduler = RoundScheduler(random = Random(5))
        val first = JoinWordGame(scheduler, Random(5), Tally().events).also { it.start() }
        val opening = first.state.wordId
        playRound(first)
        val unfinished = SchedulerMemory.decode(scheduler.checkpoint().encode())
        val tally = Tally()
        val reentered = game(5, tally, unfinished)
        assertEquals(JoinPhase.UNITS, reentered.state.phase)
        assertEquals(0, reentered.state.roundIndex)
        assertEquals(0, reentered.state.starsEarned)
        assertNotEquals(opening, reentered.state.wordId)
        assertTrue(unfinished.records.isEmpty())
        while (reentered.state.phase == JoinPhase.UNITS) playRound(reentered)
        val again = game(5, Tally(), SchedulerMemory.decode(tally.memory!!.encode()))
        assertEquals(JoinPhase.UNITS, again.state.phase)
        assertFalse(again.state.sessionComplete)
        assertEquals(0, again.state.roundIndex)
        assertEquals(0, again.state.starsEarned)
    }
}

class ReadSignGameTest {
    private fun game(seed: Int, t: Tally, memory: SchedulerMemory = SchedulerMemory()): ReadSignGame {
        val r = Random(seed)
        return ReadSignGame(RoundScheduler(memory, r), r, t.events).also { it.start() }
    }

    private val solidClear = SchedulerMemory(4, Nikud.clearWords.associate { it.id to ItemRecord(2, 3, 0) })

    private fun target(g: ReadSignGame) = Nikud.clearWords.first { it.text == g.state.signText }

    @Test
    fun nothingIsSaidBeforeAChoice() {
        for (seed in 0 until 30) {
            val t = Tally()
            val g = game(seed, t)
            assertEquals(listOf(Nikud.Voice.READ_SIGN), g.state.cue.keys)  // only the instruction, first round
            while (g.state.phase == NikudPhase.ROUND) {
                val w = target(g)
                if (g.state.roundIndex > 0) assertTrue(g.state.cue.keys.isEmpty())
                assertFalse(g.state.signHeard)
                assertTrue(g.state.pictures.all { Nikud.word(it.id).tier == Nikud.Tier.CLEAR })
                assertFalse(w.wordKey in g.state.cue.keys)
                g.onAnswer(w.id)
                assertEquals(listOf(w.wordKey), g.state.cue.keys)
                assertTrue(g.state.signHeard)
                assertEquals(w.pictureId, g.state.dockedPictureId)
                g.next()
            }
            assertEquals(8, t.stars); assertEquals(0, t.slips); assertEquals(0, g.state.assistedRounds)
        }
    }

    @Test
    fun replayBeforeAChoiceIsAssistedAndRecordedOnce() {
        val t = Tally()
        val g = game(2, t)
        val w = target(g)
        g.replay()
        assertEquals(listOf(w.wordKey), g.state.cue.keys)
        assertTrue(g.state.assisted); assertEquals(1, g.state.assistedRounds); assertEquals(1, t.slips)
        assertFalse("the sign is still plain wood", g.state.signHeard)
        g.replay()
        assertEquals(1, g.state.assistedRounds); assertEquals(1, t.slips)
        g.onAnswer(w.id)
        assertTrue(g.state.signHeard)
        g.next()
        assertFalse(g.state.assisted)
        assertEquals(1, g.state.assistedRounds)
    }

    @Test
    fun aWrongPictureSaysOnlyWhatItShowsAndTheSignStaysSilent() {
        val g = game(4, Tally())
        val w = target(g)
        val wrong = g.state.pictures.first { it.id != w.id }
        g.onAnswer(wrong.id)
        assertEquals(listOf(Nikud.word(wrong.id).wordKey), g.state.cue.keys)
        assertFalse(g.state.signHeard); assertFalse(g.state.solved)
        assertEquals(TileMark.WRONG, g.state.pictures.first { it.id == wrong.id }.mark)
        assertEquals(PictureFeedback(wrong.id, Nikud.word(wrong.id).text), g.state.feedback)
        val other = g.state.pictures.first { it.id != w.id && it.id != wrong.id }
        g.onAnswer(other.id)
        assertEquals(TileMark.HINT, g.state.pictures.first { it.id == w.id }.mark)
        assertFalse(g.state.assisted)
    }

    @Test
    fun levelOneDistractorsStartDifferentlyLevelTwoShareTheFirstConsonant() {
        for (seed in 0 until 60) {
            val one = game(seed, Tally())
            assertEquals(1, one.state.level)
            while (one.state.phase == NikudPhase.ROUND) {
                val w = target(one)
                val rest = one.state.pictures.filter { it.id != w.id }.map { Nikud.word(it.id) }
                assertTrue(rest.all { it.firstConsonant != w.firstConsonant })
                assertEquals(3, one.state.pictures.size)
                for (a in one.state.pictures) for (b in one.state.pictures) if (a.id != b.id) assertFalse(Nikud.areTwins(Nikud.word(a.id), Nikud.word(b.id)))
                one.onAnswer(w.id); one.next()
            }
            val two = game(seed, Tally(), solidClear)
            assertEquals(2, two.state.level)
            var sameStart = 0
            while (two.state.phase == NikudPhase.ROUND) {
                val w = target(two)
                val rest = two.state.pictures.filter { it.id != w.id }.map { Nikud.word(it.id) }
                val rivals = Nikud.clearWords.any { it.id != w.id && it.firstConsonant == w.firstConsonant && !Nikud.areTwins(it, w) }
                if (rivals) assertTrue(rest.any { it.firstConsonant == w.firstConsonant })
                if (rest.all { it.firstConsonant == w.firstConsonant }) sameStart++
                for (a in two.state.pictures) for (b in two.state.pictures) if (a.id != b.id) assertFalse(Nikud.areTwins(Nikud.word(a.id), Nikud.word(b.id)))
                two.onAnswer(w.id); two.next()
            }
            assertTrue(sameStart >= 1)
        }
    }

    @Test
    fun overSessionsAllNineWordsAreRead() {
        var memory = SchedulerMemory()
        val read = mutableSetOf<String>()
        for (n in 0 until 2) {
            val t = Tally()
            val g = game(n, t, memory)
            while (g.state.phase == NikudPhase.ROUND) { val w = target(g); read += w.id; g.onAnswer(w.id); g.next() }
            memory = SchedulerMemory.decode(t.memory!!.encode())
        }
        assertEquals(Nikud.clearWords.map { it.id }.toSet(), read)
    }

    @Test
    fun aMissedWordComesBackAndPositionsAreBalanced() {
        for (seed in 0 until 60) {
            val g = game(seed, Tally())
            val order = mutableListOf<String>()
            while (g.state.phase == NikudPhase.ROUND) {
                val w = target(g)
                order += w.id
                if (g.state.roundIndex == 0) g.onAnswer(g.state.pictures.first { it.id != w.id }.id)
                g.onAnswer(w.id); g.next()
            }
            val back = order.indices.filter { it > 0 && order[it] == order[0] }
            assertTrue("seed $seed: $order", back.size == 1 && (back[0] == 2 || back[0] == 3))
        }
        for (seed in 0 until 60) {
            val g = game(seed, Tally())
            val pos = mutableListOf<Int>()
            while (g.state.phase == NikudPhase.ROUND) { val w = target(g); pos += g.state.pictures.indexOfFirst { it.id == w.id }; g.onAnswer(w.id); g.next() }
            val c = (0 until 3).map { p -> pos.count { it == p } }
            assertTrue("$pos", c.max() - c.min() <= 1)
        }
    }

    @Test
    fun reentryAndAgainCreateFreshSilentReadingSessions() {
        val scheduler = RoundScheduler(random = Random(3))
        val first = ReadSignGame(scheduler, Random(3), Tally().events).also { it.start() }
        val opening = first.state.signText
        first.onAnswer(target(first).id)
        first.next()
        val unfinished = SchedulerMemory.decode(scheduler.checkpoint().encode())
        val tally = Tally()
        val reentered = game(3, tally, unfinished)
        assertEquals(0, reentered.state.roundIndex)
        assertEquals(0, reentered.state.starsEarned)
        assertNotEquals(opening, reentered.state.signText)
        assertEquals(listOf(Nikud.Voice.READ_SIGN), reentered.state.cue.keys)
        while (reentered.state.phase == NikudPhase.ROUND) {
            reentered.onAnswer(target(reentered).id)
            reentered.next()
        }
        val again = game(3, Tally(), SchedulerMemory.decode(tally.memory!!.encode()))
        assertEquals(NikudPhase.ROUND, again.state.phase)
        assertFalse(again.state.sessionComplete)
        assertEquals(0, again.state.roundIndex)
        assertEquals(0, again.state.starsEarned)
        assertEquals(listOf(Nikud.Voice.READ_SIGN), again.state.cue.keys)
    }
}

class MeetMarkCardTest {
    @Test
    fun cardOneOneSoundTwoNamesAfterHearing() {
        val seen = mutableListOf<Int>()
        val c = MeetMarkCard(1, throughAll = true) { seen += it }
        c.start()
        assertEquals(1, c.state.card)
        assertEquals(listOf(Nikud.Voice.TWO_MARKS), c.state.cue.keys)
        assertEquals(2, c.state.faces.size)
        assertFalse(c.state.namesVisible)
        c.onNameTap(0)  // names are not tappable before the sound was heard
        assertEquals(listOf(Nikud.Voice.TWO_MARKS), c.state.cue.keys)
        val before = c.state.cue.token
        c.onFaceTap(1)
        assertEquals(listOf("voice_big_nikud_mem_a"), c.state.cue.keys)
        assertNotEquals(before, c.state.cue.token)
        assertTrue(c.state.namesVisible)
        c.onSpeakerTap()
        assertEquals(listOf("voice_big_nikud_mem_a"), c.state.cue.keys)
        c.onNameTap(0)
        assertEquals(listOf("voice_big_nikud_mark_kamats"), c.state.cue.keys)
        c.onNameTap(1)
        assertEquals(listOf("voice_big_nikud_mark_patah"), c.state.cue.keys)
        c.onDone()
        assertEquals(listOf(1), seen)
        assertEquals(2, c.state.card)
        assertFalse(c.state.heard)
        c.onFaceTap(0)
        assertEquals(listOf("voice_big_nikud_mem_i"), c.state.cue.keys)
        c.onNameTap(0)
        assertEquals(listOf("voice_big_nikud_mark_hiriq"), c.state.cue.keys)
        c.onDone()
        assertTrue(c.state.finished)
        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun cardTwoAloneAndCardOneAloneEndWithoutTheOther() {
        val c2 = MeetMarkCard(2, throughAll = false).also { it.start() }
        assertEquals(2, c2.state.card)
        assertTrue(c2.state.cue.keys.isEmpty())
        c2.onDone()
        assertTrue(c2.state.finished)
        val c1 = MeetMarkCard(1, throughAll = false).also { it.start() }
        c1.onDone()
        assertTrue(c1.state.finished)
    }
}
