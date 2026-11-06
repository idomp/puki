package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.ItemRecord
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NikudEligibilityTest {
    private fun solid(ids: List<String>, seen: Int = 2, misses: Int = 0) = SchedulerMemory(
        session = 5, records = ids.associateWith { ItemRecord(seen, 4, misses) }
    )

    private val initialWords = Nikud.introductoryWords.map { it.id }.toSet()
    private val initialClear = Nikud.introductoryWords.filter { it.tier == Nikud.Tier.CLEAR }.map { it.id }.toSet()
    private val allWords = solid(Nikud.words.map { it.id })
    private val allShells = solid(SoundShellsGame.STAGE1_ITEMS + Nikud.consonants.flatMap { c ->
        Nikud.Sound.values().map { SoundShellsGame.stage2Item(c.id, it) }
    })
    private val ready = NikudEligibility(allShells, allWords)

    @Test
    fun newChildGetsExactlyTheOriginalPoolsEvenWithOtherGamesMastered() {
        val empty = SchedulerMemory()
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(empty))
        assertEquals(initialWords, NikudEligibility().joinWords(empty).map { it.id }.toSet())
        assertEquals(initialClear, NikudEligibility().readWords(empty).map { it.id }.toSet())
        assertEquals(initialWords, ready.joinWords(empty).map { it.id }.toSet())
        assertEquals(initialClear, ready.readWords(empty).map { it.id }.toSet())
        // Progress in the word games cannot substitute for meeting sounds in shells.
        assertEquals(initialWords, NikudEligibility().joinWords(allWords).map { it.id }.toSet())
        assertEquals(initialClear, NikudEligibility().readWords(allWords).map { it.id }.toSet())
    }

    @Test
    fun shellsExpandOnlyAfterTwelveOriginalSoundItemsAreSolid() {
        val ids = SoundShellsGame.STAGE2_ITEMS
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(solid(ids.take(11))))
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(solid(ids.take(12), seen = 1)))
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(solid(ids.take(12), misses = 1)))
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(solid(SoundShellsGame.STAGE1_ITEMS)))
        assertEquals(Nikud.consonants, NikudEligibility.consonants(solid(ids.take(12))))
        val exposure = SchedulerMemory(covered = ids.toSet())
        assertEquals(Nikud.introductoryConsonants, NikudEligibility.consonants(exposure))
    }

    @Test
    fun wordReadinessCountsOnlySolidOriginalItems() {
        val joinIds = initialWords.toList()
        val readIds = initialClear.toList()
        assertEquals(initialWords, ready.joinWords(solid(joinIds.take(7))).map { it.id }.toSet())
        assertEquals(initialWords, ready.joinWords(solid(joinIds.take(8), seen = 1)).map { it.id }.toSet())
        assertEquals(initialWords, ready.joinWords(solid(joinIds.take(8), misses = 1)).map { it.id }.toSet())
        assertTrue(ready.joinWords(solid(joinIds.take(8))).any { it.id == "gamal" })
        assertEquals(initialClear, ready.readWords(solid(readIds.take(5))).map { it.id }.toSet())
        assertEquals(initialClear, ready.readWords(solid(readIds.take(6), seen = 1)).map { it.id }.toSet())
        assertEquals(initialClear, ready.readWords(solid(readIds.take(6), misses = 1)).map { it.id }.toSet())
        assertTrue(ready.readWords(solid(readIds.take(6))).any { it.id == "gamal" })
    }

    @Test
    fun wordsNeedTheirCvSoundsAndClosingLettersMetInShells() {
        fun without(consonant: String, sound: Nikud.Sound? = null): Set<String> {
            val memory = allShells.copy(records = allShells.records.filterKeys { id ->
                if (sound == null) id != SoundShellsGame.stage1Item(consonant) &&
                    Nikud.Sound.values().none { id == SoundShellsGame.stage2Item(consonant, it) }
                else id != SoundShellsGame.stage2Item(consonant, sound)
            })
            return NikudEligibility(memory, allWords).joinWords(allWords).map { it.id }.toSet()
        }
        assertFalse("closing resh must be met", "sir" in without("resh"))
        assertFalse("CV resh must be met", "sira" in without("resh"))
        assertFalse("closing samekh must be met", "panas" in without("samekh"))
        assertFalse("hiriq cannot be inferred from /a/", "sir" in without("samekh", Nikud.Sound.I))
        assertTrue("a different required sound remains available", "sal" in without("samekh", Nikud.Sound.I))
        val stage1 = solid(SoundShellsGame.STAGE1_ITEMS)
        assertEquals(initialWords + "gamal", NikudEligibility(stage1).joinWords(allWords).map { it.id }.toSet())
    }

    @Test
    fun silentReadingFollowsACleanJoinWordEncounter() {
        val joined = allWords.copy(records = allWords.records - "sira")
        assertFalse(NikudEligibility(allShells, joined).readWords(allWords).any { it.id == "sira" })
        val shaky = joined.copy(records = joined.records + ("sira" to ItemRecord(2, 4, 1)))
        assertFalse(NikudEligibility(allShells, shaky).readWords(allWords).any { it.id == "sira" })
        val clean = joined.copy(records = joined.records + ("sira" to ItemRecord(1, 4, 0)))
        assertTrue(NikudEligibility(allShells, clean).readWords(allWords).any { it.id == "sira" })
    }

    @Test
    fun masteredGamesIncludeEveryApprovedAdditionAndNeverPendingAudio() {
        val active = Nikud.words.filter { it.id !in Nikud.pendingAudio }.map { it.id }.toSet()
        assertEquals(Nikud.words.size - Nikud.pendingAudio.size, active.size)
        assertEquals(active, ready.joinWords(allWords).map { it.id }.toSet())
        assertEquals(Nikud.clearWords.map { it.id }.toSet(), ready.readWords(allWords).map { it.id }.toSet())
        val pendingKeys = Nikud.pendingAudio.map { Nikud.word(it).wordKey }.toSet()
        for (seed in 0 until 20) {
            val join = JoinWordGame(RoundScheduler(allWords, Random(seed)), Random(seed), Tally().events,
                rounds = active.size, eligibility = ready).also { it.start() }
            val joined = mutableSetOf<String>()
            while (join.state.phase == JoinPhase.UNITS) {
                joined += join.state.wordId
                assertTrue(join.state.wordId !in Nikud.pendingAudio)
                for (i in join.state.units.indices) join.onUnitTap(i)
                assertTrue(join.state.cue.keys.none { it in pendingKeys })
                join.onJoined()
                assertTrue(join.state.pictures.none { it.id in Nikud.pendingAudio })
                join.onAnswer(join.state.wordId)
                join.next()
            }
            assertEquals(active, joined)
            val read = ReadSignGame(RoundScheduler(allWords, Random(seed)), Random(seed), Tally().events,
                rounds = Nikud.clearWords.size, eligibility = ready).also { it.start() }
            val readIds = mutableSetOf<String>()
            while (read.state.phase == NikudPhase.ROUND) {
                val word = Nikud.words.first { it.text == read.state.signText }
                readIds += word.id
                assertTrue(read.state.pictures.none { it.id in Nikud.pendingAudio })
                read.onAnswer(word.id)
                assertTrue(read.state.cue.keys.none { it in pendingKeys })
                read.next()
            }
            assertEquals(Nikud.clearWords.map { it.id }.toSet(), readIds)
        }
    }

    @Test
    fun unmasteredGamesKeepNewWordsOutOfPicturesToo() {
        for (seed in 0 until 30) {
            val join = JoinWordGame(RoundScheduler(random = Random(seed)), Random(seed), Tally().events,
                rounds = 12).also { it.start() }
            val joined = mutableSetOf<String>()
            while (join.state.phase == JoinPhase.UNITS) {
                joined += join.state.wordId
                for (i in join.state.units.indices) join.onUnitTap(i)
                join.onJoined()
                assertTrue(join.state.pictures.all { it.id in initialWords })
                join.onAnswer(join.state.wordId)
                join.next()
            }
            assertEquals(initialWords, joined)
            val read = ReadSignGame(RoundScheduler(random = Random(seed)), Random(seed), Tally().events,
                rounds = 9).also { it.start() }
            val readIds = mutableSetOf<String>()
            while (read.state.phase == NikudPhase.ROUND) {
                val word = Nikud.words.first { it.text == read.state.signText }
                readIds += word.id
                assertTrue(read.state.pictures.all { it.id in initialClear })
                read.onAnswer(word.id)
                read.next()
            }
            assertEquals(initialClear, readIds)
        }
    }

    @Test
    fun expandedShellsCoverBothSoundsWithoutAudibleDuplicates() {
        for (seed in 0 until 30) {
            val game = SoundShellsGame(RoundScheduler(allShells, Random(seed)), Random(seed), Tally().events,
                rounds = 26).also { it.start() }
            val asked = mutableSetOf<String>()
            while (game.state.phase == NikudPhase.ROUND) {
                assertEquals(2, game.state.stage)
                val tiles = game.state.tiles
                assertEquals(3, tiles.map { Nikud.syllable(it.id).soundId }.toSet().size)
                val clip = game.state.cue.keys.first { it in Nikud.SYLLABLE_CLIPS.values }
                val target = tiles.first { it.clipKey == clip }
                val syllable = Nikud.syllable(target.id)
                asked += SoundShellsGame.stage2Item(syllable.consonant.id, syllable.sound)
                game.onAnswer(target.id)
                game.next()
            }
            assertEquals(allShells.records.keys.filter { it.startsWith("s2_") }.toSet(), asked)
        }
    }

    @Test
    fun equivalentConsonantsCannotCompeteEvenWithDifferentClipKeys() {
        val samekh = Nikud.syllable("samekh", Nikud.Mark.PATAH)
        val sin = Nikud.Syllable(Nikud.Consonant("sin", "שׂ", "s"), Nikud.Mark.PATAH)
        val kaf = Nikud.syllable("kaf", Nikud.Mark.PATAH)
        val qof = Nikud.Syllable(Nikud.Consonant("qof", "ק", "k"), Nikud.Mark.PATAH)
        assertEquals(emptyList<Nikud.Syllable>(), SoundShellsGame.distinctSounds(samekh, listOf(sin)))
        assertEquals(emptyList<Nikud.Syllable>(), SoundShellsGame.distinctSounds(kaf, listOf(qof)))
        assertEquals(1, SoundShellsGame.distinctSounds(samekh, listOf(kaf, qof)).size)
        assertEquals(emptyList<Nikud.Syllable>(), SoundShellsGame.distinctSounds(samekh,
            listOf(Nikud.syllable("samekh", Nikud.Mark.KAMATS))))
    }
}
