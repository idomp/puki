package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    Modifier.fillMaxSize().padding(bottom = 58.dp), target = round.target)
                state.equation?.let { equation ->
                    EquationSign(equation.a, equation.op, equation.b?.toString() ?: "?", equation.result.toString(), Modifier.align(Alignment.BottomCenter))
                }
            }
            Box(Modifier.height(204.dp), contentAlignment = Alignment.TopCenter) {
                NumberBoards(state.choices, round.missing, state.roundComplete, state.feedback, hint,
                    showDots = round.isMakeN || state.session.stage < MathStage.MIXED_5, onAnswer = viewModel::onAnswer)
            }
        }
    }
}

/** The first cell is the rightmost. Still empty cells remain visible throughout the question. */
@Composable
internal fun CounterFrame(capacity: Int, filled: Int, added: Float = 0f, complete: Boolean = false,
                          modifier: Modifier = Modifier, target: Int = capacity) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val makeN = target < capacity
        val tagWidth = if (makeN) 104.dp else 0.dp
        val cell = minOf(100.dp, (maxWidth - 12.dp - tagWidth) / 5, (maxHeight - 12.dp) / (capacity / 5))
        // Keep the tag physically left, while the cells always fill from right to left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.Top) {
                if (makeN) {
                    Box(Modifier.width(tagWidth).height(cell + 12.dp).drawWithContent {
                        drawLine(BigColors.Gold, Offset(80.dp.toPx(), size.height / 2),
                            Offset(size.width + 8.dp.toPx(), size.height / 2), 5.dp.toPx())
                        drawContent()
                    }, contentAlignment = Alignment.CenterStart) {
                        FrameTargetTag(target, Modifier.width(84.dp).height(minOf(82.dp, cell)))
                    }
                }
                Column(Modifier.background(BigColors.Face, RoundedCornerShape(12.dp))
                    .border(5.dp, if (complete && !makeN) BigColors.Right else BigColors.Post, RoundedCornerShape(12.dp)).padding(6.dp)
                    .drawWithContent {
                        drawContent()
                        if (makeN) {
                            val side = cell.toPx()
                            val inset = 2.dp.toPx()
                            val right = size.width - inset
                            val outline = Path().apply {
                                if (target <= 5) {
                                    val left = (5 - target) * side + inset
                                    moveTo(left, inset); lineTo(right, inset)
                                    lineTo(right, side - inset); lineTo(left, side - inset); close()
                                } else {
                                    val step = (10 - target) * side + inset
                                    moveTo(inset, inset); lineTo(right, inset)
                                    lineTo(right, size.height - inset); lineTo(step, size.height - inset)
                                    lineTo(step, side - inset); lineTo(inset, side - inset); close()
                                }
                            }
                            drawPath(outline, BigColors.Gold, style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
                            // Small targets begin partway across the top row; the link crosses only sand.
                            if (target < 5) drawLine(BigColors.Gold, Offset(0f, side / 2),
                                Offset((5 - target) * side + inset, side / 2), 5.dp.toPx())
                        }
                    }) {
                    repeat(capacity / 5) { row ->
                        Row {
                            repeat(5) { column ->
                                val index = row * 5 + (4 - column)
                                val active = index < target
                                Box(Modifier.size(cell)
                                    .background(if (active) BigColors.Face else Color(0xFFD7C5A6))
                                    .border(1.dp, if (active) BigColors.Frame else BigColors.Post.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center) {
                                    val arriving = if (active) (added - (index - filled)).coerceIn(0f, 1f) else 0f
                                    if (index < filled || arriving > 0f) {
                                        val counterColor = if (index < filled) BigColors.Blue else if (makeN) BigColors.Coral else BigColors.Right
                                        Box(Modifier.fillMaxSize(0.7f).graphicsLayer {
                                            if (index >= filled) { translationY = -48.dp.toPx() * (1f - arriving); alpha = arriving }
                                        }.drawWithCache {
                                            onDrawBehind {
                                                drawCircle(if (makeN && index >= filled) BigColors.Coral else Color(0xFF21679C),
                                                    center = center + Offset(0f, 3.dp.toPx()))
                                                drawCircle(counterColor)
                                                drawCircle(Color.White.copy(alpha = 0.4f), size.minDimension * 0.12f, center - Offset(size.width * 0.15f, size.height * 0.18f))
                                            }
                                        })
                                    } else if (active) Box(Modifier.fillMaxSize(0.65f).drawWithCache {
                                        onDrawBehind { drawCircle(BigColors.Frame.copy(alpha = 0.4f), style = Stroke(2.dp.toPx())) }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameTargetTag(target: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(BigColors.Gold, RoundedCornerShape(16.dp))
        .border(3.dp, BigColors.GoldEdge, RoundedCornerShape(16.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(target.toString(), color = BigColors.Ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(target) { Box(Modifier.size(5.dp).background(BigColors.Ink, androidx.compose.foundation.shape.CircleShape)) }
        }
    }
}
