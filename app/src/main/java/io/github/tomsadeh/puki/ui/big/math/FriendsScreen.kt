package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun FriendsScreen(viewModel: FriendsViewModel, onBack: () -> Unit, onRestart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val voice = LocalVoicePlayer.current
    val flight = remember(state.session.roundIndex) { Animatable(0f) }
    LaunchedEffect(state.session.roundIndex, state.phase, paused) {
        if (!paused && state.phase == StoryPhase.CHANGE) flight.animateTo(1f, tween(1600))
    }
    LaunchedEffect(state.session.roundIndex, state.phase, state.voice.token, paused) {
        if (paused || state.session.isLoading || state.session.sessionComplete) return@LaunchedEffect
        if (state.phase == StoryPhase.SHOW_START || state.phase == StoryPhase.CHANGE) {
            // The kit starts the cue. Give the scene time to settle, then wait for that narration;
            // muted/missing recordings still advance. Replaying restarts this wait.
            delay(2300)
            voice.speaking.first { !it }
            viewModel.advancePhase()
        }
    }
    MathRoundUi(state, state.round?.result ?: 0, state.feedback, viewModel::replay, viewModel::next, onBack, onRestart,
        questionActive = state.phase == StoryPhase.QUESTION, backdrop = { MeadowBackdrop() }) { hint ->
        val round = state.round ?: return@MathRoundUi
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BranchScene(round, state.phase, flight.value,
                    capacity = if (maxOf(round.startCount, round.result) > 5) 10 else 5,
                    modifier = Modifier.weight(1f).fillMaxWidth())
                // Two ropes connect the equation to the lowest branch; no caption gives the answer away.
                Box(Modifier.height(14.dp).width(220.dp).drawWithCache {
                    onDrawBehind {
                        listOf(0.12f, 0.88f).forEach { x -> drawLine(BigColors.Post, Offset(size.width * x, 0f), Offset(size.width * x, size.height), 3.dp.toPx()) }
                    }
                })
                EquationSign(round.startCount, round.direction, round.change.toString(), if (state.roundComplete) round.result.toString() else "?")
                Spacer(Modifier.height(12.dp))
            }
            // Reserve exactly the same space before the question, so the branch never shifts.
            Box(Modifier.height(204.dp), contentAlignment = Alignment.TopCenter) {
                if (state.phase == StoryPhase.QUESTION || state.roundComplete) {
                    NumberBoards(state.choices, round.result, state.roundComplete, state.feedback, hint,
                        showDots = state.session.stage < MathStage.MIXED_5, onAnswer = viewModel::onAnswer)
                }
            }
        }
    }
}

@Composable
private fun BranchScene(round: FriendsRound, phase: StoryPhase, flight: Float, capacity: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        repeat(capacity / 5) { row ->
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().drawWithCache {
                val branchY = size.height - 8.dp.toPx()
                onDrawBehind {
                    drawLine(Color(0xFF805432), Offset(4.dp.toPx(), branchY + 3.dp.toPx()), Offset(size.width - 8.dp.toPx(), branchY + 3.dp.toPx()), 12.dp.toPx(), StrokeCap.Round)
                    drawLine(BigColors.Frame, Offset(4.dp.toPx(), branchY), Offset(size.width - 8.dp.toPx(), branchY), 8.dp.toPx(), StrokeCap.Round)
                }
            }) {
                val birdSize = minOf(84.dp, maxHeight - 8.dp, maxWidth / 5 - 8.dp)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    repeat(5) { column ->
                        val index = row * 5 + column
                        val joining = round.direction == MathOperation.PLUS
                        val start = index < round.startCount
                        val final = index < round.result
                        val moving = phase == StoryPhase.CHANGE && start != final
                        val occupied = when (phase) {
                            StoryPhase.SHOW_START -> start
                            StoryPhase.CHANGE -> if (moving) true else final
                            else -> final
                        }
                        Box(Modifier.weight(1f).height(birdSize + 8.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.size(birdSize).drawWithCache {
                                onDrawBehind {
                                    if (!occupied || moving) drawCircle(Color.White.copy(alpha = 0.55f), radius = size.minDimension * 0.35f, style = Stroke(2.dp.toPx()))
                                }
                            })
                            if (occupied) Image(painterResource(R.drawable.ic_count_bird), null,
                                Modifier.size(birdSize).graphicsLayer {
                                    if (moving) {
                                        val travel = if (joining) 1f - flight else flight
                                        translationX = -100.dp.toPx() * travel
                                        translationY = -70.dp.toPx() * travel - 24.dp.toPx() * sin(flight * PI).toFloat()
                                        rotationZ = -16f * travel
                                        alpha = if (!joining) 1f - flight else 1f
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeadowBackdrop() {
    Box(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val sky = Brush.verticalGradient(listOf(Color(0xFF98D9F3), Color(0xFFD2F0F9)))
        val grass = Path().apply {
            moveTo(0f, size.height * 0.65f)
            quadraticTo(size.width * 0.25f, size.height * 0.58f, size.width * 0.55f, size.height * 0.65f)
            quadraticTo(size.width * 0.8f, size.height * 0.7f, size.width, size.height * 0.63f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        onDrawBehind {
            drawRect(sky)
            drawOval(Color.White.copy(alpha = 0.85f), Offset(size.width * 0.6f, size.height * 0.28f), Size(100.dp.toPx(), 30.dp.toPx()))
            drawPath(grass, Color(0xFF9DCF75))
            drawRoundRect(Color(0xFF95643C), Offset(18.dp.toPx(), size.height * 0.2f), Size(28.dp.toPx(), size.height * 0.8f), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
            drawLine(BigColors.Frame, Offset(28.dp.toPx(), size.height * 0.29f), Offset(28.dp.toPx(), size.height), 6.dp.toPx(), StrokeCap.Round)
            drawCircle(Color(0xFF57A54B), 65.dp.toPx(), Offset(12.dp.toPx(), size.height * 0.2f))
            drawCircle(Color(0xFF72B857), 48.dp.toPx(), Offset(55.dp.toPx(), size.height * 0.14f))
        }
    })
}

@Composable
internal fun BranchDoor(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).drawWithCache {
            onDrawBehind { drawLine(BigColors.Post, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round) }
        })
        Row(Modifier.fillMaxWidth().offset(y = (-26).dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(2) { Image(painterResource(R.drawable.ic_count_bird), null, Modifier.weight(1f).aspectRatio(1f)) }
        }
    }
}
