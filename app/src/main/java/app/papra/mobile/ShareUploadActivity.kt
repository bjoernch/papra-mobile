package app.papra.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.ui.ApiKeyScreen
import app.papra.mobile.ui.ShareUploadScreen
import app.papra.mobile.ui.theme.PapraTheme
import kotlinx.coroutines.launch

class ShareUploadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUri = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (sharedUri == null) {
            finish()
            return
        }

        setContent {
            PapraTheme {
                val context = LocalContext.current
                val apiKeyStore = remember { ApiKeyStore(context) }
                val apiKey by apiKeyStore.apiKeyFlow.collectAsState(initial = null)
                val baseUrl by apiKeyStore.baseUrlFlow.collectAsState(initial = null)
                val resolvedBaseUrl = baseUrl?.ifBlank { null } ?: "https://api.papra.app/api"
                val apiClient = remember(resolvedBaseUrl) { ApiClient(baseUrl = resolvedBaseUrl) }
                val scope = rememberCoroutineScope()

                if (apiKey.isNullOrBlank()) {
                    ApiKeyScreen(
                        initialBaseUrl = resolvedBaseUrl,
                        onSave = { key, url ->
                            scope.launch { apiKeyStore.saveSettings(key, url) }
                        }
                    )
                } else {
                    ShareUploadScreen(
                        apiClient = apiClient,
                        apiKey = apiKey ?: "",
                        sharedUri = sharedUri,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}
