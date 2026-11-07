package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.data.models.AppSettings
import io.github.tomsadeh.puki.data.models.ContentPace
import io.github.tomsadeh.puki.learning.ItemRecord
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ContentPaceTest {
    private fun solid(ids: List<String>, seen: Int = 2, misses: Int = 0) = SchedulerMemory(
        session = 5, records = ids.associateWith { ItemRecord(seen, 4, misses) }
    )

    private val initialWords = Nikud.introductoryWords.map { it.id }
    private val initialClear = Nikud.introductoryWords.filter { it.tier == Nikud.Tier.CLEAR }.map { it.id }
    private val allWords = solid(Nikud.words.map { it.id })
    private val allShells = solid(SoundShellsGame.STAGE1_ITEMS + Nikud.consonants.flatMap { c ->
        Nikud.Sound.values().map { SoundShellsGame.stage2Item(c.id, it) }
    })
    private val active = Nikud.words.filter { it.id !in Nikud.pendingAudio }.map { it.id }.toSet()

    private fun NikudEligibility.join(memory: SchedulerMemory) = joinWords(memory).map { it.id }.toSet()
    private fun NikudEligibility.read(memory: SchedulerMemory) = readWords(memory).map { it.id }.toSet()

    private fun numbers(pace: ContentPace) =
        listOf(pace.shellsSolid, pace.joinSolid, pace.readSolid, pace.sessions, if (pace.prerequisites) 1 else 0)

    @Test
    fun thresholdsAreDataOnThePace() {
        assertEquals(listOf(12, 8, 6, 2, 1), numbers(ContentPace.GRADUAL))
        assertEquals(listOf(8, 6, 4, 1, 1), numbers(ContentPace.FASTER))
        assertEquals(listOf(0, 0, 0, 1, 0), numbers(ContentPace.ALL_NOW))
    }

    @Test
    fun gradualIsTheOriginalBehaviour() {
        val gradual = NikudEligibility(allShells, allWords, ContentPace.GRADUAL)
        val default = NikudEligibility(allShells, allWords)
        val ids = SoundShellsGame.STAGE2_ITEMS
        for (memory in listOf(SchedulerMemory(), solid(ids.take(11)), solid(ids.take(12)), solid(ids.take(12), seen = 1),
            solid(ids.take(12), misses = 1), solid(initialWords.take(7)), solid(initialWords.take(8)),
            solid(initialClear.take(5)), solid(initialClear.take(6)), solid(initialClear.take(6), seen = 1), allWords)) {
            assertEquals(default.consonants(memory), gradual.consonants(memory))
            assertEquals(default.join(memory), gradual.join(memory))
            assertEquals(default.read(memory), gradual.read(memory))
        }
        assertEquals(Nikud.introductoryConsonants, gradual.consonants(solid(ids.take(11))))
        assertEquals(Nikud.consonants, gradual.consonants(solid(ids.take(12))))
        assertEquals(Nikud.introductoryConsonants, gradual.consonants(solid(ids.take(12), seen = 1)))
        assertEquals(initialWords.toSet(), gradual.join(solid(initialWords.take(7))))
        assertTrue("gamal" in gradual.join(solid(initialWords.take(8))))
        assertEquals(initialWords.toSet(), gradual.join(solid(initialWords.take(8), seen = 1)))
        assertEquals(Nikud.introductoryConsonants, gradual.firstConsonants())
    }

    @Test
    fun fasterOpensWithLowerThresholdsAndOneCleanSession() {
        val faster = NikudEligibility(allShells, allWords, ContentPace.FASTER)
        val ids = SoundShellsGame.STAGE2_ITEMS
        assertEquals(Nikud.introductoryConsonants, faster.consonants(solid(ids.take(7), seen = 1)))
        assertEquals(Nikud.consonants, faster.consonants(solid(ids.take(8), seen = 1)))
        assertEquals(Nikud.introductoryConsonants, faster.consonants(solid(ids.take(8), seen = 1, misses = 1)))
        assertEquals(Nikud.introductoryConsonants, faster.consonants(SchedulerMemory(covered = ids.toSet())))

        assertEquals(initialWords.toSet(), faster.join(solid(initialWords.take(5), seen = 1)))
        assertEquals(initialWords.toSet(), faster.join(solid(initialWords.take(6), seen = 1, misses = 1)))
        assertTrue("gamal" in faster.join(solid(initialWords.take(6), seen = 1)))

        assertEquals(initialClear.toSet(), faster.read(solid(initialClear.take(3), seen = 1)))
        assertEquals(initialClear.toSet(), faster.read(solid(initialClear.take(4), seen = 1, misses = 1)))
        assertTrue("gamal" in faster.read(solid(initialClear.take(4), seen = 1)))
    }

    @Test
    fun fasterKeepsThePrerequisites() {
        val ready = solid(initialWords.take(6), seen = 1)
        assertEquals(initialWords.toSet(), NikudEligibility(SchedulerMemory(), allWords, ContentPace.FASTER).join(ready))
        val withoutResh = allShells.copy(records = allShells.records.filterKeys { "resh" !in it })
        assertFalse("sir" in NikudEligibility(withoutResh, allWords, ContentPace.FASTER).join(ready))
        val joined = allWords.copy(records = allWords.records - "sira")
        val readReady = solid(initialClear.take(4), seen = 1)
        assertFalse("sira" in NikudEligibility(allShells, joined, ContentPace.FASTER).read(readReady))
        val clean = joined.copy(records = joined.records + ("sira" to ItemRecord(1, 4, 0)))
        assertTrue("sira" in NikudEligibility(allShells, clean, ContentPace.FASTER).read(readReady))
        val shaky = joined.copy(records = joined.records + ("sira" to ItemRecord(1, 4, 1)))
        assertFalse("sira" in NikudEligibility(allShells, shaky, ContentPace.FASTER).read(readReady))
    }

    @Test
    fun allNowOpensEverythingForAnEmptyMemory() {
        val everything = NikudEligibility(pace = ContentPace.ALL_NOW)
        val empty = SchedulerMemory()
        assertEquals(Nikud.consonants, everything.consonants(empty))
        assertEquals(Nikud.consonants, everything.firstConsonants())
        assertEquals(active, everything.join(empty))
        assertEquals(Nikud.clearWords.map { it.id }.toSet(), everything.read(empty))
        assertTrue(everything.join(empty).none { it in Nikud.pendingAudio })
        assertTrue(everything.read(empty).none { it in Nikud.pendingAudio })
    }

    @Test
    fun allNowMakesEveryConsonantEligibleInAnExtendedFirstVisit() {
        val everything = NikudEligibility(pace = ContentPace.ALL_NOW)
        for (seed in 0 until 20) {
            val game = SoundShellsGame(RoundScheduler(random = Random(seed)), Random(seed), Tally().events,
                rounds = Nikud.consonants.size * 3, eligibility = everything).also { it.start() }
            assertEquals("ALL_NOW has no introductory stage", 3, game.state.stage)
            val asked = mutableSetOf<String>()
            while (game.state.phase == NikudPhase.ROUND) {
                val clip = game.state.cue.keys.first { it in Nikud.SYLLABLE_CLIPS.values }
                val target = game.state.tiles.first { it.clipKey == clip }
                asked += Nikud.syllable(target.id).consonant.id
                game.onAnswer(target.id)
                game.next()
            }
            assertEquals(Nikud.consonants.map { it.id }.toSet(), asked)
        }
    }

    @Test
    fun theSettingRoundTripsThroughItsStoredName() {
        for (pace in ContentPace.values()) assertEquals(pace, ContentPace.fromStored(pace.name))
        assertEquals(ContentPace.GRADUAL, ContentPace.fromStored(null))
        assertEquals(ContentPace.GRADUAL, ContentPace.fromStored(""))
        assertEquals(ContentPace.GRADUAL, ContentPace.fromStored("SPRINT"))
        assertEquals(ContentPace.GRADUAL, ContentPace.fromStored("faster"))
        assertEquals(ContentPace.GRADUAL, AppSettings().contentPace)
    }
}
