package io.github.tomsadeh.puki.ui.big.math

/** Names are persisted; order defines the teaching progression. */
enum class MathStage(val maximum: Int, val allowsZero: Boolean = false) {
    JOIN_5(5), TAKE_5(5), MIXED_5(5), ZERO_5(5, true), MIXED_10(10, true),
    PART_5(5, allowsZero = true), PART_10(10, allowsZero = true)
}

/** Legacy PART stages use counting content; Friends and Frame progression stops at MIXED_10. */
internal val MathStage.countingStage: MathStage get() = if (this >= MathStage.PART_5) MathStage.MIXED_10 else this

/** Only Picnic advances into part-whole stages until the other games have their own rounds. */
enum class MathGameId(val island: String, val maxStage: MathStage) {
    FRIENDS("big_math_friends", MathStage.MIXED_10),
    PICNIC("big_math_picnic", MathStage.PART_10),
    FRAME("big_math_frame", MathStage.MIXED_10);

    internal fun normalize(stage: MathStage): MathStage = minOf(stage, maxStage)
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

/** One miss at most promotes up to the game's ceiling; a strict majority of missed rounds eases. */
fun nextMathStage(game: MathGameId, stage: MathStage, rounds: Int, misses: Int, missedRounds: Int): MathStage {
    require(rounds > 0 && misses >= 0 && missedRounds in 0..rounds && misses >= missedRounds)
    val current = game.normalize(stage)
    val index = when {
        misses <= 1 -> current.ordinal + 1
        missedRounds > rounds / 2 -> current.ordinal - 1
        else -> current.ordinal
    }.coerceIn(0, game.maxStage.ordinal)
    return MathStage.entries[index]
}
