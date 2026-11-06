"""Voice lines for the big kids' letters games (ui/big/letters/). Keys start with voice_big_letters.

The games are Hebrew only, so the English column is "" (the app never plays these in English).
The 19 letters the toddler game already names reuse its voice_letter_* clips ("זאת האות X");
the three missing letters and the five final forms are here.
"""

VOICE_LINES = [
    ("voice_island_letters", "Letters", "אותיות."),

    # --- The letters the toddler game lacks, and the final forms: "this is the letter X" ---
    ("voice_big_letters_zayin", "", "זאת האות זיין."),
    ("voice_big_letters_tsadi", "", "זאת האות צדי."),
    ("voice_big_letters_resh", "", "זאת האות ריש."),
    ("voice_big_letters_final_kaf", "", "זאת האות כף סופית."),
    ("voice_big_letters_final_mem", "", "זאת האות מם סופית."),
    ("voice_big_letters_final_nun", "", "זאת האות נון סופית."),
    ("voice_big_letters_final_pe", "", "זאת האות פא סופית."),
    ("voice_big_letters_final_tsadi", "", "זאת האות צדי סופית."),

    # --- Listen and find: "<letter clip>. Find it!" ---
    ("voice_big_letters_listen_intro", "",
     ("תקשיבי טוב, ותמצאי את האות!", "תקשיב טוב, ותמצא את האות!")),
    ("voice_big_letters_find_it", "", ("תמצאי אותה!", "תמצא אותה!")),
    # after a wrong tap: "<tapped letter clip>. We were looking for another letter, listen: <target clip>"
    ("voice_big_letters_looking_for", "", ("חיפשנו אות אחרת. תקשיבי:", "חיפשנו אות אחרת. תקשיב:")),
    # after two misses, as the right tile glows
    ("voice_big_letters_hint", "", ("תסתכלי, הנה היא!", "תסתכל, הנה היא!")),

    # --- Letter detective ---
    ("voice_big_letters_detective_intro", "",
     ("בואי נהיה בלשים של אותיות! נסתכל על שתי אותיות שדומות.",
      "בוא נהיה בלשים של אותיות! נסתכל על שתי אותיות שדומות.")),
    # the compare step plays <clip A>, <clip B>, then the line that tells them apart
    ("voice_big_letters_cmp_dalet_resh", "", "לדלת יש פינה, לריש יש קשת."),
    ("voice_big_letters_cmp_he_het", "", "לחית שתי רגליים מחוברות, ולהא יש רגל קטנה שנפרדה."),
    ("voice_big_letters_cmp_bet_kaf", "", "לבית יש פינה למטה, ולכף יש קשת עגולה."),
    ("voice_big_letters_cmp_vav_yod", "", "לוו יש קו ארוך, וליוד רק פסיק קטן."),
    ("voice_big_letters_cmp_gimel_nun", "", "לגימל יש רגל קטנה באלכסון, ולנון רצפה ישרה."),
    ("voice_big_letters_cmp_tet_samekh", "", "לטית יש תלתל בפנים, ולסמך עיגול סגור."),
    ("voice_big_letters_cmp_ayin_shin", "", "לשין שלושה ענפים, ולעין שני ענפים ורגל."),
    ("voice_big_letters_cmp_vav_zayin", "", "לזיין יש כובע למעלה, ולוו אין."),
    ("voice_big_letters_cmp_nun_final_vav", "", "לנון סופית יש רגל ארוכה שיורדת מתחת לשורה, ולוו אין."),
    ("voice_big_letters_cmp_mem_final_samekh", "", "למם סופית יש פינות, ולסמך עיגול."),

    # --- Final-form partners ---
    ("voice_big_letters_partner_intro", "",
     ("יש אותיות שמשתנות כשהן בסוף המילה. בואי נמצא את הזוגות!",
      "יש אותיות שמשתנות כשהן בסוף המילה. בוא נמצא את הזוגות!")),
    ("voice_big_letters_partner_ask", "", ("תמצאי לה זוג!", "תמצא לה זוג!")),
    ("voice_big_letters_partner_not_friend", "",
     ("אבל הן לא זוג. תנסי שוב!", "אבל הן לא זוג. תנסה שוב!")),
]

# Exact IPA where plain spelling is ambiguous for the TTS: letter names, "ל" + a letter name
# (לבית would be read "to the house"), and short gendered lines. Keyed by output stem.
PHONEMES = {
    "voice_big_letters_zayin_he": "zˈot haʔˈot zˈajin.",
    "voice_big_letters_tsadi_he": "zˈot haʔˈot tsˈadi.",
    "voice_big_letters_resh_he": "zˈot haʔˈot ʁˈeʃ.",
    "voice_big_letters_final_kaf_he": "zˈot haʔˈot kˈaf sofˈit.",
    "voice_big_letters_final_mem_he": "zˈot haʔˈot mˈem sofˈit.",
    "voice_big_letters_final_nun_he": "zˈot haʔˈot nˈun sofˈit.",
    "voice_big_letters_final_pe_he": "zˈot haʔˈot pˈe sofˈit.",
    "voice_big_letters_final_tsadi_he": "zˈot haʔˈot tsˈadi sofˈit.",

    "voice_big_letters_find_it_he_f": "timtsˈi ʔotˈa!",
    "voice_big_letters_find_it_he_m": "timtsˈa ʔotˈa!",

    "voice_big_letters_cmp_dalet_resh_he": "ledˈalet jˈeʃ pinˈa, leʁˈeʃ jˈeʃ kˈeʃet.",
    "voice_big_letters_cmp_he_het_he": "leχˈet ʃtˈej ʁaɡlˈajim meχubaʁˈot, ulehˈe jˈeʃ ʁˈeɡel ktanˈa ʃenifʁedˈa.",
    "voice_big_letters_cmp_bet_kaf_he": "lebˈet jˈeʃ pinˈa lemˈata, ulekˈaf jˈeʃ kˈeʃet ʔaɡulˈa.",
    "voice_big_letters_cmp_vav_yod_he": "levˈav jˈeʃ kav ʔaʁˈoχ, ulijˈud ʁˈak psˈik katˈan.",
    "voice_big_letters_cmp_gimel_nun_he": "leɡˈimel jˈeʃ ʁˈeɡel ktanˈa beʔalaχsˈon, ulenˈun ʁitspˈa jeʃaʁˈa.",
    "voice_big_letters_cmp_tet_samekh_he": "letˈet jˈeʃ taltˈal bifnˈim, ulesˈameχ ʔiɡˈul saɡˈuʁ.",
    "voice_big_letters_cmp_ayin_shin_he": "leʃˈin ʃloʃˈa ʔanafˈim, uleʔˈajin ʃnˈej ʔanafˈim veʁˈeɡel.",
    "voice_big_letters_cmp_vav_zayin_he": "lezˈajin jˈeʃ kˈova lemˈala, ulevˈav ʔˈejn.",
    "voice_big_letters_cmp_nun_final_vav_he": "lenˈun sofˈit jˈeʃ ʁˈeɡel ʔaʁukˈa ʃejoʁˈedet mitˈaχat laʃuʁˈa, ulevˈav ʔˈejn.",
    "voice_big_letters_cmp_mem_final_samekh_he": "lemˈem sofˈit jˈeʃ pinˈot, ulesˈameχ ʔiɡˈul.",
}

# Set by ear (the parent's review, 2026-11-06): these two came out too fast at the packs' 0.7
SPEEDS = {
    "voice_big_letters_final_pe_he": 0.6,
    "voice_big_letters_looking_for_he_f": 0.6,
    "voice_big_letters_looking_for_he_m": 0.6,
}
