package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The voice keys the letters games ask for all exist in scripts/voice_packs/letters.py (or are the toddler's voice_letter_* clips). */
class LettersVoicePackTest {
    private fun repoFile(path: String): File =
        listOf("..", ".").map { File(it, path) }.first { it.exists() }

    private val packText = repoFile("scripts/voice_packs/letters.py").readText(Charsets.UTF_8)
    private val generateText = repoFile("scripts/generate_voices.py").readText(Charsets.UTF_8)

    private val lineRe = Regex("""\(\s*"(voice_[a-z0-9_]+)",\s*"[^"]*",\s*(\(\s*"[^"]*",\s*"[^"]*"\s*\)|"[^"]*")\s*\)""")
    private val packKeys = lineRe.findAll(packText).map { it.groupValues[1] }.toList()
    private val toddlerLetterKeys = Regex(""""(voice_letter_[a-z]+)",""").findAll(generateText).map { it.groupValues[1] }.toSet()
    private val known = packKeys.toSet() + toddlerLetterKeys

    private fun kotlinKeysIn(vararg dirs: String): Set<String> =
        dirs.flatMap { dir -> repoFile(dir).walk().filter { it.extension == "kt" }.toList() }
            .flatMap { f -> Regex(""""(voice_[a-z0-9_]+)"""").findAll(f.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toList() }
            .toSet()

    @Test
    fun thePackParsesAndHasNoDuplicates() {
        assertTrue(packKeys.size > 25)
        assertEquals(packKeys.size, packKeys.toSet().size)
        assertTrue(packKeys.all { it.startsWith("voice_big_letters_") || it == "voice_island_letters" })
        assertEquals(19, toddlerLetterKeys.count { k -> BigLetters.base.any { it.voiceKey == k } })
    }

    @Test
    fun everyKeyWrittenInTheLettersCodeExists() {
        val used = kotlinKeysIn(
            "app/src/main/java/io/github/tomsadeh/puki/ui/big/letters",
            "app/src/main/java/io/github/tomsadeh/puki/data/content/big"
        ).filter { !it.endsWith("_") && !it.startsWith("voice_big_math_") && !it.startsWith("voice_big_nikud_") }.toSet()  // (data/content/big also holds the sums and the nikud words)
        assertTrue(used.size > 40)
        assertEquals("missing from the pack", emptySet<String>(), used - known)
    }

    @Test
    fun everyKeyAGameAsksToPlayExists() {
        val asked = mutableSetOf<String>()
        fun collect(s: LetterGameState) { asked += s.cue.keys }
        for (seed in 0 until 40) {
            val memories = listOf(SchedulerMemory(), SchedulerMemory(9, BigLetters.base.associate { it.id to io.github.tomsadeh.puki.learning.ItemRecord(2, 8, 0) }))
            for (memory in memories) {
                val random = Random(seed)
                val games = listOf<Pair<LetterGame, () -> Unit>>(
                    ListenFindGame(RoundScheduler(memory, random), RoundEvents()).let { it to { it.start() } },
                    LetterDetectiveGame(RoundScheduler(memory, random), random, RoundEvents()).let { it to { it.start() } },
                    FinalPartnersGame(RoundScheduler(memory, random), RoundEvents()).let { it to { it.start() } }
                )
                for ((g, start) in games) {
                    start()
                    while (g.state.phase != LetterPhase.COMPLETE) {
                        collect(g.state)
                        if (g.state.phase == LetterPhase.ROUND) {
                            // tap the tiles in order: wrong ones first (feedback and hint lines), then the right one
                            for (id in g.state.tiles.map { it.id }) {
                                g.onAnswer(id)
                                collect(g.state)
                                if (g.state.solved) break
                            }
                            assertTrue(g.state.solved)
                        }
                        g.next()
                    }
                }
            }
        }
        assertTrue(asked.size > 20)
        assertEquals("missing from the pack", emptySet<String>(), asked - known)
    }

    @Test
    fun compareLinesInThePackAreTheOnesTheStateCarries() {
        for (p in BigLetters.pairs) {
            val m = Regex(""""${p.compareKey}",\s*"",\s*"([^"]*)"""").find(packText)
            assertTrue("${p.compareKey} not in the pack", m != null)
            assertEquals(p.compareKey, p.compareLine, m!!.groupValues[1])
        }
    }

    @Test
    fun gendersAreKeptWhereTheChildIsAddressed() {
        val gendered = listOf("listen_intro", "find_it", "hint", "detective_intro", "partner_intro", "partner_ask", "partner_not_friend")
        for (k in gendered) {
            assertTrue("$k should be a (girl, boy) pair",
                Regex(""""voice_big_letters_$k",\s*"",\s*\(\s*"[^"]+",\s*"[^"]+"\s*\)""").containsMatchIn(packText))
        }
    }

    @Test
    fun phonemesBelongToRealLinesAndFollowTheConventions() {
        val stems = Regex(""""(voice_big_letters_[a-z0-9_]+)":\s*"([^"]+)"""").findAll(packText).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(stems.size > 15)
        val lineStems = packKeys.flatMap { listOf(it + "_he", it + "_he_f", it + "_he_m") }.toSet()
        for ((stem, ipa) in stems) {
            assertTrue("$stem has no line", stem in lineStems)
            assertFalse("$stem: use ʁ for ר, χ for ח, ɡ (U+0261) for ג", ipa.any { it in "rRxXg" })
            assertTrue("$stem: a stress mark", "ˈ" in ipa)
            assertFalse("$stem: Hebrew letters in IPA", ipa.any { it in 'א'..'ת' })
        }
        // new letter names and every compare line have exact IPA
        val have = stems.map { it.first }.toSet()
        for (k in listOf("zayin", "tsadi", "resh", "final_kaf", "final_mem", "final_nun", "final_pe", "final_tsadi")) assertTrue(k, "voice_big_letters_${k}_he" in have)
        for (p in BigLetters.pairs) assertTrue(p.id, "${p.compareKey}_he" in have)
    }

    @Test
    fun noPromptLineSaysALetterName() {
        // The prompts that are not the letter clip itself must not give the answer away.
        val names = BigLetters.base.map { it.name.filter { c -> c.code < 0x591 || c.code > 0x5C7 } }.toSet() - setOf("הא")
        val generic = listOf("listen_intro", "find_it", "hint", "detective_intro", "partner_intro", "partner_ask", "partner_not_friend", "looking_for")
        for (k in generic) {
            val m = Regex(""""voice_big_letters_$k",\s*"",\s*(\(\s*"[^"]+",\s*"[^"]+"\s*\)|"[^"]+")""").find(packText)!!
            val words = Regex("""[א-ת]+""").findAll(m.groupValues[1]).map { it.value }.toSet()
            assertTrue("$k names a letter: ${words.intersect(names)}", words.intersect(names).isEmpty())
        }
    }
}
