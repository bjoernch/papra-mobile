package app.papra.mobile.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Organization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    apiClient: ApiClient,
    apiKey: String,
    baseUrl: String,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    defaultOrganizationId: String?,
    onSetDefaultOrganization: (String?) -> Unit,
    suppressAutoOpen: Boolean,
    onResetApp: () -> Unit,
    onOrganizationSelected: (Organization) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: OrganizationsViewModel = viewModel(
        factory = OrganizationsViewModelFactory(apiClient, apiKey)
    )
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleNotifications(true)
        }
    }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var notificationsError by remember { mutableStateOf<String?>(null) }
    var autoOpened by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }

    LaunchedEffect(viewModel.organizations, defaultOrganizationId) {
        if (autoOpened) return@LaunchedEffect
        if (suppressAutoOpen) return@LaunchedEffect
        val organizations = viewModel.organizations
        if (organizations.isEmpty()) return@LaunchedEffect
        val preferred = defaultOrganizationId?.let { id ->
            organizations.firstOrNull { it.id == id }
        }
        val target = preferred ?: organizations.singleOrNull()
        if (target != null) {
            autoOpened = true
            onOrganizationSelected(target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organizations") },
                actions = {
                    IconButton(onClick = { viewModel.loadOrganizations() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showNotificationsDialog = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = if (notificationsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { onToggleBiometric(!biometricEnabled) }) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "Biometric lock",
                            tint = if (biometricEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Reset app")
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
                    item {
                        CertificateStatusRow(baseUrl = baseUrl)
                    }
                    items(viewModel.organizations) { organization ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOrganizationSelected(organization) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(organization.name)
                                val isDefault = organization.id == defaultOrganizationId
                                OutlinedButton(
                                    onClick = {
                                        onSetDefaultOrganization(
                                            if (isDefault) null else organization.id
                                        )
                                    }
                                ) {
                                    Text(if (isDefault) "Default" else "Set default")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = {
                showNotificationsDialog = false
                notificationsError = null
            },
            title = { Text("Notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New documents uploaded")
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        notificationsError = "Allow notifications to enable alerts."
                                    } else {
                                        onToggleNotifications(true)
                                        notificationsError = null
                                    }
                                } else {
                                    onToggleNotifications(false)
                                    notificationsError = null
                                }
                            }
                        )
                    }
                    Text("Checks for new documents roughly every 15 minutes.")
                    if (notificationsError != null) {
                        Text(notificationsError ?: "")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationsDialog = false
                    notificationsError = null
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset app") },
            text = {
                Text("This clears local settings and offline files. It will not delete anything on the server.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetApp()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
