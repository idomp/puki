package io.github.tomsadeh.puki.data.content.big

import io.github.tomsadeh.puki.data.content.hebrewLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BigLetterContentTest {
    private val hebrewBlock = 'א'..'ת'

    @Test
    fun allTwentyTwoLettersAndFiveFinalsAreThere() {
        assertEquals(22, BigLetters.base.size)
        assertEquals(5, BigLetters.finals.size)
        assertEquals(27, BigLetters.all.size)
        assertEquals("אבגדהוזחטיכלמנסעפצקרשת", BigLetters.base.joinToString("") { it.glyph })
        assertEquals("ךםןףץ", BigLetters.finals.joinToString("") { it.glyph })
    }

    @Test
    fun noDuplicateIdsGlyphsOrVoiceKeys() {
        val all = BigLetters.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.glyph }.toSet().size)
        assertEquals(all.size, all.map { it.voiceKey }.toSet().size)
        assertTrue(all.all { it.glyph.length == 1 && it.glyph[0] in hebrewBlock })
        assertTrue(all.all { it.id.matches(Regex("[a-z0-9_]+")) })  // the scheduler's memory format relies on it
        assertTrue(all.all { it.name.isNotBlank() })
    }

    @Test
    fun theNineteenToddlerLettersKeepTheirIdsAndRecordings() {
        assertEquals(19, hebrewLetters.size)
        for (old in hebrewLetters) {
            val letter = BigLetters.find(old.id)
            assertNotNull("${old.id} is missing", letter)
            assertEquals(old.hebrewLetter, letter!!.glyph)
            assertEquals("voice_letter_${old.id}", letter.voiceKey)
        }
        val added = BigLetters.base.map { it.id } - hebrewLetters.map { it.id }.toSet()
        assertEquals(setOf("zayin", "tsadi", "resh"), added.toSet())
        assertTrue(added.all { BigLetters[it].voiceKey.startsWith("voice_big_letters_") })
    }

    @Test
    fun finalFormsPointAtTheirOrdinaryLetter() {
        val expected = mapOf("kaf_final" to "kaf", "mem_final" to "mem", "nun_final" to "nun", "pe_final" to "pe", "tsadi_final" to "tsadi")
        for ((final, base) in expected) {
            assertEquals(base, BigLetters[final].baseId)
            assertTrue(BigLetters[final].isFinal)
            assertFalse(BigLetters[base].isFinal)
            assertEquals(final, BigLetters.partnerOf(base))
            assertEquals(base, BigLetters.partnerOf(final))
            assertEquals(base, BigLetters.baseOf(final))
        }
        assertNull(BigLetters.partnerOf("alef"))
    }

    @Test
    fun lookalikePairsUseRealIdsAndAreNotRepeated() {
        val required = listOf("dalet" to "resh", "he" to "het", "bet" to "kaf", "vav" to "yod", "gimel" to "nun",
            "tet" to "samekh", "ayin" to "shin", "vav" to "zayin", "nun_final" to "vav", "mem_final" to "samekh",
            "kaf" to "pe", "het" to "tav")
        for ((a, b) in required) assertNotNull("$a/$b", BigLetters.pairOf(a, b))
        assertEquals(required.size, BigLetters.pairs.size)
        assertEquals(BigLetters.pairs.size, BigLetters.pairs.map { it.id }.toSet().size)
        assertEquals(BigLetters.pairs.size, BigLetters.pairs.map { setOf(it.a, it.b) }.toSet().size)
        for (p in BigLetters.pairs) {
            assertNotNull(BigLetters.find(p.a)); assertNotNull(BigLetters.find(p.b))
            assertTrue(p.a != p.b)
            assertTrue(p.compareKey.startsWith("voice_big_letters_cmp_"))
            assertTrue(p.compareLine.length in 10..80)
            assertFalse("a pair of a letter with its own final form is not a detective pair", BigLetters.baseOf(p.a) == BigLetters.baseOf(p.b))
        }
        assertEquals(BigLetters.pairs.size, BigLetters.pairs.map { it.compareKey }.toSet().size)
    }

    @Test
    fun visualDistanceIsSymmetricAndFlagsLookalikesAndFinalForms() {
        for (a in BigLetters.all) for (b in BigLetters.all) {
            assertEquals("${a.id}/${b.id}", BigLetters.visualDistance(a.id, b.id), BigLetters.visualDistance(b.id, a.id))
        }
        for (p in BigLetters.pairs) assertEquals(0, BigLetters.visualDistance(p.a, p.b))
        for (f in BigLetters.finals) assertEquals("${f.id} sounds like its letter", 0, BigLetters.visualDistance(f.id, f.baseId!!))
        assertEquals(2, BigLetters.visualDistance("alef", "lamed"))
        assertTrue(BigLetters.base.all { l -> BigLetters.all.count { BigLetters.visualDistance(l.id, it.id) == 2 } >= 15 })
    }
}
