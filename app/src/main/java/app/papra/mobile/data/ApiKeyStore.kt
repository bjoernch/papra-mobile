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
    private val baseUrlPref = stringPreferencesKey("api_base_url")

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[apiKeyPref]
    }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[baseUrlPref]
    }

    suspend fun saveSettings(apiKey: String, baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyPref] = apiKey.trim()
            prefs[baseUrlPref] = baseUrl.trim()
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(apiKeyPref)
            prefs.remove(baseUrlPref)
        }
    }
}
