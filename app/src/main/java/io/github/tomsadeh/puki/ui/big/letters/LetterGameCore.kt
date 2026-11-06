package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.learning.SchedulerMemory

/*
 * The shared part of the three letters games: what a screen sees (LetterGameState) and the tap logic
 * (LetterGame). No Android imports: the games are plain classes, wrapped by the ViewModels in
 * BigLetterViewModels.kt. All voice lines are Hebrew: a screen plays [VoiceCue.keys] with Language.HEBREW.
 */

/** Every voice key the letters games ask for, written out in full (scripts/check_voices.py reads them). */
object LetterVoice {
    // Listen and find
    const val LISTEN_INTRO = "voice_big_letters_listen_intro"
    const val FIND_IT = "voice_big_letters_find_it"
    const val LOOKING_FOR = "voice_big_letters_looking_for"
    const val HINT = "voice_big_letters_hint"

    // Letter detective
    const val DETECTIVE_INTRO = "voice_big_letters_detective_intro"

    // Final-form partners
    const val PARTNER_INTRO = "voice_big_letters_partner_intro"
    const val PARTNER_ASK = "voice_big_letters_partner_ask"
    const val PARTNER_NOT_FRIEND = "voice_big_letters_partner_not_friend"
}

/** The lines to play now. [token] changes whenever they should be played (again), even if the keys are the same. */
data class VoiceCue(val keys: List<String> = emptyList(), val token: Int = 0)

enum class TileMark {
    NONE,
    /** Tapped, and not the one. */
    WRONG,
    /** The right one, shown after two misses. */
    HINT,
    /** Found. */
    CORRECT
}

data class LetterTile(val id: String, val glyph: String, val mark: TileMark = TileMark.NONE)

/** After a wrong tap: what he tapped and what we are looking for (the voice cue says it aloud). */
data class LetterFeedback(val tappedId: String, val lookingForId: String)

/** The detective's first step: two letters side by side and the line that tells them apart. */
data class ComparePair(val a: LetterTile, val b: LetterTile, val line: String)

enum class LetterPhase {
    /** Memory not loaded yet. */
    LOADING,
    /** The detective's compare step (call `next()` to go on to the questions). */
    COMPARE,
    /** A question: tiles to tap. */
    ROUND,
    COMPLETE
}

/**
 * Everything a screen needs. The right answer is not in it until it is found (or hinted): the tiles
 * carry only ids and glyphs, and a wrong tap puts the answer into [feedback].
 */
data class LetterGameState(
    val phase: LetterPhase = LetterPhase.LOADING,
    /** 0-based question number out of [totalRounds] (a compare step is not a round). */
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    /** Final-form partners: the letter whose partner is wanted. Null in the other games. */
    val shown: LetterTile? = null,
    /** Detective only, in the [LetterPhase.COMPARE] step. */
    val compare: ComparePair? = null,
    val tiles: List<LetterTile> = emptyList(),
    /** What to play now; a new [VoiceCue.token] means play it. */
    val cue: VoiceCue = VoiceCue(),
    val feedback: LetterFeedback? = null,
    /** The right tile was found; call `next()` when the celebration is over. */
    val solved: Boolean = false,
    /** Wrong taps in this question. */
    val misses: Int = 0,
    val starsEarned: Int = 0,
    val sessionComplete: Boolean = false
)

/** What the game reports to its owner (the ViewModel wires these to GameSession and the memory store). */
class RoundEvents(
    val onStar: () -> Unit = {},
    val onSlip: () -> Unit = {},
    val onFinish: (rounds: Int, memory: SchedulerMemory) -> Unit = { _, _ -> }
)

/** One question: [options] are letter ids, one of them [correctId]. */
class RoundSpec(
    val shownId: String?,
    val options: List<String>,
    val correctId: String,
    /** Played when the question appears and on replay. */
    val promptKeys: List<String>,
    /** Played after a wrong tap on [tapped]. */
    val wrongKeys: (tapped: String) -> List<String>,
    /** Played when the right tile is found. */
    val solvedKeys: List<String> = emptyList()
)

abstract class LetterGame(protected val events: RoundEvents, val rounds: Int) {
    var state: LetterGameState = LetterGameState()
        protected set

    private var spec: RoundSpec? = null
    private var tokens = 0
    private var replayKeys: List<String> = emptyList()

    protected fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    /** The first wrong tap of a question: tell the scheduler. */
    protected abstract fun onMissed()

    /** Leave the compare step, or go on after a found letter. */
    abstract fun next()

    protected fun tile(id: String, mark: TileMark = TileMark.NONE) = LetterTile(id, BigLetters[id].glyph, mark)

    /** Show a question. [intro] plays before the prompt this once; a replay plays the prompt alone. */
    protected fun showRound(spec: RoundSpec, roundIndex: Int, intro: List<String> = emptyList()) {
        this.spec = spec
        replayKeys = spec.promptKeys
        state = state.copy(
            phase = LetterPhase.ROUND,
            roundIndex = roundIndex,
            totalRounds = rounds,
            shown = spec.shownId?.let { tile(it) },
            compare = null,
            tiles = spec.options.map { tile(it) },
            cue = cue(intro + spec.promptKeys),
            feedback = null,
            solved = false,
            misses = 0
        )
    }

    protected fun showCompare(pair: ComparePair, roundIndex: Int, keys: List<String>, intro: List<String> = emptyList()) {
        spec = null
        replayKeys = keys
        state = state.copy(
            phase = LetterPhase.COMPARE,
            roundIndex = roundIndex,
            totalRounds = rounds,
            shown = null,
            compare = pair,
            tiles = emptyList(),
            cue = cue(intro + keys),
            feedback = null,
            solved = false,
            misses = 0
        )
    }

    protected fun complete(memory: SchedulerMemory) {
        spec = null
        state = state.copy(phase = LetterPhase.COMPLETE, sessionComplete = true, tiles = emptyList(), shown = null,
            compare = null, feedback = null, cue = VoiceCue(emptyList(), ++tokens))
        events.onFinish(rounds, memory)
    }

    /** Say the prompt (or the compare step) again. */
    fun replay() {
        if (state.phase == LetterPhase.ROUND || state.phase == LetterPhase.COMPARE) {
            state = state.copy(cue = cue(replayKeys))
        }
    }

    /** The child tapped tile [id]. */
    fun onAnswer(id: String) {
        val s = spec ?: return
        if (state.phase != LetterPhase.ROUND || state.solved || id !in s.options) return
        if (id == s.correctId) {
            events.onStar()
            state = state.copy(
                tiles = state.tiles.map { if (it.id == id) it.copy(mark = TileMark.CORRECT) else it },
                solved = true,
                starsEarned = state.starsEarned + 1,
                cue = cue(s.solvedKeys)
            )
            return
        }
        val already = state.tiles.firstOrNull { it.id == id }?.mark == TileMark.WRONG
        val misses = if (already) state.misses else state.misses + 1
        if (!already && misses == 1) {
            events.onSlip()
            onMissed()
        }
        val hinted = misses >= 2
        val keys = s.wrongKeys(id) + if (hinted) listOf(LetterVoice.HINT) else emptyList()
        state = state.copy(
            tiles = state.tiles.map {
                when {
                    it.id == id -> it.copy(mark = TileMark.WRONG)
                    hinted && it.id == s.correctId -> it.copy(mark = TileMark.HINT)
                    else -> it
                }
            },
            misses = misses,
            feedback = LetterFeedback(tappedId = id, lookingForId = s.correctId),
            cue = cue(keys)
        )
    }
}
