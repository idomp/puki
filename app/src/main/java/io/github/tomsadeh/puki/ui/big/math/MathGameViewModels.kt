package io.github.tomsadeh.puki.ui.big.math

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Serializes events with loading/saving; stars and shared session records are awarded once. */
abstract class MathGameViewModel<S : MathState>(
    application: Application,
    protected val game: MathGame<S>
) : AndroidViewModel(application) {
    val session = GameSession(game.gameId.island, (application as PukiApplication).progressRepository, viewModelScope)
    private val events = Mutex()
    private val mutableState = MutableStateFlow(game.state)
    val state: StateFlow<S> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            events.withLock {
                game.start()
                mutableState.value = game.state
            }
        }
    }

    protected fun event(action: suspend () -> Unit) {
        if (mutableState.value.session.isLoading || mutableState.value.session.sessionComplete) return
        viewModelScope.launch {
            events.withLock {
                val before = game.state.session
                action()
                val after = game.state.session
                repeat(after.misses - before.misses) { session.slip() }
                repeat(after.totalStarsEarned - before.totalStarsEarned) { session.star() }
                if (!before.sessionComplete && after.sessionComplete) session.finish(after.totalRounds)
                mutableState.value = game.state
            }
        }
    }

    /** Replay requests new audio with a new token; the screen applies language/sound settings. */
    fun replay() = event { game.replay() }
    fun next() = event { game.next() }
}

class FriendsViewModel(application: Application) : MathGameViewModel<FriendsState>(
    application, FriendsGame(Random.Default, DataStoreMathProgressStore(application))
) {
    fun advancePhase() = event { (game as FriendsGame).advancePhase() }
    fun onAnswer(number: Int) = event { (game as FriendsGame).onAnswer(number) }
}

class PicnicViewModel(application: Application) : MathGameViewModel<PicnicState>(
    application, PicnicGame(Random.Default, DataStoreMathProgressStore(application))
) {
    fun addItem() = event { (game as PicnicGame).addItem() }
    fun removeItem() = event { (game as PicnicGame).removeItem() }
    fun ready() = event { (game as PicnicGame).ready() }
}

class FrameViewModel(application: Application) : MathGameViewModel<FrameState>(
    application, FrameGame(Random.Default, DataStoreMathProgressStore(application))
) {
    fun onAnswer(number: Int) = event { (game as FrameGame).onAnswer(number) }
}
