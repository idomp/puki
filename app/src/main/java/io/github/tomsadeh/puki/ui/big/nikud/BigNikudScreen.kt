package io.github.tomsadeh.puki.ui.big.nikud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.big.kit.BigColors
import io.github.tomsadeh.puki.ui.big.kit.SpeakerIcon
import io.github.tomsadeh.puki.ui.components.Door
import io.github.tomsadeh.puki.ui.components.DoorChooserScreen
import io.github.tomsadeh.puki.ui.components.Scene

private const val SHELLS = "shells"
private const val JOIN = "join"
private const val READ = "read"
private const val MEET = "meet"

/**
 * The Sounds and Words island (frame A2): three doors drawn as boards (a shell with the "heard" badge,
 * joining boards, a sign over its picture) and the chosen game inside the same composable. A small מַ
 * board in the corner opens "meet the mark" again, for a child who wants it. Hebrew only: RTL throughout.
 */
@Composable
fun BigNikudScreen(onBack: () -> Unit) {
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BackHandler(enabled = game != null) { game = null }
        val toDoors = { game = null }
        when (game) {
            SHELLS -> SoundShellsScreen(onBack = toDoors)
            JOIN -> JoinWordScreen(onBack = toDoors)
            READ -> ReadSignScreen(onBack = toDoors)
            MEET -> MeetMarkScreen(onBack = toDoors)
            else -> Box(Modifier.fillMaxSize()) {
                DoorChooserScreen(
                    onBack = onBack,
                    scene = Scene.PLAYGROUND,
                    doors = listOf(
                        Door(BilingualText("צדפי צלילים", "Sound shells"), BigColors.Coral, { game = SHELLS }) { ShellPicture() },
                        Door(BilingualText("פוקי מחבר מילה", "Puki joins a word"), BigColors.Blue, { game = JOIN }) { JoinPicture() },
                        Door(BilingualText("קוראים שלט", "Read the sign"), Color(0xFF6CC24A), { game = READ }) { SignPicture() }
                    )
                )
                MeetMarkButton(Modifier.align(Alignment.BottomStart).padding(24.dp)) { game = MEET }
            }
        }
    }
}

/** A small board with מַ: opens "meet the mark" from the doors. */
@Composable
private fun MeetMarkButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(104.dp).shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(BigColors.Frame)
            .padding(8.dp).clip(RoundedCornerShape(16.dp)).background(BigColors.Face)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("מַ", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = BigColors.Ink) }
}

/** A gold shell holding a syllable, with the coral "heard" badge. */
@Composable
private fun BoxScope.ShellPicture() {
    Box(Modifier.align(Alignment.Center).size(150.dp).clip(CircleShape).background(BigColors.Gold).border(6.dp, BigColors.GoldEdge, CircleShape),
        contentAlignment = Alignment.Center) {
        Text("דָּ", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4210))
    }
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 26.dp).size(44.dp).clip(CircleShape).background(BigColors.Coral),
        contentAlignment = Alignment.Center) { SpeakerIcon(26.dp) }
}

@Composable
private fun SmallBoard(text: String) {
    Box(
        Modifier.size(width = 62.dp, height = 72.dp).clip(RoundedCornerShape(12.dp)).background(BigColors.Frame).padding(4.dp)
            .clip(RoundedCornerShape(9.dp)).background(BigColors.Face),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = BigColors.Ink) }
}

/** Two small boards that become one sign. */
@Composable
private fun BoxScope.JoinPicture() {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SmallBoard("דָּ"); SmallBoard("ג") }
        Text("▼", fontSize = 22.sp, color = Color.White)
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(BigColors.Gold).padding(horizontal = 18.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) { Text("דָּג", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4210)) }
    }
}

/** A plain wooden sign over its picture. */
@Composable
private fun BoxScope.SignPicture() {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFC89B62)).padding(horizontal = 20.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) { Text("פִּיל", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = BigColors.Ink) }
        Image(painterResource(R.drawable.ic_animal_elephant), contentDescription = null, modifier = Modifier.size(84.dp))
    }
}
