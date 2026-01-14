package app.papra.mobile.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
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
import java.io.IOException
import java.time.Instant

enum class ScanQuality {
    LOW,
    MEDIUM,
    HIGH
}

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
    var isLoadingMore: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var uploadInProgress: Boolean by mutableStateOf(false)
        private set
    var uploadProgress: Float? by mutableStateOf(null)
        private set
    var uploadFileName: String? by mutableStateOf(null)
        private set
    var recentSearches: List<String> by mutableStateOf(emptyList())
        private set
    var lastUpdatedAt: Instant? by mutableStateOf(null)
        private set
    var hasMoreDocuments: Boolean by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            offlineCacheStore.cachedDocIdsFlow.collect { ids ->
                cachedDocIds = ids
            }
        }
    }

    private var currentTagFilter: List<String>? = null
    private var currentPageIndex: Int = 0

    fun loadDocuments(tags: List<String>? = currentTagFilter) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                currentTagFilter = tags
                currentPageIndex = 0
                val (docs, count) = apiClient.listDocuments(
                    apiKey,
                    organizationId,
                    pageIndex = currentPageIndex,
                    tags = tags
                )
                documents = docs
                documentsCount = count
                hasMoreDocuments = documents.size < documentsCount
                lastUpdatedAt = Instant.now()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load documents"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMoreDocuments() {
        if (isLoadingMore || !hasMoreDocuments) return
        viewModelScope.launch {
            isLoadingMore = true
            errorMessage = null
            try {
                val nextPage = currentPageIndex + 1
                val (docs, count) = apiClient.listDocuments(
                    apiKey,
                    organizationId,
                    pageIndex = nextPage,
                    tags = currentTagFilter
                )
                documents = documents + docs
                documentsCount = count
                currentPageIndex = nextPage
                hasMoreDocuments = documents.size < documentsCount
                lastUpdatedAt = Instant.now()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load more documents"
            } finally {
                isLoadingMore = false
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
                lastUpdatedAt = Instant.now()
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

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        recentSearches = listOf(trimmed) + recentSearches.filter { it != trimmed }.take(4)
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
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

    fun createTag(name: String, color: String, description: String?) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val normalizedColor = if (color.trim().startsWith("#")) {
                    color.trim()
                } else {
                    "#${color.trim()}"
                }
                apiClient.createTag(apiKey, organizationId, name, normalizedColor, description)
                loadTags()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to create tag"
            }
        }
    }

    fun renameDocument(documentId: String, name: String) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val trimmed = name.trim()
                if (trimmed.isBlank()) {
                    throw IllegalArgumentException("Name cannot be empty")
                }
                apiClient.updateDocumentName(apiKey, organizationId, documentId, trimmed)
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Rename failed"
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

    fun uploadScannedPdfFromImages(
        imageUris: List<Uri>,
        contentResolver: ContentResolver,
        context: Context,
        quality: ScanQuality
    ) {
        viewModelScope.launch {
            uploadInProgress = true
            uploadProgress = 0f
            errorMessage = null
            try {
                val pdfFile = createPdfFromImages(imageUris, contentResolver, context.cacheDir, quality)
                uploadFileName = pdfFile.name
                val contentLength = pdfFile.length()
                pdfFile.inputStream().use { input ->
                    apiClient.uploadDocument(
                        apiKey,
                        organizationId,
                        pdfFile.name,
                        "application/pdf",
                        input,
                        contentLength
                    ) { sent, total ->
                        uploadProgress = if (total != null && total > 0) {
                            sent.toFloat() / total.toFloat()
                        } else {
                            null
                        }
                    }
                }
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Scan upload failed"
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

    fun shareDocumentAsPdf(context: Context, document: Document) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val safeName = document.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val pdfName = if (safeName.endsWith(".pdf", ignoreCase = true)) {
                    safeName
                } else {
                    "$safeName.pdf"
                }
                val cacheFile = File(context.cacheDir, "${document.id}-$pdfName")
                cacheFile.outputStream().use { output ->
                    apiClient.downloadDocument(apiKey, organizationId, document.id, output)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share document"))
            } catch (e: Exception) {
                errorMessage = e.message ?: "Share failed"
            }
        }
    }
}

private fun createPdfFromImages(
    imageUris: List<Uri>,
    contentResolver: ContentResolver,
    cacheDir: File,
    quality: ScanQuality
): File {
    val pdfDocument = PdfDocument()
    val maxDimension = when (quality) {
        ScanQuality.LOW -> 1200
        ScanQuality.MEDIUM -> 2000
        ScanQuality.HIGH -> 3000
    }
    imageUris.forEachIndexed { index, uri ->
        val bitmap = decodeScaledBitmap(contentResolver, uri, maxDimension)
            ?: throw IOException("Unable to read scan")
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)
        bitmap.recycle()
    }

    val file = File(cacheDir, "scan-${System.currentTimeMillis()}.pdf")
    file.outputStream().use { output ->
        pdfDocument.writeTo(output)
    }
    pdfDocument.close()
    return file
}

private fun decodeScaledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    val (width, height) = bounds.outWidth to bounds.outHeight
    if (width <= 0 || height <= 0) return null
    val largest = maxOf(width, height)
    val sampleSize = if (largest > maxDimension) {
        (largest.toFloat() / maxDimension).toInt().coerceAtLeast(1)
    } else {
        1
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
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
