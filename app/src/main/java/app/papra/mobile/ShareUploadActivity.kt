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
import androidx.work.WorkManager
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.ApiKeyStore
import app.papra.mobile.data.ShareUploadQueueStore
import app.papra.mobile.ui.ApiKeyScreen
import app.papra.mobile.ui.ShareUploadScreen
import app.papra.mobile.ui.theme.PapraTheme
import app.papra.mobile.ui.normalizeBaseUrl
import kotlinx.coroutines.launch

class ShareUploadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUris = extractSharedUris(intent)
        if (sharedUris.isEmpty()) {
            finish()
            return
        }
        persistUriPermissions(intent, sharedUris)

        setContent {
            PapraTheme {
                val context = LocalContext.current
                val apiKeyStore = remember { ApiKeyStore(context) }
                val apiKey by apiKeyStore.apiKeyFlow.collectAsState(initial = null)
                val baseUrl by apiKeyStore.baseUrlFlow.collectAsState(initial = null)
                val uploadWifiOnly by apiKeyStore.uploadWifiOnlyFlow.collectAsState(initial = true)
                val resolvedBaseUrl = normalizeBaseUrl(baseUrl?.ifBlank { null } ?: "https://api.papra.app")
                val apiClient = remember(resolvedBaseUrl) { ApiClient(baseUrl = resolvedBaseUrl) }
                val scope = rememberCoroutineScope()
                val queueStore = remember { ShareUploadQueueStore(context) }
                val workManager = remember { WorkManager.getInstance(context) }

                if (apiKey.isNullOrBlank()) {
                    ApiKeyScreen(
                        initialBaseUrl = resolvedBaseUrl,
                        onSave = { key, url ->
                            scope.launch { apiKeyStore.saveSettings(key, normalizeBaseUrl(url)) }
                        }
                    )
                } else {
                    ShareUploadScreen(
                        apiClient = apiClient,
                        apiKey = apiKey ?: "",
                        sharedUris = sharedUris,
                        queueStore = queueStore,
                        workManager = workManager,
                        wifiOnly = uploadWifiOnly,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = mutableListOf<Uri>()
        val single = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (single != null) {
            uris.add(single)
        }
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                if (uri != null) {
                    uris.add(uri)
                }
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val multi = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (multi != null) {
                uris.addAll(multi)
            }
        }
        return uris.distinct()
    }

    private fun persistUriPermissions(intent: Intent?, uris: List<Uri>) {
        if (intent == null || uris.isEmpty()) return
        val takeFlags = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (takeFlags == 0) return
        val resolver = applicationContext.contentResolver
        uris.forEach { uri ->
            runCatching { resolver.takePersistableUriPermission(uri, takeFlags) }
        }
    }
}
