package app.papra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
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
    onResetApp: () -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var newApiKey by remember { mutableStateOf("") }
    var showAutoLockMenu by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val autoLockOptions = listOf(0, 1, 5, 15, 30, 60)
    val autoLockLabel = when (autoLockMinutes) {
        0 -> "Off"
        1 -> "1 minute"
        else -> "$autoLockMinutes minutes"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App Settings", color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Text("Notifications")
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Text("Biometric lock")
                    }
                    Switch(checked = biometricEnabled, onCheckedChange = onToggleBiometric)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PIN fallback")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showPinDialog = true }) {
                            Text(if (pinEnabled) "Change PIN" else "Set PIN")
                        }
                        if (pinEnabled) {
                            OutlinedButton(onClick = onClearPin) {
                                Text("Remove")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-lock")
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(onClick = { showAutoLockMenu = true }) {
                            Text(autoLockLabel)
                        }
                        DropdownMenu(
                            expanded = showAutoLockMenu,
                            onDismissRequest = { showAutoLockMenu = false }
                        ) {
                            autoLockOptions.forEach { minutes ->
                                val label = when (minutes) {
                                    0 -> "Off"
                                    1 -> "1 minute"
                                    else -> "$minutes minutes"
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onSetAutoLockMinutes(minutes)
                                        showAutoLockMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hide in app switcher")
                    Switch(checked = hideInRecents, onCheckedChange = onToggleHideInRecents)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Uploads on Wi-Fi only")
                    Switch(checked = uploadWifiOnly, onCheckedChange = onToggleUploadWifiOnly)
                }
                OutlinedButton(onClick = onLockNow, enabled = biometricEnabled) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text("Lock now", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server", color = MaterialTheme.colorScheme.primary)
                Text(baseUrl)
                OutlinedButton(onClick = onResetApp) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Reset app", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Account", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { showApiKeyDialog = true }) {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                    Text("Update API key", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Text("Log out", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Update API key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Paste your new API key below.")
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateApiKey(newApiKey.trim())
                        newApiKey = ""
                        showApiKeyDialog = false
                    },
                    enabled = newApiKey.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (pinEnabled) "Change PIN" else "Set PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { value ->
                            newPin = value.filter { it.isDigit() }.take(8)
                            pinError = null
                        },
                        label = { Text("PIN (4-8 digits)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { value ->
                            confirmPin = value.filter { it.isDigit() }.take(8)
                            pinError = null
                        },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pinError.isNullOrBlank()) {
                        Text(pinError ?: "")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length < 4) {
                            pinError = "PIN must be at least 4 digits."
                            return@Button
                        }
                        if (newPin != confirmPin) {
                            pinError = "PINs do not match."
                            return@Button
                        }
                        onSetPin(newPin)
                        newPin = ""
                        confirmPin = ""
                        pinError = null
                        showPinDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
