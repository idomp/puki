package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Validate authored pack content, not generated audio (audio generation is deliberately offline). */
class MathVoiceContentTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "scripts/voice_packs/math.py").isFile }
    private val pack = File(root, "scripts/voice_packs/math.py").readText()

    @Test fun everyLiteralMathVoiceKeyExistsInThePackAndIsUnique() {
        val keyPattern = Regex("\"(voice_[a-z0-9_]+)\"")
        val sourceFiles = File(root, "app/src/main/java/io/github/tomsadeh/puki/ui/big/math")
            .walkTopDown().filter { it.extension == "kt" && it.name != "BigMathScreen.kt" }.toList() +
            File(root, "app/src/main/java/io/github/tomsadeh/puki/data/content/big/MathContent.kt")
        val keys = sourceFiles.flatMap { file -> keyPattern.findAll(file.readText()).map { it.groupValues[1] }.toList() }.toSet()
        val entries = pack.lineSequence().filter { it.trimStart().startsWith("(\"voice_") }.toList()
        val packKeys = entries.map { keyPattern.find(it)!!.groupValues[1] }
        assertEquals(packKeys.size, packKeys.distinct().size)
        val existing = (1..10).map { "voice_number_$it" }.toSet()
        assertTrue("Keys absent from pack: ${keys - packKeys.toSet() - existing}", (keys - packKeys.toSet() - existing).isEmpty())
        assertEquals(MathContent.allLines.map { it.key }.toSet(), keys)
        assertEquals(114, keys.size)
    }

    @Test fun completeSentencesAndGenderedVariantsMatchBilingualContentAndEnglishIsAvailable() {
        for (line in MathContent.allLines) {
            val entry = pack.lineSequence().single { it.trimStart().startsWith("(\"${line.key}\",") }
            assertTrue(line.key, entry.contains("\"${line.text.he}\""))
            assertTrue(line.key, line.text.en.isNotBlank())
            assertTrue(line.key, entry.contains("\"${line.text.en}\""))
            line.text.heGirl?.let { girl ->
                assertTrue(line.key, entry.contains("(\"$girl\", \"${line.text.he}\")"))
            }
        }
    }

    @Test fun partTotalsUseNewWholeSentenceKeysAndReuseTheExistingZero() {
        assertEquals("voice_big_math_picnic_already", MathContent.picnicAlready.key)
        assertEquals(11, MathContent.picnicTotals.size)
        assertEquals(MathContent.picnicTargets[0], MathContent.picnicTotals[0])
        for (target in 1..10) assertEquals("voice_big_math_picnic_target_$target", MathContent.picnicTotals[target].key)
        assertEquals(MathContent.allLines.size, MathContent.allLines.map { it.key }.distinct().size)
    }

    @Test fun hiddenTotalsUseFeminineNumbersAndTheHintAddressesBothChildren() {
        val words = listOf("שתי", "שלוש", "ארבע", "חמש", "שש", "שבע", "שמונה", "תשע", "עשר")
        assertEquals((2..10).toSet(), MathContent.birdsNow.keys)
        for (total in 2..10) {
            val line = MathContent.birdsNow.getValue(total)
            assertEquals("voice_big_math_birds_now_$total", line.key)
            assertEquals("על הענף יש עכשיו ${words[total - 2]} ציפורים.", line.text.he)
        }
        assertEquals("בואי נתחיל מהציפורים שרואים ונספור הלאה.", MathContent.hideHint.text.heGirl)
        assertEquals("בוא נתחיל מהציפורים שרואים ונספור הלאה.", MathContent.hideHint.text.he)
    }

    @Test fun ambiguousEightUsesGenderAppropriatePhonemeStems() {
        for (stem in listOf("voice_big_math_birds_start_8_he", "voice_big_math_birds_join_8_he",
            "voice_big_math_birds_leave_8_he", "voice_big_math_picnic_8_he_f", "voice_big_math_picnic_8_he_m",
            "voice_big_math_shown_8_he", "voice_big_math_frame_8_he")) {
            assertTrue(stem, pack.contains("\"$stem\":"))
        }
    }

    @Test fun frameTargetsUseEightCompleteSentencesWithMasculineNumberWords() {
        val words = listOf("שְׁנֵי", "שְׁלוֹשָׁה", "אַרְבָּעָה", "חֲמִשָּׁה", "שִׁשָּׁה", "שִׁבְעָה", "שְׁמוֹנָה", "תִּשְׁעָה")
        assertEquals((2..9).toSet(), MathContent.frameTargets.keys)
        for (target in 2..9) {
            val line = MathContent.frameTargets.getValue(target)
            assertEquals("voice_big_math_frame_target_$target", line.key)
            assertEquals("בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת ${words[target - 2]} עִיגּוּלִים.", line.text.he)
            assertTrue(MathContent.allLines.contains(line))
        }
    }
}
