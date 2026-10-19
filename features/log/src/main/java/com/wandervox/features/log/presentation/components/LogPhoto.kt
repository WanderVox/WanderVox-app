package com.wandervox.features.log.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.log.R
import java.io.File

/**
 * Photo of an entry, loaded by Coil. The local copy wins (offline-first); the Cloudinary URL is used
 * when there is no local file, e.g. for Community entries from other users.
 */
@Composable
fun LogPhoto(
    entry: TravelLogEntry,
    modifier: Modifier = Modifier,
    isImporting: Boolean = false,
) {
    val model: Any? = remember(entry.localPhotoPath, entry.remotePhotoUrl) {
        entry.localPhotoPath?.let(::File)?.takeIf { it.exists() } ?: entry.remotePhotoUrl
    }
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    var hasFailed by remember(model) { mutableStateOf(false) }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.log_photo_description),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                onError = { hasFailed = true },
                onSuccess = { hasFailed = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            isImporting -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            model == null || hasFailed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.log_photo_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Whether [LogPhoto] has anything to show for this entry. */
val TravelLogEntry.hasPhoto: Boolean
    get() = localPhotoPath != null || remotePhotoUrl != null
