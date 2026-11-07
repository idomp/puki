package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.math.abs
import kotlin.random.Random

enum class PicnicRequest(val isPart: Boolean = false) {
    EXACT, ONE_MORE, ONE_LESS, TOP_UP(true), TAKE_OUT(true), ALREADY_RIGHT(true), EMPTY_IT(true)
}

/** shownAmount is the mat reference for familiar requests, the final total for part-whole requests. */
data class PicnicRound(val request: PicnicRequest, val shownAmount: Int, val startCount: Int = 0) {
    val objectKind: MathObject get() = MathObject.APPLE
    val target: Int get() = shownAmount + when (request) {
        PicnicRequest.ONE_MORE -> 1
        PicnicRequest.ONE_LESS -> -1
        else -> 0
    }
    init {
        require(shownAmount in 0..10 && target in 0..10 && startCount in 0..10)
        require(when (request) {
            PicnicRequest.TOP_UP -> startCount < target
            PicnicRequest.TAKE_OUT -> startCount > target && target > 0
            PicnicRequest.ALREADY_RIGHT -> startCount == target
            PicnicRequest.EMPTY_IT -> startCount > 0 && target == 0
            else -> startCount == 0
        })
    }
}

data class PicnicState(
    val round: PicnicRound? = null,
    val basketCount: Int = 0,
    /** Spare items allow an overfull basket even when the target is the stage maximum. */
    val basketLimit: Int = 7,
    val prompt: List<MathLine> = emptyList(),
    val feedback: MathFeedback? = null,
    override val roundComplete: Boolean = false,
    override val session: MathSessionState = MathSessionState(),
    override val voice: MathVoiceCue = MathVoiceCue()
) : MathState

class PicnicGame(
    random: Random, progress: MathProgressStore, history: MathHistoryStore = InMemoryMathHistoryStore(),
    floor: suspend () -> MathStage = { MathStage.JOIN_5 }
) :
    MathGame<PicnicState>(MathGameId.PICNIC, random, progress, history, floor) {
    override var state = PicnicState()
        private set
    /** Later requests are shuffled once and kept for the rest of the session. */
    private var laterRequests = emptyList<PicnicRequest>()
    private var introducedBasket = false

    override suspend fun startRound() {
        val stage = session.stage
        val index = session.roundIndex
        val maximum = picnicMaximum(stage, index)
        val request = selectRequest(stage, index)
        val round = draw(picnicPool(stage, index, request)) { picnicKey(it) }
        val prompt = when (round.request) {
            PicnicRequest.EXACT -> listOf(MathContent.picnicTargets[round.target], MathContent.pressReady)
            PicnicRequest.ONE_MORE -> listOf(MathContent.picnicShown[round.shownAmount], MathContent.oneMore, MathContent.pressReady)
            PicnicRequest.ONE_LESS -> listOf(MathContent.picnicShown[round.shownAmount], MathContent.oneLess, MathContent.pressReady)
            else -> listOf(MathContent.picnicTotals[round.target], MathContent.pressReady)
        }
        val introduction = if (round.startCount > 0 && !introducedBasket) {
            introducedBasket = true
            listOf(MathContent.picnicAlready)
        } else emptyList()
        state = PicnicState(round = round, basketCount = round.startCount, basketLimit = maximum + 2, prompt = prompt,
            session = session, voice = cue(introduction + prompt))
    }

    fun addItem() {
        if (canAnswer() && state.basketCount < state.basketLimit)
            state = state.copy(basketCount = state.basketCount + 1, feedback = null)
    }

    fun removeItem() {
        if (canAnswer() && state.basketCount > 0)
            state = state.copy(basketCount = state.basketCount - 1, feedback = null)
    }

    /** Edits are exploration; only pressing ready counts as an attempt. */
    fun ready() {
        if (!canAnswer()) return
        val difference = requireNotNull(state.round).target - state.basketCount
        if (difference == 0) {
            win()
            state = state.copy(roundComplete = true, feedback = null, session = session, voice = cue(MathContent.exact))
        } else {
            miss()
            val hint = if (difference > 0) MathContent.tooFew else MathContent.tooMany
            state = state.copy(
                feedback = MathFeedback(state.basketCount,
                    if (difference > 0) MathFeedbackKind.TOO_FEW else MathFeedbackKind.TOO_MANY, hint, difference),
                session = session, voice = cue(hint)
            )
        }
    }

    override fun replay() {
        if (!session.isLoading && !session.sessionComplete) state = state.copy(voice = cue(state.prompt))
    }
    override fun refreshSession() { state = state.copy(session = session, voice = cue(emptyList())) }

    private fun selectRequest(stage: MathStage, index: Int): PicnicRequest {
        val requests = picnicRequests(stage, index)
        requests.singleOrNull()?.let { return it }
        if (stage == MathStage.PART_10 && index == 1) return requests.random(random)
        if (laterRequests.isEmpty()) laterRequests = when (stage) {
            MathStage.PART_5 -> listOf(PicnicRequest.TAKE_OUT, PicnicRequest.ALREADY_RIGHT,
                PicnicRequest.EMPTY_IT, PicnicRequest.TOP_UP).shuffled(random).take(3)
            MathStage.PART_10 -> (listOf(PicnicRequest.TOP_UP, PicnicRequest.TAKE_OUT) +
                listOf(PicnicRequest.TOP_UP, PicnicRequest.TAKE_OUT, PicnicRequest.ALREADY_RIGHT)
                    .shuffled(random).take(2)).shuffled(random)
            else -> listOf(PicnicRequest.ONE_MORE, PicnicRequest.ONE_LESS,
                if (random.nextBoolean()) PicnicRequest.ONE_MORE else PicnicRequest.ONE_LESS).shuffled(random)
        }
        return laterRequests[index - if (stage == MathStage.PART_10) 2 else 3]
    }
}

