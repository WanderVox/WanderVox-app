package com.wandervox.features.log.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * [onDelete] / [onPickPhoto] are set, and the private/public indicator and share button only when
 * [onToggleVisibility] is set. The like button appears on public entries. With [onClick] the
 * whole card is tappable (e.g. to open the entry detail); the inner buttons keep their own action.
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
    onClick: (() -> Unit)? = null,
    onToggleVisibility: (() -> Unit)? = null,
    onPickPhoto: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        LogEntryCardBody(
            entry = entry,
            currentUserId = currentUserId,
            showAuthor = showAuthor,
            showSyncStatus = showSyncStatus,
            onToggleLike = onToggleLike,
            isImportingPhoto = isImportingPhoto,
            onToggleVisibility = onToggleVisibility,
            onPickPhoto = onPickPhoto,
            onDelete = onDelete,
        )
    }
    // Two overloads instead of a disabled clickable card, which would switch to the disabled colors.
    if (onClick != null) {
        ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        ElevatedCard(modifier = modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun LogEntryCardBody(
    entry: TravelLogEntry,
    currentUserId: String?,
    showAuthor: Boolean,
    showSyncStatus: Boolean,
    onToggleLike: () -> Unit,
    isImportingPhoto: Boolean,
    onToggleVisibility: (() -> Unit)?,
    onPickPhoto: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
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
            // A private entry that owes the server nothing lives only on the device: no sync state to show.
            if (showSyncStatus && (entry.isPublic || entry.syncStatus != SyncStatus.SYNCED)) {
                SyncStatusBadge(entry.syncStatus)
            }
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
                if (onToggleVisibility != null) VisibilityLabel(isPublic = entry.isPublic)
            }
            if (onToggleVisibility != null) {
                ShareButton(
                    isPublic = entry.isPublic,
                    // PENDING on an own entry means an upload or unpublish is running right now.
                    isSyncing = entry.syncStatus == SyncStatus.PENDING,
                    onClick = onToggleVisibility,
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

/** Share (upload) a private entry, or show that it is shared and offer to make it private again. */
@Composable
private fun ShareButton(isPublic: Boolean, isSyncing: Boolean, onClick: () -> Unit) {
    if (isSyncing) {
        val description = stringResource(R.string.log_share_in_progress)
        // Same 48dp slot as an IconButton, so the row does not jump while uploading.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description },
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
        return
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isPublic) Icons.Filled.Public else Icons.Outlined.Share,
            contentDescription = stringResource(
                if (isPublic) R.string.log_visibility_make_private else R.string.log_visibility_make_public
            ),
            tint = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisibilityLabel(isPublic: Boolean) {
    val color = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = visibilityIcon(isPublic), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(if (isPublic) R.string.log_visibility_public else R.string.log_visibility_private),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Icon for an entry's visibility, shared with the entry detail screen. */
internal fun visibilityIcon(isPublic: Boolean): ImageVector =
    if (isPublic) Icons.Outlined.Public else Icons.Outlined.Lock

/** Category pill shared with the entry detail screen. */
@Composable
internal fun CategoryLabel(category: LogCategory) {
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
            onToggleVisibility = {},
            onPickPhoto = {},
            onDelete = {},
        )
    }
}
