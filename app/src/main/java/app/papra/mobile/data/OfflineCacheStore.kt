package app.papra.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cacheDataStore by preferencesDataStore(name = "papra_cache")

class OfflineCacheStore(private val context: Context) {
    private val cachedDocIdsKey = stringSetPreferencesKey("cached_doc_ids")

    val cachedDocIdsFlow: Flow<Set<String>> = context.cacheDataStore.data.map { prefs ->
        prefs[cachedDocIdsKey] ?: emptySet()
    }

    suspend fun setCached(documentId: String, cached: Boolean) {
        context.cacheDataStore.edit { prefs ->
            val current = prefs[cachedDocIdsKey] ?: emptySet()
            prefs[cachedDocIdsKey] = if (cached) {
                current + documentId
            } else {
                current - documentId
            }
        }
    }
}
