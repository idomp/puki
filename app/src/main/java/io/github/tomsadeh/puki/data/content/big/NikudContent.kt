package io.github.tomsadeh.puki.data.content.big

/**
 * The content of the Sounds and Words island: two sounds (/a/ and /i/), consonants, the
 * syllables they make, and words split into reading units. Available voice keys are written out in full
 * so scripts/check_voices.py finds them. Pictures are referenced by id (the screen maps id -> drawable).
 */
object Nikud {
    const val DAGESH = "ּ"
    const val SHIN_DOT = "ׁ"
    const val PATAH = "ַ"
    const val KAMATS = "ָ"
    const val HIRIQ = "ִ"

    enum class Sound { A, I }

    /** A vowel mark. Kamatz and patach are two marks with one sound (and one clip). */
    enum class Mark(val vowel: String, val sound: Sound, val nameKey: String, val hebrewName: String) {
        PATAH(Nikud.PATAH, Sound.A, "voice_big_nikud_mark_patah", "פַּתָּח"),
        KAMATS(Nikud.KAMATS, Sound.A, "voice_big_nikud_mark_kamats", "קָמָץ"),
        HIRIQ(Nikud.HIRIQ, Sound.I, "voice_big_nikud_mark_hiriq", "חִירִיק")
    }

    /** A consonant: [bare] keeps its dots, no vowel; homophones share [pronunciation]. */
    data class Consonant(val id: String, val bare: String, val pronunciation: String = id)

    val introductoryConsonants: List<Consonant> = listOf(
        Consonant("mem", "מ"),
        Consonant("lamed", "ל"),
        Consonant("nun", "נ"),
        Consonant("shin", "ש$SHIN_DOT"),
        Consonant("gimel", "ג$DAGESH"),
        Consonant("dalet", "ד$DAGESH"),
        Consonant("yod", "י"),
        Consonant("pe", "פ$DAGESH")
    )

    val consonants: List<Consonant> = introductoryConsonants + listOf(
        Consonant("bet", "ב$DAGESH"),
        Consonant("resh", "ר"),
        Consonant("samekh", "ס", "s"),
        Consonant("kaf", "כ$DAGESH", "k"),
        Consonant("tav", "ת$DAGESH", "t")
    )

    fun consonant(id: String): Consonant = consonants.first { it.id == id }

    /** One consonant with one mark. Its [id] is "<consonant>_<mark>" (mem_patah); the clip is by sound. */
    data class Syllable(val consonant: Consonant, val mark: Mark) {
        val id: String get() = "${consonant.id}_${mark.name.lowercase()}"
        val text: String get() = consonant.bare + mark.vowel
        val sound: Sound get() = mark.sound
        /** Equivalent spellings share an audible identity even when their recordings differ. */
        val soundId: String get() = "${consonant.pronunciation}_${sound.name.lowercase()}"
        val clipKey: String get() = syllableClip(consonant.id, mark.sound)
    }

    fun syllable(consonantId: String, mark: Mark) = Syllable(consonant(consonantId), mark)

    /** "mem_patah" -> the syllable. */
    fun syllable(id: String): Syllable {
        val (c, m) = id.split('_')
        return syllable(c, Mark.valueOf(m.uppercase()))
    }

    /** The recording of a consonant with a sound; kamatz and patach share the /a/ one. */
    fun syllableClip(consonantId: String, sound: Sound): String = SYLLABLE_CLIPS.getValue("${consonantId}_${sound.name.lowercase()}")

