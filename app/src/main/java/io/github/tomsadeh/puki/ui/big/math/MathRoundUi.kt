package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.numberWords
import io.github.tomsadeh.puki.data.content.big.MathContent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.big.kit.*
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val zeroName = BilingualText("אפס", "Zero")
private fun numberName(n: Int) = MathContent.teenNumbers[n]?.text?.he ?: numberWords(n, false).getOrNull(1) ?: zeroName.he

@Composable
internal fun MathRoundUi(
    state: MathState, answer: Int, feedback: MathFeedback?,
    onReplay: () -> Unit, onNext: () -> Unit, onBack: () -> Unit, onRestart: () -> Unit,
    questionActive: Boolean = true,
    backdrop: @Composable () -> Unit = { BeachBackdrop() },
    pictureHint: Boolean = false,
    completionReady: Boolean = true,
    successVoiceKeys: List<String> = emptyList(),
    content: @Composable BoxScope.(showHint: Boolean) -> Unit
) {
    val session = state.session
    val paused = LocalGamePaused.current
    val voice = LocalVoicePlayer.current
    val sfx = LocalSfxPlayer.current
    var found by rememberSaveable { mutableStateOf(List<String?>(session.totalRounds) { null }) }
    var celebrated by rememberSaveable(session.roundIndex) { mutableStateOf(false) }
    var heardMisses by rememberSaveable(session.roundIndex) { mutableIntStateOf(0) }
    var completedVoiceToken by remember(session.roundIndex, state.voice.token, paused) { mutableLongStateOf(-1L) }
    val awaitSuccessVoice = successVoiceKeys.isNotEmpty()
    val help = rememberHelp(session.roundIndex, active = questionActive && !state.roundComplete && !session.sessionComplete && !session.isLoading) {
        onReplay()
    }
    if (awaitSuccessVoice) {
        LaunchedEffect(state.voice.token, paused, session.sessionComplete) {
            if (paused || session.sessionComplete || state.voice.token == 0L) return@LaunchedEffect
            // A replay during success still asks the question, then finishes the success sentence before leaving.
            val keys = if (state.roundComplete && state.voice.keys != successVoiceKeys)
                state.voice.keys + successVoiceKeys else state.voice.keys
            voice.stop()
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { voice.stop() }
                voice.playSequence(keys, Language.HEBREW) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            completedVoiceToken = state.voice.token
        }
    } else {
        DisposableEffect(paused) { if (paused) voice.stop(); onDispose {} }
        if (!paused && !session.sessionComplete) PlayVoiceCue(state.voice.keys, state.voice.token)
    }
    LaunchedEffect(session.roundIndex, state.roundComplete, paused, session.sessionComplete) {
        if (!state.roundComplete || paused || session.sessionComplete) return@LaunchedEffect
        found = found.toMutableList().also { it[session.roundIndex] = answer.toString() }
        if (!celebrated) { celebrated = true; sfx.play(Sfx.SUCCESS) }
    }
    LaunchedEffect(session.roundIndex, state.roundComplete, paused, session.sessionComplete,
        completionReady, if (awaitSuccessVoice) completedVoiceToken else 0L,
        if (awaitSuccessVoice) state.voice.token else 0L) {
        if (!state.roundComplete || paused || session.sessionComplete || !completionReady) return@LaunchedEffect
        if (awaitSuccessVoice && completedVoiceToken != state.voice.token) return@LaunchedEffect
        // Leave a brief still view of the joined card after both the movement and the sentence finish.
        delay(if (awaitSuccessVoice) 700 else 1900)
        onNext()
    }
    LaunchedEffect(session.roundIndex, session.roundMisses, paused) {
        if (!paused && session.roundMisses > heardMisses) {
            heardMisses = session.roundMisses
            sfx.play(Sfx.WRONG_GENTLE)
            help.missed()
        }
    }
    BigRoundFrame(
        onBack = onBack, found = found, current = session.roundIndex,
        stars = session.totalStarsEarned, onReplay = { if (!paused) onReplay() },
        pulseReplay = feedback != null, backdrop = backdrop,
        modifier = Modifier.noticeTouches(help),
        overlay = {
            if (feedback != null && !session.sessionComplete) {
                if (pictureHint) AppleHint(feedback.kind, Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 16.dp))
                else FeedbackBubble(numberName(feedback.chosen),
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 16.dp))
            }
            if (session.sessionComplete && !paused) {
                ConfettiOverlay(show = true)
                RoundCompleteSheet(session.totalStarsEarned, onRestart, onBack)
            }
        }
    ) {
        if (!session.isLoading) content(session.showHint || help.showHint)
    }
}

/** Fixed LTR order, including in an RTL island; choices keep the engine's order. The kit's HINT tilts, so use a still halo. */
@Composable
internal fun NumberBoards(choices: List<Int>, answer: Int, complete: Boolean, feedback: MathFeedback?, showHint: Boolean,
                          showDots: Boolean, onAnswer: (Int) -> Unit, modifier: Modifier = Modifier) {
    val paused = LocalGamePaused.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            choices.forEach { n ->
                val mark = when {
                    complete && n == answer -> BoardMark.CORRECT
                    feedback?.chosen == n -> BoardMark.WRONG
                    else -> BoardMark.NONE
                }
                AnswerBoard(n.toString(), mark, { if (!paused && !complete) onAnswer(n) },
                    modifier = Modifier.clickable(enabled = !paused && !complete) { onAnswer(n) }.drawWithCache {
                        onDrawBehind {
                            if (showHint && !complete && n == answer) drawRoundRect(Color(0xFFFFDE70).copy(alpha = 0.6f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()))
                        }
                    }, width = 120.dp, height = 132.dp, textScale = 0.45f, nameTag = numberName(n),
                    footer = if (showDots && n <= 10) { { NumberDots(n) } } else null,
                    textContent = if (n > 10) { { TeenNumeral(n) } } else null)
            }
        }
    }
}

@Composable
private fun NumberDots(number: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat((number + 4) / 5) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(minOf(5, number - row * 5)) { Box(Modifier.size(7.dp).background(BigColors.GoldEdge, androidx.compose.foundation.shape.CircleShape)) }
            }
        }
    }
}

@Composable
internal fun EquationSign(a: Int?, op: MathOperation, b: String, result: String, modifier: Modifier = Modifier,
                          unknownStart: Boolean = false, arrivingColor: Color = BigColors.Coral) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.background(Color(0xFFFFE3A6), RoundedCornerShape(12.dp)).drawWithCache {
            onDrawBehind { drawRoundRect(BigColors.Post, style = Stroke(3.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())) }
        }.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(a?.toString() ?: "?", if (op == MathOperation.PLUS) "+" else "−", b, "=", result).forEachIndexed { i, text ->
                val label = buildAnnotatedString {
                    if (i == 4 && (result.toIntOrNull() ?: 0) in 11..20) {
                        withStyle(SpanStyle(color = BigColors.GoldEdge)) { append(text.first()) }
                        append(text.drop(1))
                    } else append(text)
                }
                Text(label, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    color = when (i) { 0 -> if (unknownStart) BigColors.Coral else BigColors.Blue
                        2 -> if (unknownStart) BigColors.Blue else arrivingColor; else -> BigColors.Ink })
            }
        }
    }
}
