package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathLine
import kotlin.random.Random

enum class MathOperation { PLUS, MINUS }

/** Render operands in this order, left to right, even in Hebrew. A null operand means a visible ?. */
data class Equation(val a: Int?, val op: MathOperation, val b: Int?, val result: Int)

data class MathVoiceCue(val lines: List<MathLine> = emptyList(), val token: Long = 0) {
    val keys: List<String> get() = lines.map { it.key }
}

enum class MathFeedbackKind { COUNT_TOGETHER, COUNT_REMAINING, COUNT_EMPTY_SPACES, TOO_FEW, TOO_MANY }

data class MathFeedback(
    val chosen: Int,
    val kind: MathFeedbackKind,
    val hint: MathLine,
    /** For the picnic: positive means add, negative means remove. */
    val adjustment: Int? = null
)

data class MathSessionState(
    val stage: MathStage = MathStage.JOIN_5,
    val roundIndex: Int = 0,
    val totalRounds: Int = 6,
    val misses: Int = 0,
    val missedRounds: Int = 0,
    val roundMisses: Int = 0,
    val totalStarsEarned: Int = 0,
    val sessionComplete: Boolean = false,
    val isLoading: Boolean = true,
    val nextStage: MathStage? = null
) {
    val showHint: Boolean get() = roundMisses >= 2
}

interface MathState {
    val session: MathSessionState
    val voice: MathVoiceCue
    val roundComplete: Boolean
}

/** No Android, clocks, or audio player. A screen advances animations explicitly and plays cues. */
abstract class MathGame<S : MathState>(
    val gameId: MathGameId,
    protected val random: Random,
    private val progress: MathProgressStore,
    private val history: MathHistoryStore,
    /** The parent's starting level, read once when a session starts. */
    private val floorLevel: suspend () -> MathStage = { MathStage.JOIN_5 }
) {
    abstract val state: S
    protected var session = MathSessionState()
        private set
    private var voiceToken = 0L
    /** Earlier visits, oldest first; frozen at the start so this session's own plays never steer it. */
    private var earlier = emptyList<String>()
    private val played = mutableListOf<String>()
    private var answerSlots = emptyList<Int>()
    private var floor = MathStage.JOIN_5

    suspend fun start() {
        if (!session.isLoading) return
        earlier = history.load(gameId)
        floor = floorLevel()
        session = session.copy(stage = gameId.startStage(progress.load(gameId), floor), isLoading = false)
        startRound()
    }

    /**
     * Picks this round's problem from an explicit pool. Nothing repeats until the pool is spent; then the
     * oldest play comes back first, never twice in a row. Among the rest, problems the last visits showed
     * are avoided as far as the pool allows. Every pick is saved at once, so a left-early visit counts.
     */
    protected suspend fun <T> draw(pool: List<T>, key: (T) -> String): T {
        require(pool.isNotEmpty())
        val unseen = pool.filter { key(it) !in played }
        val candidates = if (unseen.isNotEmpty()) unseen else listOf(oldestPlay(pool, key))
        val pick = RECENT_WINDOWS.firstNotNullOfOrNull { window ->
            val avoid = earlier.takeLast(window).toSet()
            candidates.filter { key(it) !in avoid }.takeIf { it.isNotEmpty() }
        }.orEmpty().ifEmpty { candidates }.random(random)
        played += key(pick)
        history.save(gameId, (earlier + played).takeLast(RECENT_WINDOWS.first()))
        return pick
    }

    private fun <T> oldestPlay(pool: List<T>, key: (T) -> String): T {
        val last = played.last()
        val others = pool.filter { key(it) != last }.ifEmpty { pool }
        return others.minBy { played.lastIndexOf(key(it)) }
    }

    /** Where the right answer sits this round: each position once before any repeats, never twice in a row. */
    protected fun answerSlot(): Int {
        if (answerSlots.isEmpty()) answerSlots = balancedAnswerSlots(4, session.totalRounds, random)
        return answerSlots[session.roundIndex]
    }

    protected fun cue(lines: List<MathLine>) = MathVoiceCue(lines, ++voiceToken)
    protected fun cue(line: MathLine) = cue(listOf(line))
    protected fun canAnswer() = !session.isLoading && !session.sessionComplete && !state.roundComplete

    protected fun miss() {
        session = session.copy(
            misses = session.misses + 1,
            missedRounds = session.missedRounds + if (session.roundMisses == 0) 1 else 0,
            roundMisses = session.roundMisses + 1
        )
    }

    protected fun win() { session = session.copy(totalStarsEarned = session.totalStarsEarned + 1) }

    /** Guards early/double taps. Call after the answered round has been celebrated. */
    suspend fun next() {
        if (session.isLoading || session.sessionComplete || !state.roundComplete) return
        if (session.roundIndex + 1 == session.totalRounds) {
            val stage = nextMathStage(gameId, session.stage, session.totalRounds, session.misses, session.missedRounds, floor)
            progress.save(gameId, stage)
            session = session.copy(sessionComplete = true, nextStage = stage)
            refreshSession()
        } else {
            session = session.copy(roundIndex = session.roundIndex + 1, roundMisses = 0)
            startRound()
        }
    }

    abstract fun replay()
    protected abstract suspend fun startRound()
    protected abstract fun refreshSession()
}

private val RECENT_WINDOWS = listOf(12, 6, 3, 1)

/**
 * The consecutive window around the answer, shown in a shuffled order with the answer at [answerSlot]
 * (random when out of range). Callers keep the list as is, so it stays put through mistakes and replays.
 */
internal fun mathChoices(answer: Int, maximum: Int, allowsZero: Boolean, random: Random, answerSlot: Int = -1): List<Int> {
    val minimum = if (allowsZero) 0 else 1
    require(answer in minimum..maximum)
    val count = minOf(4, maximum - minimum + 1)
    // Keep the answer near the middle; clamp the whole window at either stage boundary.
    val before = if (count == 4) random.nextInt(1, 3) else count / 2
    val first = (answer - before).coerceIn(minimum, maximum - count + 1)
    val others = (first until first + count).filter { it != answer }.shuffled(random)
    val slot = if (answerSlot in 0 until count) answerSlot else random.nextInt(count)
    return others.take(slot) + answer + others.drop(slot)
}

/** Answer positions for a session: shuffled rounds of every position, so counts differ by at most one. */
internal fun balancedAnswerSlots(count: Int, rounds: Int, random: Random): List<Int> {
    val plan = mutableListOf<Int>()
    while (plan.size < rounds) {
        var bag = (0 until count).shuffled(random)
        if (plan.isNotEmpty() && count > 1 && bag.first() == plan.last()) bag = bag.drop(1) + bag.first()
        plan += bag
    }
    return plan.take(rounds)
}
