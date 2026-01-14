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
import app.papra.mobile.data.Organization
import app.papra.mobile.data.getFileName
import app.papra.mobile.data.getMimeType
import app.papra.mobile.data.guessMimeType
import kotlinx.coroutines.launch
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
    private val organizationId: String
) : ViewModel() {
    var documents: List<Document> by mutableStateOf(emptyList())
        private set
    var documentsCount: Int by mutableStateOf(0)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var uploadInProgress: Boolean by mutableStateOf(false)
        private set

    fun loadDocuments() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val (docs, count) = apiClient.listDocuments(apiKey, organizationId)
                documents = docs
                documentsCount = count
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load documents"
            } finally {
                isLoading = false
            }
        }
    }

    fun uploadDocument(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            uploadInProgress = true
            errorMessage = null
            try {
                val fileName = contentResolver.getFileName(uri)
                val mimeType = contentResolver.getMimeType(uri)
                contentResolver.openInputStream(uri)?.use { input ->
                    apiClient.uploadDocument(apiKey, organizationId, fileName, mimeType, input)
                } ?: throw IllegalStateException("Unable to read file")
                loadDocuments()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Upload failed"
            } finally {
                uploadInProgress = false
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
                val safeName = document.name.replace(Regex(\"[^A-Za-z0-9._-]\"), \"_\")
                val cacheFile = File(context.cacheDir, \"${document.id}-$safeName\")
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
    private val organizationId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentsViewModel(apiClient, apiKey, organizationId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