    val SYLLABLE_CLIPS: Map<String, String> = mapOf(
        "mem_a" to "voice_big_nikud_mem_a", "mem_i" to "voice_big_nikud_mem_i",
        "lamed_a" to "voice_big_nikud_lamed_a", "lamed_i" to "voice_big_nikud_lamed_i",
        "nun_a" to "voice_big_nikud_nun_a", "nun_i" to "voice_big_nikud_nun_i",
        "shin_a" to "voice_big_nikud_shin_a", "shin_i" to "voice_big_nikud_shin_i",
        "gimel_a" to "voice_big_nikud_gimel_a", "gimel_i" to "voice_big_nikud_gimel_i",
        "dalet_a" to "voice_big_nikud_dalet_a", "dalet_i" to "voice_big_nikud_dalet_i",
        "yod_a" to "voice_big_nikud_yod_a", "yod_i" to "voice_big_nikud_yod_i",
        "pe_a" to "voice_big_nikud_pe_a", "pe_i" to "voice_big_nikud_pe_i",
        "bet_a" to "voice_big_nikud_bet_a", "bet_i" to "voice_big_nikud_bet_i",
        "resh_a" to "voice_big_nikud_resh_a", "resh_i" to "voice_big_nikud_resh_i",
        "samekh_a" to "voice_big_nikud_samekh_a", "samekh_i" to "voice_big_nikud_samekh_i",
        "kaf_a" to "voice_big_nikud_kaf_a", "kaf_i" to "voice_big_nikud_kaf_i",
        "tav_a" to "voice_big_nikud_tav_a", "tav_i" to "voice_big_nikud_tav_i"
    )

    enum class UnitKind {
        /** Consonant + vowel: has a clip. */
        CV,
        /** A closing consonant: silent, it only lights up. */
        CLOSING
    }

    /** One board: a CV sound, or a silent closing letter with an optional prerequisite letter family. */
    data class ReadingUnit(val text: String, val kind: UnitKind, val clipKey: String?, val consonant: String?, val sound: Sound?)

    private fun cv(consonantId: String, mark: Mark, dagesh: Boolean = false, suffix: String = ""): ReadingUnit {
        val c = consonant(consonantId)
        return ReadingUnit(c.bare + (if (dagesh) DAGESH else "") + mark.vowel + suffix, UnitKind.CV,
            syllableClip(consonantId, mark.sound), consonantId, mark.sound)
    }

    private fun closing(letter: String, consonantId: String? = null) = ReadingUnit(letter, UnitKind.CLOSING, null, consonantId, null)

    enum class Tier {
        /** A clear picture: any game, target or distractor. */
        CLEAR,
        /** A scene picture: only the target of "Puki joins a word". */
        SCENE,
        /** No picture step at all. */
        NONE
    }

    /** A word: [units] joined give [text] exactly. [pictureId] is null only when [tier] is NONE. */
    data class Word(val id: String, val units: List<ReadingUnit>, val pictureId: String?, val tier: Tier, val wordKey: String) {
        val text: String get() = units.joinToString("") { it.text }
        val firstConsonant: String get() = units.first().consonant!!
        val firstClip: String get() = units.first().clipKey!!
    }

    val introductoryWords: List<Word> = listOf(
        Word("dag", listOf(cv("dalet", Mark.KAMATS), closing("ג")), "fish", Tier.CLEAR, "voice_big_nikud_word_dag"),
        Word("pil", listOf(cv("pe", Mark.HIRIQ, suffix = "י"), closing("ל")), "elephant", Tier.CLEAR, "voice_big_nikud_word_pil"),
        Word("yad", listOf(cv("yod", Mark.KAMATS), closing("ד")), "hand", Tier.CLEAR, "voice_big_nikud_word_yad"),
        Word("mapa", listOf(cv("mem", Mark.PATAH), cv("pe", Mark.KAMATS, suffix = "ה")), "map", Tier.CLEAR, "voice_big_nikud_word_mapa"),
        Word("gina", listOf(cv("gimel", Mark.HIRIQ), cv("nun", Mark.KAMATS, dagesh = true, suffix = "ה")), "garden", Tier.CLEAR, "voice_big_nikud_word_gina"),
        Word("yam", listOf(cv("yod", Mark.KAMATS), closing("ם")), "sea", Tier.CLEAR, "voice_big_nikud_word_yam"),
        Word("gal", listOf(cv("gimel", Mark.PATAH), closing("ל")), "wave", Tier.CLEAR, "voice_big_nikud_word_gal"),
        Word("gan", listOf(cv("gimel", Mark.PATAH), closing("ן")), "kindergarten", Tier.CLEAR, "voice_big_nikud_word_gan"),
        Word("gag", listOf(cv("gimel", Mark.PATAH), closing("ג")), "roof", Tier.CLEAR, "voice_big_nikud_word_gag"),
        Word("mana", listOf(cv("mem", Mark.KAMATS), cv("nun", Mark.KAMATS, suffix = "ה")), "plate", Tier.SCENE, "voice_big_nikud_word_mana"),
        Word("pina", listOf(cv("pe", Mark.HIRIQ), cv("nun", Mark.KAMATS, dagesh = true, suffix = "ה")), "corner", Tier.SCENE, "voice_big_nikud_word_pina"),
        Word("mila", listOf(cv("mem", Mark.HIRIQ), cv("lamed", Mark.KAMATS, dagesh = true, suffix = "ה")), null, Tier.NONE, "voice_big_nikud_word_mila")
    )

