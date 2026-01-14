package app.papra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning

@Composable
fun CertificateStatusRow(baseUrl: String, modifier: Modifier = Modifier) {
    val scheme = parseScheme(baseUrl)
    val isHttps = scheme == "https"
    val color = if (isHttps) Color(0xFF2E7D32) else Color(0xFFC62828)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isHttps) "Connection: HTTPS" else "Connection: HTTP", color = color)
        Icon(
            imageVector = if (isHttps) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = color
        )
    }
}
