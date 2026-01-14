package app.papra.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder

class ApiClient(
    private val baseUrl: String = "https://api.papra.app/api",
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun listOrganizations(apiKey: String): List<Organization> = withContext(Dispatchers.IO) {
        val request = requestBuilder("$baseUrl/organizations", apiKey).get().build()
        val json = executeJson(request)
        val orgs = json.optJSONArray("organizations") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until orgs.length()) {
                val item = orgs.getJSONObject(i)
                add(
                    Organization(
                        id = item.optString("id"),
                        name = item.optString("name")
                    )
                )
            }
        }
    }

    suspend fun listDocuments(
        apiKey: String,
        organizationId: String,
        pageIndex: Int = 0,
        pageSize: Int = 100,
        tags: List<String>? = null
    ): Pair<List<Document>, Int> = withContext(Dispatchers.IO) {
        val tagQuery = tags?.takeIf { it.isNotEmpty() }?.joinToString(",")?.let { encodeParam(it) }
        val url = buildString {
            append("$baseUrl/organizations/$organizationId/documents?pageIndex=$pageIndex&pageSize=$pageSize")
            if (!tagQuery.isNullOrBlank()) {
                append("&tags=$tagQuery")
            }
        }
        val request = requestBuilder(url, apiKey).get().build()
        val json = executeJson(request)
        val documents = json.optJSONArray("documents") ?: return@withContext Pair(emptyList(), 0)
        val docs = buildList {
            for (i in 0 until documents.length()) {
                val item = documents.getJSONObject(i)
                add(item.toDocument())
            }
        }
        Pair(docs, json.optInt("documentsCount", docs.size))
    }

    suspend fun listDeletedDocuments(
        apiKey: String,
        organizationId: String,
        pageIndex: Int = 0,
        pageSize: Int = 100
    ): Pair<List<Document>, Int> = withContext(Dispatchers.IO) {
        val url =
            "$baseUrl/organizations/$organizationId/documents/deleted?pageIndex=$pageIndex&pageSize=$pageSize"
        val request = requestBuilder(url, apiKey).get().build()
        val json = executeJson(request)
        val documents = json.optJSONArray("documents") ?: return@withContext Pair(emptyList(), 0)
        val docs = buildList {
            for (i in 0 until documents.length()) {
                val item = documents.getJSONObject(i)
                add(item.toDocument())
            }
        }
        Pair(docs, json.optInt("documentsCount", docs.size))
    }

    suspend fun searchDocuments(
        apiKey: String,
        organizationId: String,
        searchQuery: String,
        pageIndex: Int = 0,
        pageSize: Int = 100
    ): Pair<List<Document>, Int> = withContext(Dispatchers.IO) {
        val encodedQuery = encodeParam(searchQuery)
        val url =
            "$baseUrl/organizations/$organizationId/documents/search?searchQuery=$encodedQuery&pageIndex=$pageIndex&pageSize=$pageSize"
        val request = requestBuilder(url, apiKey).get().build()
        val json = executeJson(request)
        val documents = json.optJSONArray("documents")
        val fallbackResults = json.optJSONArray("searchResults")
        val activeArray = documents ?: fallbackResults ?: JSONArray()
        val docs = buildList {
            for (i in 0 until activeArray.length()) {
                val item = activeArray.getJSONObject(i)
                add(item.toSearchDocument())
            }
        }
        Pair(docs, docs.size)
    }

    suspend fun getDocument(apiKey: String, organizationId: String, documentId: String): Document =
        withContext(Dispatchers.IO) {
            val request = requestBuilder(
                "$baseUrl/organizations/$organizationId/documents/$documentId",
                apiKey
            ).get().build()
            val json = executeJson(request)
            json.getJSONObject("document").toDocument()
        }

    suspend fun updateDocumentName(
        apiKey: String,
        organizationId: String,
        documentId: String,
        name: String
    ): Document = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .build()
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/$documentId",
            apiKey
        ).patch(body).build()
        val json = executeJson(request)
        json.getJSONObject("document").toDocument()
    }

    suspend fun uploadDocument(
        apiKey: String,
        organizationId: String,
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        contentLength: Long? = null,
        onProgress: ((Long, Long?) -> Unit)? = null
    ): Document = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                InputStreamRequestBody(mimeType, inputStream, contentLength, onProgress)
            )
            .build()

        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents",
            apiKey
        ).post(body).build()
        val json = executeJson(request)
        json.getJSONObject("document").toDocument()
    }

    suspend fun listTags(apiKey: String, organizationId: String): List<Tag> = withContext(Dispatchers.IO) {
        val request = requestBuilder("$baseUrl/organizations/$organizationId/tags", apiKey).get().build()
        val json = executeJson(request)
        val tags = json.optJSONArray("tags") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until tags.length()) {
                val item = tags.getJSONObject(i)
                add(item.toTag())
            }
        }
    }

    suspend fun createTag(
        apiKey: String,
        organizationId: String,
        name: String,
        color: String,
        description: String?
    ): Tag = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .addFormDataPart("color", color)
            .apply {
                if (!description.isNullOrBlank()) {
                    addFormDataPart("description", description)
                }
            }
            .build()
        val request = requestBuilder("$baseUrl/organizations/$organizationId/tags", apiKey)
            .post(body)
            .build()
        val json = executeJson(request)
        json.getJSONObject("tag").toTag()
    }

    suspend fun updateTag(
        apiKey: String,
        organizationId: String,
        tagId: String,
        name: String?,
        color: String?,
        description: String?
    ): Tag = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        name?.let { bodyBuilder.addFormDataPart("name", it) }
        color?.let { bodyBuilder.addFormDataPart("color", it) }
        description?.let { bodyBuilder.addFormDataPart("description", it) }
        val request = requestBuilder("$baseUrl/organizations/$organizationId/tags/$tagId", apiKey)
            .put(bodyBuilder.build())
            .build()
        val json = executeJson(request)
        json.getJSONObject("tag").toTag()
    }

    suspend fun deleteTag(
        apiKey: String,
        organizationId: String,
        tagId: String
    ) = withContext(Dispatchers.IO) {
        val request = requestBuilder("$baseUrl/organizations/$organizationId/tags/$tagId", apiKey)
            .delete()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val payload = response.body?.string().orEmpty()
                throw IOException("Tag delete failed ${response.code}: $payload")
            }
        }
    }

    suspend fun addTagToDocument(
        apiKey: String,
        organizationId: String,
        documentId: String,
        tagId: String
    ) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("tagId", tagId).build()
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/$documentId/tags",
            apiKey
        ).post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val payload = response.body?.string().orEmpty()
                throw IOException("Tag add failed ${response.code}: $payload")
            }
        }
    }

    suspend fun removeTagFromDocument(
        apiKey: String,
        organizationId: String,
        documentId: String,
        tagId: String
    ) = withContext(Dispatchers.IO) {
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/$documentId/tags/$tagId",
            apiKey
        ).delete().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val payload = response.body?.string().orEmpty()
                throw IOException("Tag remove failed ${response.code}: $payload")
            }
        }
    }

    suspend fun getOrganizationStats(
        apiKey: String,
        organizationId: String
    ): OrganizationStats = withContext(Dispatchers.IO) {
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/statistics",
            apiKey
        ).get().build()
        val json = executeJson(request)
        val stats = json.getJSONObject("organizationStats")
        OrganizationStats(
            documentsCount = stats.optInt("documentsCount"),
            documentsSize = stats.optLong("documentsSize")
        )
    }

    suspend fun getDocumentActivity(
        apiKey: String,
        organizationId: String,
        documentId: String,
        pageIndex: Int = 0,
        pageSize: Int = 100
    ): List<ActivityEvent> = withContext(Dispatchers.IO) {
        val url =
            "$baseUrl/organizations/$organizationId/documents/$documentId/activity?pageIndex=$pageIndex&pageSize=$pageSize"
        val request = requestBuilder(url, apiKey).get().build()
        val json = executeJson(request)
        val activities = json.optJSONArray("activities") ?: JSONArray()
        buildList {
            for (i in 0 until activities.length()) {
                val item = activities.getJSONObject(i)
                add(item.toActivityEvent())
            }
        }
    }

    suspend fun downloadDocument(
        apiKey: String,
        organizationId: String,
        documentId: String,
        outputStream: java.io.OutputStream
    ) = withContext(Dispatchers.IO) {
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/$documentId/file",
            apiKey
        ).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { input ->
                input.copyTo(outputStream)
                outputStream.flush()
            }
        }
    }

    suspend fun deleteDocument(
        apiKey: String,
        organizationId: String,
        documentId: String
    ) = withContext(Dispatchers.IO) {
        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents/$documentId",
            apiKey
        ).delete().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val payload = response.body?.string().orEmpty()
                throw IOException("Delete failed ${response.code}: $payload")
            }
        }
    }

    private fun requestBuilder(url: String, apiKey: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
    }

    private fun executeJson(request: Request): JSONObject {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IOException("API error ${response.code}: $body")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body")
            return JSONObject(body)
        }
    }

    private fun JSONObject.toDocument(): Document {
        return Document(
            id = optString("id"),
            name = optString("name"),
            size = if (has("size")) optLong("size") else null,
            createdAt = optString("createdAt").ifBlank { null },
            mimeType = optString("mimeType").ifBlank { null },
            tags = parseTags()
        )
    }

    private fun JSONObject.toTag(): Tag {
        return Tag(
            id = optString("id"),
            name = optString("name"),
            color = optString("color").ifBlank { null },
            description = optString("description").ifBlank { null }
        )
    }

    private fun JSONObject.toActivityEvent(): ActivityEvent {
        return ActivityEvent(
            id = optString("id"),
            type = optString("type").ifBlank { null },
            createdAt = optString("createdAt").ifBlank { null },
            details = toString()
        )
    }

    private fun JSONObject.toSearchDocument(): Document {
        return if (has("document")) {
            getJSONObject("document").toDocument()
        } else {
            toDocument()
        }
    }

    private fun JSONObject.parseTags(): List<Tag> {
        val tags = optJSONArray("tags") ?: return emptyList()
        return buildList {
            for (i in 0 until tags.length()) {
                val item = tags.getJSONObject(i)
                add(item.toTag())
            }
        }
    }

    private class InputStreamRequestBody(
        private val mimeType: String?,
        private val inputStream: InputStream,
        private val contentLength: Long?,
        private val onProgress: ((Long, Long?) -> Unit)?
    ) : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength(): Long = contentLength ?: -1L

        override fun writeTo(sink: BufferedSink) {
            inputStream.source().use { source ->
                var totalRead = 0L
                var readCount: Long
                val bufferSize = 8_192L
                while (true) {
                    readCount = source.read(sink.buffer, bufferSize)
                    if (readCount == -1L) break
                    totalRead += readCount
                    sink.flush()
                    onProgress?.invoke(totalRead, contentLength)
                }
            }
        }
    }

    private fun encodeParam(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }
}
