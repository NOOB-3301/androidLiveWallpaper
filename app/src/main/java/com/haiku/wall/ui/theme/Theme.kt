package com.haiku.wall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun HaikuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkHaikuColors else LightHaikuColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.text,
            secondary = palette.pine,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.surfaceSolid,
            onSecondaryContainer = palette.text,
            tertiary = palette.info,
            onTertiary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceSolid,
            onSurfaceVariant = palette.muted,
            surfaceTint = palette.accent,
            outline = palette.hairline,
            outlineVariant = palette.hairline,
            error = palette.error,
            onError = palette.onAccent,
            errorContainer = palette.accentSoft,
            onErrorContainer = palette.error,
            inverseSurface = palette.text,
            inverseOnSurface = palette.bg,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.text,
            secondary = palette.pine,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.surfaceSolid,
            onSecondaryContainer = palette.text,
            tertiary = palette.info,
            onTertiary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceSolid,
            onSurfaceVariant = palette.muted,
            surfaceTint = palette.accent,
            outline = palette.hairline,
            outlineVariant = palette.hairline,
            error = palette.error,
            onError = palette.onAccent,
            errorContainer = palette.accentSoft,
            onErrorContainer = palette.error,
            inverseSurface = palette.text,
            inverseOnSurface = palette.bg,
        )
    }

    CompositionLocalProvider(
        LocalHaikuColors provides palette,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = palette.accent,
            backgroundColor = palette.accentSoft,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HaikuTypography,
            shapes = HaikuShapes,
            content = content,
        )
    }
}
