package io.github.tomsadeh.puki.ui.big.math

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MathProgressStoreTest {
    @Test fun persistedStagesLoadWithinEachGamesCeiling() = runBlocking {
        for (game in MathGameId.entries) {
            val key = stringPreferencesKey("stage_${game.island}")
            assertEquals(MathStage.JOIN_5, storeWith(emptyPreferences()).load(game))
            assertEquals(MathStage.JOIN_5, storeWith(preferencesOf(key to "UNKNOWN")).load(game))
            for (stage in MathStage.entries) {
                // Seed the exact stored name so legacy PART saves exercise the load path.
                val store = storeWith(preferencesOf(key to stage.name))
                val expected = game.normalize(stage)
                assertEquals(expected, store.load(game))
                for (other in MathGameId.entries.filter { it != game })
                    assertEquals(MathStage.JOIN_5, store.load(other))
            }
        }
    }

    private fun storeWith(saved: Preferences) = DataStoreMathProgressStore(object : DataStore<Preferences> {
        override val data = flowOf(saved)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            error("Loading progress must not write preferences")
    })

    @Test fun inMemoryStagesLoadWithinEachGamesCeiling() = runBlocking {
        val store = InMemoryMathProgressStore()
        for (game in MathGameId.entries) for (stage in MathStage.entries) {
            store.save(game, stage)
            val expected = game.normalize(stage)
            assertEquals(expected, store.load(game))
        }
    }
}
