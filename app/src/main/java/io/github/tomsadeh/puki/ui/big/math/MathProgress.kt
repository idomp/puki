package io.github.tomsadeh.puki.ui.big.math

import io.github.tomsadeh.puki.data.models.MathLevel

/** Names are persisted; order defines the teaching progression. */
enum class MathStage(val maximum: Int, val allowsZero: Boolean = false) {
    JOIN_5(5), TAKE_5(5), MIXED_5(5), ZERO_5(5, true), MIXED_10(10, true),
    PART_5(5, allowsZero = true), PART_10(10, allowsZero = true)
}

/** Friends keeps counting content when asked for a part-whole stage. */
internal val MathStage.countingStage: MathStage get() = if (this >= MathStage.PART_5) MathStage.MIXED_10 else this

/** The stage the parent's Sums level starts at; each game still stops at its own ceiling. */
val MathLevel.floor: MathStage get() = when (this) {
    MathLevel.FROM_START -> MathStage.JOIN_5
    MathLevel.WITHIN_TEN -> MathStage.MIXED_10
    MathLevel.MAKE_THE_TOTAL -> MathStage.PART_5
}

/** Each game advances only as far as its own content supports. */
enum class MathGameId(val island: String, val maxStage: MathStage) {
    FRIENDS("big_math_friends", MathStage.MIXED_10),
    PICNIC("big_math_picnic", MathStage.PART_10),
    FRAME("big_math_frame", MathStage.PART_10);

    internal fun normalize(stage: MathStage): MathStage = minOf(stage, maxStage)

    /** Saved progress lifted to the parent's floor, then held to this game's ceiling (which wins). */
    internal fun startStage(saved: MathStage, floor: MathStage): MathStage = normalize(maxOf(saved, floor))
}

interface MathProgressStore {
    suspend fun load(game: MathGameId): MathStage
    suspend fun save(game: MathGameId, stage: MathStage)
}

class InMemoryMathProgressStore : MathProgressStore {
    private val stages = mutableMapOf<MathGameId, MathStage>()
    override suspend fun load(game: MathGameId) = game.normalize(stages[game] ?: MathStage.JOIN_5)
    override suspend fun save(game: MathGameId, stage: MathStage) { stages[game] = stage }
}

/** The last problems each game showed, oldest first, so the next visit can open with something else. */
interface MathHistoryStore {
    suspend fun load(game: MathGameId): List<String>
    suspend fun save(game: MathGameId, recent: List<String>)
}

class InMemoryMathHistoryStore : MathHistoryStore {
    private val recent = mutableMapOf<MathGameId, List<String>>()
    override suspend fun load(game: MathGameId) = recent[game].orEmpty()
    override suspend fun save(game: MathGameId, recent: List<String>) { this.recent[game] = recent }
}

/**
 * One miss at most promotes up to the game's ceiling; a strict majority of missed rounds eases, but never
 * below the parent's [floor] (which the game's ceiling still outranks).
 */
fun nextMathStage(
    game: MathGameId, stage: MathStage, rounds: Int, misses: Int, missedRounds: Int,
    floor: MathStage = MathStage.JOIN_5
): MathStage {
    require(rounds > 0 && misses >= 0 && missedRounds in 0..rounds && misses >= missedRounds)
    val current = game.normalize(stage)
    val index = when {
        misses <= 1 -> current.ordinal + 1
        missedRounds > rounds / 2 -> current.ordinal - 1
        else -> current.ordinal
    }.coerceIn(minOf(floor.ordinal, game.maxStage.ordinal), game.maxStage.ordinal)
    return MathStage.entries[index]
}
