package io.github.tomsadeh.puki.ui.big.nikud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.big.kit.*
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.common.*
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import kotlinx.coroutines.delay

/** Reading units stay whole, including their marks, then travel into one heard sign. */
@Composable
fun JoinWordScreen(onBack: () -> Unit, onAgain: () -> Unit) {
    val model: JoinWordViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val voice = LocalVoicePlayer.current
    val sfx = LocalSfxPlayer.current
    var found by rememberSaveable { mutableStateOf(List(JoinWordGame.ROUNDS) { 0 }) }
    var celebrated by rememberSaveable(state.roundIndex) { mutableStateOf(false) }
    var heardMisses by rememberSaveable(state.roundIndex) { mutableIntStateOf(0) }
    val joining = remember(state.roundIndex) { Animatable(0f) }
    var joinedVisually by remember(state.roundIndex) { mutableStateOf(false) }
    val help = rememberHelp(state.roundIndex, active = state.phase == JoinPhase.UNITS || state.phase == JoinPhase.PICTURE) { model.replay() }
    LaunchedEffect(model) { model.start() }
    DisposableEffect(voice) { onDispose { voice.stop() } }
    LaunchedEffect(paused) { if (paused) voice.stop() }
    // A closing lantern lights in silence. The logic's word cue waits for the 400ms slide;
    // onJoined waits for the word to finish so PICK_PICTURE cannot cut it off.
    LaunchedEffect(state.roundIndex, state.phase, paused) {
        if (state.phase != JoinPhase.JOINED || paused) return@LaunchedEffect
        joining.animateTo(1f, tween(((1f - joining.value) * 400).toInt()))
        joinedVisually = true
    }
    if (!paused && !state.sessionComplete && (state.phase != JoinPhase.JOINED || joinedVisually)) {
        PlayVoiceCue(state.cue.keys, state.cue.token.toLong())
    }
    LaunchedEffect(state.roundIndex, state.phase, joinedVisually, state.cue.token, paused) {
        if (state.phase != JoinPhase.JOINED || !joinedVisually || paused) return@LaunchedEffect
        delay(100)
        // isPlaying includes asynchronous preparation on the slow tablet, unlike speaking.
        while (voice.isPlaying()) delay(50)
        model.onJoined()
    }
    LaunchedEffect(state.roundIndex, state.solved, paused, state.sessionComplete) {
        if (!state.solved || paused || state.sessionComplete) return@LaunchedEffect
        found = found.toMutableList().also {
            it[state.roundIndex] = state.foundPictureId?.let(::joinPicture) ?: R.drawable.ic_island_echo
        }
        if (!celebrated) { celebrated = true; sfx.play(Sfx.SUCCESS) }
        delay(1900)
        while (voice.isPlaying()) delay(50)
        model.next()
    }
    LaunchedEffect(state.roundIndex, state.misses, paused) {
        if (!paused && state.misses > heardMisses) {
            heardMisses = state.misses
            sfx.play(Sfx.WRONG_GENTLE)
            help.missed()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalLanguage provides Language.HEBREW) {
        BigRoundFrame(onBack, List(state.totalRounds) { null }, state.roundIndex, state.starsEarned,
            onReplay = { if (!paused) model.replay() }, pulseReplay = state.feedback != null && !state.solved,
            foundPictures = found.take(state.totalRounds).map { it.takeIf { id -> id != 0 } },
            modifier = Modifier.noticeTouches(help), overlay = {
                if (state.feedback != null && !state.solved && !state.sessionComplete) {
                    FeedbackBubble(state.feedback!!.tappedWordText,
                        Modifier.align(Alignment.BottomStart).padding(start = 180.dp, bottom = 12.dp), fontSize = 48.sp)
                }
                if (state.sessionComplete && !paused) {
                    ConfettiOverlay(show = true)
                    RoundCompleteSheet(state.starsEarned, onContinue = onAgain, onHome = onBack)
                }
            }) {
            if (state.phase != JoinPhase.LOADING && !state.sessionComplete) key(state.roundIndex) {
                JoinScene(state, joining.value, joinedVisually, paused, model::onUnitTap, model::replay, model::onAnswer)
            }
        }
    }
}

@Composable
private fun JoinScene(state: JoinState, joining: Float, joinedVisually: Boolean, paused: Boolean,
                      onUnit: (Int) -> Unit, onReplay: () -> Unit, onPicture: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val signWidth = minOf(360.dp, maxWidth - 100.dp)
        val signHeight = minOf(180.dp, maxHeight * .42f).coerceAtLeast(160.dp)
        val showSign = joinedVisually || state.phase == JoinPhase.PICTURE || state.phase == JoinPhase.SOLVED
        if (showSign) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) {
                AnswerBoard(state.wordText, BoardMark.NONE, { if (!paused) onReplay() }, width = signWidth,
                    height = signHeight, fontSize = 100.sp, squish = false, modifier = Modifier.joinOutline(signHeight, gold = true))
                JoinBadge(false, true, Modifier.align(Alignment.TopStart))
            }
        } else {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).size(signWidth, 70.dp)
                .drawWithCache {
                    val dash = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())))
                    onDrawBehind {
                        drawRoundRect(BigColors.Face.copy(alpha = .25f), cornerRadius = CornerRadius(18.dp.toPx()))
                        drawRoundRect(BigColors.GoldEdge.copy(alpha = .5f), cornerRadius = CornerRadius(18.dp.toPx()), style = dash)
                    }
                })
            val widths = state.units.map { if (it.kind == Nikud.UnitKind.CLOSING) 128.dp else 200.dp }
            val gap = 32.dp
            val total = widths.fold(0.dp) { sum, w -> sum + w } + gap * (widths.size - 1)
            state.units.forEachIndexed { index, unit ->
                val preceding = widths.take(index).fold(0.dp) { sum, w -> sum + w }
                // Absolute offsets preserve right-to-left reading order independently of the app.
                val initialX = (maxWidth - total) / 2 + total - preceding - gap * index - widths[index]
                val finalX = (maxWidth - total + gap) / 2 + total - gap - preceding - widths[index]
                val initialY = maxHeight * .12f
                Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(initialX + (finalX - initialX) * joining, initialY + (10.dp - initialY) * joining)) {
                    AnswerBoard(unit.text, BoardMark.NONE, { if (!paused) onUnit(index) },
                        width = widths[index], height = signHeight, fontSize = 100.sp, squish = false,
                        modifier = Modifier.joinOutline(signHeight, unit.done, unit.isNext && state.phase == JoinPhase.UNITS))
                    if (unit.kind == Nikud.UnitKind.CLOSING || unit.done) {
                        JoinBadge(unit.kind == Nikud.UnitKind.CLOSING, unit.done, Modifier.align(Alignment.TopStart))
                    }
                }
            }
        }
        if (state.pictures.isNotEmpty()) {
            val rise = remember { Animatable(0f) }
            LaunchedEffect(paused) { if (!paused) rise.animateTo(1f, tween(((1f - rise.value) * 400).toInt())) }
            val pictureSize = minOf(170.dp, (maxWidth - 64.dp) / 3, maxHeight - signHeight - 102.dp).coerceAtLeast(128.dp)
            Row(Modifier.align(Alignment.BottomCenter).graphicsLayer {
                translationY = (1f - rise.value) * 42.dp.toPx(); alpha = rise.value
            }, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                state.pictures.forEach { tile -> JoinPictureBoard(tile, pictureSize) { if (!paused && !state.solved) onPicture(tile.id) } }
            }
        }
    }
}

