package io.github.tomsadeh.puki.learning

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.schedulerDataStore: DataStore<Preferences> by preferencesDataStore(name = "scheduler")

/** [SchedulerStore] on DataStore Preferences: one string per game, in its own file (not the progress one). */
class DataStoreSchedulerStore(private val context: Context) : SchedulerStore {
    companion object {
        private var visits: OrderedSchedulerStore? = null

        /** All reading visits share a queue whose lifetime is the application's. */
        fun visits(context: Context): OrderedSchedulerStore = synchronized(this) {
            visits ?: OrderedSchedulerStore(
                DataStoreSchedulerStore(context.applicationContext), CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ).also { visits = it }
        }
    }

    private fun prefKey(key: String) = stringPreferencesKey("scheduler_$key")

    override suspend fun load(key: String): SchedulerMemory =
        SchedulerMemory.decode(context.schedulerDataStore.data.first()[prefKey(key)])

    override suspend fun save(key: String, memory: SchedulerMemory) {
        context.schedulerDataStore.edit { it[prefKey(key)] = memory.encode() }
    }
}
