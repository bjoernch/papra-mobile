package app.papra.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.papra.mobile.R
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.data.Document
import java.time.Instant
import java.time.OffsetDateTime

class DocumentNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = ApiKeyStore(applicationContext)
        if (!store.getNotificationsEnabled()) return Result.success()

        val apiKey = store.getApiKey()?.takeIf { it.isNotBlank() } ?: return Result.success()
        val baseUrl = store.getBaseUrl()?.takeIf { it.isNotBlank() } ?: return Result.success()
        val apiClient = ApiClient(baseUrl = normalizeBaseUrl(baseUrl))
        val lastSeen = store.getLastSeenDocuments()

        val organizations = try {
            apiClient.listOrganizations(apiKey)
        } catch (_: Exception) {
            return Result.retry()
        }

        createChannel()

        for (org in organizations) {
            val docs = try {
                apiClient.listDocuments(apiKey, org.id, pageIndex = 0, pageSize = 20).first
            } catch (_: Exception) {
                continue
            }

            val newest = docs
                .mapNotNull { doc -> parseInstant(doc.createdAt)?.let { it to doc } }
                .sortedByDescending { it.first }

            if (newest.isEmpty()) continue

            val latestInstant = newest.first().first
            val lastSeenInstant = lastSeen[org.id]?.let { parseInstant(it) }
            if (lastSeenInstant == null) {
                store.setLastSeenDocument(org.id, latestInstant.toString())
                continue
            }

            val newDocs = newest.filter { it.first.isAfter(lastSeenInstant) }
            if (newDocs.isNotEmpty()) {
                val latestDoc = newDocs.first().second
                notifyNewDocuments(org.name, newDocs.size, latestDoc)
                store.setLastSeenDocument(org.id, latestInstant.toString())
            } else {
                store.setLastSeenDocument(org.id, latestInstant.toString())
            }
        }

        return Result.success()
    }

    private fun notifyNewDocuments(orgName: String, count: Int, latestDoc: Document) {
        val title = "New documents in $orgName"
        val text = if (count == 1) {
            "Latest: ${latestDoc.name}"
        } else {
            "$count new documents. Latest: ${latestDoc.name}"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(orgName.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Document updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    companion object {
        private const val CHANNEL_ID = "papra_documents"
    }
}
