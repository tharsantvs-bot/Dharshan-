package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BentoDarkPrimary,
    onPrimary = BentoDarkOnPrimary,
    secondary = BentoDarkPrimaryContainer,
    onSecondary = BentoDarkOnPrimaryContainer,
    tertiary = CyanAccent,
    background = BentoDarkBackground,
    onBackground = TextLight,
    surface = BentoDarkSurface,
    onSurface = TextLight,
    error = AlertRed,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = BentoPrimary,
    onPrimary = BentoOnPrimary,
    secondary = BentoPrimaryContainer,
    onSecondary = BentoOnPrimaryContainer,
    tertiary = CyanAccent,
    background = BentoBackground,
    onBackground = BentoOnBackground,
    surface = BentoSurface,
    onSurface = BentoOnSurface,
    error = AlertRed,
    onError = PureWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
