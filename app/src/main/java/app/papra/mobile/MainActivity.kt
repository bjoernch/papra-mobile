package app.papra.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.ui.ApiKeyScreen
import app.papra.mobile.ui.DocumentsScreen
import app.papra.mobile.ui.OrganizationsScreen
import app.papra.mobile.ui.theme.PapraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PapraTheme {
                PapraApp()
            }
        }
    }
}

@Composable
fun PapraApp() {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val apiKey by apiKeyStore.apiKeyFlow.collectAsState(initial = null)
    val apiClient = remember { ApiClient() }
    val scope = rememberCoroutineScope()

    if (apiKey.isNullOrBlank()) {
        ApiKeyScreen(onSave = { key ->
            scope.launch { apiKeyStore.saveApiKey(key) }
        })
    } else {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "orgs") {
            composable("orgs") {
                OrganizationsScreen(
                    apiClient = apiClient,
                    apiKey = apiKey ?: "",
                    onOrganizationSelected = { org ->
                        navController.navigate("documents/${org.id}/${Uri.encode(org.name)}")
                    },
                    onLogout = {
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
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
