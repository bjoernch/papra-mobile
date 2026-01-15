package app.papra.mobile.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.ui.normalizeBaseUrl
import java.io.IOException

class ShareUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val organizationId = inputData.getString(KEY_ORG_ID) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "upload"
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"

        val apiKeyStore = ApiKeyStore(applicationContext)
        val apiKey = apiKeyStore.getApiKey() ?: return Result.failure()
        val baseUrl = apiKeyStore.getBaseUrl() ?: "https://api.papra.app"
        val apiClient = ApiClient(baseUrl = normalizeBaseUrl(baseUrl))

        setForeground(createForegroundInfo(fileName, 0))

        val uri = Uri.parse(uriString)
        val resolver = applicationContext.contentResolver
        val contentLength = resolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length.takeIf { length -> length >= 0 }
        }

        try {
            resolver.openInputStream(uri)?.use { input ->
                apiClient.uploadDocument(
                    apiKey,
                    organizationId,
                    fileName,
                    mimeType,
                    input,
                    contentLength
                ) { sent, total ->
                    val progress = if (total != null && total > 0) {
                        (sent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    if (progress != null) {
                        setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to progress))
                        setForegroundAsync(createForegroundInfo(fileName, (progress * 100).toInt()))
                    }
                }
            } ?: return Result.failure()
            if (uri.scheme == "file") {
                runCatching { java.io.File(uri.path ?: "").delete() }
            }
        } catch (e: IOException) {
            return Result.retry()
        } catch (_: Exception) {
            return Result.failure()
        }

        return Result.success()
    }

    private fun createForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val channelId = ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Uploading to Papra")
            .setContentText("$fileName (${progress}%)")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(): String {
        val channelId = CHANNEL_ID
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(channelId)
        if (existing == null) {
            val channel = NotificationChannel(
                channelId,
                "Papra uploads",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_URI = "uri"
        const val KEY_ORG_ID = "org_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_PROGRESS = "progress"
        private const val CHANNEL_ID = "papra_uploads"
        private const val NOTIFICATION_ID = 2001
        const val TAG = "share-upload"
    }
}
