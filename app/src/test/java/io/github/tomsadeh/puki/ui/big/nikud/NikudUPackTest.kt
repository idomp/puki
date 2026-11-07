package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.data.models.ContentPace
import io.github.tomsadeh.puki.learning.ItemRecord
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The /u/ pack: shuruk and kubutz, opened after the a/i material by the parent's content pace. */
class NikudUPackTest {
    private fun solid(ids: List<String>, seen: Int = 2, misses: Int = 0) = SchedulerMemory(
        session = 5, records = ids.associateWith { ItemRecord(seen, 4, misses) }
    )

    private val stage1 = SoundShellsGame.STAGE1_ITEMS
    private val ai = SoundShellsGame.AI_ITEMS
    private val aiWordIds = Nikud.aiWords.map { it.id }
    private val aiClearIds = Nikud.aiWords.filter { it.tier == Nikud.Tier.CLEAR }.map { it.id }
    private val shuruk = setOf("sus", "kadur", "tut")
    private val kubutz = setOf("dubi", "buba")

    /** Every a/i item solid and the /u/ syllables of [consonants] answered cleanly. */
    private fun shellsWith(consonants: List<String>) =
        solid(stage1 + ai + consonants.map { SoundShellsGame.stage2Item(it, Nikud.Sound.U) })
    private val everyShell = shellsWith(Nikud.consonants.map { it.id })

    private fun NikudEligibility.join(memory: SchedulerMemory) = joinWords(memory).map { it.id }.toSet()
    private fun NikudEligibility.read(memory: SchedulerMemory) = readWords(memory).map { it.id }.toSet()
    private val uWordIds = shuruk + kubutz
    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    @Test
    fun theThresholdsAreDataOnThePaceAndLieAboveTheOriginalMaterial() {
        fun row(p: ContentPace) = listOf(p.uShellsSolid, p.uJoinSolid, p.uReadSolid, p.kubutzSyllables)
        assertEquals(listOf(20, 16, 13, 4), row(ContentPace.GRADUAL))
        assertEquals(listOf(18, 14, 11, 3), row(ContentPace.FASTER))
        assertEquals(listOf(0, 0, 0, 0), row(ContentPace.ALL_NOW))
        assertEquals(26, ai.size)
        assertEquals(21, Nikud.aiWords.size)
        assertEquals(18, aiClearIds.size)
        for (p in listOf(ContentPace.GRADUAL, ContentPace.FASTER)) {
            // more than the original 16 / 12 / 9 items, at most all of them: the added consonants and words count
            assertTrue(p.uShellsSolid in 17..26); assertTrue(p.uJoinSolid in 13..21); assertTrue(p.uReadSolid in 10..18)
        }
        assertTrue(ContentPace.GRADUAL.uShellsSolid > ContentPace.FASTER.uShellsSolid)
        assertTrue(ContentPace.GRADUAL.kubutzSyllables > ContentPace.FASTER.kubutzSyllables)
    }

    @Test
    fun shellsOpenUOnlyAfterTheAiMaterialAtEachPace() {
        for ((pace, need, seen) in listOf(Triple(ContentPace.GRADUAL, 20, 2), Triple(ContentPace.FASTER, 18, 1))) {
            val e = NikudEligibility(pace = pace)
            assertFalse("$pace one short", e.uSyllables(solid(ai.take(need - 1), seen)))
            assertTrue("$pace", e.uSyllables(solid(ai.take(need), seen)))
            assertFalse("$pace too few sessions", e.uSyllables(solid(ai.take(need), seen - 1)))
            assertFalse("$pace still shaky", e.uSyllables(solid(ai.take(need), seen, misses = 1)))
            assertFalse("$pace exposure alone", e.uSyllables(SchedulerMemory(covered = ai.toSet())))
            // practising /u/ itself does not open /u/
            assertFalse("$pace", e.uSyllables(solid(SoundShellsGame.U_ITEMS + ai.take(need - 1), seen)))
        }
        assertFalse(NikudEligibility().uSyllables(SchedulerMemory()))
        assertTrue(NikudEligibility(pace = ContentPace.ALL_NOW).uSyllables(SchedulerMemory()))
    }

