package app.papra.mobile

import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.notifications.NotificationScheduler
import app.papra.mobile.ui.ApiKeyScreen
import app.papra.mobile.ui.BiometricLockScreen
import app.papra.mobile.ui.DocumentPreviewScreen
import app.papra.mobile.ui.DocumentsScreen
import app.papra.mobile.ui.HomeScreen
import app.papra.mobile.ui.SettingsScreen
import app.papra.mobile.ui.TagsScreen
import app.papra.mobile.ui.theme.PapraTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import app.papra.mobile.data.Organization
import androidx.navigation.compose.currentBackStackEntryAsState

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this).apply {
            setContent {
                PapraTheme {
                    PapraApp()
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
fun PapraApp() {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val apiKey by apiKeyStore.apiKeyFlow.collectAsState(initial = null)
    val baseUrl by apiKeyStore.baseUrlFlow.collectAsState(initial = null)
    val biometricEnabled by apiKeyStore.biometricEnabledFlow.collectAsState(initial = false)
    val notificationsEnabled by apiKeyStore.notificationsEnabledFlow.collectAsState(initial = false)
    val defaultOrganizationId by apiKeyStore.defaultOrganizationIdFlow.collectAsState(initial = null)
    val resolvedBaseUrl = normalizeBaseUrl(baseUrl?.ifBlank { null } ?: "https://api.papra.app")
    val apiClient = remember(resolvedBaseUrl) { ApiClient(baseUrl = resolvedBaseUrl) }
    val scope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(baseUrl, apiKey) {
        val current = baseUrl?.trim().orEmpty()
        if (current.isNotBlank() && apiKey != null) {
            val normalized = normalizeBaseUrl(current)
            if (normalized != current) {
                apiKeyStore.saveSettings(apiKey ?: "", normalized)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(notificationsEnabled, apiKey, resolvedBaseUrl) {
        if (notificationsEnabled && !apiKey.isNullOrBlank()) {
            NotificationScheduler.schedule(context)
        } else {
            NotificationScheduler.cancel(context)
        }
    }

    if (apiKey.isNullOrBlank()) {
        ApiKeyScreen(
            initialBaseUrl = baseUrl?.trim().orEmpty(),
            onSave = { key, url ->
                val normalizedUrl = normalizeBaseUrl(url)
                scope.launch { apiKeyStore.saveSettings(key, normalizedUrl) }
            }
        )
    } else if (biometricEnabled && !isUnlocked) {
        BiometricLockScreen(context = context, onAuthenticated = { isUnlocked = true })
    } else {
        MainShell(
            apiClient = apiClient,
            apiKey = apiKey ?: "",
            baseUrl = resolvedBaseUrl,
            biometricEnabled = biometricEnabled,
            onToggleBiometric = { enabled ->
                scope.launch { apiKeyStore.setBiometricEnabled(enabled) }
            },
            notificationsEnabled = notificationsEnabled,
            onToggleNotifications = { enabled ->
                scope.launch { apiKeyStore.setNotificationsEnabled(enabled) }
            },
            defaultOrganizationId = defaultOrganizationId,
            onSetDefaultOrganization = { orgId ->
                scope.launch { apiKeyStore.setDefaultOrganizationId(orgId) }
            },
            onResetApp = {
                scope.launch {
                    apiKeyStore.clearApiKey()
                    clearOfflineCache(context)
                }
                NotificationScheduler.cancel(context)
            },
            onUpdateApiKey = { newKey ->
                scope.launch { apiKeyStore.saveSettings(newKey, resolvedBaseUrl) }
            },
            onLogout = {
                scope.launch { apiKeyStore.clearApiKey() }
            }
        )
    }
}

private fun clearOfflineCache(context: android.content.Context) {
    val cacheDir = java.io.File(context.filesDir, "offline_docs")
    cacheDir.listFiles()?.forEach { it.delete() }
    cacheDir.delete()
}

private fun normalizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val noTrailingSlash = withScheme.removeSuffix("/")
    return if (noTrailingSlash.endsWith("/api")) noTrailingSlash else "$noTrailingSlash/api"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    apiClient: ApiClient,
    apiKey: String,
    baseUrl: String,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    defaultOrganizationId: String?,
    onSetDefaultOrganization: (String?) -> Unit,
    onResetApp: () -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    var selectedOrg by remember { mutableStateOf<Organization?>(null) }
    var scanRequestId by remember { mutableStateOf(0) }
    var uploadRequestId by remember { mutableStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val baseRoute = when {
        currentRoute == null -> null
        currentRoute.startsWith("documents") -> "documents"
        else -> currentRoute.substringBefore("?").substringBefore("/")
    }

    val items = listOf(
        NavItem("home", "Home", Icons.Default.Home),
        NavItem("documents", "Files", Icons.Default.Description),
        NavItem("tags", "Tags", Icons.Default.Label),
        NavItem("settings", "Settings", Icons.Default.Settings)
    )
    val scanItem = NavItem("add", "Add", Icons.Default.Add)

    Scaffold(
        topBar = {
            if (baseRoute in items.map { it.route }) {
                val title = items.firstOrNull { it.route == baseRoute }?.label ?: "Papra"
                androidx.compose.material3.TopAppBar(
                    title = { Text(title) }
                )
            }
        },
        bottomBar = {
            if (baseRoute in items.map { it.route }) {
                NavigationBar(
                    containerColor = Color(0xFFE9ECFF)
                ) {
                    val leftItems = items.take(2)
                    val rightItems = items.drop(2)
                    leftItems.forEach { item ->
                        NavigationBarItem(
                            selected = baseRoute == item.route,
                            onClick = { navController.navigate(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            showAddMenu = true
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF2F6BFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(scanItem.icon, contentDescription = scanItem.label, tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Upload files") },
                                    leadingIcon = {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                    },
                                    onClick = {
                                        showAddMenu = false
                                        uploadRequestId += 1
                                        navController.navigate("documents")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan document") },
                                    leadingIcon = {
                                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                                    },
                                    onClick = {
                                        showAddMenu = false
                                        scanRequestId += 1
                                        navController.navigate("documents")
                                    }
                                )
                            }
                        },
                        label = { Text(scanItem.label) }
                    )
                    rightItems.forEach { item ->
                        NavigationBarItem(
                            selected = baseRoute == item.route,
                            onClick = { navController.navigate(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    apiClient = apiClient,
                    apiKey = apiKey,
                    defaultOrganizationId = defaultOrganizationId,
                    selectedOrganizationId = selectedOrg?.id,
                    onSelectOrganization = { org ->
                        selectedOrg = org
                    },
                    onSetDefaultOrganization = onSetDefaultOrganization,
                    onOpenDocuments = {
                        navController.navigate("documents")
                    },
                    onOpenOffline = {
                        navController.navigate("documents?tab=offline")
                    },
                    onOpenDocument = { document ->
                        val orgId = selectedOrg?.id
                        if (orgId != null) {
                            navController.navigate("document/${orgId}/${document.id}")
                        }
                    }
                )
            }
            composable(
                route = "documents?tab={tab}",
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = "documents"
                    }
                )
            ) { backStackEntry ->
                val org = selectedOrg
                if (org == null) {
                    Text(
                        "Select an organization on Home.",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val tabArg = backStackEntry.arguments?.getString("tab") ?: "documents"
                    val initialTab = when (tabArg) {
                        "offline" -> 1
                        "trash" -> 2
                        else -> 0
                    }
                    DocumentsScreen(
                        apiClient = apiClient,
                        apiKey = apiKey,
                        organizationId = org.id,
                        organizationName = org.name,
                        onBack = {},
                        onPreview = { document ->
                            navController.navigate("document/${org.id}/${document.id}")
                        },
                        showTopBar = false,
                        scanRequestId = scanRequestId,
                        uploadRequestId = uploadRequestId,
                        onScanRequestHandled = { scanRequestId = 0 },
                        onUploadRequestHandled = { uploadRequestId = 0 },
                        initialTab = initialTab
                    )
                }
            }
            composable("tags") {
                TagsScreen(
                    apiClient = apiClient,
                    apiKey = apiKey,
                    organizationId = selectedOrg?.id
                )
            }
            composable("settings") {
                SettingsScreen(
                    baseUrl = baseUrl,
                    biometricEnabled = biometricEnabled,
                    onToggleBiometric = onToggleBiometric,
                    notificationsEnabled = notificationsEnabled,
                    onToggleNotifications = onToggleNotifications,
                    onResetApp = onResetApp,
                    onUpdateApiKey = onUpdateApiKey,
                    onLogout = onLogout
                )
            }
            composable(
                route = "document/{orgId}/{docId}",
                arguments = listOf(
                    navArgument("orgId") { type = NavType.StringType },
                    navArgument("docId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val orgId = backStackEntry.arguments?.getString("orgId") ?: ""
                val docId = backStackEntry.arguments?.getString("docId") ?: ""
                DocumentPreviewScreen(
                    apiClient = apiClient,
                    apiKey = apiKey,
                    organizationId = orgId,
                    documentId = docId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
