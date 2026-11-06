package io.github.tomsadeh.puki.data.models

data class AppSettings(
    val language: Language = Language.HEBREW,
    val gender: Gender = Gender.GIRL,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    /** Volumes, 0..1: music, voices (recorded lines and Puki), sound effects. */
    val musicVolume: Float = 1f,
    val voiceVolume: Float = 1f,
    val sfxVolume: Float = 1f,
    val timeLimitMinutes: Int = 0, // 0 = no limit
    val childName: String = "",
    val onboardingDone: Boolean = false,
    /** False until the stored settings have been read (so the welcome screen doesn't flash). */
    val loaded: Boolean = false,
    val companionEnabled: Boolean = true,
    /** Puki thinks on this device (models downloaded); off, he plays his recorded lines. */
    val brainOnDevice: Boolean = false,
    /** Which languages the parent picked for Puki's brain (each has its own reader); null until they pick
     *  (then it's what's downloaded, else the game's language: DeviceModels.languagesFor). */
    val brainLanguages: BrainLanguages? = null,
    /** "Puki remembers between sessions": the day's games for the bedtime recap and the last days' moments for
     *  reaching back, kept on the device (DayLog, MomentMemory). Off: nothing outlives the session. */
    val rememberBetween: Boolean = false,
    /** After a game's question, Puki listens a few seconds for the answer said out loud. */
    val answersOutLoud: Boolean = true,
    /** Islands a parent took off the map (fewer choices at two), by id. */
    val hiddenIslands: Set<String> = emptySet(),
    /** Whose map the hub shows: the toddler islands, or the games for the last year of kindergarten. */
    val ageGroup: AgeGroup = AgeGroup.BIG,
    /** How fast Sounds and Words opens its newer letters and words (the BIG map only). */
    val contentPace: ContentPace = ContentPace.GRADUAL
)

/** The fork's two maps. LITTLE is the original game (ages 2-4); BIG is letters, reading and sums (5-6). */
enum class AgeGroup {
    LITTLE, BIG
}

/**
 * How fast the newer reading content opens. A "solid" item was asked in at least [sessions] sessions
 * with no remaining misses; new sound-shell consonants need [shellsSolid] of the original 16 sound items
 * solid, new Join Word words [joinSolid] of the original 12 words, new Read Sign words [readSolid] of the
 * original 9 clear words. With [prerequisites] a word also waits for its sounds to be met in Sound Shells
 * (and Read Sign for a clean Join Word encounter); without them everything is open from the first session.
 */
enum class ContentPace(
    val shellsSolid: Int,
    val joinSolid: Int,
    val readSolid: Int,
    val sessions: Int,
    val prerequisites: Boolean
) {
    GRADUAL(12, 8, 6, 2, true),
    FASTER(8, 6, 4, 1, true),
    ALL_NOW(0, 0, 0, 1, false);

    companion object {
        /** The stored name back to a pace; missing or unknown (an older or newer build wrote it) is GRADUAL. */
        fun fromStored(name: String?): ContentPace = name?.let { runCatching { valueOf(it) }.getOrNull() } ?: GRADUAL
    }
}

enum class Language {
    HEBREW, ENGLISH
}

enum class BrainLanguages(val hebrew: Boolean, val english: Boolean) {
    HEBREW(true, false), ENGLISH(false, true), BOTH(true, true)
}

enum class Gender {
    BOY, GIRL
}
