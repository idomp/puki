package io.github.tomsadeh.puki.data.content.big

/**
 * A letter of the Hebrew alphabet for the big kids' games: the 22 letters and the 5 final forms.
 * [voiceKey] is the "זאת האות X" clip (the first 19 reuse the toddler game's recordings).
 * Keys are written out in full on purpose, so scripts/check_voices.py finds each one.
 */
data class BigLetter(
    val id: String,
    val glyph: String,
    /** The name with nikud, for display. */
    val name: String,
    val voiceKey: String,
    /** For a final form: the id of the ordinary letter it belongs to. */
    val baseId: String? = null
) {
    val isFinal: Boolean get() = baseId != null
}

/** Two letters easily mistaken for each other, and the short line that tells them apart. */
data class LetterPair(
    val id: String,
    val a: String,
    val b: String,
    val compareKey: String,
    /** The same Hebrew as the voice line [compareKey] in scripts/voice_packs/letters.py. */
    val compareLine: String
)

object BigLetters {
    /** The 22 letters in alphabet order. The ids of the first 19 are the toddler game's. */
    val base: List<BigLetter> = listOf(
        BigLetter("alef", "א", "אָלֶף", "voice_letter_alef"),
        BigLetter("bet", "ב", "בֵּית", "voice_letter_bet"),
        BigLetter("gimel", "ג", "גִּימֶל", "voice_letter_gimel"),
        BigLetter("dalet", "ד", "דָּלֶת", "voice_letter_dalet"),
        BigLetter("he", "ה", "הֵא", "voice_letter_he"),
        BigLetter("vav", "ו", "וָו", "voice_letter_vav"),
        BigLetter("zayin", "ז", "זַיִן", "voice_big_letters_zayin"),
        BigLetter("het", "ח", "חֵית", "voice_letter_het"),
        BigLetter("tet", "ט", "טֵית", "voice_letter_tet"),
        BigLetter("yod", "י", "יוּד", "voice_letter_yod"),
        BigLetter("kaf", "כ", "כָּף", "voice_letter_kaf"),
        BigLetter("lamed", "ל", "לָמֶד", "voice_letter_lamed"),
        BigLetter("mem", "מ", "מֵם", "voice_letter_mem"),
        BigLetter("nun", "נ", "נוּן", "voice_letter_nun"),
        BigLetter("samekh", "ס", "סָמֶך", "voice_letter_samekh"),
        BigLetter("ayin", "ע", "עַיִן", "voice_letter_ayin"),
        BigLetter("pe", "פ", "פֵּא", "voice_letter_pe"),
        BigLetter("tsadi", "צ", "צַדִּי", "voice_big_letters_tsadi"),
        BigLetter("qof", "ק", "קוּף", "voice_letter_qof"),
        BigLetter("resh", "ר", "רֵישׁ", "voice_big_letters_resh"),
        BigLetter("shin", "ש", "שִׁין", "voice_letter_shin"),
        BigLetter("tav", "ת", "תָּו", "voice_letter_tav")
    )

    /** The five letters that change shape at the end of a word. */
    val finals: List<BigLetter> = listOf(
        BigLetter("kaf_final", "ך", "כָּף סוֹפִית", "voice_big_letters_final_kaf", baseId = "kaf"),
        BigLetter("mem_final", "ם", "מֵם סוֹפִית", "voice_big_letters_final_mem", baseId = "mem"),
        BigLetter("nun_final", "ן", "נוּן סוֹפִית", "voice_big_letters_final_nun", baseId = "nun"),
        BigLetter("pe_final", "ף", "פֵּא סוֹפִית", "voice_big_letters_final_pe", baseId = "pe"),
        BigLetter("tsadi_final", "ץ", "צַדִּי סוֹפִית", "voice_big_letters_final_tsadi", baseId = "tsadi")
    )

    /** All 27. */
    val all: List<BigLetter> = base + finals

    private val byId: Map<String, BigLetter> = all.associateBy { it.id }

    operator fun get(id: String): BigLetter = byId.getValue(id)
    fun find(id: String): BigLetter? = byId[id]

    /** The ordinary form of a letter or a final form. */
    fun baseOf(id: String): String = get(id).baseId ?: id

    /** The other half of a base/final pair (kaf <-> kaf_final); null for letters without a final form. */
    fun partnerOf(id: String): String? = finals.firstOrNull { it.baseId == id }?.id ?: get(id).baseId

