package app.papra.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "papra_settings")

class ApiKeyStore(private val context: Context) {
    private val apiKeyPref = stringPreferencesKey("api_key")
    private val baseUrlPref = stringPreferencesKey("api_base_url")
    private val biometricPref = stringPreferencesKey("biometric_enabled")
    private val notificationsPref = stringPreferencesKey("notifications_enabled")
    private val lastSeenDocsPref = stringPreferencesKey("last_seen_docs")
    private val defaultOrgPref = stringPreferencesKey("default_org_id")

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[apiKeyPref]
    }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[baseUrlPref]
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[biometricPref]?.toBooleanStrictOrNull() ?: false
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notificationsPref]?.toBooleanStrictOrNull() ?: false
    }

    val defaultOrganizationIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[defaultOrgPref]
    }

    suspend fun saveSettings(apiKey: String, baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyPref] = apiKey.trim()
            prefs[baseUrlPref] = baseUrl.trim()
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[biometricPref] = enabled.toString()
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[notificationsPref] = enabled.toString()
        }
    }

    suspend fun setDefaultOrganizationId(organizationId: String?) {
        context.dataStore.edit { prefs ->
            if (organizationId.isNullOrBlank()) {
                prefs.remove(defaultOrgPref)
            } else {
                prefs[defaultOrgPref] = organizationId
            }
        }
    }

    suspend fun getApiKey(): String? {
        return context.dataStore.data.first()[apiKeyPref]
    }

    suspend fun getBaseUrl(): String? {
        return context.dataStore.data.first()[baseUrlPref]
    }

    suspend fun getNotificationsEnabled(): Boolean {
        return context.dataStore.data.first()[notificationsPref]?.toBooleanStrictOrNull() ?: false
    }

    suspend fun getLastSeenDocuments(): Map<String, String> {
        val raw = context.dataStore.data.first()[lastSeenDocsPref].orEmpty()
        if (raw.isBlank()) return emptyMap()
        val json = JSONObject(raw)
        return json.keys().asSequence().associateWith { key -> json.optString(key) }
    }

    suspend fun setLastSeenDocument(orgId: String, timestamp: String) {
        context.dataStore.edit { prefs ->
            val json = JSONObject(prefs[lastSeenDocsPref].orEmpty())
            json.put(orgId, timestamp)
            prefs[lastSeenDocsPref] = json.toString()
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(apiKeyPref)
            prefs.remove(baseUrlPref)
            prefs.remove(biometricPref)
            prefs.remove(notificationsPref)
            prefs.remove(lastSeenDocsPref)
            prefs.remove(defaultOrgPref)
        }
    }
}
