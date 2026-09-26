package com.chy.muscletome.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Exercise demo image (wger-sourced), framed the ledger way: hairline steel
 * outline over a cast-iron backing that holds the frame's exact size while
 * the network image streams in — loading never jumps the layout — and a
 * short crossfade when the bitmap lands.
 *
 * Null/blank URIs render nothing; callers decide the fallback (library rows
 * show their ledger index instead). Pass the exact size or aspect ratio in
 * [modifier]. ContentScale.Fit keeps whole-movement form visible (hero
 * usage); pickers pass Crop for thumbnails.
 */
@Composable
fun ExerciseDemoImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (uri.isNullOrBlank()) return
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(250)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentScale = contentScale,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Default hero treatment: full-width 4:3 frame for the exercise detail view. */
@Composable
fun ExerciseDemoHero(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    ExerciseDemoImage(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioFourThirds(),
        contentScale = ContentScale.Fit,
    )
}

private fun Modifier.aspectRatioFourThirds(): Modifier =
    this.aspectRatio(4f / 3f)
