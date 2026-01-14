package app.papra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    initialBaseUrl: String,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }

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
            Text("Sign in with your Papra API key.")
            Text("Create a token in your Papra account and paste it here.")

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                placeholder = { Text("Bearer token") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API base URL") },
                placeholder = { Text("https://api.papra.app") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Tip: /api is added automatically if missing.")

            Button(
                onClick = { onSave(apiKey, baseUrl) },
                enabled = apiKey.isNotBlank() && baseUrl.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Continue")
            }
        }
    }
}
