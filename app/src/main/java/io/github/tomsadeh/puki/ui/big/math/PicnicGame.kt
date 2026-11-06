package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.data.content.big.MathLine
import io.github.tomsadeh.puki.data.content.big.MathObject
import kotlin.random.Random

enum class PicnicRequest { EXACT, ONE_MORE, ONE_LESS }

data class PicnicRound(val request: PicnicRequest, val shownAmount: Int) {
    val objectKind: MathObject get() = MathObject.APPLE
    val target: Int get() = shownAmount + when (request) {
        PicnicRequest.EXACT -> 0
        PicnicRequest.ONE_MORE -> 1
        PicnicRequest.ONE_LESS -> -1
    }
    init { require(shownAmount in 0..10 && target in 0..10) }
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

class PicnicGame(random: Random, progress: MathProgressStore) :
    MathGame<PicnicState>(MathGameId.PICNIC, random, progress) {
    override var state = PicnicState()
        private set

    override fun startRound() {
        val stage = session.stage
        val index = session.roundIndex
        val maximum = if (stage == MathStage.MIXED_10 && index < 2) 5 else stage.maximum
        val minimum = if (stage.allowsZero) 0 else 1
        val request = when {
            index < 3 -> PicnicRequest.EXACT
            stage == MathStage.JOIN_5 -> PicnicRequest.ONE_MORE
            stage == MathStage.TAKE_5 -> PicnicRequest.ONE_LESS
            index % 2 == 1 -> PicnicRequest.ONE_MORE
            else -> PicnicRequest.ONE_LESS
        }
        val candidates = (0..maximum).mapNotNull { shown ->
            val target = shown + when (request) {
                PicnicRequest.EXACT -> 0
                PicnicRequest.ONE_MORE -> 1
                PicnicRequest.ONE_LESS -> -1
            }
            if (shown in minimum..maximum && target in minimum..maximum &&
                (stage != MathStage.MIXED_10 || index < 2 || maxOf(shown, target) > 5))
                PicnicRound(request, shown) else null
        }
        // Explicit zero target first, then zero as the visible reference in a one-more task.
        val round = when {
            stage == MathStage.ZERO_5 && index == 2 -> PicnicRound(PicnicRequest.EXACT, 0)
            stage == MathStage.ZERO_5 && index == 3 -> PicnicRound(PicnicRequest.ONE_MORE, 0)
            stage == MathStage.ZERO_5 && index == 4 -> PicnicRound(PicnicRequest.ONE_LESS, 1)
            else -> candidates.random(random)
        }
        val prompt = when (round.request) {
            PicnicRequest.EXACT -> listOf(MathContent.picnicTargets[round.target])
            PicnicRequest.ONE_MORE -> listOf(MathContent.picnicShown[round.shownAmount], MathContent.oneMore)
            PicnicRequest.ONE_LESS -> listOf(MathContent.picnicShown[round.shownAmount], MathContent.oneLess)
        }
        state = PicnicState(round = round, basketLimit = maximum + 2, prompt = prompt,
            session = session, voice = cue(prompt))
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
}
