package io.github.tomsadeh.puki.ui.big.math

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.Door
import io.github.tomsadeh.puki.ui.components.DoorChooserScreen
import io.github.tomsadeh.puki.ui.components.Scene

/** The route owns its doors and a fresh ViewModel store for each visit/session. */
@Composable
fun BigMathScreen(onBack: () -> Unit) {
    val models: MathScreenModels = viewModel()
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    var session by rememberSaveable { mutableIntStateOf(0) }
    val doors: () -> Unit = { models.reset(); game = null }
    val choose: (String) -> Unit = { models.reset(); game = it }
    BackHandler(enabled = game != null, onBack = doors)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalLanguage provides Language.HEBREW) {
        if (game == null) {
            DoorChooserScreen(onBack, Scene.GARDEN, listOf(
                Door(BilingualText("חברים על הענף", "Friends on the branch"), Color(0xFFB6E4F5), { choose("friends") }) {
                    BranchDoor(Modifier.fillMaxSize(0.8f))
                },
                Door(BilingualText("סל הפיקניק", "Picnic basket"), Color(0xFFFFDF9E), { choose("picnic") }) {
                    PicnicDoor(Modifier.fillMaxSize(0.8f))
                },
                Door(BilingualText("מסגרת החמש", "Fill the frame"), Color(0xFFCDE5AD), { choose("frame") }) {
                    CounterFrame(capacity = 5, filled = 3, modifier = Modifier.fillMaxSize(0.82f))
                }
            ))
        } else key(game, session) {
            val app = LocalContext.current.applicationContext as Application
            val factory = remember(app) { ViewModelProvider.AndroidViewModelFactory.getInstance(app) }
            val voice = LocalVoicePlayer.current
            DisposableEffect(voice) {
                onDispose { voice.stop() }
            }
            val restart: () -> Unit = { models.reset(); session++ }
            when (game) {
                "friends" -> FriendsScreen(viewModel(viewModelStoreOwner = models, factory = factory), doors, restart)
                "picnic" -> PicnicScreen(viewModel(viewModelStoreOwner = models, factory = factory), doors, restart)
                "frame" -> FrameScreen(viewModel(viewModelStoreOwner = models, factory = factory), doors, restart)
            }
        }
    }
}

/** Retain the game's ViewModel on activity recreation, release it on leaving/restarting the game. */
internal class MathScreenModels : ViewModel(), ViewModelStoreOwner {
    override var viewModelStore = ViewModelStore()
        private set

    fun reset() { viewModelStore.clear(); viewModelStore = ViewModelStore() }
    override fun onCleared() { viewModelStore.clear() }
}
