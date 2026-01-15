package app.papra.mobile.ui

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Organization
import app.papra.mobile.data.getFileName
import app.papra.mobile.data.getMimeType
import kotlinx.coroutines.launch

class ShareUploadViewModel(
    private val apiClient: ApiClient,
    private val apiKey: String
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

    fun uploadToOrganization(uri: Uri, contentResolver: ContentResolver, organizationId: String) {
        viewModelScope.launch {
            uploadInProgress = true
            uploadErrorMessage = null
            successMessage = null
            try {
                val rawName = contentResolver.getFileName(uri)
                val mimeType = contentResolver.getMimeType(uri)
                val fileName = normalizeFileName(rawName, mimeType)
                val uploadedName = tryUploadWithRetry(
                    uri = uri,
                    contentResolver = contentResolver,
                    organizationId = organizationId,
                    fileName = fileName,
                    mimeType = mimeType
                )
                successMessage = "Uploaded ${uploadedName}"
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

    private fun normalizeFileName(fileName: String, mimeType: String?): String {
        val safeName = fileName.trim().ifBlank { "upload" }
        val hasExtension = safeName.contains('.')
        if (hasExtension || mimeType.isNullOrBlank()) return safeName
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return if (!extension.isNullOrBlank()) "$safeName.$extension" else safeName
    }

    private suspend fun tryUploadWithRetry(
        uri: Uri,
        contentResolver: ContentResolver,
        organizationId: String,
        fileName: String,
        mimeType: String?
    ): String {
        fun upload(name: String) {
            contentResolver.openInputStream(uri)?.use { input ->
                apiClient.uploadDocument(apiKey, organizationId, name, mimeType, input)
            } ?: throw IllegalStateException("Unable to read shared file")
        }

        return try {
            upload(fileName)
            fileName
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (!message.contains("exists", ignoreCase = true)) {
                throw e
            }
            val retryName = appendSuffix(fileName, "-1")
            upload(retryName)
            retryName
        }
    }

    private fun appendSuffix(fileName: String, suffix: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) + suffix + fileName.substring(dotIndex)
        } else {
            fileName + suffix
        }
    }
}

class ShareUploadViewModelFactory(
    private val apiClient: ApiClient,
    private val apiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareUploadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareUploadViewModel(apiClient, apiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
