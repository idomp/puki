package io.github.tomsadeh.puki.learning

import kotlin.random.Random

/** What the scheduler remembers about one item (an id) between sessions. */
data class ItemRecord(
    /** Sessions in which the item was asked. */
    val seen: Int = 0,
    /** The session number it was last asked in (0 = never). */
    val lastSession: Int = 0,
    /** How shaky it is: +1 for a session with a miss, -1 for a clean one; 0..[MAX_MISSES]. */
    val misses: Int = 0
) {
    companion object {
        const val MAX_MISSES = 3
    }
}

/** The last layout of a task, kept separately from its learning record. */
data class PresentationRecord(val position: Int, val distractors: Set<String>)

/** Learning, coverage and presentation history for one game. */
data class SchedulerMemory(
    val session: Int = 0,
    val records: Map<String, ItemRecord> = emptyMap(),
    val covered: Set<String> = emptySet(),
    val lastItem: String? = null,
    val presentations: Map<String, PresentationRecord> = emptyMap(),
    val opening: String? = null
) {
    fun record(id: String): ItemRecord = records[id] ?: ItemRecord()

    /** Asked at least once and not shaky now. */
    fun isKnown(id: String): Boolean = record(id).let { it.seen > 0 && it.misses == 0 }

    /** Keeps the original learning records, followed by tagged history fields. */
    fun encode(): String =
        (listOf(session.toString()) + records.map { (id, r) -> "$id,${r.seen},${r.lastSession},${r.misses}" } +
            listOfNotNull(
                covered.takeIf { it.isNotEmpty() }?.let { "@covered,${it.joinToString(",")}" },
                lastItem?.let { "@last,$it" },
                opening?.let { "@opening,$it" }
            ) + presentations.map { (key, p) -> "@layout,$key,${p.position},${p.distractors.joinToString(",")}" })
            .joinToString(";")

    companion object {
        /** Forgiving: anything unreadable gives an empty memory (or skips the bad record). */
        fun decode(text: String?): SchedulerMemory {
            if (text.isNullOrBlank()) return SchedulerMemory()
            val parts = text.split(';')
            val session = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: return SchedulerMemory()
            val records = parts.drop(1).mapNotNull { part ->
                val f = part.split(',')
                val seen = f.getOrNull(1)?.toIntOrNull()
                val last = f.getOrNull(2)?.toIntOrNull()
                val misses = f.getOrNull(3)?.toIntOrNull()
                if (f.size != 4 || f[0].isEmpty() || f[0].startsWith('@') || seen == null || last == null || misses == null) null
                else f[0] to ItemRecord(seen.coerceAtLeast(0), last.coerceAtLeast(0), misses.coerceIn(0, ItemRecord.MAX_MISSES))
            }.toMap()
            val covered = parts.firstOrNull { it.startsWith("@covered,") }?.split(',')?.drop(1)?.filter { it.isNotEmpty() }?.toSet().orEmpty()
            val lastItem = parts.firstOrNull { it.startsWith("@last,") }?.substringAfter(',')?.takeIf { it.isNotEmpty() }
            val presentations = parts.filter { it.startsWith("@layout,") }.mapNotNull {
                val f = it.split(',')
                val slot = f.getOrNull(2)?.toIntOrNull()
                if (f.getOrNull(1).isNullOrEmpty() || slot == null || slot < 0) null
                else f[1] to PresentationRecord(slot, f.drop(3).filter { id -> id.isNotEmpty() }.toSet())
            }.toMap()
            val opening = parts.firstOrNull { it.startsWith("@opening,") }?.substringAfter(',')?.takeIf { it.isNotEmpty() }
            return SchedulerMemory(session, records, covered, lastItem, presentations, opening)
        }
    }
}

/** Where a game's [SchedulerMemory] lives between sessions. */
interface SchedulerStore {
    suspend fun load(key: String): SchedulerMemory
    suspend fun save(key: String, memory: SchedulerMemory)
}

/** For tests (and previews): forgets everything when the object goes. */
class InMemorySchedulerStore : SchedulerStore {
    private val saved = HashMap<String, String>()
    override suspend fun load(key: String) = SchedulerMemory.decode(saved[key])
    override suspend fun save(key: String, memory: SchedulerMemory) {
        saved[key] = memory.encode()
    }
}

/**
 * Hands out answer positions so that, over a session, each position comes up as evenly as possible
 * (a shuffled bag per number of choices, refilled when empty).
 */
class SlotBag(private val random: Random) {
    private val bags = HashMap<Int, MutableList<Int>>()

    /** A position in 0 until [count], not [avoid] if another is available. */
    fun draw(count: Int, avoid: Int? = null): Int {
        require(count >= 1)
        if (count == 1) return 0
        val bag = bags.getOrPut(count) { mutableListOf() }
        if (bag.isEmpty()) bag.addAll((0 until count).shuffled(random))
        val at = bag.indexOfFirst { it != avoid }
        if (at >= 0) return bag.removeAt(at)
        // Only the slot to avoid is left in the bag: use another one, and keep that one for later
        return (0 until count).filter { it != avoid }.random(random)
    }

