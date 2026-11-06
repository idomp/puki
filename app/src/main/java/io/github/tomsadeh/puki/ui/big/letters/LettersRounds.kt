package io.github.tomsadeh.puki.ui.big.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.big.BigLetters
import io.github.tomsadeh.puki.ui.big.kit.AnswerBoard
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.BigRoundFrame
import io.github.tomsadeh.puki.ui.big.kit.BoardMark
import io.github.tomsadeh.puki.ui.big.kit.FeedbackBubble
import io.github.tomsadeh.puki.ui.big.kit.PlayVoiceCue
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.rememberGamePaused
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay

/** How long the board stays green before the next question. */
private const val CELEBRATE_MS = 1800L

/** How long Puki's "this is X" bubble stays after a wrong tap. */
private const val BUBBLE_MS = 4000L

private fun TileMark.toBoard() = when (this) {
    TileMark.NONE -> BoardMark.NONE
    TileMark.WRONG -> BoardMark.WRONG
    TileMark.HINT -> BoardMark.HINT
    TileMark.CORRECT -> BoardMark.CORRECT
}

/**
 * What every letters game shares: the beach frame, the shells (each keeps the glyph found in its
 * round), the voice cue, the wrong-tap bubble and sound, the pause before the next question, and the
 * end of the session. [content] is the play area for the current state.
 */
@Composable
private fun LettersRound(vm: BigLetterGameViewModel, onExit: () -> Unit, content: @Composable BoxScope.(LetterGameState) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sfx = LocalSfxPlayer.current
    val paused by rememberGamePaused()
    var session by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { if (vm.state.value.phase == LetterPhase.COMPLETE) vm.restart() else vm.start() }
    PlayVoiceCue(state.cue.keys, state.cue.token.toLong())

    // The shells keep what this session found
    val found = remember(session) { mutableStateListOf<String?>().apply { repeat(ListenFindGame.ROUNDS) { add(null) } } }
    LaunchedEffect(state.solved, state.roundIndex) {
        if (state.solved) state.tiles.firstOrNull { it.mark == TileMark.CORRECT }?.let { tile ->
            if (state.roundIndex in found.indices) found[state.roundIndex] = tile.glyph
        }
    }

    // A found letter: a chime and the green board, then on to the next question
    LaunchedEffect(state.solved, state.roundIndex) { if (state.solved) sfx.play(Sfx.SUCCESS) }
    LaunchedEffect(state.phase, state.solved, state.roundIndex, paused) {
        if (state.phase == LetterPhase.ROUND && state.solved && !paused) {
            delay(CELEBRATE_MS)
            vm.next()
        }
    }

    // A wrong tap: a gentle sound and Puki's bubble naming only what was tapped
    var bubble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.roundIndex, state.feedback, state.misses, state.solved) {
        val feedback = state.feedback
        if (feedback != null && !state.solved && state.phase == LetterPhase.ROUND) {
            sfx.play(Sfx.WRONG_GENTLE)
            bubble = "זו ${BigLetters[feedback.tappedId].name}"
            delay(BUBBLE_MS)
        }
        bubble = null
    }

    BigRoundFrame(
        onBack = onExit,
        found = found,
        current = state.roundIndex,
        stars = state.starsEarned,
        onReplay = vm::replay,
        pulseReplay = state.misses > 0 && !state.solved,
        overlay = {
            bubble?.let { FeedbackBubble(it, Modifier.align(Alignment.BottomStart).padding(start = 176.dp, bottom = 64.dp)) }
            if (state.sessionComplete) {
                ConfettiOverlay(show = true)
                RoundCompleteSheet(
                    starsEarned = state.starsEarned,
                    onContinue = { session++; vm.restart() },
                    onHome = onExit
                )
            }
        },
        content = { content(state) }
    )
}

