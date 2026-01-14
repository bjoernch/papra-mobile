package app.papra.mobile.ui

import android.content.ContentResolver
import android.net.Uri
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
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                val fileName = contentResolver.getFileName(uri)
                val mimeType = contentResolver.getMimeType(uri)
                contentResolver.openInputStream(uri)?.use { input ->
                    apiClient.uploadDocument(apiKey, organizationId, fileName, mimeType, input)
                } ?: throw IllegalStateException("Unable to read shared file")
                successMessage = "Uploaded to Papra"
            } catch (e: Exception) {
                errorMessage = e.message ?: "Upload failed"
            } finally {
                isLoading = false
            }
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
