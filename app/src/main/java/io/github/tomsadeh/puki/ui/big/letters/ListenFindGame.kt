package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.learning.RoundScheduler

/**
 * Listen and find: Puki says a letter's name ("זאת האות בית. תמצא אותה!"), the child taps it among
 * 3 or 4 tiles. The target is never shown or written in the prompt. A wrong tap says what he tapped
 * and that we are looking for another, the question goes on; after two misses the right tile is hinted.
 *
 * Which letters: all 22. The final forms join once [FINALS_UNLOCK_KNOWN] ordinary letters are known
 * (asked, and not shaky), only those whose ordinary letter is known, at most [MAX_FINALS] a session.
 * Tiles: 3 at first, 4 once [FOUR_TILES_KNOWN] letters are known. Look-alikes (and a letter's own
 * final form, which sounds the same) are never put beside it here: the detective game does that.
 */
class ListenFindGame(
    private val scheduler: RoundScheduler,
    events: RoundEvents,
    rounds: Int = ROUNDS
) : LetterGame(events, rounds) {

    companion object {
        const val ROUNDS = 8
        const val FINALS_UNLOCK_KNOWN = 14
        const val FOUR_TILES_KNOWN = 10
        const val MAX_FINALS = 2
        const val FINALS_GROUP = "final"
    }

    private val baseIds = BigLetters.base.map { it.id }
    private var pool = baseIds
    private var tileCount = 3

    fun start() {
        val known = scheduler.knownCount(baseIds)
        tileCount = if (known >= FOUR_TILES_KNOWN) 4 else 3
        val finals = if (known >= FINALS_UNLOCK_KNOWN) BigLetters.finals.filter { scheduler.isKnown(it.baseId!!) }.map { it.id } else emptyList()
        pool = baseIds + finals
        scheduler.begin(
            rounds, pool,
            groupOf = { if (BigLetters[it].isFinal) FINALS_GROUP else "" },
            groupCaps = mapOf(FINALS_GROUP to MAX_FINALS)
        )
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
        val target = scheduler.current ?: return
        val letter = BigLetters[target]
        val choices = scheduler.choices(
            answer = target, pool = pool, count = tileCount,
            distanceTo = { BigLetters.visualDistance(target, it) }, minDistance = 1
        )
        showRound(
            RoundSpec(
                shownId = null,
                options = choices.options,
                correctId = target,
                promptKeys = listOf(letter.voiceKey, LetterVoice.FIND_IT),
                wrongKeys = { tapped -> listOf(BigLetters[tapped].voiceKey, LetterVoice.LOOKING_FOR, letter.voiceKey) }
            ),
            roundIndex = scheduler.roundIndex,
            intro = if (first) listOf(LetterVoice.LISTEN_INTRO) else emptyList()
        )
    }
}
