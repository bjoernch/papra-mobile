package app.papra.mobile.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Document
import app.papra.mobile.data.OfflineCacheStore
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.math.ln
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
fun DocumentsScreen(
    apiClient: ApiClient,
    apiKey: String,
    organizationId: String,
    organizationName: String,
    onBack: () -> Unit,
    onPreview: (Document) -> Unit,
    showTopBar: Boolean = true,
    scanRequestId: Int = 0,
    uploadRequestId: Int = 0,
    onScanRequestHandled: () -> Unit = {},
    onUploadRequestHandled: () -> Unit = {},
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val offlineCacheStore = remember { OfflineCacheStore(context) }
    val viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModelFactory(apiClient, apiKey, organizationId, offlineCacheStore)
    )
    val scope = rememberCoroutineScope()

    var pendingDownload: Document? by remember { mutableStateOf(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    var showApplyTagDialog by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var expandedDocId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Document?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Document?>(null) }
    var showScanQualityDialog by remember { mutableStateOf(false) }
    var pendingScanQuality by remember { mutableStateOf<ScanQuality?>(null) }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.uploadDocument(uri, context.contentResolver)
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val document = pendingDownload
        if (uri != null && document != null) {
            viewModel.downloadDocumentToUri(document, uri, context.contentResolver)
        }
        pendingDownload = null
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val imageUris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
            val quality = pendingScanQuality ?: ScanQuality.MEDIUM
            if (imageUris.isNotEmpty()) {
                viewModel.uploadScannedPdfFromImages(
                    imageUris,
                    context.contentResolver,
                    context,
                    quality
                )
            } else {
                val pdfUri = scanResult?.pdf?.uri
                if (pdfUri != null) {
                    viewModel.uploadDocument(pdfUri, context.contentResolver)
                } else {
                    scanError = "Scanner did not return a PDF."
                }
            }
            pendingScanQuality = null
        }
    }

    val startScan: (ScanQuality) -> Unit = startScan@{ quality ->
        val activity = context as? Activity
        if (activity == null) {
            scanError = "Unable to start scanner."
            return@startScan
        }
        scanError = null
        pendingScanQuality = quality
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setGalleryImportAllowed(true)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                scanError = "Scanner unavailable on this device."
            }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDocuments()
        viewModel.loadTags()
    }

    LaunchedEffect(scanRequestId) {
        if (scanRequestId > 0) {
            showScanQualityDialog = true
            onScanRequestHandled()
        }
    }

    LaunchedEffect(uploadRequestId) {
        if (uploadRequestId > 0) {
            uploadLauncher.launch(arrayOf("*/*"))
            onUploadRequestHandled()
        }
    }

    LaunchedEffect(selectedTagId) {
        if (selectedTab == 0) {
            viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2 && viewModel.deletedDocuments.isEmpty()) {
            viewModel.loadDeletedDocuments()
        }
    }

    val showingSearch = searchQuery.isNotBlank()
    val baseList = when (selectedTab) {
        1 -> viewModel.documents.filter { viewModel.cachedDocIds.contains(it.id) }
        2 -> viewModel.deletedDocuments
        else -> viewModel.documents
    }
    val filteredList = if (showingSearch) {
        val query = searchQuery.trim()
        baseList.filter { it.name.contains(query, ignoreCase = true) }
    } else {
        baseList
    }
    val activeCount = when (selectedTab) {
        1 -> baseList.size
        2 -> viewModel.deletedDocumentsCount
        else -> viewModel.documentsCount
    }

    val isBusy = when {
        selectedTab == 2 -> viewModel.isLoadingDeleted
        else -> viewModel.isLoading
    }
    val isRefreshing = isBusy
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            when (selectedTab) {
                2 -> viewModel.loadDeletedDocuments()
                else -> viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
            }
            viewModel.loadTags()
        }
    )

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(organizationName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        },
        floatingActionButton = {}
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                isBusy && filteredList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
                viewModel.errorMessage != null && filteredList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(viewModel.errorMessage ?: "Failed to load documents")
                        Button(onClick = {
                            when (selectedTab) {
                                2 -> viewModel.loadDeletedDocuments()
                                else -> viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    if (viewModel.errorMessage != null) {
                        item {
                            Text(viewModel.errorMessage ?: "Action failed")
                        }
                    }
                    if (showingSearch && filteredList.isEmpty() && !isBusy) {
                        item {
                            Text("No results")
                        }
                    }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selectedTab == 1) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Offline library",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("Saved items only")
                                        }
                                    }
                                }
                                val lastUpdatedLabel = viewModel.lastUpdatedAt?.let { formatInstant(it) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(lastUpdatedLabel?.let { "Last updated: $it" } ?: "Last updated: —")
                                    if (isBusy) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text("Syncing…")
                                        }
                                    }
                                }

                                if (scanError != null) {
                                    Text(scanError ?: "")
                                }
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search title") },
                                    placeholder = { Text("Document name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Row {
                                            if (searchQuery.isNotBlank()) {
                                                IconButton(onClick = {
                                                    searchQuery = ""
                                                }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                                }
                                            }
                                            IconButton(onClick = {
                                                viewModel.addRecentSearch(searchQuery)
                                            }) {
                                                Icon(Icons.Default.Search, contentDescription = "Search")
                                            }
                                        }
                                    }
                                )
                                if (viewModel.recentSearches.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Recent searches")
                                        OutlinedButton(onClick = { viewModel.clearRecentSearches() }) {
                                            Text("Clear")
                                        }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        viewModel.recentSearches.forEach { query ->
                                            AssistChip(
                                                onClick = { searchQuery = query },
                                                label = { Text(query) }
                                            )
                                        }
                                    }
                                }

                                TabRow(selectedTabIndex = selectedTab) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = { Text("Documents") }
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = { Text("Offline") }
                                    )
                                    Tab(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        text = { Text("Trash") }
                                    )
                                }

                                if (selectedTab == 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            selectedTagId?.let { tagId ->
                                                val tagName = viewModel.tags.firstOrNull { it.id == tagId }?.name
                                                "Tag filter: ${tagName ?: "Selected"}"
                                            } ?: "Tag filter: All"
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { showTagFilterDialog = true }) {
                                                Text("Filter")
                                            }
                                            if (selectedTagId != null) {
                                                OutlinedButton(onClick = { selectedTagId = null }) {
                                                    Text("Clear")
                                                }
                                            }
                                        }
                                    }
                                }

                                if (viewModel.uploadInProgress) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Uploading ${viewModel.uploadFileName ?: "file"}")
                                            if (viewModel.uploadProgress != null) {
                                                LinearProgressIndicator(progress = viewModel.uploadProgress ?: 0f)
                                            } else {
                                                LinearProgressIndicator()
                                            }
                                        }
                                    }
                                }

                                Text("${filteredList.size} of $activeCount")
                            }
                        }

                    if (selectionMode) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${selectedIds.size} selected")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                    OutlinedButton(onClick = {
                                        selectedIds = filteredList.map { it.id }.toSet()
                                    }, modifier = Modifier.weight(1f).height(44.dp)) {
                                        Text("Select all")
                                    }
                                    OutlinedButton(
                                        onClick = { selectedIds = emptySet() },
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Text("Clear")
                                    }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                viewModel.shareDocumentsAsPdf(context, docs)
                                                selectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            enabled = selectedIds.isNotEmpty(),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Share")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                viewModel.deleteDocuments(docs)
                                                selectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            enabled = selectedIds.isNotEmpty()
                                            ,
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Delete")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showBulkTagDialog = true },
                                            enabled = selectedIds.isNotEmpty(),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Add tag")
                                        }
                                        OutlinedButton(
                                            onClick = { selectionMode = false; selectedIds = emptySet() },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Done")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                docs.forEach { viewModel.cacheDocument(context, it) }
                                            },
                                            enabled = selectedIds.isNotEmpty(),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Save offline")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                docs.forEach { viewModel.removeCachedDocument(context, it) }
                                            },
                                            enabled = selectedIds.isNotEmpty(),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Remove offline")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Documents")
                                OutlinedButton(onClick = { selectionMode = true }) {
                                    Text("Select")
                                }
                            }
                        }
                    }

                    items(filteredList) { document ->
                        DocumentRow(
                            document = document,
                            onPreview = { onPreview(document) },
                            onOpen = { viewModel.openDocument(context, document) },
                            onDownload = {
                                pendingDownload = document
                                downloadLauncher.launch(document.name)
                            },
                            onShare = { viewModel.shareDocumentAsPdf(context, document) },
                            showCheckbox = selectionMode,
                            selected = selectedIds.contains(document.id),
                            isCached = viewModel.cachedDocIds.contains(document.id),
                            isMenuExpanded = expandedDocId == document.id,
                            onMenuExpandedChange = { expanded ->
                                expandedDocId = if (expanded) document.id else null
                            },
                            onRename = {
                                renameTarget = document
                                showRenameDialog = true
                            },
                            onDelete = {
                                deleteTarget = document
                                showDeleteDialog = true
                            },
                            onToggleSelected = {
                                selectedIds = if (selectedIds.contains(document.id)) {
                                    selectedIds - document.id
                                } else {
                                    selectedIds + document.id
                                }
                            },
                            onToggleOffline = {
                                if (viewModel.cachedDocIds.contains(document.id)) {
                                    viewModel.removeCachedDocument(context, document)
                                } else {
                                    viewModel.cacheDocument(context, document)
                                }
                            }
                        )
                    }

                    if (selectedTab == 0 && !showingSearch && viewModel.hasMoreDocuments) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreDocuments() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !viewModel.isLoadingMore
                            ) {
                                Text(if (viewModel.isLoadingMore) "Loading…" else "Load more")
                            }
                        }
                    }
                }
            }
            }
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showBulkTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        var bulkTagError by remember { mutableStateOf<String?>(null) }
        var hue by remember { mutableStateOf(215f) }
        var saturation by remember { mutableStateOf(0.7f) }
        var value by remember { mutableStateOf(1f) }
        val previewColor = Color.hsv(hue, saturation, value)
        AlertDialog(
            onDismissRequest = { showBulkTagDialog = false },
            title = { Text("Add tag to selected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (viewModel.tags.isEmpty()) {
                        Text("No tags available")
                    } else {
                        viewModel.tags.forEach { tag ->
                            Button(
                                onClick = {
                                    val docs = filteredList.filter { selectedIds.contains(it.id) }
                                    viewModel.addTagToDocuments(tag.id, docs)
                                    showBulkTagDialog = false
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = runCatching {
                                        Color(android.graphics.Color.parseColor(tag.color ?: "#2F6BFF"))
                                    }.getOrDefault(MaterialTheme.colorScheme.primary),
                                    contentColor = runCatching {
                                        Color(android.graphics.Color.parseColor(tag.color ?: "#2F6BFF"))
                                    }.getOrDefault(MaterialTheme.colorScheme.primary).let { color ->
                                        if (color.luminance() > 0.6f) Color.Black else Color.White
                                    }
                                )
                            ) {
                                Text(tag.name)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("New tag name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (newTagName.isNotBlank()) {
                        HsvColorPicker(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            onColorChange = { newHue, newSat, newVal ->
                                hue = newHue
                                saturation = newSat
                                value = newVal
                            }
                        )
                        Button(
                            onClick = {
                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                val trimmed = newTagName.trim()
                                if (trimmed.isBlank() || docs.isEmpty()) return@Button
                                scope.launch {
                                    try {
                                        val tag = apiClient.createTag(
                                            apiKey,
                                            organizationId,
                                            trimmed,
                                            formatHexColor(previewColor),
                                            null
                                        )
                                        viewModel.loadTags()
                                        viewModel.addTagToDocuments(tag.id, docs)
                                        showBulkTagDialog = false
                                        selectionMode = false
                                        selectedIds = emptySet()
                                        bulkTagError = null
                                    } catch (e: Exception) {
                                        bulkTagError = e.message ?: "Failed to create tag"
                                    }
                                }
                            },
                            enabled = newTagName.trim().isNotBlank() && selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = previewColor,
                                contentColor = if (previewColor.luminance() > 0.6f) Color.Black else Color.White
                            )
                        ) {
                            Text("Create & add")
                        }
                        if (!bulkTagError.isNullOrBlank()) {
                            Text(bulkTagError ?: "")
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showBulkTagDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTagFilterDialog) {
        AlertDialog(
            onDismissRequest = { showTagFilterDialog = false },
            title = { Text("Filter by tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (viewModel.tags.isEmpty()) {
                        Text("No tags available")
                    } else {
                        viewModel.tags.forEach { tag ->
                            Button(
                                onClick = {
                                    selectedTagId = tag.id
                                    showTagFilterDialog = false
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showTagFilterDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showApplyTagDialog) {
        AlertDialog(
            onDismissRequest = { showApplyTagDialog = false },
            title = { Text("Apply tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (viewModel.tags.isEmpty()) {
                        Text("No tags available")
                    } else {
                        viewModel.tags.forEach { tag ->
                            Button(
                                onClick = {
                                    viewModel.addTagToDocuments(tag.id, filteredList)
                                    viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
                                    showApplyTagDialog = false
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showApplyTagDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                Unit
            }
        )
    }

    if (showCreateTagDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        val palette = listOf(
            "#2D6A4F", "#40916C", "#52B788", "#1D3557", "#457B9D",
            "#E76F51", "#E63946", "#F4A261", "#FFB703", "#8E9AAF"
        )
        var selectedColor by remember { mutableStateOf(palette.first()) }
        val isValidColor = selectedColor.matches(Regex("^#([0-9a-fA-F]{6})$"))
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("Create tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Color")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEach { hex ->
                            val isSelected = hex == selectedColor
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Black
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                    Text("Selected: $selectedColor")
                    if (!isValidColor) {
                        Text("Color must be a hex value like #2D6A4F")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createTag(name, selectedColor, description.ifBlank { null })
                        showCreateTagDialog = false
                    },
                    enabled = name.isNotBlank() && isValidColor
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog) {
        val doc = renameTarget
        var name by remember(doc) { mutableStateOf(doc?.name ?: "") }
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameTarget = null
            },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (doc != null) {
                            viewModel.renameDocument(doc.id, name)
                        }
                        showRenameDialog = false
                        renameTarget = null
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRenameDialog = false
                    renameTarget = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScanQualityDialog) {
        AlertDialog(
            onDismissRequest = { showScanQualityDialog = false },
            title = { Text("Scan quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the PDF quality. Lower quality creates smaller files.")
                    OutlinedButton(onClick = {
                        showScanQualityDialog = false
                        startScan(ScanQuality.LOW)
                    }) {
                        Text("Low (small file)")
                    }
                    OutlinedButton(onClick = {
                        showScanQualityDialog = false
                        startScan(ScanQuality.MEDIUM)
                    }) {
                        Text("Medium")
                    }
                    OutlinedButton(onClick = {
                        showScanQualityDialog = false
                        startScan(ScanQuality.HIGH)
                    }) {
                        Text("High (larger file)")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showScanQualityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showDeleteDialog) {
        val doc = deleteTarget
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deleteTarget = null
            },
            title = { Text("Delete document") },
            text = { Text("This removes the document from the server. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (doc != null) {
                            viewModel.deleteDocuments(listOf(doc))
                        }
                        showDeleteDialog = false
                        deleteTarget = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDeleteDialog = false
                    deleteTarget = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DocumentRow(
    document: Document,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    showCheckbox: Boolean,
    selected: Boolean,
    isCached: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelected: () -> Unit,
    onToggleOffline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (showCheckbox) onToggleSelected() else onPreview() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.name)
                    formatDate(document.createdAt)?.let { dateLabel ->
                        Text("Added: $dateLabel")
                    }
                    val secondary = buildString {
                        document.size?.let { append(formatBytes(it)) }
                    }
                    if (secondary.isNotBlank()) {
                        Text(secondary)
                    }
                    if (isCached) {
                        Text("Available offline")
                    }
                }
                if (showCheckbox) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() }
                    )
                } else {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { onMenuExpandedChange(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { onMenuExpandedChange(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = {
                                    onMenuExpandedChange(false)
                                    onOpen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save as") },
                                onClick = {
                                    onMenuExpandedChange(false)
                                    onDownload()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    onMenuExpandedChange(false)
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onMenuExpandedChange(false)
                                    onDelete()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isCached) "Remove offline" else "Make offline") },
                                onClick = {
                                    onMenuExpandedChange(false)
                                    onToggleOffline()
                                }
                            )
                        }
                    }
                }
            }
            val tags = document.tags
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        val tagColor = runCatching {
                            Color(android.graphics.Color.parseColor(tag.color ?: "#2F6BFF"))
                        }.getOrDefault(MaterialTheme.colorScheme.primary)
                        val textColor = if (tagColor.luminance() > 0.6f) Color.Black else Color.White
                        AssistChip(
                            onClick = {},
                            label = { Text(tag.name, color = textColor) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = tagColor,
                                labelColor = textColor
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val unit = 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val prefix = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), prefix)
}

private fun formatDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val instant = java.time.Instant.parse(raw)
        val formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(
            java.time.format.FormatStyle.MEDIUM
        ).withZone(java.time.ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        try {
            val offset = java.time.OffsetDateTime.parse(raw)
            val formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(
                java.time.format.FormatStyle.MEDIUM
            )
            formatter.format(offset)
        } catch (_: Exception) {
            raw
        }
    }
}


private fun formatInstant(instant: java.time.Instant): String {
    val formatter = java.time.format.DateTimeFormatter.ofLocalizedDateTime(
        java.time.format.FormatStyle.MEDIUM
    ).withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
