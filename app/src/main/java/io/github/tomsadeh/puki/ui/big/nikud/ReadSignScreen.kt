package io.github.tomsadeh.puki.ui.big.nikud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.big.kit.*
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.common.*
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay

/** A silent sign becomes a heard sign only after its picture is found. */
@Composable
fun ReadSignScreen(onBack: () -> Unit) {
    val model: ReadSignViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val voice = LocalVoicePlayer.current
    val sfx = LocalSfxPlayer.current
    var found by rememberSaveable { mutableStateOf(List(ReadSignGame.ROUNDS) { 0 }) }
    var celebrated by rememberSaveable(state.roundIndex) { mutableStateOf(false) }
    var heardMisses by rememberSaveable(state.roundIndex) { mutableIntStateOf(0) }
    LaunchedEffect(model) { model.start() }
    DisposableEffect(voice) { onDispose { voice.stop() } }
    LaunchedEffect(paused) { if (paused) voice.stop() }
    if (!paused && !state.sessionComplete) PlayVoiceCue(state.cue.keys, state.cue.token.toLong())
    LaunchedEffect(state.roundIndex, state.solved, paused, state.sessionComplete) {
        if (!state.solved || paused || state.sessionComplete) return@LaunchedEffect
        found = found.toMutableList().also { it[state.roundIndex] = readPicture(state.dockedPictureId!!) }
        if (!celebrated) { celebrated = true; sfx.play(Sfx.SUCCESS) }
        delay(1900)
        while (voice.isPlaying()) delay(50)
        model.next()
    }
    LaunchedEffect(state.roundIndex, state.misses, paused) {
        if (!paused && state.misses > heardMisses) {
            heardMisses = state.misses
            sfx.play(Sfx.WRONG_GENTLE)
        }
    }
    // No automatic spoken hint: reading remains silent for as long as the child needs.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalLanguage provides Language.HEBREW) {
        BigRoundFrame(onBack, List(state.totalRounds) { null }, state.roundIndex, state.starsEarned,
            onReplay = { if (!paused) model.replay() }, pulseReplay = state.feedback != null && !state.solved,
            foundPictures = found.take(state.totalRounds).map { it.takeIf { id -> id != 0 } }, overlay = {
                if (!state.sessionComplete) {
                    val bubble = if (state.solved) state.signText else state.feedback?.tappedWordText
                    if (bubble != null) FeedbackBubble(bubble,
                        Modifier.align(Alignment.BottomStart).padding(start = 180.dp, bottom = 12.dp), fontSize = 48.sp)
                }
                if (state.sessionComplete && !paused) {
                    ConfettiOverlay(show = true)
                    RoundCompleteSheet(state.starsEarned, onContinue = {
                        found = List(ReadSignGame.ROUNDS) { 0 }; celebrated = false; heardMisses = 0
                        model.restart()
                    }, onHome = onBack)
                }
            }) {
            if (state.phase == NikudPhase.ROUND) key(state.roundIndex) {
                ReadScene(state, paused, model::replay, model::onAnswer)
            }
        }
    }
}

@Composable
private fun ReadScene(state: ReadSignState, paused: Boolean, onReplay: () -> Unit, onPicture: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val signWidth = minOf(360.dp, maxWidth - 100.dp)
        val signHeight = minOf(180.dp, maxHeight * .42f).coerceAtLeast(160.dp)
        val pictureSize = minOf(170.dp, (maxWidth - 64.dp) / 3, maxHeight - signHeight - 102.dp).coerceAtLeast(128.dp)
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) {
            // The plain sign is deliberately inert; the listening corner is the explicit hint.
            AnswerBoard(state.signText, BoardMark.NONE, { if (!paused && state.signHeard) onReplay() },
                width = signWidth, height = signHeight, fontSize = 100.sp, squish = false,
                modifier = Modifier.drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (state.signHeard) drawRoundRect(BigColors.Gold, size = Size(size.width, signHeight.toPx()),
                            cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(6.dp.toPx()))
                    }
                })
            if (state.signHeard) Box(Modifier.align(Alignment.TopStart).offset(x = (-10).dp, y = (-10).dp).size(40.dp)
                .background(BigColors.Coral, CircleShape).border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center) { SpeakerIcon(24.dp) }
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            state.pictures.forEach { tile -> ReadPictureBoard(tile, pictureSize) { if (!paused && !state.solved) onPicture(tile.id) } }
        }
        // The picture travels from its board to the physical left end of the sign (not RTL start).
        val docked = state.dockedPictureId
        if (docked != null) {
            val flight = remember { Animatable(0f) }
            LaunchedEffect(paused) { if (!paused) flight.animateTo(1f, tween(((1f - flight.value) * 400).toInt())) }
            val dockSize = 100.dp
            val targetX = (maxWidth - signWidth) / 2 - dockSize / 2 + 4.dp
            val targetY = 10.dp + (signHeight - dockSize) / 2
            val index = state.pictures.indexOfFirst { it.pictureId == docked }
            val total = pictureSize * 3 + 48.dp
            val sourceX = (maxWidth - total) / 2 + (pictureSize + 24.dp) * (2 - index) + (pictureSize - dockSize) / 2
            val sourceY = maxHeight - pictureSize - 64.dp + (pictureSize - dockSize) / 2
            Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(sourceX + (targetX - sourceX) * flight.value,
                sourceY + (targetY - sourceY) * flight.value).size(dockSize)
                .background(BigColors.Face, CircleShape).border(6.dp, BigColors.Right, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(readPicture(docked)), null, Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun ReadPictureBoard(tile: PictureTile, size: Dp, onTap: () -> Unit) {
    Box(Modifier.size(size, size + 64.dp)) {
        AnswerBoard("", when (tile.mark) {
            TileMark.WRONG -> BoardMark.WRONG
            TileMark.CORRECT -> BoardMark.CORRECT
            else -> BoardMark.NONE
        }, onTap, width = size, height = size, picture = readPicture(tile.pictureId),
            modifier = Modifier.drawWithCache {
                onDrawBehind {
                    if (tile.mark == TileMark.HINT || tile.mark == TileMark.CORRECT) drawRoundRect(
                        BigColors.Gold.copy(alpha = .4f), Offset(-6.dp.toPx(), -6.dp.toPx()),
                        Size(this.size.width + 12.dp.toPx(), size.toPx() + 12.dp.toPx()), CornerRadius(28.dp.toPx()))
                }
            })
        if (tile.mark == TileMark.WRONG) Box(Modifier.align(Alignment.BottomCenter).size(100.dp, 52.dp)
            .background(BigColors.Face, RoundedCornerShape(26.dp)).border(3.dp, BigColors.TagEdge, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center) { SpeakerIcon(26.dp, BigColors.Coral) }
    }
}

private fun readPicture(id: String): Int = when (id) {
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
    else -> error("Unknown nikud picture: $id")
}
