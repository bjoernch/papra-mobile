package app.papra.mobile.data

import android.content.Context
import org.json.JSONArray

class SavedSearchStore(
    context: Context,
    private val organizationId: String
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(keyForOrg(), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs.edit().putString(keyForOrg(), array.toString()).apply()
    }

    private fun keyForOrg(): String = "saved_searches_$organizationId"

    companion object {
        private const val PREFS_NAME = "papra_saved_searches"
    }
}
