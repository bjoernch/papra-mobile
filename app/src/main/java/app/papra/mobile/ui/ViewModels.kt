package app.papra.mobile.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Document
import app.papra.mobile.data.OrganizationStats
import app.papra.mobile.data.Organization
import app.papra.mobile.data.OfflineCacheStore
import app.papra.mobile.data.Tag
import app.papra.mobile.data.getFileName
import app.papra.mobile.data.getMimeType
import app.papra.mobile.data.guessMimeType
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

class OrganizationsViewModel(
    private val apiClient: ApiClient,
    private val apiKey: String
) : ViewModel() {
    var organizations: List<Organization> by mutableStateOf(emptyList())
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

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
}

class OrganizationsViewModelFactory(
    private val apiClient: ApiClient,
    private val apiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrganizationsViewModel(apiClient, apiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DocumentsViewModel(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val organizationId: String,
    private val offlineCacheStore: OfflineCacheStore
) : ViewModel() {
    var documents: List<Document> by mutableStateOf(emptyList())
        private set
    var documentsCount: Int by mutableStateOf(0)
        private set
    var deletedDocuments: List<Document> by mutableStateOf(emptyList())
        private set
    var deletedDocumentsCount: Int by mutableStateOf(0)
        private set
    var searchResults: List<Document> by mutableStateOf(emptyList())
        private set
    var searchResultCount: Int by mutableStateOf(0)
        private set
    var organizationStats: OrganizationStats? by mutableStateOf(null)
        private set
    var tags: List<Tag> by mutableStateOf(emptyList())
        private set
    var cachedDocIds: Set<String> by mutableStateOf(emptySet())
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var isLoadingDeleted: Boolean by mutableStateOf(false)
        private set
    var isSearching: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var uploadInProgress: Boolean by mutableStateOf(false)
        private set
    var uploadProgress: Float? by mutableStateOf(null)
        private set
    var uploadFileName: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            offlineCacheStore.cachedDocIdsFlow.collect { ids ->
                cachedDocIds = ids
            }
        }
    }

    private var currentTagFilter: List<String>? = null

    fun loadDocuments(tags: List<String>? = currentTagFilter) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                currentTagFilter = tags
                val (docs, count) = apiClient.listDocuments(apiKey, organizationId, tags = tags)
                documents = docs
                documentsCount = count
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load documents"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadDeletedDocuments() {
        viewModelScope.launch {
            isLoadingDeleted = true
            errorMessage = null
            try {
                val (docs, count) = apiClient.listDeletedDocuments(apiKey, organizationId)
                deletedDocuments = docs
                deletedDocumentsCount = count
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load trash"
            } finally {
                isLoadingDeleted = false
            }
        }
    }

    fun searchDocuments(query: String) {
        viewModelScope.launch {
            isSearching = true
            errorMessage = null
            try {
                val (docs, count) = apiClient.searchDocuments(apiKey, organizationId, query)
                searchResults = docs
                searchResultCount = count
            } catch (e: Exception) {
                errorMessage = e.message ?: "Search failed"
            } finally {
                isSearching = false
            }
        }
    }

    fun clearSearch() {
        searchResults = emptyList()
        searchResultCount = 0
    }

    fun loadOrganizationStats() {
        viewModelScope.launch {
            try {
                organizationStats = apiClient.getOrganizationStats(apiKey, organizationId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load statistics"
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            try {
                tags = apiClient.listTags(apiKey, organizationId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load tags"
            }
        }
    }

    fun cacheDocument(context: Context, document: Document) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val safeName = document.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val cacheDir = File(context.filesDir, "offline_docs").apply { mkdirs() }
                val cacheFile = File(cacheDir, "${document.id}-$safeName")
                cacheFile.outputStream().use { output ->
                    apiClient.downloadDocument(apiKey, organizationId, document.id, output)
                }
                offlineCacheStore.setCached(document.id, true)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Offline cache failed"
            }
        }
    }

    fun removeCachedDocument(context: Context, document: Document) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val cacheDir = File(context.filesDir, "offline_docs")
                val prefix = "${document.id}-"
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(prefix)) {
                        file.delete()
                    }
                }
                offlineCacheStore.setCached(document.id, false)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Remove offline cache failed"
            }
        }
    }

    fun deleteDocuments(documents: List<Document>) {
        viewModelScope.launch {
            errorMessage = null
            try {
                documents.forEach { doc ->
                    apiClient.deleteDocument(apiKey, organizationId, doc.id)
                }
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Delete failed"
            }
        }
    }

    fun addTagToDocuments(tagId: String, documents: List<Document>) {
        viewModelScope.launch {
            errorMessage = null
            try {
                documents.forEach { doc ->
                    apiClient.addTagToDocument(apiKey, organizationId, doc.id, tagId)
                }
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Tagging failed"
            }
        }
    }
    fun uploadDocument(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            uploadInProgress = true
            uploadProgress = 0f
            errorMessage = null
            try {
                val fileName = contentResolver.getFileName(uri)
                val mimeType = contentResolver.getMimeType(uri)
                uploadFileName = fileName
                val contentLength = contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    it.length.takeIf { length -> length >= 0 }
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    apiClient.uploadDocument(
                        apiKey,
                        organizationId,
                        fileName,
                        mimeType,
                        input,
                        contentLength
                    ) { sent, total ->
                        uploadProgress = if (total != null && total > 0) {
                            sent.toFloat() / total.toFloat()
                        } else {
                            null
                        }
                    }
                } ?: throw IllegalStateException("Unable to read file")
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Upload failed"
            } finally {
                uploadInProgress = false
                uploadFileName = null
                uploadProgress = null
            }
        }
    }

    fun downloadDocumentToUri(
        document: Document,
        uri: Uri,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            errorMessage = null
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    apiClient.downloadDocument(apiKey, organizationId, document.id, output)
                } ?: throw IllegalStateException("Unable to write file")
            } catch (e: Exception) {
                errorMessage = e.message ?: "Download failed"
            }
        }
    }

    fun openDocument(context: Context, document: Document) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val safeName = document.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val cacheFile = File(context.cacheDir, "${document.id}-$safeName")
                cacheFile.outputStream().use { output ->
                    apiClient.downloadDocument(apiKey, organizationId, document.id, output)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                val mimeType = document.mimeType ?: guessMimeType(document.name)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                errorMessage = e.message ?: "Open failed"
            }
        }
    }
}

class DocumentsViewModelFactory(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val organizationId: String,
    private val offlineCacheStore: OfflineCacheStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentsViewModel(apiClient, apiKey, organizationId, offlineCacheStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
