package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.data.content.big.MathContent
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import kotlinx.coroutines.delay

@Composable
internal fun FrameScreen(viewModel: FrameViewModel, onBack: () -> Unit, onRestart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FrameRoundScreen(state, viewModel::onAnswer, viewModel::replay, viewModel::next, onBack, onRestart)
}

@Composable
internal fun FrameRoundScreen(state: FrameState, onAnswer: (Int) -> Unit, onReplay: () -> Unit,
                              onNext: () -> Unit, onBack: () -> Unit, onRestart: () -> Unit) {
    if (state.round?.isTeen == true) {
        TeenFrameRound(state, onAnswer, onReplay, onNext, onBack, onRestart)
        return
    }
    val paused = LocalGamePaused.current
    val fill = remember(state.session.roundIndex) { Animatable(0f) }
    LaunchedEffect(state.session.roundIndex, state.roundComplete, paused) {
        val round = state.round ?: return@LaunchedEffect
        if (state.roundComplete && !paused) {
            delay(100)
            fill.animateTo(round.missing.toFloat(), tween(1400))
        }
    }
    MathRoundUi(state, state.round?.answer ?: 0, state.feedback, onReplay, onNext, onBack, onRestart) { hint ->
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
                    showDots = round.isMakeN || state.session.stage < MathStage.MIXED_5, onAnswer = onAnswer)
            }
        }
    }
}