    @Test
    fun joinWordOpensShurukWordsAfterTheAiWordsAndOnlyWhenTheirSoundsWereMet() {
        val gradual = NikudEligibility(everyShell, pace = ContentPace.GRADUAL, cards = setOf(1, 2, 3))
        assertTrue(gradual.join(solid(aiWordIds.take(15))).none { it in uWordIds })
        assertEquals(uWordIds, gradual.join(solid(aiWordIds.take(16))).intersect(uWordIds))
        assertTrue(gradual.join(solid(aiWordIds.take(16), seen = 1)).none { it in uWordIds })
        assertTrue(gradual.join(solid(aiWordIds.take(16), misses = 1)).none { it in uWordIds })
        // the /u/ words alone do not open anything for a child who has not done the a/i words
        assertTrue(gradual.join(solid(uWordIds.toList())).none { it in uWordIds })

        val faster = NikudEligibility(everyShell, pace = ContentPace.FASTER, cards = setOf(1, 2, 3))
        assertTrue(faster.join(solid(aiWordIds.take(13), seen = 1)).none { it in uWordIds })
        assertEquals(uWordIds, faster.join(solid(aiWordIds.take(14), seen = 1)).intersect(uWordIds))

        // a word needs every CV sound it is built of, and its closing letter's family, answered in Sound Shells
        val words = solid(aiWordIds.take(16))
        fun without(vararg items: String) = NikudEligibility(everyShell.copy(records = everyShell.records - items.toSet()),
            pace = ContentPace.GRADUAL, cards = setOf(1, 2, 3)).join(words)
        assertEquals(setOf("sus", "tut", "buba"), without("s2_dalet_u").intersect(uWordIds))
        assertFalse("tav /u/ unmet", "tut" in without("s2_tav_u"))
        assertTrue("another /u/ sound is not needed", "sus" in without("s2_tav_u"))
        assertFalse("closing resh unmet", "kadur" in without("s2_resh_a", "s2_resh_i", "s2_resh_u", "s1_resh"))
    }

    @Test
    fun readSignOpensShurukWordsAfterTheAiWordsAndACleanJoinWordEncounter() {
        val joined = solid(Nikud.words.map { it.id })
        val gradual = NikudEligibility(everyShell, joined, ContentPace.GRADUAL, setOf(1, 2, 3))
        assertTrue(gradual.read(solid(aiClearIds.take(12))).none { it in uWordIds })
        assertEquals(uWordIds, gradual.read(solid(aiClearIds.take(13))).intersect(uWordIds))
        val faster = NikudEligibility(everyShell, joined, ContentPace.FASTER, setOf(1, 2, 3))
        assertTrue(faster.read(solid(aiClearIds.take(10), seen = 1)).none { it in uWordIds })
        assertEquals(uWordIds, faster.read(solid(aiClearIds.take(11), seen = 1)).intersect(uWordIds))
        // a /u/ word is read silently only after it was joined cleanly
        val unjoined = NikudEligibility(everyShell, joined.copy(records = joined.records - "sus"), ContentPace.GRADUAL, setOf(1, 2, 3))
        assertFalse("sus" in unjoined.read(solid(aiClearIds.take(13))))
        assertTrue("tut" in unjoined.read(solid(aiClearIds.take(13))))
        val shaky = NikudEligibility(everyShell, joined.copy(records = joined.records + ("sus" to ItemRecord(2, 4, 1))), ContentPace.GRADUAL, setOf(1, 2, 3))
        assertFalse("sus" in shaky.read(solid(aiClearIds.take(13))))
    }