/** Boards in a fixed row on the sand: the same places for the whole question. */
@Composable
private fun BoardsRow(state: LetterGameState, onTap: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = state.tiles.size.coerceAtLeast(1)
        val gap = 16.dp
        val w = minOf(156.dp, (maxWidth - gap * (n - 1)) / n)
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
            for (tile in state.tiles) key(tile.id) {
                AnswerBoard(
                    text = tile.glyph,
                    mark = tile.mark.toBoard(),
                    onTap = { onTap(tile.id) },
                    width = w,
                    height = w * (176f / 156f),
                    nameTag = if (tile.mark == TileMark.WRONG) BigLetters[tile.id].name else null
                )
            }
        }
    }
}

/** Listen and find: Puki names a letter, the child taps it among 3 or 4 boards. */
@Composable
fun ListenFindRound(onBack: () -> Unit) {
    val vm: ListenFindViewModel = viewModel()
    LettersRound(vm, onBack) { state -> BoardsRow(state, vm::onAnswer) }
}

/** Letter detective: two look-alikes side by side with the difference said aloud, then questions. */
@Composable
fun LetterDetectiveRound(onBack: () -> Unit) {
    val vm: LetterDetectiveViewModel = viewModel()
    LettersRound(vm, onBack) { state ->
        val compare = state.compare
        if (state.phase == LetterPhase.COMPARE && compare != null) {
            CompareView(compare, onReplay = vm::replay, onGo = vm::next)
        } else {
            BoardsRow(state, vm::onAnswer)
        }
    }
}

@Composable
private fun CompareView(compare: ComparePair, onReplay: () -> Unit, onGo: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val goSize = 112.dp
        val gap = 24.dp
        val w = minOf(210.dp, (maxWidth - goSize - gap * 3) / 2)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                // (a look at both: tapping a board just says the difference again)
                AnswerBoard(compare.a.glyph, BoardMark.NONE, onReplay, width = w, height = w * 1.12f)
                AnswerBoard(compare.b.glyph, BoardMark.NONE, onReplay, width = w, height = w * 1.12f)
                GoButton(goSize, onGo)
            }
            Text(
                compare.line,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BigColors.TagInk
            )
        }
    }
}

/** The round green "go" button with a white play triangle (pointing the way the page reads on). */
@Composable
private fun GoButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Canvas(
        Modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(BigColors.Right)
            .border(6.dp, Color.White, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        val u = this.size.width / 100f
        val triangle = Path().apply {
            moveTo(34 * u, 50 * u); lineTo(64 * u, 30 * u); lineTo(64 * u, 70 * u); close()  // points left: forward in Hebrew
        }
        drawPath(triangle, Color.White)
    }
}

/** Final-form partners: a letter on the reading side, a link, and three candidates for its partner. */
@Composable
fun FinalPartnersRound(onBack: () -> Unit) {
    val vm: FinalPartnersViewModel = viewModel()
    LettersRound(vm, onBack) { state ->
        val shown = state.shown
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val shownW = 190.dp
            val link = 64.dp
            val gap = 16.dp
            val n = state.tiles.size.coerceAtLeast(1)
            val w = minOf(150.dp, (maxWidth - shownW - link - gap * (n + 1)) / n)
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                if (shown != null) {
                    AnswerBoard(
                        text = shown.glyph,
                        mark = if (state.solved) BoardMark.CORRECT else BoardMark.NONE,
                        onTap = vm::replay,
                        width = shownW, height = shownW * 1.12f
                    )
                    PartnerLink(state.solved, Modifier.width(link).height(shownW * 1.12f))
                }
                for (tile in state.tiles) key(tile.id) {
                    AnswerBoard(
                        text = tile.glyph,
                        mark = tile.mark.toBoard(),
                        onTap = { vm.onAnswer(tile.id) },
                        width = w, height = w * (176f / 156f),
                        nameTag = if (tile.mark == TileMark.WRONG) BigLetters[tile.id].name else null
                    )
                }
            }
        }
    }
}

/** A short line between the shown letter and its candidates; a green heart on it once they are matched. */
@Composable
private fun PartnerLink(matched: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(0.dp))
        if (matched) Text("♥", fontSize = 40.sp, color = BigColors.Right)
        Spacer(Modifier.height(6.dp))
        Spacer(
            Modifier.width(48.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (matched) BigColors.Right else BigColors.Post.copy(alpha = 0.5f))
        )
    }
}
