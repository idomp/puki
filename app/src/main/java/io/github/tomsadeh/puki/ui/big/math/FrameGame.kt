package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

enum class FrameKind { FILL, COUNT, CROSS, MAKE, PART }

data class FrameRound(val capacity: Int, val filled: Int, val target: Int = capacity, val kind: FrameKind = FrameKind.FILL) {
    val objectKind: MathObject get() = MathObject.COUNTER
    val missing: Int get() = target - filled
    val isTeen: Boolean get() = capacity == 20
    val isMakeN: Boolean get() = kind == FrameKind.MAKE || (!isTeen && target < capacity)
    val answer: Int get() = if (kind == FrameKind.COUNT || kind == FrameKind.CROSS) target else missing
    // Preserve old full-frame keys; make-N problems include both the whole and the starting part.
    internal val historyKey: String get() = when {
        isTeen -> "teen:${kind.name}:$target:$filled"
        isMakeN -> "part:$capacity:$target:$filled"
        else -> "$capacity:$filled"
    }
    init {
        require(capacity == 5 || capacity == 10 || capacity == 20)
        require(target in 1..capacity)
        require(filled in 0..target)
        if (isTeen) {
            require(kind != FrameKind.FILL && target in 11..20)
            if (kind == FrameKind.COUNT) require(filled == target)
            else require(filled > 0 && missing in 1..10)
            if (kind == FrameKind.CROSS) require(filled in 6..9 && target <= 18 && missing <= 9)
            if (kind == FrameKind.PART) require(filled in 6..9 && target <= 18)
        } else {
            require(kind == FrameKind.FILL)
            if (isMakeN) require(target in 2..9 && filled in 1 until target)
        }
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

class FrameGame(
    random: Random, progress: MathProgressStore, history: MathHistoryStore = InMemoryMathHistoryStore(),
    floor: suspend () -> MathStage = { MathStage.JOIN_5 }
) :
    MathGame<FrameState>(MathGameId.FRAME, random, progress, history, floor) {
    override var state = FrameState()
        private set

    override suspend fun startRound() {
        val round = draw(framePool(session.stage, session.roundIndex)) { it.historyKey }
        if (round.isTeen) {
            val prompt = when (round.kind) {
                FrameKind.COUNT -> listOf(MathContent.teenHowMany)
                FrameKind.CROSS -> listOf(MathContent.frameFilled[round.filled], MathContent.teenArrive, MathContent.teenHowManyNow)
                FrameKind.MAKE -> listOf(MathContent.teenTarget, MathContent.howManyMissing)
                FrameKind.PART -> listOf(MathContent.frameFilled[round.filled], MathContent.teenPart)
                FrameKind.FILL -> error("A teen round needs a question")
            }
            state = FrameState(round = round, choices = teenChoices(round, random, answerSlot()),
                prompt = prompt, cells = List(20) { it < round.filled },
                equation = if (round.kind == FrameKind.PART) Equation(round.filled, MathOperation.PLUS, null, round.target) else null,
                session = session, voice = cue(prompt))
            return
        }
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
        if (number == round.answer) {
            win()
            state = state.copy(roundComplete = true, feedback = null,
                equation = if (round.kind == FrameKind.COUNT) null else Equation(round.filled, MathOperation.PLUS, round.missing, round.target),
                session = session, voice = cue(if (round.isTeen) listOf(MathContent.exact, MathContent.teenNumbers.getValue(round.target)) else listOf(MathContent.exact)))
        } else {
            miss()
            val hint = if (round.isTeen) MathContent.teenHint else MathContent.frameHint
            val question = if (round.isTeen) state.prompt.takeLast(1) else listOf(MathContent.howManyMissing)
            state = state.copy(feedback = MathFeedback(number, MathFeedbackKind.COUNT_EMPTY_SPACES, hint),
                session = session, voice = cue(listOf(hint) + question))
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
    when (stage) {
        MathStage.TEEN_COUNT -> return (11..if (index < 2) 14 else 20).map { FrameRound(20, it, it, FrameKind.COUNT) }
        MathStage.TEEN_CROSS -> return (6..9).flatMap { a -> (1..if (index < 3) 5 else 9)
            .filter { a + it in 11..18 }.map { FrameRound(20, a, a + it, FrameKind.CROSS) } }
        MathStage.TEEN_MAKE -> return (11..20).flatMap { target ->
            ((if (index < 3) 10 else maxOf(1, target - 10)) until target).map { FrameRound(20, it, target, FrameKind.MAKE) } }
        MathStage.TEEN_PART -> return (6..9).flatMap { a -> (11..18).filter { it - a in 1..10 }
            .map { FrameRound(20, a, it, FrameKind.PART) } }
        else -> Unit
    }
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

/** Keep total choices near the answer; a loose-ones distractor can replace one neighbour. */
internal fun teenChoices(round: FrameRound, random: Random, slot: Int): List<Int> {
    if (round.kind != FrameKind.COUNT && round.kind != FrameKind.CROSS)
        return mathChoices(round.answer, 10, false, random, slot)
    val choices = mathChoices(round.answer - 10, 10, false, random, slot).map { it + 10 }.toMutableList()
    val ones = round.answer % 10
    if (ones > 0 && random.nextBoolean()) {
        val replace = choices.indices.filter { it != slot }.random(random)
        choices[replace] = ones
    }
    return choices
}