    @Test
    fun kubutzWaitsForTheCardAndSomeCleanUSyllables() {
        val u = SoundShellsGame.U_ITEMS
        fun open(pace: ContentPace, cards: Set<Int>, memory: SchedulerMemory) = NikudEligibility(memory, pace = pace, cards = cards).kubutz(memory)
        assertFalse("card not seen", open(ContentPace.GRADUAL, setOf(1, 2), solid(u.take(4))))
        assertFalse("three of four", open(ContentPace.GRADUAL, setOf(1, 2, 3), solid(u.take(3))))
        assertTrue(open(ContentPace.GRADUAL, setOf(1, 2, 3), solid(u.take(4))))
        assertTrue("one clean session is enough", open(ContentPace.GRADUAL, setOf(1, 2, 3), solid(u.take(4), seen = 1)))
        assertFalse("shaky ones do not count", open(ContentPace.GRADUAL, setOf(1, 2, 3), solid(u.take(4), misses = 1)))
        assertFalse("asked but never seen", open(ContentPace.GRADUAL, setOf(1, 2, 3), SchedulerMemory(covered = u.toSet())))
        assertFalse(open(ContentPace.FASTER, setOf(1, 2, 3), solid(u.take(2))))
        assertTrue(open(ContentPace.FASTER, setOf(1, 2, 3), solid(u.take(3))))
        assertTrue("at once under ALL_NOW", open(ContentPace.ALL_NOW, emptySet(), SchedulerMemory()))
    }

    @Test
    fun kubutzWordsComeAfterShurukInJoinWordAndReadSign() {
        // exactly three consonants practised in /u/: samekh, dalet, tav
        val three = shellsWith(listOf("samekh", "dalet", "tav"))
        val four = shellsWith(listOf("samekh", "dalet", "tav", "bet"))
        val words = solid(aiWordIds.take(16))
        val cards = setOf(1, 2, 3)
        fun gradual(shells: SchedulerMemory, cards: Set<Int>) = NikudEligibility(shells, solid(Nikud.words.map { it.id }), ContentPace.GRADUAL, cards)

        assertEquals(shuruk, gradual(three, cards).join(words).intersect(uWordIds))
        assertEquals("the card was not seen", shuruk, gradual(four, setOf(1, 2)).join(words).intersect(uWordIds))
        assertEquals(uWordIds, gradual(four, cards).join(words).intersect(uWordIds))
        assertEquals(shuruk, gradual(four, setOf(1, 2)).read(solid(aiClearIds.take(13))).intersect(uWordIds))
        assertEquals(uWordIds, gradual(four, cards).read(solid(aiClearIds.take(13))).intersect(uWordIds))

        // FASTER needs three; דֻּבִּי is ready then, בֻּבָּה still needs its own בֻּ syllable
        val faster = NikudEligibility(three, pace = ContentPace.FASTER, cards = cards)
        val fasterJoin = faster.join(solid(aiWordIds.take(14), seen = 1))
        assertTrue("dubi" in fasterJoin); assertFalse("buba" in fasterJoin)
    }

    @Test
    fun allNowOpensEveryUWordAtOnce() {
        val everything = NikudEligibility(pace = ContentPace.ALL_NOW)
        assertEquals(Nikud.words.map { it.id }.toSet(), everything.join(SchedulerMemory()))
        assertEquals(Nikud.clearWords.map { it.id }.toSet(), everything.read(SchedulerMemory()))
        assertTrue(everything.join(SchedulerMemory()).containsAll(uWordIds))
    }

