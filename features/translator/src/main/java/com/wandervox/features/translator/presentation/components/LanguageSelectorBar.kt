package com.wandervox.features.translator.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.translator.R

@Composable
fun LanguageSelectorBar(
    sourceLanguage: String,
    targetLanguage: String,
    swapEnabled: Boolean,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceName = languageDisplayName(sourceLanguage)
    val targetName = languageDisplayName(targetLanguage)
    val sourceDescription = stringResource(R.string.translator_source_language_description, sourceName)
    val targetDescription = stringResource(R.string.translator_target_language_description, targetName)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguagePill(
            name = sourceName,
            description = sourceDescription,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = onSwapClick,
            enabled = swapEnabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(SwapButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.translator_swap_languages),
            )
        }
        LanguagePill(
            name = targetName,
            description = targetDescription,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LanguagePill(
    name: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
        modifier = modifier
            .heightIn(min = PillMinHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun languageDisplayName(code: String): String = when (code.uppercase()) {
    "ES" -> stringResource(R.string.translator_language_spanish)
    "EN" -> stringResource(R.string.translator_language_english)
    else -> code
}

private val SwapButtonSize = 48.dp
private val PillMinHeight = 48.dp

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun LanguageSelectorBarPreview() {
    WanderVoxTheme {
        LanguageSelectorBar(
            sourceLanguage = "ES",
            targetLanguage = "EN",
            swapEnabled = true,
            onSwapClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
