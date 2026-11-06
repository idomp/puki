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

/** Everything the scheduler keeps for one game: the number of finished sessions and a record per item. */
data class SchedulerMemory(
    val session: Int = 0,
    val records: Map<String, ItemRecord> = emptyMap()
) {
    fun record(id: String): ItemRecord = records[id] ?: ItemRecord()

    /** Asked at least once and not shaky now. */
    fun isKnown(id: String): Boolean = record(id).let { it.seen > 0 && it.misses == 0 }

    /** One line of text, ids as `[a-z0-9_]` only: `session;id,seen,last,misses;id,...`. */
    fun encode(): String =
        (listOf(session.toString()) + records.map { (id, r) -> "$id,${r.seen},${r.lastSession},${r.misses}" })
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
                if (f.size != 4 || f[0].isEmpty() || seen == null || last == null || misses == null) null
                else f[0] to ItemRecord(seen.coerceAtLeast(0), last.coerceAtLeast(0), misses.coerceIn(0, ItemRecord.MAX_MISSES))
            }.toMap()
            return SchedulerMemory(session, records)
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
 * (letters now; numbers or nikud later).
 *
 *  - [begin] draws the session's items without replacement, favouring items never asked, not asked
 *    for a while, or missed in earlier sessions (see [SchedulerMemory]).
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
    private val shown = HashMap<String, Pair<Int, Set<String>>>()  // key -> (slot, distractors) of the last showing

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
        groupCaps: Map<String, Int> = emptyMap()
    ): List<String> {
        require(pool.isNotEmpty() && rounds > 0)
        val ids = pool.distinct()
        plan.clear(); retrySlots.clear(); retried.clear(); visited.clear(); missedItems.clear(); shown.clear()
        missedThisRound = false
        index = 0
        begun = true
        val perGroup = HashMap<String, Int>()
        var remaining = ids.toMutableList()
        var last: String? = null
        while (plan.size < rounds) {
            if (remaining.isEmpty()) {  // the pool is used up: start another pass (not the same item twice in a row)
                remaining = ids.filter { it != last || ids.size == 1 }.toMutableList()
            }
            val allowed = remaining.filter { id -> (groupCaps[groupOf(id)] ?: Int.MAX_VALUE) > (perGroup[groupOf(id)] ?: 0) }
            val from = allowed.ifEmpty { remaining }
            val pick = weighted(from)
            remaining.remove(pick)
            perGroup.merge(groupOf(pick), 1, Int::plus)
            plan.add(pick)
            last = pick
        }
        return plan.toList()
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
        val gap = if (random.nextBoolean()) 2 else 3
        val slot = listOf(index + gap, index + 5 - gap).firstOrNull { it < plan.size && it !in retrySlots } ?: return null
        plan[slot] = item  // (the item that was planned there waits for a later session)
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
        return current
    }

    /** The memory after this session (items asked so far count). Call once, when the session ends. */
    fun finish(): SchedulerMemory {
        val records = memory.records.toMutableMap()
        for (id in visited.distinct()) {
            val old = memory.record(id)
            val shaky = if (id in missedItems) minOf(old.misses + 1, ItemRecord.MAX_MISSES) else maxOf(old.misses - 1, 0)
            records[id] = ItemRecord(old.seen + 1, session, shaky)
        }
        return SchedulerMemory(session, records)
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
            .sortedWith(compareBy<String> { it in (before?.second ?: emptySet()) }.thenByDescending(distanceTo))
            .take(need)
        val others = (taken + picked).shuffled(random)
        val slot = slots.draw(others.size + 1, avoid = before?.first?.takeIf { others.isNotEmpty() })
        val options = others.toMutableList().also { it.add(slot.coerceAtMost(it.size), answer) }
        shown[key] = options.indexOf(answer) to (taken + picked).toSet()
        return Choices(answer, options)
    }
}
