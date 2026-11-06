package io.github.tomsadeh.puki.ui.big.nikud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.data.models.ContentPace
import io.github.tomsadeh.puki.learning.DataStoreSchedulerStore
import io.github.tomsadeh.puki.learning.RoundScheduler
import io.github.tomsadeh.puki.learning.SchedulerMemory
import io.github.tomsadeh.puki.ui.big.letters.RoundEvents
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Wraps one scored Sounds-and-Words game for a screen: [start] once, observe [state], send the game's
 * events through the subclass's functions. The screen plays each state's `cue` (keys + token) in Hebrew.
 * Stars go to a GameSession; learning is saved on completion and exposure when the visit's owner is released.
 */
abstract class NikudGameViewModel<G, S>(
    application: Application,
    island: String,
    private val memoryKey: String,
    initial: S
) : AndroidViewModel(application) {
    private val store = DataStoreSchedulerStore.visits(application)
    private val cards = DataStoreNikudCardStore.visits(application)
    val session = GameSession(island, (application as PukiApplication).progressRepository, viewModelScope)

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    protected var game: G? = null
        private set
    private var starting = false
    private var scheduler: RoundScheduler? = null
    private var completedMemory: SchedulerMemory? = null

    private val events = RoundEvents(
        onStar = { session.star() },
        onSlip = { session.slip() },
        onFinish = { rounds, memory ->
            session.finish(rounds)
            completedMemory = memory
            store.save(memoryKey, memory)
        }
    )

    protected abstract fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents,
                                      cardsSeen: Set<Int>, eligibility: NikudEligibility): G
    protected abstract fun startGame(game: G)
    protected abstract fun snapshot(game: G): S

    fun start() {
        if (game != null || starting) return
        begin()
    }

    private fun begin() {
        starting = true
        viewModelScope.launch {
            val memory = runCatching { store.load(memoryKey) }.getOrDefault(SchedulerMemory())
            val seen = runCatching { cards.seen() }.getOrDefault(emptySet())
            val shells = if (memoryKey == "big_nikud_shells") memory else
                runCatching { store.load("big_nikud_shells") }.getOrDefault(SchedulerMemory())
            val join = if (memoryKey == "big_nikud_join") memory else
                runCatching { store.load("big_nikud_join") }.getOrDefault(SchedulerMemory())
            // The parent's pace is read as a session begins: a change applies to the next one, not a running one
            val pace = runCatching { (getApplication<Application>() as PukiApplication).settingsRepository.settings.first().contentPace }
                .getOrDefault(ContentPace.GRADUAL)
            val random = Random.Default
            val nextScheduler = RoundScheduler(memory, random)
            scheduler = nextScheduler
            completedMemory = null
            val g = createGame(nextScheduler, random, events, seen, NikudEligibility(shells, join, pace))
            game = g
            startGame(g)
            _state.value = snapshot(g)
            starting = false
        }
    }

    private fun saveVisit() {
        val memory = completedMemory ?: scheduler?.checkpoint() ?: return
        store.save(memoryKey, memory)
    }

    override fun onCleared() { saveVisit() }

    protected fun act(action: (G) -> Unit) {
        val g = game ?: return
        action(g)
        _state.value = snapshot(g)
    }

    /** A card was seen (from the shells' offer or the door): remember it. */
    fun rememberCard(card: Int) {
        cards.markSeen(card)
    }
}

class SoundShellsViewModel(application: Application) :
    NikudGameViewModel<SoundShellsGame, ShellsState>(application, "big_nikud_shells", "big_nikud_shells", ShellsState()) {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents,
                            cardsSeen: Set<Int>, eligibility: NikudEligibility) =
        SoundShellsGame(scheduler, random, events, cardsSeen, eligibility = eligibility)

    override fun startGame(game: SoundShellsGame) = game.start()
    override fun snapshot(game: SoundShellsGame) = game.state

    fun onAnswer(tileId: String) = act { it.onAnswer(tileId) }
    fun replay() = act { it.replay() }
    fun next() = act { it.next() }
}

class JoinWordViewModel(application: Application) :
    NikudGameViewModel<JoinWordGame, JoinState>(application, "big_nikud_join", "big_nikud_join", JoinState()) {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents,
                            cardsSeen: Set<Int>, eligibility: NikudEligibility) =
        JoinWordGame(scheduler, random, events, eligibility = eligibility)

    override fun startGame(game: JoinWordGame) = game.start()
    override fun snapshot(game: JoinWordGame) = game.state

    fun onUnitTap(index: Int) = act { it.onUnitTap(index) }
    fun onJoined() = act { it.onJoined() }
    fun onAnswer(wordId: String) = act { it.onAnswer(wordId) }
    fun replay() = act { it.replay() }
    fun next() = act { it.next() }
}

class ReadSignViewModel(application: Application) :
    NikudGameViewModel<ReadSignGame, ReadSignState>(application, "big_nikud_read", "big_nikud_read", ReadSignState()) {
    override fun createGame(scheduler: RoundScheduler, random: Random, events: RoundEvents,
                            cardsSeen: Set<Int>, eligibility: NikudEligibility) =
        ReadSignGame(scheduler, random, events, eligibility = eligibility)

    override fun startGame(game: ReadSignGame) = game.start()
    override fun snapshot(game: ReadSignGame) = game.state

    fun onAnswer(wordId: String) = act { it.onAnswer(wordId) }
    fun replay() = act { it.replay() }
    fun next() = act { it.next() }
}

/** The unscored "meet the mark" card. [open] starts a visit (card 1, or card 2 only); seen cards are remembered. */
class MeetMarkViewModel(application: Application) : AndroidViewModel(application) {
    private val cards = DataStoreNikudCardStore.visits(application)
    private val _state = MutableStateFlow(MeetMarkState())
    val state: StateFlow<MeetMarkState> = _state.asStateFlow()
    private var card: MeetMarkCard? = null

    fun open(startCard: Int = 1, throughAll: Boolean = true) {
        val c = MeetMarkCard(startCard, throughAll, cards::markSeen)
        card = c
        c.start()
        _state.value = c.state
    }

    private fun act(action: (MeetMarkCard) -> Unit) {
        val c = card ?: return
        action(c)
        _state.value = c.state
    }

    fun onFaceTap(index: Int = 0) = act { it.onFaceTap(index) }
    fun onSpeakerTap() = act { it.onSpeakerTap() }
    fun onNameTap(index: Int) = act { it.onNameTap(index) }
    fun onDone() = act { it.onDone() }

    /** For a screen that wants to know before the first session. */
    suspend fun seenCards(): Set<Int> = runCatching { cards.seen() }.getOrDefault(emptySet())
}
