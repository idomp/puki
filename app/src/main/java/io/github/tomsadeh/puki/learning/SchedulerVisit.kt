package io.github.tomsadeh.puki.learning

/** Saves exposure before publishing a game's state, independently of its owner's disposal. */
internal class SchedulerVisit(
    private val scheduler: RoundScheduler,
    private val key: String,
    private val store: OrderedSchedulerStore
) {
    private var completedMemory: SchedulerMemory? = null
    private var savedMemory: SchedulerMemory? = null

    fun <S> update(action: () -> S): S {
        val state = action()
        save()
        return state
    }

    fun complete(memory: SchedulerMemory) {
        completedMemory = memory
        save()
    }

    fun save() {
        // A completed visit must never be replaced with the scheduler's pre-completion mastery.
        val memory = completedMemory ?: scheduler.checkpoint()
        if (memory == savedMemory) return
        store.save(key, memory)
        savedMemory = memory
    }
}
