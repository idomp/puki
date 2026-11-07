package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun FriendsScreen(viewModel: FriendsViewModel, onBack: () -> Unit, onRestart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val voice = LocalVoicePlayer.current
    val sfx = LocalSfxPlayer.current
    val flight = remember(state.session.roundIndex) { Animatable(0f) }
    var rustled by rememberSaveable(state.session.roundIndex) { mutableStateOf(false) }
    LaunchedEffect(state.session.roundIndex, state.phase, paused) {
        if (!paused && state.phase == StoryPhase.CHANGE) {
            if (state.round?.unknown == FriendsUnknown.CHANGE && !rustled) {
                rustled = true
                sfx.play(Sfx.MARACAS)
            }
            flight.animateTo(1f, tween(1600))
        }
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
    MathRoundUi(state, state.round?.answer ?: 0, state.feedback, viewModel::replay, viewModel::next, onBack, onRestart,
        questionActive = state.phase == StoryPhase.QUESTION, backdrop = { MeadowBackdrop() }) { hint ->
        val round = state.round ?: return@MathRoundUi
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (round.isHidden) HiddenBranchScene(round, state.phase, flight.value, state.showHiddenOutlines,
                    Modifier.weight(1f).fillMaxWidth())
                else BranchScene(round, state.phase, flight.value,
                    capacity = if (maxOf(round.startCount, round.result) > 5) 10 else 5,
                    modifier = Modifier.weight(1f).fillMaxWidth())
                // Two ropes connect the equation to the lowest branch; no caption gives the answer away.
                Box(Modifier.height(14.dp).width(220.dp).drawWithCache {
                    onDrawBehind {
                        listOf(0.12f, 0.88f).forEach { x -> drawLine(BigColors.Post, Offset(size.width * x, 0f), Offset(size.width * x, size.height), 3.dp.toPx()) }
                    }
                })
                val equation = state.equation
                EquationSign(if (equation != null) equation.a else round.startCount, round.direction,
                    if (equation != null) equation.b?.toString() ?: "?" else round.change.toString(),
                    if (equation != null) equation.result.toString() else "?",
                    unknownStart = round.unknown == FriendsUnknown.START)
                Spacer(Modifier.height(12.dp))
            }
            // Reserve exactly the same space before the question, so the branch never shifts.
            Box(Modifier.height(204.dp), contentAlignment = Alignment.TopCenter) {
                if (state.phase == StoryPhase.QUESTION || state.roundComplete) {
                    NumberBoards(state.choices, round.answer, state.roundComplete, state.feedback, hint && !round.isHidden,
                        showDots = state.session.stage < MathStage.MIXED_5, onAnswer = viewModel::onAnswer)
                }
            }
        }
    }
}

@Composable
private fun HiddenBranchScene(round: FriendsRound, phase: StoryPhase, flight: Float, outlines: Boolean, modifier: Modifier) {
    val revealed = phase == StoryPhase.ANSWERED
    val coverAlpha by animateFloatAsState(if (revealed) 0f else if (outlines) 0.5f else 1f, tween(650), label = "leaves")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.padding(horizontal = 12.dp).drawWithCache {
            onDrawBehind {
                val y = size.height - 8.dp.toPx()
                drawLine(Color(0xFF805432), Offset(0f, y + 3.dp.toPx()), Offset(size.width, y + 3.dp.toPx()), 12.dp.toPx(), StrokeCap.Round)
                drawLine(BigColors.Frame, Offset(0f, y), Offset(size.width, y), 8.dp.toPx(), StrokeCap.Round)
            }
        }) {
            // Both groups have fixed areas. Only the uncovered group determines its bird spacing.
            val coverWidth = maxWidth * 0.53f
            val coverHeight = minOf(maxHeight - 12.dp, coverWidth * 0.52f)
            val blueWidth = maxWidth * 0.43f
            val arriving = round.unknown == FriendsUnknown.START
            val visible = !arriving || phase != StoryPhase.SHOW_START
            if (visible) BirdGroup(round.visibleCount, false, false,
                Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp).width(blueWidth).height(coverHeight * 0.8f)
                    .graphicsLayer {
                        if (arriving && phase == StoryPhase.CHANGE) {
                            translationX = -400.dp.toPx() * (1f - flight)
                            translationY = -90.dp.toPx() * (1f - flight) - 24.dp.toPx() * sin(flight * PI).toFloat()
                        }
                    })
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 9.dp).width(coverWidth).height(coverHeight)) {
                if (revealed || outlines) BirdGroup(round.answer, true, !revealed,
                    Modifier.fillMaxSize().padding(horizontal = 22.dp))
                LeafCover(Modifier.fillMaxSize().graphicsLayer {
                    alpha = coverAlpha
                    if (!arriving && phase == StoryPhase.CHANGE) {
                        rotationZ = (sin(flight * PI * 6) * (1f - flight) * 3).toFloat()
                    }
                })
            }
            if (phase != StoryPhase.SHOW_START && !revealed) Box(
                Modifier.align(Alignment.TopStart).fillMaxWidth().height(60.dp).drawWithCache {
                    val from = if (arriving) 0.04f else 0.36f
                    val to = if (arriving) 0.3f else 0.59f
                    val arrow = Path().apply {
                        moveTo(size.width * from, size.height * 0.85f)
                        quadraticTo(size.width * (from + to) / 2, -size.height * 0.15f, size.width * to, size.height * 0.7f)
                    }
                    onDrawBehind {
                        drawPath(arrow, Color.White, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 9.dp.toPx()))))
                        val tip = Offset(size.width * to, size.height * 0.7f)
                        drawLine(Color.White, tip - Offset(14.dp.toPx(), 14.dp.toPx()), tip, 4.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White, tip - Offset(18.dp.toPx(), -2.dp.toPx()), tip, 4.dp.toPx(), StrokeCap.Round)
                    }
                })
            if (phase == StoryPhase.QUESTION || revealed) {
                Column(Modifier.align(Alignment.TopEnd).padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(round.result.toString(), Modifier.background(Color(0xFFFFFDF1), RoundedCornerShape(14.dp))
                        .padding(horizontal = 22.dp, vertical = 2.dp), color = Color(0xFF547448), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.size(16.dp, 10.dp).drawWithCache {
                        val tail = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }
                        onDrawBehind { drawPath(tail, Color(0xFFFFFDF1)) }
                    })
                }
            }
        }
    }
}

