package eu.sancris.cititor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color.Black,
    background = Color.Black,
    surface = Color(0xFF111111),
    onSurface = Color.White,
)

@Composable
fun SancrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
