package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.data.models.ContentPace
import io.github.tomsadeh.puki.learning.SchedulerMemory

/**
 * Expansion uses completed-session records, never exposure or the number of visits alone. The parent
 * picks the [ContentPace]; each value is a row of thresholds (see the enum), so nothing below knows
 * the numbers. A solid item was asked in at least `pace.sessions` sessions and has no remaining misses.
 *
 * GRADUAL (the default): solid is two sessions. New shells need 12 of the original 16 consonant-sound
 * items solid, leaving the original /a/ introduction and stage change intact. This three-quarter
 * threshold allows a little unfinished practice without rushing /i/. New Join Word targets need 8 of
 * its original 12 words solid; new Read Sign targets need 6 of its original 9 clear words solid,
 * preserving the existing level-2 threshold. FASTER: solid is one session, and the thresholds are 8, 6
 * and 4. ALL_NOW: every consonant and word is open from the first session with no prerequisites.
 *
 * Except under ALL_NOW, each new word also needs its CV sounds answered in Sound Shells at least once
 * without remaining misses. Closing letters need an encounter with their letter family in either sound;
 * a mater yod or final ה adds no sound. Read Sign additionally needs that word answered in Join Word
 * without remaining misses. The same eligibility applies to picture distractors, and pending
 * recordings are excluded throughout, whatever the pace.
 *
 * The /u/ sound opens after all of the a/i material, game by game: Sound Shells when `uShellsSolid` of the
 * 26 a/i consonant-sound items are solid (GRADUAL 20, FASTER 18), Join Word when `uJoinSolid` of the 21 a/i
 * words are solid (16, 14), Read Sign when `uReadSolid` of the 18 clear a/i words are solid (13, 11). ALL_NOW
 * opens it at once. The /u/ words follow the same sound, joining and closing-letter rules as any new word.
 * Shuruk comes first: the second mark, kubutz, also needs the /u/ card seen and `kubutzSyllables` /u/
 * syllables answered cleanly in Sound Shells (GRADUAL 4, FASTER 3; ALL_NOW at once). [cards] is the set of
 * "meet the mark" cards the child has seen.
 */
class NikudEligibility(
    private val shells: SchedulerMemory = SchedulerMemory(),
    private val join: SchedulerMemory = SchedulerMemory(),
    private val pace: ContentPace = ContentPace.GRADUAL,
    private val cards: Set<Int> = emptySet()
) {
    private fun solid(memory: SchedulerMemory, id: String) =
        memory.record(id).let { it.seen >= pace.sessions && it.misses == 0 }

    /** The consonants Sound Shells uses at stage 1: the original eight, unless the pace opens everything at once. */
    fun firstConsonants(): List<Nikud.Consonant> =
        if (pace.shellsSolid == 0) Nikud.consonants else Nikud.introductoryConsonants

    fun consonants(memory: SchedulerMemory): List<Nikud.Consonant> =
        if (SoundShellsGame.STAGE2_ITEMS.count { solid(memory, it) } >= pace.shellsSolid) Nikud.consonants
        else Nikud.introductoryConsonants

    /** ALL_NOW has no prerequisites: Sound Shells starts at its last stage, with every consonant and /u/. */
    fun opensAtOnce(): Boolean = !pace.prerequisites

    /** /u/ syllables in Sound Shells (the memory is the shells memory). */
    fun uSyllables(memory: SchedulerMemory): Boolean =
        SoundShellsGame.AI_ITEMS.count { solid(memory, it) } >= pace.uShellsSolid

    /** The second /u/ mark (kubutz) in shells and words: after the /u/ card and some clean /u/ syllables. */
    fun kubutz(memory: SchedulerMemory): Boolean = !pace.prerequisites ||
        MeetMarkCard.CARD_U in cards && SoundShellsGame.U_ITEMS.count { memory.isKnown(it) } >= pace.kubutzSyllables

    private fun met(consonant: String, sound: Nikud.Sound): Boolean =
        shells.isKnown(SoundShellsGame.stage2Item(consonant, sound)) ||
            (sound == Nikud.Sound.A && shells.isKnown(SoundShellsGame.stage1Item(consonant)))

    private fun met(word: Nikud.Word) = word.units.all { unit ->
        val consonant = unit.consonant
        when {
            consonant == null -> true
            unit.sound != null -> met(consonant, unit.sound)
            else -> Nikud.Sound.values().any { met(consonant, it) }
        }
    }

    /** A /u/ word also waits for the /u/ threshold, and a kubutz word for the second mark. */
    private fun open(word: Nikud.Word, ready: Boolean, uReady: Boolean) =
        (if (word.hasU) uReady && (!word.usesKubutz || kubutz(shells)) else ready) && (!pace.prerequisites || met(word))

    fun joinWords(memory: SchedulerMemory): List<Nikud.Word> {
        val ready = Nikud.introductoryWords.count { solid(memory, it.id) } >= pace.joinSolid
        val uReady = ready && Nikud.aiWords.count { solid(memory, it.id) } >= pace.uJoinSolid
        return Nikud.words.filter { it.id !in Nikud.pendingAudio && (it in Nikud.introductoryWords || open(it, ready, uReady)) }
    }

    fun readWords(memory: SchedulerMemory): List<Nikud.Word> {
        val ready = Nikud.introductoryWords.count { it.tier == Nikud.Tier.CLEAR && solid(memory, it.id) } >= pace.readSolid
        val uReady = ready && Nikud.aiWords.count { it.tier == Nikud.Tier.CLEAR && solid(memory, it.id) } >= pace.uReadSolid
        return Nikud.clearWords.filter { it in Nikud.introductoryWords || open(it, ready, uReady) && (!pace.prerequisites || join.isKnown(it.id)) }
    }
}

/** The last "meet the mark" card the island's corner button goes through: the /u/ card once /u/ is open. */
fun lastMeetCard(shells: SchedulerMemory, pace: ContentPace): Int =
    if (NikudEligibility(shells, pace = pace).uSyllables(shells)) MeetMarkCard.CARD_U else MeetMarkCard.CARD_I
