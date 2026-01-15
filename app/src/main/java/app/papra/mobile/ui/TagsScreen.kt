package app.papra.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.papra.mobile.data.ApiClient
import app.papra.mobile.data.Tag

@Composable
fun TagsScreen(
    apiClient: ApiClient,
    apiKey: String,
    organizationId: String?
) {
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var activeTag by remember { mutableStateOf<Tag?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(organizationId) {
        val orgId = organizationId ?: return@LaunchedEffect
        try {
            tags = apiClient.listTags(apiKey, orgId)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Failed to load tags"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tags", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { showCreateDialog = true }) {
                    Text("Create")
                }
            }
        }

        if (organizationId == null) {
            item { Text("Select an organization to view tags.") }
        } else if (error != null) {
            item { Text(error ?: "Failed to load tags") }
        } else {
            items(tags) { tag ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable {
                                activeTag = tag
                                showOptionsDialog = true
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(tag.name)
                            tag.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(desc)
                            }
                        }
                        val color = runCatching { Color(android.graphics.Color.parseColor(tag.color)) }
                            .getOrNull() ?: MaterialTheme.colorScheme.primary
                        Row(
                            modifier = Modifier
                                .size(24.dp)
                                .background(color)
                        ) {}
                    }
                }
            }
        }
    }

    if (showOptionsDialog) {
        val tag = activeTag
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text(tag?.name ?: "Tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manage this tag.")
                    OutlinedButton(
                        onClick = {
                            showOptionsDialog = false
                            showEditDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text("Edit tag", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            showOptionsDialog = false
                            showDeleteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Delete tag", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showOptionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showEditDialog) {
        val tag = activeTag
        var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
        var description by remember(tag?.id) { mutableStateOf(tag?.description.orEmpty()) }
        val initialColor = parseHexToHsv(tag?.color ?: "#2F6BFF") ?: Triple(215f, 0.7f, 1f)
        var hue by remember(tag?.id) { mutableStateOf(initialColor.first) }
        var saturation by remember(tag?.id) { mutableStateOf(initialColor.second) }
        var value by remember(tag?.id) { mutableStateOf(initialColor.third) }
        val previewColor = Color.hsv(hue, saturation, value)
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HsvColorPicker(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onColorChange = { newHue, newSat, newVal ->
                            hue = newHue
                            saturation = newSat
                            value = newVal
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val orgId = organizationId ?: return@Button
                        val tagId = tag?.id ?: return@Button
                        val normalizedColor = formatHexColor(previewColor)
                        scope.launch {
                            try {
                                apiClient.updateTag(
                                    apiKey,
                                    orgId,
                                    tagId,
                                    name.trim().ifBlank { null },
                                    normalizedColor,
                                    description.trim().ifBlank { null }
                                )
                                tags = apiClient.listTags(apiKey, orgId)
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to update tag"
                            } finally {
                                showEditDialog = false
                            }
                        }
                    },
                    enabled = name.isNotBlank() && organizationId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewColor,
                        contentColor = if (previewColor.luminance() > 0.6f) Color.Black else Color.White
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        val tag = activeTag
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete tag") },
            text = { Text("This removes the tag from the organization.") },
            confirmButton = {
                Button(
                    onClick = {
                        val orgId = organizationId ?: return@Button
                        val tagId = tag?.id ?: return@Button
                        scope.launch {
                            try {
                                apiClient.deleteTag(apiKey, orgId, tagId)
                                tags = apiClient.listTags(apiKey, orgId)
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to delete tag"
                            } finally {
                                showDeleteDialog = false
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var hue by remember { mutableStateOf(215f) }
        var saturation by remember { mutableStateOf(0.7f) }
        var value by remember { mutableStateOf(1f) }
        val previewColor = Color.hsv(hue, saturation, value)
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (name.isNotBlank()) {
                        HsvColorPicker(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            onColorChange = { newHue, newSat, newVal ->
                                hue = newHue
                                saturation = newSat
                                value = newVal
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = {
                        val orgId = organizationId ?: return@Button
                        val normalizedColor = formatHexColor(previewColor)
                        scope.launch {
                            try {
                                apiClient.createTag(apiKey, orgId, name, normalizedColor, description.ifBlank { null })
                                tags = apiClient.listTags(apiKey, orgId)
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to create tag"
                            } finally {
                                showCreateDialog = false
                            }
                        }
                    },
                    enabled = name.isNotBlank() && organizationId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewColor,
                        contentColor = if (previewColor.luminance() > 0.6f) Color.Black else Color.White
                    )
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
