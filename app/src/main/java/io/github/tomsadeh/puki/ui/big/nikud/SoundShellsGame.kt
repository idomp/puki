package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.big.letters.VoiceCue
import kotlin.random.Random

/** A pointed syllable on a board. [bare] is what the shell keeps (the letter without its vowel). */
data class SyllableTile(val id: String, val text: String, val clipKey: String, val bare: String, val mark: TileMark = TileMark.NONE)

/** After a wrong tap: what he tapped (the cue plays its clip, then the target's again). */
data class SyllableFeedback(val tappedId: String, val tappedText: String)

data class ShellsState(
    val phase: NikudPhase = NikudPhase.LOADING,
    /** 1: different consonants, all /a/. 2: one consonant with /a/ and /i/, plus another consonant. 3: as 2, and /u/ against /a/ and /i/. */
    val stage: Int = 1,
    /** A "meet the mark" card (1, 2 or 3) not seen yet, which the screen may offer before the first round. */
    val offerCard: Int? = null,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val tiles: List<SyllableTile> = emptyList(),
    val cue: VoiceCue = VoiceCue(),
    val feedback: SyllableFeedback? = null,
    val solved: Boolean = false,
    val misses: Int = 0,
    val starsEarned: Int = 0,
    /** Once solved: the bare consonant for the shell. */
    val foundBare: String? = null,
    val sessionComplete: Boolean = false
)

/**
 * Sound shells: Puki says a syllable, the child taps it among 3 boards. No syllable is ever written in
 * the prompt. A wrong tap plays the tapped syllable's clip and then the target again, the round goes on,
 * and after two misses the right board is hinted.
 *
 * Stage 1 (items: the 8 consonants): three different consonants, all /a/; kamatz and patach are mixed
 * across rounds (never both in one round: every /a/ board of a round has the same mark).
 * Stage 2 (items: consonant + sound, 16): the target's consonant with /a/ and with /i/, plus another
 * consonant with the target's sound. Never two boards with the same sound of the same consonant.
 * Stage 3 (once [NikudEligibility] opens /u/): a /u/ target has the same consonant with /a/ and /i/ as its
 * other two boards (סוּ / סַ / סִ); a few rounds ([AI_REVIEW_ROUNDS] at most) keep asking /a/ and /i/ as in
 * stage 2. Shuruk is the /u/ spelling; kubutz joins the rounds, mixed with it, when [NikudEligibility]
 * allows. Never two spellings of one sound in a round (not מוּ with מֻ, not מַ with מָ).
 *
 * The stage comes from the scheduler's record: stage 2 once [STAGE2_SOLID] of the 8 stage-1 items are
 * solid (asked in two sessions or more and not shaky now). [NikudEligibility] adds consonants only
 * after the original stage-2 sounds are comfortable (or at once, if the parent chose so); each addition
 * joins in /a/ and /i/ (and /u/ at stage 3).
 */
