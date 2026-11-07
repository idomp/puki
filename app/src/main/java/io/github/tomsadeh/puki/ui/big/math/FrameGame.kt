package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

data class FrameRound(val capacity: Int, val filled: Int, val target: Int = capacity) {
    val objectKind: MathObject get() = MathObject.COUNTER
    val missing: Int get() = target - filled
    val isMakeN: Boolean get() = target < capacity
    // Preserve old full-frame keys; make-N problems include both the whole and the starting part.
    internal val historyKey: String get() = if (isMakeN) "part:$capacity:$target:$filled" else "$capacity:$filled"
    init {
        require(capacity == 5 || capacity == 10)
        require(target in 1..capacity)
        require(filled in 0..target)
        if (isMakeN) require(target in 2..9 && filled in 1 until target)
    }
}

data class FrameState(
    val round: FrameRound? = null,
    val choices: List<Int> = emptyList(),
    val prompt: List<MathLine> = emptyList(),
    /** Filled first, then empty, including inactive cells beyond a make-N target. */
    val cells: List<Boolean> = emptyList(),
    val equation: Equation? = null,
    val feedback: MathFeedback? = null,
    override val roundComplete: Boolean = false,
    override val session: MathSessionState = MathSessionState(),
    override val voice: MathVoiceCue = MathVoiceCue()
) : MathState

class FrameGame(random: Random, progress: MathProgressStore, history: MathHistoryStore = InMemoryMathHistoryStore()) :
    MathGame<FrameState>(MathGameId.FRAME, random, progress, history) {
    override var state = FrameState()
        private set

    override suspend fun startRound() {
        val round = draw(framePool(session.stage, session.roundIndex)) { it.historyKey }
        val capacity = round.capacity
        val filled = round.filled
        val prompt = listOf(if (round.isMakeN) MathContent.frameTargets.getValue(round.target)
            else if (capacity == 5) MathContent.frameFive else MathContent.frameTen,
            MathContent.frameFilled[filled], MathContent.howManyMissing)
        state = FrameState(
            round = round, choices = mathChoices(round.missing, capacity, session.stage.allowsZero, random, answerSlot()),
            prompt = prompt, cells = List(capacity) { it < filled },
            equation = if (!round.isMakeN && session.stage >= MathStage.MIXED_5 && session.roundIndex >= 3)
                Equation(filled, MathOperation.PLUS, null, round.target) else null,
            session = session, voice = cue(prompt)
        )
    }

    fun onAnswer(number: Int) {
        if (!canAnswer() || number !in state.choices) return
        val round = requireNotNull(state.round)
        if (number == round.missing) {
            win()
            state = state.copy(roundComplete = true, feedback = null,
                equation = Equation(round.filled, MathOperation.PLUS, round.missing, round.target),
                session = session, voice = cue(MathContent.exact))
        } else {
            miss()
            state = state.copy(feedback = MathFeedback(number, MathFeedbackKind.COUNT_EMPTY_SPACES, MathContent.frameHint),
                session = session, voice = cue(listOf(MathContent.frameHint, MathContent.howManyMissing)))
        }
    }

    override fun replay() {
        if (!session.isLoading && !session.sessionComplete) state = state.copy(voice = cue(state.prompt))
    }
    override fun refreshSession() { state = state.copy(session = session, voice = cue(emptyList())) }
}

private fun frameCapacity(stage: MathStage, index: Int) =
    if (stage in listOf(MathStage.MIXED_10, MathStage.PART_10) && index >= 2) 10 else 5

/**
 * Every frame this round may show. Counters stay in canonical order; make-N problems also vary the target.
 * Empty and full examples are fixed rounds where zero is taught; make-N rounds keep both parts positive.
 */
internal fun framePool(stage: MathStage, index: Int): List<FrameRound> {
    val capacity = frameCapacity(stage, index)
    if (stage >= MathStage.PART_5) {
        val targets = if (capacity == 5) 2..4 else 6..9
        return targets.flatMap { target -> (1 until target).map { FrameRound(capacity, it, target) } }
    }
    if (stage.allowsZero && index == 3) return listOf(FrameRound(capacity, 0))
    if (stage.allowsZero && index == 4) return listOf(FrameRound(capacity, capacity))
    // Zero-stage warmups on the small frame may still be empty or full; the big frame saves them for the examples.
    val range = if (stage == MathStage.MIXED_10 && capacity == 5) 0..5 else 1 until capacity
    return range.map { FrameRound(capacity, it) }
}

/** Everything a stage can show over its six rounds. */
internal fun frameCatalog(stage: MathStage): Set<FrameRound> =
    (0 until 6).flatMap { framePool(stage, it) }.toSet()
