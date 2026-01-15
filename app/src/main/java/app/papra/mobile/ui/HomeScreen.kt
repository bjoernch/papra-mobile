package app.papra.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Document
import app.papra.mobile.data.Organization
import app.papra.mobile.data.OrganizationStats
import kotlin.math.ln
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    apiClient: ApiClient,
    apiKey: String,
    defaultOrganizationId: String?,
    selectedOrganizationId: String?,
    onSelectOrganization: (Organization) -> Unit,
    onSetDefaultOrganization: (String?) -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenDocument: (Document) -> Unit
) {
    val viewModel: OrganizationsViewModel = viewModel(
        factory = OrganizationsViewModelFactory(apiClient, apiKey)
    )
    var stats by remember { mutableStateOf<OrganizationStats?>(null) }
    var recentDocuments by remember { mutableStateOf<List<Document>>(emptyList()) }
    var showCreateOrgDialog by remember { mutableStateOf(false) }
    var createOrgError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Organization?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteFinal by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }

    LaunchedEffect(viewModel.organizations, defaultOrganizationId) {
        val orgs = viewModel.organizations
        if (orgs.isEmpty()) return@LaunchedEffect
        val preferred = defaultOrganizationId?.let { id -> orgs.firstOrNull { it.id == id } }
        val target = preferred ?: orgs.singleOrNull()
        if (target != null && selectedOrganizationId != target.id) {
            onSelectOrganization(target)
        }
    }

    LaunchedEffect(selectedOrganizationId) {
        val orgId = selectedOrganizationId ?: return@LaunchedEffect
        stats = try {
            apiClient.getOrganizationStats(apiKey, orgId)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(selectedOrganizationId) {
        val orgId = selectedOrganizationId ?: return@LaunchedEffect
        recentDocuments = try {
            apiClient.listDocuments(apiKey, orgId, pageIndex = 0, pageSize = 5).first
        } catch (_: Exception) {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Home",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            val documentsCount = stats?.documentsCount?.toString() ?: "-"
            val storageSize = stats?.documentsSize?.let { formatBytes(it) } ?: "-"
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Documents",
                        value = documentsCount,
                        color = Color(0xFF2F6BFF),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenDocuments() }
                    )
                    StatCard(
                        title = "Storage",
                        value = storageSize,
                        color = Color(0xFFEF6C00),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        title = "Add organization",
                        subtitle = "Create new",
                        color = Color(0xFF26A69A),
                        icon = Icons.Default.Business,
                        modifier = Modifier.weight(1f),
                        onClick = { showCreateOrgDialog = true }
                    )
                    ActionCard(
                        title = "Offline files",
                        subtitle = "On this device",
                        color = Color(0xFF7E57C2),
                        icon = Icons.Default.Folder,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenOffline
                    )
                }
            }
        }

        if (viewModel.isLoading) {
            item {
                Text("Loading organizations...")
            }
        } else if (!viewModel.errorMessage.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(viewModel.errorMessage ?: "Failed to load organizations")
                        OutlinedButton(onClick = { viewModel.loadOrganizations() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else if (viewModel.organizations.isEmpty()) {
            item {
                Text("No organizations found.")
            }
        }

        if (recentDocuments.isNotEmpty()) {
            item {
                Text(
                    "Recent documents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(recentDocuments) { document ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            document.name,
                            modifier = Modifier.clickable { onOpenDocument(document) }
                        )
                        if (!document.createdAt.isNullOrBlank()) {
                            Text(
                                "Added: ${document.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Organizations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                viewModel.organizations.forEach { organization ->
                    OrganizationCard(
                        organization = organization,
                        isDefault = organization.id == defaultOrganizationId,
                        isSelected = organization.id == selectedOrganizationId,
                        onSelect = { onSelectOrganization(organization) },
                        onSetDefault = {
                            onSetDefaultOrganization(if (organization.id == defaultOrganizationId) null else organization.id)
                        },
                        onDelete = {
                            deleteTarget = organization
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }

    }

    if (showCreateOrgDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateOrgDialog = false },
            title = { Text("Create organization") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!createOrgError.isNullOrBlank()) {
                        Text(createOrgError ?: "")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isBlank()) return@Button
                        scope.launch {
                            try {
                                apiClient.createOrganization(apiKey, trimmed)
                                viewModel.loadOrganizations()
                                createOrgError = null
                                showCreateOrgDialog = false
                            } catch (e: Exception) {
                                createOrgError = e.message ?: "Failed to create organization"
                            }
                        }
                    },
                    enabled = name.trim().length >= 3
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateOrgDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val org = deleteTarget
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete organization") },
            text = {
                Text("This removes ${org?.name ?: "this organization"} permanently. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        showDeleteFinal = true
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteFinal) {
        val org = deleteTarget
        AlertDialog(
            onDismissRequest = { showDeleteFinal = false },
            title = { Text("Type DELETE to confirm") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Deleting ${org?.name ?: "this organization"} cannot be undone.")
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        label = { Text("Type DELETE") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deleteTarget ?: return@Button
                        scope.launch {
                            try {
                                apiClient.deleteOrganization(apiKey, target.id)
                                viewModel.loadOrganizations()
                                deleteTarget = null
                                deleteConfirmText = ""
                            } catch (e: Exception) {
                                createOrgError = e.message ?: "Failed to delete organization"
                            } finally {
                                showDeleteFinal = false
                            }
                        }
                    },
                    enabled = deleteConfirmText == "DELETE"
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteFinal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(120.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White)
            Text(
                value,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable { onClick() },
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Column {
                Text(title, color = Color.White)
                Text(
                    subtitle,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OrganizationCard(
    organization: Organization,
    isDefault: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(organization.name)
                if (isSelected) {
                    Text("Active", color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onSetDefault) {
                    Text(if (isDefault) "Default" else "Set default")
                }
                OutlinedButton(onClick = onDelete, enabled = !isDefault) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete", modifier = Modifier.padding(start = 8.dp))
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
