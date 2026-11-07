package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

data class FrameRound(val capacity: Int, val filled: Int) {
    val objectKind: MathObject get() = MathObject.COUNTER
    val missing: Int get() = capacity - filled
    init { require(capacity == 5 || capacity == 10); require(filled in 0..capacity) }
}

data class FrameState(
    val round: FrameRound? = null,
    val choices: List<Int> = emptyList(),
    val prompt: List<MathLine> = emptyList(),
    /** Filled first, then empty; a future screen may render five cells per row. */
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
        val round = draw(framePool(session.stage, session.roundIndex)) { "${it.capacity}:${it.filled}" }
        val capacity = round.capacity
        val filled = round.filled
        val prompt = listOf(if (capacity == 5) MathContent.frameFive else MathContent.frameTen,
            MathContent.frameFilled[filled], MathContent.howManyMissing)
        state = FrameState(
            round = round, choices = mathChoices(round.missing, capacity, session.stage.allowsZero, random, answerSlot()),
            prompt = prompt, cells = List(capacity) { it < filled },
            equation = if (session.stage >= MathStage.MIXED_5 && session.roundIndex >= 3)
                Equation(filled, MathOperation.PLUS, null, capacity) else null,
            session = session, voice = cue(prompt)
        )
    }

    fun onAnswer(number: Int) {
        if (!canAnswer() || number !in state.choices) return
        val round = requireNotNull(state.round)
        if (number == round.missing) {
            win()
            state = state.copy(roundComplete = true, feedback = null,
                equation = Equation(round.filled, MathOperation.PLUS, round.missing, round.capacity),
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

private fun frameCapacity(stage: MathStage, index: Int) = if (stage == MathStage.MIXED_10 && index >= 2) 10 else 5

/**
 * Every frame this round may show. Counters stay in canonical order, so a problem is just a count. The empty and
 * full examples are fixed rounds where zero is taught, and the open rounds leave them out so nothing repeats.
 */
internal fun framePool(stage: MathStage, index: Int): List<FrameRound> {
    if (stage != stage.countingStage) return framePool(stage.countingStage, index)
    val capacity = frameCapacity(stage, index)
    if (stage.allowsZero && index == 3) return listOf(FrameRound(capacity, 0))
    if (stage.allowsZero && index == 4) return listOf(FrameRound(capacity, capacity))
    // Zero-stage warmups on the small frame may still be empty or full; the big frame saves them for the examples.
    val range = if (stage == MathStage.MIXED_10 && capacity == 5) 0..5 else 1 until capacity
    return range.map { FrameRound(capacity, it) }
}

/** Everything a stage can show over its six rounds. */
internal fun frameCatalog(stage: MathStage): Set<FrameRound> =
    (0 until 6).flatMap { framePool(stage, it) }.toSet()