    @Test
    fun aNewChildStartsExactlyAsBefore() {
        val e = NikudEligibility()
        val nothing = SchedulerMemory()
        assertEquals(Nikud.introductoryConsonants, e.consonants(nothing))
        assertEquals(Nikud.introductoryConsonants, e.firstConsonants())
        assertEquals(Nikud.introductoryWords.map { it.id }.toSet(), e.join(nothing))
        assertEquals(Nikud.introductoryWords.filter { it.tier == Nikud.Tier.CLEAR }.map { it.id }.toSet(), e.read(nothing))
        assertEquals(16, SoundShellsGame.STAGE2_ITEMS.size)
        assertTrue(SoundShellsGame.STAGE2_ITEMS.none { it.endsWith("_u") })
        val shells = SoundShellsGame(RoundScheduler(random = Random(1)), Random(1), Tally().events).also { it.start() }
        assertEquals(1, shells.state.stage); assertEquals(1, shells.state.offerCard)
        assertTrue(shells.state.tiles.all { Nikud.syllable(it.id).sound == Nikud.Sound.A })
    }

    // ---- Sound Shells ----

    private val aiOpen = solid(stage1 + ai.take(20))

    private fun shells(seed: Int, memory: SchedulerMemory, cards: Set<Int>, pace: ContentPace = ContentPace.GRADUAL): SoundShellsGame {
        val r = Random(seed)
        return SoundShellsGame(RoundScheduler(memory, r), r, Tally().events, cards, eligibility = NikudEligibility(memory, pace = pace, cards = cards))
            .also { it.start() }
    }

    private fun target(g: SoundShellsGame): Nikud.Syllable {
        val clip = g.state.cue.keys.first { it in Nikud.SYLLABLE_CLIPS.values }
        return Nikud.syllable(g.state.tiles.first { it.clipKey == clip }.id)
    }

    private fun rounds(g: SoundShellsGame, onRound: (List<Nikud.Syllable>, Nikud.Syllable) -> Unit) {
        while (g.state.phase == NikudPhase.ROUND) {
            val t = target(g)
            onRound(g.state.tiles.map { Nikud.syllable(it.id) }, t)
            g.onAnswer(g.state.tiles.first { it.clipKey == t.clipKey }.id)
            g.next()
        }
    }

    @Test
    fun stageThreeSetsAUSyllableAgainstTheSameConsonantsAAndI() {
        for (seed in 0 until 40) {
            val g = shells(seed, aiOpen, setOf(1, 2, 3))
            assertEquals(3, g.state.stage)
            var u = 0; var other = 0
            rounds(g) { tiles, t ->
                assertEquals("no two boards sound the same", 3, tiles.map { it.soundId }.toSet().size)
                if (t.sound == Nikud.Sound.U) {
                    u++
                    assertEquals("one consonant", 1, tiles.map { it.consonant.id }.toSet().size)
                    assertEquals(setOf(Nikud.Sound.A, Nikud.Sound.I, Nikud.Sound.U), tiles.map { it.sound }.toSet())
                    assertEquals("shuruk comes first", Nikud.Mark.SHURUK, t.mark)
                } else {
                    other++
                    assertTrue("as in stage 2", tiles.none { it.sound == Nikud.Sound.U })
                    assertEquals(2, tiles.count { it.consonant == t.consonant })
                }
            }
            assertEquals(8, u + other)
            assertTrue("at most $other /a/ and /i/ rounds", other <= SoundShellsGame.AI_REVIEW_ROUNDS)
        }
    }

    @Test
    fun kubutzJoinsTheShellsOnlyWhenOpenAndNeverBesideShuruk() {
        val practised = solid(stage1 + ai + SoundShellsGame.U_ITEMS.take(4))
        val seen = mutableSetOf<Nikud.Mark>()
        for (seed in 0 until 60) {
            val g = shells(seed, practised, setOf(1, 2, 3))
            assertEquals(3, g.state.stage)
            rounds(g) { tiles, t ->
                assertTrue("one /u/ spelling", tiles.count { it.sound == Nikud.Sound.U } <= 1)
                if (t.sound == Nikud.Sound.U) seen += t.mark
            }
        }
        assertEquals(setOf(Nikud.Mark.SHURUK, Nikud.Mark.KUBUTZ), seen)
        // the card unseen, or too few clean syllables: shuruk only
        for ((memory, cards) in listOf(practised to setOf(1, 2), solid(stage1 + ai + SoundShellsGame.U_ITEMS.take(3)) to setOf(1, 2, 3))) {
            for (seed in 0 until 30) rounds(shells(seed, memory, cards)) { _, t ->
                if (t.sound == Nikud.Sound.U) assertEquals(Nikud.Mark.SHURUK, t.mark)
            }
        }
    }

