package com.wandervox.features.log.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.features.log.presentation.components.CategoryLabel
import com.wandervox.features.log.presentation.components.LogPhoto
import com.wandervox.features.log.presentation.components.LogPlaceholder
import com.wandervox.features.log.presentation.components.hasPhoto
import com.wandervox.features.log.presentation.components.visibilityIcon
import com.wandervox.log.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateless entry detail UI: the full, untruncated entry (photo, category, visibility, both texts,
 * notes, author, date and geotag) under a top bar with a back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailContent(
    uiState: LogDetailUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.log_detail_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.log_detail_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (uiState) {
            LogDetailUiState.Loading -> Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LogDetailUiState.NotFound -> LogPlaceholder(
                icon = Icons.Outlined.SearchOff,
                text = stringResource(R.string.log_detail_not_found),
                actionLabel = stringResource(R.string.log_detail_back),
                onAction = onNavigateBack,
                modifier = bodyModifier,
            )
            is LogDetailUiState.Loaded -> EntryDetail(entry = uiState.entry, modifier = bodyModifier)
        }
    }
}

@Composable
private fun EntryDetail(entry: TravelLogEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (entry.hasPhoto) {
            LogPhoto(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PHOTO_HEIGHT),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryLabel(entry.category)
                Spacer(modifier = Modifier.weight(1f))
                entry.coordinates?.let { GeotagBadge(it) }
            }

            VisibilityRow(isPublic = entry.isPublic)

            LabeledText(
                label = stringResource(R.string.log_detail_original, entry.sourceLanguage),
                text = entry.originalText,
            ) { text ->
                Text(text = text, style = MaterialTheme.typography.headlineSmall)
            }
            if (entry.translatedText.isNotBlank()) {
                LabeledText(
                    label = stringResource(R.string.log_detail_translation, entry.targetLanguage),
                    text = entry.translatedText,
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (entry.notes.isNotBlank()) {
                LabeledText(label = stringResource(R.string.log_detail_notes), text = entry.notes) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.log_author_by,
                        entry.authorName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.log_author_anonymous),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = rememberFormattedDateTime(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, text: String, body: @Composable (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        body(text)
    }
}

@Composable
private fun VisibilityRow(isPublic: Boolean) {
    val color = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = visibilityIcon(isPublic), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = stringResource(if (isPublic) R.string.log_visibility_public else R.string.log_visibility_private),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            Text(
                text = stringResource(
                    if (isPublic) R.string.log_visibility_public_detail else R.string.log_visibility_private_detail
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GeotagBadge(coordinates: Coordinates) {
    val description = stringResource(R.string.log_detail_location_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Place, contentDescription = description, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(
                    R.string.log_detail_location,
                    coordinates.latitude.toCoordinateString(),
                    coordinates.longitude.toCoordinateString(),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val PHOTO_HEIGHT = 280.dp

/** Locale.ROOT keeps the dot as decimal separator so the comma between lat and lon stays unambiguous. */
private fun Double.toCoordinateString(): String = String.format(Locale.ROOT, "%.5f", this)

@Composable
private fun rememberFormattedDateTime(timestamp: Long): String =
    remember(timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(timestamp))
    }

@Preview(showBackground = true)
@Composable
private fun LogDetailContentPreview() {
    WanderVoxTheme {
        LogDetailContent(
            uiState = LogDetailUiState.Loaded(
                TravelLogEntry(
                    id = "1",
                    originalText = "¿Dónde está la estación de tren más cercana? Necesito llegar al centro antes de las ocho.",
                    translatedText = "Where is the nearest train station? I need to get downtown before eight.",
                    notes = "Me lo indicaron en la recepción del hotel.",
                    category = LogCategory.TRANSPORT,
                    authorName = "Lucas",
                    isPublic = true,
                    coordinates = Coordinates(latitude = -34.603722, longitude = -58.381592),
                ),
            ),
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LogDetailContentNotFoundPreview() {
    WanderVoxTheme {
        LogDetailContent(uiState = LogDetailUiState.NotFound, onNavigateBack = {})
    }
}
