package io.github.tomsadeh.puki.ui.big.math

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.SpeakerIcon
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.components.*

private val readyLabel = BilingualText("מוכן", "Ready")

@Composable
internal fun PicnicScreen(viewModel: PicnicViewModel, onBack: () -> Unit, onRestart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paused = LocalGamePaused.current
    val sfx = LocalSfxPlayer.current
    val drag = remember(state.session.roundIndex) { DragDropState() }
    CompositionLocalProvider(LocalDragDropState provides drag) {
        MathRoundUi(state, state.round?.target ?: 0, state.feedback, viewModel::replay, viewModel::next, onBack, onRestart,
            pictureHint = true) { hint ->
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val round = state.round
                    if (round != null && round.request != PicnicRequest.EXACT) {
                        AppleMat(round.shownAmount, Modifier.fillMaxWidth().height(120.dp))
                    }
                    // Two countable rows, replenished after each drop so overfilling is possible.
                    val pileCount = minOf(10, (round?.target ?: 0) + 3)
                    val columns = (pileCount + 1) / 2
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val appleSize = minOf(100.dp, maxWidth / columns)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            repeat(2) { row ->
                                Row {
                                    repeat(minOf(columns, pileCount - row * columns)) { column ->
                                        val index = row * columns + column
                                        key(index, state.session.roundIndex, state.basketCount, paused, state.roundComplete) {
                                            if (!paused && !state.roundComplete && state.basketCount < state.basketLimit) {
                                                DragTarget("apple_$index", modifier = Modifier.size(appleSize), tapToZone = "basket",
                                                    onDragEnd = { zone ->
                                                        if (zone == "basket" && !viewModel.state.value.roundComplete) {
                                                            viewModel.addItem()
                                                            sfx.play(Sfx.DROP)
                                                        }
                                                    }, landed = { it == "basket" }) {
                                                    Apple(Modifier.size(appleSize))
                                                }
                                            } else Apple(Modifier.size(appleSize))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DropZone("basket", Modifier.size(260.dp, 200.dp)) {
                        BasketApples(state.basketCount, state.basketLimit, state.roundComplete, hint,
                            onRemove = { if (!paused && !state.roundComplete) { viewModel.removeItem(); sfx.play(Sfx.POP) } },
                            modifier = Modifier.fillMaxSize())
                    }
                    ReadyButton(enabled = !paused && !state.roundComplete, onReady = viewModel::ready)
                }
            }
        }
    }
}

@Composable
private fun ReadyButton(enabled: Boolean, onReady: () -> Unit) {
    Row(Modifier.height(100.dp).width(190.dp)
        .clip(RoundedCornerShape(28.dp)).background(BigColors.Right)
        .border(4.dp, Color.White, RoundedCornerShape(28.dp))
        .clickable(enabled = enabled, onClick = onReady),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).drawWithCache {
            val check = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.5f)
                lineTo(size.width * 0.4f, size.height * 0.8f)
                lineTo(size.width * 0.9f, size.height * 0.15f)
            }
            onDrawBehind { drawPath(check, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)) }
        })
        Text(readyLabel.he, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun BasketApples(count: Int, limit: Int, complete: Boolean, hint: Boolean, onRemove: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.clickable(enabled = !complete && count > 0, onClick = onRemove).drawWithCache {
        val rim = size.height * 0.12f
        val basket = Path().apply {
            moveTo(2.dp.toPx(), rim); lineTo(size.width - 2.dp.toPx(), rim)
            lineTo(size.width * 0.94f, size.height - 4.dp.toPx()); lineTo(size.width * 0.06f, size.height - 4.dp.toPx()); close()
        }
        onDrawBehind {
            drawPath(basket, Color(0xFFFFE7B1))
            // A light weave stays behind the clearly separated apples.
            repeat(7) { i ->
                val x = size.width * (i + 1) / 8
                drawLine(BigColors.Frame.copy(alpha = 0.35f), Offset(x, rim), Offset(x, size.height), 3.dp.toPx())
            }
            drawPath(basket, if (complete) BigColors.Right else BigColors.Post, style = Stroke(if (complete || hint) 7.dp.toPx() else 4.dp.toPx()))
            drawLine(BigColors.Post, Offset(0f, rim), Offset(size.width, rim), 10.dp.toPx(), StrokeCap.Round)
            drawArc(BigColors.Post, 180f, 180f, false, Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, rim * 2), style = Stroke(8.dp.toPx()))
        }
    }, contentAlignment = Alignment.BottomCenter) {
        val rows = (limit + 3) / 4
        val cell = minOf(56.dp, (maxWidth - 40.dp) / 4, (maxHeight - 44.dp) / rows)
        Column(Modifier.padding(bottom = 12.dp)) {
            repeat(rows) { row ->
                Row {
                    repeat(4) { column ->
                        val index = row * 4 + column
                        Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                            if (index < count) Apple(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleMat(shown: Int, modifier: Modifier) {
    Box(modifier.graphicsLayer().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF7E5)).drawWithCache {
        onDrawBehind {
            repeat(6) { i ->
                val x = size.width * i / 5
                drawLine(BigColors.Coral.copy(alpha = 0.16f), Offset(x, 0f), Offset(x, size.height), 8.dp.toPx())
            }
        }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat((shown + 4) / 5) { row ->
                Row {
                    repeat(minOf(5, shown - row * 5)) { Apple(Modifier.size(48.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun AppleHint(kind: MathFeedbackKind, modifier: Modifier = Modifier) {
    Row(modifier.background(Color.White, RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Apple(Modifier.size(44.dp))
        Text(if (kind == MathFeedbackKind.TOO_FEW) "+" else "−", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = BigColors.Blue)
        Box(Modifier.size(40.dp).background(BigColors.Coral, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { SpeakerIcon(26.dp) }
    }
}

@Composable
private fun Apple(modifier: Modifier = Modifier) { Image(painterResource(R.drawable.ic_count_apple), null, modifier) }

@Composable
internal fun PicnicDoor(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.55f).align(Alignment.BottomCenter).background(BigColors.Frame, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .border(5.dp, BigColors.Post, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)))
        Row(Modifier.fillMaxWidth().offset(y = (-12).dp), horizontalArrangement = Arrangement.Center) {
            repeat(2) { Apple(Modifier.weight(1f).aspectRatio(1f)) }
        }
    }
}
