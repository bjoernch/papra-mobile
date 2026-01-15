package app.papra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.res.painterResource
import app.papra.mobile.R
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    initialBaseUrl: String,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var permissionsOk by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var lastCheckedUrl by remember { mutableStateOf<String?>(null) }
    var showIntro by remember { mutableStateOf(initialBaseUrl.isBlank()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Papra") })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Connect to your Papra server.")
                Text("Step 1: enter the server URL.")

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        permissionsOk = false
                        checkError = null
                    },
                    label = { Text("Papra server URL") },
                    placeholder = { Text("https://docs.bjoernfelgner.com") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("We add /api automatically if needed.")

                CertificateStatusRow(baseUrl = baseUrl)

                Button(
                    onClick = {
                        if (isValidBaseUrl(baseUrl)) {
                            permissionsOk = true
                            lastCheckedUrl = normalizeBaseUrl(baseUrl)
                            checkError = null
                        } else {
                            permissionsOk = false
                            lastCheckedUrl = null
                            checkError = "Please enter a valid URL (including .com, .de, etc)."
                        }
                    },
                    enabled = baseUrl.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Confirm URL")
                }

                if (checkError != null) {
                    Text(checkError ?: "")
                }

                if (lastCheckedUrl != null) {
                    Text("Using API base: ${lastCheckedUrl ?: ""}")
                }

                AnimatedVisibility(
                    visible = permissionsOk,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text("URL confirmed", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (permissionsOk) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
                        placeholder = { Text("Paste your token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Step 2: add your API key.")
                    Text("Don't know how to create one? Visit:")
                    val helpUrl = buildHelpUrl(baseUrl)
                    Text(
                        helpUrl,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))
                            context.startActivity(intent)
                        }
                    )
                }

                Button(
                    onClick = { onSave(normalizeApiKey(apiKey), normalizeBaseUrl(baseUrl)) },
                    enabled = permissionsOk && apiKey.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Continue")
                }
            }

            if (showIntro) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "Papra Mobile",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = "Papra Mobile",
                                modifier = Modifier
                                    .size(72.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                "Your documents, synced and organized in one place.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FeatureRow(Icons.Default.Description, "Preview PDFs with swipe, zoom, and rotation.")
                                FeatureRow(Icons.Default.CloudDone, "Upload from local files or scan to PDF with background sync.")
                                FeatureRow(Icons.Default.Lock, "Secure access with biometric lock, auto-lock, and hide in recents.")
                                FeatureRow(Icons.Default.Description, "Tags, bulk actions, and offline downloads.")
                                FeatureRow(Icons.Default.CloudDone, "Search with saved searches and quick filters.")
                                FeatureRow(Icons.Default.Share, "Share to Papra from other apps.")
                            }
                            Button(
                                onClick = { showIntro = false },
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text("Get started")
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun normalizeApiKey(rawKey: String): String {
    val trimmed = rawKey.trim()
    return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
        trimmed.removePrefix("Bearer ").trim()
    } else {
        trimmed
    }
}
