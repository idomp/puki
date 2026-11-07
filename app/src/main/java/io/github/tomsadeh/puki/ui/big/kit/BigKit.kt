package io.github.tomsadeh.puki.ui.big.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.BackButton
import io.github.tomsadeh.puki.ui.components.hintGlow

/*
 * The big kids' games share one look (mockup option 3, "Explorer's chart, refined"): a beach under
 * the sky, shells at the top that keep what was found, still and upright boards for anything being
 * studied, and a listening corner (Puki in a captain's hat, with the replay button) that never moves.
 * Nothing being studied bobs, tilts or scatters; only presses squish.
 */

/**
 * The face for Hebrew in the big-kids reading games. Android's default Hebrew face puts the dagesh of
 * ג outside the letter; this one keeps every dagesh inside, and the parent chose it for keeping the
 * familiar print shapes (ש, מ) a child meets in kindergarten books.
 */
@OptIn(ExperimentalTextApi::class)
val ReadingFont: FontFamily = if (READING_FACE == "rubik")
    FontFamily(Font(R.font.rubik, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))
else
    FontFamily(Font(R.font.varela_round, FontWeight.Normal))
private const val READING_FACE = "rubik"

/** Wraps a reading island: right to left, and [ReadingFont] for every Text that doesn't pick its own face. */
@Composable
fun ReadingIsland(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = ReadingFont),
        content = content
    )
}

object BigColors {
    val Ink = Color(0xFF33241A)
    val Frame = Color(0xFFD9A866)
    val Post = Color(0xFFA57A4C)
    val Face = Color(0xFFFFFDF6)
    val Coral = Color(0xFFF07A5A)
    val Gold = Color(0xFFFFC93C)
    val GoldEdge = Color(0xFFE0A21B)
    val Right = Color(0xFF6CC24A)
    val Blue = Color(0xFF2F7FBF)
    val TagEdge = Color(0xFF8FC3DA)
    val TagInk = Color(0xFF3F6576)
    val Navy = Color(0xFF2E4C7E)
}

/** Sky, a strip of sea, and the sand the boards stand on. [horizon] is where the sand starts (0..1). */
@Composable
fun BeachBackdrop(modifier: Modifier = Modifier, horizon: Float = 0.45f) {
    Box(modifier.fillMaxSize().drawWithCache {
        val sandTop = size.height * horizon
        val seaTop = sandTop - size.height * 0.12f
        val sky = Brush.verticalGradient(listOf(Color(0xFF93D4F3), Color(0xFFD6F0FB)), endY = seaTop)
        val sand = Brush.verticalGradient(listOf(Color(0xFFF8E4B3), Color(0xFFEFCB86)), startY = sandTop)
        val shore = Path().apply {
            moveTo(0f, sandTop)
            quadraticTo(size.width * 0.25f, sandTop - 16f, size.width * 0.5f, sandTop)
            quadraticTo(size.width * 0.75f, sandTop + 16f, size.width, sandTop - 4f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        val foam = Path().apply {
            moveTo(0f, sandTop)
            quadraticTo(size.width * 0.25f, sandTop - 16f, size.width * 0.5f, sandTop)
            quadraticTo(size.width * 0.75f, sandTop + 16f, size.width, sandTop - 4f)
        }
        onDrawBehind {
            drawRect(sky)
            drawRect(Color(0xFF3AA6D3), topLeft = Offset(0f, seaTop), size = Size(size.width, sandTop - seaTop + 20f))
            drawRect(Color(0xFF6CC3E6), topLeft = Offset(0f, seaTop), size = Size(size.width, 10f))
            drawOval(Color.White, topLeft = Offset(size.width * 0.24f, size.height * 0.14f), size = Size(116f, 44f))
            drawOval(Color.White, topLeft = Offset(size.width * 0.27f, size.height * 0.11f), size = Size(72f, 48f))
            drawOval(Color.White, topLeft = Offset(size.width * 0.72f, size.height * 0.2f), size = Size(100f, 36f))
            drawPath(shore, sand)
            drawPath(foam, Color.White.copy(alpha = 0.85f), style = Stroke(width = 5.dp.toPx()))
        }
    })
}

/** A shell on the progress string: gold and holding what was found, ringed for now, pale for later. */
@Composable
private fun Shell(found: String?, current: Boolean, picture: Int? = null) {
    val filled = found != null || picture != null
    val size = if (filled || current) 40.dp else 26.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> BigColors.Gold
                    current -> Color.White
                    else -> Color.White.copy(alpha = 0.65f)
                }
            )
            .border(
                width = if (filled) 3.dp else if (current) 4.dp else 0.dp,
                color = if (filled) BigColors.GoldEdge else BigColors.Gold,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (picture != null) Image(painterResource(picture), contentDescription = null, modifier = Modifier.size(28.dp))
        else if (found != null) Text(found, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4210))
    }
}