class SoundShellsGame(
    private val scheduler: RoundScheduler,
    private val random: Random,
    private val events: RoundEvents,
    private val cardsSeen: Set<Int> = emptySet(),
    val rounds: Int = ROUNDS,
    private val eligibility: NikudEligibility = NikudEligibility()
) {
    companion object {
        const val ROUNDS = 8
        const val STAGE2_SOLID = 6
        /** In a stage-3 session at most this many rounds are /a/ or /i/; the rest are /u/. */
        const val AI_REVIEW_ROUNDS = 3
        fun stage1Item(consonant: String) = "s1_$consonant"
        fun stage2Item(consonant: String, sound: Nikud.Sound) = "s2_${consonant}_${sound.name.lowercase()}"

        val STAGE1_ITEMS = Nikud.introductoryConsonants.map { stage1Item(it.id) }
        /** The original 16 /a/ and /i/ items: they pace the extra consonants. */
        val STAGE2_ITEMS = Nikud.introductoryConsonants.flatMap { c -> Nikud.aiSounds.map { stage2Item(c.id, it) } }
        /** All 26 /a/ and /i/ items: they pace /u/. */
        val AI_ITEMS = Nikud.consonants.flatMap { c -> Nikud.aiSounds.map { stage2Item(c.id, it) } }
        val U_ITEMS = Nikud.consonants.map { stage2Item(it.id, Nikud.Sound.U) }

        private fun isU(item: String) = item.endsWith("_u")

        /** One spelling per audible sound, even if two consonants use different recordings. */
        fun distinctSounds(answer: Nikud.Syllable, candidates: List<Nikud.Syllable>): List<Nikud.Syllable> =
            candidates.filter { it.soundId != answer.soundId }.distinctBy { it.soundId }
    }

    var state = ShellsState()
        private set

    private var tokens = 0
    private var stage = 1
    private var aMarks = emptyList<Nikud.Mark>()
    private var uMarks = emptyList<Nikud.Mark>()
    private var target: Nikud.Syllable? = null
    private var correctId = ""
    private var replayKeys: List<String> = emptyList()
    private var slipped = false
    private var consonants = Nikud.introductoryConsonants

    private fun cue(keys: List<String>) = VoiceCue(keys, ++tokens)

    fun start() {
        val solid = STAGE1_ITEMS.count { id -> scheduler.memory.record(id).let { it.seen >= 2 && it.misses == 0 } }
        stage = when {
            solid < STAGE2_SOLID -> 1
            eligibility.uSyllables(scheduler.memory) -> 3
            else -> 2
        }
        consonants = if (stage == 1) eligibility.firstConsonants() else eligibility.consonants(scheduler.memory)
        val pool = if (stage == 1) consonants.map { stage1Item(it.id) } else consonants.flatMap { c -> Nikud.aiSounds.map { stage2Item(c.id, it) } }
        if (stage == 3) {
            scheduler.begin(rounds, pool + consonants.map { stage2Item(it.id, Nikud.Sound.U) },
                groupOf = { if (isU(it)) "u" else "ai" }, groupCaps = mapOf("ai" to AI_REVIEW_ROUNDS))
        } else scheduler.begin(rounds, pool)
        aMarks = List(rounds) { if (it % 2 == 0) Nikud.Mark.PATAH else Nikud.Mark.KAMATS }.shuffled(random)
        uMarks = if (eligibility.kubutz(scheduler.memory)) {
            List(rounds) { if (it % 2 == 0) Nikud.Mark.SHURUK else Nikud.Mark.KUBUTZ }.shuffled(random)
        } else List(rounds) { Nikud.Mark.SHURUK }
        // (a child who skipped the stage-2 card still gets it before the /u/ one)
        val offer = if (stage == 3) listOf(2, 3).firstOrNull { it !in cardsSeen } else stage.takeIf { it !in cardsSeen }
        state = ShellsState(stage = stage, offerCard = offer)
        show(first = true)
    }

    /** Both spellings get equal practice, in a fresh order. */
    private fun nextAMark(): Nikud.Mark {
        return aMarks[scheduler.roundIndex]
    }

    /** Shuruk, mixed with kubutz once that is open. */
    private fun nextUMark(): Nikud.Mark = uMarks[scheduler.roundIndex]

    private fun tile(s: Nikud.Syllable) = SyllableTile(s.id, s.text, s.clipKey, s.consonant.bare)

    private fun show(first: Boolean) {
        val item = scheduler.current ?: return
        val others = consonants
        val choices: RoundScheduler.Choices
        val answer: Nikud.Syllable
        if (stage == 1) {
            val consonant = item.removePrefix("s1_")
            val mark = nextAMark()
            answer = Nikud.syllable(consonant, mark)
            choices = scheduler.choices(answer.id, distinctSounds(answer, others.map { Nikud.syllable(it.id, mark) }).map { it.id }, 3,
                distanceTo = { 2 }, key = item)
        } else if (isU(item)) {
            // the same consonant in all three sounds: only the vowel tells the boards apart
            val consonant = item.removePrefix("s2_").substringBeforeLast('_')
            answer = Nikud.syllable(consonant, nextUMark())
            choices = scheduler.choices(answer.id, emptyList(), 3, distanceTo = { 2 },
                forced = listOf(Nikud.syllable(consonant, nextAMark()).id, Nikud.syllable(consonant, Nikud.Mark.HIRIQ).id), key = item)
        } else {
            val parts = item.removePrefix("s2_").split('_')
            val consonant = parts[0]
            val sound = Nikud.Sound.valueOf(parts[1].uppercase())
            val aMark = nextAMark()
            val ownMark = if (sound == Nikud.Sound.A) aMark else Nikud.Mark.HIRIQ
            val twinMark = if (sound == Nikud.Sound.A) Nikud.Mark.HIRIQ else aMark
            answer = Nikud.syllable(consonant, ownMark)
            choices = scheduler.choices(answer.id, distinctSounds(answer, others.map { Nikud.syllable(it.id, ownMark) }).map { it.id }, 3,
                distanceTo = { 2 }, forced = listOf(Nikud.syllable(consonant, twinMark).id), key = item)
        }
        target = answer
        correctId = answer.id
        replayKeys = listOf(answer.clipKey)
        slipped = false
        state = state.copy(
            phase = NikudPhase.ROUND,
            roundIndex = scheduler.roundIndex,
            totalRounds = rounds,
            tiles = choices.options.map { tile(Nikud.syllable(it)) },
            // the first round of a session says what to do, then plays the sound and asks
            cue = cue(if (first) listOf(Nikud.Voice.LISTEN, answer.clipKey, Nikud.Voice.WHICH_SOUND) else replayKeys),
            feedback = null, solved = false, misses = 0, foundBare = null
        )
    }

    fun replay() {
        if (state.phase == NikudPhase.ROUND) state = state.copy(cue = cue(replayKeys))
    }

    /** The child tapped board [id]. A board that was already wrong replays its sound and the target (no new miss). */
    fun onAnswer(id: String) {
        val t = target ?: return
        if (state.phase != NikudPhase.ROUND || state.solved || state.tiles.none { it.id == id }) return
        val tapped = state.tiles.first { it.id == id }
        if (id == correctId) {
            events.onStar()
            state = state.copy(
                tiles = state.tiles.map { if (it.id == id) it.copy(mark = TileMark.CORRECT) else it },
                solved = true, starsEarned = state.starsEarned + 1, foundBare = t.consonant.bare,
                cue = cue(listOf(t.clipKey))
            )
            return
        }
        val already = tapped.mark == TileMark.WRONG
        val misses = if (already) state.misses else state.misses + 1
        if (!already && !slipped) {
            slipped = true
            events.onSlip()
            scheduler.miss()
        }
        val hinted = misses >= 2
        state = state.copy(
            tiles = state.tiles.map {
                when {
                    it.id == id -> it.copy(mark = TileMark.WRONG)
                    hinted && it.id == correctId -> it.copy(mark = TileMark.HINT)
                    else -> it
                }
            },
            misses = misses,
            feedback = SyllableFeedback(id, tapped.text),
            cue = cue(listOf(tapped.clipKey, t.clipKey))
        )
    }

    /** After the celebration. */
    fun next() {
        if (state.phase != NikudPhase.ROUND || !state.solved) return
        if (scheduler.advance() == null) {
            state = state.copy(phase = NikudPhase.COMPLETE, sessionComplete = true, tiles = emptyList(), feedback = null, cue = VoiceCue(emptyList(), ++tokens))
            events.onFinish(rounds, scheduler.finish())
        } else {
            show(first = false)
        }
    }
}
