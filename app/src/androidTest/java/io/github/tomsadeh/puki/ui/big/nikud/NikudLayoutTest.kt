package io.github.tomsadeh.puki.ui.big.nikud

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tomsadeh.puki.data.content.big.Nikud
import io.github.tomsadeh.puki.ui.big.kit.BigRoundFrame
import io.github.tomsadeh.puki.ui.big.kit.ReadingIsland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run on the landscape tablet profile: 1340 x 800 pixels, density 213. */
@RunWith(AndroidJUnit4::class)
class NikudLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun fullScreen() {
        rule.runOnUiThread {
            val window = rule.activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun assertReadable(text: String) {
        val node = rule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.size)
        assertEquals(100.sp, results.single().layoutInput.style.fontSize)
        assertEquals(1, results.single().lineCount)
        val result = results.single()
        // A centered paragraph can fill the board even when Text wraps its glyphs. Check the line.
        val lineWidth = result.getLineRight(0) - result.getLineLeft(0)
        assertTrue("$text: $lineWidth in ${result.size.width}", lineWidth <= result.size.width + 1f)
        assertTrue(text, result.multiParagraph.height <= result.size.height + 1f)
        val area = rule.onNodeWithTag("play-area").fetchSemanticsNode().boundsInRoot
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$text stays clear of the listening corner", area.contains(bounds.topLeft) && area.contains(bounds.bottomRight))
    }

    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir, "nikud-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun threeBoardWordsAndTwoBoardWordsFitBesideTheListeningCorner() {
        fullScreen()
        var word by mutableStateOf(Nikud.word("gamal"))
        rule.setContent {
            ReadingIsland {
                BigRoundFrame({}, List(8) { null }, 0, 0, {}, false) {
                    Box(Modifier.fillMaxSize().testTag("play-area")) {
                        JoinScene(JoinState(phase = JoinPhase.UNITS, wordText = word.text,
                            units = word.units.map { JoinUnit(it.text, it.kind, it.clipKey) }),
                            0f, false, false, {}, {}, {})
                    }
                }
            }
        }
        for (id in listOf("gamal", "panas", "banana", "tiras", "sira", "mapa")) {
            rule.runOnIdle { word = Nikud.word(id) }
            screenshot("boards-$id")
            word.units.forEach { assertReadable(it.text) }
        }
    }

    @Test
    fun joinedAndSilentSignsKeepLongWordsOnOneLine() {
        fullScreen()
        var word by mutableStateOf(Nikud.word("banana"))
        var read by mutableStateOf(false)
        rule.setContent {
            ReadingIsland {
                BigRoundFrame({}, List(8) { null }, 0, 0, {}, false) {
                    Box(Modifier.fillMaxSize().testTag("play-area")) {
                        val pictures = listOf(word, Nikud.word("dag"), Nikud.word("pil"))
                            .map { PictureTile(it.id, it.pictureId!!) }
                        if (read) ReadScene(ReadSignState(signText = word.text, pictures = pictures), false, {}, {})
                        else JoinScene(JoinState(phase = JoinPhase.PICTURE, wordText = word.text, pictures = pictures),
                            1f, true, false, {}, {}, {})
                    }
                }
            }
        }
        for (isRead in listOf(false, true)) for (id in listOf("gamal", "panas", "banana", "tiras")) {
            rule.runOnIdle { word = Nikud.word(id); read = isRead }
            screenshot("sign-$id-$isRead")
            assertReadable(word.text)
        }
    }
}