    /**
     * The look-alike pairs. The first four are the ones the toddler game's table lacked or got wrong
     * (ד/ר, ה/ח, ב/כ, ו/י); ג/נ, ט/ס and ע/ש stay from that table (they are the classic
     * handwriting mix-ups: ג and נ both bend over a foot, ט and ס are both round, ע and ש are both
     * forks); ו/ז, ן/ו and ם/ס are new. The toddler table's English pairs (e/f, p/r ...) are not
     * Hebrew and are dropped.
     */
    val pairs: List<LetterPair> = listOf(
        LetterPair("dalet_resh", "dalet", "resh", "voice_big_letters_cmp_dalet_resh", "לדלת יש פינה, לריש יש קשת."),
        LetterPair("he_het", "he", "het", "voice_big_letters_cmp_he_het", "לחית שתי רגליים מחוברות, ולהא יש רגל קטנה שנפרדה."),
        LetterPair("bet_kaf", "bet", "kaf", "voice_big_letters_cmp_bet_kaf", "לבית יש פינה למטה, ולכף יש קשת עגולה."),
        LetterPair("vav_yod", "vav", "yod", "voice_big_letters_cmp_vav_yod", "לוו יש קו ארוך, וליוד רק פסיק קטן."),
        LetterPair("gimel_nun", "gimel", "nun", "voice_big_letters_cmp_gimel_nun", "לגימל יש רגל קטנה באלכסון, ולנון רצפה ישרה."),
        LetterPair("tet_samekh", "tet", "samekh", "voice_big_letters_cmp_tet_samekh", "לטית יש תלתל בפנים, ולסמך עיגול סגור."),
        LetterPair("ayin_shin", "ayin", "shin", "voice_big_letters_cmp_ayin_shin", "לשין שלושה ענפים, ולעין שני ענפים ורגל."),
        LetterPair("vav_zayin", "vav", "zayin", "voice_big_letters_cmp_vav_zayin", "לזיין יש כובע למעלה, ולוו אין."),
        LetterPair("nun_final_vav", "nun_final", "vav", "voice_big_letters_cmp_nun_final_vav", "לנון סופית יש רגל ארוכה שיורדת מתחת לשורה, ולוו אין."),
        LetterPair("mem_final_samekh", "mem_final", "samekh", "voice_big_letters_cmp_mem_final_samekh", "למם סופית יש פינות, ולסמך עיגול."),
        LetterPair("kaf_pe", "kaf", "pe", "voice_big_letters_cmp_kaf_pe", "לפא יש לשון קטנה בפנים, ולכף אין."),
        LetterPair("het_tav", "het", "tav", "voice_big_letters_cmp_het_tav", "לתו יש רגל שפונה החוצה, ולחית שתי רגליים ישרות.")
    )

    private val pairKeys: Set<Set<String>> = pairs.map { setOf(it.a, it.b) }.toSet()

    /** Pairs of letters that look a little alike but are not on the detective's list. */
    private val nearKeys: Set<Set<String>> = listOf(
        "gimel" to "zayin", "nun" to "zayin", "tet" to "mem", "ayin" to "tsadi", "shin" to "mem",
        "he" to "tav", "kaf_final" to "resh", "kaf_final" to "dalet", "kaf_final" to "nun_final",
        "nun_final" to "zayin", "pe_final" to "tsadi_final", "tsadi" to "tsadi_final", "qof" to "kaf", "qof" to "het"
    ).map { setOf(it.first, it.second) }.toSet()

    /** The pair that includes [id] and [other], if the detective has it. */
    fun pairOf(id: String, other: String): LetterPair? = pairs.firstOrNull { setOf(it.a, it.b) == setOf(id, other) }

    /** The look-alike partners of a letter (from [pairs]). */
    fun lookalikesOf(id: String): List<String> =
        pairs.mapNotNull { if (it.a == id) it.b else if (it.b == id) it.a else null }

    /** True for letters one would never put side by side as a question: a look-alike pair, or a letter and its own final form. */
    fun areConfusable(a: String, b: String): Boolean =
        a == b || setOf(a, b) in pairKeys || baseOf(a) == baseOf(b)

    /**
     * How different two letters look: 0 = confusable (a look-alike pair, or the same letter in its
     * final form, which also *sounds* the same), 1 = a bit alike, 2 = clearly different.
     */
    fun visualDistance(a: String, b: String): Int = when {
        areConfusable(a, b) -> 0
        setOf(a, b) in nearKeys -> 1
        else -> 2
    }
}
