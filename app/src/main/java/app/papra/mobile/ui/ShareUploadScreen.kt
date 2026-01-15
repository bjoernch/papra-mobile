package app.papra.mobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Organization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareUploadScreen(
    apiClient: ApiClient,
    apiKey: String,
    sharedUris: List<Uri>,
    queueStore: app.papra.mobile.data.ShareUploadQueueStore,
    workManager: androidx.work.WorkManager,
    wifiOnly: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ShareUploadViewModel = viewModel(
        factory = ShareUploadViewModelFactory(apiClient, apiKey, queueStore, workManager, wifiOnly)
    )
    var showCloseBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }
    val sessionIds = viewModel.currentSessionIds
    val sessionInQueue = viewModel.uploadQueue.any { it.id in sessionIds }
    val sessionStarted = sessionIds.isNotEmpty()
    val sessionComplete = sessionStarted && !sessionInQueue
    val canClose = !sessionStarted || sessionComplete

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share to Papra") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (canClose) {
                                onClose()
                            } else {
                                showCloseBlocked = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            viewModel.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(viewModel.errorMessage ?: "Failed to load organizations")
                    Button(onClick = { viewModel.loadOrganizations() }) {
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
                    if (viewModel.uploadInProgress) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Uploading...")
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                    if (viewModel.uploadErrorMessage != null) {
                        item {
                            Text(
                                viewModel.uploadErrorMessage ?: "Upload failed",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (viewModel.successMessage != null) {
                        item {
                            Text(
                                viewModel.successMessage ?: "Uploaded",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (sessionComplete) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("All files uploaded.")
                                    OutlinedButton(onClick = onClose) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                    if (viewModel.uploadQueue.isNotEmpty()) {
                        item {
                            Text("Upload queue", style = MaterialTheme.typography.titleSmall)
                        }
                        items(viewModel.uploadQueue) { item ->
                            val workInfo = viewModel.workStates[item.workId]
                            val progress = workInfo?.progress?.getFloat(app.papra.mobile.work.ShareUploadWorker.KEY_PROGRESS, -1f)
                            val immediateProgress = viewModel.immediateProgress[item.id]
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.fileName)
                                    if (progress != null && progress >= 0f) {
                                        LinearProgressIndicator(progress = progress)
                                    } else if (immediateProgress != null) {
                                        LinearProgressIndicator(progress = immediateProgress)
                                    }
                                    val stateLabel = when (workInfo?.state) {
                                        androidx.work.WorkInfo.State.SUCCEEDED -> "Uploaded"
                                        androidx.work.WorkInfo.State.FAILED -> "Failed"
                                        androidx.work.WorkInfo.State.RUNNING -> "Uploading..."
                                        androidx.work.WorkInfo.State.ENQUEUED -> "Queued"
                                        else -> viewModel.immediateStatus[item.id] ?: "Queued"
                                    }
                                    Text(stateLabel)
                                    if (workInfo?.state == androidx.work.WorkInfo.State.FAILED) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { viewModel.retryQueuedItem(item) }) {
                                                Text("Retry")
                                            }
                                            OutlinedButton(onClick = { viewModel.removeQueuedItem(item.id) }) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(viewModel.organizations) { org ->
                        OrganizationCard(
                            organization = org,
                            onSelect = {
                                viewModel.uploadImmediately(context, sharedUris, context.contentResolver, org.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCloseBlocked) {
        AlertDialog(
            onDismissRequest = { showCloseBlocked = false },
            title = { Text("Uploads in progress") },
            text = { Text("Please wait until all files are uploaded before closing.") },
            confirmButton = {
                Button(onClick = { showCloseBlocked = false }) {
                    Text("OK")
                }
            }
        )
    }

}

@Composable
private fun OrganizationCard(organization: Organization, onSelect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(organization.name)
            Button(onClick = onSelect, modifier = Modifier.padding(top = 12.dp)) {
                Text("Upload here")
            }
        }
    }
}
