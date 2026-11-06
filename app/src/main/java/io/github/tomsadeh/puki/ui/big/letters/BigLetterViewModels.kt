package io.github.tomsadeh.puki.ui.big.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.learning.DataStoreSchedulerStore
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import io.github.tomsadeh.puki.learning.SchedulerVisit
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Wraps one letters game for a screen. The screen calls [start] once, observes [state], and sends
 * [onAnswer] / [replay] / [next]. It plays [LetterGameState.cue]
 * whenever its token changes, always in Hebrew. The ViewModel keeps the stars (GameSession) and saves
 * learning on completion and exposure before each new presentation is published.
 */
abstract class BigLetterGameViewModel(
    application: Application,
    island: String,
    private val memoryKey: String
) : AndroidViewModel(application) {
    private val store = DataStoreSchedulerStore.visits(application)
    val session = GameSession(island, (application as PukiApplication).progressRepository, viewModelScope)

    private val _state = MutableStateFlow(LetterGameState())
    val state: StateFlow<LetterGameState> = _state.asStateFlow()

    private var game: LetterGame? = null
    private var starting = false
    private var visit: SchedulerVisit? = null

    private val events = RoundEvents(
        onStar = { session.star() },
        onSlip = { session.slip() },
        onFinish = { rounds, memory ->
            session.finish(rounds)
            visit?.complete(memory)
        }
    )

    protected abstract fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents): LetterGame
    protected abstract fun startGame(game: LetterGame)

    /** Starts the first session (does nothing if one is already going, e.g. after a rotation). */
    fun start() {
        if (game != null || starting) return
        begin()
    }

    private fun begin() {
        starting = true
        viewModelScope.launch {
            val memory = runCatching { store.load(memoryKey) }.getOrDefault(SchedulerMemory())
            val random = Random.Default
            val nextScheduler = RoundScheduler(memory, random)
            val nextVisit = SchedulerVisit(nextScheduler, memoryKey, store)
            visit = nextVisit
            val g = createGame(nextScheduler, random, events)
            game = g
            _state.value = nextVisit.update {
                startGame(g)
                g.state
            }
            starting = false
        }
    }

    override fun onCleared() { visit?.save() }

    fun onAnswer(letterId: String) = act { it.onAnswer(letterId) }
    fun replay() = act { it.replay() }
    fun next() = act { it.next() }

    private fun act(action: (LetterGame) -> Unit) {
        val g = game ?: return
        _state.value = visit?.update {
            action(g)
            g.state
        } ?: return
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
