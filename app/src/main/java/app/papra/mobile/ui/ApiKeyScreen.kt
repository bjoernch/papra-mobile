package app.papra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Papra") })
        }
    ) { padding ->
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
