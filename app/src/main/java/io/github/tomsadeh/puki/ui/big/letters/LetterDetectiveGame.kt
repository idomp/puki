package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.data.content.big.LetterPair
import io.github.tomsadeh.puki.learning.RoundScheduler
import kotlin.random.Random

/**
 * Letter detective: one look-alike pair at a time. First a compare step (both letters, their names
 * and the line that tells them apart), then [roundsPerPair] questions: Puki names one of the two and
 * the child finds it among 3 tiles (the pair and one clearly different letter).
 * A session is [pairsPerSession] pairs, [ROUNDS] questions in all.
 *
 * [pairScheduler] picks the pairs (over pair ids: new and shaky pairs first, and remembers them);
 * each pair gets its own short scheduler for which of the two to ask, sharing the answer-position bag.
 */
class LetterDetectiveGame(
    private val pairScheduler: RoundScheduler,
    private val random: Random,
    events: RoundEvents,
    rounds: Int = ROUNDS,
    private val pairsPerSession: Int = PAIRS_PER_SESSION
) : LetterGame(events, rounds) {

    companion object {
        const val ROUNDS = 8
        const val PAIRS_PER_SESSION = 2
        const val OPTIONS = 3
    }

    private val roundsPerPair = rounds / pairsPerSession
    private val otherLetters = BigLetters.base.map { it.id }
    private var pair: LetterPair? = null
    private var inner: RoundScheduler = newInner()
    private var firstPair = true

    private fun newInner() = RoundScheduler(random = random, slots = pairScheduler.slots)

    fun start() {
        pairScheduler.begin(pairsPerSession, BigLetters.pairs.map { it.id })
        firstPair = true
        showPair()
    }

    private fun showPair() {
        val p = BigLetters.pairs.first { it.id == pairScheduler.current }
        pair = p
        val a = BigLetters[p.a]
        val b = BigLetters[p.b]
        showCompare(
            ComparePair(tile(p.a), tile(p.b), p.compareLine),
            roundIndex = pairScheduler.roundIndex * roundsPerPair,
            keys = listOf(a.voiceKey, b.voiceKey, p.compareKey),
            intro = if (firstPair) listOf(LetterVoice.DETECTIVE_INTRO) else emptyList()
        )
        firstPair = false
    }

    override fun onMissed() {
        inner.miss()
        pairScheduler.miss()
    }

    override fun next() {
        when (state.phase) {
            LetterPhase.COMPARE -> {
                val p = pair ?: return
                inner = newInner()
                inner.begin(roundsPerPair, listOf(p.a, p.b))
                showQuestion(p)
            }
            LetterPhase.ROUND -> {
                if (!state.solved) return
                val p = pair ?: return
                if (inner.advance() != null) {
                    showQuestion(p)
                } else if (pairScheduler.advance() != null) {
                    showPair()
                } else {
                    complete(pairScheduler.finish())
                }
            }
            else -> Unit
        }
    }

    private fun showQuestion(p: LetterPair) {
        val target = inner.current ?: return
        val partner = if (target == p.a) p.b else p.a
        val letter = BigLetters[target]
        val choices = inner.choices(
            answer = target, pool = otherLetters, count = OPTIONS,
            distanceTo = { minOf(BigLetters.visualDistance(target, it), BigLetters.visualDistance(partner, it)) },
            minDistance = 1, forced = listOf(partner)
        )
        showRound(
            RoundSpec(
                shownId = null,
                options = choices.options,
                correctId = target,
                promptKeys = listOf(letter.voiceKey, LetterVoice.FIND_IT),
                wrongKeys = { tapped ->
                    val base = listOf(BigLetters[tapped].voiceKey, LetterVoice.LOOKING_FOR, letter.voiceKey)
                    if (tapped == partner) base + p.compareKey else base  // mixed up the pair: say the difference again
                }
            ),
            roundIndex = pairScheduler.roundIndex * roundsPerPair + inner.roundIndex
        )
    }
}
