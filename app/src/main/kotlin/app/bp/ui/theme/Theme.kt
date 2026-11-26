package app.bp.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Fixed teal-based fallback palette, used on Android 12 and below. */
private val TealLight = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF99F6E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF07211D),
    surface = Color(0xFFFAFDFB),
    background = Color(0xFFFAFDFB),
    onSurface = Color(0xFF191C1B),
    onBackground = Color(0xFF191C1B),
    error = Color(0xFFBA1A1A),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF5FD4C8),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00514A),
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = Color(0xFFB0CCC6),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    surface = Color(0xFF111413),
    background = Color(0xFF111413),
    onSurface = Color(0xFFE1E3E2),
    onBackground = Color(0xFFE1E3E2),
    error = Color(0xFFFFB4AB),
)

@Composable
fun BpTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> TealDark
        else -> TealLight
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
