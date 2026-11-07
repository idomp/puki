package io.github.tomsadeh.puki.ui.big.nikud

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

private val Context.nikudDataStore: DataStore<Preferences> by preferencesDataStore(name = "nikud")

/** The "meet the mark" cards seen, as a comma-separated string of card numbers. */
class DataStoreNikudCardStore(private val context: Context) : NikudCardStore {
    companion object {
        private var visits: OrderedNikudCardStore? = null

        /** "1,2" to {1, 2}; anything unreadable is skipped. Old values (before the /u/ card) read the same. */
        fun decode(text: String?): Set<Int> = text.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()

        fun encode(cards: Set<Int>): String = cards.sorted().joinToString(",")

        fun visits(context: Context): OrderedNikudCardStore = synchronized(this) {
            visits ?: OrderedNikudCardStore(
                DataStoreNikudCardStore(context.applicationContext), CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ).also { visits = it }
        }
    }

    private val key = stringPreferencesKey("cards_seen")

    override suspend fun seen(): Set<Int> =
        decode(context.nikudDataStore.data.first()[key])

    override suspend fun markSeen(card: Int) {
        context.nikudDataStore.edit { prefs ->
            val now = decode(prefs[key]) + card
            prefs[key] = encode(now)
        }
    }
}
