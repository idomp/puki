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
 */
class NikudEligibility(
    private val shells: SchedulerMemory = SchedulerMemory(),
    private val join: SchedulerMemory = SchedulerMemory(),
    private val pace: ContentPace = ContentPace.GRADUAL
) {
    private fun solid(memory: SchedulerMemory, id: String) =
        memory.record(id).let { it.seen >= pace.sessions && it.misses == 0 }

    /** The consonants Sound Shells uses at stage 1: the original eight, unless the pace opens everything at once. */
    fun firstConsonants(): List<Nikud.Consonant> =
        if (pace.shellsSolid == 0) Nikud.consonants else Nikud.introductoryConsonants

    fun consonants(memory: SchedulerMemory): List<Nikud.Consonant> =
        if (SoundShellsGame.STAGE2_ITEMS.count { solid(memory, it) } >= pace.shellsSolid) Nikud.consonants
        else Nikud.introductoryConsonants

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

    fun joinWords(memory: SchedulerMemory): List<Nikud.Word> {
        val ready = Nikud.introductoryWords.count { solid(memory, it.id) } >= pace.joinSolid
        return Nikud.words.filter { it.id !in Nikud.pendingAudio &&
            (it in Nikud.introductoryWords || ready && (!pace.prerequisites || met(it))) }
    }

    fun readWords(memory: SchedulerMemory): List<Nikud.Word> {
        val ready = Nikud.introductoryWords.count { it.tier == Nikud.Tier.CLEAR && solid(memory, it.id) } >= pace.readSolid
        return Nikud.clearWords.filter { it in Nikud.introductoryWords ||
            ready && (!pace.prerequisites || met(it) && join.isKnown(it.id)) }
    }
}
