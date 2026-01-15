package app.papra.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun HsvColorPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChange: (Float, Float, Float) -> Unit
) {
    val previewColor = Color.hsv(hue, saturation, value)
    var hexInput by remember { mutableStateOf(formatHexColor(previewColor)) }

    LaunchedEffect(hue, saturation, value) {
        hexInput = formatHexColor(Color.hsv(hue, saturation, value))
    }

    Column {
        Text("Hue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(
            value = hue / 360f,
            colors = listOf(
                Color(0xFFFF0000),
                Color(0xFFFFFF00),
                Color(0xFF00FF00),
                Color(0xFF00FFFF),
                Color(0xFF0000FF),
                Color(0xFFFF00FF),
                Color(0xFFFF0000)
            ),
            onValueChange = { onColorChange(it * 360f, saturation, value) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Saturation", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(
            value = saturation,
            colors = listOf(
                Color.hsv(hue, 0f, value),
                Color.hsv(hue, 1f, value)
            ),
            onValueChange = { onColorChange(hue, it, value) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Lightness", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(
            value = value,
            colors = listOf(
                Color.Black,
                Color.hsv(hue, saturation, 1f)
            ),
            onValueChange = { onColorChange(hue, saturation, it) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hexInput,
            onValueChange = {
                hexInput = it
                parseHexToHsv(it)?.let { (h, s, v) ->
                    onColorChange(h, s, v)
                }
            },
            label = { Text("Hex") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun GradientSlider(
    value: Float,
    colors: List<Color>,
    onValueChange: (Float) -> Unit
) {
    val thumbRadius = 12.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        val trackHeight = 12.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val trackSize = Size(size.width, trackHeight)
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            topLeft = Offset(0f, top),
            size = trackSize,
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        )
        val thumbCenter = Offset(value * size.width, size.height / 2f)
        drawCircle(
            color = Color.White,
            radius = thumbRadius.toPx(),
            center = thumbCenter
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = thumbRadius.toPx(),
            center = thumbCenter,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

fun formatHexColor(color: Color): String {
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

fun parseHexToHsv(value: String): Triple<Float, Float, Float>? {
    val cleaned = value.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    return runCatching {
        val colorInt = android.graphics.Color.parseColor("#$cleaned")
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(colorInt, hsv)
        Triple(hsv[0], hsv[1], hsv[2])
    }.getOrNull()
}
