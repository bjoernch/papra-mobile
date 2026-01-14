package app.papra.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

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
        pageSize: Int = 100
    ): Pair<List<Document>, Int> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/organizations/$organizationId/documents?pageIndex=$pageIndex&pageSize=$pageSize"
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

    suspend fun getDocument(apiKey: String, organizationId: String, documentId: String): Document =
        withContext(Dispatchers.IO) {
            val request = requestBuilder(
                "$baseUrl/organizations/$organizationId/documents/$documentId",
                apiKey
            ).get().build()
            val json = executeJson(request)
            json.getJSONObject("document").toDocument()
        }

    suspend fun uploadDocument(
        apiKey: String,
        organizationId: String,
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): Document = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                InputStreamRequestBody(mimeType, inputStream)
            )
            .build()

        val request = requestBuilder(
            "$baseUrl/organizations/$organizationId/documents",
            apiKey
        ).post(body).build()
        val json = executeJson(request)
        json.getJSONObject("document").toDocument()
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
            mimeType = optString("mimeType").ifBlank { null }
        )
    }

    private class InputStreamRequestBody(
        private val mimeType: String?,
        private val inputStream: InputStream
    ) : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun writeTo(sink: BufferedSink) {
            inputStream.source().use { source ->
                sink.writeAll(source)
            }
        }
    }
}
