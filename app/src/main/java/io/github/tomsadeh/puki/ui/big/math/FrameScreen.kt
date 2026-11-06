package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import kotlinx.coroutines.delay

@Composable
internal fun FrameScreen(viewModel: FrameViewModel, onBack: () -> Unit, onRestart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val fill = remember(state.session.roundIndex) { Animatable(0f) }
    LaunchedEffect(state.session.roundIndex, state.roundComplete, paused) {
        val round = state.round ?: return@LaunchedEffect
        if (state.roundComplete && !paused) {
            delay(100)
            fill.animateTo(round.missing.toFloat(), tween(1400))
        }
    }
    MathRoundUi(state, state.round?.missing ?: 0, state.feedback, viewModel::replay, viewModel::next, onBack, onRestart) { hint ->
        val round = state.round ?: return@MathRoundUi
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                CounterFrame(round.capacity, round.filled, fill.value, state.roundComplete,
                    Modifier.fillMaxSize().padding(bottom = 58.dp))
                state.equation?.let { equation ->
                    EquationSign(equation.a, equation.op, equation.b?.toString() ?: "?", equation.result.toString(), Modifier.align(Alignment.BottomCenter))
                }
            }
            Box(Modifier.height(204.dp), contentAlignment = Alignment.TopCenter) {
                NumberBoards(state.choices, round.missing, state.roundComplete, state.feedback, hint,
                    showDots = state.session.stage < MathStage.MIXED_5, onAnswer = viewModel::onAnswer)
            }
        }
    }
}

/** The first cell is the rightmost. Still empty cells remain visible throughout the question. */
@Composable
internal fun CounterFrame(capacity: Int, filled: Int, added: Float = 0f, complete: Boolean = false, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = minOf(100.dp, (maxWidth - 12.dp) / 5, (maxHeight - 12.dp) / (capacity / 5))
        Column(Modifier.background(BigColors.Face, RoundedCornerShape(12.dp))
            .border(5.dp, if (complete) BigColors.Right else BigColors.Post, RoundedCornerShape(12.dp)).padding(6.dp)) {
            repeat(capacity / 5) { row ->
                Row {
                    repeat(5) { column ->
                        val index = row * 5 + column
                        Box(Modifier.size(cell).border(1.dp, BigColors.Frame), contentAlignment = Alignment.Center) {
                            val arriving = (added - (index - filled)).coerceIn(0f, 1f)
                            if (index < filled || arriving > 0f) {
                                Box(Modifier.fillMaxSize(0.7f).graphicsLayer {
                                    if (index >= filled) { translationY = -48.dp.toPx() * (1f - arriving); alpha = arriving }
                                }.drawWithCache {
                                    onDrawBehind {
                                        drawCircle(Color(0xFF21679C), center = center + Offset(0f, 3.dp.toPx()))
                                        drawCircle(if (index < filled) BigColors.Blue else BigColors.Right)
                                        drawCircle(Color.White.copy(alpha = 0.4f), size.minDimension * 0.12f, center - Offset(size.width * 0.15f, size.height * 0.18f))
                                    }
                                })
                            } else Box(Modifier.fillMaxSize(0.65f).drawWithCache {
                                onDrawBehind { drawCircle(BigColors.Frame.copy(alpha = 0.4f), style = Stroke(2.dp.toPx())) }
                            })
                        }
                    }
                }
            }
        }
    }
}