/**
 * The top of every big-kids round: back (start side), the shells (centre, the first round on the
 * reading side), and the stars. [found] holds what each finished round found (its glyph), null for
 * rounds still to play; [current] is the round being played. A round that found a picture (a word
 * read) puts it in [pictures] instead: pointed text is never squeezed into a shell (its dots vanish).
 */
@Composable
fun BigTopBar(onBack: () -> Unit, found: List<String?>, current: Int, stars: Int, modifier: Modifier = Modifier, pictures: List<Int?> = emptyList()) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BackButton(onBack, Modifier.testTag("round-back"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            found.forEachIndexed { i, glyph ->
                val picture = pictures.getOrNull(i)
                Shell(glyph, current = i == current && glyph == null && picture == null, picture = picture)
            }
        }
        Row(
            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(painterResource(R.drawable.ic_ui_star), contentDescription = null, modifier = Modifier.size(26.dp))
            Text("$stars", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** A speaker drawn in white, for the replay button and the feedback bubble. */
@Composable
fun SpeakerIcon(size: Dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Box(modifier.size(size).drawBehind {
        val u = this.size.width / 60f
        val body = Path().apply {
            moveTo(8 * u, 22 * u); lineTo(20 * u, 22 * u); lineTo(36 * u, 8 * u); lineTo(36 * u, 52 * u)
            lineTo(20 * u, 38 * u); lineTo(8 * u, 38 * u); close()
        }
        drawPath(body, color)
        drawArc(color, -50f, 100f, false, topLeft = Offset(31 * u, 20 * u), size = Size(18 * u, 20 * u),
            style = Stroke(5 * u, cap = StrokeCap.Round))
        drawArc(color, -55f, 110f, false, topLeft = Offset(30 * u, 10 * u), size = Size(34 * u, 40 * u),
            style = Stroke(5 * u, cap = StrokeCap.Round))
    })
}

/** Puki's captain's hat, drawn over his picture (same box as the picture; sized to ic_companion_parrot's head). */
private fun Modifier.captainHat(): Modifier = drawBehind {
    val u = size.width / 120f
    val dx = -8 * u   // his head sits a little to the beak side of the box
    val dy = 9 * u    // and a little lower than the box's top
    val crown = Path().apply {
        moveTo(dx + 34 * u, dy + 30 * u); quadraticTo(dx + 40 * u, dy + 6 * u, dx + 62 * u, dy + 8 * u)
        quadraticTo(dx + 84 * u, dy + 6 * u, dx + 90 * u, dy + 30 * u); quadraticTo(dx + 62 * u, dy + 21 * u, dx + 34 * u, dy + 30 * u); close()
    }
    drawPath(crown, BigColors.Navy)
    drawLine(Color(0xFFF5C542), Offset(dx + 36 * u, dy + 28 * u), Offset(dx + 88 * u, dy + 28 * u), strokeWidth = 3 * u)
    drawCircle(Color(0xFFF5C542), radius = 4f * u, center = Offset(dx + 62 * u, dy + 16 * u))
}

/**
 * The listening corner: the coral replay button above Puki, on a soft pad, in the same place in every
 * big-kids game. [pulse] rings the button after a miss, as a reminder that he can hear it again.
 */
@Composable
fun ListeningCorner(onReplay: () -> Unit, pulse: Boolean, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = 300f), label = "replay")
    Column(
        modifier = modifier
            .width(150.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.28f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (pulse) Box(Modifier.size(148.dp).clip(CircleShape).background(BigColors.Coral.copy(alpha = 0.16f)))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(BigColors.Coral)
                    .border(7.dp, Color.White, CircleShape)
                    .clickable(press, indication = null, onClick = onReplay),
                contentAlignment = Alignment.Center
            ) { SpeakerIcon(60.dp) }
        }
        Box(Modifier.size(width = 120.dp, height = 150.dp)) {
            Image(painterResource(R.drawable.ic_companion_parrot), contentDescription = null, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().captainHat())
        }
    }
}

/** How a board shows its answer state. */
enum class BoardMark { NONE, WRONG, HINT, CORRECT }

/**
 * A still, upright board on a post, holding a letter, a pointed syllable or a number. A wrong tap
 * sinks it a little into the sand, greys it and hangs [nameTag] under it; HINT glows (after misses);
 * CORRECT turns its frame green. The only motion of its own is a squish under the finger.
 */
