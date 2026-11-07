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

class FriendsGame(
    random: Random, progress: MathProgressStore, history: MathHistoryStore = InMemoryMathHistoryStore(),
    floor: suspend () -> MathStage = { MathStage.JOIN_5 }
) :
    MathGame<FriendsState>(MathGameId.FRIENDS, random, progress, history, floor) {
    override var state = FriendsState()
        private set
    /** Rounds 3-6 of a mixed stage: two joins and two leaves in a shuffled order. */
    private var laterOperations = emptyList<MathOperation>()

    override suspend fun startRound() {
        val round = selectRound()
        // Warmup stories use at most three birds, but still offer the mockup's four boards.
        val choiceMaximum = if (session.stage == MathStage.JOIN_5) session.stage.maximum else roundMaximum()
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
            choices = mathChoices(round.result, choiceMaximum, session.stage.allowsZero, random, answerSlot()),
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

    private fun roundMaximum() = friendsMaximum(session.stage, session.roundIndex)

    private suspend fun selectRound(): FriendsRound {
        val stage = session.stage.countingStage
        val index = session.roundIndex
        val operation = when {
            stage == MathStage.JOIN_5 -> MathOperation.PLUS
            stage == MathStage.TAKE_5 -> MathOperation.MINUS
            index < 2 || stage == MathStage.ZERO_5 -> MathOperation.PLUS
            else -> {
                if (laterOperations.isEmpty()) laterOperations = listOf(MathOperation.PLUS, MathOperation.PLUS,
                    MathOperation.MINUS, MathOperation.MINUS).shuffled(random)
                laterOperations[index - 2]
            }
        }
        return draw(friendsPool(stage, index, operation)) { friendsKey(it) }
    }
}

private fun friendsKey(round: FriendsRound) =
    "${round.startCount}${if (round.direction == MathOperation.PLUS) '+' else '-'}${round.change}"

/** Warmup stories stay small: at most three birds at the start of JOIN_5, five at the start of MIXED_10. */
internal fun friendsMaximum(stage: MathStage, index: Int): Int = when {
    stage.countingStage == MathStage.MIXED_10 && index < 2 -> 5
    stage == MathStage.JOIN_5 && index < 2 -> 3
    else -> stage.countingStage.maximum
}

/** Operations a round can ask for; mixed stages open with joins, then shuffle the rest. */
internal fun friendsOperations(stage: MathStage, index: Int): Set<MathOperation> = when {
    stage == MathStage.JOIN_5 -> setOf(MathOperation.PLUS)
    stage == MathStage.TAKE_5 -> setOf(MathOperation.MINUS)
    index < 2 || stage == MathStage.ZERO_5 -> setOf(MathOperation.PLUS)
    else -> MathOperation.entries.toSet()
}

/** Every story this round may show. The zero examples are fixed at ZERO_5; the opening rounds leave zero to them. */
internal fun friendsPool(stage: MathStage, index: Int, operation: MathOperation): List<FriendsRound> {
    if (stage != stage.countingStage) return friendsPool(stage.countingStage, index, operation)
    val maximum = friendsMaximum(stage, index)
    // Zero gets deliberate examples: empty start, no change, take everything, empty + empty.
    if (stage == MathStage.ZERO_5 && index >= 2) {
        val counts = 1..maximum
        return when (index) {
            2 -> counts.map { FriendsRound(0, it, MathOperation.PLUS) }
            3 -> counts.map { FriendsRound(it, 0, MathOperation.PLUS) }
            4 -> counts.map { FriendsRound(it, it, MathOperation.MINUS) }
            else -> listOf(FriendsRound(0, 0, MathOperation.PLUS))
        }
    }
    val openingLeavesZeroToLaterRounds = stage == MathStage.ZERO_5
    val minimum = if (stage.allowsZero) 0 else 1
    return (minimum..maximum).flatMap { a ->
        (minimum..maximum).mapNotNull { b ->
            val result = if (operation == MathOperation.PLUS) a + b else a - b
            if (result in minimum..maximum &&
                (stage != MathStage.MIXED_10 || index < 2 || maxOf(a, b, result) > 5) &&
                (!openingLeavesZeroToLaterRounds || (a > 0 && b > 0)))
                FriendsRound(a, b, operation) else null
        }
    }
}

/** Everything a stage can show over its six rounds. */
internal fun friendsCatalog(stage: MathStage): Set<FriendsRound> =
    (0 until 6).flatMap { index -> friendsOperations(stage, index).flatMap { friendsPool(stage, index, it) } }.toSet()