/** A fixed outline, rather than the kit's moving HINT treatment. */
private fun Modifier.joinOutline(height: Dp, gold: Boolean = false, next: Boolean = false) = drawWithCache {
    val ring = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
    onDrawWithContent {
        drawContent()
        if (gold) drawRoundRect(BigColors.Gold, size = Size(size.width, height.toPx()),
            cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(6.dp.toPx()))
        if (next) {
            drawRoundRect(Color.White, Offset(-7.dp.toPx(), -7.dp.toPx()),
                Size(size.width + 14.dp.toPx(), height.toPx() + 14.dp.toPx()), CornerRadius(30.dp.toPx()), style = ring)
            val x = size.width / 2; val y = height.toPx() + 44.dp.toPx()
            drawLine(BigColors.GoldEdge, Offset(x + 16.dp.toPx(), y), Offset(x - 16.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(BigColors.GoldEdge, Offset(x - 16.dp.toPx(), y), Offset(x - 8.dp.toPx(), y - 7.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawLine(BigColors.GoldEdge, Offset(x - 16.dp.toPx(), y), Offset(x - 8.dp.toPx(), y + 7.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun JoinBadge(lantern: Boolean, lit: Boolean, modifier: Modifier) {
    Box(modifier.offset(x = (-10).dp, y = (-10).dp).size(40.dp)
        .background(if (!lantern) BigColors.Coral else if (lit) BigColors.Gold else BigColors.Face, CircleShape)
        .border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        if (!lantern) SpeakerIcon(24.dp)
        else Box(Modifier.size(26.dp).drawWithCache {
            val ink = if (lit) BigColors.GoldEdge else BigColors.TagInk
            val stroke = Stroke(2.dp.toPx())
            onDrawBehind {
                val w = size.width; val h = size.height
                drawArc(ink, 180f, 180f, false, Offset(w * .32f, 0f), Size(w * .36f, h * .3f), style = stroke)
                drawRoundRect(ink, Offset(w * .22f, h * .28f), Size(w * .56f, h * .52f), CornerRadius(2.dp.toPx()), style = stroke)
                drawLine(ink, Offset(w * .16f, h * .22f), Offset(w * .84f, h * .22f), 2.dp.toPx())
                drawLine(ink, Offset(w * .16f, h * .88f), Offset(w * .84f, h * .88f), 2.dp.toPx())
                if (lit) drawCircle(BigColors.Face, w * .12f, Offset(w * .5f, h * .53f))
            }
        })
    }
}

@Composable
private fun JoinPictureBoard(tile: PictureTile, size: Dp, onTap: () -> Unit) {
    Box(Modifier.size(size, size + 64.dp)) {
        AnswerBoard("", when (tile.mark) {
            TileMark.WRONG -> BoardMark.WRONG
            TileMark.CORRECT -> BoardMark.CORRECT
            else -> BoardMark.NONE
        }, onTap, width = size, height = size, picture = joinPicture(tile.pictureId),
            modifier = Modifier.drawWithCache {
                onDrawBehind {
                    if (tile.mark == TileMark.HINT || tile.mark == TileMark.CORRECT) drawRoundRect(
                        BigColors.Gold.copy(alpha = .4f), Offset(-6.dp.toPx(), -6.dp.toPx()),
                        Size(this.size.width + 12.dp.toPx(), size.toPx() + 12.dp.toPx()), CornerRadius(28.dp.toPx()))
                }
            })
        // No 24sp pointed name tag: the board remains tappable and carries a speaker tag.
        if (tile.mark == TileMark.WRONG) Box(Modifier.align(Alignment.BottomCenter).size(100.dp, 52.dp)
            .background(BigColors.Face, RoundedCornerShape(26.dp)).border(3.dp, BigColors.TagEdge, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center) { SpeakerIcon(26.dp, BigColors.Coral) }
    }
}

private fun joinPicture(id: String): Int = when (id) {
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
