package app.papra.mobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    sharedUri: Uri,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ShareUploadViewModel = viewModel(
        factory = ShareUploadViewModelFactory(apiClient, apiKey)
    )

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share to Papra") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                    if (viewModel.successMessage != null) {
                        item {
                            Text(viewModel.successMessage ?: "Uploaded")
                        }
                    }
                    items(viewModel.organizations) { org ->
                        OrganizationCard(
                            organization = org,
                            onSelect = {
                                viewModel.uploadToOrganization(sharedUri, context.contentResolver, org.id)
                            }
                        )
                    }
                }
            }
        }
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
