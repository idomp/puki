package io.github.tomsadeh.puki.ui.big.math

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// One delegate per process; separate from the shared settings/progress stores and their 3-level rule.
private val Context.bigMathDataStore: DataStore<Preferences> by preferencesDataStore(name = "big_math")

class DataStoreMathProgressStore(context: Context) : MathProgressStore {
    private val dataStore = context.applicationContext.bigMathDataStore
    private fun key(game: MathGameId) = stringPreferencesKey("stage_${game.island}")

    override suspend fun load(game: MathGameId): MathStage {
        val saved = dataStore.data.first()[key(game)]
        return MathStage.entries.firstOrNull { it.name == saved } ?: MathStage.JOIN_5
    }

    override suspend fun save(game: MathGameId, stage: MathStage) {
        dataStore.edit { it[key(game)] = stage.name }
    }
}

/** Problem keys never contain commas, so one comma-joined string per game is enough. */
class DataStoreMathHistoryStore(context: Context) : MathHistoryStore {
    private val dataStore = context.applicationContext.bigMathDataStore
    private fun key(game: MathGameId) = stringPreferencesKey("recent_${game.island}")

    override suspend fun load(game: MathGameId): List<String> =
        dataStore.data.first()[key(game)].orEmpty().split(',').filter { it.isNotEmpty() }

    override suspend fun save(game: MathGameId, recent: List<String>) {
        dataStore.edit { it[key(game)] = recent.joinToString(",") }
    }
}
