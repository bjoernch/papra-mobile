package app.papra.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "papra_settings")

class ApiKeyStore(private val context: Context) {
    private val apiKeyPref = stringPreferencesKey("api_key")
    private val baseUrlPref = stringPreferencesKey("api_base_url")
    private val biometricPref = stringPreferencesKey("biometric_enabled")
    private val notificationsPref = stringPreferencesKey("notifications_enabled")
    private val uploadWifiOnlyPref = stringPreferencesKey("upload_wifi_only")
    private val lastSeenDocsPref = stringPreferencesKey("last_seen_docs")
    private val defaultOrgPref = stringPreferencesKey("default_org_id")
    private val autoLockPref = stringPreferencesKey("auto_lock_minutes")
    private val hideRecentsPref = stringPreferencesKey("hide_in_recents")
    private val pinHashPref = stringPreferencesKey("pin_hash")

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

    val uploadWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[uploadWifiOnlyPref]?.toBooleanStrictOrNull() ?: true
    }

    val autoLockMinutesFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[autoLockPref]?.toIntOrNull() ?: 0
    }

    val hideInRecentsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[hideRecentsPref]?.toBooleanStrictOrNull() ?: false
    }

    val pinHashFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[pinHashPref]
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

    suspend fun setUploadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[uploadWifiOnlyPref] = enabled.toString()
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[autoLockPref] = minutes.toString()
        }
    }

    suspend fun setHideInRecents(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hideRecentsPref] = enabled.toString()
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

    suspend fun setPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[pinHashPref] = hashPin(pin)
        }
    }

    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(pinHashPref)
        }
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        return hashPin(pin) == storedHash
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

    suspend fun getUploadWifiOnly(): Boolean {
        return context.dataStore.data.first()[uploadWifiOnlyPref]?.toBooleanStrictOrNull() ?: true
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
            prefs.remove(uploadWifiOnlyPref)
            prefs.remove(autoLockPref)
            prefs.remove(hideRecentsPref)
            prefs.remove(lastSeenDocsPref)
            prefs.remove(defaultOrgPref)
            prefs.remove(pinHashPref)
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
