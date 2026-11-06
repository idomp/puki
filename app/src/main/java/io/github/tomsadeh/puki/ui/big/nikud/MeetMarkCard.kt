package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.ui.big.letters.VoiceCue

/** One face of a card: a pointed מ and the mark's name (shown only after the sound was heard). */
data class MarkFace(val text: String, val markName: String, val nameKey: String)

data class MeetMarkState(
    /** 1: מַ and מָ (one sound); 2: מִ; 3: מוּ and מֻ (one sound). 0 before [MeetMarkCard.start]. */
    val card: Int = 0,
    val faces: List<MarkFace> = emptyList(),
    /** The sound was heard at least once: the name tags may appear. */
    val heard: Boolean = false,
    val namesVisible: Boolean = false,
    val cue: VoiceCue = VoiceCue(),
    /** Every card of this visit is done. */
    val finished: Boolean = false
)

/**
 * Meet the mark: a short card, not scored. Card 1 shows מַ and מָ on one double board with one speaker:
 * tapping either face or the speaker plays the same clip (voice_big_nikud_mem_a); the name tags
 * (קָמָץ, פַּתָּח) show after the sound was heard and play their name when tapped. Card 2 shows מִ
 * (mem_i, חִירִיק). Card 3 is built like card 1 for מוּ and מֻ (mem_u, שׁוּרוּק, קֻבּוּץ) and says the same
 * "two marks, one sound" line. [startCard] 1 plays card 1 (then the next cards up to [lastCard] if
 * [throughAll], as from the door), 2 or 3 only that card. [lastCard] is 3 once /u/ is open, else 2.
 * [onSeen] is told when a card is finished, so the island can remember it was seen.
 */
class MeetMarkCard(
    private val startCard: Int = 1,
    private val throughAll: Boolean = true,
    private val lastCard: Int = CARD_I,
    private val onSeen: (Int) -> Unit = {}
) {
    companion object {
        const val CARD_I = 2
        const val CARD_U = 3
    }

    var state = MeetMarkState()
        private set

    private var tokens = 0
    private fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    fun start() = open(startCard, intro = true)

    private fun open(card: Int, intro: Boolean) {
        val mem = Nikud.consonant("mem")
        val faces = when (card) {
            1 -> listOf(Nikud.Mark.KAMATS, Nikud.Mark.PATAH)
            CARD_I -> listOf(Nikud.Mark.HIRIQ)
            else -> listOf(Nikud.Mark.SHURUK, Nikud.Mark.KUBUTZ)
        }.map { MarkFace(mem.bare + it.vowel, it.hebrewName, it.nameKey) }
        // the two-marks cards say their line when they start, and card 3 also when it follows card 2
        val saysTwoMarks = card == CARD_U || card == 1 && intro
        state = MeetMarkState(card = card, faces = faces, cue = cue(if (saysTwoMarks) listOf(Nikud.Voice.TWO_MARKS) else emptyList()))
    }

    private val soundKey get() = Nikud.syllableClip("mem", when (state.card) {
        1 -> Nikud.Sound.A
        CARD_I -> Nikud.Sound.I
        else -> Nikud.Sound.U
    })

    /** Tapping a face or the speaker: the card's sound, and then the names may show. */
    fun onFaceTap(index: Int = 0) {
        if (state.card == 0 || state.finished || index !in state.faces.indices) return
        state = state.copy(heard = true, namesVisible = true, cue = cue(listOf(soundKey)))
    }

    fun onSpeakerTap() = onFaceTap(0)

    /** Tapping a name tag says the mark's name (only once the sound was heard). */
    fun onNameTap(index: Int) {
        if (!state.namesVisible || index !in state.faces.indices) return
        state = state.copy(cue = cue(listOf(state.faces[index].nameKey)))
    }

    /** The "go on" button: the next card, or the end. */
    fun onDone() {
        if (state.card == 0 || state.finished) return
        onSeen(state.card)
        if (throughAll && state.card < lastCard) open(state.card + 1, intro = false) else state = state.copy(finished = true, cue = cue(emptyList()))
    }
}
