package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

class NikudContentTest {
    private fun repoFile(path: String): File = listOf("..", ".").map { File(it, path) }.first { it.exists() }
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private val packText = repoFile("scripts/voice_packs/nikud.py").readText(Charsets.UTF_8)
    private val packLines = Regex("""\("(voice_[a-z0-9_]+)",\s*"",\s*(?:"([^"]*)"|\("([^"]*)",\s*"([^"]*)"\))\)""")
        .findAll(packText).associate { it.groupValues[1] to it.groupValues.drop(2).filter { t -> t.isNotEmpty() } }
    private val rawKeys = repoFile("app/src/main/res/raw").listFiles()!!.map { it.name.substringBefore("_he").substringBefore("_en") }.toSet()

    @Test
    fun unitsRebuildEachWordExactlyAsTheVoicePackWritesIt() {
        assertEquals(21, Nikud.words.size)
        for (w in Nikud.words) {
            assertEquals(w.id, w.units.joinToString("") { it.text }, w.text)
            if (w.id in Nikud.pendingAudio) continue
            val packed = packLines[w.wordKey]
            assertNotNull("${w.wordKey} in the pack", packed)
            assertEquals(w.id, nfc(packed!!.first()), nfc(w.text))
        }
    }

    @Test
    fun newWordsKeepTheirApprovedPointingAndReadingUnits() {
        val expected = mapOf(
            "gamal" to listOf("גָּ", "מָ", "ל"), "kaf" to listOf("כַּ", "ף"),
            "sal" to listOf("סַ", "ל"), "sir" to listOf("סִי", "ר"), "sira" to listOf("סִי", "רָה"),
            "panas" to listOf("פָּ", "נָ", "ס"), "banana" to listOf("בָּ", "נָ", "נָה"),
            "para" to listOf("פָּ", "רָה"), "tiras" to listOf("תִּי", "רָ", "ס")
        )
        for ((id, units) in expected) {
            val word = Nikud.word(id)
            assertEquals(id, units.map(::nfc), word.units.map { nfc(it.text) })
            assertEquals(id, nfc(units.joinToString("")), nfc(word.text))
            assertEquals(Nikud.Tier.CLEAR, word.tier)
            assertEquals("voice_big_nikud_word_$id", word.wordKey)
        }
        assertTrue(Nikud.pendingAudio.all { it in setOf("banana", "para", "tiras") })
    }

    @Test
    fun unitsAreCvOrASilentClosingLetter() {
        for (w in Nikud.words) {
            assertEquals(Nikud.UnitKind.CV, w.units.first().kind)
            for (u in w.units) {
                if (u.kind == Nikud.UnitKind.CV) {
                    assertTrue("${w.id}: ${u.clipKey}", u.clipKey in Nikud.SYLLABLE_CLIPS.values)
                    assertNotNull(u.consonant); assertNotNull(u.sound)
                } else {
                    assertNull(u.clipKey)
                    assertEquals(1, u.text.length)
                }
            }
            // a closing consonant is only ever the last board
            assertTrue(w.units.dropLast(1).none { it.kind == Nikud.UnitKind.CLOSING })
        }
        // the examples of the design
        assertEquals(listOf("voice_big_nikud_dalet_a", null), Nikud.word("dag").units.map { it.clipKey })
        assertEquals(listOf("voice_big_nikud_mem_a", "voice_big_nikud_pe_a"), Nikud.word("mapa").units.map { it.clipKey })
        assertEquals(listOf("voice_big_nikud_pe_i", null), Nikud.word("pil").units.map { it.clipKey })
        assertEquals(listOf("voice_big_nikud_gimel_i", "voice_big_nikud_nun_a"), Nikud.word("gina").units.map { it.clipKey })
    }