    @Test
    fun allNowShowsBothMarksFromTheStartOfStageThree() {
        val advanced = solid(stage1)  // stage 1 is done; the pace needs nothing else
        val marks = mutableSetOf<Nikud.Mark>()
        for (seed in 0 until 40) {
            val g = shells(seed, advanced, emptySet(), ContentPace.ALL_NOW)
            assertEquals(3, g.state.stage)
            rounds(g) { _, t -> if (t.sound == Nikud.Sound.U) marks += t.mark }
        }
        assertEquals(setOf(Nikud.Mark.SHURUK, Nikud.Mark.KUBUTZ), marks)
    }

    @Test
    fun shellsNeverOfferTwoSpellingsOfOneSound() {
        val practised = solid(stage1 + ai + SoundShellsGame.U_ITEMS.take(4))
        val memories = listOf(
            Triple(SchedulerMemory(), emptySet<Int>(), ContentPace.GRADUAL),
            Triple(solid(stage1), setOf(1), ContentPace.GRADUAL),
            Triple(solid(stage1 + ai.take(19)), setOf(1, 2), ContentPace.GRADUAL),
            Triple(aiOpen, setOf(1, 2, 3), ContentPace.GRADUAL),
            Triple(practised, setOf(1, 2, 3), ContentPace.GRADUAL),
            Triple(SchedulerMemory(), emptySet<Int>(), ContentPace.ALL_NOW),
            Triple(solid(stage1), emptySet<Int>(), ContentPace.ALL_NOW)
        )
        for ((memory, cards, pace) in memories) for (seed in 0 until 40) {
            rounds(shells(seed, memory, cards, pace)) { tiles, _ ->
                assertEquals(tiles.map { it.id }, tiles.map { it.id }.distinct())
                assertEquals("three sounds", tiles.size, tiles.map { it.soundId }.toSet().size)
                assertTrue("one spelling of each consonant and sound", tiles.groupBy { it.consonant.id to it.sound }.values.all { it.size == 1 })
                // the /a/ boards of one round share a mark; מַ is never beside מָ, and מוּ never beside מֻ
                assertTrue(tiles.filter { it.sound == Nikud.Sound.A }.map { it.mark }.distinct().size <= 1)
                assertTrue(tiles.count { it.sound == Nikud.Sound.U } <= 1)
            }
        }
    }

    @Test
    fun theUCardIsOfferedWhenUFirstOpens() {
        assertEquals(3, shells(1, aiOpen, setOf(1, 2)).state.offerCard)
        assertNull(shells(1, aiOpen, setOf(1, 2, 3)).state.offerCard)
        // a child who never saw an earlier card meets it first, one card per session
        assertEquals(2, shells(1, aiOpen, setOf(1)).state.offerCard)
        assertEquals(1, shells(1, aiOpen, emptySet()).state.offerCard)
        // the earlier stages keep offering their own card
        assertEquals(2, shells(1, solid(stage1), setOf(1)).state.offerCard)
        assertEquals(1, shells(1, SchedulerMemory(), emptySet()).state.offerCard)
    }

    // ---- Join Word and Read Sign ----

