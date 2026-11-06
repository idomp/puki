package io.github.tomsadeh.puki.ui.big.nikud

import io.github.tomsadeh.puki.R

/** The same picture follows a word from joining to silent reading. */
internal fun nikudPicture(id: String): Int = when (id) {
    "hand" -> R.drawable.ic_nikud_yad
    "map" -> R.drawable.ic_nikud_mapa
    "garden" -> R.drawable.ic_nikud_gina
    "sea" -> R.drawable.ic_nikud_yam
    "wave" -> R.drawable.ic_nikud_gal
    "kindergarten" -> R.drawable.ic_nikud_gan
    "roof" -> R.drawable.ic_nikud_gag
    "plate" -> R.drawable.ic_nikud_mana
    "corner" -> R.drawable.ic_nikud_pina
    "fish" -> R.drawable.ic_food_fish
    "elephant" -> R.drawable.ic_animal_elephant
    "camel" -> R.drawable.ic_word_camel
    "spoon" -> R.drawable.ic_table_spoon
    "basket" -> R.drawable.ic_nikud_sal
    "pot" -> R.drawable.ic_nikud_sir
    "boat" -> R.drawable.ic_nikud_sira
    "flashlight" -> R.drawable.ic_nikud_panas
    "banana" -> R.drawable.ic_food_banana
    "cow" -> R.drawable.ic_animal_cow
    "corn" -> R.drawable.ic_food_corn
    "horse" -> R.drawable.ic_animal_horse
    "ball" -> R.drawable.ic_word_ball
    "teddy" -> R.drawable.ic_tidy_teddy
    "strawberry" -> R.drawable.ic_nikud_tut
    "doll" -> R.drawable.ic_nikud_buba
    else -> error("Unknown nikud picture: $id")
}
