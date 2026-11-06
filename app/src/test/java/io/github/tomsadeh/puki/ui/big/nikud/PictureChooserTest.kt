package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PictureChooserTest {
    @Test
    fun picturesThatCanAnswerTheSameWordNeverShareARound() {
        val excluded = listOf("kaf" to "yad", "yam" to "sira", "gag" to "gan",
            "gal" to "yam", "gan" to "gina",
            "dubi" to "buba",   // the teddy is דֻּבִּי, and a child may call it a doll
            "kadur" to "pina")  // the corner scene has a ball in it
        for ((a, b) in excluded) {
            assertTrue("$a / $b", Nikud.areTwins(Nikud.word(a), Nikud.word(b)))
            assertTrue("$b / $a", Nikud.areTwins(Nikud.word(b), Nikud.word(a)))
        }
        for (seed in 0 until 50) for (level in DistractorLevel.values()) {
            for (target in Nikud.words.filter { it.pictureId != null }) {
                val random = Random(seed)
                val layout = PictureChooser(random, RoundScheduler(random = random))
                    .choose(target, level, pool = Nikud.words)
                assertEquals(target, layout.options[layout.correctIndex])
                assertEquals(3, layout.options.size)
                val ids = layout.options.map { it.id }.toSet()
                for ((a, b) in excluded) {
                    assertFalse("seed $seed, $level, ${target.id}: $ids", a in ids && b in ids)
                }
            }
        }
    }

    @Test
    fun expandedFamiliesNeedReadingBeyondTheFirstConsonant() {
        val families = listOf(listOf("sal", "sir", "sira", "sus"), listOf("pil", "para", "panas"),
            listOf("gag", "gal", "gamal"), listOf("kaf", "kadur"))
        for (ids in families) {
            assertEquals(1, ids.map { Nikud.word(it).firstConsonant }.toSet().size)
            for (a in ids) for (b in ids) if (a != b) assertFalse(Nikud.areTwins(Nikud.word(a), Nikud.word(b)))
        }
        for (seed in 0 until 30) for (id in listOf("sal", "sir", "sira", "sus")) {
            val random = Random(seed)
            val layout = PictureChooser(random, RoundScheduler(random = random))
                .choose(Nikud.word(id), DistractorLevel.SAME_START, pool = Nikud.clearWords)
            // three of the four סָ words, so only reading to the end tells them apart
            assertTrue(layout.options.map { it.id }.toSet().all { it in setOf("sal", "sir", "sira", "sus") })
        }
        for (seed in 0 until 30) for ((id, rival) in listOf("kaf" to "kadur", "kadur" to "kaf")) {
            val random = Random(seed)
            val layout = PictureChooser(random, RoundScheduler(random = random))
                .choose(Nikud.word(id), DistractorLevel.SAME_START, pool = Nikud.clearWords)
            assertTrue(layout.options.any { it.id == rival })
        }
        val random = Random(3)
        val chooser = PictureChooser(random, RoundScheduler(random = random))
        val pe = chooser.choose(Nikud.word("panas"), DistractorLevel.SAME_START, pool = Nikud.words)
        assertTrue(pe.options.any { it.id == "pil" })
        assertFalse(pe.options.any { it.id in Nikud.pendingAudio })
    }

    @Test
    fun theTeddyAndTheBallNeverShareARoundWithAPictureThatCouldBeCalledTheSame() {
        for (seed in 0 until 60) for (level in DistractorLevel.values()) for (pool in listOf(Nikud.words, Nikud.clearWords)) {
            for (target in pool.filter { it.pictureId != null }) {
                val random = Random(seed)
                val pictures = PictureChooser(random, RoundScheduler(random = random)).choose(target, level, pool = pool)
                    .options.map { it.pictureId }
                assertFalse("teddy beside a doll: $pictures", "teddy" in pictures && "doll" in pictures)
                assertFalse("ball beside the corner scene: $pictures", "ball" in pictures && "corner" in pictures)
            }
        }
    }

    @Test
    fun pictureLayoutsChangeAfterSavingAndReloading() {
        for (seed in 0 until 30) for (word in Nikud.words.filter { it.pictureId != null && it.id !in Nikud.pendingAudio }) {
            val random = Random(seed)
            val firstScheduler = RoundScheduler(random = random)
            val first = PictureChooser(random, firstScheduler).choose(word, DistractorLevel.DIFFERENT_START)
            val memory = SchedulerMemory.decode(firstScheduler.checkpoint().encode())
            val nextRandom = Random(seed)
            val nextScheduler = RoundScheduler(memory, nextRandom)
            val next = PictureChooser(nextRandom, nextScheduler).choose(word, DistractorLevel.DIFFERENT_START)
            assertNotEquals(first.correctIndex, next.correctIndex)
            assertNotEquals(first.options.map { it.id }.toSet(), next.options.map { it.id }.toSet())
            assertEquals(memory, SchedulerMemory.decode(memory.encode()))
            val distractors = next.options.filter { it.id != word.id }
            assertTrue(distractors.all { it.tier == Nikud.Tier.CLEAR && it.firstConsonant != word.firstConsonant })
            for (a in next.options) for (b in next.options) if (a.id != b.id) assertFalse(Nikud.areTwins(a, b))
        }
    }

    @Test
    fun sameStartRivalsTakePriorityOverFreshDistractorsAfterReload() {
        for (seed in 0 until 30) for (word in Nikud.clearWords) {
            var memory = SchedulerMemory()
            var previousSlot: Int? = null
            repeat(4) {
                val random = Random(seed)
                val scheduler = RoundScheduler(memory, random)
                val layout = PictureChooser(random, scheduler).choose(word, DistractorLevel.SAME_START, pool = Nikud.clearWords)
                if (previousSlot != null) assertNotEquals(previousSlot, layout.correctIndex)
                previousSlot = layout.correctIndex
                val rivals = Nikud.clearWords.filter {
                    it.id != word.id && it.firstConsonant == word.firstConsonant && !Nikud.areTwins(word, it)
                }
                if (rivals.isNotEmpty()) assertTrue(layout.options.any { it in rivals })
                if (rivals.size == 2 && !Nikud.areTwins(rivals[0], rivals[1])) assertTrue(layout.options.containsAll(rivals))
                for (a in layout.options) for (b in layout.options) if (a.id != b.id) assertFalse(Nikud.areTwins(a, b))
                memory = SchedulerMemory.decode(scheduler.checkpoint().encode())
            }
        }
    }
}
