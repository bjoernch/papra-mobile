package app.papra.mobile.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.asImageBitmap
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ActivityEvent
import app.papra.mobile.data.Document
import app.papra.mobile.data.Tag
import app.papra.mobile.data.guessMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentPreviewViewModel(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val organizationId: String,
    private val documentId: String
) : ViewModel() {
    var document: Document? by mutableStateOf(null)
        private set
    var availableTags: List<Tag> by mutableStateOf(emptyList())
        private set
    var activities: List<ActivityEvent> by mutableStateOf(emptyList())
        private set
    var previewBitmap: androidx.compose.ui.graphics.ImageBitmap? by mutableStateOf(null)
        private set
    var previewMessage: String? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var isPreviewLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    private var cachedFile: File? = null

    fun load() {
        loadDocument()
        loadTags()
        loadActivity()
    }

    fun loadDocument() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                document = apiClient.getDocument(apiKey, organizationId, documentId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load document"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            try {
                availableTags = apiClient.listTags(apiKey, organizationId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load tags"
            }
        }
    }

    fun loadActivity() {
        viewModelScope.launch {
            try {
                activities = apiClient.getDocumentActivity(apiKey, organizationId, documentId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load activity"
            }
        }
    }

    fun renameDocument(name: String) {
        viewModelScope.launch {
            errorMessage = null
            try {
                document = apiClient.updateDocumentName(apiKey, organizationId, documentId, name)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Rename failed"
            }
        }
    }

    fun addTag(tagId: String) {
        viewModelScope.launch {
            try {
                apiClient.addTagToDocument(apiKey, organizationId, documentId, tagId)
                loadDocument()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to add tag"
            }
        }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch {
            try {
                apiClient.removeTagFromDocument(apiKey, organizationId, documentId, tagId)
                loadDocument()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to remove tag"
            }
        }
    }

    fun createTag(name: String, color: String, description: String?) {
        viewModelScope.launch {
            try {
                apiClient.createTag(apiKey, organizationId, name, color, description)
                loadTags()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to create tag"
            }
        }
    }

    fun updateTag(tagId: String, name: String?, color: String?, description: String?) {
        viewModelScope.launch {
            try {
                apiClient.updateTag(apiKey, organizationId, tagId, name, color, description)
                loadTags()
                loadDocument()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to update tag"
            }
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            try {
                apiClient.deleteTag(apiKey, organizationId, tagId)
                loadTags()
                loadDocument()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to delete tag"
            }
        }
    }

    fun loadPreview(context: Context) {
        viewModelScope.launch {
            isPreviewLoading = true
            previewMessage = null
            previewBitmap = null
            try {
                val doc = document ?: apiClient.getDocument(apiKey, organizationId, documentId)
                document = doc
                val file = ensureCachedFile(context, doc)
                val mimeType = doc.mimeType ?: guessMimeType(doc.name)
                val bitmap = withContext(Dispatchers.IO) { renderPreview(file, mimeType) }
                if (bitmap != null) {
                    previewBitmap = bitmap.asImageBitmap()
                } else {
                    previewMessage = "Preview not available"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load preview"
            } finally {
                isPreviewLoading = false
            }
        }
    }

    fun openExternal(context: Context) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val doc = document ?: apiClient.getDocument(apiKey, organizationId, documentId)
                document = doc
                val file = ensureCachedFile(context, doc)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = doc.mimeType ?: guessMimeType(doc.name)
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

    fun shareExternal(context: Context) {
        viewModelScope.launch {
            errorMessage = null
            try {
                val doc = document ?: apiClient.getDocument(apiKey, organizationId, documentId)
                document = doc
                val file = ensureCachedFile(context, doc)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = doc.mimeType ?: guessMimeType(doc.name)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share document"))
            } catch (e: Exception) {
                errorMessage = e.message ?: "Share failed"
            }
        }
    }

    fun downloadToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            errorMessage = null
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    apiClient.downloadDocument(apiKey, organizationId, documentId, output)
                } ?: throw IllegalStateException("Unable to write file")
            } catch (e: Exception) {
                errorMessage = e.message ?: "Download failed"
            }
        }
    }

    private suspend fun ensureCachedFile(context: Context, doc: Document): File {
        val existing = cachedFile
        if (existing != null && existing.exists() && existing.length() > 0) {
            return existing
        }
        val safeName = doc.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(context.cacheDir, "${doc.id}-$safeName")
        withContext(Dispatchers.IO) {
            file.outputStream().use { output ->
                apiClient.downloadDocument(apiKey, organizationId, doc.id, output)
            }
        }
        cachedFile = file
        return file
    }

    private fun renderPreview(file: File, mimeType: String): Bitmap? {
        return when {
            mimeType.startsWith("image/") -> decodeBitmap(file)
            mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true) ->
                renderPdfFirstPage(file)
            else -> null
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun renderPdfFirstPage(file: File): Bitmap? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                val page = renderer.openPage(0)
                page.use {
                    val maxWidth = 1200
                    val scale = minOf(1f, maxWidth.toFloat() / it.width.toFloat())
                    val width = (it.width * scale).toInt()
                    val height = (it.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val matrix = Matrix().apply { postScale(scale, scale) }
                    it.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}

class DocumentPreviewViewModelFactory(
    private val apiClient: ApiClient,
    private val apiKey: String,
    private val organizationId: String,
    private val documentId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentPreviewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentPreviewViewModel(apiClient, apiKey, organizationId, documentId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
