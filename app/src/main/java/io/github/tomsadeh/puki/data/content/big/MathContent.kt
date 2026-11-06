package io.github.tomsadeh.puki.data.content.big

import io.github.tomsadeh.puki.data.models.BilingualText

/** Resource names keep this content usable by JVM engines without Android resource imports. */
enum class MathObject(val label: BilingualText, val drawableName: String?) {
    BIRD(BilingualText("ציפורים", "Birds"), "ic_count_bird"),
    APPLE(BilingualText("תפוחים", "Apples"), "ic_count_apple"),
    COUNTER(BilingualText("עיגולים", "Counters"), null)
}

data class MathLine(val key: String, val text: BilingualText)

private fun line(key: String, he: String, en: String, girl: String? = null) =
    MathLine(key, BilingualText(he, en, girl))

/** Every key is literal so check_voices.py can discover it. Numbers and nouns are never spliced. */
object MathContent {
    val birdStarts = listOf(
        line("voice_big_math_birds_start_0", "על הענף אין ציפורים.", "There are no birds on the branch."),
        line("voice_big_math_birds_start_1", "על הענף יש ציפור אחת.", "There is one bird on the branch."),
        line("voice_big_math_birds_start_2", "על הענף יש שתי ציפורים.", "There are two birds on the branch."),
        line("voice_big_math_birds_start_3", "על הענף יש שלוש ציפורים.", "There are three birds on the branch."),
        line("voice_big_math_birds_start_4", "על הענף יש ארבע ציפורים.", "There are four birds on the branch."),
        line("voice_big_math_birds_start_5", "על הענף יש חמש ציפורים.", "There are five birds on the branch."),
        line("voice_big_math_birds_start_6", "על הענף יש שש ציפורים.", "There are six birds on the branch."),
        line("voice_big_math_birds_start_7", "על הענף יש שבע ציפורים.", "There are seven birds on the branch."),
        line("voice_big_math_birds_start_8", "על הענף יש שמונה ציפורים.", "There are eight birds on the branch."),
        line("voice_big_math_birds_start_9", "על הענף יש תשע ציפורים.", "There are nine birds on the branch."),
        line("voice_big_math_birds_start_10", "על הענף יש עשר ציפורים.", "There are ten birds on the branch.")
    )
    val birdArrivals = listOf(
        line("voice_big_math_birds_join_0", "הפעם לא מגיעות עוד ציפורים.", "This time no more birds arrive."),
        line("voice_big_math_birds_join_1", "אל הענף מגיעה עוד ציפור אחת.", "One more bird arrives on the branch."),
        line("voice_big_math_birds_join_2", "אל הענף מגיעות עוד שתי ציפורים.", "Two more birds arrive on the branch."),
        line("voice_big_math_birds_join_3", "אל הענף מגיעות עוד שלוש ציפורים.", "Three more birds arrive on the branch."),
        line("voice_big_math_birds_join_4", "אל הענף מגיעות עוד ארבע ציפורים.", "Four more birds arrive on the branch."),
        line("voice_big_math_birds_join_5", "אל הענף מגיעות עוד חמש ציפורים.", "Five more birds arrive on the branch."),
        line("voice_big_math_birds_join_6", "אל הענף מגיעות עוד שש ציפורים.", "Six more birds arrive on the branch."),
        line("voice_big_math_birds_join_7", "אל הענף מגיעות עוד שבע ציפורים.", "Seven more birds arrive on the branch."),
        line("voice_big_math_birds_join_8", "אל הענף מגיעות עוד שמונה ציפורים.", "Eight more birds arrive on the branch."),
        line("voice_big_math_birds_join_9", "אל הענף מגיעות עוד תשע ציפורים.", "Nine more birds arrive on the branch."),
        line("voice_big_math_birds_join_10", "אל הענף מגיעות עוד עשר ציפורים.", "Ten more birds arrive on the branch.")
    )
    val birdDepartures = listOf(
        line("voice_big_math_birds_leave_0", "הפעם אף ציפור לא עפה מהענף.", "This time no birds fly away."),
        line("voice_big_math_birds_leave_1", "ציפור אחת עפה מהענף.", "One bird flies away from the branch."),
        line("voice_big_math_birds_leave_2", "שתי ציפורים עפות מהענף.", "Two birds fly away from the branch."),
        line("voice_big_math_birds_leave_3", "שלוש ציפורים עפות מהענף.", "Three birds fly away from the branch."),
        line("voice_big_math_birds_leave_4", "ארבע ציפורים עפות מהענף.", "Four birds fly away from the branch."),
        line("voice_big_math_birds_leave_5", "חמש ציפורים עפות מהענף.", "Five birds fly away from the branch."),
        line("voice_big_math_birds_leave_6", "שש ציפורים עפות מהענף.", "Six birds fly away from the branch."),
        line("voice_big_math_birds_leave_7", "שבע ציפורים עפות מהענף.", "Seven birds fly away from the branch."),
        line("voice_big_math_birds_leave_8", "שמונה ציפורים עפות מהענף.", "Eight birds fly away from the branch."),
        line("voice_big_math_birds_leave_9", "תשע ציפורים עפות מהענף.", "Nine birds fly away from the branch."),
        line("voice_big_math_birds_leave_10", "עשר ציפורים עפות מהענף.", "Ten birds fly away from the branch.")
    )
    val picnicTargets = listOf(
        line("voice_big_math_picnic_0", "הפעם השאר את הסל בלי תפוחים, ולחץ על מוכן.", "This time leave no apples in the basket, then press ready.", "הפעם השאירי את הסל בלי תפוחים, ולחצי על מוכן."),
        line("voice_big_math_picnic_1", "שים בסל תפוח אחד, ואז לחץ על מוכן.", "Put one apple in the basket, then press ready.", "שימי בסל תפוח אחד, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_2", "שים בסל שני תפוחים, ואז לחץ על מוכן.", "Put two apples in the basket, then press ready.", "שימי בסל שני תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_3", "שים בסל שלושה תפוחים, ואז לחץ על מוכן.", "Put three apples in the basket, then press ready.", "שימי בסל שלושה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_4", "שים בסל ארבעה תפוחים, ואז לחץ על מוכן.", "Put four apples in the basket, then press ready.", "שימי בסל ארבעה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_5", "שים בסל חמישה תפוחים, ואז לחץ על מוכן.", "Put five apples in the basket, then press ready.", "שימי בסל חמישה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_6", "שים בסל שישה תפוחים, ואז לחץ על מוכן.", "Put six apples in the basket, then press ready.", "שימי בסל שישה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_7", "שים בסל שבעה תפוחים, ואז לחץ על מוכן.", "Put seven apples in the basket, then press ready.", "שימי בסל שבעה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_8", "שים בסל שמונה תפוחים, ואז לחץ על מוכן.", "Put eight apples in the basket, then press ready.", "שימי בסל שמונה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_9", "שים בסל תשעה תפוחים, ואז לחץ על מוכן.", "Put nine apples in the basket, then press ready.", "שימי בסל תשעה תפוחים, ואז לחצי על מוכן."),
        line("voice_big_math_picnic_10", "שים בסל עשרה תפוחים, ואז לחץ על מוכן.", "Put ten apples in the basket, then press ready.", "שימי בסל עשרה תפוחים, ואז לחצי על מוכן.")
    )
    val picnicShown = listOf(
        line("voice_big_math_shown_0", "על המחצלת אין תפוחים.", "There are no apples on the mat."),
        line("voice_big_math_shown_1", "על המחצלת יש תפוח אחד.", "There is one apple on the mat."),
        line("voice_big_math_shown_2", "על המחצלת יש שני תפוחים.", "There are two apples on the mat."),
        line("voice_big_math_shown_3", "על המחצלת יש שלושה תפוחים.", "There are three apples on the mat."),
        line("voice_big_math_shown_4", "על המחצלת יש ארבעה תפוחים.", "There are four apples on the mat."),
        line("voice_big_math_shown_5", "על המחצלת יש חמישה תפוחים.", "There are five apples on the mat."),
        line("voice_big_math_shown_6", "על המחצלת יש שישה תפוחים.", "There are six apples on the mat."),
        line("voice_big_math_shown_7", "על המחצלת יש שבעה תפוחים.", "There are seven apples on the mat."),
        line("voice_big_math_shown_8", "על המחצלת יש שמונה תפוחים.", "There are eight apples on the mat."),
        line("voice_big_math_shown_9", "על המחצלת יש תשעה תפוחים.", "There are nine apples on the mat."),
        line("voice_big_math_shown_10", "על המחצלת יש עשרה תפוחים.", "There are ten apples on the mat.")
    )
    val frameFilled = listOf(
        line("voice_big_math_frame_0", "במסגרת אין עיגולים.", "There are no counters in the frame."),
        line("voice_big_math_frame_1", "במסגרת יש עיגול אחד.", "There is one counter in the frame."),
        line("voice_big_math_frame_2", "במסגרת יש שני עיגולים.", "There are two counters in the frame."),
        line("voice_big_math_frame_3", "במסגרת יש שלושה עיגולים.", "There are three counters in the frame."),
        line("voice_big_math_frame_4", "במסגרת יש ארבעה עיגולים.", "There are four counters in the frame."),
        line("voice_big_math_frame_5", "במסגרת יש חמישה עיגולים.", "There are five counters in the frame."),
        line("voice_big_math_frame_6", "במסגרת יש שישה עיגולים.", "There are six counters in the frame."),
        line("voice_big_math_frame_7", "במסגרת יש שבעה עיגולים.", "There are seven counters in the frame."),
        line("voice_big_math_frame_8", "במסגרת יש שמונה עיגולים.", "There are eight counters in the frame."),
        line("voice_big_math_frame_9", "במסגרת יש תשעה עיגולים.", "There are nine counters in the frame."),
        line("voice_big_math_frame_10", "במסגרת יש עשרה עיגולים.", "There are ten counters in the frame.")
    )
    val frameFive = line("voice_big_math_frame_five", "במסגרת יש מקום לחמישה עיגולים.", "The frame has room for five counters.")
    val frameTen = line("voice_big_math_frame_ten", "במסגרת יש מקום לעשרה עיגולים.", "The frame has room for ten counters.")
    val howMany = line("voice_big_math_how_many", "כמה יש עכשיו?", "How many are there now?")
    val howManyLeft = line("voice_big_math_how_many_left", "כמה נשארו?", "How many are left?")
    val howManyMissing = line("voice_big_math_how_many_missing", "כמה חסרים?", "How many more fill the frame?")
    val joinHint = line("voice_big_math_join_hint", "אפשר לספור שוב את כל הציפורים יחד.", "We can count all the birds together again.")
    val leaveHint = line("voice_big_math_leave_hint", "אפשר לספור שוב את הציפורים שנשארו על הענף.", "We can count the birds still on the branch again.")
    val frameHint = line("voice_big_math_frame_hint", "אפשר לספור את המקומות הריקים במסגרת.", "We can count the empty spaces in the frame.")
    val tooFew = line("voice_big_math_too_few", "עדיין חסרים תפוחים בסל. אפשר להוסיף עוד.", "The basket needs more apples. We can add some.")
    val tooMany = line("voice_big_math_too_many", "בסל יש יותר תפוחים ממה שצריך. אפשר להוציא כמה.", "The basket has more apples than we need. We can take some out.")
    val exact = line("voice_big_math_exact", "בדיוק!", "Exactly!")
    val oneMore = line("voice_big_math_one_more", "שים בסל תפוח אחד יותר מאשר על המחצלת, ואז לחץ על מוכן.", "Put one more apple in the basket than on the mat, then press ready.", "שימי בסל תפוח אחד יותר מאשר על המחצלת, ואז לחצי על מוכן.")
    val oneLess = line("voice_big_math_one_less", "שים בסל תפוח אחד פחות מאשר על המחצלת, ואז לחץ על מוכן.", "Put one fewer apple in the basket than on the mat, then press ready.", "שימי בסל תפוח אחד פחות מאשר על המחצלת, ואז לחצי על מוכן.")

    val allLines = birdStarts + birdArrivals + birdDepartures + picnicTargets + picnicShown + frameFilled +
        listOf(frameFive, frameTen, howMany, howManyLeft, howManyMissing, joinHint, leaveHint,
            frameHint, tooFew, tooMany, exact, oneMore, oneLess)
}
