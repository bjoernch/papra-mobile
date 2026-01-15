package app.papra.mobile.ui

import android.content.ContentResolver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiException
import app.papra.mobile.data.Organization
import app.papra.mobile.data.ShareUploadItem
import app.papra.mobile.data.ShareUploadQueueStore
import app.papra.mobile.data.getFileName
import app.papra.mobile.data.getMimeType
import app.papra.mobile.work.ShareUploadWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID

class ShareUploadViewModel(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val queueStore: ShareUploadQueueStore,
    private val workManager: WorkManager,
    private val wifiOnly: Boolean
) : ViewModel() {
    var organizations: List<Organization> by mutableStateOf(emptyList())
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var uploadInProgress: Boolean by mutableStateOf(false)
        private set
    var uploadErrorMessage: String? by mutableStateOf(null)
        private set
    var successMessage: String? by mutableStateOf(null)
        private set
    var uploadQueue: List<ShareUploadItem> by mutableStateOf(emptyList())
        private set
    var workStates: Map<String, WorkInfo> by mutableStateOf(emptyMap())
        private set
    var currentSessionIds: Set<String> by mutableStateOf(emptySet())
        private set
    var immediateProgress: Map<String, Float> by mutableStateOf(emptyMap())
        private set
    var immediateStatus: Map<String, String> by mutableStateOf(emptyMap())
        private set
    private var pendingImmediateItems: List<ShareUploadItem> = emptyList()

    init {
        uploadQueue = queueStore.load()
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(ShareUploadWorker.TAG).collectLatest { infos ->
                workStates = infos.associateBy { it.id.toString() }
                cleanupSucceededUploads(infos)
            }
        }
    }

    fun loadOrganizations() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                organizations = apiClient.listOrganizations(apiKey)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load organizations"
            } finally {
                isLoading = false
            }
        }
    }

    fun uploadImmediately(
        context: android.content.Context,
        uris: List<Uri>,
        contentResolver: ContentResolver,
        organizationId: String
    ) {
        viewModelScope.launch {
            uploadInProgress = true
            uploadErrorMessage = null
            successMessage = null
            immediateProgress = emptyMap()
            immediateStatus = emptyMap()
            try {
                if (!hasAnyNetwork(context)) {
                    uploadErrorMessage = "No network connection available."
                    return@launch
                }
                val items = uris.map { uri ->
                    val rawName = contentResolver.getFileName(uri)
                    val mimeType = contentResolver.getMimeType(uri) ?: "application/octet-stream"
                    val fileName = normalizeFileName(rawName, mimeType)
                    ShareUploadItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        organizationId = organizationId,
                        workId = ""
                    )
                }
                uploadQueue = items
                currentSessionIds = items.map { it.id }.toSet()
                pendingImmediateItems = items
                processImmediateQueue(context, contentResolver, organizationId)
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                uploadErrorMessage = if (message.contains("exists", ignoreCase = true)) {
                    "A document with this name already exists."
                } else {
                    message.ifBlank { "Upload failed" }
                }
            } finally {
                uploadInProgress = false
            }
        }
    }

    fun enqueueUploads(
        context: android.content.Context,
        uris: List<Uri>,
        contentResolver: ContentResolver,
        organizationId: String
    ) {
        viewModelScope.launch {
            uploadInProgress = true
            uploadErrorMessage = null
            successMessage = null
            try {
                val forceConnected = wifiOnly && !isUnmeteredNetwork(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(
                        if (wifiOnly && !forceConnected) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        }
                    )
                    .build()
                val newItems = mutableListOf<ShareUploadItem>()
                val requests = mutableListOf<androidx.work.OneTimeWorkRequest>()
                uris.forEach { uri ->
                    val rawName = contentResolver.getFileName(uri)
                    val mimeType = contentResolver.getMimeType(uri) ?: "application/octet-stream"
                    val fileName = normalizeFileName(rawName, mimeType)
                    val id = UUID.randomUUID().toString()
                    val request = OneTimeWorkRequestBuilder<ShareUploadWorker>()
                        .setConstraints(constraints)
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(
                            workDataOf(
                                ShareUploadWorker.KEY_TASK_ID to id,
                                ShareUploadWorker.KEY_URI to uri.toString(),
                                ShareUploadWorker.KEY_ORG_ID to organizationId,
                                ShareUploadWorker.KEY_FILE_NAME to fileName,
                                ShareUploadWorker.KEY_MIME_TYPE to mimeType
                            )
                        )
                        .addTag(ShareUploadWorker.TAG)
                        .build()
                    val item = ShareUploadItem(
                        id = id,
                        uri = uri.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        organizationId = organizationId,
                        workId = request.id.toString()
                    )
                    queueStore.add(item)
                    newItems.add(item)
                    requests.add(request)
                }
                uploadQueue = queueStore.load()
                currentSessionIds = newItems.map { it.id }.toSet()
                val connected = hasAnyNetwork(context)
                val uploadNow = connected && (forceConnected || !wifiOnly || isUnmeteredNetwork(context))
                successMessage = if (uploadNow) {
                    "Uploading now (${newItems.size} file(s))"
                } else {
                    "Queued ${newItems.size} upload(s)"
                }
                if (requests.isNotEmpty()) {
                    var continuation = workManager.beginWith(requests.first())
                    requests.drop(1).forEach { request ->
                        continuation = continuation.then(request)
                    }
                    continuation.enqueue()
                }
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                uploadErrorMessage = if (message.contains("exists", ignoreCase = true)) {
                    "A document with this name already exists. Rename the file and try again."
                } else {
                    message.ifBlank { "Upload failed" }
                }
            } finally {
                uploadInProgress = false
            }
        }
    }

    fun removeQueuedItem(id: String) {
        val item = uploadQueue.firstOrNull { it.id == id }
        if (item != null) {
            runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
        }
        queueStore.remove(id)
        uploadQueue = queueStore.load()
    }

    fun retryQueuedItem(item: ShareUploadItem) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ShareUploadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ShareUploadWorker.KEY_TASK_ID to item.id,
                    ShareUploadWorker.KEY_URI to item.uri,
                    ShareUploadWorker.KEY_ORG_ID to item.organizationId,
                    ShareUploadWorker.KEY_FILE_NAME to item.fileName,
                    ShareUploadWorker.KEY_MIME_TYPE to item.mimeType
                )
            )
            .addTag(ShareUploadWorker.TAG)
            .build()
        val updated = item.copy(workId = request.id.toString())
        queueStore.remove(item.id)
        queueStore.add(updated)
        uploadQueue = queueStore.load()
        workManager.enqueue(request)
    }

    private fun cleanupSucceededUploads(infos: List<WorkInfo>) {
        if (uploadQueue.isEmpty()) return
        val workMap = infos.associateBy { it.id.toString() }
        val succeededIds = uploadQueue
            .filter { workMap[it.workId]?.state == WorkInfo.State.SUCCEEDED }
            .map { it.id }
        if (succeededIds.isEmpty()) return
        succeededIds.forEach { queueStore.remove(it) }
        uploadQueue = queueStore.load()
    }

    private fun normalizeFileName(fileName: String, mimeType: String?): String {
        val safeName = fileName.trim().ifBlank { "upload" }
        val hasExtension = safeName.contains('.')
        if (hasExtension || mimeType.isNullOrBlank()) return safeName
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!extension.isNullOrBlank()) "$safeName.$extension" else safeName
    }

    private fun isUnmeteredNetwork(context: android.content.Context): Boolean {
        val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun hasAnyNetwork(context: android.content.Context): Boolean {
        val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun processImmediateQueue(
        context: android.content.Context,
        contentResolver: ContentResolver,
        organizationId: String
    ) {
        val items = pendingImmediateItems
        for (item in items) {
            immediateStatus = immediateStatus + (item.id to "Uploading...")
            val uri = Uri.parse(item.uri)
            val contentLength = contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length.takeIf { length -> length >= 0 }
            }
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    apiClient.uploadDocument(
                        apiKey,
                        organizationId,
                        item.fileName,
                        item.mimeType,
                        input,
                        contentLength
                    ) { sent, total ->
                        if (total != null && total > 0) {
                            val progress = (sent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            immediateProgress = immediateProgress + (item.id to progress)
                        }
                    }
                } ?: throw IllegalStateException("Unable to read file")
                immediateProgress = immediateProgress + (item.id to 1f)
                immediateStatus = immediateStatus + (item.id to "Uploaded")
                if (uri.scheme == "file") {
                    runCatching { java.io.File(uri.path ?: "").delete() }
                }
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == 409) {
                    immediateStatus = immediateStatus + (item.id to "Name already exists")
                    val conflictHint = e.message?.takeIf { it.isNotBlank() }
                    uploadErrorMessage = listOfNotNull(
                        "A document with this name already exists.",
                        conflictHint
                    ).joinToString(" ")
                    return
                }
                throw e
            }
        }
        successMessage = "Uploaded ${items.size} file(s)"
        uploadQueue = emptyList()
        pendingImmediateItems = emptyList()
    }
}

class ShareUploadViewModelFactory(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val queueStore: ShareUploadQueueStore,
    private val workManager: WorkManager,
    private val wifiOnly: Boolean
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareUploadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareUploadViewModel(apiClient, apiKey, queueStore, workManager, wifiOnly) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