/** The first cell is the rightmost. Still empty cells remain visible throughout the question. */
@Composable
internal fun CounterFrame(capacity: Int, filled: Int, added: Float = 0f, complete: Boolean = false,
                          modifier: Modifier = Modifier, target: Int = capacity,
                          goldFull: Boolean = false, arrivingColor: Color? = null,
                          strongOutlines: Boolean = false, cellSize: Dp? = null, showTargetTag: Boolean = true) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val makeN = target < capacity
        val tagWidth = if (makeN && showTargetTag) 104.dp else 0.dp
        val cell = cellSize ?: minOf(100.dp, (maxWidth - 12.dp - tagWidth) / 5, (maxHeight - 12.dp) / (capacity / 5))
        // Keep the tag physically left, while the cells always fill from right to left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.Top) {
                if (makeN && showTargetTag) {
                    Box(Modifier.width(tagWidth).height(cell + 12.dp).drawWithContent {
                        drawLine(BigColors.Gold, Offset(80.dp.toPx(), size.height / 2),
                            Offset(size.width + 8.dp.toPx(), size.height / 2), 5.dp.toPx())
                        drawContent()
                    }, contentAlignment = Alignment.CenterStart) {
                        FrameTargetTag(target, Modifier.width(84.dp).height(minOf(82.dp, cell)))
                    }
                }
                Column(Modifier.drawWithContent {
                    if (goldFull) drawRoundRect(Brush.radialGradient(listOf(BigColors.Gold.copy(alpha = 0.65f), Color.Transparent),
                        radius = size.width * 0.65f), topLeft = Offset(-10.dp.toPx(), -10.dp.toPx()),
                        size = Size(size.width + 20.dp.toPx(), size.height + 20.dp.toPx()), cornerRadius = CornerRadius(18.dp.toPx()))
                    drawContent()
                }.background(BigColors.Face, RoundedCornerShape(12.dp))
                    .border(5.dp, if (goldFull) BigColors.Gold else if (complete && !makeN) BigColors.Right else BigColors.Post, RoundedCornerShape(12.dp)).padding(6.dp)
                    .drawWithContent {
                        drawContent()
                        if (makeN && target > 0) {
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
                            if (target < 5 && showTargetTag) drawLine(BigColors.Gold, Offset(0f, side / 2),
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
                                    .border(if (active && strongOutlines) 3.dp else 1.dp,
                                        if (active && strongOutlines) BigColors.Blue else if (active) BigColors.Frame else BigColors.Post.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center) {
                                    val arriving = if (active) (added - (index - filled)).coerceIn(0f, 1f) else 0f
                                    if (index < filled || arriving > 0f) {
                                        val counterColor = if (index < filled) BigColors.Blue else arrivingColor ?: if (makeN) BigColors.Coral else BigColors.Right
                                        Box(Modifier.fillMaxSize(0.7f).graphicsLayer {
                                            if (index >= filled) { translationY = -48.dp.toPx() * (1f - arriving); alpha = arriving }
                                        }.drawWithCache {
                                            onDrawBehind {
                                                drawCircle(if (index >= filled && arrivingColor != null) arrivingColor else if (makeN && index >= filled) BigColors.Coral else Color(0xFF21679C),
                                                    center = center + Offset(0f, 3.dp.toPx()))
                                                drawCircle(counterColor)
                                                drawCircle(Color.White.copy(alpha = 0.4f), size.minDimension * 0.12f, center - Offset(size.width * 0.15f, size.height * 0.18f))
                                            }
                                        })
                                    } else if (active) Box(Modifier.fillMaxSize(0.65f).drawWithCache {
                                        onDrawBehind { drawCircle(if (strongOutlines) BigColors.Blue else BigColors.Frame.copy(alpha = 0.4f),
                                            style = Stroke((if (strongOutlines) 4.dp else 2.dp).toPx())) }
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

private val ArrivalTeal = Color(0xFF27A88E)

/** A ten is always pictured as a gold frame, and loose ones as blue dots. */
@Composable
internal fun TeenNumeral(number: Int, fontSize: TextUnit = 54.sp) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Top,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = number.toString() }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(43.dp)) {
                Text((number / 10).toString(), fontSize = fontSize, fontWeight = FontWeight.ExtraBold, color = BigColors.GoldEdge)
                repeat(number / 10) { MiniTen(); Spacer(Modifier.height(3.dp)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(43.dp)) {
                Text((number % 10).toString(), fontSize = fontSize, fontWeight = FontWeight.ExtraBold, color = BigColors.Ink)
                LooseDots(number % 10)
            }
        }
    }
}

@Composable
private fun MiniTen() {
    Canvas(Modifier.size(32.dp, 15.dp)) {
        drawRoundRect(BigColors.Gold, cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(BigColors.GoldEdge, style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
        val cell = size.width / 5
        repeat(10) { i -> drawCircle(BigColors.Blue, cell * 0.28f,
            Offset((i % 5 + 0.5f) * cell, size.height * (if (i < 5) 0.28f else 0.72f))) }
    }
}

@Composable
private fun LooseDots(number: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat((number + 4) / 5) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(minOf(5, number - row * 5)) {
                    Box(Modifier.size(5.dp).background(BigColors.Blue, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
    }
}

@Composable
private fun TeenFrameRound(state: FrameState, onAnswer: (Int) -> Unit, onReplay: () -> Unit,
                           onNext: () -> Unit, onBack: () -> Unit, onRestart: () -> Unit) {
    val round = requireNotNull(state.round)
    val paused = LocalGamePaused.current
    val key = state.session.roundIndex to round.historyKey
    val arrivals = remember(key) { Animatable(0f) }
    val join = remember(key) { Animatable(0f) }
    var showOnes by remember(key) { mutableStateOf(false) }
    var joinedVoiceToken by remember(key) { mutableLongStateOf(-1L) }
    val moving = round.kind == FrameKind.CROSS || round.kind == FrameKind.PART
    // Replay after success repeats the card join. Pausing suspends both movements in place.
    LaunchedEffect(key, state.roundComplete, paused, if (state.roundComplete) state.voice.token else 0L) {
        if (paused) return@LaunchedEffect
        if (moving && (round.kind == FrameKind.CROSS || state.roundComplete)) {
            val remaining = round.missing - arrivals.value
            if (remaining > 0f) {
                delay(600)
                arrivals.animateTo(round.missing.toFloat(), tween((remaining * 450).toInt()))
            }
        } else if (round.kind == FrameKind.MAKE && state.roundComplete) {
            arrivals.animateTo(round.missing.toFloat(), tween(1800))
        }
        if (state.roundComplete) {
            joinedVoiceToken = -1L
            showOnes = false
            join.snapTo(0f)
            delay(500)
            showOnes = true
            delay(1000)
            join.animateTo(1f, tween(2600))
            joinedVoiceToken = state.voice.token
        }
    }
    val questionReady = round.kind != FrameKind.CROSS || arrivals.value >= round.missing
    MathRoundUi(state, round.answer, state.feedback, onReplay, onNext, onBack, onRestart,
        questionActive = questionReady, completionReady = joinedVoiceToken == state.voice.token,
        successVoiceKeys = listOf(MathContent.exact.key, MathContent.teenNumbers.getValue(round.target).key)) { hint ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                TeenFrames(round, arrivals.value, showOnes, join.value, state.session.showHint, state.equation)
            }
            Box(Modifier.height(184.dp), contentAlignment = Alignment.TopCenter) {
                NumberBoards(state.choices, round.answer, state.roundComplete, state.feedback, hint, true,
                    onAnswer = { if (questionReady) onAnswer(it) }, modifier = Modifier.testTag("teen-boards"))
            }
        }
    }
}

/** The same cells as the original frame, continued RTL through four rows. */
@Composable
private fun TeenFrames(round: FrameRound, added: Float, showOnes: Boolean, join: Float, strongOutlines: Boolean,
                       equation: Equation?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = minOf(100.dp, (maxHeight - 48.dp) / 4, (maxWidth - 350.dp) / 5).coerceAtLeast(24.dp)
        val fw = cell * 5 + 12.dp
        val fh = cell * 2 + 12.dp
        val gap = 24.dp
        val y2 = fh + gap
        val frameX = 96.dp
        val tagX = frameX + fw + 22.dp
        val tagY = (fh - 96.dp) / 2
        val onesY = y2
        val trayCell = 34.dp
        // A covered tray keeps a fixed size so its shape cannot give away the hidden count.
        val trayRows = if (round.kind == FrameKind.PART || round.missing > 5) 2 else 1
        val trayY = y2
        val sideWidth = 244.dp
        val isTray = round.kind == FrameKind.CROSS || round.kind == FrameKind.PART
        val arrived = if (isTray) kotlin.math.floor(added) else added
        val totalShown = round.filled + arrived
        val target = if (round.isMakeN) round.target else 20
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.size(tagX + sideWidth, fh * 2 + gap)) {
                if (round.kind == FrameKind.MAKE || round.kind == FrameKind.PART) {
                    Column(Modifier.offset(y = y2 + 4.dp).size(84.dp, 110.dp)
                        .testTag("teen-target").background(BigColors.Gold, RoundedCornerShape(22.dp))
                        .border(4.dp, BigColors.GoldEdge, RoundedCornerShape(22.dp)).padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(round.target.toString(), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = BigColors.Ink)
                        // This card is the goal; the frame tag instead carries the tiny ten-frame picture.
                        Canvas(Modifier.size(38.dp, 12.dp)) {
                            drawLine(BigColors.GoldEdge, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
                        }
                    }
                }
                CounterFrame(10, minOf(10, round.filled), arrived, modifier = Modifier.offset(x = frameX).size(fw, fh).testTag("teen-frame-first"),
                    goldFull = totalShown >= 10, arrivingColor = ArrivalTeal, cellSize = cell, showTargetTag = false)
                CounterFrame(10, (round.filled - 10).coerceAtLeast(0),
                    (arrived - (10 - round.filled).coerceAtLeast(0)).coerceAtLeast(0f),
                    modifier = Modifier.offset(x = frameX, y = y2).size(fw, fh).testTag("teen-frame-second"), target = (target - 10).coerceIn(0, 10),
                    goldFull = totalShown >= 20, arrivingColor = ArrivalTeal, strongOutlines = strongOutlines,
                    cellSize = cell, showTargetTag = false)
                if (totalShown >= 10) {
                    Column(Modifier.offset(x = tagX, y = tagY).size(108.dp, 96.dp).testTag("full-ten-tag")
                        .background(BigColors.Gold, RoundedCornerShape(12.dp))
                        .border(3.dp, BigColors.GoldEdge, RoundedCornerShape(12.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (round.target == 20 && join >= 1f) TeenNumeral(20, 42.sp)
                        else TeenNumeral(10, 42.sp)
                    }
                }
                if (isTray && !showOnes) {
                    ArrivalTray(round, added, Modifier.offset(x = tagX, y = trayY).size(trayCell * 5 + 12.dp, trayCell * trayRows + 12.dp))
                    // Two visible routes: the first group closes the ten; the rest continues into frame two.
                    Canvas(Modifier.fillMaxSize()) {
                        val tx = tagX.toPx(); val fy = fh.toPx(); val sy = y2.toPx()
                        val route = Path().apply {
                            moveTo(tx + 130.dp.toPx(), trayY.toPx()); lineTo(tx + 130.dp.toPx(), fy + gap.toPx() / 2)
                            lineTo(frameX.toPx() + cell.toPx(), fy + gap.toPx() / 2); lineTo(frameX.toPx() + cell.toPx(), fy - 8.dp.toPx())
                            moveTo(tx, sy + cell.toPx() / 2 + 6.dp.toPx()); lineTo((frameX + fw).toPx(), sy + cell.toPx() / 2 + 6.dp.toPx())
                        }
                        drawPath(route, ArrivalTeal.copy(alpha = 0.65f), style = Stroke(2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
                        if (added > 0 && added < round.missing && added % 1f > 0f) {
                            val i = added.toInt()
                            val progress = added % 1f
                            val index = round.filled + i
                            val closing = 10 - round.filled
                            val slot = if (i < closing) round.missing - closing + i else i - closing
                            val start = Offset(tx + 6.dp.toPx() + ((slot % 5) + 0.5f) * trayCell.toPx(),
                                trayY.toPx() + 6.dp.toPx() + ((slot / 5) + 0.5f) * trayCell.toPx())
                            val end = Offset(frameX.toPx() + 6.dp.toPx() + (4 - index % 5 + 0.5f) * cell.toPx(),
                                (if (index < 10) 0f else sy) + 6.dp.toPx() + ((index % 10) / 5 + 0.5f) * cell.toPx())
                            val bend = if (index < 10) Offset(start.x, fy + gap.toPx() / 2) else Offset(tx, end.y)
                            val secondBend = if (index < 10) Offset(end.x, bend.y) else Offset((frameX + fw).toPx(), end.y)
                            val center = when {
                                progress < 0.25f -> start + (bend - start) * (progress / 0.25f)
                                progress < 0.8f -> bend + (secondBend - bend) * ((progress - 0.25f) / 0.55f)
                                else -> secondBend + (end - secondBend) * ((progress - 0.8f) / 0.2f)
                            }
                            drawCircle(ArrivalTeal, cell.toPx() * 0.35f, center)
                            drawCircle(Color.White.copy(alpha = 0.4f), cell.toPx() * 0.08f, center - Offset(5.dp.toPx(), 5.dp.toPx()))
                        }
                    }
                }
                if (showOnes && !(round.target == 20 && join >= 1f)) {
                    Column(Modifier.offset(x = tagX + 54.dp * join, y = onesY + (tagY - onesY) * join)
                        .size(54.dp, 84.dp).testTag("ones-card")
                        .background(BigColors.Face, RoundedCornerShape(10.dp))
                        .border(2.dp, BigColors.Post, RoundedCornerShape(10.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text((round.target - 10).toString(), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = BigColors.Ink)
                        if (round.target == 20) MiniTen() else LooseDots(round.target % 10)
                    }
                }
                // The tray (or the ones card) uses the top of this lane; the equation has its own bottom slot.
                Box(Modifier.offset(x = tagX, y = y2).size(sideWidth, fh).padding(bottom = 4.dp)) {
                    equation?.let {
                        EquationSign(it.a, it.op, it.b?.toString() ?: "?", it.result.toString(),
                            Modifier.align(Alignment.BottomStart).testTag("teen-equation"), arrivingColor = ArrivalTeal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalTray(round: FrameRound, added: Float, modifier: Modifier) {
    val covered = round.kind == FrameKind.PART && added == 0f
    Box(modifier.testTag("arrival-tray").background(BigColors.Post, RoundedCornerShape(10.dp))
        .border(3.dp, BigColors.Frame, RoundedCornerShape(10.dp)).padding(6.dp), contentAlignment = Alignment.Center) {
        if (covered) {
            Text("?", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = BigColors.Ink)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val side = size.width / 5
                val closing = 10 - round.filled
                // Counters nearest the frame are the loose ones; the far group completes the ten.
                val order = (closing until round.missing).toList() + (0 until closing).toList()
                order.forEachIndexed { slot, i ->
                    val center = Offset((slot % 5 + 0.5f) * side, (slot / 5 + 0.5f) * side)
                    drawRect(BigColors.Face, Offset((slot % 5) * side + 1.dp.toPx(), (slot / 5) * side + 1.dp.toPx()),
                        Size(side - 2.dp.toPx(), side - 2.dp.toPx()))
                    drawCircle(ArrivalTeal.copy(alpha = 0.3f), side * 0.37f, center, style = Stroke(1.5.dp.toPx()))
                    if (added <= i) {
                        drawCircle(ArrivalTeal, side * 0.33f, center)
                        drawCircle(Color.White.copy(alpha = 0.4f), side * 0.08f, center - Offset(side * 0.1f, side * 0.1f))
                    }
                }
                val rest = round.missing - closing
                val dividerX = (rest % 5) * side
                val divider = Color(0xFF714820)
                if (rest < 5) drawLine(divider, Offset(dividerX, -4.dp.toPx()), Offset(dividerX, side + 4.dp.toPx()), 5.dp.toPx())
                else if (rest == 5) drawLine(divider, Offset(-4.dp.toPx(), side), Offset(size.width + 4.dp.toPx(), side), 5.dp.toPx())
                else drawLine(divider, Offset(dividerX, side - 4.dp.toPx()), Offset(dividerX, size.height + 4.dp.toPx()), 5.dp.toPx())
            }
        }
    }
}
