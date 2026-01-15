package app.papra.mobile.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
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
import java.io.FileOutputStream

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
    var previewPages: List<androidx.compose.ui.graphics.ImageBitmap> by mutableStateOf(emptyList())
        private set
    var previewMessage: String? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var isPreviewLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var isSavingRotation: Boolean by mutableStateOf(false)
        private set
    var saveMessage: String? by mutableStateOf(null)
        private set
    var saveErrorMessage: String? by mutableStateOf(null)
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
                val trimmed = name.trim()
                if (trimmed.isBlank()) {
                    throw IllegalArgumentException("Name cannot be empty")
                }
                document = apiClient.updateDocumentName(apiKey, organizationId, documentId, trimmed)
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

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            errorMessage = null
            try {
                apiClient.deleteDocument(apiKey, organizationId, id)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Delete failed"
            }
        }
    }

    fun saveRotatedPdf(context: Context, rotations: List<Float>, replaceOriginal: Boolean) {
        viewModelScope.launch {
            isSavingRotation = true
            saveMessage = null
            saveErrorMessage = null
            try {
                val doc = document ?: apiClient.getDocument(apiKey, organizationId, documentId)
                document = doc
                val mimeType = doc.mimeType ?: guessMimeType(doc.name)
                if (mimeType != "application/pdf" && !doc.name.endsWith(".pdf", ignoreCase = true)) {
                    throw IllegalStateException("Rotation export is only available for PDF files.")
                }
                val rotatedFile = withContext(Dispatchers.IO) {
                    renderRotatedPdf(context, doc, rotations)
                }
                val targetName = if (replaceOriginal) {
                    doc.name
                } else {
                    appendSuffix(doc.name, "rotated")
                }
                apiClient.uploadDocument(
                    apiKey,
                    organizationId,
                    targetName,
                    "application/pdf",
                    rotatedFile.inputStream(),
                    rotatedFile.length()
                )
                if (replaceOriginal) {
                    apiClient.deleteDocument(apiKey, organizationId, documentId)
                }
                saveMessage = if (replaceOriginal) {
                    "Rotation saved. Original deleted."
                } else {
                    "Rotation saved as a new document."
                }
            } catch (e: Exception) {
                saveErrorMessage = e.message ?: "Failed to save rotation"
            } finally {
                isSavingRotation = false
            }
        }
    }

    fun createTag(name: String, color: String, description: String?) {
        viewModelScope.launch {
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

    fun createAndAddTag(name: String, color: String, description: String?) {
        viewModelScope.launch {
            try {
                val normalizedColor = if (color.trim().startsWith("#")) {
                    color.trim()
                } else {
                    "#${color.trim()}"
                }
                val tag = apiClient.createTag(apiKey, organizationId, name, normalizedColor, description)
                apiClient.addTagToDocument(apiKey, organizationId, documentId, tag.id)
                loadTags()
                loadDocument()
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
            previewPages = emptyList()
            try {
                val doc = document ?: apiClient.getDocument(apiKey, organizationId, documentId)
                document = doc
                val file = ensureCachedFile(context, doc)
                val mimeType = doc.mimeType ?: guessMimeType(doc.name)
                val bitmaps = withContext(Dispatchers.IO) { renderPreviewPages(file, mimeType) }
                if (!bitmaps.isNullOrEmpty()) {
                    previewPages = bitmaps.map { it.asImageBitmap() }
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

    private fun renderPreviewPages(file: File, mimeType: String): List<Bitmap>? {
        return when {
            mimeType.startsWith("image/") -> decodeBitmap(file)?.let { listOf(it) }
            mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true) ->
                renderPdfPages(file)
            else -> null
        }
    }

    private suspend fun renderRotatedPdf(context: Context, doc: Document, rotations: List<Float>): File {
        val source = ensureCachedFile(context, doc)
        val output = File(context.cacheDir, "rotated-${doc.id}.pdf")
        val pdfDocument = PdfDocument()
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    throw IllegalStateException("PDF has no pages.")
                }
                for (index in 0 until renderer.pageCount) {
                    val rotation = rotations.getOrNull(index) ?: 0f
                    val page = renderer.openPage(index)
                    page.use {
                        val width = it.width
                        val height = it.height
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val rotated = if (rotation % 360f != 0f) {
                            val matrix = Matrix().apply { postRotate(rotation) }
                            Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                        } else {
                            bitmap
                        }
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            rotated.width,
                            rotated.height,
                            index + 1
                        ).create()
                        val outPage = pdfDocument.startPage(pageInfo)
                        outPage.canvas.drawBitmap(rotated, 0f, 0f, null)
                        pdfDocument.finishPage(outPage)
                        if (rotated != bitmap) {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        FileOutputStream(output).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return output
    }

    private fun appendSuffix(fileName: String, suffix: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) + "-$suffix" + fileName.substring(dotIndex)
        } else {
            "$fileName-$suffix"
        }
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun renderPdfPages(file: File): List<Bitmap>? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                val maxWidth = 1200
                val pages = mutableListOf<Bitmap>()
                for (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    page.use {
                        val scale = minOf(1f, maxWidth.toFloat() / it.width.toFloat())
                        val width = (it.width * scale).toInt()
                        val height = (it.height * scale).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val matrix = Matrix().apply { postScale(scale, scale) }
                        it.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bitmap)
                    }
                }
                return pages
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
