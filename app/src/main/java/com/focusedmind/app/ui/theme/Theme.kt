package com.focusedmind.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFFFB74D), secondary = androidx.compose.ui.graphics.Color(0xFF9FA8DA), tertiary = androidx.compose.ui.graphics.Color(0xFF80CBC4))
private val Light = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8A5A00), secondary = androidx.compose.ui.graphics.Color(0xFF5F6368), tertiary = androidx.compose.ui.graphics.Color(0xFF00695C))

@Composable
fun FocusedMindTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when { dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { val c=LocalContext.current; if(darkTheme) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c) }; darkTheme -> Dark; else -> Light }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