    @Test
    fun tiersAndPictures() {
        assertEquals(setOf("dag", "pil", "yad", "mapa", "gina", "yam", "gal", "gan", "gag",
            "gamal", "kaf", "sal", "sir", "sira", "panas", "banana", "para", "tiras") - Nikud.pendingAudio,
            Nikud.clearWords.map { it.id }.toSet())
        assertEquals(setOf("mana", "pina"), Nikud.words.filter { it.tier == Nikud.Tier.SCENE }.map { it.id }.toSet())
        assertEquals(listOf("mila"), Nikud.words.filter { it.tier == Nikud.Tier.NONE }.map { it.id })
        assertNull(Nikud.word("mila").pictureId)
        val pictures = Nikud.words.mapNotNull { it.pictureId }
        assertEquals(pictures.size, pictures.toSet().size)
        assertTrue(Nikud.areTwins(Nikud.word("gal"), Nikud.word("yam")))
        assertTrue(Nikud.areTwins(Nikud.word("gan"), Nikud.word("gina")))
        assertFalse(Nikud.areTwins(Nikud.word("gal"), Nikud.word("gan")))
        assertTrue(Nikud.areTwins(Nikud.word("sal"), Nikud.word("sal").copy(id = "another_basket")))
    }

    @Test
    fun everyPictureMapsToAnExistingDrawable() {
        val mapping = repoFile("app/src/main/java/io/github/tomsadeh/puki/ui/big/nikud/NikudPictures.kt").readText()
        val resources = Regex(""""([a-z]+)" -> R.drawable.([a-z_]+)""").findAll(mapping)
            .associate { it.groupValues[1] to it.groupValues[2] }
        for (word in Nikud.words.filter { it.pictureId != null }) {
            val drawable = resources[word.pictureId]
            assertNotNull(word.id, drawable)
            assertTrue(word.id, repoFile("app/src/main/res/drawable/$drawable.xml").isFile)
        }
    }

    @Test
    fun syllablesAreTwentySixClipsAndKamatzSharesPatah() {
        assertEquals(26, Nikud.SYLLABLE_CLIPS.size)
        for (c in Nikud.consonants) for (m in Nikud.Mark.values()) {
            val s = Nikud.syllable(c.id, m)
            assertEquals(Nikud.syllableClip(c.id, m.sound), s.clipKey)
            assertEquals(s, Nikud.syllable(s.id))
        }
        assertEquals(Nikud.syllable("mem", Nikud.Mark.PATAH).clipKey, Nikud.syllable("mem", Nikud.Mark.KAMATS).clipKey)
        assertTrue(Nikud.syllable("mem", Nikud.Mark.PATAH).clipKey != Nikud.syllable("mem", Nikud.Mark.HIRIQ).clipKey)
        assertEquals("מַ", nfc(Nikud.syllable("mem", Nikud.Mark.PATAH).text))
        assertEquals("גַּ", nfc(packLines["voice_big_nikud_gimel_a"]!!.first()).let { nfc(Nikud.syllable("gimel", Nikud.Mark.PATAH).text) }.let { nfc(it) })
    }

    @Test
    fun everyActiveVoiceKeyHasAPackLineAndAudio() {
        val dirs = listOf("app/src/main/java/io/github/tomsadeh/puki/ui/big/nikud", "app/src/main/java/io/github/tomsadeh/puki/data/content/big/NikudContent.kt")
        val used = dirs.map { repoFile(it) }.flatMap { f -> if (f.isDirectory) f.walk().filter { it.extension == "kt" }.toList() else listOf(f) }
            .flatMap { f -> Regex(""""(voice_big_nikud_[a-z0-9_]+)"""").findAll(f.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toList() }.toSet()
        assertTrue(used.size > 30)
        val playable = used + Nikud.SYLLABLE_CLIPS.values + Nikud.words.filter { it.id !in Nikud.pendingAudio }.map { it.wordKey }
        assertEquals("missing pack lines", emptySet<String>(), playable - packLines.keys)
        assertEquals("missing audio", emptySet<String>(), playable - rawKeys)
    }
}
