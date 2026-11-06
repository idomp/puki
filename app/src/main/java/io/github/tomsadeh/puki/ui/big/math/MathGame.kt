package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathLine
import kotlin.random.Random

enum class MathOperation { PLUS, MINUS }

/** Render operands in this order, left to right, even in Hebrew. Null b means a visible ?. */
data class Equation(val a: Int, val op: MathOperation, val b: Int?, val result: Int)

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
    private val progress: MathProgressStore
) {
    abstract val state: S
    protected var session = MathSessionState()
        private set
    private var voiceToken = 0L

    suspend fun start() {
        if (!session.isLoading) return
        session = session.copy(stage = progress.load(gameId), isLoading = false)
        startRound()
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
            val stage = nextMathStage(session.stage, session.totalRounds, session.misses, session.missedRounds)
            progress.save(gameId, stage)
            session = session.copy(sessionComplete = true, nextStage = stage)
            refreshSession()
        } else {
            session = session.copy(roundIndex = session.roundIndex + 1, roundMisses = 0)
            startRound()
        }
    }

    abstract fun replay()
    protected abstract fun startRound()
    protected abstract fun refreshSession()
}

internal fun mathChoices(answer: Int, maximum: Int, allowsZero: Boolean, random: Random): List<Int> {
    val minimum = if (allowsZero) 0 else 1
    require(answer in minimum..maximum)
    return ((minimum..maximum).filter { it != answer }.shuffled(random).take(3) + answer).shuffled(random)
}