    /** Start the next session with fresh bags. */
    fun reset() = bags.clear()
}

/**
 * Picks a session's items and the choices around them. Items are plain ids, so any game can use it
 * across the reading games.
 *
 *  - [begin] covers the eligible pool across visits; shaky items may return sooner for review.
 *  - [miss] says the child got the current item wrong: it comes back 2-3 rounds later in the same
 *    session (if the session is long enough), and is favoured in later ones.
 *  - [advance] goes to the next round; [finish] gives the updated memory to save.
 *  - [choices] builds the answer options: visually distant distractors first, the correct answer in
 *    a balanced position, a different position and different distractors the second time around.
 */
class RoundScheduler(
    val memory: SchedulerMemory = SchedulerMemory(),
    private val random: Random = Random.Default,
    val slots: SlotBag = SlotBag(random)
) {
    /** The options of one round: [options] holds [answer] at [correctIndex]. */
    data class Choices(val answer: String, val options: List<String>) {
        val correctIndex: Int get() = options.indexOf(answer)
    }

    private val plan = mutableListOf<String>()
    private val retrySlots = mutableSetOf<Int>()
    private val retried = mutableSetOf<String>()
    private val visited = mutableListOf<String>()
    private val missedItems = mutableSetOf<String>()
    private var missedThisRound = false
    private var index = 0
    private var begun = false
    private val shown = memory.presentations.toMutableMap()
    private val covered = memory.covered.toMutableSet()
    private var lastItem = memory.lastItem
    private var opening = memory.opening
    private var eligible = emptyList<String>()

    /** The session number being played (1 = the very first). */
    val session: Int get() = memory.session + 1

    val total: Int get() = plan.size
    val roundIndex: Int get() = index
    val current: String? get() = if (begun) plan.getOrNull(index) else null
    val isFinished: Boolean get() = begun && index >= plan.size
    fun isKnown(id: String) = memory.isKnown(id)
    fun knownCount(ids: Collection<String>) = ids.count { memory.isKnown(it) }

    private fun weight(id: String): Double {
        val r = memory.record(id)
        val age = if (r.seen == 0) 2.0 else 0.5 * minOf(session - r.lastSession, 6)
        return 1.0 + age + 4.0 * r.misses
    }

    /**
     * Plans [rounds] rounds from [pool] and returns the plan (the first item is [current]). No item
     * repeats until the pool is used up; [groupCaps] limits how many of a group ([groupOf]) one
     * session holds (for example, at most 2 final forms).
     */
    fun begin(
        rounds: Int,
        pool: List<String>,
        groupOf: (String) -> String = { "" },
        groupCaps: Map<String, Int> = emptyMap(),
        spreadGroups: Boolean = false
    ): List<String> {
        require(pool.isNotEmpty() && rounds > 0)
        val ids = pool.distinct()
        prepare(ids)
        val perGroup = HashMap<String, Int>()
        var remaining = ids.toMutableList()
        val cycle = covered.toMutableSet()
        var last: String? = lastItem
        while (plan.size < rounds) {
            if (remaining.isEmpty()) {  // the pool is used up: start another pass (not the same item twice in a row)
                remaining = ids.filter { it != last || ids.size == 1 }.toMutableList()
            }
            val allowed = remaining.filter { id -> (groupCaps[groupOf(id)] ?: Int.MAX_VALUE) > (perGroup[groupOf(id)] ?: 0) }
                .ifEmpty { remaining }
            if (ids.all { it in cycle }) cycle.removeAll(ids.toSet())
            val review = allowed.filter { memory.record(it).misses > 0 }
            val practice = if (spreadGroups) {
                val fewest = allowed.minOf { perGroup[groupOf(it)] ?: 0 }
                allowed.filter { (perGroup[groupOf(it)] ?: 0) == fewest }
            } else allowed
            val unused = practice.filter { it !in cycle }.ifEmpty { practice }
            val spread = if (spreadGroups) {
                val counts = ids.filter { it in cycle }.groupingBy(groupOf).eachCount()
                val fewest = unused.minOf { counts[groupOf(it)] ?: 0 }
                unused.filter { (counts[groupOf(it)] ?: 0) == fewest }
            } else unused
            // (review waits for the second round, so a shaky item doesn't open every visit)
            val from = if (plan.isEmpty()) spread else review.ifEmpty { spread }
            val fresh = if (plan.isEmpty()) from.filter { it != opening }.ifEmpty { from } else from
            val pick = weighted(fresh.filter { it != last }.ifEmpty { fresh })
            remaining.remove(pick)
            cycle.add(pick)
            perGroup.merge(groupOf(pick), 1, Int::plus)
            plan.add(pick)
            last = pick
        }
        expose()
        return plan.toList()
    }

    /** A short, balanced block supplied by a game (for example, two of each detective letter). */
    fun beginPlan(items: List<String>) {
        require(items.isNotEmpty())
        prepare(items.distinct())
        plan.addAll(items)
        expose()
    }

    private fun prepare(ids: List<String>) {
        plan.clear(); retrySlots.clear(); retried.clear(); visited.clear(); missedItems.clear()
        missedThisRound = false
        index = 0
        begun = true
        eligible = ids
    }

    private fun expose() {
        val item = current ?: return
        if (eligible.all { it in covered }) covered.removeAll(eligible.toSet())
        covered.add(item)
        lastItem = item
        if (index == 0) opening = item
    }

    private fun weighted(from: List<String>): String {
        val weights = from.map(::weight)
        var r = random.nextDouble() * weights.sum()
        for (i in from.indices) {
            r -= weights[i]
            if (r <= 0) return from[i]
        }
        return from.last()
    }

    /**
     * The child got [current] wrong (call it on the first wrong answer of a round; later calls in the
     * same round do nothing). It is asked again 2 or 3 rounds on, once per session, if there is room.
     * Returns the round it comes back in, or null.
     */
    fun miss(): Int? {
        val item = current ?: return null
        if (missedThisRound) return null
        missedThisRound = true
        missedItems.add(item)
        if (item in retried) return null
        // Reuse an existing occurrence when it already supplies the spaced review.
        val existing = (index + 2..minOf(index + 3, plan.lastIndex)).firstOrNull { plan[it] == item }
        if (existing != null) {
            retrySlots.add(existing)
            retried.add(item)
            return existing
        }
        val gap = if (random.nextBoolean()) 2 else 3
        val later = (index + 1 until plan.size).firstOrNull { plan[it] == item && it !in retrySlots }
        val slot = listOf(index + gap, index + 5 - gap).firstOrNull {
            if (it >= plan.size || it in retrySlots) false else {
                val proposed = plan.toMutableList()
                if (later != null) proposed[later] = proposed[it]
                proposed[it] = item
                proposed.getOrNull(it - 1) != item && proposed.getOrNull(it + 1) != item
            }
        } ?: return null
        if (later != null) plan[later] = plan[slot]
        plan[slot] = item  // displaced ordinary practice waits for another visit
        retrySlots.add(slot)
        retried.add(item)
        return slot
    }

    /** Goes to the next round and returns its item, or null when the session is over. */
    fun advance(): String? {
        val item = current ?: return null
        visited.add(item)
        missedThisRound = false
        index++
        expose()
        return current
    }

    /** What was shown, even on an abandoned visit; no extra mastery or misses are awarded. */
    fun checkpoint(): SchedulerMemory = memory.copy(
        covered = covered.toSet(), lastItem = lastItem, presentations = shown.toMap(), opening = opening
    )

    fun presentation(key: String): PresentationRecord? = shown[key]

    fun rememberPresentation(key: String, position: Int, distractors: Set<String>) {
        shown[key] = PresentationRecord(position, distractors)
    }

    /** The memory after this session (items asked so far count). Call once, when the session ends. */
    fun finish(): SchedulerMemory {
        val records = memory.records.toMutableMap()
        for (id in visited.distinct()) {
            val old = memory.record(id)
            val shaky = if (id in missedItems) minOf(old.misses + 1, ItemRecord.MAX_MISSES) else maxOf(old.misses - 1, 0)
            records[id] = ItemRecord(old.seen + 1, session, shaky)
        }
        return checkpoint().copy(session = session, records = records)
    }

    /**
     * The options for a round whose right answer is [answer].
     *  - [count] options in all; [forced] are always among them (a look-alike a game asks for).
     *  - Distractors come from [pool], those with the highest [distanceTo] first (random among equals);
     *    a candidate closer than [minDistance] is never used (so look-alikes only come as [forced]).
     *  - [key] names the round (default: the answer); asked again, it goes in a different position
     *    and keeps away from the distractors it had.
     */
    fun choices(
        answer: String,
        pool: List<String>,
        count: Int,
        distanceTo: (String) -> Int,
        minDistance: Int = 1,
        forced: List<String> = emptyList(),
        key: String = answer
    ): Choices {
        val before = shown[key]
        val taken = forced.distinct().filter { it != answer }
        val need = (count - 1 - taken.size).coerceAtLeast(0)
        val candidates = pool.distinct().filter { it != answer && it !in taken && distanceTo(it) >= minDistance }
        val picked = candidates.shuffled(random)
            .sortedWith(compareBy<String> { it in (before?.distractors ?: emptySet()) }.thenByDescending(distanceTo))
            .take(need)
        val others = (taken + picked).shuffled(random)
        val slot = slots.draw(others.size + 1, avoid = before?.position?.takeIf { others.isNotEmpty() })
        val options = others.toMutableList().also { it.add(slot.coerceAtMost(it.size), answer) }
        rememberPresentation(key, options.indexOf(answer), (taken + picked).toSet())
        return Choices(answer, options)
    }
}
