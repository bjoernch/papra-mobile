package app.papra.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import app.papra.mobile.data.Tag
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(
    apiClient: ApiClient,
    apiKey: String,
    organizationId: String,
    documentId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DocumentPreviewViewModel = viewModel(
        factory = DocumentPreviewViewModelFactory(apiClient, apiKey, organizationId, documentId)
    )

    var showTagDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var editTag: Tag? by remember { mutableStateOf(null) }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.downloadToUri(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadPreview(context)
    }

    val document = viewModel.document
    val previewBitmap = viewModel.previewBitmap

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadDocument()
                        viewModel.loadPreview(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (viewModel.errorMessage != null) {
                    Text(viewModel.errorMessage ?: "Error")
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (viewModel.isPreviewLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        } else if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(viewModel.previewMessage ?: "Preview not available")
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { viewModel.openExternal(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Open")
                    }
                    OutlinedButton(
                        onClick = { downloadLauncher.launch(document?.name ?: "document") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Download")
                    }
                    OutlinedButton(onClick = { viewModel.shareExternal(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Share")
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Details")
                            OutlinedButton(onClick = { showRenameDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Rename")
                            }
                        }
                        Text("Name: ${document?.name ?: "-"}")
                        Text("Type: ${document?.mimeType ?: "-"}")
                        Text("Size: ${document?.size?.let { formatBytes(it) } ?: "-"}")
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tags")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showTagDialog = true }) {
                                    Text("Add")
                                }
                                OutlinedButton(onClick = { showManageTagsDialog = true }) {
                                    Text("Manage")
                                }
                            }
                        }
                        val tags = document?.tags ?: emptyList()
                        if (tags.isEmpty()) {
                            Text("No tags")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tags.forEach { tag ->
                                    TagRow(tag = tag, onRemove = { viewModel.removeTag(tag.id) })
                                }
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Activity")
                        if (viewModel.activities.isEmpty()) {
                            Text("No activity yet")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                viewModel.activities.forEach { activity ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(activity.type ?: "Activity")
                                        if (!activity.createdAt.isNullOrBlank()) {
                                            Text(activity.createdAt ?: "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTagDialog) {
        val currentTagIds = document?.tags?.map { it.id }?.toSet() ?: emptySet()
        val availableTags = viewModel.availableTags.filter { it.id !in currentTagIds }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add tag") },
            text = {
                if (availableTags.isEmpty()) {
                    Text("No available tags")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTags.forEach { tag ->
                            Button(
                                onClick = {
                                    viewModel.addTag(tag.id)
                                    showTagDialog = false
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
                OutlinedButton(onClick = { showTagDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRenameDialog) {
        var name by remember(document?.name) { mutableStateOf(document?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                        viewModel.renameDocument(name)
                        showRenameDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManageTagsDialog) {
        AlertDialog(
            onDismissRequest = { showManageTagsDialog = false },
            title = { Text("Manage tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCreateTagDialog = true }) {
                        Text("Create tag")
                    }
                    if (viewModel.availableTags.isEmpty()) {
                        Text("No tags available")
                    } else {
                        viewModel.availableTags.forEach { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tag.name)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editTag = tag }) {
                                        Text("Edit")
                                    }
                                    OutlinedButton(onClick = { viewModel.deleteTag(tag.id) }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showManageTagsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showCreateTagDialog) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf("#2D6A4F") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("Create tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Color (hex)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createTag(name, color, description.ifBlank { null })
                        showCreateTagDialog = false
                    },
                    enabled = name.isNotBlank() && color.isNotBlank()
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

    editTag?.let { tag ->
        var name by remember { mutableStateOf(tag.name) }
        var color by remember { mutableStateOf(tag.color ?: "#2D6A4F") }
        var description by remember { mutableStateOf(tag.description ?: "") }
        AlertDialog(
            onDismissRequest = { editTag = null },
            title = { Text("Edit tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Color (hex)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTag(tag.id, name, color, description.ifBlank { null })
                        editTag = null
                    },
                    enabled = name.isNotBlank() && color.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editTag = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TagRow(tag: Tag, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tag.name)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
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
