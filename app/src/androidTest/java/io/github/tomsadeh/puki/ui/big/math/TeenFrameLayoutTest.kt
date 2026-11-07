package io.github.tomsadeh.puki.ui.big.math

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tomsadeh.puki.audio.SfxPlayer
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.ui.big.kit.ReadingIsland
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Landscape tablet: 1340 x 800, density 213. Uses the real round UI and sequence completion callback. */
@RunWith(AndroidJUnit4::class)
class TeenFrameLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private data class Case(val stage: MathStage, val target: Int, val filled: Int, val index: Int = 0,
                            val suffix: String = "")

    private fun game(case: Case): FrameGame = runBlocking {
        (0..1000).firstNotNullOf { seed ->
            val progress = InMemoryMathProgressStore().also { it.save(MathGameId.FRAME, case.stage) }
            FrameGame(Random(seed), progress).also { engine ->
                engine.start()
                repeat(case.index) { engine.onAnswer(engine.state.round!!.answer); engine.next() }
            }.takeIf { it.state.round!!.let { round -> round.target == case.target && round.filled == case.filled } }
        }
    }

    private fun fullScreen() {
        rule.runOnUiThread {
            val window = rule.activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        rule.mainClock.autoAdvance = false
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir, "ten-and-more-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val sceneTags = setOf("teen-frame-first", "teen-frame-second", "teen-target", "arrival-tray",
        "teen-equation", "full-ten-tag", "ones-card", "teen-boards", "listening-corner", "round-back")

    private fun assertNoOverlap(label: String) {
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = rule.onAllNodes(SemanticsMatcher("scene objects") {
            it.config.getOrNull(SemanticsProperties.TestTag) in sceneTags
        }, useUnmergedTree = true).fetchSemanticsNodes()
        val bounds = nodes.associate { it.config[SemanticsProperties.TestTag] to it.boundsInRoot }
        assertTrue(label, bounds.keys.containsAll(listOf("teen-frame-first", "teen-frame-second", "teen-boards", "listening-corner", "round-back")))
        for ((tag, rect) in bounds) {
            assertTrue("$label: $tag must have visible bounds: $rect", rect.width > 0f && rect.height > 0f)
            assertTrue("$label: $tag stays on screen: $rect", rect.left >= root.left - 1 && rect.top >= root.top - 1 &&
                rect.right <= root.right + 1 && rect.bottom <= root.bottom + 1)
        }
        for ((index, a) in bounds.entries.withIndex()) for (b in bounds.entries.drop(index + 1)) {
            // The ones card intentionally covers the zero of the ten tag; together they are the joined card.
            if (setOf(a.key, b.key) == setOf("full-ten-tag", "ones-card")) continue
            assertFalse("$label: ${a.key} ${a.value} overlaps ${b.key} ${b.value}", a.value.overlaps(b.value))
        }
    }

    @Test fun stagesAndBoundariesStaySeparateAndAdvanceOnlyAfterJoinAndVoice() {
        fullScreen()
        val cases = listOf(
            Case(MathStage.TEEN_COUNT, 13, 13), Case(MathStage.TEEN_CROSS, 13, 8),
            Case(MathStage.TEEN_MAKE, 13, 10), Case(MathStage.TEEN_PART, 13, 8),
            Case(MathStage.TEEN_COUNT, 20, 20, 2, "-boundary"),
            Case(MathStage.TEEN_CROSS, 18, 9, 3, "-boundary"),
            Case(MathStage.TEEN_MAKE, 13, 5, 3, "-boundary"),
            Case(MathStage.TEEN_PART, 18, 8, 0, "-boundary")
        )
        var engine = game(cases.first())
        var state by mutableStateOf(engine.state)
        var backCalls = 0
        var nextCalls = 0
        val voice = VoicePlayer(rule.activity).apply { enabled = false }
        val sfx = SfxPlayer(rule.activity).apply { enabled = false }
        rule.setContent {
            CompositionLocalProvider(LocalVoicePlayer provides voice, LocalSfxPlayer provides sfx) {
                ReadingIsland {
                    key(state.session.stage) {
                        FrameRoundScreen(state,
                            onAnswer = { engine.onAnswer(it); state = engine.state },
                            onReplay = { engine.replay(); state = engine.state },
                            onNext = { nextCalls++; runBlocking { engine.next() }; state = engine.state },
                            onBack = { backCalls++ }, onRestart = {})
                    }
                }
            }
        }
        try {
            for (case in cases) {
                rule.runOnIdle { engine = game(case); state = engine.state }
                val name = case.stage.name.lowercase() + case.suffix
                rule.mainClock.advanceTimeBy(1000)
                assertNoOverlap("$name arriving")
                if (case.stage == MathStage.TEEN_CROSS && case.suffix.isEmpty()) screenshot("teen_cross-arriving")
                rule.mainClock.advanceTimeBy(5000)
                rule.onNodeWithTag("ones-card").assertDoesNotExist()
                rule.onNodeWithContentDescription("Next round").assertDoesNotExist()
                if (case.stage == MathStage.TEEN_PART) {
                    rule.onNodeWithTag("teen-equation").assertIsDisplayed()
                    rule.onAllNodesWithText("?", useUnmergedTree = true).assertCountEquals(2)
                }
                assertNoOverlap("$name before")
                screenshot("$name-before")
                val backBefore = backCalls
                rule.onNodeWithTag("round-back").assertIsDisplayed().performTouchInput { click(center) }
                assertEquals(backBefore + 1, backCalls)
                // Hold a sentence longer than the animation, including its loading time. No audio is played.
                // isPlaying remains false, so checking that flag instead of the completion callback would fail.
                rule.runOnIdle { voice.enabled = true; voice.hold() }
                val answer = state.round!!.answer
                if (answer > 10) rule.onNodeWithContentDescription(answer.toString()).performClick()
                else rule.onNodeWithText(answer.toString(), useUnmergedTree = true).performClick()
                rule.mainClock.advanceTimeBy(100)
                rule.onNodeWithTag("round-back").assertIsDisplayed().performTouchInput { click(center) }
                assertEquals(backBefore + 2, backCalls)
                assertNoOverlap("$name immediately after success")
                if (case == cases.first()) screenshot("teen_count-success-back")
                val advancesBefore = nextCalls
                repeat(20) { step ->
                    rule.mainClock.advanceTimeBy(500)
                    assertNoOverlap("$name success step $step")
                }
                assertEquals(case.index, state.session.roundIndex)
                assertEquals(advancesBefore, nextCalls)
                assertFalse(voice.isPlaying())
                rule.onNodeWithTag("arrival-tray").assertDoesNotExist()
                if (case.target != 20) rule.onNodeWithTag("ones-card").assertIsDisplayed()
                screenshot("$name-after")
                rule.runOnIdle { voice.enabled = false; voice.unhold() }
                rule.mainClock.advanceTimeBy(1500)
                assertEquals(case.index + 1, state.session.roundIndex)
                assertEquals(advancesBefore + 1, nextCalls)
                rule.mainClock.advanceTimeBy(3000)
                assertEquals(advancesBefore + 1, nextCalls)
            }
        } finally { voice.release(); sfx.release() }
    }

    @Test fun shortOrMissingVoiceStillWaitsForJoinAndBackCanLeaveDuringSuccess() {
        fullScreen()
        val engine = game(Case(MathStage.TEEN_COUNT, 13, 13))
        var state by mutableStateOf(engine.state)
        var visible by mutableStateOf(true)
        var paused by mutableStateOf(false)
        var nextCalls = 0
        val voice = VoicePlayer(rule.activity).apply { enabled = false }
        val sfx = SfxPlayer(rule.activity).apply { enabled = false }
        rule.setContent {
            CompositionLocalProvider(LocalVoicePlayer provides voice, LocalSfxPlayer provides sfx, LocalGamePaused provides paused) {
                ReadingIsland {
                    if (visible) FrameRoundScreen(state,
                        onAnswer = { engine.onAnswer(it); state = engine.state },
                        onReplay = { engine.replay(); state = engine.state },
                        onNext = { nextCalls++; runBlocking { engine.next() }; state = engine.state },
                        onBack = { visible = false }, onRestart = {})
                    else Text("Left game")
                }
            }
        }
        try {
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithContentDescription("13").performClick()
            rule.mainClock.advanceTimeBy(2000)
            assertEquals(0, nextCalls) // Voice is complete; the join is still moving.
            rule.runOnIdle { engine.replay(); state = engine.state }
            rule.mainClock.advanceTimeBy(3000)
            assertEquals(0, nextCalls) // Replay must finish its own join, not use an old completion.
            rule.runOnIdle { paused = true }
            rule.mainClock.advanceTimeBy(10000)
            assertEquals(0, nextCalls)
            rule.runOnIdle { paused = false }
            rule.mainClock.advanceTimeBy(6000)
            assertEquals(1, nextCalls)
            // Leave the next success immediately using the usual Back control.
            rule.runOnIdle { engine.onAnswer(state.round!!.answer); state = engine.state }
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithTag("round-back").performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(10000)
            rule.onNodeWithText("Left game").assertIsDisplayed()
            assertEquals(1, nextCalls)
        } finally { voice.release(); sfx.release() }
    }
}
