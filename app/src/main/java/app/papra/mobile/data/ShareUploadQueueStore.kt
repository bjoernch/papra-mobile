package app.papra.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShareUploadItem(
    val id: String,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val organizationId: String,
    val workId: String
)

class ShareUploadQueueStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ShareUploadItem> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val uri = item.optString("uri")
                    val fileName = item.optString("fileName")
                    val mimeType = item.optString("mimeType")
                    val orgId = item.optString("organizationId")
                    val workId = item.optString("workId")
                    if (id.isNotBlank() && uri.isNotBlank() && orgId.isNotBlank() && workId.isNotBlank()) {
                        add(
                            ShareUploadItem(
                                id = id,
                                uri = uri,
                                fileName = fileName.ifBlank { "upload" },
                                mimeType = mimeType.ifBlank { "application/octet-stream" },
                                organizationId = orgId,
                                workId = workId
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<ShareUploadItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
                .put("id", item.id)
                .put("uri", item.uri)
                .put("fileName", item.fileName)
                .put("mimeType", item.mimeType)
                .put("organizationId", item.organizationId)
                .put("workId", item.workId)
            array.put(obj)
        }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    fun add(item: ShareUploadItem) {
        val updated = load() + item
        save(updated)
    }

    fun remove(id: String) {
        val updated = load().filterNot { it.id == id }
        save(updated)
    }

    companion object {
        private const val PREFS_NAME = "papra_share_uploads"
        private const val KEY_QUEUE = "queue"
    }
}