    @Test
    fun aKubutzWordIsJoinedFromItsBoardsWithTheSharedUClip() {
        val words = solid(Nikud.words.map { it.id })
        val eligibility = NikudEligibility(everyShell, words, ContentPace.GRADUAL, setOf(1, 2, 3))
        val seenWords = mutableSetOf<String>()
        for (seed in 0 until 10) {
            val g = JoinWordGame(RoundScheduler(words, Random(seed)), Random(seed), Tally().events, rounds = Nikud.words.size, eligibility = eligibility)
                .also { it.start() }
            while (g.state.phase == JoinPhase.UNITS) {
                val word = Nikud.word(g.state.wordId)
                seenWords += word.id
                val cues = mutableListOf<List<String>>()
                for (i in word.units.indices) { g.onUnitTap(i); cues += g.state.cue.keys }
                when (word.id) {
                    "kadur" -> assertEquals(listOf(listOf("voice_big_nikud_kaf_a"), listOf("voice_big_nikud_dalet_u"),
                        listOf("voice_big_nikud_word_kadur")), cues)
                    "dubi" -> assertEquals(listOf(listOf("voice_big_nikud_dalet_u"), listOf("voice_big_nikud_bet_i", "voice_big_nikud_word_dubi")), cues)
                    "buba" -> assertEquals(listOf("voice_big_nikud_bet_u"), cues.first())
                }
                g.onJoined()
                if (word.id == "dubi") assertTrue(g.state.pictures.none { it.id == "buba" })
                if (word.id == "buba") assertTrue(g.state.pictures.none { it.id == "dubi" })
                if (word.id == "pina") assertTrue(g.state.pictures.none { it.id == "kadur" })
                g.onAnswer(word.id)
                g.next()
            }
        }
        assertEquals(Nikud.words.map { it.id }.toSet(), seenWords)
    }

    @Test
    fun readSignPutsTheShurukWordsInTheSameStartFamilies() {
        val clear = Nikud.clearWords
        for (seed in 0 until 40) {
            for ((id, family) in mapOf("sus" to setOf("sal", "sir", "sira"), "sal" to setOf("sus", "sir", "sira"),
                "sir" to setOf("sus", "sal", "sira"), "sira" to setOf("sus", "sal", "sir"), "kadur" to setOf("kaf"), "kaf" to setOf("kadur"))) {
                val random = Random(seed)
                val layout = PictureChooser(random, RoundScheduler(random = random)).choose(Nikud.word(id), DistractorLevel.SAME_START, pool = clear)
                val ids = layout.options.map { it.id }.toSet()
                assertTrue("$id: $ids", ids.any { it in family })
                if (family.size >= 2) assertTrue("$id: $ids", ids.all { it == id || it in family })
            }
        }
    }

    // ---- Meet the mark ----

    @Test
    fun cardThreeIsCardOnesDoubleBoardForShurukAndKubutz() {
        val seen = mutableListOf<Int>()
        val c = MeetMarkCard(3, throughAll = false, onSeen = { seen += it }).also { it.start() }
        assertEquals(3, c.state.card)
        assertEquals(listOf(Nikud.Voice.TWO_MARKS), c.state.cue.keys)
        assertEquals(listOf("סוּ", "סֻ").map(::nfc), c.state.faces.map { nfc(it.text) })
        assertEquals(listOf("voice_big_nikud_mark_shuruk", "voice_big_nikud_mark_kubutz"), c.state.faces.map { it.nameKey })
        assertFalse(c.state.namesVisible)
        c.onNameTap(0)  // not before the sound was heard
        assertEquals(listOf(Nikud.Voice.TWO_MARKS), c.state.cue.keys)
        c.onFaceTap(1)
        assertEquals(listOf("voice_big_nikud_samekh_u"), c.state.cue.keys)
        assertTrue(c.state.namesVisible)
        c.onSpeakerTap()
        assertEquals(listOf("voice_big_nikud_samekh_u"), c.state.cue.keys)
        c.onNameTap(0)
        assertEquals(listOf("voice_big_nikud_mark_shuruk"), c.state.cue.keys)
        c.onNameTap(1)
        assertEquals(listOf("voice_big_nikud_mark_kubutz"), c.state.cue.keys)
        c.onDone()
        assertTrue(c.state.finished)
        assertEquals(listOf(3), seen)
    }

