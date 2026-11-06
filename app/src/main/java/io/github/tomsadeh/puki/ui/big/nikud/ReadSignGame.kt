package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.big.letters.VoiceCue
import kotlin.random.Random

data class ReadSignState(
    val phase: NikudPhase = NikudPhase.LOADING,
    /** 1: the distractors start with another sound. 2: they share the first consonant (גַּל / גַּן / גַּג). */
    val level: Int = 1,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    /** The pointed word on the sign. Silent until the right picture is chosen. */
    val signText: String = "",
    /** False (plain wood) until the round is solved: only then is the word said as "heard". */
    val signHeard: Boolean = false,
    val pictures: List<PictureTile> = emptyList(),
    val cue: VoiceCue = VoiceCue(),
    val feedback: PictureFeedback? = null,
    val solved: Boolean = false,
    val misses: Int = 0,
    /** The word was played before he answered (recorded for the parent; the screen never shows it). */
    val assisted: Boolean = false,
    val assistedRounds: Int = 0,
    val starsEarned: Int = 0,
    /** Once solved: the picture to dock onto the sign and keep in the shell. */
    val dockedPictureId: String? = null,
    val sessionComplete: Boolean = false
)

/**
 * Read the sign: one pointed word, silent, and 3 pictures (clear tier only). Nothing is said until a
 * choice, except the instruction in the first round: replay before a choice plays the word and marks
 * the round assisted (a slip for the stars, and the word comes back sooner like a miss). After any
 * choice the chosen picture's word is said; the right one solves the round and the sign turns "heard".
 *
 * Level 2 (same first consonant where a word has a rival) replaces level 1 once [LEVEL2_SOLID] of the 9 clear words
 * are solid (asked in two sessions or more, not shaky now). All 9 words stay in the pool.
 */
class ReadSignGame(
    private val scheduler: RoundScheduler,
    random: Random,
    private val events: RoundEvents,
    val rounds: Int = ROUNDS
) {
    companion object {
        const val ROUNDS = 8
        const val LEVEL2_SOLID = 6
    }

    var state = ReadSignState()
        private set

    private val chooser = PictureChooser(random, scheduler)
    private var tokens = 0
    private var level = 1
    private var word: Nikud.Word? = null
    private var slipped = false

    private fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    fun start() {
        val ids = Nikud.clearWords.map { it.id }
        level = if (ids.count { scheduler.memory.record(it).let { r -> r.seen >= 2 && r.misses == 0 } } >= LEVEL2_SOLID) 2 else 1
        val pool = Nikud.clearWords  // (a word with no same-start rival simply gets different-start distractors at level 2)
        scheduler.begin(rounds, pool.map { it.id })
        state = ReadSignState(level = level)
        show(first = true)
    }

    private fun show(first: Boolean) {
        val w = Nikud.word(scheduler.current ?: return)
        word = w
        slipped = false
        val layout = chooser.choose(w, if (level == 2) DistractorLevel.SAME_START else DistractorLevel.DIFFERENT_START)
        state = state.copy(
            phase = NikudPhase.ROUND,
            roundIndex = scheduler.roundIndex,
            totalRounds = rounds,
            signText = w.text,
            signHeard = false,
            pictures = layout.options.map { PictureTile(it.id, it.pictureId!!) },
            cue = cue(if (first) listOf(Nikud.Voice.READ_SIGN) else emptyList()),
            feedback = null, solved = false, misses = 0, assisted = false, dockedPictureId = null
        )
    }

    private fun slip() {
        if (slipped) return
        slipped = true
        events.onSlip()
        scheduler.miss()
    }

    /** The replay button: before a choice it plays the word (and marks the round assisted); after, it just plays it. */
    fun replay() {
        val w = word ?: return
        if (state.phase != NikudPhase.ROUND) return
        if (!state.solved && !state.assisted) {
            slip()
            state = state.copy(assisted = true, assistedRounds = state.assistedRounds + 1)
        }
        state = state.copy(cue = cue(listOf(w.wordKey)))
    }

    /** The child tapped the picture of word [wordId]. */
    fun onAnswer(wordId: String) {
        val w = word ?: return
        if (state.phase != NikudPhase.ROUND || state.solved || state.pictures.none { it.id == wordId }) return
        val chosen = Nikud.word(wordId)
        if (wordId == w.id) {
            events.onStar()
            state = state.copy(
                solved = true, signHeard = true, starsEarned = state.starsEarned + 1, dockedPictureId = w.pictureId,
                pictures = state.pictures.map { if (it.id == wordId) it.copy(mark = TileMark.CORRECT) else it },
                cue = cue(listOf(chosen.wordKey))
            )
            return
        }
        val already = state.pictures.first { it.id == wordId }.mark == TileMark.WRONG
        val misses = if (already) state.misses else state.misses + 1
        if (!already) slip()
        state = state.copy(
            pictures = state.pictures.map {
                when {
                    it.id == wordId -> it.copy(mark = TileMark.WRONG)
                    misses >= 2 && it.id == w.id -> it.copy(mark = TileMark.HINT)
                    else -> it
                }
            },
            misses = misses,
            feedback = PictureFeedback(wordId, chosen.text),
            cue = cue(listOf(chosen.wordKey))  // only what the tapped picture says: the sign stays silent
        )
    }

    fun next() {
        if (state.phase != NikudPhase.ROUND || !state.solved) return
        if (scheduler.advance() == null) {
            state = state.copy(phase = NikudPhase.COMPLETE, sessionComplete = true, pictures = emptyList(), feedback = null,
                cue = VoiceCue(emptyList(), ++tokens))
            events.onFinish(rounds, scheduler.finish())
        } else {
            show(first = false)
        }
    }
}
