package com.chy.muscletome.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * MuscleTome is a dark-only, brutalist iron journal. The scheme is fixed:
 * Material You dynamic color and light mode are intentionally absent so the
 * cast-iron / archival-cream / data-amber brand reads identically everywhere.
 */
private val IronTomeColorScheme = darkColorScheme(
    primary = DataAmber,
    onPrimary = Ink,
    primaryContainer = AmberContainer,
    onPrimaryContainer = DataAmber,
    inversePrimary = DataAmber,

    secondary = SteelBlue,
    onSecondary = Ink,
    secondaryContainer = IronRaised,
    onSecondaryContainer = SteelBlue,

    tertiary = ArchivalCream,
    onTertiary = Ink,
    tertiaryContainer = CastIron,
    onTertiaryContainer = ArchivalCream,

    background = Ink,
    onBackground = ArchivalCream,
    surface = Ink,
    onSurface = ArchivalCream,
    surfaceVariant = CastIron,
    onSurfaceVariant = AgedPaper,
    surfaceDim = Ink,
    surfaceBright = IronRaised,
    surfaceContainer = CastIron,
    surfaceContainerLow = InkRaised,
    surfaceContainerLowest = Ink,
    surfaceContainerHigh = IronRaised,
    surfaceContainerHighest = IronRaised,
    inverseSurface = ArchivalCream,
    inverseOnSurface = Ink,

    outline = RawSteel,
    outlineVariant = IronRaised,

    error = Rust,
    onError = Ink,
    errorContainer = RustContainer,
    onErrorContainer = Rust,

    scrim = Ink,
)

/** Near-zero radii — monolithic blocks, not rounded pebbles. */
private val IronShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
)

@Composable
fun MuscleTomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IronTomeColorScheme,
        typography = Typography,
        shapes = IronShapes,
        content = content,
    )
}
