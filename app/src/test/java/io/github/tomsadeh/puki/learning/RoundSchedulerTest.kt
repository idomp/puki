package io.github.tomsadeh.puki.learning

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoundSchedulerTest {
    private val pool = (1..22).map { "i$it" }

    private fun scheduler(seed: Int, memory: SchedulerMemory = SchedulerMemory()) = RoundScheduler(memory, Random(seed))

    /** Plays a whole session; [missAt] says which rounds get a wrong answer first. Returns the targets in order. */
    private fun play(s: RoundScheduler, rounds: Int = 8, missAt: (Int, String) -> Boolean = { _, _ -> false }): List<String> {
        s.begin(rounds, pool)
        val seen = mutableListOf<String>()
        while (true) {
            val item = s.current ?: break
            seen.add(item)
            if (missAt(s.roundIndex, item)) s.miss()
            if (s.advance() == null) break
        }
        return seen
    }

    @Test
    fun aSessionCoversDifferentItems() {
        for (seed in 0 until 100) {
            val plan = scheduler(seed).begin(8, pool)
            assertEquals(8, plan.size)
            assertEquals("seed $seed", 8, plan.toSet().size)
        }
    }

    @Test
    fun aSmallPoolRepeatsButNeverBackToBack() {
        for (seed in 0 until 50) {
            val plan = scheduler(seed).begin(8, listOf("a", "b", "c"))
            assertEquals(8, plan.size)
            plan.zipWithNext().forEach { (x, y) -> assertNotEquals("seed $seed: $plan", x, y) }
            assertTrue(plan.toSet().size == 3)
        }
    }

    @Test
    fun groupCapsLimitTheFinals() {
        val mixed = pool + listOf("f1", "f2", "f3", "f4", "f5")
        for (seed in 0 until 200) {
            val plan = scheduler(seed).begin(8, mixed, groupOf = { if (it.startsWith("f")) "final" else "" }, groupCaps = mapOf("final" to 2))
            assertTrue("seed $seed: $plan", plan.count { it.startsWith("f") } <= 2)
        }
    }

    @Test
    fun aMissedItemComesBackTwoOrThreeRoundsLater() {
        for (seed in 0 until 200) {
            val s = scheduler(seed)
            val targets = play(s) { index, _ -> index == 1 }
            val first = targets[1]
            val again = targets.indices.filter { it > 1 && targets[it] == first }
            assertEquals("seed $seed: $targets", 1, again.size)
            assertTrue("seed $seed: back at ${again[0]}", again[0] == 3 || again[0] == 4)
        }
    }

    @Test
    fun itComesBackOnlyOnceAndOnlyWhereThereIsRoom() {
        val s = scheduler(1)
        s.begin(8, pool)
        repeat(6) { s.advance() }  // round 6: no room for 2 more
        assertNull(s.miss())
        s.advance()
        assertNull(s.miss())  // round 7: the last one

        val t = scheduler(2)
        t.begin(8, pool)
        assertNotNull(t.miss())
        assertNull("a second miss in the same round does nothing", t.miss())
    }

    @Test
    fun aSecondMissOfTheSameItemDoesNotLoopForever() {
        for (seed in 0 until 50) {
            val targets = play(scheduler(seed)) { _, _ -> true }  // wrong every time
            assertEquals(8, targets.size)
            for (item in targets.toSet()) assertTrue("seed $seed: $targets", targets.count { it == item } <= 2)
        }
    }

    @Test
    fun missedItemsAreFavouredInTheNextSession() {
        var missedBack = 0
        var cleanBack = 0
        for (seed in 0 until 400) {
            val first = scheduler(seed)
            val played = play(first) { index, _ -> index == 0 }
            val missed = played[0]
            val clean = played[1]
            val memory = first.finish()
            assertEquals(ItemRecord(1, 1, 1), memory.record(missed))
            assertEquals(ItemRecord(1, 1, 0), memory.record(clean))
            val second = scheduler(seed + 1000, memory).begin(8, pool)
            if (missed in second) missedBack++
            if (clean in second) cleanBack++
        }
        assertTrue("missed $missedBack vs clean $cleanBack", missedBack > 2 * cleanBack)
        assertTrue(missedBack > 400 / 2)
    }

    @Test
    fun newAndLongUnseenItemsComeBeforeRecentOnes() {
        val recent = SchedulerMemory(5, pool.take(11).associateWith { ItemRecord(3, 5, 0) })
        var oldHits = 0
        var recentHits = 0
        for (seed in 0 until 200) {
            val plan = scheduler(seed, recent).begin(8, pool)
            recentHits += plan.count { it in pool.take(11) }
            oldHits += plan.count { it !in pool.take(11) }
        }
        assertTrue("never seen $oldHits vs just seen $recentHits", oldHits > recentHits * 1.3)
    }

    @Test
    fun aCleanSessionCoolsOffAShakyItem() {
        val s = scheduler(3, SchedulerMemory(4, mapOf("i1" to ItemRecord(2, 4, 2))))
        s.begin(1, listOf("i1"))
        s.advance()
        val m = s.finish()
        assertEquals(ItemRecord(3, 5, 1), m.record("i1"))
        assertEquals(5, m.session)
        assertFalse(m.isKnown("i1"))
    }

    @Test
    fun memoryRoundTripsAndForgivesGarbage() {
        val memory = SchedulerMemory(7, mapOf("alef" to ItemRecord(2, 6, 1), "kaf_final" to ItemRecord(1, 7, 0)))
        assertEquals(memory, SchedulerMemory.decode(memory.encode()))
        assertEquals(SchedulerMemory(), SchedulerMemory.decode(null))
        assertEquals(SchedulerMemory(), SchedulerMemory.decode("garbage"))
        assertEquals(SchedulerMemory(3, mapOf("ok" to ItemRecord(1, 2, 3))), SchedulerMemory.decode("3;ok,1,2,9;bad,x,1,1;,1,1,1;short,1"))
    }

    @Test
    fun theInMemoryStoreKeepsEachGameApart() = runBlocking {
        val store = InMemorySchedulerStore()
        val m = SchedulerMemory(2, mapOf("x" to ItemRecord(1, 2, 0)))
        store.save("one", m)
        assertEquals(m, store.load("one"))
        assertEquals(SchedulerMemory(), store.load("two"))
    }

    @Test
    fun choicesHaveTheAnswerOnceAndStayDistinct() {
        for (seed in 0 until 100) {
            val c = scheduler(seed).choices("i1", pool, 4, { 2 })
            assertEquals(4, c.options.size)
            assertEquals(4, c.options.toSet().size)
            assertEquals(1, c.options.count { it == "i1" })
            assertEquals("i1", c.options[c.correctIndex])
        }
    }

    @Test
    fun distractorsAreTheVisuallyDistantOnesAndLookalikesOnlyWhenForced() {
        // i2..i5 look like the answer (distance 0), i6..i10 a bit (1), the rest are far (2)
        val distance = { id: String -> when (id.drop(1).toInt()) { in 2..5 -> 0; in 6..10 -> 1; else -> 2 } }
        for (seed in 0 until 100) {
            val c = scheduler(seed).choices("i1", pool, 4, distance)
            assertTrue("seed $seed $c", c.options.filter { it != "i1" }.all { distance(it) == 2 })
        }
        val forced = scheduler(1).choices("i1", pool, 3, distance, forced = listOf("i3"))
        assertTrue("i3" in forced.options)
        assertEquals(1, forced.options.count { it != "i1" && distance(it) == 2 })
        // when there are too few far ones, the nearer ones fill in, still never a look-alike
        val few = pool.filter { distance(it) != 2 || it in listOf("i11", "i12") }
        val c = scheduler(2).choices("i1", few, 5, distance)
        assertTrue(c.options.none { it in listOf("i2", "i3", "i4", "i5") })
    }

    @Test
    fun theAnswerPositionIsBalancedAcrossASession() {
        for (seed in 0 until 100) {
            for (count in listOf(3, 4)) {
                val s = scheduler(seed)
                val slots = (1..8).map { s.choices("i$it", pool, count, { 2 }).correctIndex }
                val counts = (0 until count).map { p -> slots.count { it == p } }
                assertTrue("seed $seed count $count: $slots", counts.max() - counts.min() <= 1)
                // never the same place three times in a row
                for (i in 2 until slots.size) assertFalse("seed $seed: $slots", slots[i] == slots[i - 1] && slots[i] == slots[i - 2])
            }
        }
    }

    @Test
    fun askedAgainItGoesElsewhereWithOtherDistractors() {
        for (seed in 0 until 100) {
            val s = scheduler(seed)
            val first = s.choices("i1", pool, 4, { 2 })
            s.choices("i2", pool, 4, { 2 })
            val second = s.choices("i1", pool, 4, { 2 })
            assertNotEquals("seed $seed", first.correctIndex, second.correctIndex)
            val d1 = first.options.toSet() - "i1"
            val d2 = second.options.toSet() - "i1"
            assertTrue("seed $seed: $d1 vs $d2", d1.intersect(d2).isEmpty())
        }
    }

    @Test
    fun theSameSeedGivesTheSameSession() {
        val a = scheduler(42).begin(8, pool)
        val b = scheduler(42).begin(8, pool)
        assertEquals(a, b)
        assertEquals(scheduler(42).choices("i1", pool, 4, { 2 }), scheduler(42).choices("i1", pool, 4, { 2 }))
    }
}
