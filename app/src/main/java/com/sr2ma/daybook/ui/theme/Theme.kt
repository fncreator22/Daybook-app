package com.sr2ma.daybook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DaybookColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = PaperRaised,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    inversePrimary = Teal95,

    secondary = Slate40,
    onSecondary = PaperRaised,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,

    tertiary = Indigo40,
    onTertiary = PaperRaised,
    tertiaryContainer = Indigo90,
    onTertiaryContainer = Indigo10,

    error = Red40,
    onError = PaperRaised,
    errorContainer = Red90,
    onErrorContainer = Red10,

    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceTint = Teal40,

    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    surfaceBright = PaperRaised,
    surfaceDim = PaperHighest,

    outline = Line,
    outlineVariant = LineFaint,

    inverseSurface = Ink,
    inverseOnSurface = PaperLow,
    scrim = Ink,
)

/**
 * The app's only theme.
 *
 * There is deliberately no dark scheme and no dynamic colour: the app was
 * specified as clean and light, and a single scheme means one set of contrast
 * decisions to get right. [MaterialTheme] is therefore given the light scheme
 * unconditionally. Matching light system bars are set once in MainActivity,
 * because this composable also runs inside dialog subcompositions, where there
 * is no Activity to reach through and no window of its own to configure.
 */
@Composable
fun DaybookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DaybookColorScheme,
        typography = DaybookTypography,
        shapes = DaybookShapes,
        content = content,
    )
}
