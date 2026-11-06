package io.github.tomsadeh.puki.ui.big.math

/** Names are persisted; order defines the teaching progression. */
enum class MathStage(val maximum: Int, val allowsZero: Boolean = false) {
    JOIN_5(5), TAKE_5(5), MIXED_5(5), ZERO_5(5, true), MIXED_10(10, true),
    PART_5(5, allowsZero = true), PART_10(10, allowsZero = true)
}

/** Friends and Frame keep MIXED_10 content until their own part-whole rounds exist. */
internal val MathStage.countingStage: MathStage get() = if (this >= MathStage.PART_5) MathStage.MIXED_10 else this

enum class MathGameId(val island: String) {
    FRIENDS("big_math_friends"), PICNIC("big_math_picnic"), FRAME("big_math_frame")
}

interface MathProgressStore {
    suspend fun load(game: MathGameId): MathStage
    suspend fun save(game: MathGameId, stage: MathStage)
}

class InMemoryMathProgressStore : MathProgressStore {
    private val stages = mutableMapOf<MathGameId, MathStage>()
    override suspend fun load(game: MathGameId) = stages[game] ?: MathStage.JOIN_5
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

/** One miss at most promotes; misses in a strict majority of rounds ease the next session. */
fun nextMathStage(stage: MathStage, rounds: Int, misses: Int, missedRounds: Int): MathStage {
    require(rounds > 0 && misses >= 0 && missedRounds in 0..rounds && misses >= missedRounds)
    val index = when {
        misses <= 1 -> stage.ordinal + 1
        missedRounds > rounds / 2 -> stage.ordinal - 1
        else -> stage.ordinal
    }.coerceIn(0, MathStage.entries.lastIndex)
    return MathStage.entries[index]
}