@Composable
fun AnswerBoard(
    text: String,
    mark: BoardMark,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 156.dp,
    height: Dp = 176.dp,
    nameTag: String? = null,
    textScale: Float = 0.67f,
    footer: (@Composable () -> Unit)? = null,
    /** A picture in place of the text (a word's meaning, in the reading games). */
    picture: Int? = null,
    /** An exact size for the text, where the height fraction doesn't fit (a pointed word on a wide board). */
    fontSize: TextUnit? = null,
    /** False for boards holding pointed text being read: no squish, the dots stay still under the finger. */
    squish: Boolean = true,
    textContent: (@Composable () -> Unit)? = null
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && squish) 0.92f else 1f, spring(dampingRatio = 0.4f, stiffness = 300f), label = "board")
    val sink by animateDpAsState(if (mark == BoardMark.WRONG) 12.dp else 0.dp, label = "sink")
    val wrong = mark == BoardMark.WRONG
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .offset(y = sink)
                .size(width, height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .hintGlow(mark == BoardMark.HINT)
                .shadow(if (wrong) 0.dp else 6.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when (mark) {
                        BoardMark.WRONG -> Color(0xFFD6CCBD)
                        BoardMark.CORRECT -> BigColors.Right
                        else -> BigColors.Frame
                    }
                )
                .padding(14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (wrong) Color(0xFFF1ECE4) else BigColors.Face)
                .clickable(press, indication = null, onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (textContent != null) {
                    textContent()
                } else if (picture != null) {
                    Image(
                        painterResource(picture), contentDescription = null,
                        modifier = Modifier.size(minOf(width, height) * 0.62f).graphicsLayer { alpha = if (wrong) 0.5f else 1f }
                    )
                } else {
                    val size = fontSize ?: with(LocalDensity.current) { (height * textScale).toSp() }
                    Text(
                        text,
                        fontSize = size,
                        fontWeight = FontWeight.Bold,
                        color = if (wrong) Color(0xFF9A8B7B) else BigColors.Ink,
                        textAlign = TextAlign.Center,
                        lineHeight = size * 1.3f
                    )
                }
                footer?.invoke()
            }
        }
        // The post into the sand, or, after a wrong tap, the name tag hanging below. A fixed height,
        // so a board that gets its tag doesn't shift its neighbours in the row
        Box(Modifier.height(64.dp), contentAlignment = Alignment.TopCenter) {
            if (wrong && nameTag != null) {
                Text(
                    nameTag,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .border(3.dp, BigColors.TagEdge, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BigColors.TagInk
                )
            } else {
                Box(Modifier.width(16.dp).height(30.dp).background(BigColors.Post))
            }
        }
    }
}

/**
 * Puki's bubble after a wrong tap: only what he tapped ("זו רֵישׁ") and a speaker. What we're looking
 * for is said, never written, so a child who starts to read can't match its first letter to a board.
 */
@Composable
fun FeedbackBubble(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    Row(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pointed syllables and words pass a larger size (48sp+): at 28sp their dots are hard to see
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = BigColors.Ink, lineHeight = fontSize * 1.3f)
        Box(Modifier.size(44.dp).clip(CircleShape).background(BigColors.Coral), contentAlignment = Alignment.Center) {
            SpeakerIcon(28.dp)
        }
    }
}

/**
 * Plays a game's voice cue whenever its [token] changes (the game logic bumps it for every new cue,
 * replays included). A new cue silences the one still playing, even an empty cue (a tap that found
 * the answer): a fast child never hears the last question over the next one. Hebrew only: the
 * big-kids games have no English recordings.
 */
@Composable
fun PlayVoiceCue(keys: List<String>, token: Long) {
    val voicePlayer = LocalVoicePlayer.current
    LaunchedEffect(token) {
        if (token == 0L) return@LaunchedEffect
        voicePlayer.stop()
        if (keys.isNotEmpty()) voicePlayer.playSequence(keys, Language.HEBREW)
    }
}

/**
 * The frame of a big-kids round: the beach, the top bar, the play area ([content], between the reading
 * edge and the listening corner), the listening corner on the far side, and [overlay] on top (the
 * feedback bubble, the end-of-session sheet).
 */
@Composable
fun BigRoundFrame(
    onBack: () -> Unit,
    found: List<String?>,
    current: Int,
    stars: Int,
    onReplay: () -> Unit,
    pulseReplay: Boolean,
    modifier: Modifier = Modifier,
    backdrop: @Composable () -> Unit = { BeachBackdrop() },
    overlay: @Composable BoxScope.() -> Unit = {},
    foundPictures: List<Int?> = emptyList(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        backdrop()
        Column(Modifier.fillMaxSize()) {
            BigTopBar(onBack, found, current, stars, pictures = foundPictures)
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                // In Hebrew (RTL) the corner sits on the right, where Puki always is
                ListeningCorner(onReplay, pulseReplay, Modifier.testTag("listening-corner"))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = content)
            }
        }
        overlay()
    }
}
