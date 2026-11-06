package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

enum class StoryPhase { SHOW_START, CHANGE, QUESTION, ANSWERED }

data class FriendsRound(val startCount: Int, val change: Int, val direction: MathOperation) {
    val objectKind: MathObject get() = MathObject.BIRD
    val result: Int get() = if (direction == MathOperation.PLUS) startCount + change else startCount - change
    val equation: Equation get() = Equation(startCount, direction, change, result)
    init {
        require(startCount in 0..10 && change in 0..10)
        require(result in 0..10)
    }
}

data class FriendsState(
    val round: FriendsRound? = null,
    val phase: StoryPhase = StoryPhase.SHOW_START,
    val phaseVoices: Map<StoryPhase, List<MathLine>> = emptyMap(),
    val choices: List<Int> = emptyList(),
    val equation: Equation? = null,
    val feedback: MathFeedback? = null,
    override val session: MathSessionState = MathSessionState(),
    override val voice: MathVoiceCue = MathVoiceCue()
) : MathState {
    override val roundComplete: Boolean get() = phase == StoryPhase.ANSWERED
}

class FriendsGame(random: Random, progress: MathProgressStore) :
    MathGame<FriendsState>(MathGameId.FRIENDS, random, progress) {
    override var state = FriendsState()
        private set

    override fun startRound() {
        val round = selectRound()
        val question = if (round.direction == MathOperation.PLUS) MathContent.howMany else MathContent.howManyLeft
        val voices = mapOf(
            StoryPhase.SHOW_START to listOf(MathContent.birdStarts[round.startCount]),
            StoryPhase.CHANGE to listOf(if (round.direction == MathOperation.PLUS)
                MathContent.birdArrivals[round.change] else MathContent.birdDepartures[round.change]),
            StoryPhase.QUESTION to listOf(question),
            StoryPhase.ANSWERED to listOf(MathContent.exact)
        )
        state = FriendsState(
            round = round, phaseVoices = voices,
            choices = mathChoices(round.result, roundMaximum(), session.stage.allowsZero, random),
            session = session, voice = cue(voices.getValue(StoryPhase.SHOW_START))
        )
    }

    /** Called when the start narration/change animation finishes; no timers in the engine. */
    fun advancePhase() {
        if (!canAnswer()) return
        val phase = when (state.phase) {
            StoryPhase.SHOW_START -> StoryPhase.CHANGE
            StoryPhase.CHANGE -> StoryPhase.QUESTION
            else -> return
        }
        state = state.copy(phase = phase, voice = cue(state.phaseVoices.getValue(phase)))
    }

    fun onAnswer(number: Int) {
        if (!canAnswer() || state.phase != StoryPhase.QUESTION || number !in state.choices) return
        val round = requireNotNull(state.round)
        if (number == round.result) {
            win()
            state = state.copy(phase = StoryPhase.ANSWERED, equation = round.equation, feedback = null,
                session = session, voice = cue(MathContent.exact))
        } else {
            miss()
            val joining = round.direction == MathOperation.PLUS
            val hint = if (joining) MathContent.joinHint else MathContent.leaveHint
            state = state.copy(
                feedback = MathFeedback(number, if (joining) MathFeedbackKind.COUNT_TOGETHER else MathFeedbackKind.COUNT_REMAINING, hint),
                session = session, voice = cue(listOf(hint) + state.phaseVoices.getValue(StoryPhase.QUESTION))
            )
        }
    }

    override fun replay() {
        if (!session.isLoading && !session.sessionComplete) state = state.copy(voice = cue(state.voice.lines))
    }
    override fun refreshSession() { state = state.copy(session = session, voice = cue(emptyList())) }

    private fun roundMaximum(): Int = when {
        session.stage == MathStage.MIXED_10 && session.roundIndex < 2 -> 5
        session.stage == MathStage.JOIN_5 && session.roundIndex < 2 -> 3
        else -> session.stage.maximum
    }

    private fun selectRound(): FriendsRound {
        val stage = session.stage
        val index = session.roundIndex
        val maximum = roundMaximum()
        // Zero gets deliberate examples: empty start, no change, take everything, empty + empty.
        if (stage == MathStage.ZERO_5 && index >= 2) {
            val n = random.nextInt(1, maximum + 1)
            return when (index) {
                2 -> FriendsRound(0, n, MathOperation.PLUS)
                3 -> FriendsRound(n, 0, MathOperation.PLUS)
                4 -> FriendsRound(n, n, MathOperation.MINUS)
                else -> FriendsRound(0, 0, MathOperation.PLUS)
            }
        }
        val operation = when (stage) {
            MathStage.JOIN_5 -> MathOperation.PLUS
            MathStage.TAKE_5 -> MathOperation.MINUS
            else -> when (index) {
                0, 1 -> MathOperation.PLUS
                2, 3 -> MathOperation.MINUS
                else -> if (random.nextBoolean()) MathOperation.PLUS else MathOperation.MINUS
            }
        }
        val minimum = if (stage.allowsZero) 0 else 1
        val candidates = (minimum..maximum).flatMap { a ->
            (minimum..maximum).mapNotNull { b ->
                val result = if (operation == MathOperation.PLUS) a + b else a - b
                if (result in minimum..maximum &&
                    (stage != MathStage.MIXED_10 || index < 2 || maxOf(a, b, result) > 5))
                    FriendsRound(a, b, operation) else null
            }
        }
        return candidates.random(random)
    }
}