    /** Remove an id only after its whole-word recording has been approved and added to the pack. */
    val pendingAudio: Set<String> = emptySet()

    val words: List<Word> = introductoryWords + listOf(
        Word("gamal", listOf(cv("gimel", Mark.KAMATS), cv("mem", Mark.KAMATS), closing("ל", "lamed")), "camel", Tier.CLEAR, "voice_big_nikud_word_gamal"),
        Word("kaf", listOf(cv("kaf", Mark.PATAH), closing("ף", "pe")), "spoon", Tier.CLEAR, "voice_big_nikud_word_kaf"),
        Word("sal", listOf(cv("samekh", Mark.PATAH), closing("ל", "lamed")), "basket", Tier.CLEAR, "voice_big_nikud_word_sal"),
        Word("sir", listOf(cv("samekh", Mark.HIRIQ, suffix = "י"), closing("ר", "resh")), "pot", Tier.CLEAR, "voice_big_nikud_word_sir"),
        Word("sira", listOf(cv("samekh", Mark.HIRIQ, suffix = "י"), cv("resh", Mark.KAMATS, suffix = "ה")), "boat", Tier.CLEAR, "voice_big_nikud_word_sira"),
        Word("panas", listOf(cv("pe", Mark.KAMATS), cv("nun", Mark.KAMATS), closing("ס", "samekh")), "flashlight", Tier.CLEAR, "voice_big_nikud_word_panas"),
        Word("banana", listOf(cv("bet", Mark.KAMATS), cv("nun", Mark.KAMATS), cv("nun", Mark.KAMATS, suffix = "ה")), "banana", Tier.CLEAR, "voice_big_nikud_word_banana"),
        Word("para", listOf(cv("pe", Mark.KAMATS), cv("resh", Mark.KAMATS, suffix = "ה")), "cow", Tier.CLEAR, "voice_big_nikud_word_para"),
        Word("tiras", listOf(cv("tav", Mark.HIRIQ, suffix = "י"), cv("resh", Mark.KAMATS), closing("ס", "samekh")), "corn", Tier.CLEAR, "voice_big_nikud_word_tiras")
    )

    private val wordsById = words.associateBy { it.id }
    fun word(id: String): Word = wordsById.getValue(id)

    /** The words that may be a picture distractor or a "Read the sign" word. */
    val clearWords: List<Word> = words.filter { it.tier == Tier.CLEAR && it.id !in pendingAudio }

    /** Pictures that look alike enough to be confused (never in one round). */
    val pictureTwins: Set<Set<String>> = setOf(setOf("wave", "sea"), setOf("kindergarten", "garden"))

    fun areTwins(a: Word, b: Word): Boolean = a.pictureId != null && b.pictureId != null &&
        (a.pictureId == b.pictureId || setOf(a.pictureId, b.pictureId) in pictureTwins)

    /** Voice lines of the instructions, written out in full. */
    object Voice {
        const val LISTEN = "voice_big_nikud_listen"
        const val WHICH_SOUND = "voice_big_nikud_which_sound"
        const val JOIN = "voice_big_nikud_join"
        const val PICK_PICTURE = "voice_big_nikud_pick_picture"
        const val READ_SIGN = "voice_big_nikud_read_sign"
        const val TWO_MARKS = "voice_big_nikud_two_marks"
    }
}
