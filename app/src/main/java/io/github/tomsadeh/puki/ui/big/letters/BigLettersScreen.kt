package io.github.tomsadeh.puki.ui.big.letters

import io.github.tomsadeh.puki.ui.big.kit.ReadingIsland
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.ui.big.kit.ReadingScreenModels
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.SpeakerIcon
import io.github.tomsadeh.puki.ui.components.Door
import io.github.tomsadeh.puki.ui.components.DoorChooserScreen
import io.github.tomsadeh.puki.ui.components.Scene

private const val LISTEN = "listen"
private const val DETECTIVE = "detective"
private const val PARTNERS = "partners"

/**
 * The big kids' letters island: three doors (listen and find, the letter detective, final-form
 * partners), then the chosen game in the same composable. Hebrew only, so the whole island reads
 * right to left.
 */
@Composable
fun BigLettersScreen(onBack: () -> Unit) {
    val models: ReadingScreenModels = viewModel()
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    var visit by rememberSaveable { mutableIntStateOf(0) }
    val toDoors = { models.reset(); game = null }
    val choose: (String) -> Unit = { models.reset(); game = it }
    val again: () -> Unit = { models.reset(); visit++ }
    ReadingIsland {
        BackHandler(enabled = game != null, onBack = toDoors)
        key(game, visit) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides models) {
                when (game) {
                    LISTEN -> ListenFindRound(onBack = toDoors, onAgain = again)
                    DETECTIVE -> LetterDetectiveRound(onBack = toDoors, onAgain = again)
                    PARTNERS -> FinalPartnersRound(onBack = toDoors, onAgain = again)
                    else -> DoorChooserScreen(
                        onBack = onBack,
                        scene = Scene.PLAYGROUND,
                        doors = listOf(
                            Door(BilingualText("שמע ומצא", "Listen and find"), BigColors.Coral, { choose(LISTEN) }) { ListenPicture() },
                            Door(BilingualText("בלש אותיות", "Letter detective"), BigColors.Blue, { choose(DETECTIVE) }) { DetectivePicture() },
                            Door(BilingualText("חברות סופיות", "Final-form partners"), Color(0xFF6CC24A), { choose(PARTNERS) }) { PartnersPicture() }
                        )
                    )
                }
            }
        }
    }
}

/** A big white speaker: Puki says it, you find it. */
@Composable
private fun BoxScope.ListenPicture() {
    SpeakerIcon(size = 120.dp, modifier = Modifier.align(Alignment.Center))
}

/** ד and ר under a magnifying glass. */
@Composable
private fun BoxScope.DetectivePicture() {
    Text("דר", fontSize = 84.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.align(Alignment.Center))
    Canvas(Modifier.size(150.dp).align(Alignment.Center)) {
        val r = size.width * 0.34f
        val c = Offset(size.width * 0.46f, size.height * 0.44f)
        drawCircle(Color.White.copy(alpha = 0.25f), r, c)
        drawCircle(BigColors.Ink, r, c, style = Stroke(9.dp.toPx()))
        val dir = Offset(0.7f, 0.7f)
        drawLine(BigColors.Ink, c + dir * (r + 4.dp.toPx()), c + dir * (r + 46.dp.toPx()), 14.dp.toPx(), StrokeCap.Round)
    }
}

/** מ and ם with a heart between them. */
@Composable
private fun BoxScope.PartnersPicture() {
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
        Text("מ", fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("♥", fontSize = 40.sp, color = Color(0xFFFFE082))
        Text("ם", fontSize = 76.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
