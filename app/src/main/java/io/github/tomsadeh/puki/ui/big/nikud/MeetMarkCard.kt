package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.ui.big.letters.VoiceCue

/** One face of a card: a pointed מ and the mark's name (shown only after the sound was heard). */
data class MarkFace(val text: String, val markName: String, val nameKey: String)

data class MeetMarkState(
    /** 1: מַ and מָ (one sound); 2: מִ. 0 before [MeetMarkCard.start]. */
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
 * (mem_i, חִירִיק). [startCard] 1 plays card 1 (then card 2 if [throughAll], as from the door), 2 only card 2.
 * [onSeen] is told when a card is finished, so the island can remember it was seen.
 */
class MeetMarkCard(
    private val startCard: Int = 1,
    private val throughAll: Boolean = true,
    private val onSeen: (Int) -> Unit = {}
) {
    var state = MeetMarkState()
        private set

    private var tokens = 0
    private fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    fun start() = open(startCard, intro = true)

    private fun open(card: Int, intro: Boolean) {
        val mem = Nikud.consonant("mem")
        val faces = if (card == 1) {
            listOf(Nikud.Mark.KAMATS, Nikud.Mark.PATAH)
        } else {
            listOf(Nikud.Mark.HIRIQ)
        }.map { MarkFace(mem.bare + it.vowel, it.hebrewName, it.nameKey) }
        state = MeetMarkState(card = card, faces = faces, cue = cue(if (card == 1 && intro) listOf(Nikud.Voice.TWO_MARKS) else emptyList()))
    }

    private val soundKey get() = Nikud.syllableClip("mem", if (state.card == 1) Nikud.Sound.A else Nikud.Sound.I)

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
        if (state.card == 1 && throughAll) open(2, intro = false) else state = state.copy(finished = true, cue = cue(emptyList()))
    }
}
