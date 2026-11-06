package io.github.tomsadeh.puki.ui.big.nikud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.ui.big.kit.BeachBackdrop
import io.github.tomsadeh.puki.ui.big.kit.ListeningCorner
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.PlayVoiceCue
import io.github.tomsadeh.puki.ui.big.kit.SpeakerIcon
import io.github.tomsadeh.puki.ui.components.BackButton

/**
 * Meet the mark (frame F), a short unscored card in plain ink. Card 1: מָ and מַ on one double board
 * with one speaker between the faces: tapping either face or the speaker plays the same clip; the name
 * tags (קָמָץ, פַּתָּח) appear once it was heard and say their name when tapped. Card 2: מִ and חִירִיק.
 * The green button goes on (card 2, then [onFinished]). [startCard] 2 with [throughAll] false shows
 * card 2 alone (before stage 2). [onFinished] defaults to going back.
 */
@Composable
fun MeetMarkScreen(
    onBack: () -> Unit,
    startCard: Int = 1,
    throughAll: Boolean = true,
    onFinished: () -> Unit = onBack
) {
    val vm: MeetMarkViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    // (the ViewModel outlives the screen: ignore what a previous visit left in it until open() has run)
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(startCard, throughAll) { vm.open(startCard, throughAll); opened = true }
    LaunchedEffect(state.finished, opened) { if (opened && state.finished) onFinished() }
    PlayVoiceCue(if (opened) state.cue.keys else emptyList(), state.cue.token.toLong())

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize()) {
            BeachBackdrop()
            Box(Modifier.padding(16.dp)) { BackButton(onBack) }
            if (opened && state.card != 0 && state.faces.isNotEmpty()) {
                // Puki's listening corner on the reading side, as in every big-kids game; the board in the
                // middle of the beach; the go button on the far side, where the reading goes next
                Row(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 24.dp, top = 88.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ListeningCorner(onReplay = vm::onSpeakerTap, pulse = false)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DoubleBoard(state, vm) }
                    GoButton(112.dp, onClick = vm::onDone)
                }
            }
        }
    }
}

/** One wide board: the faces side by side with the speaker between them, name tags hanging below. */
@Composable
private fun DoubleBoard(state: MeetMarkState, vm: MeetMarkViewModel) {
    val faceWidth = 190.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(BigColors.Frame)
                .padding(14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BigColors.Face)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // one speaker sits between two faces; with one face it follows it
            state.faces.forEachIndexed { i, face ->
                Text(
                    face.text,
                    modifier = Modifier.width(faceWidth).clickable(remember { MutableInteractionSource() }, indication = null) { vm.onFaceTap(i) },
                    fontSize = 120.sp, fontWeight = FontWeight.Bold, color = BigColors.Ink,
                    textAlign = TextAlign.Center, lineHeight = 156.sp
                )
                if (i == 0 && state.faces.size > 1) Speaker(vm)
            }
            if (state.faces.size == 1) Speaker(vm)
        }
        // The posts and, once the sound was heard, the optional name tags
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.faces.forEachIndexed { i, face ->
                Box(Modifier.width(faceWidth + if (i == 0 && state.faces.size > 1) 56.dp else 0.dp).height(72.dp), contentAlignment = Alignment.TopCenter) {
                    if (state.namesVisible) {
                        Text(
                            face.markName,
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp)).background(Color.White)
                                .border(3.dp, BigColors.TagEdge, RoundedCornerShape(22.dp))
                                .clickable(remember { MutableInteractionSource() }, indication = null) { vm.onNameTap(i) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            fontSize = 40.sp, fontWeight = FontWeight.Bold, color = BigColors.TagInk
                        )
                    } else {
                        Box(Modifier.padding(top = 4.dp).width(16.dp).height(44.dp).background(BigColors.Post))
                    }
                }
            }
        }
    }
}

@Composable
private fun Speaker(vm: MeetMarkViewModel) {
    Box(
        Modifier.size(100.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(BigColors.Coral)
            .border(6.dp, Color.White, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = vm::onSpeakerTap),
        contentAlignment = Alignment.Center
    ) { SpeakerIcon(52.dp) }
}

/** The round green "go on" button with a white triangle pointing the way the page reads on (left in Hebrew). */
@Composable
private fun GoButton(size: Dp, onClick: () -> Unit) {
    Canvas(
        Modifier.size(size).shadow(6.dp, CircleShape).clip(CircleShape).background(BigColors.Right)
            .border(6.dp, Color.White, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        val u = this.size.width / 100f
        drawPath(Path().apply { moveTo(34 * u, 50 * u); lineTo(64 * u, 30 * u); lineTo(64 * u, 70 * u); close() }, Color.White)
    }
}
