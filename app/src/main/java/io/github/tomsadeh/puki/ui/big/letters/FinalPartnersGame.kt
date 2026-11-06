package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.learning.RoundScheduler

/**
 * Final-form partners: the state shows one letter ([LetterGameState.shown]; an ordinary letter or its
 * final form) and 3 candidates for its partner (מ ↔ ם, נ ↔ ן, כ ↔ ך, פ ↔ ף, צ ↔ ץ). Puki names the
 * shown letter and asks for its friend; the prompt never names the partner. When it is found, Puki
 * names it ("זאת האות מם סופית"), which is how he learns the final forms' names.
 */
class FinalPartnersGame(
    private val scheduler: RoundScheduler,
    events: RoundEvents,
    rounds: Int = ROUNDS
) : LetterGame(events, rounds) {

    companion object {
        const val ROUNDS = 8
        const val OPTIONS = 3
    }

    /** The ten letters that can be shown: five ordinary letters with a final form, and the five final forms. */
    private val items = BigLetters.finals.flatMap { listOf(it.baseId!!, it.id) }
    private val allLetters = BigLetters.all.map { it.id }

    fun start() {
        scheduler.begin(rounds, items)
        show(first = true)
    }

    override fun onMissed() {
        scheduler.miss()
    }

    override fun next() {
        if (state.phase != LetterPhase.ROUND || !state.solved) return
        if (scheduler.advance() == null) complete(scheduler.finish()) else show(first = false)
    }

    private fun show(first: Boolean) {
        val item = scheduler.current ?: return
        val partner = BigLetters.partnerOf(item) ?: return
        val shown = BigLetters[item]
        val answer = BigLetters[partner]
        val choices = scheduler.choices(
            answer = partner, pool = allLetters, count = OPTIONS,
            distanceTo = { minOf(BigLetters.visualDistance(partner, it), BigLetters.visualDistance(item, it)) },
            minDistance = 1, key = item
        )
        showRound(
            RoundSpec(
                shownId = item,
                options = choices.options,
                correctId = partner,
                promptKeys = listOf(shown.voiceKey, LetterVoice.PARTNER_ASK),
                wrongKeys = { tapped -> listOf(BigLetters[tapped].voiceKey, LetterVoice.PARTNER_NOT_FRIEND) },
                solvedKeys = listOf(answer.voiceKey)
            ),
            roundIndex = scheduler.roundIndex,
            intro = if (first) listOf(LetterVoice.PARTNER_INTRO) else emptyList()
        )
    }
}
