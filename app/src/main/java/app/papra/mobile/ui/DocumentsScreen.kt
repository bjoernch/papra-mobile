package app.papra.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Document
import app.papra.mobile.data.OfflineCacheStore
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    apiClient: ApiClient,
    apiKey: String,
    organizationId: String,
    organizationName: String,
    onBack: () -> Unit,
    onPreview: (Document) -> Unit
) {
    val context = LocalContext.current
    val offlineCacheStore = remember { OfflineCacheStore(context) }
    val viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModelFactory(apiClient, apiKey, organizationId, offlineCacheStore)
    )

    var pendingDownload: Document? by remember { mutableStateOf(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        viewModel.loadDocuments()
        viewModel.loadOrganizationStats()
        viewModel.loadTags()
    }

    LaunchedEffect(selectedTagId) {
        if (selectedTab == 0) {
            viewModel.loadDocuments(tags = selectedTagId?.let { listOf(it) })
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && viewModel.deletedDocuments.isEmpty()) {
            viewModel.loadDeletedDocuments()
        }
    }

    val showingSearch = searchQuery.isNotBlank()
    val baseList = if (selectedTab == 1) viewModel.deletedDocuments else viewModel.documents
    val filteredList = if (showingSearch) {
        val query = searchQuery.trim()
        baseList.filter { it.name.contains(query, ignoreCase = true) }
    } else {
        baseList
    }
    val activeCount = if (selectedTab == 1) viewModel.deletedDocumentsCount else viewModel.documentsCount

    val isBusy = when {
        selectedTab == 1 -> viewModel.isLoadingDeleted
        else -> viewModel.isLoading
    }

    Scaffold(
        topBar = {
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
                        viewModel.loadOrganizationStats()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Upload")
            }
        }
    ) { padding ->
        when {
            isBusy -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            }
            viewModel.errorMessage != null && filteredList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(viewModel.errorMessage ?: "Failed to load documents")
                    Button(onClick = { viewModel.loadDocuments() }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                                        }) {
                                            Icon(Icons.Default.Search, contentDescription = "Search")
                                        }
                                    }
                                }
                            )

                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Documents") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
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

                            val stats = viewModel.organizationStats
                            if (stats != null) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Organization stats")
                                        Text("${stats.documentsCount} documents")
                                        Text("${formatBytes(stats.documentsSize)} total")
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            selectedIds = filteredList.map { it.id }.toSet()
                                        }) {
                                            Text("Select all")
                                        }
                                        OutlinedButton(onClick = { selectedIds = emptySet() }) {
                                            Text("Clear")
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                viewModel.deleteDocuments(docs)
                                                selectionMode = false
                                                selectedIds = emptySet()
                                            },
                                            enabled = selectedIds.isNotEmpty()
                                        ) {
                                            Text("Delete")
                                        }
                                        OutlinedButton(
                                            onClick = { showBulkTagDialog = true },
                                            enabled = selectedIds.isNotEmpty()
                                        ) {
                                            Text("Add tag")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                docs.forEach { viewModel.cacheDocument(context, it) }
                                            },
                                            enabled = selectedIds.isNotEmpty()
                                        ) {
                                            Text("Save offline")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val docs = filteredList.filter { selectedIds.contains(it.id) }
                                                docs.forEach { viewModel.removeCachedDocument(context, it) }
                                            },
                                            enabled = selectedIds.isNotEmpty()
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
                            showCheckbox = selectionMode,
                            selected = selectedIds.contains(document.id),
                            isCached = viewModel.cachedDocIds.contains(document.id),
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
                }
            }
        }
    }

    if (showBulkTagDialog) {
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(tag.name)
                            }
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
}

@Composable
private fun DocumentRow(
    document: Document,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    showCheckbox: Boolean,
    selected: Boolean,
    isCached: Boolean,
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
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onOpen) {
                    Text("Open")
                }
                OutlinedButton(onClick = onDownload, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Download")
                }
                OutlinedButton(onClick = onToggleOffline, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (isCached) "Offline" else "Save")
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