    @Test
    fun theDoorFlowReachesCardThreeOnlyOnceUIsOpen() {
        fun flow(lastCard: Int): List<Int> {
            val seen = mutableListOf<Int>()
            val c = MeetMarkCard(1, throughAll = true, onSeen = { seen += it }, lastCard = lastCard).also { it.start() }
            while (!c.state.finished) c.onDone()
            return seen
        }
        // the card limit comes from the same eligibility test Sound Shells uses, per pace
        val locked = mapOf(ContentPace.GRADUAL to solid(stage1 + ai.take(19)), ContentPace.FASTER to solid(stage1 + ai.take(17), seen = 1))
        val unlocked = mapOf(ContentPace.GRADUAL to solid(stage1 + ai.take(20)), ContentPace.FASTER to solid(stage1 + ai.take(18), seen = 1))
        for ((pace, memory) in locked) assertEquals("$pace locked", listOf(1, 2), flow(lastMeetCard(memory, pace)))
        for ((pace, memory) in unlocked) assertEquals("$pace unlocked", listOf(1, 2, 3), flow(lastMeetCard(memory, pace)))
        assertEquals(listOf(1, 2), flow(lastMeetCard(SchedulerMemory(), ContentPace.GRADUAL)))
        assertEquals(listOf(1, 2), flow(lastMeetCard(SchedulerMemory(), ContentPace.FASTER)))
        assertEquals(listOf(1, 2, 3), flow(lastMeetCard(SchedulerMemory(), ContentPace.ALL_NOW)))
        assertEquals(MeetMarkCard.CARD_I, lastMeetCard(SchedulerMemory(), ContentPace.GRADUAL))
        assertEquals(MeetMarkCard.CARD_U, lastMeetCard(unlocked.getValue(ContentPace.GRADUAL), ContentPace.GRADUAL))
        val default = MeetMarkCard(1, true).also { it.start() }
        default.onDone(); default.onDone()
        assertTrue(default.state.finished)
        // card 3 says "two marks, one sound" when it follows card 2, and card 2 stays silent
        val c = MeetMarkCard(1, true, lastCard = 3).also { it.start() }
        c.onDone()
        assertTrue(c.state.cue.keys.isEmpty())
        c.onDone()
        assertEquals(3, c.state.card)
        assertEquals(listOf(Nikud.Voice.TWO_MARKS), c.state.cue.keys)
    }

    // ---- Saved progress from before the pack ----