@Composable
private fun BirdGroup(count: Int, coral: Boolean, outlines: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val birdSize = minOf(76.dp, maxWidth / count, maxHeight)
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
            repeat(count) {
                if (outlines) Box(Modifier.size(birdSize).drawWithCache {
                    val bird = PathParser().parsePathString("M18,52 L4,40 L8,56 L4,68 L20,62 M14,56 C14,14 78,14 78,56 C78,98 14,98 14,56 Z M76,44 L96,50 L76,58 M40,86 L38,94 M50,86 L52,94 M24,52 Q38,36 52,58 Q36,74 24,52 M40,24 Q44,14 52,16 L46,26").toPath()
                    onDrawBehind {
                        scale(size.width / 100f, size.height / 100f, Offset.Zero) {
                            drawPath(bird, Color(0xFFFFE0CD), style = Stroke(3f, cap = StrokeCap.Round))
                            drawCircle(Color(0xFFFFE0CD), 4f, Offset(65f, 42f), style = Stroke(2f))
                        }
                    }
                }) else Image(painterResource(if (coral) R.drawable.ic_math_bird_coral else R.drawable.ic_count_bird),
                    null, Modifier.size(birdSize))
            }
        }
    }
}

@Composable
private fun LeafCover(modifier: Modifier) {
    Box(modifier.drawWithCache {
        val canopy = PathParser().parsePathString("M26,209 Q-4,172 20,136 Q-3,78 58,61 Q70,7 131,26 Q175,-9 220,25 Q281,-6 321,25 Q376,1 407,42 Q474,20 490,76 Q550,98 519,145 Q556,204 494,222 L73,225 Q41,231 26,209 Z").toPath()
        val leaf = PathParser().parsePathString("M4,34 Q12,-6 76,8 Q57,53 4,34 Z").toPath()
        onDrawBehind {
            scale(size.width / 540f, size.height / 225f, Offset.Zero) {
                drawPath(canopy, Color(0xFF5A9E47))
                drawPath(canopy, Color(0xFF4D8D3E), style = Stroke(4f))
                listOf(Offset(53f, 60f), Offset(182f, 25f), Offset(359f, 55f), Offset(85f, 151f), Offset(360f, 151f)).forEach { pos ->
                    withTransform({ translate(pos.x, pos.y) }) {
                        drawPath(leaf, Color(0xFF80BA50))
                        drawLine(Color(0xFF559443), Offset(8f, 33f), Offset(62f, 14f), 3f, StrokeCap.Round)
                    }
                }
            }
        }
    }, contentAlignment = Alignment.Center) {
        Text("?", Modifier.size(66.dp).background(Color(0xFFFFF7D7), CircleShape).drawWithCache {
            onDrawBehind { drawCircle(Color(0xFFD5B877), style = Stroke(3.dp.toPx())) }
        }.wrapContentSize(Alignment.Center), color = Color(0xFF735434), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
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
                            // Colour belongs to the bird, so it survives both landing and departure.
                            val bird = if (joining && !start) R.drawable.ic_math_bird_coral else R.drawable.ic_count_bird
                            if (occupied) Image(painterResource(bird), null,
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