private fun picnicKey(round: PicnicRound): String {
    if (round.request.isPart) return "part:${round.startCount}:${round.target}"
    val symbol = when (round.request) {
        PicnicRequest.EXACT -> '='
        PicnicRequest.ONE_MORE -> '+'
        PicnicRequest.ONE_LESS -> '-'
        else -> error("Expected a familiar request")
    }
    return "$symbol${round.shownAmount}"
}

private fun picnicMaximum(stage: MathStage, index: Int) =
    if (stage in listOf(MathStage.MIXED_10, MathStage.PART_10) && index < 2) 5 else stage.maximum

/** Familiar stages open with exact quantities; part-whole stages open with small top-ups. */
internal fun picnicRequests(stage: MathStage, index: Int): Set<PicnicRequest> = when {
    stage == MathStage.PART_5 -> if (index < 3) setOf(PicnicRequest.TOP_UP)
        else setOf(PicnicRequest.TOP_UP, PicnicRequest.TAKE_OUT, PicnicRequest.ALREADY_RIGHT, PicnicRequest.EMPTY_IT)
    // One familiar warmup keeps the request mix; all four later rounds practise small changes above five.
    stage == MathStage.PART_10 -> when (index) {
        0 -> setOf(PicnicRequest.TOP_UP)
        1 -> setOf(PicnicRequest.EXACT, PicnicRequest.ONE_MORE, PicnicRequest.ONE_LESS)
        else -> setOf(PicnicRequest.TOP_UP, PicnicRequest.TAKE_OUT, PicnicRequest.ALREADY_RIGHT)
    }
    index < 3 -> setOf(PicnicRequest.EXACT)
    stage == MathStage.JOIN_5 -> setOf(PicnicRequest.ONE_MORE)
    stage == MathStage.TAKE_5 -> setOf(PicnicRequest.ONE_LESS)
    stage == MathStage.ZERO_5 -> setOf(if (index % 2 == 1) PicnicRequest.ONE_MORE else PicnicRequest.ONE_LESS)
    else -> setOf(PicnicRequest.ONE_MORE, PicnicRequest.ONE_LESS)
}

/** Every basket this round may ask for. ZERO_5 fixes the zero examples; the opening rounds leave zero to them. */
internal fun picnicPool(stage: MathStage, index: Int, request: PicnicRequest): List<PicnicRound> {
    if (request !in picnicRequests(stage, index)) return emptyList()
    val maximum = picnicMaximum(stage, index)
    if (request.isPart) return (0..maximum).flatMap { start ->
        (0..maximum).mapNotNull { target ->
            val matches = when (request) {
                PicnicRequest.TOP_UP -> start < target
                PicnicRequest.TAKE_OUT -> start > target && target > 0
                PicnicRequest.ALREADY_RIGHT -> start == target
                PicnicRequest.EMPTY_IT -> start > 0 && target == 0
                else -> false
            }
            if (matches && (stage != MathStage.PART_10 || index < 2 ||
                    (maxOf(start, target) > 5 && abs(start - target) <= 3)))
                PicnicRound(request, target, start) else null
        }
    }
    // Explicit zero target first, then zero as the visible reference in a one-more task.
    if (stage == MathStage.ZERO_5) when (index) {
        2 -> return listOf(PicnicRound(PicnicRequest.EXACT, 0))
        3 -> return listOf(PicnicRound(PicnicRequest.ONE_MORE, 0))
        4 -> return listOf(PicnicRound(PicnicRequest.ONE_LESS, 1))
    }
    val minimum = if (stage.allowsZero) 0 else 1
    val change = when (request) {
        PicnicRequest.ONE_MORE -> 1
        PicnicRequest.ONE_LESS -> -1
        else -> 0
    }
    return (minimum..maximum).mapNotNull { shown ->
        val target = shown + change
        if (target in minimum..maximum &&
            (stage != MathStage.MIXED_10 || index < 2 || maxOf(shown, target) > 5) &&
            !(stage == MathStage.ZERO_5 && index < 2 && shown == 0))
            PicnicRound(request, shown) else null
    }
}

/** Everything a stage can show over its six rounds. */
internal fun picnicCatalog(stage: MathStage): Set<PicnicRound> =
    (0 until 6).flatMap { index -> picnicRequests(stage, index).flatMap { picnicPool(stage, index, it) } }.toSet()
