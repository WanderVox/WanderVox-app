package com.wandervox.features.translator.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.translator.R

@Composable
fun RecognizedTextCard(
    languageName: String,
    text: String,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    TranslationCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            CardLabel(languageName)
        }
        val placeholder = stringResource(
            if (isListening) R.string.translator_listening_placeholder else R.string.translator_recognized_placeholder
        )
        CardBody(text = text, placeholder = placeholder, style = BodyStyle.Recognized)
    }
}

@Composable
fun TranslatedTextCard(
    languageName: String,
    text: String,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TranslationCard(modifier = modifier) {
        CardLabel(languageName, emphasized = true)
        CardBody(
            text = text,
            placeholder = stringResource(R.string.translator_translation_placeholder),
            style = BodyStyle.Translated,
            // Announces new translations to TalkBack users without them having to move focus.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // IconButton enforces a 48 dp touch target via minimumInteractiveComponentSize.
            IconButton(onClick = onCopyClick, enabled = text.isNotBlank()) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.translator_copy_translation),
                )
            }
            // Text-to-speech placeholder: visible in the layout but disabled until TTS is implemented.
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = stringResource(R.string.translator_speak_translation),
                )
            }
        }
    }
}

@Composable
private fun TranslationCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun CardLabel(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private enum class BodyStyle { Recognized, Translated }

@Composable
private fun CardBody(
    text: String,
    placeholder: String,
    style: BodyStyle,
    modifier: Modifier = Modifier,
) {
    val isEmpty = text.isBlank()
    Text(
        text = if (isEmpty) placeholder else text,
        style = when (style) {
            BodyStyle.Recognized -> MaterialTheme.typography.titleLarge
            BodyStyle.Translated -> MaterialTheme.typography.headlineSmall
        },
        color = if (isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(end = 8.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun TranslationCardsPreview() {
    WanderVoxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecognizedTextCard(languageName = "Español", text = "¿Dónde está la estación de tren?", isListening = true)
            TranslatedTextCard(languageName = "Inglés", text = "Where is the train station?", onCopyClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun TranslationCardsEmptyPreview() {
    WanderVoxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecognizedTextCard(languageName = "Español", text = "", isListening = false)
            TranslatedTextCard(languageName = "Inglés", text = "", onCopyClick = {})
        }
    }
}
