package com.wandervox.features.log.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.log.R
import java.text.DateFormat
import java.util.Date

/**
 * One Travel Log entry. Optional parts are switched on by the caller: the author label for the
 * Community feed, the sync badge for the user's own entries, and delete / photo picking only when
 * [onDelete] / [onPickPhoto] are set. The like button appears on public entries.
 */
@Composable
fun LogEntryCard(
    entry: TravelLogEntry,
    currentUserId: String?,
    showAuthor: Boolean,
    showSyncStatus: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    isImportingPhoto: Boolean = false,
    onPickPhoto: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        if (entry.hasPhoto || isImportingPhoto) {
            LogPhoto(
                entry = entry,
                isImporting = isImportingPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_HEIGHT),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                CategoryLabel(entry.category)
                Spacer(modifier = Modifier.weight(1f))
                if (showSyncStatus) SyncStatusBadge(entry.syncStatus)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = entry.originalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.translatedText.isNotBlank()) {
                    Text(
                        text = entry.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (entry.notes.isNotBlank()) {
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showAuthor) {
                        Text(
                            text = stringResource(
                                R.string.log_author_by,
                                entry.authorName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.log_author_anonymous),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        text = rememberFormattedDate(entry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.isPublic) {
                    LikeButton(
                        isLiked = currentUserId != null && currentUserId in entry.likes,
                        likeCount = entry.likes.size,
                        enabled = currentUserId != null,
                        onToggle = onToggleLike,
                    )
                }
                if (onPickPhoto != null) {
                    IconButton(onClick = onPickPhoto, enabled = !isImportingPhoto) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = stringResource(
                                if (entry.hasPhoto) R.string.log_change_photo else R.string.log_add_photo
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.log_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(category: LogCategory) {
    Pill(
        icon = category.icon,
        text = stringResource(category.labelRes),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun SyncStatusBadge(status: SyncStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        // PENDING_DELETE never reaches the UI (the repository hides tombstones); grouped to stay exhaustive.
        SyncStatus.PENDING, SyncStatus.PENDING_DELETE -> Pill(
            icon = Icons.Outlined.CloudUpload,
            text = stringResource(R.string.log_sync_pending),
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        )
        SyncStatus.SYNCED -> Pill(
            icon = Icons.Outlined.CloudDone,
            text = stringResource(R.string.log_sync_synced),
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        )
        SyncStatus.FAILED -> Pill(
            icon = Icons.Outlined.CloudOff,
            text = stringResource(R.string.log_sync_failed),
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
        )
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, contentColor = contentColor, shape = MaterialTheme.shapes.small) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LikeButton(isLiked: Boolean, likeCount: Int, enabled: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (likeCount > 0) {
            Text(
                text = likeCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconToggleButton(checked = isLiked, onCheckedChange = { onToggle() }, enabled = enabled) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (isLiked) R.string.log_unlike else R.string.log_like),
                tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PHOTO_HEIGHT = 180.dp

@Composable
private fun rememberFormattedDate(timestamp: Long): String =
    remember(timestamp) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)) }

@Preview(showBackground = true)
@Composable
private fun LogEntryCardPreview() {
    WanderVoxTheme {
        LogEntryCard(
            entry = TravelLogEntry(
                id = "1",
                originalText = "¿Dónde está la estación de tren?",
                translatedText = "Where is the train station?",
                notes = "Me lo indicaron en la recepción del hotel.",
                category = LogCategory.TRANSPORT,
                isPublic = true,
                likes = listOf("u2", "u3"),
                syncStatus = SyncStatus.FAILED,
            ),
            currentUserId = "u2",
            showAuthor = false,
            showSyncStatus = true,
            onToggleLike = {},
            onPickPhoto = {},
            onDelete = {},
        )
    }
}
