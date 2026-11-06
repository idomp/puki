package io.github.tomsadeh.puki.data.content.big

/**
 * The content of the Sounds and Words island: three sounds (/a/, /i/ and /u/), consonants, the
 * syllables they make, and words split into reading units. Available voice keys are written out in full
 * so scripts/check_voices.py finds them. Pictures are referenced by id (the screen maps id -> drawable).
 */
object Nikud {
    const val DAGESH = "ּ"
    const val SHIN_DOT = "ׁ"
    const val PATAH = "ַ"
    const val KAMATS = "ָ"
    const val HIRIQ = "ִ"

    /** Kubutz: three diagonal dots under the consonant. */
    const val KUBUTZ = "ֻ"
    /** Shuruk: a vav with a dagesh after the consonant. The vav belongs to the reading unit, like hiriq's yod. */
    const val SHURUK = "וּ"

    enum class Sound { A, I, U }

    /** The sounds the island taught first. /u/ opens only after their material (see NikudEligibility). */
    val aiSounds: List<Sound> = listOf(Sound.A, Sound.I)

    /** A vowel mark. Kamatz and patach are two marks with one sound (and one clip); so are shuruk and kubutz. */
    enum class Mark(val vowel: String, val sound: Sound, val nameKey: String, val hebrewName: String) {
        PATAH(Nikud.PATAH, Sound.A, "voice_big_nikud_mark_patah", "פַּתָּח"),
        KAMATS(Nikud.KAMATS, Sound.A, "voice_big_nikud_mark_kamats", "קָמָץ"),
        HIRIQ(Nikud.HIRIQ, Sound.I, "voice_big_nikud_mark_hiriq", "חִירִיק"),
        SHURUK(Nikud.SHURUK, Sound.U, "voice_big_nikud_mark_shuruk", "שׁוּרוּק"),
        KUBUTZ(Nikud.KUBUTZ, Sound.U, "voice_big_nikud_mark_kubutz", "קֻבּוּץ")
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

    /** The recording of a consonant with a sound; kamatz and patach share the /a/ one, shuruk and kubutz the /u/ one. */
    fun syllableClip(consonantId: String, sound: Sound): String = SYLLABLE_CLIPS.getValue("${consonantId}_${sound.name.lowercase()}")

    val SYLLABLE_CLIPS: Map<String, String> = mapOf(
        "mem_a" to "voice_big_nikud_mem_a", "mem_i" to "voice_big_nikud_mem_i", "mem_u" to "voice_big_nikud_mem_u",
        "lamed_a" to "voice_big_nikud_lamed_a", "lamed_i" to "voice_big_nikud_lamed_i", "lamed_u" to "voice_big_nikud_lamed_u",
        "nun_a" to "voice_big_nikud_nun_a", "nun_i" to "voice_big_nikud_nun_i", "nun_u" to "voice_big_nikud_nun_u",
        "shin_a" to "voice_big_nikud_shin_a", "shin_i" to "voice_big_nikud_shin_i", "shin_u" to "voice_big_nikud_shin_u",
        "gimel_a" to "voice_big_nikud_gimel_a", "gimel_i" to "voice_big_nikud_gimel_i", "gimel_u" to "voice_big_nikud_gimel_u",
        "dalet_a" to "voice_big_nikud_dalet_a", "dalet_i" to "voice_big_nikud_dalet_i", "dalet_u" to "voice_big_nikud_dalet_u",
        "yod_a" to "voice_big_nikud_yod_a", "yod_i" to "voice_big_nikud_yod_i", "yod_u" to "voice_big_nikud_yod_u",
        "pe_a" to "voice_big_nikud_pe_a", "pe_i" to "voice_big_nikud_pe_i", "pe_u" to "voice_big_nikud_pe_u",
        "bet_a" to "voice_big_nikud_bet_a", "bet_i" to "voice_big_nikud_bet_i", "bet_u" to "voice_big_nikud_bet_u",
        "resh_a" to "voice_big_nikud_resh_a", "resh_i" to "voice_big_nikud_resh_i", "resh_u" to "voice_big_nikud_resh_u",
        "samekh_a" to "voice_big_nikud_samekh_a", "samekh_i" to "voice_big_nikud_samekh_i", "samekh_u" to "voice_big_nikud_samekh_u",
        "kaf_a" to "voice_big_nikud_kaf_a", "kaf_i" to "voice_big_nikud_kaf_i", "kaf_u" to "voice_big_nikud_kaf_u",
        "tav_a" to "voice_big_nikud_tav_a", "tav_i" to "voice_big_nikud_tav_i", "tav_u" to "voice_big_nikud_tav_u"
    )

