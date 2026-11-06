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

class FrameGame(random: Random, progress: MathProgressStore) :
    MathGame<FrameState>(MathGameId.FRAME, random, progress) {
    override var state = FrameState()
        private set

    override fun startRound() {
        val capacity = if (session.stage == MathStage.MIXED_10 && session.roundIndex >= 2) 10 else 5
        val minimum = if (session.stage.allowsZero) 0 else 1
        val maximum = if (session.stage.allowsZero) capacity else capacity - 1
        val filled = when {
            session.stage.allowsZero && session.roundIndex == 3 -> 0
            session.stage.allowsZero && session.roundIndex == 4 -> capacity
            else -> random.nextInt(minimum, maximum + 1)
        }
        val round = FrameRound(capacity, filled)
        val prompt = listOf(if (capacity == 5) MathContent.frameFive else MathContent.frameTen,
            MathContent.frameFilled[filled], MathContent.howManyMissing)
        state = FrameState(
            round = round, choices = mathChoices(round.missing, capacity, session.stage.allowsZero, random),
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