    @Test
    fun savedMemoryFromBeforeTheUPackStillLoadsAndPlays() {
        val shellsText = "7;" + (1..8).joinToString(";") { "s1_${Nikud.introductoryConsonants[it - 1].id},2,6,0" } +
            ";s2_mem_a,2,6,0;s2_mem_i,2,6,1;s2_lamed_a,1,6,0;@covered,s2_mem_a,s2_mem_i;@last,s2_mem_i;@opening,s2_mem_a;@layout,s2_mem_a,1,s2_lamed_a"
        val joinText = "4;dag,2,3,0;pil,2,3,0;gamal,1,3,0;@covered,dag,pil,gamal;@last,gamal;@layout,dag,2,pil,yad"
        val readText = "3;dag,1,2,0;sira,1,2,1;@layout,sira,0,sal,sir"
        val shells = SchedulerMemory.decode(shellsText)
        val join = SchedulerMemory.decode(joinText)
        val read = SchedulerMemory.decode(readText)
        assertEquals(ItemRecord(2, 6, 1), shells.record("s2_mem_i"))
        assertEquals(ItemRecord(1, 3, 0), join.record("gamal"))
        assertEquals(ItemRecord(1, 2, 1), read.record("sira"))
        for (m in listOf(shells, join, read)) assertEquals(m, SchedulerMemory.decode(m.encode()))

        val e = NikudEligibility(shells, join)
        assertFalse(e.uSyllables(shells))
        assertEquals(Nikud.introductoryConsonants, e.consonants(shells))
        assertTrue(e.joinWords(join).none { it.hasU })
        assertTrue(e.readWords(read).none { it.hasU })

        val r = Random(2)
        val g = SoundShellsGame(RoundScheduler(shells, r), r, Tally().events, setOf(1, 2), eligibility = e).also { it.start() }
        assertEquals(2, g.state.stage)
        assertNull(g.state.offerCard)
        rounds(g) { tiles, _ -> assertTrue(tiles.none { it.sound == Nikud.Sound.U }) }

        val j = JoinWordGame(RoundScheduler(join, Random(2)), Random(2), Tally().events, eligibility = e).also { it.start() }
        while (j.state.phase == JoinPhase.UNITS) {
            assertTrue(Nikud.word(j.state.wordId).hasU.not())
            for (i in j.state.units.indices) j.onUnitTap(i)
            j.onJoined()
            j.onAnswer(j.state.wordId)
            j.next()
        }
        assertEquals(JoinPhase.COMPLETE, j.state.phase)

        val s = ReadSignGame(RoundScheduler(read, Random(2)), Random(2), Tally().events, eligibility = e).also { it.start() }
        while (s.state.phase == NikudPhase.ROUND) {
            val word = Nikud.clearWords.first { it.text == s.state.signText }
            assertFalse(word.hasU)
            s.onAnswer(word.id)
            s.next()
        }
        assertEquals(NikudPhase.COMPLETE, s.state.phase)
    }

    @Test
    fun oldCardRecordsKeepTheirMeaning() {
        // "1,2" was the whole stored value before the /u/ card: the production decoder reads it as two cards seen
        val old = DataStoreNikudCardStore.decode("1,2")
        assertEquals(setOf(1, 2), old)
        assertEquals(setOf(1), DataStoreNikudCardStore.decode("1"))
        assertEquals(emptySet<Int>(), DataStoreNikudCardStore.decode(null))
        assertEquals(emptySet<Int>(), DataStoreNikudCardStore.decode(""))
        assertEquals(setOf(1, 2, 3), DataStoreNikudCardStore.decode("1,x,2,,3"))
        assertEquals("1,2,3", DataStoreNikudCardStore.encode(old + 3))
        assertEquals(setOf(1, 2, 3), DataStoreNikudCardStore.decode(DataStoreNikudCardStore.encode(old + 3)))
        val g = shells(1, solid(stage1), old)
        assertNull(g.state.offerCard)
        assertEquals(3, shells(1, aiOpen, old).state.offerCard)
    }

    // ---- ALL_NOW ----

    @Test
    fun allNowRunsAFullSoundShellsSessionWithUFromAnEmptyMemory() {
        for (seed in 0 until 30) {
            val tally = Tally()
            val r = Random(seed)
            val memory = SchedulerMemory()
            val g = SoundShellsGame(RoundScheduler(memory, r), r, tally.events, emptySet(),
                eligibility = NikudEligibility(memory, pace = ContentPace.ALL_NOW)).also { it.start() }
            assertEquals("no introductory stage", 3, g.state.stage)
            assertEquals("the first card is still offered", 1, g.state.offerCard)
            var u = 0
            var count = 0
            rounds(g) { tiles, t ->
                count++
                if (t.sound == Nikud.Sound.U) u++
                assertEquals(3, tiles.map { it.soundId }.toSet().size)
            }
            assertEquals(SoundShellsGame.ROUNDS, count)
            assertTrue("/u/ rounds: $u", u >= SoundShellsGame.ROUNDS - SoundShellsGame.AI_REVIEW_ROUNDS)
            assertEquals(1, tally.finished); assertEquals(SoundShellsGame.ROUNDS, tally.stars); assertEquals(0, tally.slips)
            assertTrue(g.state.sessionComplete)
        }
    }
}
