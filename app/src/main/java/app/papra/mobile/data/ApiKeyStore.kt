package app.papra.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "papra_settings")

class ApiKeyStore(private val context: Context) {
    private val apiKeyPref = stringPreferencesKey("api_key")

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[apiKeyPref]
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyPref] = apiKey.trim()
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(apiKeyPref)
        }
    }
}
