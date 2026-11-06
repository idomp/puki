package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.StoreQueue
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random

/*
 * The shared part of the Sounds and Words games: phases, picture tiles, and the picture chooser that
 * "Puki joins a word" and "Read the sign" both use. Plain Kotlin, no Android imports. The games reuse
 * the letters games' VoiceCue (keys + token), RoundEvents and TileMark.
 */

enum class NikudPhase {
    /** Memory not loaded yet. */
    LOADING,
    ROUND,
    COMPLETE
}

/** A picture to tap: [id] is the word's id (what the picture shows), [pictureId] the drawable's name. */
data class PictureTile(val id: String, val pictureId: String, val mark: TileMark = TileMark.NONE)

/** After a wrong picture: the word the tapped picture shows (its clip is in the cue), for the screen's small caption. */
data class PictureFeedback(val tappedId: String, val tappedWordText: String)

/** How "Read the sign" picks distractors. */
enum class DistractorLevel {
    /** Different first sound: the first letter can be enough. */
    DIFFERENT_START,
    /** The same first consonant where possible (גַּל / גַּן / גַּג): only reading the end solves it. */
    SAME_START
}

/** Where the answer went and which distractors it had, so a second showing can change both. */
class PictureLayout(val options: List<Nikud.Word>, val correctIndex: Int)

/** Chooses picture distractors (clear tier only, never twins of each other or of the target) and the answer's place. */
class PictureChooser(private val random: Random, private val scheduler: RoundScheduler) {

    fun choose(target: Nikud.Word, level: DistractorLevel, count: Int = 3): PictureLayout {
        val seen = scheduler.presentation(target.id)
        val candidates = Nikud.clearWords.filter { it.id != target.id && !Nikud.areTwins(it, target) }
        val sameStart = { w: Nikud.Word -> w.firstConsonant == target.firstConsonant }
        val ordered = candidates.shuffled(random).sortedWith(
            compareBy<Nikud.Word> {
                when (level) {
                    DistractorLevel.DIFFERENT_START -> if (sameStart(it)) 1 else 0
                    DistractorLevel.SAME_START -> if (sameStart(it)) 0 else 1
                }
            }.thenBy { it.id in (seen?.distractors ?: emptySet()) }  // asked again: other neighbours when there are any
        )
        val allowed = if (level == DistractorLevel.DIFFERENT_START) ordered.filter { !sameStart(it) }.ifEmpty { ordered } else ordered
        val picked = mutableListOf<Nikud.Word>()
        for (w in allowed) {
            if (picked.size == count - 1) break
            if (picked.none { Nikud.areTwins(it, w) }) picked.add(w)
        }
        val others = picked.shuffled(random).toMutableList()
        val slot = scheduler.slots.draw(others.size + 1, avoid = seen?.position).coerceAtMost(others.size)
        others.add(slot, target)
        scheduler.rememberPresentation(target.id, slot, picked.map { it.id }.toSet())
        return PictureLayout(others, slot)
    }
}

/** Which "meet the mark" cards the child has seen (1 = kamatz/patach, 2 = hiriq). */
interface NikudCardStore {
    suspend fun seen(): Set<Int>
    suspend fun markSeen(card: Int)
}

/** A completed card stays remembered even when its screen leaves immediately. */
class OrderedNikudCardStore(private val store: NikudCardStore, scope: CoroutineScope) {
    private val queue = StoreQueue(scope)
    suspend fun seen(): Set<Int> = queue.read { store.seen() }
    fun markSeen(card: Int) = queue.write { store.markSeen(card) }
}

class InMemoryNikudCardStore(initial: Set<Int> = emptySet()) : NikudCardStore {
    private val cards = initial.toMutableSet()
    override suspend fun seen(): Set<Int> = cards.toSet()
    override suspend fun markSeen(card: Int) { cards.add(card) }
}
