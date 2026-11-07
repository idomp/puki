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
    val hideArrive = line("voice_big_math_hide_arrive", "עוד ציפורים הגיעו והתחבאו בין העלים.", "More birds arrived and hid among the leaves.")
    val hideAsk = line("voice_big_math_hide_ask", "כמה ציפורים התחבאו?", "How many birds hid?")
    val hideStart = line("voice_big_math_hide_start", "על הענף ישבו ציפורים, והן מתחבאות בין העלים.", "Birds were sitting on the branch, hiding among the leaves.")
    val hideAskStart = line("voice_big_math_hide_ask_start", "כמה ציפורים היו על הענף בהתחלה?", "How many birds were on the branch at the start?")
    val hideHint = line("voice_big_math_hide_hint", "בוא נתחיל מהציפורים שרואים ונספור הלאה.", "Let's start with the birds we can see and count on.", "בואי נתחיל מהציפורים שרואים ונספור הלאה.")
    val birdsNow = mapOf(
        2 to line("voice_big_math_birds_now_2", "עכשיו יש על הענף שתי ציפורים.", "Now there are two birds on the branch."),
        3 to line("voice_big_math_birds_now_3", "עכשיו יש על הענף שלוש ציפורים.", "Now there are three birds on the branch."),
        4 to line("voice_big_math_birds_now_4", "עכשיו יש על הענף ארבע ציפורים.", "Now there are four birds on the branch."),
        5 to line("voice_big_math_birds_now_5", "עכשיו יש על הענף חמש ציפורים.", "Now there are five birds on the branch."),
        6 to line("voice_big_math_birds_now_6", "עכשיו יש על הענף שש ציפורים.", "Now there are six birds on the branch."),
        7 to line("voice_big_math_birds_now_7", "עכשיו יש על הענף שבע ציפורים.", "Now there are seven birds on the branch."),
        8 to line("voice_big_math_birds_now_8", "עכשיו יש על הענף שמונה ציפורים.", "Now there are eight birds on the branch."),
        9 to line("voice_big_math_birds_now_9", "עכשיו יש על הענף תשע ציפורים.", "Now there are nine birds on the branch."),
        10 to line("voice_big_math_birds_now_10", "עכשיו יש על הענף עשר ציפורים.", "Now there are ten birds on the branch.")
    )
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
        line("voice_big_math_birds_join_1", "עוד ציפור אחת מגיעה לענף.", "One more bird arrives on the branch."),
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
        line("voice_big_math_birds_leave_0", "כל הציפורים נשארות על הענף.", "All the birds stay on the branch."),
        line("voice_big_math_birds_leave_1", "ציפור אחת עוזבת את הענף.", "One bird leaves the branch."),
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
        line("voice_big_math_picnic_0", "הסל צריך להישאר ריק.", "The basket should stay empty."),
        line("voice_big_math_picnic_1", "תשים בסל תפוח אחד.", "Put one apple in the basket.", "תשימי בסל תפוח אחד."),
        line("voice_big_math_picnic_2", "תשים בסל שני תפוחים.", "Put two apples in the basket.", "תשימי בסל שני תפוחים."),
        line("voice_big_math_picnic_3", "תשים בסל שלושה תפוחים.", "Put three apples in the basket.", "תשימי בסל שלושה תפוחים."),
        line("voice_big_math_picnic_4", "תשים בסל ארבעה תפוחים.", "Put four apples in the basket.", "תשימי בסל ארבעה תפוחים."),
        line("voice_big_math_picnic_5", "תשים בסל חמישה תפוחים.", "Put five apples in the basket.", "תשימי בסל חמישה תפוחים."),
        line("voice_big_math_picnic_6", "תשים בסל שישה תפוחים.", "Put six apples in the basket.", "תשימי בסל שישה תפוחים."),
        line("voice_big_math_picnic_7", "תשים בסל שבעה תפוחים.", "Put seven apples in the basket.", "תשימי בסל שבעה תפוחים."),
        line("voice_big_math_picnic_8", "תשים בסל שמונה תפוחים.", "Put eight apples in the basket.", "תשימי בסל שמונה תפוחים."),
        line("voice_big_math_picnic_9", "תשים בסל תשעה תפוחים.", "Put nine apples in the basket.", "תשימי בסל תשעה תפוחים."),
        line("voice_big_math_picnic_10", "תשים בסל עשרה תפוחים.", "Put ten apples in the basket.", "תשימי בסל עשרה תפוחים.")
    )
    val picnicAlready = line("voice_big_math_picnic_already", "בַּסַּל כְּבָר יֵשׁ תַּפּוּחִים.", "There are already apples in the basket.")
    val picnicTotals = listOf(
        picnicTargets[0],
        line("voice_big_math_picnic_target_1", "בַּסַּל צָרִיךְ לִהְיוֹת תַּפּוּחַ אֶחָד.", "There should be one apple in the basket."),
        line("voice_big_math_picnic_target_2", "בַּסַּל צְרִיכִים לִהְיוֹת שְׁנֵי תַּפּוּחִים.", "There should be two apples in the basket."),
        line("voice_big_math_picnic_target_3", "בַּסַּל צְרִיכִים לִהְיוֹת שְׁלוֹשָׁה תַּפּוּחִים.", "There should be three apples in the basket."),
        line("voice_big_math_picnic_target_4", "בַּסַּל צְרִיכִים לִהְיוֹת אַרְבָּעָה תַּפּוּחִים.", "There should be four apples in the basket."),
        line("voice_big_math_picnic_target_5", "בַּסַּל צְרִיכִים לִהְיוֹת חֲמִשָּׁה תַּפּוּחִים.", "There should be five apples in the basket."),
        line("voice_big_math_picnic_target_6", "בַּסַּל צְרִיכִים לִהְיוֹת שִׁשָּׁה תַּפּוּחִים.", "There should be six apples in the basket."),
        line("voice_big_math_picnic_target_7", "בַּסַּל צְרִיכִים לִהְיוֹת שִׁבְעָה תַּפּוּחִים.", "There should be seven apples in the basket."),
        line("voice_big_math_picnic_target_8", "בַּסַּל צְרִיכִים לִהְיוֹת שְׁמוֹנָה תַּפּוּחִים.", "There should be eight apples in the basket."),
        line("voice_big_math_picnic_target_9", "בַּסַּל צְרִיכִים לִהְיוֹת תִּשְׁעָה תַּפּוּחִים.", "There should be nine apples in the basket."),
        line("voice_big_math_picnic_target_10", "בַּסַּל צְרִיכִים לִהְיוֹת עֲשָׂרָה תַּפּוּחִים.", "There should be ten apples in the basket.")
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
        line("voice_big_math_frame_0", "המסגרת ריקה, אין בה עיגולים.", "The frame is empty, there are no counters in it."),
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
    val frameTargets = mapOf(
        2 to line("voice_big_math_frame_target_2", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת שְׁנֵי עִיגּוּלִים.", "There should be two counters in the frame."),
        3 to line("voice_big_math_frame_target_3", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת שְׁלוֹשָׁה עִיגּוּלִים.", "There should be three counters in the frame."),
        4 to line("voice_big_math_frame_target_4", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת אַרְבָּעָה עִיגּוּלִים.", "There should be four counters in the frame."),
        5 to line("voice_big_math_frame_target_5", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת חֲמִשָּׁה עִיגּוּלִים.", "There should be five counters in the frame."),
        6 to line("voice_big_math_frame_target_6", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת שִׁשָּׁה עִיגּוּלִים.", "There should be six counters in the frame."),
        7 to line("voice_big_math_frame_target_7", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת שִׁבְעָה עִיגּוּלִים.", "There should be seven counters in the frame."),
        8 to line("voice_big_math_frame_target_8", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת שְׁמוֹנָה עִיגּוּלִים.", "There should be eight counters in the frame."),
        9 to line("voice_big_math_frame_target_9", "בַּמִּסְגֶּרֶת צְרִיכִים לִהְיוֹת תִּשְׁעָה עִיגּוּלִים.", "There should be nine counters in the frame.")
    )
    val frameFive = line("voice_big_math_frame_five", "במסגרת יש מקום לחמישה עיגולים.", "The frame has room for five counters.")
    val frameTen = line("voice_big_math_frame_ten", "במסגרת יש מקום לעשרה עיגולים.", "The frame has room for ten counters.")
    val howMany = line("voice_big_math_how_many", "כמה ציפורים יש עכשיו?", "How many birds are there now?")
    val howManyLeft = line("voice_big_math_how_many_left", "כמה נשארו?", "How many are left?")
    val howManyMissing = line("voice_big_math_how_many_missing", "כמה חסרים?", "How many more fill the frame?")
    val joinHint = line("voice_big_math_join_hint", "אפשר לספור שוב את כל הציפורים יחד.", "We can count all the birds together again.")
    val leaveHint = line("voice_big_math_leave_hint", "בוא נספור שוב כמה ציפורים נשארו על הענף.", "Let's count again how many birds are left on the branch.", "בואי נספור שוב כמה ציפורים נשארו על הענף.")
    val frameHint = line("voice_big_math_frame_hint", "בוא נספור את המקומות הריקים במסגרת.", "Let's count the empty spaces in the frame.", "בואי נספור את המקומות הריקים במסגרת.")
    val tooFew = line("voice_big_math_too_few", "עדיין חסרים תפוחים בסל. אפשר להוסיף עוד.", "The basket needs more apples. We can add some.")
    val tooMany = line("voice_big_math_too_many", "בסל יש יותר תפוחים ממה שצריך. אפשר להוציא כמה.", "The basket has more apples than we need. We can take some out.")
    val exact = line("voice_big_math_exact", "בדיוק!", "Exactly!")
    val pressReady = line("voice_big_math_press_ready", "ואז תלחץ על מוכן.", "Then press ready.", "ואז תלחצי על מוכן.")
    val oneMore = line("voice_big_math_one_more", "תשים בסל תפוח אחד יותר ממה שיש על המחצלת.", "Put one apple more than on the mat in the basket.", "תשימי בסל תפוח אחד יותר ממה שיש על המחצלת.")
    val oneLess = line("voice_big_math_one_less", "תשים בסל תפוח אחד פחות ממה שיש על המחצלת.", "Put one apple fewer than on the mat in the basket.", "תשימי בסל תפוח אחד פחות ממה שיש על המחצלת.")

    val allLines = birdsNow.values + listOf(hideArrive, hideAsk, hideStart, hideAskStart, hideHint) +
        birdStarts + birdArrivals + birdDepartures + picnicTargets + picnicTotals.drop(1) + picnicShown + frameFilled + frameTargets.values +
        listOf(picnicAlready, frameFive, frameTen, howMany, howManyLeft, howManyMissing, joinHint, leaveHint,
            frameHint, tooFew, tooMany, exact, pressReady, oneMore, oneLess)
}
