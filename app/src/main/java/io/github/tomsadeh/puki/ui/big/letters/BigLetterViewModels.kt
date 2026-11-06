package io.github.tomsadeh.puki.ui.big.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.learning.DataStoreSchedulerStore
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import io.github.tomsadeh.puki.learning.SchedulerStore
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Wraps one letters game for a screen. The screen calls [start] once, observes [state], and sends
 * [onAnswer] / [replay] / [next] (and [restart] for another session). It plays [LetterGameState.cue]
 * whenever its token changes, always in Hebrew. The ViewModel keeps the stars (GameSession) and saves
 * what the scheduler learned when a session ends.
 */
abstract class BigLetterGameViewModel(
    application: Application,
    island: String,
    private val memoryKey: String
) : AndroidViewModel(application) {
    private val store: SchedulerStore = DataStoreSchedulerStore(application)
    val session = GameSession(island, (application as PukiApplication).progressRepository, viewModelScope)

    private val _state = MutableStateFlow(LetterGameState())
    val state: StateFlow<LetterGameState> = _state.asStateFlow()

    private var game: LetterGame? = null
    private var starting = false

    private val events = RoundEvents(
        onStar = { session.star() },
        onSlip = { session.slip() },
        onFinish = { rounds, memory ->
            session.finish(rounds)
            viewModelScope.launch { runCatching { store.save(memoryKey, memory) } }
        }
    )

    protected abstract fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents): LetterGame
    protected abstract fun startGame(game: LetterGame)

    /** Starts the first session (does nothing if one is already going, e.g. after a rotation). */
    fun start() {
        if (game != null || starting) return
        begin()
    }

    /** A new session (the memory is read again, so it includes the last one). */
    fun restart() {
        if (starting) return
        begin()
    }

    private fun begin() {
        starting = true
        viewModelScope.launch {
            val memory = runCatching { store.load(memoryKey) }.getOrDefault(SchedulerMemory())
            val random = Random.Default
            val g = createGame(RoundScheduler(memory, random), random, events)
            game = g
            startGame(g)
            _state.value = g.state
            starting = false
        }
    }

    fun onAnswer(letterId: String) = act { it.onAnswer(letterId) }
    fun replay() = act { it.replay() }
    fun next() = act { it.next() }

    private fun act(action: (LetterGame) -> Unit) {
        val g = game ?: return
        action(g)
        _state.value = g.state
    }
}

class ListenFindViewModel(application: Application) :
    BigLetterGameViewModel(application, "big_letters_listen", "big_letters_listen") {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents): LetterGame =
        ListenFindGame(scheduler, events)

    override fun startGame(game: LetterGame) = (game as ListenFindGame).start()
}

class LetterDetectiveViewModel(application: Application) :
    BigLetterGameViewModel(application, "big_letters_detective", "big_letters_detective") {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents): LetterGame =
        LetterDetectiveGame(scheduler, random, events)

    override fun startGame(game: LetterGame) = (game as LetterDetectiveGame).start()
}

class FinalPartnersViewModel(application: Application) :
    BigLetterGameViewModel(application, "big_letters_finals", "big_letters_finals") {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents): LetterGame =
        FinalPartnersGame(scheduler, events)

    override fun startGame(game: LetterGame) = (game as FinalPartnersGame).start()
}
