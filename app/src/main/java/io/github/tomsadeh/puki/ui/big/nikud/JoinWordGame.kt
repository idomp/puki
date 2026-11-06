package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.big.letters.VoiceCue

enum class JoinPhase {
    LOADING,
    /** The word's boards are on the sand; tap them in order. */
    UNITS,
    /** Every board was tapped: the screen slides them into one sign and calls [JoinWordGame.onJoined]; the word is being said. */
    JOINED,
    /** Pick the picture of the word. */
    PICTURE,
    /** The round is done: the screen celebrates, then calls [JoinWordGame.next]. */
    SOLVED,
    COMPLETE
}

/** A board of the word. [clipKey] is null for a closing consonant (silent: it only lights up). */
data class JoinUnit(
    val text: String,
    val kind: Nikud.UnitKind,
    val clipKey: String?,
    /** Tapped already (a closing consonant is "lit"). */
    val done: Boolean = false,
    /** The one to tap now. */
    val isNext: Boolean = false
)

data class JoinState(
    val phase: JoinPhase = JoinPhase.LOADING,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val wordId: String = "",
    /** The whole word (shown once the boards are one sign). */
    val wordText: String = "",
    val units: List<JoinUnit> = emptyList(),
    /** Counts taps on a unit that is not next yet (the screen may squish the boards a little). */
    val nudge: Int = 0,
    /** False for מִלָּה: the round ends when the word is heard. */
    val hasPicture: Boolean = true,
    val pictures: List<PictureTile> = emptyList(),
    val cue: VoiceCue = VoiceCue(),
    val feedback: PictureFeedback? = null,
    val solved: Boolean = false,
    val misses: Int = 0,
    val starsEarned: Int = 0,
    /** Once solved: the picture the shell keeps (null for a word without one). */
    val foundPictureId: String? = null,
    val sessionComplete: Boolean = false
)

/**
 * Puki joins a word. The word arrives as boards (its reading units); the child taps them in order:
 * a CV unit plays its clip, a closing consonant lights up silently, a tap on a later unit does nothing
 * but count [JoinState.nudge]. After the last one: JOINED (the word is said), the screen calls
 * [onJoined] when the boards have slid together, then PICTURE with 3 pictures (the word's and 2 clear,
 * different-sounding ones, never twins). A wrong picture plays what it shows and then the word again;
 * the round goes on. מִלָּה has no picture step: it is solved when joined.
 */
class JoinWordGame(
    private val scheduler: RoundScheduler,
    random: kotlin.random.Random,
    private val events: RoundEvents,
    val rounds: Int = ROUNDS
) {
    companion object {
        const val ROUNDS = 8
    }

    var state = JoinState()
        private set

    private val chooser = PictureChooser(random, scheduler.slots)
    private var tokens = 0
    private var word: Nikud.Word? = null
    private var layout: PictureLayout? = null
    private var slipped = false

    private fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    fun start() {
        chooser.forget()
        scheduler.begin(rounds, Nikud.words.map { it.id })
        show(first = true)
    }

    private fun show(first: Boolean) {
        val w = Nikud.word(scheduler.current ?: return)
        word = w
        layout = null
        slipped = false
        state = state.copy(
            phase = JoinPhase.UNITS,
            roundIndex = scheduler.roundIndex,
            totalRounds = rounds,
            wordId = w.id,
            wordText = w.text,
            units = w.units.mapIndexed { i, u -> JoinUnit(u.text, u.kind, u.clipKey, done = false, isNext = i == 0) },
            hasPicture = w.pictureId != null,
            pictures = emptyList(),
            cue = cue(if (first) listOf(Nikud.Voice.JOIN) else emptyList()),
            feedback = null, solved = false, misses = 0, foundPictureId = null
        )
    }

    /** The child tapped unit [index]. */
    fun onUnitTap(index: Int) {
        val w = word ?: return
        if (state.phase != JoinPhase.UNITS || index !in state.units.indices) return
        val next = state.units.indexOfFirst { !it.done }
        val unit = state.units[index]
        when {
            index > next -> state = state.copy(nudge = state.nudge + 1)
            index < next -> state = state.copy(cue = cue(listOfNotNull(unit.clipKey)))  // heard already: say it again
            else -> {
                val units = state.units.mapIndexed { i, u -> u.copy(done = u.done || i == index, isNext = i == index + 1) }
                if (index == units.lastIndex) {
                    // joined: the last clip (if it has one), then the whole word
                    state = state.copy(units = units, phase = JoinPhase.JOINED, cue = cue(listOfNotNull(unit.clipKey) + w.wordKey))
                } else {
                    state = state.copy(units = units, cue = cue(listOfNotNull(unit.clipKey)))
                }
            }
        }
    }

    /** The boards have slid into one sign. */
    fun onJoined() {
        val w = word ?: return
        if (state.phase != JoinPhase.JOINED) return
        if (w.pictureId == null) {
            solve(w)
            return
        }
        val l = chooser.choose(w, DistractorLevel.DIFFERENT_START)
        layout = l
        state = state.copy(
            phase = JoinPhase.PICTURE,
            pictures = l.options.map { PictureTile(it.id, it.pictureId!!) },
            cue = cue(listOf(Nikud.Voice.PICK_PICTURE))
        )
    }

    private fun solve(w: Nikud.Word) {
        events.onStar()
        state = state.copy(phase = JoinPhase.SOLVED, solved = true, starsEarned = state.starsEarned + 1, foundPictureId = w.pictureId)
    }

    /** Say the instruction (while joining) or the word again. */
    fun replay() {
        val w = word ?: return
        state = when (state.phase) {
            JoinPhase.UNITS -> state.copy(cue = cue(listOf(Nikud.Voice.JOIN)))
            JoinPhase.JOINED, JoinPhase.PICTURE, JoinPhase.SOLVED -> state.copy(cue = cue(listOf(w.wordKey)))
            else -> state
        }
    }

    /** The child tapped the picture of word [wordId]. */
    fun onAnswer(wordId: String) {
        val w = word ?: return
        if (state.phase != JoinPhase.PICTURE || state.solved || state.pictures.none { it.id == wordId }) return
        if (wordId == w.id) {
            events.onStar()
            state = state.copy(
                phase = JoinPhase.SOLVED, solved = true, starsEarned = state.starsEarned + 1, foundPictureId = w.pictureId,
                pictures = state.pictures.map { if (it.id == wordId) it.copy(mark = TileMark.CORRECT) else it }
            )
            return
        }
        val tapped = Nikud.word(wordId)
        val already = state.pictures.first { it.id == wordId }.mark == TileMark.WRONG
        val misses = if (already) state.misses else state.misses + 1
        if (!already && !slipped) {
            slipped = true
            events.onSlip()
            scheduler.miss()
        }
        state = state.copy(
            pictures = state.pictures.map {
                when {
                    it.id == wordId -> it.copy(mark = TileMark.WRONG)
                    misses >= 2 && it.id == w.id -> it.copy(mark = TileMark.HINT)
                    else -> it
                }
            },
            misses = misses,
            feedback = PictureFeedback(wordId, tapped.text),
            cue = cue(listOf(tapped.wordKey, w.wordKey))
        )
    }

    fun next() {
        if (state.phase != JoinPhase.SOLVED) return
        if (scheduler.advance() == null) {
            state = state.copy(phase = JoinPhase.COMPLETE, sessionComplete = true, units = emptyList(), pictures = emptyList(),
                feedback = null, cue = VoiceCue(emptyList(), ++tokens))
            events.onFinish(rounds, scheduler.finish())
        } else {
            show(first = false)
        }
    }
}
