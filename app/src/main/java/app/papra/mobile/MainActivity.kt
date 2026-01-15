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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import app.papra.mobile.ui.PinLockScreen
import app.papra.mobile.ui.SettingsScreen
import app.papra.mobile.ui.TagsScreen
import app.papra.mobile.ui.theme.PapraTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.papra.mobile.ui.OrganizationsViewModel
import app.papra.mobile.ui.OrganizationsViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import android.view.WindowManager

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
    val autoLockMinutes by apiKeyStore.autoLockMinutesFlow.collectAsState(initial = 0)
    val hideInRecents by apiKeyStore.hideInRecentsFlow.collectAsState(initial = false)
    val uploadWifiOnly by apiKeyStore.uploadWifiOnlyFlow.collectAsState(initial = true)
    val defaultOrganizationId by apiKeyStore.defaultOrganizationIdFlow.collectAsState(initial = null)
    val pinHash by apiKeyStore.pinHashFlow.collectAsState(initial = null)
    val resolvedBaseUrl = normalizeBaseUrl(baseUrl?.ifBlank { null } ?: "https://api.papra.app")
    val apiClient = remember(resolvedBaseUrl) { ApiClient(baseUrl = resolvedBaseUrl) }
    val scope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }
    var lastBackgroundAt by remember { mutableStateOf<Long?>(null) }
    var usePinFallback by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val pinEnabled = !pinHash.isNullOrBlank()
    val lockEnabled = biometricEnabled || pinEnabled

    LaunchedEffect(biometricEnabled, pinEnabled) {
        if (!lockEnabled) {
            isUnlocked = true
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, lockEnabled, autoLockMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    lastBackgroundAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    val last = lastBackgroundAt
                    if (lockEnabled && autoLockMinutes > 0 && last != null) {
                        val elapsed = System.currentTimeMillis() - last
                        if (elapsed >= autoLockMinutes * 60_000L) {
                            isUnlocked = false
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    androidx.compose.runtime.LaunchedEffect(hideInRecents) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (hideInRecents) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
    } else if (lockEnabled && !isUnlocked) {
        if (pinEnabled && usePinFallback) {
            PinLockScreen(
                onSubmit = { pin ->
                    val verified = apiKeyStore.verifyPin(pin, pinHash)
                    if (verified) {
                        isUnlocked = true
                        usePinFallback = false
                        pinErrorMessage = null
                    } else {
                        pinErrorMessage = "Incorrect PIN"
                    }
                },
                errorMessage = pinErrorMessage
            )
        } else if (biometricEnabled) {
            BiometricLockScreen(
                context = context,
                onAuthenticated = {
                    isUnlocked = true
                    usePinFallback = false
                    pinErrorMessage = null
                },
                onUsePin = if (pinEnabled) {
                    {
                        usePinFallback = true
                        pinErrorMessage = null
                    }
                } else {
                    null
                }
            )
        } else {
            PinLockScreen(
                onSubmit = { pin ->
                    val verified = apiKeyStore.verifyPin(pin, pinHash)
                    if (verified) {
                        isUnlocked = true
                        pinErrorMessage = null
                    } else {
                        pinErrorMessage = "Incorrect PIN"
                    }
                },
                errorMessage = pinErrorMessage
            )
        }
    } else {
        MainShell(
            apiClient = apiClient,
            apiKey = apiKey ?: "",
            baseUrl = resolvedBaseUrl,
            biometricEnabled = biometricEnabled,
            onToggleBiometric = { enabled ->
                scope.launch { apiKeyStore.setBiometricEnabled(enabled) }
            },
            pinEnabled = pinEnabled,
            onSetPin = { pin ->
                scope.launch { apiKeyStore.setPin(pin) }
            },
            onClearPin = {
                scope.launch { apiKeyStore.clearPin() }
            },
            autoLockMinutes = autoLockMinutes,
            onSetAutoLockMinutes = { minutes ->
                scope.launch { apiKeyStore.setAutoLockMinutes(minutes) }
            },
            hideInRecents = hideInRecents,
            onToggleHideInRecents = { enabled ->
                scope.launch { apiKeyStore.setHideInRecents(enabled) }
            },
            onLockNow = {
                if (lockEnabled) {
                    isUnlocked = false
                    usePinFallback = false
                    pinErrorMessage = null
                }
            },
            uploadWifiOnly = uploadWifiOnly,
            onToggleUploadWifiOnly = { enabled ->
                scope.launch { apiKeyStore.setUploadWifiOnly(enabled) }
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
    pinEnabled: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    autoLockMinutes: Int,
    onSetAutoLockMinutes: (Int) -> Unit,
    hideInRecents: Boolean,
    onToggleHideInRecents: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    uploadWifiOnly: Boolean,
    onToggleUploadWifiOnly: (Boolean) -> Unit,
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
    var showOrgMenu by remember { mutableStateOf(false) }
    val orgViewModel: OrganizationsViewModel = viewModel(
        factory = OrganizationsViewModelFactory(apiClient, apiKey)
    )
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

    LaunchedEffect(apiKey) {
        orgViewModel.loadOrganizations()
    }

    LaunchedEffect(orgViewModel.organizations, defaultOrganizationId) {
        if (selectedOrg != null) return@LaunchedEffect
        val orgs = orgViewModel.organizations
        val preferred = defaultOrganizationId?.let { id -> orgs.firstOrNull { it.id == id } }
        val target = preferred ?: orgs.singleOrNull()
        if (target != null) {
            selectedOrg = target
        }
    }

    Scaffold(
        topBar = {
            if (baseRoute in items.map { it.route }) {
                val baseTitle = items.firstOrNull { it.route == baseRoute }?.label ?: "Papra"
                val showOrgSwitcher = baseRoute == "documents" || baseRoute == "tags"
                val title = if (showOrgSwitcher) {
                    val orgLabel = selectedOrg?.name ?: "Select organization"
                    "$baseTitle · $orgLabel"
                } else {
                    baseTitle
                }
                androidx.compose.material3.TopAppBar(
                    title = { Text(title) },
                    actions = {
                        if (showOrgSwitcher) {
                            IconButton(onClick = { showOrgMenu = true }) {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = "Switch organization"
                                )
                            }
                            DropdownMenu(
                                expanded = showOrgMenu,
                                onDismissRequest = { showOrgMenu = false }
                            ) {
                                if (orgViewModel.organizations.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No organizations") },
                                        onClick = { showOrgMenu = false },
                                        enabled = false
                                    )
                                } else {
                                    orgViewModel.organizations.forEach { org ->
                                        DropdownMenuItem(
                                            text = { Text(org.name) },
                                            trailingIcon = {
                                                if (org.id == selectedOrg?.id) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                selectedOrg = org
                                                showOrgMenu = false
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Manage organizations") },
                                    onClick = {
                                        showOrgMenu = false
                                        navController.navigate("home")
                                    }
                                )
                            }
                        }
                    }
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
                    pinEnabled = pinEnabled,
                    onSetPin = onSetPin,
                    onClearPin = onClearPin,
                    autoLockMinutes = autoLockMinutes,
                    onSetAutoLockMinutes = onSetAutoLockMinutes,
                    hideInRecents = hideInRecents,
                    onToggleHideInRecents = onToggleHideInRecents,
                    onLockNow = onLockNow,
                    uploadWifiOnly = uploadWifiOnly,
                    onToggleUploadWifiOnly = onToggleUploadWifiOnly,
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
