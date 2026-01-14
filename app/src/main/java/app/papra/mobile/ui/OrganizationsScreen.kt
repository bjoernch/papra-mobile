package app.papra.mobile.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ExitToApp
import androidx.compose.material3.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Organization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    apiClient: ApiClient,
    apiKey: String,
    onOrganizationSelected: (Organization) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: OrganizationsViewModel = viewModel(
        factory = OrganizationsViewModelFactory(apiClient, apiKey)
    )

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organizations") },
                actions = {
                    IconButton(onClick = { viewModel.loadOrganizations() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Log out")
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
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
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
                    items(viewModel.organizations) { organization ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOrganizationSelected(organization) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(organization.name)
                                Text("${organization.id}")
                            }
                        }
                    }
                }
            }
        }
    }
}
