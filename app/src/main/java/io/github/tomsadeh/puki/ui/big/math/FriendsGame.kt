package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

enum class StoryPhase { SHOW_START, CHANGE, QUESTION, ANSWERED }

enum class FriendsUnknown { TOTAL, CHANGE, START }

data class FriendsRound(
    val startCount: Int, val change: Int, val direction: MathOperation,
    val unknown: FriendsUnknown = FriendsUnknown.TOTAL
) {
    val objectKind: MathObject get() = MathObject.BIRD
    val result: Int get() = if (direction == MathOperation.PLUS) startCount + change else startCount - change
    val equation: Equation get() = Equation(startCount, direction, change, result)
    val isHidden: Boolean get() = unknown != FriendsUnknown.TOTAL
    val answer: Int get() = when (unknown) {
        FriendsUnknown.TOTAL -> result
        FriendsUnknown.CHANGE -> change
        FriendsUnknown.START -> startCount
    }
    val visibleCount: Int get() = if (unknown == FriendsUnknown.START) change else startCount
    val questionEquation: Equation? get() = when (unknown) {
        FriendsUnknown.TOTAL -> null
        FriendsUnknown.CHANGE -> Equation(startCount, direction, null, result)
        FriendsUnknown.START -> Equation(null, direction, change, result)
    }
    val historyKey: String get() = when (unknown) {
        FriendsUnknown.TOTAL -> "$startCount${if (direction == MathOperation.PLUS) '+' else '-'}$change"
        else -> "hidden:${unknown.name}:$startCount:$change"
    }
    init {
        require(startCount in 0..10 && change in 0..10)
        require(result in 0..10)
        require(!isHidden || (direction == MathOperation.PLUS && startCount > 0 && change > 0))
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
    val showHiddenOutlines: Boolean get() = round?.isHidden == true && session.showHint && !roundComplete
    val storyLines: List<MathLine> get() = listOf(StoryPhase.SHOW_START, StoryPhase.CHANGE, StoryPhase.QUESTION)
        .flatMap { phaseVoices[it].orEmpty() }
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
    private var laterUnknowns = emptyList<FriendsUnknown>()

    override suspend fun startRound() {
        val round = selectRound()
        // Warmup stories use at most three birds, but still offer the mockup's four boards.
        val choiceMaximum = if (session.stage == MathStage.JOIN_5) session.stage.maximum else roundMaximum()
        val question = if (round.direction == MathOperation.PLUS) MathContent.howMany else MathContent.howManyLeft
        val voices = if (round.isHidden) mapOf(
            StoryPhase.SHOW_START to listOf(if (round.unknown == FriendsUnknown.START)
                MathContent.hideStart else MathContent.birdStarts[round.startCount]),
            StoryPhase.CHANGE to listOf(if (round.unknown == FriendsUnknown.START)
                MathContent.birdArrivals[round.change] else MathContent.hideArrive),
            StoryPhase.QUESTION to listOf(MathContent.birdsNow.getValue(round.result),
                if (round.unknown == FriendsUnknown.START) MathContent.hideAskStart else MathContent.hideAsk),
            StoryPhase.ANSWERED to listOf(MathContent.exact)
        ) else mapOf(
            StoryPhase.SHOW_START to listOf(MathContent.birdStarts[round.startCount]),
            StoryPhase.CHANGE to listOf(if (round.direction == MathOperation.PLUS)
                MathContent.birdArrivals[round.change] else MathContent.birdDepartures[round.change]),
            StoryPhase.QUESTION to listOf(question),
            StoryPhase.ANSWERED to listOf(MathContent.exact)
        )
        state = FriendsState(
            round = round, phaseVoices = voices,
            choices = mathChoices(round.answer, choiceMaximum, session.stage.allowsZero, random, answerSlot()),
            equation = round.questionEquation,
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
        if (number == round.answer) {
            win()
            state = state.copy(phase = StoryPhase.ANSWERED, equation = round.equation, feedback = null,
                session = session, voice = cue(MathContent.exact))
        } else {
            miss()
            val joining = round.direction == MathOperation.PLUS
            val hint = if (round.isHidden) MathContent.hideHint else if (joining) MathContent.joinHint else MathContent.leaveHint
            state = state.copy(
                feedback = MathFeedback(number, if (joining) MathFeedbackKind.COUNT_TOGETHER else MathFeedbackKind.COUNT_REMAINING, hint),
                session = session, voice = cue(listOf(hint) + if (round.isHidden)
                    listOf(if (round.unknown == FriendsUnknown.START) MathContent.hideAskStart else MathContent.hideAsk)
                    else state.phaseVoices.getValue(StoryPhase.QUESTION))
            )
        }
    }

    override fun replay() {
        if (!session.isLoading && !session.sessionComplete) {
            state = if (state.round?.isHidden == true)
                state.copy(phase = if (state.roundComplete) StoryPhase.ANSWERED else StoryPhase.QUESTION,
                    voice = cue(state.storyLines))
            else state.copy(voice = cue(state.voice.lines))
        }
    }
    override fun refreshSession() { state = state.copy(session = session, voice = cue(emptyList())) }

    private fun roundMaximum() = friendsMaximum(session.stage, session.roundIndex)

    private suspend fun selectRound(): FriendsRound {
        val stage = session.stage
        val index = session.roundIndex
        if (stage >= MathStage.PART_5) {
            val unknown = if (stage == MathStage.PART_5 || index < 2) FriendsUnknown.CHANGE else {
                if (laterUnknowns.isEmpty()) laterUnknowns = listOf(FriendsUnknown.CHANGE, FriendsUnknown.CHANGE,
                    FriendsUnknown.START, FriendsUnknown.START).shuffled(random)
                laterUnknowns[index - 2]
            }
            return draw(hiddenFriendsPool(stage, index, unknown)) { it.historyKey }
        }
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
        return draw(friendsPool(stage, index, operation)) { it.historyKey }
    }
}

/** Warmup stories stay small: at most three birds at the start of JOIN_5, five at the start of MIXED_10. */
internal fun friendsMaximum(stage: MathStage, index: Int): Int = when {
    stage in listOf(MathStage.MIXED_10, MathStage.PART_10) && index < 2 -> 5
    stage == MathStage.JOIN_5 && index < 2 -> 3
    else -> stage.maximum
}

/** Operations a round can ask for; mixed stages open with joins, then shuffle the rest. */
internal fun friendsOperations(stage: MathStage, index: Int): Set<MathOperation> = when {
    stage == MathStage.JOIN_5 -> setOf(MathOperation.PLUS)
    stage == MathStage.TAKE_5 -> setOf(MathOperation.MINUS)
    stage >= MathStage.PART_5 -> setOf(MathOperation.PLUS)
    index < 2 || stage == MathStage.ZERO_5 -> setOf(MathOperation.PLUS)
    else -> MathOperation.entries.toSet()
}

/** Every story this round may show. The zero examples are fixed at ZERO_5; the opening rounds leave zero to them. */
internal fun friendsPool(stage: MathStage, index: Int, operation: MathOperation): List<FriendsRound> {
    if (stage >= MathStage.PART_5) return if (operation == MathOperation.MINUS) emptyList() else
        hiddenFriendsPool(stage, index, FriendsUnknown.CHANGE) +
            if (stage == MathStage.PART_10 && index >= 2) hiddenFriendsPool(stage, index, FriendsUnknown.START) else emptyList()
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

internal fun hiddenFriendsPool(stage: MathStage, index: Int, unknown: FriendsUnknown): List<FriendsRound> {
    require(stage >= MathStage.PART_5 && unknown != FriendsUnknown.TOTAL)
    val small = stage == MathStage.PART_5 || index < 2
    if (unknown == FriendsUnknown.START && small) return emptyList()
    return (1..9).flatMap { start ->
        (1..4).mapNotNull { change ->
            val total = start + change
            val allowed = if (unknown == FriendsUnknown.START) start in 2..7 && total <= 10
                else if (small) total in 3..5 && change in 1..3 else total in 6..10
            if (allowed) FriendsRound(start, change, MathOperation.PLUS, unknown) else null
        }
    }
}

/** Everything a stage can show over its six rounds. */
internal fun friendsCatalog(stage: MathStage): Set<FriendsRound> =
    (0 until 6).flatMap { index -> friendsOperations(stage, index).flatMap { friendsPool(stage, index, it) } }.toSet()
