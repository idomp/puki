package io.github.tomsadeh.puki.ui.big.nikud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.big.kit.AnswerBoard
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.BigRoundFrame
import io.github.tomsadeh.puki.ui.big.kit.BoardMark
import io.github.tomsadeh.puki.ui.big.kit.FeedbackBubble
import io.github.tomsadeh.puki.ui.big.kit.PlayVoiceCue
import io.github.tomsadeh.puki.ui.big.kit.SpeakerIcon
import io.github.tomsadeh.puki.ui.big.letters.TileMark
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.rememberGamePaused
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay

private const val CELEBRATE_MS = 1800L
private const val BUBBLE_MS = 4000L

private fun TileMark.toBoard() = when (this) {
    TileMark.NONE -> BoardMark.NONE
    TileMark.WRONG -> BoardMark.WRONG
    TileMark.HINT -> BoardMark.HINT
    TileMark.CORRECT -> BoardMark.CORRECT
}

/** A shell keeps the bare letter: at shell size the vowel dots would be lost, so the marks are dropped. */
private fun withoutMarks(text: String) = text.filter { it.code !in 0x591..0x5C7 }

/**
 * Sound shells (frames B1/B2): Puki says a syllable, the child taps it among three pointed boards.
 * Same boards, corner and shells as the Letters round. Before the first round of a stage the "meet the
 * mark" card is offered once (the logic's `offerCard`, minus the cards already seen). A wrong tap sinks
 * and greys the board, hangs a speaker tag under it (the grey board stays tappable to hear it again) and
 * Puki's bubble shows the tapped syllable large. After two misses the right board glows.
 */
@Composable
fun SoundShellsScreen(onBack: () -> Unit, onAgain: () -> Unit) {
    val vm: SoundShellsViewModel = viewModel()
    val meet: MeetMarkViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val sfx = LocalSfxPlayer.current
    val paused by rememberGamePaused()
    var seenCards by remember { mutableStateOf<Set<Int>?>(null) }
    var offered by rememberSaveable { mutableStateOf("") }  // the cards shown in this visit, as digits

    LaunchedEffect(Unit) {
        seenCards = meet.seenCards()
        vm.start()
    }

    // The card before the first question of a stage, once
    val offer = state.offerCard
    val seen = seenCards
    if (offer != null && seen != null && offer !in seen && offered.indexOf(offer.digitToChar()) < 0 &&
        state.phase == NikudPhase.ROUND && state.roundIndex == 0 && !state.solved && state.misses == 0
    ) {
        MeetMarkScreen(onBack = onBack, startCard = offer, throughAll = false, onFinished = { offered += offer.toString() })
        return
    }
    if (seen == null) return  // (a blink while the seen cards are read)

    PlayVoiceCue(state.cue.keys, state.cue.token.toLong())

    val found = remember { mutableStateListOf<String?>().apply { repeat(SoundShellsGame.ROUNDS) { add(null) } } }
    LaunchedEffect(state.solved, state.roundIndex) {
        if (state.solved) state.foundBare?.let { if (state.roundIndex in found.indices) found[state.roundIndex] = withoutMarks(it) }
    }
    LaunchedEffect(state.solved, state.roundIndex) { if (state.solved) sfx.play(Sfx.SUCCESS) }
    LaunchedEffect(state.phase, state.solved, state.roundIndex, paused) {
        if (state.phase == NikudPhase.ROUND && state.solved && !paused) {
            delay(CELEBRATE_MS)
            vm.next()
        }
    }

    var bubble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.roundIndex, state.feedback, state.misses, state.solved) {
        val feedback = state.feedback
        if (feedback != null && !state.solved && state.phase == NikudPhase.ROUND) {
            sfx.play(Sfx.WRONG_GENTLE)
            bubble = feedback.tappedText
            delay(BUBBLE_MS)
        }
        bubble = null
    }

    BigRoundFrame(
        onBack = onBack,
        found = found,
        current = state.roundIndex,
        stars = state.starsEarned,
        onReplay = vm::replay,
        pulseReplay = state.misses > 0 && !state.solved,
        overlay = {
            // Only what he tapped, large enough for the dots to show
            bubble?.let { FeedbackBubble(it, Modifier.align(Alignment.BottomStart).padding(start = 176.dp, bottom = 64.dp), fontSize = 64.sp) }
            if (state.sessionComplete) {
                ConfettiOverlay(show = true)
                RoundCompleteSheet(starsEarned = state.starsEarned, onContinue = onAgain, onHome = onBack)
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val n = state.tiles.size.coerceAtLeast(1)
            val gap = 16.dp
            val w = minOf(180.dp, (maxWidth - gap * (n - 1)) / n)
            val h = w * (212f / 180f)
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                for (tile in state.tiles) key(tile.id) {
                    val mark = tile.mark.toBoard()
                    Box(contentAlignment = Alignment.TopCenter) {
                        AnswerBoard(
                            text = tile.text,
                            mark = mark,
                            onTap = { vm.onAnswer(tile.id) },
                            width = w, height = h,
                            fontSize = maxOf(100f, w.value * 0.66f).sp,  // never below 100sp
                            squish = false
                        )
                        // The tag of a wrong board is a speaker, not a name: the board already shows what it is
                        if (mark == BoardMark.WRONG) SpeakerTag(Modifier.offset(y = h + 22.dp))
                    }
                }
            }
        }
    }
}

/** A small speaker on a tag: "tap the board to hear what this one says". */
@Composable
private fun SpeakerTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(3.dp, BigColors.TagEdge, RoundedCornerShape(22.dp))
            .padding(horizontal = 22.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) { SpeakerIcon(28.dp, color = BigColors.Coral) }
}
