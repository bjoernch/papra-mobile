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
import app.papra.mobile.ui.OrganizationsScreen
import app.papra.mobile.ui.theme.PapraTheme
import kotlinx.coroutines.launch

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
    var suppressAutoOpen by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(notificationsEnabled, apiKey, resolvedBaseUrl) {
        if (notificationsEnabled && !apiKey.isNullOrBlank()) {
            NotificationScheduler.schedule(context)
        } else {
            NotificationScheduler.cancel(context)
        }
    }

    if (apiKey.isNullOrBlank()) {
        ApiKeyScreen(
            initialBaseUrl = resolvedBaseUrl,
            onSave = { key, url ->
                val normalizedUrl = normalizeBaseUrl(url)
                scope.launch { apiKeyStore.saveSettings(key, normalizedUrl) }
            }
        )
    } else if (biometricEnabled && !isUnlocked) {
        BiometricLockScreen(context = context, onAuthenticated = { isUnlocked = true })
    } else {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "orgs") {
            composable("orgs") {
                OrganizationsScreen(
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
                    suppressAutoOpen = suppressAutoOpen,
                    onResetApp = {
                        scope.launch {
                            apiKeyStore.clearApiKey()
                            clearOfflineCache(context)
                        }
                        NotificationScheduler.cancel(context)
                        suppressAutoOpen = false
                    },
                    onOrganizationSelected = { org ->
                        suppressAutoOpen = false
                        navController.navigate("documents/${org.id}/${Uri.encode(org.name)}")
                    },
                    onLogout = {
                        suppressAutoOpen = false
                        scope.launch { apiKeyStore.clearApiKey() }
                    }
                )
            }
            composable(
                route = "documents/{orgId}/{orgName}",
                arguments = listOf(
                    navArgument("orgId") { type = NavType.StringType },
                    navArgument("orgName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val orgId = backStackEntry.arguments?.getString("orgId") ?: ""
                val orgName = backStackEntry.arguments?.getString("orgName") ?: "Documents"
                DocumentsScreen(
                    apiClient = apiClient,
                    apiKey = apiKey ?: "",
                    organizationId = orgId,
                    organizationName = orgName,
                    onBack = {
                        suppressAutoOpen = true
                        navController.popBackStack()
                    },
                    onPreview = { document ->
                        navController.navigate("document/$orgId/${document.id}")
                    }
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
                    apiKey = apiKey ?: "",
                    organizationId = orgId,
                    documentId = docId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun clearOfflineCache(context: android.content.Context) {
    val cacheDir = java.io.File(context.filesDir, "offline_docs")
    cacheDir.listFiles()?.forEach { it.delete() }
    cacheDir.delete()
}

private fun normalizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().removeSuffix("/")
    return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
}