    enum class UnitKind {
        /** Consonant + vowel: has a clip. */
        CV,
        /** A closing consonant: silent, it only lights up. */
        CLOSING
    }

    /** One board: a CV sound, or a silent closing letter with an optional prerequisite letter family. */
    data class ReadingUnit(val text: String, val kind: UnitKind, val clipKey: String?, val consonant: String?, val sound: Sound?,
                           val mark: Mark? = null)

    private fun cv(consonantId: String, mark: Mark, dagesh: Boolean = false, suffix: String = ""): ReadingUnit {
        val c = consonant(consonantId)
        return ReadingUnit(c.bare + (if (dagesh) DAGESH else "") + mark.vowel + suffix, UnitKind.CV,
            syllableClip(consonantId, mark.sound), consonantId, mark.sound, mark)
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
        /** A /u/ word: it opens only after the a/i material. */
        val hasU: Boolean get() = units.any { it.sound == Sound.U }
        /** Written with kubutz: it waits until the second /u/ mark has been introduced. */
        val usesKubutz: Boolean get() = units.any { it.mark == Mark.KUBUTZ }
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

    /** The /a/ and /i/ words: the material /u/ opens after. */
    val aiWords: List<Word> = introductoryWords + listOf(
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

    /** The /u/ words: shuruk first (its vav is inside the unit), then the two kubutz words. */
    val uWords: List<Word> = listOf(
        Word("sus", listOf(cv("samekh", Mark.SHURUK), closing("ס", "samekh")), "horse", Tier.CLEAR, "voice_big_nikud_word_sus"),
        Word("kadur", listOf(cv("kaf", Mark.PATAH), cv("dalet", Mark.SHURUK), closing("ר", "resh")), "ball", Tier.CLEAR, "voice_big_nikud_word_kadur"),
        Word("tut", listOf(cv("tav", Mark.SHURUK), closing("ת", "tav")), "strawberry", Tier.CLEAR, "voice_big_nikud_word_tut"),
        Word("dubi", listOf(cv("dalet", Mark.KUBUTZ), cv("bet", Mark.HIRIQ, suffix = "י")), "teddy", Tier.CLEAR, "voice_big_nikud_word_dubi"),
        Word("buba", listOf(cv("bet", Mark.KUBUTZ), cv("bet", Mark.KAMATS, suffix = "ה")), "doll", Tier.CLEAR, "voice_big_nikud_word_buba")
    )

    val words: List<Word> = aiWords + uWords

    private val wordsById = words.associateBy { it.id }
    fun word(id: String): Word = wordsById.getValue(id)

    /** The words that may be a picture distractor or a "Read the sign" word. */
    val clearWords: List<Word> = words.filter { it.tier == Tier.CLEAR && it.id !in pendingAudio }

    /** Pictures that look alike or can reasonably name the same thing (never in one round). */
    val pictureTwins: Set<Set<String>> = setOf(
        setOf("wave", "sea"),
        setOf("kindergarten", "garden"),
        setOf("spoon", "hand"), // kaf also means the palm of a hand
        setOf("sea", "boat"), // the sea picture includes a sailboat
        setOf("roof", "kindergarten"), // the kindergarten picture includes a prominent roof
        setOf("teddy", "doll"), // a child may call the teddy a doll (בובה), and the teddy is דֻּבִּי
        setOf("ball", "corner") // the corner scene has a ball in it, and the ball is כַּדּוּר
    )

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
